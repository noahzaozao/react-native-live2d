/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLES20.*
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework

class LAppDelegate private constructor() {
    
    companion object {
        /**
         * 单例实例，使用 Double-Checked Locking 模式确保线程安全
         * 使用 @Volatile 确保多线程可见性
         */
        @Volatile
        private var INSTANCE: LAppDelegate? = null
        
        /**
         * 获取单例实例
         * 使用 Double-Checked Locking 减少同步开销
         */
        fun getInstance(): LAppDelegate {
            // 第一次检查，避免不必要的同步
            val instance = INSTANCE
            if (instance != null) {
                return instance
            }
            
            // 同步块内创建实例
            return synchronized(this) {
                // 第二次检查，防止多线程同时创建
                val newInstance = INSTANCE
                if (newInstance != null) {
                    newInstance
                } else {
                    LAppDelegate().also { 
                        INSTANCE = it
                        if (LAppDefine.DEBUG_LOG_ENABLE) {
                            LAppPal.printLog("LAppDelegate: New instance created")
                        }
                    }
                }
            }
        }

        /**
         * 释放单例实例
         * 注意：调用此方法前必须确保所有 GL 回调已停止，且在主线程或 GL 线程外调用
         * 此方法会真正释放实例，允许垃圾回收
         */
        fun releaseInstance() {
            synchronized(this) {
                val instance = INSTANCE
                if (instance != null) {
                    if (LAppDefine.DEBUG_LOG_ENABLE) {
                        LAppPal.printLog("LAppDelegate: Releasing singleton instance")
                    }
                    
                    // 标记为关闭状态
                    instance.isShuttingDown = true
                    
                    // 清空引用，允许垃圾回收
                    INSTANCE = null
                    
                    if (LAppDefine.DEBUG_LOG_ENABLE) {
                        LAppPal.printLog("LAppDelegate: Instance released successfully")
                    }
                } else {
                    if (LAppDefine.DEBUG_LOG_ENABLE) {
                        LAppPal.printLog("LAppDelegate: No instance to release")
                    }
                }
            }
        }
    }

    private var activity: Activity? = null
    private val cubismOption = CubismFramework.Option()
    private var textureManager: LAppTextureManager? = null
    internal var view: LAppView? = null
    internal var windowWidth: Int = 0
    internal var windowHeight: Int = 0
    private var isActive: Boolean = true
    
    /**
     * 标记实例正在关闭，避免在清理过程中继续处理回调
     */
    @Volatile
    private var isShuttingDown: Boolean = false
    
    /**
     * 标记 GL 上下文是否已经初始化
     */
    @Volatile
    private var isGLContextInitialized: Boolean = false

    /**
     * モデルシーンインデックス
     */
    private var currentModel: Int = 0

    /**
     * クリックしているか
     */
    private var isCaptured: Boolean = false
    
    /**
     * マウスのX座標
     */
    private var mouseX: Float = 0f
    
    /**
     * マウスのY座標
     */
    private var mouseY: Float = 0f

    init {
        currentModel = 0

        // Set up Cubism SDK framework.
        cubismOption.logFunction = LAppPal.PrintLogFunction()
        cubismOption.loggingLevel = LAppDefine.cubismLoggingLevel

        CubismFramework.cleanUp()
        CubismFramework.startUp(cubismOption)
    }

    /**
     * アプリケーションを非アクティブにする
     */
    fun deactivateApp() {
        isActive = false
    }
    
    /**
     * 标记实例为关闭状态
     */
    fun markAsShuttingDown() {
        synchronized(this) {
            isShuttingDown = true
        }
    }
    
    /**
     * 检查是否正在关闭
     */
    fun isShuttingDown(): Boolean {
        synchronized(this) {
            return isShuttingDown
        }
    }

