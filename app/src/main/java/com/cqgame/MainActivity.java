package com.cqgame;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import org.egret.egretnativeandroid.EgretNativeAndroid;
import org.egret.runtime.launcherInterface.INativePlayer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import static com.cqgame.Global.LOG_TAG;

public class MainActivity extends Activity {
    private final String TAG = "MainActivity";
    private EgretNativeAndroid nativeAndroid;
    private GameUpdateComponent guc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        _startEgret();

    }

    private void _startEgret() {
        guc = new GameUpdateComponent(getApplicationContext(), this);

        if (nativeAndroid != null) {
            nativeAndroid.exitGame();
        }

        nativeAndroid = new EgretNativeAndroid(this);
        if (!nativeAndroid.checkGlEsVersion()) {
            Toast.makeText(this, "This device does not support OpenGL ES 2.0.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        nativeAndroid.config.showFPS = false;
        nativeAndroid.config.fpsLogTime = 30;
        nativeAndroid.config.disableNativeRender = false;
        nativeAndroid.config.clearCache = false;
        nativeAndroid.config.loadingTimeout = 0;
        nativeAndroid.config.immersiveMode = true;
        nativeAndroid.config.useCutout = true;

        _setExternalInterfaces();

        nativeAndroid.config.preloadPath = guc.preloadPath;
        Log.i(LOG_TAG, "_startEgret: preload path:" + nativeAndroid.config.preloadPath);

        String versionPath = guc.preloadPath + GameUpdateComponent.HTTP_FOLDER + guc.getCurrentVersion() + File.separator + "game" + File.separator + "index.html";
        Log.i(LOG_TAG, "_startEgret: check version path exists:" + versionPath);
        File folder = new File(versionPath);
        if (!folder.exists()) {
            Log.i(LOG_TAG, "_startEgret: versionPath doesn't exist");
        } else {
            Log.d(LOG_TAG, "_startEgret: versionPath exists");
        }


        String url = "http://" + guc.getCurrentVersion() + "/game/index.html";

        Log.d(LOG_TAG, "_startEgret: init game url:" + url);
        if (!nativeAndroid.initialize(url)) {
            Toast.makeText(this, "Initialize native failed.",
                    Toast.LENGTH_LONG).show();
            return;
        }
//        guc.startUpdate("http://192.168.137.2:8087/");


        setContentView(nativeAndroid.getRootFrameLayout());
    }

    public void showUpdateProgress(int percent) {
        try {
            JSONObject result = new JSONObject();
            result.put("type", "update_progress");
            result.put("percent", percent);
            _sendToJs(result);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void updateComplete() {
        try {
            JSONObject result = new JSONObject();
            result.put("type", "ready_for_restart");
            _sendToJs(result);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void updateFailed(String step){
        try {
            JSONObject result = new JSONObject();
            result.put("type", "update_failed");
            result.put("step", step);
            _sendToJs(result);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        nativeAndroid.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nativeAndroid.resume();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            nativeAndroid.exitGame();
//        }

        return super.onKeyDown(keyCode, keyEvent);
    }

    /**
     * check if any new version res is published
     */
    private void _checkUpdate(JSONObject jsonData) throws JSONException {
        String remoteUrl = jsonData.getString("remote_url");
        String currentVersion = jsonData.getString("new_version");
        JSONObject result = new JSONObject();
        result.put("type", "check_update");
        if (guc.hasUpdate(remoteUrl, currentVersion)) {
            result.put("update", 1);
        } else {
            result.put("update", 0);
        }

        _sendToJs(result);
    }

    /**
     * start update progress
     */
    private void _startUpdate(JSONObject jsonData) throws JSONException {
        String remoteUrl = jsonData.getString("remote_url");

        guc.startUpdate(remoteUrl);
    }

    /**
     * handle the messages sent from js
     *
     * @param jsonData
     * @throws JSONException
     */
    private void _handleMessageFromJs(JSONObject jsonData) throws JSONException {
        switch (jsonData.getString("type")) {
            case "check_update":
                _checkUpdate(jsonData);
                break;
            case "start_update":
                _startUpdate(jsonData);
                break;
            case "start_after_update":
                Log.e(TAG, "start_after_update");
                _startEgret();
                break;
            case "get_version":
                _getCurrentVersion();
                break;
            default:
                break;
        }
    }

    private void _getCurrentVersion(){
        try {
            JSONObject result = new JSONObject();
            result.put("type", "current_version");
            result.put("version", Global.versionToString(Integer.parseInt(guc.getCurrentVersion())));
            _sendToJs(result);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void _setExternalInterfaces() {
        nativeAndroid.setExternalInterface("sendToNative", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.i(LOG_TAG, "Get message from js: " + message);
                try {
                    JSONObject json = new JSONObject(message);
                    _handleMessageFromJs(json);
                } catch (JSONException ex) {
                    Log.e(LOG_TAG, "read the message from js failed");
                    ex.printStackTrace();
                }
            }
        });
        nativeAndroid.setExternalInterface("@onState", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.e(TAG, "Get @onState: " + message);
            }
        });
        nativeAndroid.setExternalInterface("@onError", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.e(TAG, "Get @onError: " + message);
            }
        });
        nativeAndroid.setExternalInterface("@onJSError", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.e(TAG, "Get @onJSError: " + message);
            }
        });
    }

    private void _sendToJs(JSONObject json) {
        Log.i(LOG_TAG, "send message to js: " + json.toString());
        nativeAndroid.callExternalInterface("sendToJS", json.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
