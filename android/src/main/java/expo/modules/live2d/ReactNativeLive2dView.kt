package expo.modules.live2d

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import expo.modules.kotlin.views.ViewManagerDefinition

class ReactNativeLive2DView(context: Context) : TextView(context) {
  private var modelPath: String? = null
  
  init {
    // 设置基本属性
    text = "Live2D View\n(模型加载中...)"
    textSize = 16f
    textAlignment = View.TEXT_ALIGNMENT_CENTER
    setPadding(20, 20, 20, 20)
  }

  fun loadModel(assetPath: String) {
    Log.d("ReactNativeLive2D", "Loading model: $assetPath")
    modelPath = assetPath
    text = "Live2D View\n模型: $assetPath\n(需要集成 Cubism SDK)"
  }
  
  fun startMotion(motionGroup: String, motionIndex: Int) {
    Log.d("ReactNativeLive2D", "Starting motion: $motionGroup[$motionIndex]")
    text = "Live2D View\n动作: $motionGroup[$motionIndex]\n(需要集成 Cubism SDK)"
  }
  
  fun setExpression(expressionId: String) {
    Log.d("ReactNativeLive2D", "Setting expression: $expressionId")
    text = "Live2D View\n表情: $expressionId\n(需要集成 Cubism SDK)"
  }
  
  fun update() {
    // TODO: 更新模型状态
  }
}

// ViewManager
class ReactNativeLive2DViewManager :
        expo.modules.kotlin.views.ViewManager<ReactNativeLive2DView>() {
  override fun getName() = "ReactNativeLive2DView"

  override fun createViewInstance(context: Context): ReactNativeLive2DView {
    return ReactNativeLive2DView(context)
  }

  override fun definition() = ViewManagerDefinition {
    prop("modelPath") { view: ReactNativeLive2DView, path: String -> 
      view.loadModel(path) 
    }
    prop("motionGroup") { view: ReactNativeLive2DView, group: String ->
      // 默认播放第一个动作
      view.startMotion(group, 0)
    }
    prop("expression") { view: ReactNativeLive2DView, expression: String ->
      view.setExpression(expression)
    }
  }
}