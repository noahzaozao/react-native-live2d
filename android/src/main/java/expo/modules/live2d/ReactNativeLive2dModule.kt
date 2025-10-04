package expo.modules.live2d

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import android.content.Context
import android.util.Log
import com.live2d.kotlin.LAppDelegate
import com.live2d.kotlin.LAppLive2DManager
import com.live2d.kotlin.LAppModel
import java.io.IOException
import java.io.File
import java.net.URI

class ReactNativeLive2dModule : Module() {
  private val context: Context by lazy { 
    appContext.reactContext ?: throw Exception("React context not available") 
  }
  
  companion object {
    private const val TAG = "ReactNativeLive2dModule"
  }

  override fun definition() = ModuleDefinition {
    Name("ReactNativeLive2d")

    View(ReactNativeLive2dView::class) {
      Name("ReactNativeLive2dView")
      Events("onModelLoaded", "onError", "onTap", "onMotionFinished")
      
      Prop("modelPath") { view: ReactNativeLive2dView, path: String? -> 
        Log.d(TAG, "modelPath prop received: '$path'")
        Log.d(TAG, "Current view state - isInitialized: ${view.isInitialized}, modelPath: ${view.modelPath}")
        
        if (path != null && path.isNotEmpty()) {
          
          Log.d(TAG, "Calling view.loadModel with path: '$path'")
          
          view.loadModel(path) 
          
          Log.d(TAG, "view.loadModel call completed for path: '$path'")

        } else {
          
          Log.d(TAG, "modelPath is null or empty, skipping model loading")
          
        }
      }
      
      Prop("motionGroup") { view: ReactNativeLive2dView, group: String ->
        view.startMotion(group, 0)
      }
      
      Prop("expression") { view: ReactNativeLive2dView, expression: String ->
        view.setExpression(expression)
      }

      Prop("autoBlink") { view: ReactNativeLive2dView, enabled: Boolean ->
        view.setAutoBlink(enabled)
      }

      Prop("autoBreath") { view: ReactNativeLive2dView, enabled: Boolean ->
        view.setAutoBreath(enabled)
      }

      Prop("scale") { view: ReactNativeLive2dView, scale: Float ->
        view.setScale(scale)
      }

      Prop("position") { view: ReactNativeLive2dView, position: Map<String, Float> ->
        val x = position["x"] ?: 0f
        val y = position["y"] ?: 0f
        view.setPosition(x, y)
      }
    }

    AsyncFunction("initializeLive2D") { promise: Promise ->
      try {
        Log.d(TAG, "Initializing Live2D framework")
        
        // 初始化 Live2D 框架
        val delegate = LAppDelegate.getInstance()
        
        // 检查是否需要初始化
        val currentView = delegate.getView()
        val textureManager = delegate.getTextureManager()
        
        if (currentView == null || textureManager == null) {
          Log.d(TAG, "Live2D framework not initialized, initializing now")
          
          // 获取当前 Activity
          val activity = appContext.currentActivity
          if (activity == null) {
            promise.reject("INIT_ERROR", "No current activity available for initialization", null)
            return@AsyncFunction
          }
          
          // 初始化 Live2D
          delegate.onStart(activity)
          
          // 验证初始化是否成功
          val newView = delegate.getView()
          val newTextureManager = delegate.getTextureManager()
          
          if (newView != null && newTextureManager != null) {
            Log.d(TAG, "Live2D framework initialized successfully")
            promise.resolve(true)
          } else {
            promise.reject("INIT_ERROR", "Live2D framework initialization failed - view or texture manager is null", null)
          }
        } else {
          Log.d(TAG, "Live2D framework already initialized")
          promise.resolve(true)
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Live2D: ${e.message}")
        promise.reject("INIT_ERROR", "Failed to initialize Live2D: ${e.message}", e)
      }
    }

    // 内部自动初始化方法
    fun ensureLive2DInitialized(): Boolean {
      try {
        Log.d(TAG, "Ensuring Live2D framework is initialized")
        
        val delegate = LAppDelegate.getInstance()
        val currentView = delegate.getView()
        val textureManager = delegate.getTextureManager()
        
        if (currentView == null || textureManager == null) {
          Log.d(TAG, "Live2D framework not initialized, initializing now")
          
          val activity = appContext.currentActivity
          if (activity == null) {
            Log.e(TAG, "No current activity available for initialization")
            return false
          }
          
          delegate.onStart(activity)
          
          // 验证初始化是否成功
          val newView = delegate.getView()
          val newTextureManager = delegate.getTextureManager()
          
          if (newView != null && newTextureManager != null) {
            Log.d(TAG, "Live2D framework auto-initialized successfully")
            return true
          } else {
            Log.e(TAG, "Live2D framework auto-initialization failed")
            return false
          }
        } else {
          Log.d(TAG, "Live2D framework already initialized")
          return true
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to auto-initialize Live2D: ${e.message}")
        return false
      }
    }

    AsyncFunction("getAvailableModels") { promise: Promise ->
      try {
        Log.d(TAG, "Getting available models")
        
        val models = mutableListOf<String>()
        val rootDirs = context.assets.list("") ?: emptyArray()
        
        for (dir in rootDirs) {
          try {
            val files = context.assets.list(dir) ?: continue
            if (files.any { it.endsWith(".model3.json") }) {
              models.add(dir)
            }
          } catch (_: Exception) {
            // 忽略无法访问的目录
          }
        }
        
        Log.d(TAG, "Found models: ${models.joinToString(", ")}")
        promise.resolve(models)
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to get available models: ${e.message}")
        promise.reject("MODELS_ERROR", "Failed to get available models: ${e.message}", e)
      }
    }

    AsyncFunction("getAvailableMotions") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Getting available motions for: $modelPath")
        
        val actualPath = if (modelPath.startsWith("public/")) modelPath.substring(7) else modelPath
        
        // 通过 LAppLive2DManager 获取当前模型的动作信息
        val manager = LAppLive2DManager.getInstance()
        val currentModel = manager.getModel(0) // 获取第一个模型
        
        if (currentModel != null) {
          // 这里需要根据实际的 LAppModel API 来获取动作列表
          // 暂时返回常见的动作类型
          val motions = listOf("idle", "tap_body", "tap_head", "shake", "flick_head")
          promise.resolve(motions)
        } else {
          // 如果没有加载模型，返回默认动作列表
          val defaultMotions = listOf("idle", "tap_body", "tap_head")
          promise.resolve(defaultMotions)
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to get motions: ${e.message}")
        promise.reject("MOTIONS_ERROR", "Failed to get motions: ${e.message}", e)
      }
    }

    AsyncFunction("getAvailableExpressions") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Getting available expressions for: $modelPath")
        
        val actualPath = if (modelPath.startsWith("public/")) modelPath.substring(7) else modelPath
        
        // 通过 LAppLive2DManager 获取当前模型的表情信息
        val manager = LAppLive2DManager.getInstance()
        val currentModel = manager.getModel(0)
        
        if (currentModel != null) {
          // 这里需要根据实际的 LAppModel API 来获取表情列表
          // 暂时返回常见的表情
          val expressions = listOf("f01", "f02", "f03", "f04", "f05", "f06", "f07", "f08")
          promise.resolve(expressions)
        } else {
          // 如果没有加载模型，返回默认表情列表
          val defaultExpressions = listOf("f01", "f02", "f03")
          promise.resolve(defaultExpressions)
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to get expressions: ${e.message}")
        promise.reject("EXPRESSIONS_ERROR", "Failed to get expressions: ${e.message}", e)
      }
    }

    AsyncFunction("startMotion") { motionGroup: String, motionIndex: Int, priority: Int, promise: Promise ->
      try {
        Log.d(TAG, "Starting motion: $motionGroup[$motionIndex] with priority: $priority")
        
        val manager = LAppLive2DManager.getInstance()
        val currentModel = manager.getModel(0)
        
        if (currentModel != null) {
          // 使用 LAppModel 的动作播放功能
          currentModel.startMotion(motionGroup, motionIndex, priority)
          promise.resolve(true)
        } else {
          promise.reject("NO_MODEL", "No model loaded", null)
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start motion: ${e.message}")
        promise.reject("MOTION_ERROR", "Failed to start motion: ${e.message}", e)
      }
    }

    AsyncFunction("setExpression") { expressionId: String, promise: Promise ->
      try {
        Log.d(TAG, "Setting expression: $expressionId")
        
        val manager = LAppLive2DManager.getInstance()
        val currentModel = manager.getModel(0)
        
        if (currentModel != null) {
          currentModel.setExpression(expressionId)
          promise.resolve(true)
        } else {
          promise.reject("NO_MODEL", "No model loaded", null)
        }
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to set expression: ${e.message}")
        promise.reject("EXPRESSION_ERROR", "Failed to set expression: ${e.message}", e)
      }
    }

    AsyncFunction("releaseModel") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Releasing model: $modelPath")
        
        val manager = LAppLive2DManager.getInstance()
        manager.releaseAllModel()
        
        promise.resolve(null)
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to release model: ${e.message}")
        promise.reject("MODEL_RELEASE_ERROR", "Failed to release model: ${e.message}", e)
      }
    }

    AsyncFunction("changeScene") { modelIndex: Int, promise: Promise ->
      try {
        Log.d(TAG, "Changing scene to model index: $modelIndex")
        
        val manager = LAppLive2DManager.getInstance()
        manager.changeScene(modelIndex)
        
        promise.resolve(true)
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to change scene: ${e.message}")
        promise.reject("SCENE_CHANGE_ERROR", "Failed to change scene: ${e.message}", e)
      }
    }
  }
}