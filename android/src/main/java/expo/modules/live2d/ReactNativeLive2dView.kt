package expo.modules.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import com.live2d.kotlin.GLRenderer
import com.live2d.kotlin.LAppDelegate
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

  companion object {
    private const val TAG = "ReactNativeLive2dView"
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

  init {
    Log.d(TAG, "init")
    
    // 在初始化前先清理可能存在的旧状态，避免Expo Refresh后的状态不一致
    try {
      val manager = LAppLive2DManager.getInstance()
      manager.releaseAllModel()
      Log.d(TAG, "init: cleared existing models during initialization")
    } catch (e: Exception) {
      Log.w(TAG, "init: failed to clear existing models: ${e.message}")
    }
    
    initializeComponents()

    val activity = getActivity()
    val delegate = LAppDelegate.getInstance()
    activity?.let { delegate.onStart(it) }
  }

  private fun initializeComponents() {
    try {
      Log.d(TAG, "initializeComponents")
      
      live2dManager = LAppLive2DManager.getInstance()

      glSurfaceView = GLSurfaceView(context)
      renderer = GLRenderer()

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
      Log.d(TAG, "setupGLSurfaceView setEGLContextClientVersion 2")

      // 设置渲染器
      glSurfaceView.setRenderer(renderer)
      Log.d(TAG, "setupGLSurfaceView glSurfaceView.setRenderer")

      // 设置渲染模式为连续渲染
      glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

      isGLSetupComplete = true

      Log.d(TAG, "initializeComponents successfully")
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

  fun loadModel(modelPath: String) {
    Log.d(TAG, "loadModel: $modelPath")

    this.modelPath = modelPath

    try {
      Log.d(TAG, "loadModel: starting model loading process")

      // 确保在 GL 线程加载模型与创建纹理
      Log.d(TAG, "loadModel before queueEvent")

      glSurfaceView.queueEvent {
        try {
          Log.d(TAG, "loadModel queueEvent try")

          val manager = LAppLive2DManager.getInstance()

          loadModelFromFileSystem(modelPath, manager)

          Log.d(TAG, "loadModel queueEvent loadModelFromFileSystem success")

          glSurfaceView.requestRender()

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
    Log.d(TAG, "loadModelFromFileSystem: clearing existing models before loading new one")
    manager.releaseAllModel()

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

    currentMotionGroup = motionGroup
    currentMotionIndex = motionIndex

    var currentModel = live2dManager?.getModel(0)
    if (currentModel == null) {
      Log.w(TAG, "startMotion currentModel is null")
      return
    }

    try {
      currentModel.startMotion(motionGroup, motionIndex, 3) // 使用高优先级
      glSurfaceView.requestRender()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start motion: ${e.message}")
      dispatchEvent(
              "onError",
              mapOf("error" to "MOTION_ERROR", "message" to "Failed to start motion: ${e.message}")
      )
    }
  }

  fun setExpression(expressionId: String) {
    Log.d(TAG, "setExpression $expressionId")

    currentExpressionId = expressionId

    var currentModel = live2dManager?.getModel(0)
    if (currentModel == null) {
      Log.w(TAG, "setExpression currentModel is null")
      return
    }

    try {
      currentModel.setExpression(expressionId)
      glSurfaceView.requestRender()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to set expression: ${e.message}")
      dispatchEvent(
              "onError",
              mapOf(
                      "error" to "EXPRESSION_ERROR",
                      "message" to "Failed to set expression: ${e.message}"
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

  fun setScale(scale: Float) {
    Log.d(TAG, "setScale $scale")

    currentScale = scale

    var currentModel = LAppLive2DManager.getInstance().getModel(0)
    if (currentModel == null) {
      Log.w(TAG, "setScale currentModel is null")
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
          dispatchEvent("onTap", mapOf("x" to x, "y" to y))
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
    
    // Expo Refresh 后会重新 init，所以这里不需要重复初始化
    // init 方法已经处理了所有必要的初始化工作
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    Log.d(TAG, "onDetachedFromWindow")
    
    // 1. 清理Live2D模型资源，避免Expo Refresh后的状态不一致
    try {
      val manager = LAppLive2DManager.getInstance()
      manager.releaseAllModel()
      Log.d(TAG, "onDetachedFromWindow: released all models")
    } catch (e: Exception) {
      Log.w(TAG, "onDetachedFromWindow: failed to release models: ${e.message}")
    }

    // 2. 清理LAppView资源（包括sprite shader等）
    try {
      val delegate = LAppDelegate.getInstance()
      delegate.view?.close()
      Log.d(TAG, "onDetachedFromWindow: closed LAppView")
    } catch (e: Exception) {
      Log.w(TAG, "onDetachedFromWindow: failed to close LAppView: ${e.message}")
    }

    // 3. 清理纹理管理器（通过LAppDelegate的onStop方法）
    try {
      val delegate = LAppDelegate.getInstance()
      delegate.onStop()
      Log.d(TAG, "onDetachedFromWindow: called delegate.onStop()")
    } catch (e: Exception) {
      Log.w(TAG, "onDetachedFromWindow: failed to call delegate.onStop(): ${e.message}")
    }

    // 4. 暂停GLSurfaceView渲染
    try {
      glSurfaceView.onPause()
      Log.d(TAG, "onDetachedFromWindow: paused GLSurfaceView")
    } catch (e: Exception) {
      Log.w(TAG, "onDetachedFromWindow: failed to pause GLSurfaceView: ${e.message}")
    }

    // 5. 重置组件状态
    isGLSetupComplete = false
    isInitialized = false
    modelPath = null
    
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
