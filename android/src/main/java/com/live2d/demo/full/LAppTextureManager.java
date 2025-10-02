/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import com.live2d.demo.LAppDefine;
import com.live2d.sdk.cubism.framework.CubismFramework;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.io.FileInputStream;
import java.io.File;

// テクスチャの管理を行うクラス
public class LAppTextureManager {
    // 画像情報データクラス
    public static class TextureInfo {
        public int id;  // テクスチャID
        public int width;   // 横幅
        public int height;  // 高さ
        public String filePath; // ファイル名
    }

    // 画像読み込み
    // imageFileOffset: glGenTexturesで作成したテクスチャの保存場所
    public TextureInfo createTextureFromPngFile(String filePath) {
        // search loaded texture already
        for (TextureInfo textureInfo : textures) {
            if (textureInfo.filePath.equals(filePath)) {
                return textureInfo;
            }
        }

        // assetsフォルダの画像からビットマップを作成する
        AssetManager assetManager = LAppDelegate.getInstance().getActivity().getAssets();
        InputStream stream = null;
        try {
            stream = assetManager.open(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // decodeStreamは乗算済みアルファとして画像を読み込むようである
        Bitmap bitmap = BitmapFactory.decodeStream(stream);

        // Texture0をアクティブにする
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // OpenGLにテクスチャを生成
        int[] textureId = new int[1];
        GLES20.glGenTextures(1, textureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);

        // 对齐与环绕参数，防止某些设备出现读取与采样异常
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // メモリ上の2D画像をテクスチャに割り当てる
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        // ミップマップを生成する
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        // 縮小时の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        // 拡大時の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        TextureInfo textureInfo = new TextureInfo();
        textureInfo.filePath = filePath;
        textureInfo.width = bitmap.getWidth();
        textureInfo.height = bitmap.getHeight();
        textureInfo.id = textureId[0];

        textures.add(textureInfo);

        // bitmap解放
        bitmap.recycle();
        bitmap = null;

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            CubismFramework.coreLogFunction("createTextureFromPngFile: Create texture: " + filePath);
        }

        return textureInfo;
    }

    // 从文件系统加载纹理
    public TextureInfo createTextureFromFileSystem(String filePath) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("createTextureFromFileSystem: Attempting to load " + filePath);
        }
        
        // search loaded texture already
        for (TextureInfo textureInfo : textures) {
            if (textureInfo.filePath.equals(filePath)) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("createTextureFromFileSystem: Texture already loaded " + filePath);
                }
                return textureInfo;
            }
        }

        // 从文件系统读取图片文件
        FileInputStream stream = null;
        try {
            File file = new File(filePath);
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("createTextureFromFileSystem: File exists check - " + file.exists() + " for " + filePath);
                LAppPal.printLog("createTextureFromFileSystem: File absolute path - " + file.getAbsolutePath());
            }
            if (!file.exists()) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("Texture file not found: " + filePath);
                }
                return null;
            }
            stream = new FileInputStream(file);
        } catch (Exception e) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("createTextureFromFileSystem: Exception opening file " + filePath + ": " + e.getMessage());
            }
            e.printStackTrace();
            return null;
        }

        // decodeStreamは乗算済みアルファとして画像を読み込むようである
        Bitmap bitmap = BitmapFactory.decodeStream(stream);
        
        try {
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (bitmap == null) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("createTextureFromFileSystem: Failed to decode bitmap from: " + filePath);
            }
            return null;
        }

        // Texture0をアクティブにする
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // OpenGLにテクスチャを生成
        int[] textureId = new int[1];
        GLES20.glGenTextures(1, textureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);

        // メモリ上の2D画像をテクスチャに割り当てる
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        // ミップマップを生成する
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        // 縮小时の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        // 拡大時の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        TextureInfo textureInfo = new TextureInfo();
        textureInfo.filePath = filePath;
        textureInfo.width = bitmap.getWidth();
        textureInfo.height = bitmap.getHeight();
        textureInfo.id = textureId[0];

        textures.add(textureInfo);

        // bitmap解放
        bitmap.recycle();
        bitmap = null;

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("createTextureFromFileSystem: Create texture from file system: " + filePath);
        }

        return textureInfo;
    }

    private final List<TextureInfo> textures = new ArrayList<TextureInfo>();        // 画像情報のリスト
}
