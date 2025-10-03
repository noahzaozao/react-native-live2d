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
    
    // Called at initialization (when the drawing context is lost and recreated).
    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        try {
            LAppDelegate.getInstance().onSurfaceCreated()
        } catch (e: Exception) {
            Log.e("GLRenderer", "Error in onSurfaceCreated: ${e.message}", e)
        }
    }

    // Mainly called when switching between landscape and portrait.
    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        try {
            val delegate = LAppDelegate.getInstance()
            if (delegate.getView() != null) {
                Log.d("GLRenderer", "onSurfaceChanged: View exists, initializing with size ${width}x${height}")
                delegate.onSurfaceChanged(width, height)
            } else {
                Log.w("GLRenderer", "LAppDelegate view is null, skipping onSurfaceChanged - this may happen during initialization")
                // 延迟重试，给初始化更多时间
                Handler(Looper.getMainLooper()).postDelayed({
                    val retryDelegate = LAppDelegate.getInstance()
                    if (retryDelegate.getView() != null) {
                        Log.d("GLRenderer", "Retry successful: View now exists, calling onSurfaceChanged")
                        retryDelegate.onSurfaceChanged(width, height)
                    } else {
                        Log.w("GLRenderer", "Retry failed: View still null after delay")
                    }
                }, 100) // 延迟100ms重试
            }
        } catch (e: Exception) {
            Log.e("GLRenderer", "Error in onSurfaceChanged: ${e.message}", e)
        }
    }

    // Called repeatedly for drawing.
    override fun onDrawFrame(unused: GL10?) {
        try {
            val delegate = LAppDelegate.getInstance()
            if (delegate.getView() != null) {
                delegate.run()
            }
        } catch (e: Exception) {
            Log.e("GLRenderer", "Error in onDrawFrame: ${e.message}", e)
        }
    }
}