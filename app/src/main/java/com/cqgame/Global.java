package com.cqgame;

import android.util.Log;

public class Global {
    public static final String LOG_TAG = "jj------Android------>";

    /**
     * ex:99.99.99 to 999999
     * @param ver
     * @return
     */
    public static int parseVersion(String ver){
        Log.i(LOG_TAG, "parseVersion param: " + ver);
        String[] arr = ver == null ? new String[]{"0"} : ver.split("\\.");
        int verNm = 0;

        for(int i = arr.length - 1; i >= 0; i--){
            if(i > 2){
                continue;
            }
            verNm += (Integer.parseInt(arr[i]) * Math.pow(100, 2 - i));
        }

        return verNm;
    }

    public static String versionToString(int ver){
        String str = Integer.toString(ver);
        String v1 = str.substring(str.length() - 2);
        String v2 = str.substring(str.length() - 4, str.length() - 2);
        String v3 = str.substring(0, str.length() - 4);
        StringBuffer sb = new StringBuffer();
        sb.append(Integer.parseInt(v3)).append(".")
                .append(Integer.parseInt(v2)).append(".")
                .append(Integer.parseInt(v1));
        return sb.toString();
    }

    public static String getFileDirByUrl(String urlString /*"http://game.com/game/index.html"*/) {
        int lastSlash = urlString.lastIndexOf('/');
        String server = urlString.substring(0, lastSlash + 1);
        return server.replaceFirst("://", "/").replace(":", "#0A");
    }
}
