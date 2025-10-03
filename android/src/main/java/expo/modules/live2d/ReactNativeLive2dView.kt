package expo.modules.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import expo.modules.kotlin.viewevent.EventDispatcher
import com.live2d.kotlin.LAppDelegate
import com.live2d.kotlin.LAppLive2DManager
import com.live2d.kotlin.LAppModel
import com.live2d.kotlin.GLRenderer

class ReactNativeLive2dView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val container: FrameLayout = FrameLayout(context)
  private lateinit var glSurfaceView: GLSurfaceView
  private lateinit var renderer: GLRenderer
  var modelPath: String? = null
    private set
  var isInitialized = false
    private set
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
    Log.d(TAG, "init")
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
      Log.d(TAG, "initializeComponents")
      glSurfaceView = GLSurfaceView(context)
      renderer = GLRenderer()
      setupGLSurfaceView()
      setupContainer()
      isGLSetupComplete = true
      Log.d(TAG, "initializeComponents successfully")
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
      Log.d(TAG, "setupGLSurfaceView")
      
      // 基本的 OpenGL ES 2.0 设置
      glSurfaceView.setEGLContextClientVersion(2)
      Log.d(TAG, "setupGLSurfaceView setEGLContextClientVersion 2")
      
      // 首先初始化Live2D，确保在GLSurfaceView生命周期开始前完成
      initializeLive2D()
      
      // 验证初始化是否成功
      if (!isInitialized) {
        Log.e(TAG, "Live2D initialization failed, cannot setup GLSurfaceView")
        dispatchEvent("onError", mapOf(
          "error" to "GL_SETUP_ERROR",
          "message" to "Live2D initialization failed"
        ))
        return
      }
      
      // 设置渲染器
      glSurfaceView.setRenderer(renderer)
      Log.d(TAG, "setupGLSurfaceView glSurfaceView.setRenderer")
      
      // 设置渲染模式为连续渲染
      glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

      Log.d(TAG, "setupGLSurfaceView glSurfaceView.renderMode RENDERMODE_CONTINUOUSLY")
      
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
    Log.d(TAG, "initializeLive2D")
    if (!isInitialized) {
      try {
        Log.d(TAG, "initializeLive2D isInitialized false")
        
        // 获取Activity引用
        val activity = getActivity()
        if (activity == null) {
          Log.e(TAG, "initializeLive2D getActivity() is null")
          dispatchEvent("onError", mapOf(
            "error" to "INIT_ERROR",
            "message" to "initializeLive2D getActivity() is null"
          ))
          return
        }
        
        // 确保之前的实例被正确清理
        val delegate = LAppDelegate.getInstance()
        Log.d(TAG, "initializeLive2D LAppDelegate.getInstance")
        
        // 如果view存在但状态不正确，先清理
        val currentView = delegate.getView()
        if (currentView != null) {
          Log.d(TAG, "initializeLive2D currentView from delegate.getView success")
          try {
            delegate.onStop()
          } catch (e: Exception) {
            Log.w(TAG, "initializeLive2D delegate.onStop failed: ${e.message}")
          }
        }
        
        Log.d(TAG, "initializeLive2D delegate.onStart activity: ${activity.javaClass.simpleName}")
        delegate.onStart(activity)
        
        // 验证view是否被正确创建
        val newView = delegate.getView()
        if (newView == null) {
          Log.e(TAG, "initializeLive2D delegate.getView newView is null")
          dispatchEvent("onError", mapOf(
            "error" to "INIT_ERROR",
            "message" to "initializeLive2D delegate.getView newView is null"
          ))
          return
        }
        
        Log.d(TAG, "initializeLive2D delegate.getView success")
        
        isInitialized = true

        Log.d(TAG, "initializeLive2D isInitialized true")
        
      } catch (e: Exception) {
        Log.e(TAG, "initializeLive2D Failed to initialize Live2D: ${e.message}", e)
        // 发送错误事件
        dispatchEvent("onError", mapOf(
          "error" to "INIT_ERROR",
          "message" to "initializeLive2D Failed to initialize Live2D: ${e.message}"
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
    Log.d(TAG, "loadModel $modelPath")
    Log.d(TAG, "loadModel called - isGLSetupComplete: $isGLSetupComplete, isInitialized: $isInitialized")

    if (!isGLSetupComplete) {
      Log.w(TAG, "loadModel isGLSetupComplete false")
      post {
        loadModel(modelPath)
      }
      return
    }

    Log.d(TAG, "loadModel isGLSetupComplete true")

    try {
      Log.d(TAG, "loadModel try Starting model loading process")
      
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
      Log.d(TAG, "loadModel modelPath: $fullPath")

      // 确保在 GL 线程加载模型与创建纹理
      Log.d(TAG, "loadModel before queueEvent")
      glSurfaceView.queueEvent {
        try {
          Log.d(TAG, "loadModel queueEvent try")

          val manager = LAppLive2DManager.getInstance()

          loadModelFromFileSystem(fullPath, manager)
          
          Log.d(TAG, "loadModel queueEvent loadModelFromFileSystem success")
          
          glSurfaceView.requestRender()
          dispatchEvent("onModelLoaded", mapOf(
            "modelPath" to fullPath
          ))
          
          Log.d(TAG, "loadModel queueEvent onModelLoaded success")

        } catch (e: Exception) {
          
          Log.e(TAG, "loadModel queueEvent onError: ${e.message}", e)
          
          dispatchEvent("onError", mapOf(
            "error" to "MODEL_LOAD_ERROR",
            "message" to "loadModel queueEvent onError: ${e.message}"
          ))
        }
      }

    } catch (e: Exception) {

      Log.e(TAG, "loadModel Failed to load model: ${e.message}")
      
      dispatchEvent("onError", mapOf(
        "error" to "MODEL_LOAD_ERROR",
        "message" to "loadModel Failed to load model: ${e.message}"
      ))
    }
  }
  
  private fun loadModelFromFileSystem(modelPath: String, manager: LAppLive2DManager) {
    Log.d(TAG, "loadModelFromFileSystem: $modelPath")
    
    // 处理 file:// URI
    val actualPath = if (modelPath.startsWith("file://")) {
      modelPath.substring(7) // 移除 "file://" 前缀
    } else {
      modelPath
    }
    
    Log.d(TAG, "loadModelFromFileSystem: actualPath: $actualPath")
    
    // 验证文件是否存在
    val file = java.io.File(actualPath)
    if (!file.exists()) {
      Log.e(TAG, "loadModelFromFileSystem: file not found $actualPath")
      dispatchEvent("onError", mapOf(
        "error" to "FILE_NOT_FOUND",
        "message" to "loadModelFromFileSystem: file not found $actualPath"
      ))
      return
    }
    
    Log.d(TAG, "loadModelFromFileSystem: file exists, size: ${file.length()} bytes")
    
    // 检查CubismFramework是否已初始化
    try {
      val idManager = com.live2d.sdk.cubism.framework.CubismFramework.getIdManager()
      if (idManager == null) {
        Log.e(TAG, "loadModelFromFileSystem: CubismFramework not properly initialized")
        dispatchEvent("onError", mapOf(
          "error" to "FRAMEWORK_NOT_INITIALIZED",
          "message" to "CubismFramework not properly initialized"
        ))
        return
      }
      Log.d(TAG, "loadModelFromFileSystem: CubismFramework is properly initialized")
    } catch (e: Exception) {
      Log.e(TAG, "loadModelFromFileSystem: Error checking CubismFramework initialization: ${e.message}")
      dispatchEvent("onError", mapOf(
        "error" to "FRAMEWORK_CHECK_ERROR",
        "message" to "Error checking CubismFramework initialization: ${e.message}"
      ))
      return
    }
    
    // Check textureManager availability
    val textureManager = LAppDelegate.getInstance().getTextureManager()
    if (textureManager == null) {
      Log.e(TAG, "loadModelFromFileSystem: textureManager is null, cannot load model")
      dispatchEvent("onError", mapOf(
        "error" to "TEXTURE_MANAGER_NOT_AVAILABLE",
        "message" to "textureManager is null, cannot load model"
      ))
      return
    }
    Log.d(TAG, "loadModelFromFileSystem: textureManager is available")
    
    // 彻底清除现有模型 - 多次调用确保清理完成
    Log.d(TAG, "loadModelFromFileSystem: before manager.releaseAllModel: ${manager.getModelNum()} models)")

    manager.releaseAllModel()
    
    // 等待一帧确保清理完成
    try {
      Thread.sleep(16) // 等待约一帧的时间
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
    
    Log.d(TAG, "loadModelFromFileSystem: after manager.releaseAllModel: ${manager.getModelNum()} models")
    
    // 创建新模型并从文件系统加载
    Log.d(TAG, "loadModelFromFileSystem: before LAppModel")

    val model = LAppModel()
    
    Log.d(TAG, "loadModelFromFileSystem: before model.loadAssetsFromFileSystem: $actualPath")
    
    model.loadAssetsFromFileSystem(actualPath)
    
    // 检查模型是否成功加载
    if (model.model == null) {
      Log.e(TAG, "loadModelFromFileSystem: model is null")
      dispatchEvent("onError", mapOf(
        "error" to "MODEL_LOAD_FAILED",
        "message" to "loadModelFromFileSystem: model is null"
      ))
      return
    }
    
    Log.d(TAG, "loadModelFromFileSystem: after model.loadAssetsFromFileSystem: $actualPath")
    
    // 将模型添加到管理器
    manager.addModel(model)
    
    Log.d(TAG, "loadModelFromFileSystem: after manager.addModel: ${manager.getModelNum()} models")
    
    // 尝试绑定延迟的纹理
    model.bindPendingTextures()
    
    // 延迟检查渲染器可用性，因为渲染器可能在模型添加到管理器后才初始化
    glSurfaceView.queueEvent {
      try {
        // 等待一小段时间让渲染器初始化
        Thread.sleep(100)
        
        // 检查渲染器是否现在可用
        if (model.checkRendererAvailability()) {
          Log.d(TAG, "loadModelFromFileSystem: Renderer is now available, binding pending textures")
          model.bindPendingTextures()
        } else {
          Log.w(TAG, "loadModelFromFileSystem: Renderer is still not available after delay")
        }
      } catch (e: Exception) {
        Log.e(TAG, "loadModelFromFileSystem: Error checking renderer availability: ${e.message}")
      }
    }
  }

  fun startMotion(motionGroup: String, motionIndex: Int) {
    Log.d(TAG, "startMotion $motionGroup[$motionIndex]")
    
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
        Log.w(TAG, "startMotion currentModel is null")
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
    Log.d(TAG, "setExpression $expressionId")
    
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
    Log.d(TAG, "setAutoBlink $enabled")
    
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
    Log.d(TAG, "setAutoBreath $enabled")
    
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
    Log.d(TAG, "setScale $scale")
    
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

  fun setPosition(x: Float, y: Float) {
    Log.d(TAG, "setPosition ($x, $y)")
    
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

    Log.d(TAG, "onAttachedToWindow")
    
    try {
      // 如果之前被detach过，需要重新初始化
      if (!isInitialized) {
        Log.d(TAG, "onAttachedToWindow isInitialized false")
        try {

          initializeLive2D()

          Log.d(TAG, "onAttachedToWindow initializeLive2D success")
        } catch (e: Exception) {
          Log.e(TAG, "onAttachedToWindow initializeLive2D failed: ${e.message}", e)
        }
      } else {
        Log.d(TAG, "onAttachedToWindow isInitialized true")
      }
      
      // 调试信息：检查状态
      Log.d(TAG, "onAttachedToWindow - modelPath: $modelPath, isInitialized: $isInitialized")
      
      // 延迟重新加载模型，确保GLSurfaceView完全初始化
      Log.d(TAG, "onAttachedToWindow before postDelayed")
      postDelayed({
        try {
          Log.d(TAG, "onAttachedToWindow postDelayed - modelPath: $modelPath, isInitialized: $isInitialized")
          
          // 如果之前有模型路径，重新加载模型
          if (modelPath != null && isInitialized) {
            Log.d(TAG, "onAttachedToWindow postDelayed before loadModel: $modelPath")
            loadModel(modelPath!!)
          } else {
            Log.w(TAG, "onAttachedToWindow postDelayed modelPath is null or isInitialized is false")
          }
        } catch (e: Exception) {
          Log.e(TAG, "onAttachedToWindow postDelayed exception: ${e.message}", e)
        }
      }, 500) // 延迟500ms
      
      Log.d(TAG, "onAttachedToWindow before glSurfaceView.onResume()")
      glSurfaceView.onResume()
      Log.d(TAG, "onAttachedToWindow glSurfaceView.onResume() completed")
    } catch (e: Exception) {
      Log.e(TAG, "onAttachedToWindow exception: ${e.message}", e)
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    Log.d(TAG, "View detached from window - modelPath: $modelPath, isInitialized: $isInitialized")
    
    try {
      glSurfaceView.onPause()
      
      // 清理 Live2D 资源，但不销毁单例
      if (isInitialized) {
        val delegate = LAppDelegate.getInstance()
        Log.d(TAG, "Cleaning up Live2D resources on detach")
        
        // 只调用onStop来清理view，不调用onDestroy来保持单例
        delegate.onStop()
        
        // 重置初始化状态，但不销毁delegate
        isInitialized = false
        isGLSetupComplete = false
        
        Log.d(TAG, "Live2D resources cleaned up")
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