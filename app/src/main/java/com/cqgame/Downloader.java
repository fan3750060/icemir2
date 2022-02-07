package com.cqgame;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import android.os.Environment;

public class Downloader {

    //进行下载的线程数量
    public static final int THREADCOUNT = 20;

    //下载完成的线程数量
    public int finishedThread = 0;

    public int max_length = 0;

    public int now_length = 0;

    public void download(String fileName,String path,String dir) {
        try {
            URL url = new URL(path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(8000);

            //请求成功时的响应码为200(注意响应码为200)
            if (conn.getResponseCode() == 200) {
                // 拿到需要下载的文件的大小
                int length = conn.getContentLength();
                max_length = length;
                System.out.println("文件大小:" + length);

                // 先占个位置，生成临时文件
                File file = new File(fileName);
                RandomAccessFile raf = new RandomAccessFile(file, "rwd");
                raf.setLength(length);

                raf.close();

                //每个线程应该下载的长度(最后一个线程除外，因为不一定能够平分)
                int size = length / THREADCOUNT;
                for (int i = 0; i < THREADCOUNT; i++) {
                    // 1.确定每个线程的下载区间
                    // 2.开启对应的子线程
                    int startIndex = i * size;  //开始位置
                    int endIndex = (i + 1) * size - 1;  //结束位置
                    // 最后一个线程
                    if (i == THREADCOUNT - 1) {
                        endIndex = length - 1;
                    }
                    System.out.println("第" + (i + 1) + "个线程的下载区间为:"+ startIndex + "-" + endIndex);
                    new DownloadThread(startIndex, endIndex, path, i,fileName,dir).start();
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class DownloadThread extends Thread{
        private int lastProgress;
        private int startIndex,endIndex,threadId;
        private String path;
        private String fileName;
        private String dir;

        public DownloadThread(int startIndex,int endIndex,String path,int threadId,String fileName,String dir) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.path = path;
            this.threadId = threadId;
            this.fileName = fileName;
            this.dir = dir;
        }
        @Override
        public void run() {
            try {
                //建立进度临时文件，其实这时还没有创建。当往文件里写东西的时候才创建。
                File progressFile = new File(dir,threadId+".txt");

                //判断临时文件是否存在，存在表示已下载过，没下完而已
                if (progressFile.exists()) {
                    FileInputStream fis = new FileInputStream(progressFile);
                    BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                    br.close();
                    fis.close();
                }

                //真正请求数据
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(8000);

                //设置本次http请求所请求的数据的区间(这是需要服务器那边支持断点)，格式需要这样写，不能写错
                conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                //请求部分数据，响应码是206(注意响应码是206)
                if (conn.getResponseCode() == 206) {

                    //此时流中只有1/3原数据
                    InputStream is = conn.getInputStream();
                    File file = new File(fileName);
                    RandomAccessFile raf = new RandomAccessFile(file, "rwd");

                    //把文件的写入位置移动至startIndex
                    raf.seek(startIndex);

                    byte[] b = new byte[1024];
                    int len = 0;
                    int total = lastProgress;
                    while ((len = is.read(b)) != -1) {
                        raf.write(b, 0, len);
                        total += len;

                        now_length+=len;
//                        System.out.println("线程" + threadId + "下载了" + total);

                        //生成一个专门用来记录下载进度的临时文件
                        RandomAccessFile progressRaf = new RandomAccessFile(progressFile, "rwd");

                        //每次读取流里数据之后，同步把当前线程下载的总进度写入进度临时文件中
                        progressRaf.write((total + "").getBytes());
                        progressRaf.close();
                    }
                    System.out.println("线程" + threadId + "下载完成");
                    raf.close();

                    //每完成一个线程就+1
                    finishedThread ++;

                    //等标志位等于线程数的时候就说明线程全部完成了
                    if (finishedThread == THREADCOUNT) {
                        for (int i = 0; i < finishedThread; i++) {
                            //将生成的进度临时文件删除
                            File f = new File(dir,i + ".txt");
                            f.delete();
                        }
                    }
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
