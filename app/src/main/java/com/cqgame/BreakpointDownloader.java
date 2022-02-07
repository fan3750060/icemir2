package com.cqgame;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class BreakpointDownloader {

    // 总线程数
    private static final int THREAD_AMOUNT = 5;

    // 目标下载地址
    private URL url;

    // 本地文件
    private File dataFile;

    // 用来存储每个线程下载的进度的临时文件
    private File tempFile;

    // 每个线程要下载的长度
    private long threadLen;

    // 总共完成了多少
    public long totalFinish;

    // 服务端文件总长度
    public long totalLen;

    // 用来记录开始下载时的时间
    private long begin;

    public BreakpointDownloader(String file_path,String address) throws IOException {
        url = new URL(address);
        dataFile = new File(file_path, address.substring(address.lastIndexOf("/") + 1));
        tempFile = new File(dataFile.getAbsolutePath() + ".temp");
    }

    public void download() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000);

        totalLen = conn.getContentLength();
        threadLen = (totalLen + THREAD_AMOUNT - 1) / THREAD_AMOUNT;

        if (!dataFile.exists()) {
            RandomAccessFile raf = new RandomAccessFile(dataFile, "rws");
            raf.setLength(totalLen);
            raf.close();
        }

        if (!tempFile.exists()) {
            RandomAccessFile raf = new RandomAccessFile(tempFile, "rws");
            for (int i = 0; i < THREAD_AMOUNT; i++)
                raf.writeLong(0);
            raf.close();
        }

        for (int i = 0; i < THREAD_AMOUNT; i++) {
            new DownloadThread(i).start();
        }

        // 记录开始时间
        begin = System.currentTimeMillis();
    }

    private class DownloadThread extends Thread {
        // 用来标记当前线程是下载任务中的第几个线程
        private int id;

        public DownloadThread(int id) {
            this.id = id;
        }

        public void run() {
            try {
                RandomAccessFile tempRaf = new RandomAccessFile(tempFile, "rws");
                tempRaf.seek(id * 8);
                long threadFinish = tempRaf.readLong();
                synchronized(BreakpointDownloader.this) {
                    totalFinish += threadFinish;
                }

                long start = id * threadLen + threadFinish;
                long end = id * threadLen + threadLen - 1;
                System.out.println("线程" + id + ": " + start + "-" + end);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setRequestProperty("Range", "bytes=" + start + "-" + end);

                InputStream in = conn.getInputStream();
                RandomAccessFile dataRaf = new RandomAccessFile(dataFile, "rws");
                dataRaf.seek(start);

                byte[] buffer = new byte[1024 * 100];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    dataRaf.write(buffer, 0, len);
                    threadFinish += len;
                    tempRaf.seek(id * 8);
                    tempRaf.writeLong(threadFinish);
                    synchronized(BreakpointDownloader.this) {
                        totalFinish += len;
                    }
                }
                dataRaf.close();
                tempRaf.close();

                System.out.println("线程" + id + "下载完毕");
                // 如果已完成长度等于服务端文件长度(代表下载完成)
                if (totalFinish >= totalLen) {
                    System.out.println("下载完成, 耗时: " + (System.currentTimeMillis() - begin));
                    // 删除临时文件
                    tempFile.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
