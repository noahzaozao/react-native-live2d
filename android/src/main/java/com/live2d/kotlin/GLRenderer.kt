/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {

    @Volatile
    private var isShuttingDown = false
    
    // 每次获取最新的 delegate 实例，而不是缓存
    private fun getDelegate(): LAppDelegate? {
        return try {
            if (isShuttingDown) {
                null
            } else {
                LAppDelegate.getInstance()
            }
        } catch (e: Exception) {
            Log.w("GLRenderer", "Failed to get delegate instance: ${e.message}")
            null
        }
    }
    
    /**
     * 通知渲染器即将关闭，停止所有渲染操作
     */
    fun shutdown() {
        isShuttingDown = true
        Log.d("GLRenderer", "Renderer shutdown initiated")
    }
    
    /**
     * 重置渲染器状态，允许重新使用
     */
    fun reset() {
        isShuttingDown = false
        Log.d("GLRenderer", "Renderer reset, ready for reuse")
    }
    
    // Called at initialization (when the drawing context is lost and recreated).
    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        if (isShuttingDown) {
            Log.d("GLRenderer", "onSurfaceCreated skipped: renderer is shutting down")
            return
        }
        
        Log.d("GLRenderer", "onSurfaceCreated")
        try {
            getDelegate()?.onSurfaceCreated()
        } catch (e: Exception) {
            Log.e("GLRenderer", "Error in onSurfaceCreated: ${e.message}", e)
        }
    }

    // Mainly called when switching between landscape and portrait.
    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        if (isShuttingDown) {
            Log.d("GLRenderer", "onSurfaceChanged skipped: renderer is shutting down")
            return
        }
        
        Log.d("GLRenderer", "onSurfaceChanged: ${width}x${height}")
        try {
            val delegate = getDelegate()
            if (delegate?.getView() != null) {
                Log.d("GLRenderer", "onSurfaceChanged: View exists, initializing with size ${width}x${height}")
                delegate.onSurfaceChanged(width, height)
            } else {
                Log.w("GLRenderer", "LAppDelegate view is null, skipping onSurfaceChanged - this may happen during initialization")
                // 延迟重试，给初始化更多时间
                retrySurfaceChanged(width, height)
            }
        } catch (e: Exception) {
            Log.e("GLRenderer", "Error in onSurfaceChanged: ${e.message}", e)
        }
    }

    private fun retrySurfaceChanged(width: Int, height: Int, attempts: Int = 3) {
        var remaining = attempts
        val handler = Handler(Looper.getMainLooper())
        
        val runnable = object : Runnable {
            override fun run() {
                if (isShuttingDown) {
                    Log.d("GLRenderer", "Retry cancelled: renderer is shutting down")
                    return
                }
                
                val delegate = getDelegate()
                if (delegate?.getView() != null) {
                    Log.d("GLRenderer", "Retry successful: View now exists, calling onSurfaceChanged")
                    delegate.onSurfaceChanged(width, height)
                } else if (remaining > 0) {
                    remaining--
                    handler.postDelayed(this, 100)
                } else {
                    Log.w("GLRenderer", "Retry failed: View still null after $attempts attempts")
                }
            }
        }
        
        handler.post(runnable)
    }

    // Called repeatedly for drawing.
    override fun onDrawFrame(unused: GL10?) {
        if (isShuttingDown) {
            // 静默跳过，避免日志刷屏
            return
        }
        
        try {
            val delegate = getDelegate()
            if (delegate?.getView() != null) {
                delegate.run()
            } else {
                // 只在非关闭状态下记录警告
                if (!isShuttingDown) {
                    Log.w("GLRenderer", "onDrawFrame skipped: delegate view is null")
                }
            }
        } catch (e: Exception) {
            if (!isShuttingDown) {
                Log.e("GLRenderer", "Error in onDrawFrame: ${e.message}", e)
            }
        }
    }
}