package expo.modules.live2d

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView

class ReactNativeLive2dView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val textView: TextView = TextView(context)
  private var modelPath: String? = null
  
  init {
    // 设置基本属性
    textView.text = "Live2D View\n(模型加载中...)"
    textView.textSize = 16f
    textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
    textView.setPadding(20, 20, 20, 20)
    addView(textView)
  }

  fun loadModel(assetPath: String) {
    Log.d("ReactNativeLive2D", "Loading model: $assetPath")
    
    // 处理 public 目录路径
    val actualPath = if (assetPath.startsWith("public/")) {
      assetPath.substring(7) // 移除 "public/" 前缀
    } else {
      assetPath
    }
    
    modelPath = actualPath
    textView.text = "Live2D View\n模型: $actualPath\n(需要集成 Cubism SDK)"
  }
  
  fun startMotion(motionGroup: String, motionIndex: Int) {
    Log.d("ReactNativeLive2D", "Starting motion: $motionGroup[$motionIndex]")
    textView.text = "Live2D View\n动作: $motionGroup[$motionIndex]\n(需要集成 Cubism SDK)"
  }
  
  fun setExpression(expressionId: String) {
    Log.d("ReactNativeLive2D", "Setting expression: $expressionId")
    textView.text = "Live2D View\n表情: $expressionId\n(需要集成 Cubism SDK)"
  }
  
  fun update() {
    // TODO: 更新模型状态
  }
}