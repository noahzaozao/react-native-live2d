/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import com.live2d.kotlin.LAppDefine
import com.live2d.kotlin.LAppPal
import com.live2d.kotlin.LAppDelegate
import com.live2d.sdk.cubism.framework.CubismFramework
import java.io.IOException
import java.io.InputStream
import java.io.FileInputStream
import java.io.File

/**
 * テクスチャの管理を行うクラス
 */
class LAppTextureManager {
    
    /**
     * 画像情報データクラス
     */
    data class TextureInfo(
        var id: Int = 0,        // テクスチャID
        var width: Int = 0,     // 横幅
        var height: Int = 0,    // 高さ
        var filePath: String = ""  // ファイル名
    )

    private val textures: MutableList<TextureInfo> = ArrayList()

    /**
     * 画像読み込み
     * imageFileOffset: glGenTexturesで作成したテクスチャの保存場所
     */
    fun createTextureFromPngFile(filePath: String): TextureInfo? {
        // search loaded texture already
        for (textureInfo in textures) {
            if (textureInfo.filePath == filePath) {
                return textureInfo
            }
        }

        // assetsフォルダの画像からビットマップを作成する
        val assetManager: AssetManager = LAppDelegate.getInstance().getActivity()!!.assets
        var stream: InputStream? = null
        try {
            stream = assetManager.open(filePath)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        
        // decodeStreamは乗算済みアルファとして画像を読み込むようである
        val bitmap: Bitmap? = BitmapFactory.decodeStream(stream)
        
        if (bitmap == null) {
            return null
        }

        // Texture0をアクティブにする
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        // OpenGLにテクスチャを生成
        val textureId = IntArray(1)
        GLES20.glGenTextures(1, textureId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])

        // 对齐与环绕参数，防止某些设备出现读取与采样异常
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // メモリ上の2D画像をテクスチャに割り当てる
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // ミップマップを生成する
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)

        // 縮小时の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        // 拡大時の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val textureInfo = TextureInfo(
            id = textureId[0],
            width = bitmap.width,
            height = bitmap.height,
            filePath = filePath
        )

        textures.add(textureInfo)

        // bitmap解放
        bitmap.recycle()

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            CubismFramework.coreLogFunction("createTextureFromPngFile: Create texture: $filePath")
        }

        return textureInfo
    }

    /**
     * 从文件系统加载纹理
     */
    fun createTextureFromFileSystem(filePath: String): TextureInfo? {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("createTextureFromFileSystem: Attempting to load $filePath")
        }
        
        // search loaded texture already
        for (textureInfo in textures) {
            if (textureInfo.filePath == filePath) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("createTextureFromFileSystem: Texture already loaded $filePath")
                }
                return textureInfo
            }
        }

        // 从文件系统读取图片文件
        var stream: FileInputStream? = null
        try {
            val file = File(filePath)
            LAppPal.printLogLazy { "createTextureFromFileSystem: File exists check - ${file.exists()} for $filePath" }
            LAppPal.printLogLazy { "createTextureFromFileSystem: File absolute path - ${file.absolutePath}" }
            LAppPal.printLogLazy { "createTextureFromFileSystem: File can read - ${file.canRead()}" }
            LAppPal.printLogLazy { "createTextureFromFileSystem: File size - ${file.length()} bytes" }
            if (!file.exists()) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("createTextureFromFileSystem: Texture file not found: $filePath")
                }
                return null
            }
            if (!file.canRead()) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("createTextureFromFileSystem: Cannot read texture file: $filePath")
                }
                return null
            }
            stream = FileInputStream(file)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("createTextureFromFileSystem: FileInputStream created successfully for $filePath")
            }
        } catch (e: Exception) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("createTextureFromFileSystem: Exception opening file $filePath: ${e.message}")
                LAppPal.printLog("createTextureFromFileSystem: Exception type: ${e.javaClass.simpleName}")
            }
            e.printStackTrace()
            return null
        }

        // decodeStreamは乗算済みアルファとして画像を読み込むようである
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("createTextureFromFileSystem: Attempting to decode bitmap from stream")
        }
        
        val bitmap: Bitmap? = BitmapFactory.decodeStream(stream)
        
        try {
            stream.close()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("createTextureFromFileSystem: FileInputStream closed successfully")
            }
        } catch (e: Exception) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("createTextureFromFileSystem: Exception closing stream: ${e.message}")
            }
            e.printStackTrace()
        }
        
        if (bitmap == null) {
            LAppPal.printLogLazy { "createTextureFromFileSystem: Failed to decode bitmap from: $filePath" }
            LAppPal.printLogLazy { "createTextureFromFileSystem: BitmapFactory.decodeStream returned null" }
            return null
        }
        
        LAppPal.printLogLazy { "createTextureFromFileSystem: Bitmap decoded successfully - Size: ${bitmap.width}x${bitmap.height}" }

        // Texture0をアクティブにする
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        // OpenGLにテクスチャを生成
        val textureId = IntArray(1)
        GLES20.glGenTextures(1, textureId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])

        // メモリ上の2D画像をテクスチャに割り当てる
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // ミップマップを生成する
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)

        // 縮小时の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        // 拡大時の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val textureInfo = TextureInfo(
            id = textureId[0],
            width = bitmap.width,
            height = bitmap.height,
            filePath = filePath
        )

        textures.add(textureInfo)

        // bitmap解放
        bitmap.recycle()

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("createTextureFromFileSystem: Create texture from file system: $filePath")
        }

        return textureInfo
    }

    /**
     * 释放由本管理器创建的所有 OpenGL 纹理。需在 GL 线程调用。
     */
    fun dispose() {
        if (textures.isEmpty()) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("dispose: No textures to dispose")
            }
            return
        }
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("dispose: Disposing ${textures.size} textures")
        }
        
        try {
            val ids = IntArray(textures.size)
            for (i in textures.indices) {
                ids[i] = textures[i].id
            }
            
            if (ids.isNotEmpty()) {
                GLES20.glDeleteTextures(ids.size, ids, 0)
                
                // 检查 OpenGL 错误
                val error = GLES20.glGetError()
                if (error != GLES20.GL_NO_ERROR) {
                    LAppPal.printLog("dispose: Failed to delete textures, GL error: $error")
                } else if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("dispose: Successfully deleted ${ids.size} textures")
                }
            }
        } catch (e: Exception) {
            LAppPal.printLog("dispose: Exception during texture disposal: ${e.message}")
            e.printStackTrace()
        } finally {
            textures.clear()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("dispose: Texture list cleared")
            }
        }
    }
}