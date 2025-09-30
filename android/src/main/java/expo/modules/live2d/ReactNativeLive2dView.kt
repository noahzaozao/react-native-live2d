package expo.modules.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import expo.modules.kotlin.viewevent.EventDispatcher
import com.live2d.demo.full.LAppDelegate
import com.live2d.demo.full.LAppLive2DManager
import com.live2d.demo.full.LAppModel
import com.live2d.demo.full.GLRenderer

class ReactNativeLive2dView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val container: FrameLayout = FrameLayout(context)
  private lateinit var glSurfaceView: GLSurfaceView
  private lateinit var renderer: GLRenderer
  private var modelPath: String? = null
  private var isInitialized = false
  private var isGLSetupComplete = false

  // Event dispatchers for each event type
  private val onModelLoaded by EventDispatcher()
  private val onError by EventDispatcher()
  private val onTap by EventDispatcher()
  private val onMotionFinished by EventDispatcher()

  companion object {
    private const val TAG = "ReactNativeLive2dView"
  }

  init {
    Log.d(TAG, "Initializing ReactNativeLive2dView")
    try {
      // 确保在主线程中初始化
      if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        initializeComponents()
      } else {
        // 如果不在主线程，切换到主线程
        post {
          initializeComponents()
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize ReactNativeLive2dView: ${e.message}", e)
      dispatchEvent("onError", mapOf(
        "error" to "INIT_ERROR",
        "message" to "Failed to initialize view: ${e.message}"
      ))
    }
  }
  
  private fun initializeComponents() {
    try {
      Log.d(TAG, "Initializing GL components")
      glSurfaceView = GLSurfaceView(context)
      renderer = GLRenderer()
      setupGLSurfaceView()
      setupContainer()
      isGLSetupComplete = true
      Log.d(TAG, "GL components initialized successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize GL components: ${e.message}", e)
      dispatchEvent("onError", mapOf(
        "error" to "GL_INIT_ERROR",
        "message" to "Failed to initialize OpenGL components: ${e.message}"
      ))
    }
  }

  private fun setupGLSurfaceView() {
    try {
      Log.d(TAG, "Setting up GLSurfaceView with basic configuration")
      
      // 首先初始化Live2D，确保在GLSurfaceView生命周期开始前完成
      initializeLive2D()
      
      // 基本的 OpenGL ES 2.0 设置
      glSurfaceView.setEGLContextClientVersion(2)
      Log.d(TAG, "Set EGL context client version to 2")
      
      // 设置渲染器
      glSurfaceView.setRenderer(renderer)
      Log.d(TAG, "Set renderer")
      
      // 设置渲染模式为连续渲染
      glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
      Log.d(TAG, "Set render mode to CONTINUOUSLY")
      
      Log.d(TAG, "GLSurfaceView setup completed successfully")
      
    } catch (e: Exception) {
      Log.e(TAG, "Failed to setup GLSurfaceView: ${e.message}", e)
      dispatchEvent("onError", mapOf(
        "error" to "GL_SETUP_ERROR",
        "message" to "Failed to setup OpenGL surface: ${e.message}"
      ))
      throw e
    }
  }

  private fun setupContainer() {
    container.addView(
      glSurfaceView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )
    addView(container)
  }

  private fun initializeLive2D() {
    if (!isInitialized) {
      try {
        Log.d(TAG, "Initializing Live2D delegate")
        
        // 获取Activity引用
        val activity = getActivity()
        if (activity == null) {
          Log.e(TAG, "Cannot get Activity reference for Live2D initialization")
          dispatchEvent("onError", mapOf(
            "error" to "INIT_ERROR",
            "message" to "Cannot get Activity reference"
          ))
          return
        }
        
        // 初始化 LAppDelegate
        val delegate = LAppDelegate.getInstance()
        Log.d(TAG, "Calling delegate.onStart with activity: ${activity.javaClass.simpleName}")
        delegate.onStart(activity)
        
        // 不要手动调用 onSurfaceCreated，让 GLSurfaceView 的渲染器自然调用
        // 这确保 OpenGL 上下文已经完全准备就绪
        Log.d(TAG, "LAppDelegate initialized, waiting for GLSurfaceView to call onSurfaceCreated")
        
        isInitialized = true
        // Log.d(TAG, "Live2D initialized successfully")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Live2D: ${e.message}", e)
        // 发送错误事件
        dispatchEvent("onError", mapOf(
          "error" to "INIT_ERROR",
          "message" to "Failed to initialize Live2D: ${e.message}"
        ))
      }
    }
  }
  
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

  fun loadModel(modelPath: String) {
    Log.d(TAG, "Loading model: $modelPath")

    if (!isGLSetupComplete) {
      Log.w(TAG, "GL components not ready, deferring model loading")
      post {
        loadModel(modelPath)
      }
      return
    }

    try {
      // 处理路径格式，统一转换为文件系统绝对路径
      val fullPath = when {
        modelPath.startsWith("file://") -> modelPath.substring(7)
        modelPath.startsWith("/") -> modelPath
        modelPath.startsWith("public/") -> {
          val relativePath = modelPath.substring(7)
          "${context.cacheDir.absolutePath}/$relativePath"
        }
        else -> "${context.cacheDir.absolutePath}/$modelPath"
      }

      this.modelPath = fullPath
      Log.d(TAG, "Resolved model path: $fullPath")

      // 确保在 GL 线程加载模型与创建纹理
      glSurfaceView.queueEvent {
        try {
          val manager = LAppLive2DManager.getInstance()
          loadModelFromFileSystem(fullPath, manager)
          glSurfaceView.requestRender()
          dispatchEvent("onModelLoaded", mapOf(
            "modelPath" to fullPath
          ))
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load model on GL thread: ${e.message}")
          dispatchEvent("onError", mapOf(
            "error" to "MODEL_LOAD_ERROR",
            "message" to "Failed to load model: ${e.message}"
          ))
        }
      }

    } catch (e: Exception) {
      Log.e(TAG, "Failed to load model: ${e.message}")
      dispatchEvent("onError", mapOf(
        "error" to "MODEL_LOAD_ERROR",
        "message" to "Failed to load model: ${e.message}"
      ))
    }
  }
  
  private fun loadModelFromFileSystem(modelPath: String, manager: LAppLive2DManager) {
    Log.d(TAG, "Loading model from file system: $modelPath")
    
    // 处理 file:// URI
    val actualPath = if (modelPath.startsWith("file://")) {
      modelPath.substring(7) // 移除 "file://" 前缀
    } else {
      modelPath
    }
    
    Log.d(TAG, "Actual file path: $actualPath")
    
    // 验证文件是否存在
    val file = java.io.File(actualPath)
    if (!file.exists()) {
      Log.e(TAG, "Model file does not exist: $actualPath")
      dispatchEvent("onError", mapOf(
        "error" to "FILE_NOT_FOUND",
        "message" to "Model file does not exist: $actualPath"
      ))
      return
    }
    
    Log.d(TAG, "Model file exists, size: ${file.length()} bytes")
    
    // 彻底清除现有模型 - 多次调用确保清理完成
    Log.d(TAG, "Releasing existing models (before: ${manager.getModelNum()} models)")
    manager.releaseAllModel()
    
    // 等待一帧确保清理完成
    try {
      Thread.sleep(16) // 等待约一帧的时间
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
    
    Log.d(TAG, "Models after release: ${manager.getModelNum()}")
    
    // 创建新模型并从文件系统加载
    Log.d(TAG, "Creating new LAppModel instance")
    val model = LAppModel()
    
    Log.d(TAG, "Loading model assets from file system")
    model.loadAssetsFromFileSystem(actualPath)
    
    // 检查模型是否成功加载
    if (model.model == null) {
      Log.e(TAG, "Failed to load model - model is null")
      dispatchEvent("onError", mapOf(
        "error" to "MODEL_LOAD_FAILED",
        "message" to "Failed to load model from: $actualPath"
      ))
      return
    }
    
    Log.d(TAG, "Model loaded successfully, adding to manager")
    
    // 将模型添加到管理器
    manager.addModel(model)
    
    Log.d(TAG, "Model added to manager, total models: ${manager.getModelNum()}")
    Log.d(TAG, "Model loaded from file system successfully")
  }

  fun startMotion(motionGroup: String, motionIndex: Int) {
    Log.d(TAG, "Starting motion: $motionGroup[$motionIndex]")
    
    if (!isGLSetupComplete) {
      Log.w(TAG, "GL components not ready, deferring motion start")
      post {
        startMotion(motionGroup, motionIndex)
      }
      return
    }
    
    try {
      val manager = LAppLive2DManager.getInstance()
      val currentModel = manager.getModel(0)
      
      if (currentModel != null) {
        currentModel.startMotion(motionGroup, motionIndex, 3) // 使用高优先级
        glSurfaceView.requestRender()
      } else {
        Log.w(TAG, "No model loaded for motion")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start motion: ${e.message}")
      dispatchEvent("onError", mapOf(
        "error" to "MOTION_ERROR",
        "message" to "Failed to start motion: ${e.message}"
      ))
    }
  }

  fun setExpression(expressionId: String) {
    Log.d(TAG, "Setting expression: $expressionId")
    
    if (!isGLSetupComplete) {
      Log.w(TAG, "GL components not ready, deferring expression setting")
      post {
        setExpression(expressionId)
      }
      return
    }
    
    try {
      val manager = LAppLive2DManager.getInstance()
      val currentModel = manager.getModel(0)
      
      if (currentModel != null) {
        currentModel.setExpression(expressionId)
        glSurfaceView.requestRender()
      } else {
        Log.w(TAG, "No model loaded for expression")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to set expression: ${e.message}")
      dispatchEvent("onError", mapOf(
        "error" to "EXPRESSION_ERROR",
        "message" to "Failed to set expression: ${e.message}"
      ))
    }
  }

  fun setAutoBlink(enabled: Boolean) {
    Log.d(TAG, "Setting auto blink: $enabled")
    
    try {
      val manager = LAppLive2DManager.getInstance()
      val currentModel = manager.getModel(0)
      
      if (currentModel != null) {
        // 这里需要根据实际的 LAppModel API 来设置自动眨眼
        // 暂时记录日志
        Log.d(TAG, "Auto blink setting: $enabled")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to set auto blink: ${e.message}")
    }
  }

  fun setAutoBreath(enabled: Boolean) {
    Log.d(TAG, "Setting auto breath: $enabled")
    
    try {
      val manager = LAppLive2DManager.getInstance()
      val currentModel = manager.getModel(0)
      
      if (currentModel != null) {
        // 这里需要根据实际的 LAppModel API 来设置自动呼吸
        // 暂时记录日志
        Log.d(TAG, "Auto breath setting: $enabled")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to set auto breath: ${e.message}")
    }
  }

  fun setScale(scale: Float) {
    Log.d(TAG, "Setting scale: $scale")
    
    if (!isGLSetupComplete) {
      Log.w(TAG, "GL components not ready, deferring scale setting")
      post {
        setScale(scale)
      }
      return
    }
    
    try {
      val delegate = LAppDelegate.getInstance()
      val view = delegate.getView()
      
      if (view != null) {
        // 这里需要根据实际的 LAppView API 来设置缩放
        // 暂时记录日志
        Log.d(TAG, "Scale setting: $scale")
        glSurfaceView.requestRender()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to set scale: ${e.message}")
    }
  }

  fun setPosition(x: Float, y: Float) {
    Log.d(TAG, "Setting position: ($x, $y)")
    
    if (!isGLSetupComplete) {
      Log.w(TAG, "GL components not ready, deferring position setting")
      post {
        setPosition(x, y)
      }
      return
    }
    
    try {
      val delegate = LAppDelegate.getInstance()
      val view = delegate.getView()
      
      if (view != null) {
        // 这里需要根据实际的 LAppView API 来设置位置
        // 暂时记录日志
        Log.d(TAG, "Position setting: ($x, $y)")
        glSurfaceView.requestRender()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to set position: ${e.message}")
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val x = event.x
    val y = event.y
    
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        Log.d(TAG, "Touch down at: ($x, $y)")
        
        try {
          val delegate = LAppDelegate.getInstance()
          delegate.onTouchBegan(x, y)
          
          // 发送点击事件
          dispatchEvent("onTap", mapOf(
            "x" to x,
            "y" to y
          ))
          
        } catch (e: Exception) {
          Log.e(TAG, "Failed to handle touch down: ${e.message}")
        }
      }
      
      MotionEvent.ACTION_MOVE -> {
        try {
          val delegate = LAppDelegate.getInstance()
          delegate.onTouchMoved(x, y)
        } catch (e: Exception) {
          Log.e(TAG, "Failed to handle touch move: ${e.message}")
        }
      }
      
      MotionEvent.ACTION_UP -> {
        try {
          val delegate = LAppDelegate.getInstance()
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
    Log.d(TAG, "View attached to window")
    
    try {
      glSurfaceView.onResume()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to resume GLSurfaceView: ${e.message}")
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    Log.d(TAG, "View detached from window")
    
    try {
      glSurfaceView.onPause()
      
      // 清理 Live2D 资源
      if (isInitialized) {
        val delegate = LAppDelegate.getInstance()
        delegate.onStop()
        delegate.onDestroy()
        isInitialized = false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to cleanup: ${e.message}")
    }
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