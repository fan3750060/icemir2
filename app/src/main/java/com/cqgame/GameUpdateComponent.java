package com.cqgame;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.cqgame.Global.LOG_TAG;

public class GameUpdateComponent {

    private SharedPreferences _preferences;
    private AssetManager _am;
    private MainActivity _uiActivity;

    // connection timeout in milliseconds
    private static final int CONNECTION_TIMEOUT = 30000;

    // data read timeout in milliseconds
    private static final int READ_TIMEOUT = 30000;

    public static final String PREFERENCES_NAME_OF_VERSION = "game_version";    // SharedPreferences 名称

    public static final String VERSION_KEY_CURRENT_VERSION = "current_version";                 // SP 中记录当前包内运行的版本号
    public static final String VERSION_KEY_NEW_VERSION = "new_version";                         // SP 中记录服务器端最新版本号
    public static final String VERSION_KEY_NEW_VERSION_STATE = "new_version_state";             // SP 中记录最新版本的更新状态- 0：未完成/1：已完成
    public static final String VERSION_KEY_NEW_VERSION_REMOTE_URL = "new_version_remote_url";   // SP 中记录最新版本资源所在服务器地址

    public static final String FILE_NAME_VERSION_MANIFEST = "version.manifest"; //版本号文件, 当版本由后台管理（此示例为后台管理版本）的时候，此文件可以不用，因为project.manifest包含version.manifest，当前端管理版本时候，此文件用来最初比较远端版本资源
    public static final String FILE_NAME_PROJECT_MANIFEST = "project.manifest"; //所有文件唯一标识文件

    public static final String HTTP_FOLDER = "http" + File.separator;

    public String preloadPath = ""; // 白鹭游戏预加载路径

    public static final int NEW_VERSION_STATE_INCOMPLETE = 0;   // 有新版本，未更新完成
    public static final int NEW_VERSION_STATE_COMPLETE = 1;     // 新版本已经更新完成

    public static final int PROGRESS_UPDATE = 0;    // 更新中
    public static final int PROGRESS_COMPLETE = 1;  // 更新完成
    public static final int PROGRESS_EXCEPTION = 2;  // 更新异常

    private List<ManifestFile> _deleteItems = null; // 更新中需要删除的文件
    private List<ManifestFile> _updateItems = null; // 更新中需要下载覆盖或者新增的文件


    public GameUpdateComponent(Context context, MainActivity uiActivity) {
        /**
         * initialize share preferences for game version management
         */
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME_OF_VERSION, Context.MODE_PRIVATE);
        _preferences = preferences;
        _am = context.getAssets();
        _uiActivity = uiActivity;
        preloadPath = context.getExternalFilesDir(null).getPath() + File.separator + "update" + File.separator; // 外部存储路径，文件管理可见
//        preloadPath = context.getFilesDir().getPath() + File.separator + "update" + File.separator;    // 应用内存储，文件管理不可见

