/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import android.opengl.GLES20
import android.opengl.GLES20.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class LAppSprite(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    private val textureId: Int,
    private val programId: Int
) {
    
    /**
     * Rectクラス
     */
    private class Rect {
        /**
         * 左辺
         */
        var left: Float = 0f
        /**
         * 右辺
         */
        var right: Float = 0f
        /**
         * 上辺
         */
        var up: Float = 0f
        /**
         * 下辺
         */
        var down: Float = 0f
    }

    private val rect = Rect()
    private val positionLocation: Int  // 位置アトリビュート
    private val uvLocation: Int // UVアトリビュート
    private val textureLocation: Int   // テクスチャアトリビュート
    private val colorLocation: Int     // カラーアトリビュート
    private val spriteColor = FloatArray(4)   // 表示カラー

    private var maxWidth: Int = 0   // ウィンドウ幅
    private var maxHeight: Int = 0  // ウィンドウ高さ

    private val uvVertex = FloatArray(8)
    private val positionVertex = FloatArray(8)

    private var posVertexFloatBuffer: FloatBuffer? = null
    private var uvVertexFloatBuffer: FloatBuffer? = null

    init {
        rect.left = x - width * 0.5f
        rect.right = x + width * 0.5f
        rect.up = y + height * 0.5f
        rect.down = y - height * 0.5f

        // 何番目のattribute変数か
        positionLocation = GLES20.glGetAttribLocation(programId, "position")
        uvLocation = GLES20.glGetAttribLocation(programId, "uv")
        textureLocation = GLES20.glGetUniformLocation(programId, "texture")
        colorLocation = GLES20.glGetUniformLocation(programId, "baseColor")

        spriteColor[0] = 1.0f
        spriteColor[1] = 1.0f
        spriteColor[2] = 1.0f
        spriteColor[3] = 1.0f
    }

    fun render() {
        // 确保使用精灵着色器
        GLES20.glUseProgram(programId)
        // 添加调试日志
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            Log.d("LAppSprite", "render: Starting sprite render - TextureID: $textureId" + 
                ", Window: ${maxWidth}x$maxHeight" + 
                ", Rect: (${rect.left}, ${rect.up}, ${rect.right}, ${rect.down})")
        }
        
        // Set the camera position (View matrix)
        uvVertex[0] = 1.0f
        uvVertex[1] = 0.0f
        uvVertex[2] = 0.0f
        uvVertex[3] = 0.0f
        uvVertex[4] = 0.0f
        uvVertex[5] = 1.0f
        uvVertex[6] = 1.0f
        uvVertex[7] = 1.0f

        // 透過設定
        GLES20.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glEnableVertexAttribArray(uvLocation)

        GLES20.glUniform1i(textureLocation, 0)

        // 頂点データ
        positionVertex[0] = (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f)
        positionVertex[1] = (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f)
        positionVertex[2] = (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f)
        positionVertex[3] = (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f)
        positionVertex[4] = (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f)
        positionVertex[5] = (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f)
        positionVertex[6] = (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f)
        positionVertex[7] = (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f)

        if (posVertexFloatBuffer == null) {
            val posVertexByteBuffer = ByteBuffer.allocateDirect(positionVertex.size * 4)
            posVertexByteBuffer.order(ByteOrder.nativeOrder())
            posVertexFloatBuffer = posVertexByteBuffer.asFloatBuffer()
        }
        if (uvVertexFloatBuffer == null) {
            val uvVertexByteBuffer = ByteBuffer.allocateDirect(uvVertex.size * 4)
            uvVertexByteBuffer.order(ByteOrder.nativeOrder())
            uvVertexFloatBuffer = uvVertexByteBuffer.asFloatBuffer()
        }
        posVertexFloatBuffer?.put(positionVertex)?.position(0)
        uvVertexFloatBuffer?.put(uvVertex)?.position(0)

        glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, posVertexFloatBuffer)
        glVertexAttribPointer(uvLocation, 2, GL_FLOAT, false, 0, uvVertexFloatBuffer)

        GLES20.glUniform4f(colorLocation, spriteColor[0], spriteColor[1], spriteColor[2], spriteColor[3])

        GLES20.glBindTexture(GL_TEXTURE_2D, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
    }

    /**
     * テクスチャIDを指定して描画する
     *
     * @param textureId テクスチャID
     * @param uvVertex uv頂点座標
     */
    fun renderImmediate(textureId: Int, uvVertex: FloatArray) {
        // attribute属性を有効にする
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glEnableVertexAttribArray(uvLocation)

        // uniform属性の登録
        GLES20.glUniform1i(textureLocation, 0)

        // 頂点データ
        val positionVertex = floatArrayOf(
            (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f),
            (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f),
            (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f),
            (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f)
        )

        // attribute属性を登録
        run {
            val bb = ByteBuffer.allocateDirect(positionVertex.size * 4)
            bb.order(ByteOrder.nativeOrder())
            val buffer = bb.asFloatBuffer()
            buffer.put(positionVertex)
            buffer.position(0)

            GLES20.glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, buffer)
        }
        run {
            val bb = ByteBuffer.allocateDirect(uvVertex.size * 4)
            bb.order(ByteOrder.nativeOrder())
            val buffer = bb.asFloatBuffer()
            buffer.put(uvVertex)
            buffer.position(0)

            GLES20.glVertexAttribPointer(uvLocation, 2, GL_FLOAT, false, 0, buffer)
        }

        GLES20.glUniform4f(colorLocation, spriteColor[0], spriteColor[1], spriteColor[2], spriteColor[3])

        // モデルの描画
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
    }

    // リサイズする
    fun resize(x: Float, y: Float, width: Float, height: Float) {
        rect.left = x - width * 0.5f
        rect.right = x + width * 0.5f
        rect.up = y + height * 0.5f
        rect.down = y - height * 0.5f
    }

    /**
     * 画像との当たり判定を行う
     *
     * @param pointX タッチした点のx座標
     * @param pointY タッチした点のy座標
     * @return 当たっていればtrue
     */
    fun isHit(pointX: Float, pointY: Float): Boolean {
        // y座標は変換する必要あり
        val y = maxHeight - pointY

        return (pointX >= rect.left && pointX <= rect.right && y <= rect.up && y >= rect.down)
    }

    fun setColor(r: Float, g: Float, b: Float, a: Float) {
        spriteColor[0] = r
        spriteColor[1] = g
        spriteColor[2] = b
        spriteColor[3] = a
    }

    /**
     * ウィンドウサイズを設定する。
     *
     * @param width 横幅
     * @param height 高さ
     */
    fun setWindowSize(width: Int, height: Int) {
        maxWidth = width
        maxHeight = height
    }
}