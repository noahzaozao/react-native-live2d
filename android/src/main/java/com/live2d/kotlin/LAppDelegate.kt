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
        // 使用 lazy 委托确保线程安全的单例初始化
        // 注意：属性名使用下划线前缀避免与 getInstance() 方法的 JVM 签名冲突
        private val _instance: LAppDelegate by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            LAppDelegate()
        }
        
        fun getInstance(): LAppDelegate = _instance

        /**
         * クラスのインスタンス（シングルトン）を解放する。
         * 注意：调用此方法前必须确保所有 GL 回调已停止
         * 警告：由于使用了 lazy 委托，实例在 JVM 生命周期内会持续存在
         * 这个方法主要用于标记关闭状态，而非真正释放实例
         */
        @Deprecated(
            message = "Singleton instance cannot be truly released due to lazy initialization",
            replaceWith = ReplaceWith("getInstance().markAsShuttingDown()")
        )
        fun releaseInstance() {
            synchronized(this) {
                _instance.isShuttingDown = true
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

    fun onPause() {
        currentModel = LAppLive2DManager.getInstance().getCurrentModel()
    }

    fun onStop() {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onStop: Cleaning up resources")
        }
        
        // 标记正在关闭，阻止新的操作
        markAsShuttingDown()
        
        view?.let {
            it.close()
            view = null  // 显式设置为null，避免状态不一致
        }
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onStop: Setting textureManager to null")
        }
        textureManager = null

        LAppLive2DManager.releaseInstance()
        CubismFramework.dispose()
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onStop: Cleanup completed")
        }
    }

    fun onDestroy() {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppDelegate.onDestroy: Releasing singleton instance")
        }
        releaseInstance()
    }

    fun onSurfaceCreated() {
        Log.d("LAppDelegate", "onSurfaceCreated: Starting OpenGL initialization")
        
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("LAppDelegate", "OpenGL error in onSurfaceCreated: $error")
        }

        CubismFramework.initialize()

        Log.d("LAppDelegate", "onSurfaceCreated: CubismFramework initialized")
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