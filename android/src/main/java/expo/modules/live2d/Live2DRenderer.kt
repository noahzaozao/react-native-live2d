package expo.modules.live2d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.io.File
import java.io.InputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import org.json.JSONObject
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismFramework.Option
import com.live2d.sdk.cubism.framework.CubismFrameworkConfig.LogLevel
import com.live2d.sdk.cubism.core.ICubismLogger
import com.live2d.sdk.cubism.framework.model.CubismMoc
import com.live2d.sdk.cubism.framework.model.CubismModel
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink
import com.live2d.sdk.cubism.framework.effect.CubismBreath
import com.live2d.sdk.cubism.framework.physics.CubismPhysics

class Live2DRenderer(private val context: Context) : GLSurfaceView.Renderer {
  private var modelPath: String? = null
  private var currentMotionGroup: String? = null
  private var currentMotionIndex: Int = 0
  private var currentExpressionId: String? = null
  private var textureId: Int = 0
  private var pendingBitmap: Bitmap? = null
  private data class PendingTex(val index: Int, val bitmap: Bitmap)
  private val pendingTexQueue: java.util.ArrayDeque<PendingTex> = java.util.ArrayDeque()
  private var viewWidth: Int = 0
  private var viewHeight: Int = 0
  private var mocPathAbs: String? = null
  private var physicsPathAbs: String? = null
  private var posePathAbs: String? = null
  var onModelLoaded: (() -> Unit)? = null
  var onError: ((String) -> Unit)? = null
  private var program: Int = 0
  private var aPosition: Int = -1
  private var aTexCoord: Int = -1
  private var uTexture: Int = -1
  private var vertexBuffer: java.nio.FloatBuffer? = null
  private var texBuffer: java.nio.FloatBuffer? = null

  // Cubism runtime objects
  private var cubismStarted: Boolean = false
  private var cubismMoc: CubismMoc? = null
  private var cubismModel: CubismModel? = null
  private var cubismRenderer: CubismRendererAndroid? = null
  private var boundTextureIndex0: Boolean = false
  private var eyeBlink: CubismEyeBlink? = null
  private var breath: CubismBreath? = null
  private var physics: CubismPhysics? = null
  private var autoBlinkEnabled: Boolean = true
  private var autoBreathEnabled: Boolean = true

  fun setModelPath(path: String) {
    modelPath = path
    Log.d("Live2DRenderer", "setModelPath: $path")
    // 尝试解析 model3.json 并加载第一张纹理
    tryLoadFirstTexture(path)
  }

  fun startMotion(group: String, index: Int) {
    currentMotionGroup = group
    currentMotionIndex = index
    Log.d("Live2DRenderer", "startMotion: $group[$index]")
  }

  fun setExpression(expressionId: String) {
    currentExpressionId = expressionId
    Log.d("Live2DRenderer", "setExpression: $expressionId")
  }

  fun setAutoBlink(enabled: Boolean) { autoBlinkEnabled = enabled }
  fun setAutoBreath(enabled: Boolean) { autoBreathEnabled = enabled }

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    // 透明清屏
    GLES20.glClearColor(0f, 0f, 0f, 0f)
    // 开启混合，便于模型透明区域正确呈现
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

    // 编译最小纹理绘制着色器
    program = buildProgram(
      "attribute vec2 aPosition;\n" +
      "attribute vec2 aTexCoord;\n" +
      "varying vec2 vTex;\n" +
      "void main(){\n" +
      "  vTex = aTexCoord;\n" +
      "  gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
      "}"
      ,
      "precision mediump float;\n" +
      "varying vec2 vTex;\n" +
      "uniform sampler2D uTexture;\n" +
      "void main(){\n" +
      "  gl_FragColor = texture2D(uTexture, vTex);\n" +
      "}"
    )
    aPosition = GLES20.glGetAttribLocation(program, "aPosition")
    aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
    uTexture = GLES20.glGetUniformLocation(program, "uTexture")