    fun onStart(activity: Activity) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onStart: Initializing textureManager")
        }
        
        // 重置关闭标志，允许重新使用
        synchronized(this) {
            isShuttingDown = false
        }
        
        textureManager = LAppTextureManager()
        view = LAppView()

        this.activity = activity

        LAppPal.updateTime()
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onStart: textureManager initialized: ${textureManager != null}")
        }
    }

    fun onPause() {}

    fun onStop() {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onStop: Cleaning up resources")
        }
        
        // 标记正在关闭，阻止新的操作
        markAsShuttingDown()
        
        // 标记 GL 上下文将要失效（下次 onSurfaceCreated 时需要重新创建资源）
        // 注意：不在这里重置，而是保留标志，让 onSurfaceCreated 检测到是恢复场景
        
        try {
            // 1. 先释放 Live2D 管理器（会释放所有模型）
            LAppLive2DManager.releaseInstance()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("LAppDelegate.onStop: LAppLive2DManager released")
            }
            
            // 2. 关闭视图（会释放着色器等）
            view?.let {
                it.close()
                view = null
            }
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("LAppDelegate.onStop: View closed")
            }
            
            // 3. 清空纹理管理器引用
            textureManager = null
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("LAppDelegate.onStop: TextureManager cleared")
            }
            
            // 4. 清空 Activity 引用
            activity = null
            
            // 5. 最后清理 Cubism Framework
            CubismFramework.dispose()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("LAppDelegate.onStop: CubismFramework disposed")
            }
        } catch (e: Exception) {
            LAppPal.printLog("LAppDelegate.onStop: Error during cleanup: ${e.message}")
            e.printStackTrace()
        }
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onStop: Cleanup completed")
        }
    }

    fun onDestroy() {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onDestroy: Starting destruction")
        }
        
        // 先执行 onStop 确保资源已清理
        onStop()
        
        // 然后释放单例实例
        releaseInstance()
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onDestroy: Destruction completed")
        }
    }

    fun onSurfaceCreated() {
        Log.d("LAppDelegate", "onSurfaceCreated: Starting OpenGL initialization")
        
        val wasInitialized = isGLContextInitialized
        
        // 设置 OpenGL 状态
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("LAppDelegate", "OpenGL error in onSurfaceCreated: $error")
        }

        // 初始化 Cubism Framework
        CubismFramework.initialize()
        
        if (wasInitialized) {
            Log.w("LAppDelegate", "onSurfaceCreated: GL context was lost, recreating resources")
            // GL 上下文丢失，需要重新创建资源
            recreateGLResources()
        } else {
            Log.d("LAppDelegate", "onSurfaceCreated: First time initialization")
        }
        
        isGLContextInitialized = true
        Log.d("LAppDelegate", "onSurfaceCreated: CubismFramework initialized")
    }
    
    /**
     * 重新创建 GL 资源（在 GL 上下文丢失后调用）
     * 注意：必须在 GL 线程调用
     */
    private fun recreateGLResources() {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate: Recreating GL resources after context loss")
        }
        
        try {
            // 1. 重新创建纹理管理器（旧的纹理 ID 已失效）
            textureManager = LAppTextureManager()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("LAppDelegate: TextureManager recreated")
            }
            
            // 2. 重新创建视图（包括着色器）
            view?.let { oldView ->
                try {
                    oldView.close()
                } catch (e: Exception) {
                    LAppPal.printLog("LAppDelegate: Error closing old view: ${e.message}")
                }
            }
            view = LAppView()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("LAppDelegate: LAppView recreated")
            }
            
            // 3. 通知 Live2D 管理器重新绑定所有模型的纹理
            val manager = LAppLive2DManager.getInstance()
            manager.notifyGLContextRecreated()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("LAppDelegate: Models notified of GL context recreation")
            }
            
        } catch (e: Exception) {
            LAppPal.printLog("LAppDelegate: Error recreating GL resources: ${e.message}")
            e.printStackTrace()
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        Log.d("LAppDelegate", "onSurfaceChanged: Starting OpenGL initialization")

        // 描画範囲指定
        GLES20.glViewport(0, 0, width, height)

        Log.d("LAppDelegate", "onSurfaceChanged: glViewport set to ${width}x$height")

        windowWidth = width
        windowHeight = height

        // AppViewの初期化 - 添加空指针检查
        view?.let {
            Log.d("LAppDelegate", "onSurfaceChanged: view is not null, initializing")

            it.initialize()
            it.initializeSprite()
            
        } ?: run {
            Log.e("LAppDelegate", "view is null in onSurfaceChanged, cannot initialize")
            return
        }

        // 模型加载现在通过文件系统路径进行，不再在此处自动加载
        Log.d("LAppDelegate", "onSurfaceChanged: Model loading is now handled via file system paths")

        isActive = true
    }

    fun run() {
        // 如果正在关闭，停止渲染
        if (isShuttingDown()) {
            return
        }
        
        // 時間更新
        LAppPal.updateTime()

        // 画面初期化
        glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glClearDepthf(1.0f)

        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     Log.d("LAppDelegate", "run: About to call view.render(), view is ${if (view != null) "not null" else "null"}")
        // }
        
        view?.render()

        // アプリケーションを非アクティブにする
        if (!isActive) {
            activity?.runOnUiThread {
                activity?.finishAndRemoveTask()
            }
        }
    }

    fun onTouchBegan(x: Float, y: Float) {
        if (view == null) {
            return
        }
        if (isShuttingDown()) return
        
        mouseX = x
        mouseY = y

        view?.let {
            isCaptured = true
            it.onTouchesBegan(mouseX, mouseY)
        }
    }

    fun onTouchEnd(x: Float, y: Float) {
        if (isShuttingDown()) return
        
        mouseX = x
        mouseY = y

        view?.let {
            isCaptured = false
            it.onTouchesEnded(mouseX, mouseY)
        }
    }

    fun onTouchMoved(x: Float, y: Float) {
        if (isShuttingDown()) return
        
        mouseX = x
        mouseY = y

        if (isCaptured) {
            view?.onTouchesMoved(mouseX, mouseY)
        }
    }

    // getter methods
    fun getActivity(): Activity? = activity

    fun getTextureManager(): LAppTextureManager? {
        return textureManager
    }

    fun getView(): LAppView? = view

    fun getWindowWidth(): Int = windowWidth

    fun getWindowHeight(): Int = windowHeight
}