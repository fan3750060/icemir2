package com.cqgame;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.cqgame.Global.LOG_TAG;

class UpdateLoaderWorker implements Runnable {

    private String _downloadFolder = "";
    private String _contentFolderUrl = "";
    private List<ManifestFile> _files = null;
    private Handler _uiHandler = null;


    //下载

    /**
     * Constructor.
     *
     */
    UpdateLoaderWorker(String downloadFolder, String contentFolderUrl, List<ManifestFile> files, Handler uiHandler) {
        _downloadFolder = downloadFolder;
        _contentFolderUrl = contentFolderUrl;
        _files = files;
        _uiHandler = uiHandler;
    }

    @Override
    public void run() {
        Log.i(LOG_TAG, "_installUpdate: start download files");

        List<ManifestFile> files = _files;
        String contentFolderUrl = _contentFolderUrl;
        String downloadFolder = _downloadFolder;
        Handler uiHandler = _uiHandler;

        long totalSize = 0;
        for(int i = 0; i < files.size(); i++){
            totalSize += files.get(i).size;
        }

        String fileUrl = "";
        long progress = 0;
        try{
            for (int i = 0; i < files.size(); i++) {
                ManifestFile file = files.get(i);
                Message msg = new Message();
                fileUrl = URLUtility.construct(contentFolderUrl, file.name);
                progress += file.size;
                Log.i(LOG_TAG, "download url from：" + fileUrl);
                String filePath = GameUpdateComponent.getPath(downloadFolder, file.name);
                download(fileUrl, filePath,file.hash);
                msg.what = i == (files.size() - 1) ? GameUpdateComponent.PROGRESS_COMPLETE : GameUpdateComponent.PROGRESS_UPDATE;
                msg.arg1 = (int)(progress * 1.0 / totalSize * 100);
                uiHandler.sendMessage(msg);
            }
        } catch (Exception ex){
            Log.e(LOG_TAG, "downloadFiles failed");
            ex.printStackTrace();
            Message msg = new Message();
            msg.what = GameUpdateComponent.PROGRESS_EXCEPTION;
            msg.obj = fileUrl;
            uiHandler.sendMessage(msg);
        }

        _files = null;
    }

    /**
     * Download file from server, save it on the disk and check his hash.
     *
     * @param urlFrom  url to download from
     * @param filePath where to save file
     * @throws IOException
     */
    public void download(final String urlFrom,final String filePath,final String hash) throws Exception {

        final File downloadFile = new File(filePath);

        //判断文件是否已经存在
        if(fileIsExists(filePath))
        {
            String md5 = getFileMD5(downloadFile);

            if(md5.toLowerCase().trim().equals(hash.toLowerCase().trim()))
            {
                Log.i(LOG_TAG, "文件:"+filePath + " 校验一致 md5:" + md5.toLowerCase() + " hash:"+hash.toLowerCase());
                return;
            }else{
                Log.i(LOG_TAG, "文件:"+filePath + " 校验不一致 md5:" + md5.toLowerCase() + " hash:"+hash.toLowerCase());
                deleteFile(filePath);
            }
        }

        //多线程断点续传下载
        String path = downloadFile.getParentFile().getAbsolutePath();
        BreakpointDownloader obj = new BreakpointDownloader(path,urlFrom);
        obj.download();

        while (obj.totalFinish < obj.totalLen)
        {
            double rate = obj.totalFinish*1.0/obj.totalLen*100;
            rate = (double) Math.round(rate * 100) / 100;
            Log.i(LOG_TAG, "文件:"+filePath + " 下载: "+obj.totalFinish/1048576+"mb/"+obj.totalLen/1048576+"mb ("+rate+"%)");
            Thread.sleep(1000);
        }

        Thread.sleep(3000);

//        //  第二种
//        String path = downloadFile.getParentFile().getAbsolutePath();
//        Downloader obj = new Downloader();
//        obj.download(filePath,urlFrom,path);
//
//        while (obj.finishedThread < obj.THREADCOUNT)
//        {
//            double rate = obj.now_length*1.0/obj.max_length*100;
//            rate = (double) Math.round(rate * 100) / 100;
//            Log.i(LOG_TAG, "文件:"+filePath + " 下载: "+obj.now_length/1048576+"mb/"+obj.max_length/1048576+"mb ("+rate+"%)");
//            Thread.sleep(1000);
//        }

//        //单线程下载
//        GameUpdateComponent.delete(downloadFile);
//        GameUpdateComponent.ensureDirectoryExists(downloadFile.getParentFile());
//
//        // download file
//        final URLConnection connection = GameUpdateComponent.createConnectionToURL(urlFrom);
//        final InputStream input = new BufferedInputStream(connection.getInputStream());
//        final OutputStream output = new BufferedOutputStream(new FileOutputStream(filePath, false));
//
//        final byte data[] = new byte[1024];
//        int count;
//        while ((count = input.read(data)) != -1) {
//            output.write(data, 0, count);
//        }
//
//        output.flush();
//        output.close();
//        input.close();

        //判断是否为zip并解压
        boolean status = filePath.contains(".zip");
        if(status)
        {
            if(fileIsExists(filePath))
            {
                String dirStr =  filePath.replace(".zip","");
                Log.i(LOG_TAG, "解压目录:"+dirStr);
                creatFile(dirStr);

                Log.i(LOG_TAG, "解压文件:"+filePath);
                unzip(dirStr,filePath);

            }else{
                Log.i(LOG_TAG, "文件不存在:"+filePath);
            }

        }
    }

    public static String getFileMD5(File file) {
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest = null;
        FileInputStream in = null;
        byte buffer[] = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return bytesToHexString(digest.digest());
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    /**
     * 删除单个文件
     * @param   filePath    被删除文件的文件名
     * @return 文件删除成功返回true，否则返回false
     */
    public boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.isFile() && file.exists()) {
            return file.delete();
        }
        return false;
    }

    public void creatFile(String path) {
        File file = new File(path);
        file.mkdirs();
    }

    //判断文件是否存在
    public boolean fileIsExists(String strFile)
    {
        try
        {
            File f=new File(strFile);
            if(!f.exists())
            {
                return false;
            }

        }
        catch (Exception e)
        {
            return false;
        }

        return true;
    }

    /**
     * 解压缩zip文件，耗时操作，建议放入异步线程
     *
     * */
    public static void unzip(String targetPath, String zipFilePath) {
        try {
            int BUFFER = 2048;
            String fileName = zipFilePath;
            String filePath = targetPath;
            ZipFile zipFile = new ZipFile(fileName);
            Enumeration emu = zipFile.entries();
            int i = 0;
            while (emu.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) emu.nextElement();
                if (entry.isDirectory()) {
                    new File(filePath + "/" + entry.getName()).mkdirs();
                    continue;
                }

                Log.i(LOG_TAG, "解压:"+filePath + "/" + entry.getName());

                BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                File file = new File(filePath + "/" + entry.getName());
                File parent = file.getParentFile();
                if (parent != null && (!parent.exists())) {
                    parent.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER);

                int count;
                byte data[] = new byte[BUFFER];
                while ((count = bis.read(data, 0, BUFFER)) != -1) {
                    bos.write(data, 0, count);
                }
                bos.flush();
                bos.close();
                bis.close();
            }
            zipFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
