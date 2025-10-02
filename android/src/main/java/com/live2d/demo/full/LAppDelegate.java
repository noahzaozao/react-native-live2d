/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.app.Activity;
import android.opengl.GLES20;
import android.os.Build;
import com.live2d.demo.LAppDefine;
import com.live2d.sdk.cubism.framework.CubismFramework;

import static android.opengl.GLES20.*;

public class LAppDelegate {
    public static LAppDelegate getInstance() {
        if (s_instance == null) {
            s_instance = new LAppDelegate();
        }
        return s_instance;
    }

    /**
     * クラスのインスタンス（シングルトン）を解放する。
     */
    public static void releaseInstance() {
        if (s_instance != null) {
            s_instance = null;
        }
    }

    /**
     * アプリケーションを非アクティブにする
     */
    public void deactivateApp() {
        isActive = false;
    }

    public void onStart(Activity activity) {
        textureManager = new LAppTextureManager();
        view = new LAppView();

        this.activity = activity;

        LAppPal.updateTime();
    }

    public void onPause() {
        currentModel = LAppLive2DManager.getInstance().getCurrentModel();
    }

    public void onStop() {
        if (view != null) {
            view.close();
            view = null;  // 显式设置为null，避免状态不一致
        }
        textureManager = null;

        LAppLive2DManager.releaseInstance();
        CubismFramework.dispose();
    }

    public void onDestroy() {
        releaseInstance();
    }

    public void onSurfaceCreated() {
        android.util.Log.d("LAppDelegate", "onSurfaceCreated: Starting OpenGL initialization");
        
        // テクスチャサンプリング設定
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        android.util.Log.d("LAppDelegate", "onSurfaceCreated: Texture parameters set");

        // 透過設定
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        android.util.Log.d("LAppDelegate", "onSurfaceCreated: Blend mode set to GL_ONE, GL_ONE_MINUS_SRC_ALPHA");

        // Check OpenGL errors
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            android.util.Log.e("LAppDelegate", "OpenGL error after initial setup: " + error);
        }

        // Initialize Cubism SDK framework
        CubismFramework.initialize();

        android.util.Log.d("LAppDelegate", "onSurfaceCreated: CubismFramework initialized");
    }

    public void onSurfaceChanged(int width, int height) {
        
        android.util.Log.d("LAppDelegate", "onSurfaceChanged: Starting OpenGL initialization");

        // 描画範囲指定
        GLES20.glViewport(0, 0, width, height);

        android.util.Log.d("LAppDelegate", "onSurfaceChanged: glViewport set to " + width + "x" + height);

        windowWidth = width;
        windowHeight = height;

        // AppViewの初期化 - 添加空指针检查
        if (view != null) {
            
            android.util.Log.d("LAppDelegate", "onSurfaceChanged: view is not null, initializing");

            view.initialize();
            
            view.initializeSprite();

        } else {
            android.util.Log.e("LAppDelegate", "view is null in onSurfaceChanged, cannot initialize");
            return;
        }

        // 模型加载现在通过文件系统路径进行，不再在此处自动加载
        android.util.Log.d("LAppDelegate", "onSurfaceChanged: Model loading is now handled via file system paths");

        isActive = true;
    }

    public void run() {
        // 時間更新
        LAppPal.updateTime();

        // 画面初期化
        glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearDepthf(1.0f);

        if (view != null) {
            view.render();
        }

        // アプリケーションを非アクティブにする
        if (!isActive) {
            activity.finishAndRemoveTask();
        }
    }


    public void onTouchBegan(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = true;
            view.onTouchesBegan(mouseX, mouseY);
        }
    }

    public void onTouchEnd(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = false;
            view.onTouchesEnded(mouseX, mouseY);
        }
    }

    public void onTouchMoved(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (isCaptured && view != null) {
            view.onTouchesMoved(mouseX, mouseY);
        }
    }

    // getter, setter群
    public Activity getActivity() {
        return activity;
    }

    public LAppTextureManager getTextureManager() {
        return textureManager;
    }

    public LAppView getView() {
        return view;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    private static LAppDelegate s_instance;

    private LAppDelegate() {
        currentModel = 0;

        // Set up Cubism SDK framework.
        cubismOption.logFunction = new LAppPal.PrintLogFunction();
        cubismOption.loggingLevel = LAppDefine.cubismLoggingLevel;

        CubismFramework.cleanUp();
        CubismFramework.startUp(cubismOption);
    }

    private Activity activity;

    private final CubismFramework.Option cubismOption = new CubismFramework.Option();

    private LAppTextureManager textureManager;
    private LAppView view;
    private int windowWidth;
    private int windowHeight;
    private boolean isActive = true;

    /**
     * モデルシーンインデックス
     */
    private int currentModel;

    /**
     * クリックしているか
     */
    private boolean isCaptured;
    /**
     * マウスのX座標
     */
    private float mouseX;
    /**
     * マウスのY座標
     */
    private float mouseY;
}
