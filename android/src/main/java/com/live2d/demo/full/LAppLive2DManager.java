/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.IBeganMotionCallback;
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback;

import java.util.ArrayList;
import java.util.List;

import static com.live2d.demo.LAppDefine.*;

/**
 * サンプルアプリケーションにおいてCubismModelを管理するクラス。
 * モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
 */
public class LAppLive2DManager {
    public static LAppLive2DManager getInstance() {
        if (s_instance == null) {
            s_instance = new LAppLive2DManager();
        }
        return s_instance;
    }

    public static void releaseInstance() {
        s_instance = null;
    }

    /**
     * 現在のシーンで保持している全てのモデルを解放する
     */
    public void releaseAllModel() {
        for (LAppModel model : models) {
            model.deleteModel();
        }
        models.clear();
    }

    /**
     * 初始化模型管理器（移除assets扫描逻辑，统一使用文件系统路径）
     */
    public void setUpModel() {
        // 清空模型目录列表，不再从assets扫描模型
        modelDir.clear();
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("setUpModel: Assets scanning removed, using file system paths only");
        }
    }

    // モデル更新処理及び描画処理を行う
    public void onUpdate() {
        int width = LAppDelegate.getInstance().getWindowWidth();
        int height = LAppDelegate.getInstance().getWindowHeight();

        // if (DEBUG_LOG_ENABLE) {
        //     LAppPal.printLog("onUpdate: Total models: " + models.size() + ", Window size: " + width + "x" + height);
        // }

        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);
            // LAppPal.printLog("onUpdate: Processing model " + i + ", getModel() result: " + (model.getModel() != null ? "not null" : "null"));

            if (model.getModel() == null) {
                LAppPal.printLog("Failed to model.getModel() for model " + i + " - skipping render");
                continue;
            }

            // 投影矩阵を初期化
            projection.loadIdentity();

            // 画面比率を考慮した投影矩阵を設定
            float aspectRatio = (float) width / (float) height;
            
            // if (DEBUG_LOG_ENABLE) {
            //     LAppPal.printLog("onUpdate: Model " + i + " - Screen: " + width + "x" + height + ", aspect ratio: " + aspectRatio);
            // }
            
            if (aspectRatio > 1.0f) {
                // 横長画面の場合
                projection.scale(1.0f / aspectRatio, 1.0f);
                // if (DEBUG_LOG_ENABLE) {
                //     LAppPal.printLog("onUpdate: Landscape mode - scaling by (1/" + aspectRatio + ", 1.0)");
                // }
            } else {
                // 縦長画面の場合
                projection.scale(1.0f, aspectRatio);
                // if (DEBUG_LOG_ENABLE) {
                //     LAppPal.printLog("onUpdate: Portrait mode - scaling by (1.0, " + aspectRatio + ")");
                // }
            }
            
            // モデルを画面中央に配置し、適切なサイズに調整（叠加用户位移）
            projection.translateRelative(userOffsetX, userOffsetY);
            // 叠加用户缩放比例
            projection.scaleRelative(userScale, userScale);

            // if (DEBUG_LOG_ENABLE) {
            //     LAppPal.printLog("onUpdate: Projection matrix configured for model " + i);
            // }

            // モデル1体描画前コール
            LAppDelegate.getInstance().getView().preModelDraw(model);

            model.update();

            model.draw(projection);     // 参照渡しなのでprojectionは変質する

            // モデル1体描画後コール
            LAppDelegate.getInstance().getView().postModelDraw(model);
        }
    }

    /**
     * 设置用户期望的等比缩放比例
     */
    public void setUserScale(float scale) {
        if (scale <= 0.0f) {
            return;
        }
        // 可根据需要做 clamp，这里保持直接赋值
        userScale = scale;
    }

    /**
     * 设置用户期望的模型位置偏移（逻辑坐标系，原点为中心，向右为正X，向上为正Y）。
     */
    public void setUserPosition(float x, float y) {
        userOffsetX = x;
        userOffsetY = y;
    }

    /**
     * 画面をドラッグした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    public void onDrag(float x, float y) {
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = getModel(i);
            model.setDragging(x, y);
        }
    }

    /**
     * 画面をタップした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    public void onTap(float x, float y) {
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("tap point: {" + x + ", y: " + y);
        }

        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);

            // 頭をタップした場合表情をランダムで再生する
            if (model.hitTest(HitAreaName.HEAD.getId(), x, y)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: " + HitAreaName.HEAD.getId());
                }
                model.setRandomExpression();
            }
            // 体をタップした場合ランダムモーションを開始する
            else if (model.hitTest(HitAreaName.BODY.getId(), x, y)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: " + HitAreaName.HEAD.getId());
                }

                model.startRandomMotion(MotionGroup.TAP_BODY.getId(), Priority.NORMAL.getPriority(), finishedMotion, beganMotion);
            }
        }
    }

    /**
     * 次のシーンに切り替える（已弃用 - 现在统一使用文件系统路径）
     */
    @Deprecated
    public void nextScene() {
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("nextScene: This method is deprecated. Use file system paths with addModel() instead.");
        }
        // 不再支持场景切换，因为不再使用assets模型列表
        LAppPal.printLog("nextScene: Scene switching is no longer supported. Please use file system paths.");
    }

    /**
     * シーンを切り替える（已弃用 - 现在统一使用文件系统路径）
     */
    @Deprecated
    public void changeScene(int index) {
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("changeScene: This method is deprecated. Use file system paths with addModel() instead.");
            // 打印调用栈来追踪changeScene调用来源
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
                LAppPal.printLog("changeScene: Stack[" + i + "]: " + stackTrace[i].toString());
            }
        }
        
        // 不再支持从assets加载模型，直接返回
        LAppPal.printLog("changeScene: Assets loading is no longer supported. Please use file system paths.");
    }

    /**
     * 現在のシーンで保持しているモデルを返す
     *
     * @param number モデルリストのインデックス値
     * @return モデルのインスタンスを返す。インデックス値が範囲外の場合はnullを返す
     */
    public LAppModel getModel(int number) {
        if (number < models.size()) {
            return models.get(number);
        }
        return null;
    }

    /**
     * シーンインデックスを返す
     *
     * @return シーンインデックス
     */
    public int getCurrentModel() {
        return currentModel;
    }

    /**
     * Return the number of models in this LAppLive2DManager instance has.
     *
     * @return number fo models in this LAppLive2DManager instance has. If models list is null, return 0.
     */
    public int getModelNum() {
        if (models == null) {
            return 0;
        }
        return models.size();
    }

    /**
     * Add a model to the manager
     *
     * @param model The model to add
     */
    public void addModel(LAppModel model) {
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("addModel: Starting - Thread: " + Thread.currentThread().getName());
            LAppPal.printLog("addModel: Model parameter: " + (model != null ? "not null" : "null"));
            LAppPal.printLog("addModel: Models list: " + (models != null ? "not null" : "null"));
            LAppPal.printLog("addModel: Adding model, current count: " + models.size());
            // 打印调用栈来追踪模型创建来源
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
                LAppPal.printLog("addModel: Stack[" + i + "]: " + stackTrace[i].toString());
            }
        }
        
        try {
            if (DEBUG_LOG_ENABLE) {
                LAppPal.printLog("addModel: About to add model to list");
            }
            models.add(model);
            if (DEBUG_LOG_ENABLE) {
                LAppPal.printLog("addModel: Model added successfully, new count: " + models.size());
                LAppPal.printLog("addModel: Completed successfully");
            }
        } catch (Exception e) {
            if (DEBUG_LOG_ENABLE) {
                LAppPal.printLog("addModel: Exception occurred while adding model: " + e.getMessage());
                e.printStackTrace();
            }
            throw e; // 重新抛出异常
        }
    }

    /**
     * モーション再生時に実行されるコールバック関数
     */
    private static class BeganMotion implements IBeganMotionCallback {
        @Override
        public void execute(ACubismMotion motion) {
            LAppPal.printLog("Motion Began: " + motion);
        }
    }

    private static final BeganMotion beganMotion = new BeganMotion();

    /**
     * モーション終了時に実行されるコールバック関数
     */
    private static class FinishedMotion implements IFinishedMotionCallback {
        @Override
        public void execute(ACubismMotion motion) {
            LAppPal.printLog("Motion Finished: " + motion);
        }
    }

    private static final FinishedMotion finishedMotion = new FinishedMotion();

    /**
     * シングルトンインスタンス
     */
    private static LAppLive2DManager s_instance;

    private LAppLive2DManager() {
        setUpModel();
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("LAppLive2DManager initialized - models will be loaded via file system paths only");
        }
    }

    private final List<LAppModel> models = new ArrayList<>();

    /**
     * 表示するシーンのインデックス値
     */
    private int currentModel;

    /**
     * モデルディレクトリ名
     */
    private final List<String> modelDir = new ArrayList<>();

    // onUpdateメソッドで使用されるキャッシュ変数
    private final CubismMatrix44 viewMatrix = CubismMatrix44.create();
    private final CubismMatrix44 projection = CubismMatrix44.create();

    // 由外部（如 RN 层）控制的缩放因子，默认 1.0
    private float userScale = 1.0f;

    // 由外部控制的位置偏移，默认居中 (0,0)
    private float userOffsetX = 0.0f;
    private float userOffsetY = 0.0f;
}
