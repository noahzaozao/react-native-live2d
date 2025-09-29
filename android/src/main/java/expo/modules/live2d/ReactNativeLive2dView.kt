package expo.modules.live2d

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.events.EventDispatcher
import expo.modules.kotlin.views.ExpoView

class ReactNativeLive2dView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val container: FrameLayout = FrameLayout(context)
  private val glView: Live2DGLSurfaceView = Live2DGLSurfaceView(context)
  private val renderer: Live2DRenderer = Live2DRenderer(context)
  private val events by lazy { EventDispatcher() }
  private var modelPath: String? = null

  init {
    glView.setEGLContextClientVersion(2)
    glView.setRenderer(renderer)
    glView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
    glView.setBackgroundColor(0x00000000)

    container.addView(
      glView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )
    container.setBackgroundColor(0x00000000)
    addView(container)
  }

  fun loadModel(assetPath: String) {
    Log.d("ReactNativeLive2D", "Loading model: $assetPath")

    val actualPath = if (assetPath.startsWith("public/")) assetPath.substring(7) else assetPath
    modelPath = actualPath
    renderer.setModelPath(actualPath)
    glView.requestRender()
  }

  fun startMotion(motionGroup: String, motionIndex: Int) {
    Log.d("ReactNativeLive2D", "Starting motion: $motionGroup[$motionIndex]")
    renderer.startMotion(motionGroup, motionIndex)
  }

  fun setExpression(expressionId: String) {
    Log.d("ReactNativeLive2D", "Setting expression: $expressionId")
    renderer.setExpression(expressionId)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    glView.onResume()
    renderer.onModelLoaded = {
      events.dispatch("onModelLoaded", mapOf("ok" to true))
    }
    renderer.onError = { msg ->
      events.dispatch("onError", mapOf("message" to msg))
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    glView.onPause()
  }
}