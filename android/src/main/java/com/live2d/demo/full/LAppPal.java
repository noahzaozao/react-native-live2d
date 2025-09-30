/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.util.Log;
import com.live2d.demo.LAppDefine;
import com.live2d.sdk.cubism.core.ICubismLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;

public class LAppPal {
    /**
     * Logging Function class to be registered in the CubismFramework's logging function.
     */
    public static class PrintLogFunction implements ICubismLogger {
        @Override
        public void print(String message) {
            Log.d(TAG, message);
        }
    }

    // アプリケーションを中断状態にする。実行されるとonPause()イベントが発生する
    public static void moveTaskToBack() {
        LAppDelegate.getInstance().getActivity().moveTaskToBack(true);
    }

    // デルタタイムの更新
    public static void updateTime() {
        s_currentFrame = getSystemNanoTime();
        _deltaNanoTime = s_currentFrame - _lastNanoTime;
        _lastNanoTime = s_currentFrame;
    }

    // ファイルをバイト列として読み込む
    public static byte[] loadFileAsBytes(final String path) {
        InputStream fileData = null;
        try {
            fileData = LAppDelegate.getInstance().getActivity().getAssets().open(path);

            int fileSize = fileData.available();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);

            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File open error.");
            }

            return new byte[0];
        } finally {
            try {
                if (fileData != null) {
                    fileData.close();
                }
            } catch (IOException e) {
                e.printStackTrace();

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File open error.");
                }
            }
        }
    }

    // デルタタイム(前回フレームとの差分)を取得する
    public static float getDeltaTime() {
        // ナノ秒を秒に変換
        return (float) (_deltaNanoTime / 1000000000.0f);
    }

    // ファイルシステムからファイルをバイト列として読み込む
    public static byte[] loadFileFromFileSystem(final String filePath) {
        FileInputStream fileData = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File not found: " + filePath);
                }
                return new byte[0];
            }

            fileData = new FileInputStream(file);
            int fileSize = (int) file.length();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);

            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File read error: " + filePath);
            }

            return new byte[0];
        } finally {
            try {
                if (fileData != null) {
                    fileData.close();
                }
            } catch (IOException e) {
                e.printStackTrace();

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File close error: " + filePath);
                }
            }
        }
    }

    /**
     * Logging function
     *
     * @param message log message
     */
    public static void printLog(String message) {
        Log.d(TAG, message);
    }

    private static long getSystemNanoTime() {
        return System.nanoTime();
    }

    private static long s_currentFrame = 0L;
    private static long _lastNanoTime = getSystemNanoTime();
    private static long _deltaNanoTime = 0L;

    private static final String TAG = "SampleApp";

    private LAppPal() {}
}
