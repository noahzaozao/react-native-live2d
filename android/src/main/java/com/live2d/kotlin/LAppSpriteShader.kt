package com.live2d.kotlin

import android.opengl.GLES20
import com.live2d.kotlin.LAppDefine
import com.live2d.kotlin.LAppPal
import com.live2d.sdk.cubism.framework.utils.CubismDebug

/**
 * スプライト用のシェーダー設定を保持するクラス
 */
class LAppSpriteShader : AutoCloseable {
    
    private val programId: Int // シェーダーID

    /**
     * コンストラクタ
     */
    init {
        programId = createShader()
    }

    override fun close() {
        // プログラムオブジェクトを削除する
        GLES20.glDeleteProgram(programId)
    }

    /**
     * シェーダーIDを取得する。
     *
     * @return シェーダーID
     */
    fun getShaderId(): Int {
        return programId
    }

    /**
     * シェーダーを作成する。
     *
     * @return シェーダーID。正常に作成できなかった場合は0を返す。
     */
    private fun createShader(): Int {
        // シェーダーのパスの作成
        val vertShaderFile = "${LAppDefine.ResourcePath.SHADER_ROOT.path}/${LAppDefine.ResourcePath.VERT_SHADER.path}"
        val fragShaderFile = "${LAppDefine.ResourcePath.SHADER_ROOT.path}/${LAppDefine.ResourcePath.FRAG_SHADER.path}"

        // シェーダーのコンパイル
        val vertexShaderId = compileShader(vertShaderFile, GLES20.GL_VERTEX_SHADER)
        val fragmentShaderId = compileShader(fragShaderFile, GLES20.GL_FRAGMENT_SHADER)

        if (vertexShaderId == 0 || fragmentShaderId == 0) {
            return 0
        }

        // プログラムオブジェクトの作成
        val programId = GLES20.glCreateProgram()

        // Programのシェーダーを設定
        GLES20.glAttachShader(programId, vertexShaderId)
        GLES20.glAttachShader(programId, fragmentShaderId)

        GLES20.glLinkProgram(programId)
        GLES20.glUseProgram(programId)

        // 不要になったシェーダーオブジェクトの削除
        GLES20.glDeleteShader(vertexShaderId)
        GLES20.glDeleteShader(fragmentShaderId)

        return programId
    }

    /**
     * CreateShader内部関数。エラーチェックを行う。
     *
     * @param shaderId シェーダーID
     * @return エラーチェック結果。trueの場合、エラーなし。
     */
    private fun checkShader(shaderId: Int): Boolean {
        val logLength = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_INFO_LOG_LENGTH, logLength, 0)

        if (logLength[0] > 0) {
            val log = GLES20.glGetShaderInfoLog(shaderId)
            CubismDebug.cubismLogError("Shader compile log: %s", log)
        }

        val status = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0)

        if (status[0] == GLES20.GL_FALSE) {
            GLES20.glDeleteShader(shaderId)
            return false
        }

        return true
    }

    /**
     * シェーダーをコンパイルする。
     * コンパイルに成功したら0を返す。
     *
     * @param fileName シェーダーファイル名
     * @param shaderType 作成するシェーダーの種類
     * @return シェーダーID。正常に作成できなかった場合は0を返す。
     */
    private fun compileShader(fileName: String, shaderType: Int): Int {
        // ファイル読み込み
        val shaderBuffer = LAppPal.loadFileAsBytes(fileName)

        // コンパイル
        val shaderId = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shaderId, String(shaderBuffer))
        GLES20.glCompileShader(shaderId)

        if (!checkShader(shaderId)) {
            return 0
        }

        return shaderId
    }
}