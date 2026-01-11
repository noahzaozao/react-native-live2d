package expo.modules.live2d

import android.content.Context
import android.util.Log
import com.live2d.kotlin.LAppDelegate
import com.live2d.kotlin.LAppLive2DManager
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File

class ReactNativeLive2dModule : Module() {
    private val context: Context
        get() =
                appContext.currentActivity
                        ?: appContext.reactContext ?: throw Exception("No valid context")

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
                    //   if (view.modelPath != path) {
                    //     view.loadModel(path)
                    //   } else {
                    //     Log.d(TAG, "modelPath unchanged, skip loadModel")
                    //   }

                    Log.d(TAG, "view.loadModel call completed for path: '$path'")
                } else {

                    if (view.modelPath != null) {
                        view.clearModel()
                        Log.d(TAG, "modelPath cleared")
                    } else {
                        Log.d(TAG, "modelPath is null or empty, skipping model loading")
                    }
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

            Prop("scale") { view: ReactNativeLive2dView, scale: Float -> view.setScale(scale) }

            Prop("position") { view: ReactNativeLive2dView, position: Map<String, Float> ->
                val x = position["x"] ?: 0f
                val y = position["y"] ?: 0f
                view.setPosition(x, y)
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

        /**
         * 显式初始化 Live2D 框架（供 JS 调用）
         *
         * 说明：
         * - `ReactNativeLive2dView` 通常会在 GL 线程里调用 delegate.onStart() 做初始化
         * - 但 JS 侧有时需要提前/显式确保框架已就绪（例如页面首次进入、或某些竞态场景）
         */
        AsyncFunction("initializeLive2D") { promise: Promise ->
            try {
                val ok = ensureLive2DInitialized()
                promise.resolve(ok)
            } catch (e: Exception) {
                Log.e(TAG, "initializeLive2D failed: ${e.message}", e)
                promise.resolve(false)
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

                val actualPath =
                        if (modelPath.startsWith("public/")) modelPath.substring(7) else modelPath

                // 推断模型根目录：可能传的是目录或 model3.json 文件
                val baseDir =
                        try {
                            val f = java.io.File(actualPath)
                            if (f.isDirectory) f else f.parentFile
                        } catch (_: Exception) {
                            null
                        }

                val motions: List<String> =
                        if (baseDir != null) {
                            val motionsDir = java.io.File(baseDir, "motions")
                            if (motionsDir.exists() && motionsDir.isDirectory) {
                                motionsDir
                                        .listFiles()
                                        ?.filter { it.isFile && it.name.endsWith(".motion3.json") }
                                        ?.map { file ->
                                            // 去掉 .motion3.json 后缀作为动作名
                                            file.name.removeSuffix(".motion3.json")
                                        }
                                        ?.sorted()
                                        ?: emptyList()
                            } else emptyList()
                        } else emptyList()

                if (motions.isNotEmpty()) {
                    Log.d(TAG, "Found motions from filesystem: ${motions.joinToString(", ")}")
                    promise.resolve(motions)
                } else {
                    Log.w(
                            TAG,
                            "No motions found on filesystem for: $actualPath, falling back to defaults"
                    )
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

                val actualPath =
                        if (modelPath.startsWith("public/")) modelPath.substring(7) else modelPath

                val baseDir =
                        try {
                            val f = java.io.File(actualPath)
                            if (f.isDirectory) f else f.parentFile
                        } catch (_: Exception) {
                            null
                        }

                val expressions: List<String> =
                        if (baseDir != null) {
                            val expressionsDir = java.io.File(baseDir, "expressions")
                            if (expressionsDir.exists() && expressionsDir.isDirectory) {
                                expressionsDir
                                        .listFiles()
                                        ?.filter { it.isFile && it.name.endsWith(".exp3.json") }
                                        ?.map { file ->
                                            // 去掉 .exp3.json 后缀作为表情名
                                            file.name.removeSuffix(".exp3.json")
                                        }
                                        ?.sorted()
                                        ?: emptyList()
                            } else emptyList()
                        } else emptyList()

                if (expressions.isNotEmpty()) {
                    Log.d(
                            TAG,
                            "Found expressions from filesystem: ${expressions.joinToString(", ")}"
                    )
                    promise.resolve(expressions)
                } else {
                    Log.w(
                            TAG,
                            "No expressions found on filesystem for: $actualPath, falling back to defaults"
                    )
                    val defaultExpressions = listOf("f01", "f02", "f03")
                    promise.resolve(defaultExpressions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get expressions: ${e.message}")
                promise.reject("EXPRESSIONS_ERROR", "Failed to get expressions: ${e.message}", e)
            }
        }

        AsyncFunction("startMotion") {
                motionGroup: String,
                motionIndex: Int,
                priority: Int,
                promise: Promise ->
            try {
                Log.d(TAG, "Starting motion: $motionGroup[$motionIndex] with priority: $priority")

                val manager = LAppLive2DManager.getInstance()
                val currentModel = manager.getModel(0)

                val success =
                        if (currentModel != null) {
                            // 使用 LAppModel 的动作播放功能
                            currentModel.startMotion(motionGroup, motionIndex, priority)
                            true
                        } else false

                promise.resolve(success)
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

                val success =
                        if (currentModel != null) {
                            currentModel.setExpression(expressionId)
                            true
                        } else false

                promise.resolve(success)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set expression: ${e.message}")
                promise.reject("EXPRESSION_ERROR", "Failed to set expression: ${e.message}", e)
            }
        }
        
        /**
         * 设置口型同步值（用于实时口型同步）
         * 
         * @param value 嘴巴开合度（0.0 ~ 1.0）
         */
        Function("setMouthValue") { value: Float ->
            try {
                val manager = LAppLive2DManager.getInstance()
                val currentModel = manager.getModel(0)
                
                if (currentModel != null) {
                    currentModel.setMouthValue(value)
                } else {
                    Log.w(TAG, "Cannot set mouth value: No model loaded")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set mouth value: ${e.message}")
            }
        }
        
        /**
         * 获取当前口型同步值
         * 
         * @return 当前嘴巴开合度（0.0 ~ 1.0）
         */
        Function("getMouthValue") {
            try {
                val manager = LAppLive2DManager.getInstance()
                val currentModel = manager.getModel(0)
                
                currentModel?.getMouthValue() ?: 0.0f
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get mouth value: ${e.message}")
                0.0f
            }
        }
    }
}