        if (isFirstRun()) {
            _initGameVersionPreference();
        }
    }

    public String getCurrentVersion() {
        if (_preferences == null) {
            Log.e(LOG_TAG, "getCurrentVersion - _preferences is null");
            return "";
        }
        int localCurrentVersion = _preferences.getInt(VERSION_KEY_CURRENT_VERSION, 0);
        Log.i(LOG_TAG, "getCurrentVersion:" + localCurrentVersion);
        return localCurrentVersion + "";
    }

    public boolean isFirstRun() {
        if (_preferences == null) {
            Log.e(LOG_TAG, "isFirstRun the object - _preferences is null");
            return false;
        }
        if (_preferences.contains(VERSION_KEY_CURRENT_VERSION)) {
            Log.i(LOG_TAG, "game_version.xml exists");
            return false;
        } else {
            Log.i(LOG_TAG, "first run app");
            return true;
        }
    }

    private void _initGameVersionPreference() {
        if (_preferences == null) {
            Log.e(LOG_TAG, "_initGameVersionPreference the object - _preferences is null");
            return;
        }

        SharedPreferences.Editor editor = _preferences.edit();
        String originalVersionManifestStr = getFromAssets(FILE_NAME_PROJECT_MANIFEST);
        Log.i(LOG_TAG, "local original version.manifest:" + originalVersionManifestStr);
        try {
            JSONObject json = new JSONObject(originalVersionManifestStr);
            String originalVersionStr = json.getString(VERSION_KEY_CURRENT_VERSION);
            int originalVersionNm = Global.parseVersion(originalVersionStr);
            Log.i(LOG_TAG, "original version:" + originalVersionNm);

            editor.putInt(VERSION_KEY_CURRENT_VERSION, originalVersionNm);
            editor.putInt(VERSION_KEY_NEW_VERSION, originalVersionNm);
            editor.putInt(VERSION_KEY_NEW_VERSION_STATE, NEW_VERSION_STATE_COMPLETE);
            editor.putString(VERSION_KEY_NEW_VERSION_REMOTE_URL, "");

            editor.commit();
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "the original version.manifest cannot be read");
            ex.printStackTrace();
        }
    }

    /**
     * 从服务器端获取最新版本号和资源下载地址，与本地版本比对，判断是否需要更新
     *
     * @param remoteUrl
     * @param newVersionStr
     * @return
     */
    public boolean hasUpdate(String remoteUrl, String newVersionStr) {
        int localCurrentVersion = _preferences.getInt(VERSION_KEY_CURRENT_VERSION, 0);
        int newVersion = Global.parseVersion(newVersionStr);
        Log.i(LOG_TAG, "localCurrentVersionNm:" + localCurrentVersion);
        Log.i(LOG_TAG, "newVersionNm:" + newVersion);
        if (localCurrentVersion >= newVersion) {
            return false;
        } else {
            SharedPreferences.Editor editor = _preferences.edit();

            editor.putInt(VERSION_KEY_NEW_VERSION, newVersion);
            editor.putInt(VERSION_KEY_NEW_VERSION_STATE, NEW_VERSION_STATE_INCOMPLETE);
            editor.putString(VERSION_KEY_NEW_VERSION_REMOTE_URL, remoteUrl);

            editor.commit();

            String targetDir = preloadPath + HTTP_FOLDER + newVersion;
            File targetFolder = new File(targetDir);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }
            return true;
        }
    }

    /**
     * 从本地assets文件夹获取某个文件
     *
     * @param fileName
     * @return
     */
    public String getFromAssets(String fileName) {
        if (_am == null) {
            Log.e(LOG_TAG, "getFromAssets the object - _am is null");
            return null;
        }
        try {
            InputStreamReader inputReader = new InputStreamReader(_am.open(fileName));
            BufferedReader bufReader = new BufferedReader(inputReader);
            String line = "";
            String Result = "";
            while ((line = bufReader.readLine()) != null)
                Result += line;
            return Result;
        } catch (Exception e) {
            Log.e(LOG_TAG, "read res from assets failed:" + fileName);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 开始更新
     *
     * @param remoteUrl
     */
    public void startUpdate(String remoteUrl) {
        // create the new version folder
        int newVersion = _preferences.getInt(VERSION_KEY_NEW_VERSION, 0);

        // check if any new version res is published
        _downloadProjectManifest(remoteUrl, preloadPath + HTTP_FOLDER + newVersion);
    }

    /**
     * 从资源服务器地址下载project.manifest
     *
     * @param remoteUrl
     * @param targetDir
     */
    private void _downloadProjectManifest(final String remoteUrl, String targetDir) {
        final String projectFileName = targetDir + File.separator + GameUpdateComponent.FILE_NAME_PROJECT_MANIFEST;
        final File file = new File(projectFileName);
        delete(file);
        ensureDirectoryExists(file.getParentFile());

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                OutputStream output = null;
                URLConnection connection = null;
                boolean finish = false;

                try {
                    // download file
                    String url = remoteUrl + GameUpdateComponent.FILE_NAME_PROJECT_MANIFEST;
                    Log.d(LOG_TAG, "download project manifest:" + url);
                    connection = createConnectionToURL(url);
                    input = new BufferedInputStream(connection.getInputStream());
                    output = new BufferedOutputStream(new FileOutputStream(file, false));

                    final byte data[] = new byte[1024];
                    int count;
                    while ((count = input.read(data)) != -1) {
                        output.write(data, 0, count);
                    }

                    output.flush();
                    finish = true;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "download files failed:project.manifest");
                    e.printStackTrace();
                } finally {
                    try {
                        if (output != null) {
                            output.close();
                        }
                        if (input != null) {
                            input.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }

                if (finish) {
                    try {
                        InputStreamReader inputReader = new InputStreamReader(new FileInputStream(file));
                        BufferedReader bufReader = new BufferedReader(inputReader);
                        String line = "";
                        String result = "";
                        while ((line = bufReader.readLine()) != null)
                            result += line;
                        JSONObject newManifestJson = new JSONObject(result);
                        JSONObject localManifestJson = _getLocalProjectManifest();
                        _compareManifest(localManifestJson, newManifestJson);
                    } catch (Exception e) {
                        Message msg = new Message();
                        msg.what = GameUpdateComponent.PROGRESS_EXCEPTION;
                        msg.obj = "readDownloadProjectManifest";
                        _handler.sendMessage(msg);
                        Log.e(LOG_TAG, "read res from remote url failed:project.manifest");
                        e.printStackTrace();
                    }
                } else {
                    Message msg = new Message();
                    msg.what = GameUpdateComponent.PROGRESS_EXCEPTION;
                    msg.obj = "downloadProjectManifest";
                    _handler.sendMessage(msg);
                }
            }
        };
        new Thread(runnable).start();
    }

    /**
     * 获取本地当前版本project.manifest, 如果是第一升级，则取assets中的project.manifest
     *
     * @return
     */
    private JSONObject _getLocalProjectManifest() {
        int localCurrentVersion = _preferences.getInt(VERSION_KEY_CURRENT_VERSION, 0);
        String currentVersionFolder = preloadPath + HTTP_FOLDER + localCurrentVersion;
        File folder = new File(currentVersionFolder);
        JSONObject localManifestJson = null;
        if (folder.exists()) {
            File localManifest = new File(currentVersionFolder + File.separator + FILE_NAME_PROJECT_MANIFEST);
            try {
                InputStreamReader inputReader = new InputStreamReader(new FileInputStream(localManifest));
                BufferedReader bufReader = new BufferedReader(inputReader);
                String line = "";
                String result = "";
                while ((line = bufReader.readLine()) != null)
                    result += line;
                localManifestJson = new JSONObject(result);
            } catch (Exception e) {
                Log.e(LOG_TAG, "read res from local version url failed:project.manifest");
                e.printStackTrace();
            }
        } else {
            String originalManifestStr = getFromAssets(FILE_NAME_PROJECT_MANIFEST);
            try {
                localManifestJson = new JSONObject(originalManifestStr);
            } catch (Exception e) {
                Log.e(LOG_TAG, "read res from assets url failed:project.manifest");
                e.printStackTrace();
            }
        }
        return localManifestJson;
    }

    /**
     * 比较服务器版本与本地版本的project.manifest
     *
     * @param localManifestJson
     * @param newManifestJson
     */
    private void _compareManifest(JSONObject localManifestJson, JSONObject newManifestJson) {
        if (localManifestJson == null || newManifestJson == null) {
            Message msg = new Message();
            msg.what = GameUpdateComponent.PROGRESS_EXCEPTION;
            msg.obj = "compareManifest: null";
            _handler.sendMessage(msg);
            return;
        }
        List<ManifestFile> deleteItems = new ArrayList<ManifestFile>();
        List<ManifestFile> updateItems = new ArrayList<ManifestFile>();
        try {
            JSONObject localAssetsNode = localManifestJson.getJSONObject("assets");
            JSONObject newAssetsNode = newManifestJson.getJSONObject("assets");

            // find delete items
            Iterator<String> localKeys = localAssetsNode.keys();
            while (localKeys.hasNext()) {
                String key = localKeys.next();
                JSONObject nodeValue = localAssetsNode.getJSONObject(key);
                if (!newAssetsNode.has(key)) {
                    deleteItems.add(new ManifestFile(key, nodeValue.getInt("size"), nodeValue.getString("md5")));
                }
            }

            // find modified/increased items
            Iterator<String> newKeys = newAssetsNode.keys();
            while (newKeys.hasNext()) {
                String key = newKeys.next();
                boolean needUpdate = false;
                if (!localAssetsNode.has(key)) {
                    needUpdate = true;
                } else {
                    String localHash = localAssetsNode.getJSONObject(key).getString("md5");
                    String newHash = newAssetsNode.getJSONObject(key).getString("md5");
                    if (!localHash.equals(newHash)) {
                        needUpdate = true;
                    }
                }

                if (needUpdate) {
                    JSONObject nodeValue = newAssetsNode.getJSONObject(key);
                    updateItems.add(new ManifestFile(key, nodeValue.getInt("size"), nodeValue.getString("md5")));
                }
            }

            updateItems.add(new ManifestFile(FILE_NAME_VERSION_MANIFEST, 1000, newManifestJson.getString(VERSION_KEY_CURRENT_VERSION)));

            _installUpdate(deleteItems, updateItems);
        } catch (Exception ex) {
            Log.e(LOG_TAG, "read manifest failed");
            ex.printStackTrace();
            Message msg = new Message();
            msg.what = GameUpdateComponent.PROGRESS_EXCEPTION;
            msg.obj = "compareManifest";
            _handler.sendMessage(msg);
        }
    }

    /**
     * 安装更新，先复制本地版本资源到新版本文件夹，删除无用文件，下载有修改的和新增的文件
     *
     * @param deleteItems
     * @param updateItems
     */
    private void _installUpdate(List<ManifestFile> deleteItems, List<ManifestFile> updateItems) {
        _deleteItems = deleteItems;
        _updateItems = updateItems;

        // copy local current assets
        int newVersion = _preferences.getInt(VERSION_KEY_NEW_VERSION, 0);
        String targetDir = preloadPath + HTTP_FOLDER + newVersion;
        File targetFolder = new File(targetDir);
        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }

        if (!_copyCurrentVersionAssets(targetDir)) {
            Message msg = new Message();
            msg.what = GameUpdateComponent.PROGRESS_EXCEPTION;
            msg.obj = "copyCurrentVersionAssets";
            _handler.sendMessage(msg);
            return;
        }

        // delete
        if (deleteItems != null && deleteItems.size() > 0) {
            for (int i = 0; i < deleteItems.size(); i++) {
                File file = new File(targetDir + File.separator + deleteItems.get(i).name);
                delete(file);
                Log.i(LOG_TAG, "_installUpdate: delete file:" + file.getName());
            }
        } else {
            Log.i(LOG_TAG, "_installUpdate: no files to delete");
        }
        deleteItems = null;

        // download
        if (updateItems != null && updateItems.size() > 0) {
            String remoteUrl = _preferences.getString(VERSION_KEY_NEW_VERSION_REMOTE_URL, "");
            final UpdateLoaderWorker task = new UpdateLoaderWorker(targetDir, remoteUrl, updateItems, _handler);
            new Thread(task).start();
        } else {
            Log.i(LOG_TAG, "_installUpdate: no files to download");
        }
    }

    /**
     * 下载进度回调事件
     */
    private Handler _handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PROGRESS_UPDATE:
                    _uiActivity.showUpdateProgress(msg.arg1);
                    break;
                case PROGRESS_COMPLETE:
                    _uiActivity.showUpdateProgress(msg.arg1);
                    _updateComplete();
                    break;
                case PROGRESS_EXCEPTION:
                    _updateFailed((String) msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * 更新进度完成
     */
    private void _updateComplete() {
        Log.i(LOG_TAG, "update complete");

        _deleteItems = null;
        _updateItems = null;

        int currentVersion = _preferences.getInt(VERSION_KEY_CURRENT_VERSION, 0);

        SharedPreferences.Editor editor = _preferences.edit();
        editor.putInt(VERSION_KEY_CURRENT_VERSION, _preferences.getInt(VERSION_KEY_NEW_VERSION, 0));
        editor.putInt(VERSION_KEY_NEW_VERSION_STATE, NEW_VERSION_STATE_COMPLETE);

        editor.commit();

        delete(new File(preloadPath + HTTP_FOLDER + currentVersion));
        Log.i(LOG_TAG, "delete last version complete");

        _uiActivity.updateComplete();
    }

    /**
     * 更新失败
     *
     * @param step
     */
    private void _updateFailed(String step) {
        Log.e(LOG_TAG, "_updateFailed: " + step);
        _deleteItems = null;
        _updateItems = null;
        _uiActivity.updateFailed(step);
    }

    /**
     * 复制本地当前版本资源到新版本文件夹中
     *
     * @return 新版本文件夹地址
     */
    private boolean _copyCurrentVersionAssets(String targetFolderName) {
        int localCurrentVersion = _preferences.getInt(VERSION_KEY_CURRENT_VERSION, 0);
        String currentVersionFolder = preloadPath + HTTP_FOLDER + localCurrentVersion;
        File folder = new File(currentVersionFolder);

        if (folder.exists()) {
            // 以前更新过，复制当前版本文件夹内资源
            return _copyExternalAssets(currentVersionFolder, targetFolderName);
        } else {
            // 初次更新，复制assets文件内资源
            return _copyOriginalAssets("", targetFolderName);
        }
    }

    /**
     * 以前更新过，从外部存储中复制当前版本文件夹内资源到指定文件夹
     *
     * @param assetsDir
     * @param releaseDir
     */
    private boolean _copyExternalAssets(String assetsDir, String releaseDir) {
        try {
            File newFile = new File(releaseDir);
            if (!newFile.exists()) {
                if (!newFile.mkdirs()) {
                    Log.e(LOG_TAG, "copyFolder: cannot create directory.");
                    return false;
                }
            }
            File oldFile = new File(assetsDir);
            String[] files = oldFile.list();
            File temp;
            for (String file : files) {
                if (assetsDir.endsWith(File.separator)) {
                    temp = new File(assetsDir + file);
                } else {
                    temp = new File(assetsDir + File.separator + file);
                }

                if (temp.isDirectory()) {   //如果是子文件夹
                    _copyExternalAssets(assetsDir + File.separator + file, releaseDir + File.separator + file);
                } else if (temp.getName().equals(FILE_NAME_PROJECT_MANIFEST) || temp.getName().equals(FILE_NAME_VERSION_MANIFEST)) {
                    Log.i(LOG_TAG, "_copyExternalAssets doesn't copy manifest files:" + temp.getName());
                    continue;
                } else if (!temp.exists()) {
                    Log.e(LOG_TAG, "_copyExternalAssets:  oldFile not exist:" + temp.getName());
                    return false;
                } else if (!temp.isFile()) {
                    Log.e(LOG_TAG, "_copyExternalAssets:  oldFile not file:" + temp.getName());
                    return false;
                } else if (!temp.canRead()) {
                    Log.e(LOG_TAG, "_copyExternalAssets:  oldFile cannot read:" + temp.getName());
                    return false;
                } else {
                    FileInputStream fileInputStream = new FileInputStream(temp);
                    FileOutputStream fileOutputStream = new FileOutputStream(releaseDir + File.separator + temp.getName());
                    byte[] buffer = new byte[1024];
                    int byteRead;
                    while ((byteRead = fileInputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, byteRead);
                    }
                    fileInputStream.close();
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    Log.i(LOG_TAG, "_copyExternalAssets:  copy complete -." + temp.getName());
                }

            /* 如果不需要打log，可以使用下面的语句
            if (temp.isDirectory()) {   //如果是子文件夹
                copyFolder(assetsDir + File.separator + file, releaseDir + File.separator + file);
            } else if (temp.exists() && temp.isFile() && temp.canRead()) {
                FileInputStream fileInputStream = new FileInputStream(temp);
                FileOutputStream fileOutputStream = new FileOutputStream(releaseDir + File.separator + temp.getName());
                byte[] buffer = new byte[1024];
                int byteRead;
                while ((byteRead = fileInputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, byteRead);
                }
                fileInputStream.close();
                fileOutputStream.flush();
                fileOutputStream.close();
            }
            */
            }
            return true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "_copyExternalAssets: failed");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 复制assets文件夹内文件到指定文件夹内
     *
     * @param assetsDir
     * @param releaseDir
     */
    private boolean _copyOriginalAssets(String assetsDir, String releaseDir) {

        if (TextUtils.isEmpty(releaseDir)) {
            return false;
        } else if (releaseDir.endsWith(File.separator)) {
            releaseDir = releaseDir.substring(0, releaseDir.length() - 1);
        }

        if (TextUtils.isEmpty(assetsDir) || assetsDir.equals(File.separator)) {
            assetsDir = "";
        } else if (assetsDir.endsWith(File.separator)) {
            assetsDir = assetsDir.substring(0, assetsDir.length() - 1);
        }

        AssetManager assets = _am;
        try {
            String[] fileNames = assets.list(assetsDir);//只能获取到文件(夹)名,所以还得判断是文件夹还是文件
            if (fileNames.length > 0) {// is dir
                for (String name : fileNames) {
                    if (name.equals(FILE_NAME_PROJECT_MANIFEST) || name.equals(FILE_NAME_VERSION_MANIFEST)) {
                        Log.i(LOG_TAG, "_copyOriginalAssets doesn't copy manifest files:" + name);
                        continue;
                    }
                    if (!TextUtils.isEmpty(assetsDir)) {
                        name = assetsDir + File.separator + name;//补全assets资源路径
                    }

                    String[] childNames = assets.list(name);//判断是文件还是文件夹
                    if (!TextUtils.isEmpty(name) && childNames.length > 0) {
                        checkFolderExists(releaseDir + File.separator + name);
                        _copyOriginalAssets(name, releaseDir);//递归, 因为资源都是带着全路径,
                        //所以不需要在递归是设置目标文件夹的路径
                    } else {
                        InputStream is = assets.open(name);
                        _writeFile(releaseDir + File.separator + name, is);
                    }
                }
            } else {// is file
                InputStream is = assets.open(assetsDir);

                // 写入文件前, 需要提前级联创建好路径, 下面有代码贴出
                _writeFile(releaseDir + File.separator + assetsDir, is);
            }
            return true;
        } catch (Exception e) {
            Log.v(LOG_TAG, "copy original assets failed");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 创建文件
     *
     * @param fileName
     * @param in
     * @return
     * @throws IOException
     */
    private static boolean _writeFile(String fileName, InputStream in) throws IOException {

        boolean bRet = true;
        try {
            OutputStream os = new FileOutputStream(fileName);
            byte[] buffer = new byte[4112];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }

            in.close();
            in = null;

            os.flush();
            os.close();
            os = null;

            Log.i(LOG_TAG, "copy and release original assets:" + fileName);
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "copy and release original assets failed:" + fileName);
            e.printStackTrace();
            bRet = false;
        }
        return bRet;
    }

    public static void checkFolderExists(String path) {
        File file = new File(path);
        if ((file.exists() && !file.isDirectory()) || !file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 复制单个文件
     *
     * @param oldPath$Name String 原文件路径+文件名 如：data/user/0/com.test/files/abc.txt
     * @param newPath$Name String 复制后路径+文件名 如：data/user/0/com.test/cache/abc.txt
     * @return <code>true</code> if and only if the file was copied;
     * <code>false</code> otherwise
     */
    public static boolean copyFile(String oldPath$Name, String newPath$Name) {
        try {
            File oldFile = new File(oldPath$Name);
            if (!oldFile.exists()) {
                Log.e("--Method--", "copyFile:  oldFile not exist.");
                return false;
            } else if (!oldFile.isFile()) {
                Log.e("--Method--", "copyFile:  oldFile not file.");
                return false;
            } else if (!oldFile.canRead()) {
                Log.e("--Method--", "copyFile:  oldFile cannot read.");
                return false;
            }

        /* 如果不需要打log，可以使用下面的语句
        if (!oldFile.exists() || !oldFile.isFile() || !oldFile.canRead()) {
            return false;
        }
        */

            FileInputStream fileInputStream = new FileInputStream(oldPath$Name);    //读入原文件
            FileOutputStream fileOutputStream = new FileOutputStream(newPath$Name);
            byte[] buffer = new byte[1024];
            int byteRead;
            while ((byteRead = fileInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, byteRead);
            }
            fileInputStream.close();
            fileOutputStream.flush();
            fileOutputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if folder exists.
     * If not - it will be created with with all subdirectories.
     *
     * @param dir file object
     */
    public static void ensureDirectoryExists(File dir) {
        if (dir != null && (!dir.exists() || !dir.isDirectory())) {
            dir.mkdirs();
        }
    }

    /**
     * Delete file object.
     * If it is a folder - it will be deleted recursively will all content.
     *
     * @param fileOrDirectory file/directory to delete
     */
    public static void delete(File fileOrDirectory) {
        if (!fileOrDirectory.exists()) {
            return;
        }

        if (fileOrDirectory.isDirectory()) {
            File[] filesList = fileOrDirectory.listFiles();
            for (File child : filesList) {
                delete(child);
            }
        }

        final File to = new File(fileOrDirectory.getAbsolutePath() + System.currentTimeMillis());
        fileOrDirectory.renameTo(to);
        to.delete();
    }

    /**
     * Create URLConnection instance.
     *
     * @param url to what url
     * @return connection instance
     * @throws IOException when url is invalid or failed to establish connection
     */
    public static URLConnection createConnectionToURL(final String url) throws IOException {
        final URL connectionURL = URLUtility.stringToUrl(url);
        if (connectionURL == null) {
            throw new IOException("Invalid url format: " + url);
        }

        final URLConnection urlConnection = connectionURL.openConnection();
        urlConnection.setConnectTimeout(CONNECTION_TIMEOUT);
        urlConnection.setReadTimeout(READ_TIMEOUT);
        urlConnection.setRequestProperty("Connection", "Keep-Alive");
//        urlConnection.setRequestProperty("Accept","image/gif, image/jpeg, image/pjpeg, image/pjpeg, " +
//                "application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, " +
//                "application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, " +
//                "application/vnd.ms-powerpoint, application/msword,text/xml , text/html ,application/json ,*/*");
//        urlConnection.setRequestProperty("Accept-Language","zh-CN");
//        urlConnection.setRequestProperty("Accept-Charset","UTF-8");
//        urlConnection.setRequestProperty(
//                "User-Agent",
//                "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; " +
//                        ".NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30;" +
//                        " .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)");
//        urlConnection.setRequestProperty("Charset", "UTF-8");
//        if (requestHeaders != null) {
//            for (final Map.Entry<String, String> entry : requestHeaders.entrySet()) {
//                urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
//            }
//        }

        return urlConnection;
    }

    /**
     * Construct path from the given set of paths.
     *
     * @param paths list of paths to concat
     * @return resulting path
     */
    public static String getPath(String... paths) {
        StringBuilder builder = new StringBuilder();
        for (String path : paths) {
            builder.append(normalizeDashes(path));
        }
        return builder.toString();
    }

    private static String normalizeDashes(String path) {
        if (!path.startsWith(File.separator)) {
            path = File.separator + path;
        }

        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }
}

