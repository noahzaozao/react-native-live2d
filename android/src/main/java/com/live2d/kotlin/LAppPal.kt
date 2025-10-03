/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import android.util.Log
import com.live2d.sdk.cubism.core.ICubismLogger
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

object LAppPal {
    private const val TAG = "Live2DDemoApp"
    
    private var s_currentFrame = 0L
    private var _lastNanoTime = getSystemNanoTime()
    private var _deltaNanoTime = 0L

    /**
     * Logging Function class to be registered in the CubismFramework's logging function.
     */
    class PrintLogFunction : ICubismLogger {
        override fun print(message: String) {
            Log.d(TAG, message)
        }
    }

    // アプリケーションを中断状態にする。実行されるとonPause()イベントが発生する
    fun moveTaskToBack() {
        LAppDelegate.getInstance().getActivity()?.moveTaskToBack(true)
    }

    // デルタタイムの更新
    fun updateTime() {
        s_currentFrame = getSystemNanoTime()
        _deltaNanoTime = s_currentFrame - _lastNanoTime
        _lastNanoTime = s_currentFrame
    }

    // ファイルをバイト列として読み込む
    fun loadFileAsBytes(path: String): ByteArray {
        var fileData: InputStream? = null
        return try {
            fileData = LAppDelegate.getInstance().getActivity()?.assets?.open(path)
                ?: return byteArrayOf()

            val fileSize = fileData.available()
            val fileBuffer = ByteArray(fileSize)
            fileData.read(fileBuffer, 0, fileSize)

            fileBuffer
        } catch (e: IOException) {
            e.printStackTrace()

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File open error.")
            }

            byteArrayOf()
        } finally {
            try {
                fileData?.close()
            } catch (e: IOException) {
                e.printStackTrace()

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File close error.")
                }
            }
        }
    }

    // デルタタイム(前回フレームとの差分)を取得する
    fun getDeltaTime(): Float {
        // ナノ秒を秒に変換
        return (_deltaNanoTime / 1000000000.0f)
    }

    // ファイルシステムからファイルをバイト列として読み込む
    fun loadFileFromFileSystem(filePath: String): ByteArray {
        var fileData: FileInputStream? = null
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File not found: $filePath")
                }
                return byteArrayOf()
            }

            fileData = FileInputStream(file)
            val fileSize = file.length().toInt()
            val fileBuffer = ByteArray(fileSize)
            fileData.read(fileBuffer, 0, fileSize)

            fileBuffer
        } catch (e: IOException) {
            e.printStackTrace()

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File read error: $filePath")
            }

            byteArrayOf()
        } finally {
            try {
                fileData?.close()
            } catch (e: IOException) {
                e.printStackTrace()

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File close error: $filePath")
                }
            }
        }
    }

    /**
     * Logging function
     *
     * @param message log message
     */
    fun printLog(message: String) {
        Log.d(TAG, message)
    }

    private fun getSystemNanoTime(): Long {
        return System.nanoTime()
    }
}