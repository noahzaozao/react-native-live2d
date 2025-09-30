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
      Events("onModelLoaded", "onError", "onTap")
      
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

      Prop("autoBlink") { view: ReactNativeLive2dView, enabled: Boolean ->
        view.setAutoBlink(enabled)
      }

      Prop("autoBreath") { view: ReactNativeLive2dView, enabled: Boolean ->
        view.setAutoBreath(enabled)
      }
    }

    AsyncFunction("preloadModel") { modelPath: String, promise: Promise ->
      try {
        Log.d(TAG, "Preloading model: $modelPath")

        // 1) 本地文件：支持 file:// 或 绝对路径
        if (modelPath.startsWith("file://") || modelPath.startsWith("/")) {
          val path = if (modelPath.startsWith("file://")) modelPath.removePrefix("file://") else modelPath
          val file = java.io.File(path)
          if (!file.exists() || !file.isFile) {
            throw IOException("Local model file not found: $path")
          }
          // 仅校验可读
          java.io.FileInputStream(file).use { /* no-op */ }
          Log.d(TAG, "Model preloaded from local file: $path")
          promise.resolve(null)
          return@AsyncFunction
        }

        // 2) 资产目录尝试：先按原样尝试，再尝试移除 public/
        val candidates = mutableListOf<String>()
        candidates += modelPath
        if (modelPath.startsWith("public/")) {
          candidates += modelPath.substring(7)
        }

        var opened: String? = null
        var lastError: IOException? = null
        for (p in candidates) {
          try {
            Log.d(TAG, "Trying asset open: $p")
            context.assets.open(p).use { /* no-op */ }
            opened = p
            break
          } catch (e: IOException) {
            lastError = e
          }
        }

        if (opened != null) {
          Log.d(TAG, "Model preloaded from assets: $opened")
          promise.resolve(null)
        } else {
          // 打印 live2d 目录下已打包的清单，帮助排查
          try {
            val list = context.assets.list("live2d")?.joinToString(", ") ?: "<empty>"
            Log.e(TAG, "Assets in live2d/: $list")
          } catch (_: Exception) {}
          throw lastError ?: IOException("Asset not found for candidates: ${candidates.joinToString()}")
        }

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