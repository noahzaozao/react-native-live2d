package expo.modules.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.graphics.PixelFormat

class Live2DGLSurfaceView : GLSurfaceView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  init {
    // 配置透明背景（RGBA8888）以避免黑底
    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    holder.setFormat(PixelFormat.TRANSLUCENT)
    // 置顶绘制，避免被 RN 视图遮挡；若影响布局可按需调整
    setZOrderOnTop(true)
  }
}


