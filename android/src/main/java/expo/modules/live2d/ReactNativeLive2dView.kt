package expo.modules.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import com.live2d.kotlin.GLRenderer
import com.live2d.kotlin.LAppDelegate
import com.live2d.kotlin.LAppDefine
import com.live2d.kotlin.LAppLive2DManager
import com.live2d.kotlin.LAppModel
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView

class ReactNativeLive2dView(context: Context, appContext: AppContext) :
        ExpoView(context, appContext) {
    private val container: FrameLayout = FrameLayout(context)
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: GLRenderer
    var modelPath: String? = null
        private set
    var isInitialized = false
        private set
    private var isGLSetupComplete = false
    private var pendingOperations = mutableSetOf<String>() // 防止重复的延迟操作
    private val motionQueue: ArrayDeque<Pair<String, Int>> = ArrayDeque()
    private val expressionQueue: ArrayDeque<String> = ArrayDeque()
    private var isMotionPlaying: Boolean = false
    private val motionHandler = android.os.Handler(context.mainLooper)
    
    companion object {
        private const val TAG = "ReactNativeLive2dView"
        
        // 队列管理常量
        private const val MAX_MOTION_QUEUE_SIZE = 3  // 最大动作队列长度
        private const val MAX_EXPRESSION_QUEUE_SIZE = 2  // 最大表情队列长度
        private const val TEXTURE_BIND_RETRY_DELAY_MS = 50L  // 纹理绑定重试延迟
    }

    // Event dispatchers for each event type
    private val onModelLoaded by EventDispatcher()
    private val onError by EventDispatcher()
    private val onTap by EventDispatcher()
    private val onMotionFinished by EventDispatcher()

    var live2dManager: LAppLive2DManager? = null
        private set

    var currentMotionGroup: String? = null
        private set
    var currentMotionIndex: Int? = null
        private set
    var currentExpressionId: String? = null
        private set
    var currentScale: Float? = null
        private set
    var currentPosition: Pair<Float, Float>? = null
        private set
    var currentAutoBlink: Boolean? = null
        private set
    var currentAutoBreath: Boolean? = null
        private set

    private val delegate: LAppDelegate by lazy { LAppDelegate.getInstance() }

    private fun getActivity(): android.app.Activity? {
        var context = this.context
        while (context is android.content.ContextWrapper) {
            if (context is android.app.Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    init {
        Log.d(TAG, "🎨 [Init] ReactNativeLive2dView initialization started (Thread: ${Thread.currentThread().name})")
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            Log.d(TAG, "init")
        }

        initializeComponents()

        // 关键修复：CubismFramework 必须在 GL 线程中初始化
        // 否则在 GL 线程中调用 getIdManager() 会返回 null
        val activity = getActivity()
        activity?.let { act ->
            Log.d(TAG, "🎨 [Init] Scheduling delegate.onStart() to run in GL thread")
            runAfterGLReady("delegate_onStart") {
                try {
                    Log.d(TAG, "🎨 [GL Thread] Calling delegate.onStart() (Thread: ${Thread.currentThread().name})")
                    delegate.onStart(act)
                    Log.d(TAG, "✅ [GL Thread] delegate.onStart() completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [GL Thread] delegate.onStart() failed: ${e.message}", e)
                }
            }
        } ?: run {
            Log.w(TAG, "⚠️ [Init] Activity is null, cannot start delegate")
        }
        
        Log.d(TAG, "✅ [Init] ReactNativeLive2dView initialization completed")
    }

    private fun initializeComponents() {
        try {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "initializeComponents")
            }

            live2dManager = LAppLive2DManager.getInstance()

            glSurfaceView = GLSurfaceView(context)
            renderer = if (::renderer.isInitialized) renderer else GLRenderer()

            container.addView(
                    glSurfaceView,
                    FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    )
            )
            addView(container)

            // 基本的 OpenGL ES 2.0 设置
            glSurfaceView.setEGLContextClientVersion(2)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "setupGLSurfaceView setEGLContextClientVersion 2")
            }

            // 设置渲染器
            glSurfaceView.setRenderer(renderer)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "setupGLSurfaceView glSurfaceView.setRenderer")
            }

            // 设置渲染模式为连续渲染
            glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            isGLSetupComplete = true

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "initializeComponents successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GL components: ${e.message}", e)
            dispatchEvent(
                    "onError",
                    mapOf(
                            "error" to "GL_INIT_ERROR",
                            "message" to "Failed to initialize OpenGL components: ${e.message}"
                    )
            )
        }
    }

    private fun bindTexturesWhenReady(model: LAppModel) {
        val handler = android.os.Handler(context.mainLooper)
        val runnable =
                object : Runnable {
                    override fun run() {
                        try {
                            if (model.checkRendererAvailability()) {
                                // 在 GL 线程执行真实的绑定操作
                                glSurfaceView.queueEvent {
                                    try {
                                        model.bindPendingTextures()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "bindPendingTextures error: ${e.message}")
                                    }
                                }
                                Log.d(TAG, "Renderer ready: bound textures for model")
                            } else {
                                handler.postDelayed(this, 50)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error binding textures: ${e.message}")
                        }
                    }
                }
        handler.post(runnable)
    }

    private fun runAfterGLReady(operationKey: String, action: () -> Unit) {
        Log.d(TAG, "⏳ [runAfterGLReady] Scheduling operation: '$operationKey'")
        
        if (pendingOperations.contains(operationKey)) {
            Log.d(
                    TAG,
                    "runAfterGLReady: operation '$operationKey' already pending, skip re-schedule"
            )
            return
        }
        pendingOperations.add(operationKey)

        val handler = android.os.Handler(context.mainLooper)
        var checkCount = 0
        val checker =
                object : Runnable {
                    override fun run() {
                        try {
                            checkCount++
                            if (isGLSetupComplete && ::glSurfaceView.isInitialized) {
                                Log.d(TAG, "✅ [runAfterGLReady] GL ready after $checkCount checks, executing '$operationKey'")
                                try {
                                    glSurfaceView.queueEvent {
                                        try {
                                            Log.d(TAG, "🚀 [GL Thread] Executing operation: '$operationKey'")
                                            action()
                                            Log.d(TAG, "✅ [GL Thread] Operation completed: '$operationKey'")
                                        } finally {
                                            pendingOperations.remove(operationKey)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "runAfterGLReady queueEvent error: ${e.message}")
                                    pendingOperations.remove(operationKey)
                                }
                            } else {
                                if (checkCount % 10 == 0) {
                                    Log.d(TAG, "⏳ [runAfterGLReady] Still waiting for GL (check #$checkCount)...")
                                }
                                handler.postDelayed(this, 16)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "runAfterGLReady error: ${e.message}")
                            pendingOperations.remove(operationKey)
                        }
                    }
                }
        handler.post(checker)
    }

    /**
     * Load a Live2D model from the specified file path
     * 
     * @param modelPath Full path to the model3.json file (supports file:// URI)
     * 
     * Thread safety: This method queues GL operations and is safe to call from any thread
     * 
     * Note: Duplicate calls with the same path will be skipped automatically
     */
    fun loadModel(modelPath: String) {
        Log.d(TAG, "📥 [loadModel] Received request to load: $modelPath")

        // 去重检查：如果已经加载了相同的模型路径，则跳过
        if (this.modelPath == modelPath && isInitialized) {
            Log.d(TAG, "✅ [loadModel] Model '$modelPath' already loaded, skipping")
            return
        }

        this.modelPath = modelPath

        try {
            Log.d(TAG, "🚀 [loadModel] Starting model loading process")
            
            // 检查 delegate 是否已准备好
            val delegateReady = delegate.getView() != null && delegate.getTextureManager() != null
            Log.d(TAG, "🔍 [loadModel] Delegate ready check: view=${delegate.getView() != null}, textureManager=${delegate.getTextureManager() != null}")

            // 如果 delegate 还未准备好，等待一下
            if (!delegateReady) {
                Log.w(TAG, "⚠️ [loadModel] Delegate not ready yet, scheduling retry...")
                val handler = android.os.Handler(context.mainLooper)
                handler.postDelayed({
                    Log.d(TAG, "🔄 [loadModel] Retrying model load after delegate initialization")
                    loadModel(modelPath)
                }, 100)
                return
            }

            // 确保在 GL 线程加载模型与创建纹理
            Log.d(TAG, "📤 [loadModel] Queueing model load to GL thread")

            glSurfaceView.queueEvent {
                try {
                    Log.d(TAG, "🎬 [GL Thread] Model load execution started")

                    val manager = LAppLive2DManager.getInstance()

                    loadModelFromFileSystem(modelPath, manager)

                    Log.d(TAG, "loadModel queueEvent loadModelFromFileSystem success")

                    glSurfaceView.requestRender()

                    // 标记模型已成功加载
                    isInitialized = true

                    dispatchEvent("onModelLoaded", mapOf("modelPath" to modelPath))

                    Log.d(TAG, "loadModel queueEvent onModelLoaded success")
                } catch (e: Exception) {

                    Log.e(TAG, "loadModel queueEvent onError: ${e.message}", e)

                    dispatchEvent(
                            "onError",
                            mapOf(
                                    "error" to "MODEL_LOAD_ERROR",
                                    "message" to "loadModel queueEvent onError: ${e.message}"
                            )
                    )
                }
            }
        } catch (e: Exception) {

            Log.e(TAG, "loadModel Failed to load model: ${e.message}")

            dispatchEvent(
                    "onError",
                    mapOf(
                            "error" to "MODEL_LOAD_ERROR",
                            "message" to "loadModel Failed to load model: ${e.message}"
                    )
            )
        }
    }

    private fun loadModelFromFileSystem(modelPath: String, manager: LAppLive2DManager) {
        Log.d(TAG, "loadModelFromFileSystem: $modelPath")

        // 处理 file:// URI
        val actualPath =
                if (modelPath.startsWith("file://")) {
                    modelPath.substring(7) // 移除 "file://" 前缀
                } else {
                    modelPath
                }

        Log.d(TAG, "loadModelFromFileSystem: actualPath: $actualPath")

        // 验证文件是否存在
        val file = java.io.File(actualPath)
        if (!file.exists()) {
            Log.e(TAG, "loadModelFromFileSystem: file not found $actualPath")
            dispatchEvent(
                    "onError",
                    mapOf(
                            "error" to "FILE_NOT_FOUND",
                            "message" to "loadModelFromFileSystem: file not found $actualPath"
                    )
            )
            return
        }

        Log.d(TAG, "loadModelFromFileSystem: file exists, size: ${file.length()} bytes")

        // 在加载新模型前，先清理旧模型，避免Expo Refresh后的状态不一致
        // Log.d(TAG, "loadModelFromFileSystem: clearing existing models before loading new one")
        // manager.releaseAllModel()

        Log.d(TAG, "loadModelFromFileSystem: before LAppModel")

        val model = LAppModel()

        Log.d(TAG, "loadModelFromFileSystem: before model.loadAssetsFromFileSystem: $actualPath")

        model.loadAssetsFromFileSystem(actualPath)

        // 检查模型是否成功加载
        if (model.model == null) {
            Log.e(TAG, "loadModelFromFileSystem: model is null")
            dispatchEvent(
                    "onError",
                    mapOf(
                            "error" to "MODEL_LOAD_FAILED",
                            "message" to "loadModelFromFileSystem: model is null"
                    )
            )
            return
        }

        Log.d(TAG, "loadModelFromFileSystem: after model.loadAssetsFromFileSystem: $actualPath")

        // 将模型添加到管理器
        manager.addModel(model)

        Log.d(
                TAG,
                "loadModelFromFileSystem: after manager.addModel: ${manager.getModelNum()} models"
        )

        // 尝试绑定延迟的纹理
        model.bindPendingTextures()

        // 标记初始化完成并应用可能的缓存视图状态
        isInitialized = true
        try {
            val view = delegate.getView()
            if (view != null) {
                // 应用 scale
                currentScale?.let {
                    try {
                        view.setViewScale(it)
                        Log.d(TAG, "Applied cached scale: $it")
                    } catch (e: Exception) {
                        Log.w(TAG, "apply cached scale failed: ${e.message}")
                    }
                }
                
                // 应用 position
                currentPosition?.let { pos ->
                    try {
                        view.setViewPosition(pos.first, pos.second)
                        Log.d(TAG, "Applied cached position: ${pos.first}, ${pos.second}")
                    } catch (e: Exception) {
                        Log.w(TAG, "apply cached position failed: ${e.message}")
                    }
                }
                
                // 应用 autoBlink（直接设置到模型）
                currentAutoBlink?.let { enabled ->
                    try {
                        // TODO: LAppModel 需要添加 autoBlink 支持
                        Log.d(TAG, "Cached autoBlink: $enabled (not yet applied)")
                    } catch (e: Exception) {
                        Log.w(TAG, "apply cached autoBlink failed: ${e.message}")
                    }
                }
                
                // 应用 autoBreath（直接设置到模型）
                currentAutoBreath?.let { enabled ->
                    try {
                        // TODO: LAppModel 需要添加 autoBreath 支持
                        Log.d(TAG, "Cached autoBreath: $enabled (not yet applied)")
                    } catch (e: Exception) {
                        Log.w(TAG, "apply cached autoBreath failed: ${e.message}")
                    }
                }
                
                // 应用 expression
                currentExpressionId?.let { expId ->
                    try {
                        model.setExpression(expId)
                        Log.d(TAG, "Applied cached expression: $expId")
                    } catch (e: Exception) {
                        Log.w(TAG, "apply cached expression failed: ${e.message}")
                    }
                }
                
                // 应用 motion
                if (currentMotionGroup != null && currentMotionIndex != null) {
                    try {
                        model.startMotion(currentMotionGroup!!, currentMotionIndex!!, LAppDefine.Priority.NORMAL.priority)
                        Log.d(TAG, "Applied cached motion: ${currentMotionGroup}[$currentMotionIndex]")
                    } catch (e: Exception) {
                        Log.w(TAG, "apply cached motion failed: ${e.message}")
                    }
                }
                
                glSurfaceView.requestRender()
            }
        } catch (e: Exception) {
            Log.w(TAG, "apply cached state error: ${e.message}")
        }

        // 延迟检查渲染器可用性，因为渲染器可能在模型添加到管理器后才初始化
        // bindTexturesWhenReady(model)
    }

    fun clearModel() {
        Log.d(TAG, "clearModel")

        // 1. 清理Live2D模型资源，避免Expo Refresh后的状态不一致
        try {
            val manager = LAppLive2DManager.getInstance()
            manager.releaseAllModel()
            Log.d(TAG, "onDetachedFromWindow: released all models")
        } catch (e: Exception) {
            Log.w(TAG, "onDetachedFromWindow: failed to release models: ${e.message}")
        }

        resetViewState()
    }

    fun clearAll() {
        Log.d(TAG, "clearAll: Starting full cleanup")

        // 1. 清理模型资源
        clearModel()

        // 2. 清理LAppView资源（包括sprite shader等）
        try {
            delegate.view?.close()
            Log.d(TAG, "clearAll: closed LAppView")
        } catch (e: Exception) {
            Log.w(TAG, "clearAll: failed to close LAppView: ${e.message}")
        }

        // 3. 清理纹理管理器（通过LAppDelegate的onStop方法）
        try {
            // 先在 GL 线程释放纹理与渲染相关资源
            try {
                val tm = delegate.getTextureManager()
                if (tm != null) {
                    glSurfaceView.queueEvent {
                        try {
                            tm.dispose()
                            Log.d(TAG, "clearAll: textures disposed on GL thread")
                        } catch (e: Exception) {
                            Log.w(TAG, "clearAll: failed to dispose textures: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "clearAll: failed to access texture manager: ${e.message}")
            }
            
            // 调用 onStop 做框架级清理
            delegate.onStop()
            Log.d(TAG, "clearAll: delegate.onStop() completed")
        } catch (e: Exception) {
            Log.w(TAG, "clearAll: failed to call delegate.onStop(): ${e.message}")
        }

        // 4. 暂停GLSurfaceView渲染
        try {
            glSurfaceView.onPause()
            Log.d(TAG, "clearAll: GLSurfaceView paused")
        } catch (e: Exception) {
            Log.w(TAG, "clearAll: failed to pause GLSurfaceView: ${e.message}")
        }

        // 5. 断开渲染器引用，帮助 GC
        try {
            // 在 GL 线程上清空 renderer 相关引用（若有内部状态）
            try {
                glSurfaceView.queueEvent { 
                    Log.d(TAG, "clearAll: GL tasks flushed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "clearAll: failed to queue GL flush event: ${e.message}")
            }
            // 注意：GLSurfaceView 一旦设置过渲染器就不能再设置为 null，只能暂停
            // 这里我们只暂停渲染，不尝试 unset renderer
            Log.d(TAG, "clearAll: GLSurfaceView renderer kept (cannot unset)")
        } catch (e: Exception) {
            Log.w(TAG, "clearAll: failed to flush GL tasks: ${e.message}")
        }

        // 6. 释放 LAppDelegate 单例（允许垃圾回收）
        try {
            LAppDelegate.releaseInstance()
            Log.d(TAG, "clearAll: LAppDelegate singleton released")
        } catch (e: Exception) {
            Log.w(TAG, "clearAll: failed to release LAppDelegate: ${e.message}")
        }

        // 7. 重置组件状态
        isGLSetupComplete = false
        live2dManager = null
        
        Log.d(TAG, "clearAll: Full cleanup completed")
    }

    /**
     * Start playing a motion animation
     * 
     * @param motionGroup Motion group name (e.g., "Idle", "TapBody")
     * @param motionIndex Motion index within the group (0-based)
     * 
     * Note: Motions are queued and played sequentially to prevent conflicts
     * Smart queue management: If queue is full, removes oldest non-playing motion
     */
    fun startMotion(motionGroup: String, motionIndex: Int) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            Log.d(TAG, "startMotion $motionGroup[$motionIndex]")
        }

        // 使用去重键避免快速重复触发
        val opKey = "motion:${motionGroup}#${motionIndex}"
        if (!pendingOperations.add(opKey)) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "startMotion de-duplicated: $opKey")
            }
            return
        }

        currentMotionGroup = motionGroup
        currentMotionIndex = motionIndex

        // 智能队列管理：如果队列已满，移除最旧的项（保留最新的用户意图）
        if (motionQueue.size >= MAX_MOTION_QUEUE_SIZE) {
            val removed = motionQueue.removeFirstOrNull()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "startMotion: Queue full, removed oldest motion: $removed")
            }
        }

        // 入队，统一串行播放
        motionQueue.addLast(Pair(motionGroup, motionIndex))
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            Log.d(TAG, "startMotion: Queue size: ${motionQueue.size}")
        }
        processNextMotionIfIdle()
        pendingOperations.remove(opKey)
    }

    fun setExpression(expressionId: String) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            Log.d(TAG, "setExpression $expressionId")
        }

        // 使用去重键避免快速重复触发
        val opKey = "expression:${expressionId}"
        if (!pendingOperations.add(opKey)) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "setExpression de-duplicated: $opKey")
            }
            return
        }

        currentExpressionId = expressionId
        
        // 智能队列管理：表情切换更倾向于立即响应，保留最新的几个请求即可
        if (expressionQueue.size >= MAX_EXPRESSION_QUEUE_SIZE) {
            // 清空旧队列，只保留最新的表情意图
            expressionQueue.clear()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                Log.d(TAG, "setExpression: Queue full, cleared old expressions")
            }
        }
        
        expressionQueue.addLast(expressionId)
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            Log.d(TAG, "setExpression: Queue size: ${expressionQueue.size}")
        }
        processExpressionQueue()
        pendingOperations.remove(opKey)
    }

    private fun processNextMotionIfIdle() {
        if (isMotionPlaying) return
        val item = motionQueue.removeFirstOrNull() ?: return

        val (group, index) = item
        val currentModel = live2dManager?.getModel(0)
        if (currentModel == null) {
            Log.w(TAG, "processNextMotionIfIdle: currentModel is null")
            return
        }

        try {
            isMotionPlaying = true
            currentModel.startMotion(group, index, 3)
            glSurfaceView.requestRender()

            // 使用分帧非阻塞方式监控动作完成
            monitorMotionCompletion(currentModel) {
                isMotionPlaying = false
                motionHandler.post { processNextMotionIfIdle() }
                motionHandler.post { processExpressionQueue() }
            }
        } catch (e: Exception) {
            isMotionPlaying = false
            Log.e(TAG, "processNextMotionIfIdle error: ${e.message}")
            dispatchEvent(
                    "onError",
                    mapOf(
                            "error" to "MOTION_ERROR",
                            "message" to "processNextMotionIfIdle: ${e.message}"
                    )
            )
        }
    }

    private fun monitorMotionCompletion(model: LAppModel, onFinished: () -> Unit) {
        glSurfaceView.queueEvent(
                object : Runnable {
                    override fun run() {
                        try {
                            if (model.isMainMotionFinished()) {
                                onFinished()
                            } else {
                                glSurfaceView.postDelayed(this, 20)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "monitorMotionCompletion error: ${e.message}")
                            glSurfaceView.postDelayed(this, 20)
                        }
                    }
                }
        )
    }

    private fun processExpressionQueue() {
        val currentModel = live2dManager?.getModel(0) ?: return
        try {
            // 取队列最后一个（最新意图），丢弃之前的，避免堆积
            val latest = expressionQueue.removeLastOrNull() ?: return
            expressionQueue.clear()
            currentModel.setExpression(latest)
            glSurfaceView.requestRender()
        } catch (e: Exception) {
            Log.e(TAG, "processExpressionQueue error: ${e.message}")
            dispatchEvent(
                    "onError",
                    mapOf(
                            "error" to "EXPRESSION_ERROR",
                            "message" to "processExpressionQueue: ${e.message}"
                    )
            )
        }
    }

    fun setAutoBlink(enabled: Boolean) {
        Log.d(TAG, "setAutoBlink $enabled")

        currentAutoBlink = enabled

        var currentModel = live2dManager?.getModel(0)
        if (currentModel == null) {
            Log.w(TAG, "setAutoBlink currentModel is null")
            return
        }

        try {
            Log.d(TAG, "TODO: Auto blink setting: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set auto blink: ${e.message}")
        }
    }

    fun setAutoBreath(enabled: Boolean) {
        Log.d(TAG, "setAutoBreath $enabled")

        currentAutoBreath = enabled

        var currentModel = live2dManager?.getModel(0)
        if (currentModel == null) {
            Log.w(TAG, "setAutoBreath currentModel is null")
            return
        }

        try {
            Log.d(TAG, "TODO: Auto breath setting: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set auto breath: ${e.message}")
        }
    }

    /**
     * Set the model's scale (uniform scaling)
     * 
     * @param scale Scaling factor (1.0 = 100%, must be > 0)
     * 
     * Thread safety: GL operations are queued automatically
     */
    fun setScale(scale: Float) {
        Log.d(TAG, "setScale $scale")

        currentScale = scale

        var currentModel = LAppLive2DManager.getInstance().getModel(0)
        if (currentModel == null) {
            Log.w(TAG, "setScale currentModel is null")
            return
        }

        try {
            val view = delegate.getView()

            if (view != null) {
                // 在 GL 线程设置缩放
                glSurfaceView.queueEvent {
                    try {
                        view.setViewScale(scale)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling view.setViewScale: ${e.message}")
                    }
                }
                glSurfaceView.requestRender()
            } else {
                Log.w(TAG, "LAppView is null when setting scale")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set scale: ${e.message}")
        }
    }

    /**
     * Set the model's position offset
     * 
     * @param x X offset in logical coordinates (center is 0, right is positive)
     * @param y Y offset in logical coordinates (center is 0, up is positive)
     * 
     * Thread safety: GL operations are queued automatically
     */
    fun setPosition(x: Float, y: Float) {
        Log.d(TAG, "setPosition ($x, $y)")

        currentPosition = Pair(x, y)

        var currentModel = live2dManager?.getModel(0)
        if (currentModel == null) {
            Log.w(TAG, "setPosition currentModel is null")
            return
        }

        try {
            val delegate = LAppDelegate.getInstance()
            val view = delegate.getView()

            if (view != null) {
                glSurfaceView.queueEvent {
                    try {
                        view.setViewPosition(x, y)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling view.setViewPosition: ${e.message}")
                    }
                }
                glSurfaceView.requestRender()
            } else {
                Log.w(TAG, "LAppView is null when setting position")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set position: ${e.message}")
        }
    }

    /** 重置视图状态，在模型释放后调用 */
    fun resetViewState() {
        Log.d(TAG, "resetViewState: resetting view state after model release")

        try {
            // 重置状态标志
            this.isInitialized = false
            this.modelPath = null

            // 清空队列
            this.motionQueue.clear()
            this.expressionQueue.clear()
            this.pendingOperations.clear()

            // 重置当前状态
            // currentMotionGroup = null
            // currentMotionIndex = null
            // currentExpressionId = null
            // currentScale = null
            // currentPosition = null
            // currentAutoBlink = null
            // currentAutoBreath = null

            // 停止动作播放
            this.isMotionPlaying = false

            Log.d(TAG, "resetViewState: view state reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "resetViewState: failed to reset view state: ${e.message}")
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "Touch down at: ($x, $y)")

                try {
                    delegate.onTouchBegan(x, y)

                    // 发送点击事件
                    dispatchEvent("onTap", mapOf("x" to x, "y" to y))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle touch down: ${e.message}")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                try {
                    delegate.onTouchMoved(x, y)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle touch move: ${e.message}")
                }
            }
            MotionEvent.ACTION_UP -> {
                try {
                    delegate.onTouchEnd(x, y)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle touch up: ${e.message}")
                }
            }
        }

        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        Log.d(TAG, "onAttachedToWindow: View attached to window")

        // 恢复 GLSurfaceView 渲染
        try {
            if (::glSurfaceView.isInitialized) {
                glSurfaceView.onResume()
                Log.d(TAG, "onAttachedToWindow: GLSurfaceView resumed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "onAttachedToWindow: Failed to resume GLSurfaceView: ${e.message}")
        }
        
        // 如果之前有加载的模型路径，尝试恢复模型
        modelPath?.let { path ->
            if (!isInitialized) {
                Log.d(TAG, "onAttachedToWindow: Restoring model from path: $path")
                // 延迟一帧，确保 GL 上下文已准备好
                post {
                    loadModel(path)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        Log.d(TAG, "onDetachedFromWindow")

        clearAll()

        Log.d(TAG, "onDetachedFromWindow: cleanup completed")
    }

    private fun dispatchEvent(eventName: String, params: Map<String, Any>) {
        try {
            // 使用 Expo 的事件分发机制
            when (eventName) {
                "onModelLoaded" -> onModelLoaded(params)
                "onError" -> onError(params)
                "onTap" -> onTap(params)
                "onMotionFinished" -> onMotionFinished(params)
                else -> Log.w(TAG, "Unknown event: $eventName")
            }
            Log.d(TAG, "Event dispatched: $eventName, Params: $params")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch event: ${e.message}")
        }
    }
}
