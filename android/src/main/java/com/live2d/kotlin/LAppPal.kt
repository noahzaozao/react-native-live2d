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
    private const val MAX_FILE_SIZE = Int.MAX_VALUE.toLong() // 2GB limit for ByteArray
    
    // 使用 @Volatile 确保多线程可见性
    @Volatile private var currentFrame = 0L
    @Volatile private var lastNanoTime = getSystemNanoTime()
    @Volatile private var deltaNanoTime = 0L

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
    @Synchronized
    fun updateTime() {
        currentFrame = getSystemNanoTime()
        deltaNanoTime = currentFrame - lastNanoTime
        lastNanoTime = currentFrame
    }

    // ファイルをバイト列として読み込む
    fun loadFileAsBytes(path: String): ByteArray {
        return try {
            val inputStream = LAppDelegate.getInstance().getActivity()?.assets?.open(path)
            if (inputStream == null) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("Failed to open asset file: $path (Activity or Assets is null)")
                }
                return byteArrayOf()
            }
            
            inputStream.use { fileData ->
                val fileBuffer = ByteArray(fileData.available())
                fileData.read(fileBuffer)
                fileBuffer
            }
        } catch (e: IOException) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File read error: $path - ${e.message}")
                e.printStackTrace()
            }
            byteArrayOf()
        }
    }

    // デルタタイム(前回フレームとの差分)を取得する
    fun getDeltaTime(): Float {
        // ナノ秒を秒に変換
        return (deltaNanoTime / 1000000000.0f)
    }

    // ファイルシステムからファイルをバイト列として読み込む
    fun loadFileFromFileSystem(filePath: String): ByteArray {
        return try {
            val file = File(filePath)
            
            // 文件存在性检查
            if (!file.exists()) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File not found: $filePath")
                }
                return byteArrayOf()
            }

            // 文件大小检查（防止整数溢出）
            val fileLength = file.length()
            if (fileLength > MAX_FILE_SIZE) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File too large: $filePath (${fileLength} bytes, max: $MAX_FILE_SIZE bytes)")
                }
                return byteArrayOf()
            }

            // 使用 use 函数自动关闭流
            FileInputStream(file).use { fileData ->
                val fileBuffer = ByteArray(fileLength.toInt())
                fileData.read(fileBuffer)
                fileBuffer
            }
        } catch (e: IOException) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File read error: $filePath - ${e.message}")
                e.printStackTrace()
            }
            byteArrayOf()
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
    
    /**
     * Logging function with lazy evaluation
     * Only constructs the message string if debug logging is enabled
     *
     * @param messageProvider lambda that provides the log message
     */
    fun printLogLazy(messageProvider: () -> String) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            Log.d(TAG, messageProvider())
        }
    }

    private fun getSystemNanoTime(): Long {
        return System.nanoTime()
    }
}