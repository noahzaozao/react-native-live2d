package expo.modules.live2d

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import android.content.Context
import android.util.Log
import java.io.IOException

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
      
      Prop("modelPath") { view: ReactNativeLive2dView, path: String -> 
        view.loadModel(path) 
      }
      
      Prop("motionGroup") { view: ReactNativeLive2dView, group: String ->
        // 默认播放第一个动作
        view.startMotion(group, 0)
      }
      
      Prop("expression") { view: ReactNativeLive2dView, expression: String ->
        view.setExpression(expression)
      }
    }

    AsyncFunction("preloadModel") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Preloading model: $modelPath")
        
        // 检查模型文件是否存在 - 支持从 public 目录加载
        val assetPath = if (modelPath.startsWith("public/")) {
          modelPath.substring(7) // 移除 "public/" 前缀
        } else {
          modelPath
        }
        
        val inputStream = context.assets.open(assetPath)
        inputStream.close()
        
        Log.d(TAG, "Model preloaded successfully: $assetPath")
        promise.resolve(null)
        
      } catch (e: IOException) {
        Log.e(TAG, "Failed to preload model: ${e.message}")
        promise.reject("MODEL_LOAD_ERROR", "Failed to preload model: ${e.message}", e)
      }
    }

    AsyncFunction("releaseModel") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Releasing model: $modelPath")
        // TODO: 实现模型资源释放
        promise.resolve(null)
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to release model: ${e.message}")
        promise.reject("MODEL_RELEASE_ERROR", "Failed to release model: ${e.message}", e)
      }
    }

    AsyncFunction("getAvailableMotions") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Getting available motions for: $modelPath")
        
        // TODO: 解析模型文件，获取可用动作列表
        val motions = listOf("idle", "tap", "shake")
        promise.resolve(motions)
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to get motions: ${e.message}")
        promise.reject("MOTIONS_ERROR", "Failed to get motions: ${e.message}", e)
      }
    }

    AsyncFunction("getAvailableExpressions") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Getting available expressions for: $modelPath")
        
        // TODO: 解析模型文件，获取可用表情列表
        val expressions = listOf("f01", "f02", "f03")
        promise.resolve(expressions)
        
      } catch (e: Exception) {
        Log.e(TAG, "Failed to get expressions: ${e.message}")
        promise.reject("EXPRESSIONS_ERROR", "Failed to get expressions: ${e.message}", e)
      }
    }
  }
}