    // 顶点：覆盖全屏的矩形（NDC）
    val vertices = floatArrayOf(
      -1f, -1f,
       1f, -1f,
      -1f,  1f,
       1f,  1f
    )
    val tex = floatArrayOf(
      0f, 1f,
      1f, 1f,
      0f, 0f,
      1f, 0f
    )
    vertexBuffer = toFloatBuffer(vertices)
    texBuffer = toFloatBuffer(tex)
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    viewWidth = width
    viewHeight = height
  }

  override fun onDrawFrame(gl: GL10?) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    // 如有待上传位图，则在 GL 线程创建纹理
    if (textureId == 0 && pendingBitmap != null) {
      try {
        textureId = createGlTexture(pendingBitmap!!)
        Log.d("Live2DRenderer", "texture created: id=$textureId")
        onModelLoaded?.invoke()
      } catch (e: Exception) {
        Log.e("Live2DRenderer", "create texture failed: ${e.message}")
        onError?.invoke("create texture failed: ${e.message}")
      } finally {
        pendingBitmap?.recycle()
        pendingBitmap = null
      }
    }

    // 处理待上传的所有模型纹理并绑定到 Cubism 渲染器
    if (cubismRenderer != null && pendingTexQueue.isNotEmpty()) {
      while (pendingTexQueue.isNotEmpty()) {
        val item = pendingTexQueue.poll()
        try {
          val texId = createGlTexture(item.bitmap)
          cubismRenderer!!.bindTexture(item.index, texId)
          Log.d("Live2DRenderer", "Cubism bindTexture(${item.index}, $texId)")
        } catch (e: Exception) {
          Log.e("Live2DRenderer", "bindTexture failed: ${e.message}")
        } finally {
          item.bitmap.recycle()
        }
      }
    }
    // 初始化并绘制 Cubism 模型（若准备就绪）
    tryInitCubismIfReady()
    if (cubismRenderer != null && cubismModel != null) {
      // 基于画布宽高与视图比例，等比缩放以避免拉伸
      val canvasW = cubismModel!!.canvasWidth
      val canvasH = cubismModel!!.canvasHeight
      val sx = if (canvasW > 0f) 2f / canvasW else 1f
      val sy = if (canvasH > 0f) 2f / canvasH else 1f
      val s = kotlin.math.min(sx, sy) // 等比缩放，取较小值以完整显示
      val mvp = CubismMatrix44.create()
      mvp.scale(s, s)

      // 更新参数：眨眼、呼吸、物理
      val deltaTimeSeconds = 1f / 60f
      if (autoBlinkEnabled) eyeBlink?.updateParameters(cubismModel!!, deltaTimeSeconds)
      if (autoBreathEnabled) breath?.updateParameters(cubismModel!!, deltaTimeSeconds)
      physics?.evaluate(cubismModel!!, deltaTimeSeconds)

      cubismRenderer!!.setMvpMatrix(mvp)
      cubismRenderer!!.drawModel()
      return
    }

    if (textureId != 0 && program != 0 && vertexBuffer != null && texBuffer != null) {
      GLES20.glUseProgram(program)
      GLES20.glEnableVertexAttribArray(aPosition)
      GLES20.glEnableVertexAttribArray(aTexCoord)

      vertexBuffer!!.position(0)
      GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
      texBuffer!!.position(0)
      GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
      GLES20.glUniform1i(uTexture, 0)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
      GLES20.glDisableVertexAttribArray(aPosition)
      GLES20.glDisableVertexAttribArray(aTexCoord)
    }
  }

  private fun tryLoadFirstTexture(model3Path: String) {
    try {
      val json = readTextFromUriOrFile(model3Path)
      if (json.isNullOrEmpty()) return
      // 解析 Cubism model3.json: FileReferences.Textures 或低版本 textures
      val root = JSONObject(json)
      var firstTex: String? = null
      val texturesRel = mutableListOf<String>()
      var mocRel: String? = null
      var physicsRel: String? = null
      var poseRel: String? = null
      if (root.has("FileReferences")) {
        val refs = root.getJSONObject("FileReferences")
        if (refs.has("Moc")) mocRel = refs.getString("Moc")
        if (refs.has("Textures")) {
          val arr = refs.getJSONArray("Textures")
          for (i in 0 until arr.length()) {
            texturesRel.add(arr.getString(i))
          }
          if (arr.length() > 0) firstTex = arr.getString(0)
        }
        if (refs.has("Physics")) physicsRel = refs.getString("Physics")
        if (refs.has("Pose")) poseRel = refs.getString("Pose")
      }
      if (firstTex == null && root.has("textures")) {
        val arr = root.getJSONArray("textures")
        for (i in 0 until arr.length()) {
          texturesRel.add(arr.getString(i))
        }
        if (arr.length() > 0) firstTex = arr.getString(0)
      }
      if (firstTex == null) {
        Log.e("Live2DRenderer", "textures array not found in model3.json (FileReferences.Textures / textures)")
        return
      }

      val baseDir = File(resolveLocalPath(model3Path)).parentFile?.absolutePath ?: return
      val texPath = File(baseDir, firstTex).absolutePath
      mocPathAbs = mocRel?.let { File(baseDir, it).absolutePath }
      physicsPathAbs = physicsRel?.let { File(baseDir, it).absolutePath }
      posePathAbs = poseRel?.let { File(baseDir, it).absolutePath }
      Log.d("Live2DRenderer", "first texture: $texPath")
      Log.d("Live2DRenderer", "moc: ${mocPathAbs ?: "<none>"}")
      Log.d("Live2DRenderer", "physics: ${physicsPathAbs ?: "<none>"}")
      Log.d("Live2DRenderer", "pose: ${posePathAbs ?: "<none>"}")

      // 加载全部纹理为位图，入队等 GL 线程上传
      texturesRel.forEachIndexed { idx, rel ->
        val p = File(baseDir, rel).absolutePath
        val bmp = BitmapFactory.decodeFile(p)
        if (bmp == null) {
          Log.e("Live2DRenderer", "decodeFile failed: $p")
        } else {
          pendingTexQueue.add(PendingTex(idx, bmp))
        }
      }
    } catch (e: Exception) {
      Log.e("Live2DRenderer", "load texture failed: ${e.message}")
      onError?.invoke("load texture failed: ${e.message}")
    }
  }

  private fun tryInitCubismIfReady() {
    val mocPath = mocPathAbs ?: return

    if (!cubismStarted) {
      try {
        val opt = Option().apply {
          loggingLevel = LogLevel.VERBOSE
          logFunction = ICubismLogger { msg -> Log.d("CubismCore", msg) }
        }
        CubismFramework.startUp(opt)
        CubismFramework.initialize()
        cubismStarted = true
        Log.d("Live2DRenderer", "CubismFramework initialized")
      } catch (e: Exception) {
        Log.e("Live2DRenderer", "CubismFramework init failed: ${e.message}")
        onError?.invoke("Cubism init failed: ${e.message}")
        return
      }
    }

    if (cubismModel == null) {
      try {
        val mocBytes = File(mocPath).readBytes()
        cubismMoc = CubismMoc.create(mocBytes)
        cubismModel = cubismMoc?.createModel()
        if (cubismModel == null) {
          onError?.invoke("Create CubismModel failed")
          return
        }
        cubismRenderer = CubismRendererAndroid.create() as CubismRendererAndroid
        cubismRenderer!!.initialize(cubismModel!!)
        // 初始化 EyeBlink & Breath
        eyeBlink = CubismEyeBlink.create()
        breath = CubismBreath.create()
        // 加载 Physics（如果有）
        physicsPathAbs?.let { p ->
          try {
            physics = CubismPhysics.create(File(p).readBytes())
          } catch (_: Exception) {}
        }
        Log.d("Live2DRenderer", "Cubism model created")
      } catch (e: Exception) {
        Log.e("Live2DRenderer", "Create model failed: ${e.message}")
        onError?.invoke("Create model failed: ${e.message}")
        return
      }
    }

    if (textureId != 0 && cubismRenderer != null && !boundTextureIndex0) {
      cubismRenderer!!.bindTexture(0, textureId)
      boundTextureIndex0 = true
      Log.d("Live2DRenderer", "Cubism bindTexture(0, $textureId)")
    }
  }

  private fun readTextFromUriOrFile(path: String): String? {
    return try {
      if (path.startsWith("file://")) {
        val p = path.removePrefix("file://")
        File(p).readText()
      } else if (path.startsWith("/")) {
        File(path).readText()
      } else {
        // assets 相对路径
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun resolveLocalPath(path: String): String {
    return if (path.startsWith("file://")) path.removePrefix("file://") else path
  }

  private fun createGlTexture(bitmap: Bitmap): Int {
    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    val id = textures[0]
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    return id
  }

  private fun buildProgram(vsSrc: String, fsSrc: String): Int {
    fun compile(type: Int, src: String): Int {
      val s = GLES20.glCreateShader(type)
      GLES20.glShaderSource(s, src)
      GLES20.glCompileShader(s)
      val compiled = IntArray(1)
      GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, compiled, 0)
      if (compiled[0] == 0) {
        val log = GLES20.glGetShaderInfoLog(s)
        GLES20.glDeleteShader(s)
        throw RuntimeException("Shader compile error: $log")
      }
      return s
    }
    val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
    val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
    val prog = GLES20.glCreateProgram()
    GLES20.glAttachShader(prog, vs)
    GLES20.glAttachShader(prog, fs)
    GLES20.glLinkProgram(prog)
    val linked = IntArray(1)
    GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
    if (linked[0] == 0) {
      val log = GLES20.glGetProgramInfoLog(prog)
      GLES20.glDeleteProgram(prog)
      throw RuntimeException("Program link error: $log")
    }
    GLES20.glDeleteShader(vs)
    GLES20.glDeleteShader(fs)
    return prog
  }

  private fun toFloatBuffer(data: FloatArray): java.nio.FloatBuffer {
    val bb = java.nio.ByteBuffer.allocateDirect(data.size * 4)
    bb.order(java.nio.ByteOrder.nativeOrder())
    val fb = bb.asFloatBuffer()
    fb.put(data)
    fb.position(0)
    return fb
  }
}


