/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import com.live2d.kotlin.LAppDefine
import com.live2d.kotlin.LAppPal
import com.live2d.kotlin.LAppDelegate
import com.live2d.kotlin.LAppModel
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.motion.ACubismMotion
import com.live2d.sdk.cubism.framework.motion.IBeganMotionCallback
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback
import java.util.Collections

/**
 * サンプルアプリケーションにおいてCubismModelを管理するクラス。
 * モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
 */
class LAppLive2DManager private constructor() {
    
    // 使用线程安全的列表来避免多线程并发问题
    private val models: MutableList<LAppModel> = Collections.synchronizedList(ArrayList())

    // onUpdateメソッドで使用されるキャッシュ変数
    private val viewMatrix = CubismMatrix44.create()
    private val projection = CubismMatrix44.create()

    // 由外部（如 RN 层）控制的缩放因子，默认 1.0
    private var userScale: Float = 1.0f

    // 由外部控制的位置偏移，默认居中 (0,0)
    private var userOffsetX: Float = 0.0f
    private var userOffsetY: Float = 0.0f

    init {
        LAppPal.printLog("LAppLive2DManager constructor (empty function)")
    }

    /**
     * 現在のシーンで保持している全てのモデルを解放する
     */
    fun releaseAllModel() {
        for (model in models) {
            model.deleteModel()
        }
        models.clear()
    }

    /**
     * 初始化模型管理器（移除assets扫描逻辑，统一使用文件系统路径）
     */
    fun setUpModel() {
        LAppPal.printLog("LAppLive2DManager setUpModel (empty function)")
    }

    // モデル更新処理及び描画処理を行う
    fun onUpdate() {
        val width = LAppDelegate.getInstance().windowWidth
        val height = LAppDelegate.getInstance().windowHeight

        for (i in models.indices) {
            val model = models[i]

            if (model.model == null) {
                LAppPal.printLog("Failed to model.getModel() for model $i - skipping render")
                continue
            }

            // 投影矩阵を初期化
            projection.loadIdentity()

            if (model.model!!.canvasWidth > 1.0f && width < height) {
                model.modelMatrix?.setWidth(2.0f)
                projection.scale(1.0f, width.toFloat() / height.toFloat())
            } else {
                projection.scale(height.toFloat() / width.toFloat(), 1.0f)
            }

            // 必要があればここで乗算する
            viewMatrix.multiplyByMatrix(projection)

            projection.translateRelative(userOffsetX, userOffsetY)
            projection.scaleRelative(userScale, userScale)
            
            // モデル1体描画前コール
            LAppDelegate.getInstance().view?.preModelDraw(model)

            model.update()

            model.draw(projection)     // 参照渡しなのでprojectionは変質する

            // モデル1体描画後コール
            LAppDelegate.getInstance().view?.postModelDraw(model)
        }
    }

    /**
     * 设置用户期望的等比缩放比例
     */
    fun setUserScale(scale: Float) {
        // 限制缩放范围在 0.1 到 10.0 之间
        userScale = scale.coerceIn(0.1f, 10.0f)
    }

    /**
     * 设置用户期望的模型位置偏移（逻辑坐标系，原点为中心，向右为正X，向上为正Y）。
     */
    fun setUserPosition(x: Float, y: Float) {
        userOffsetX = x
        userOffsetY = y
    }

    /**
     * 画面をドラッグした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    fun onDrag(x: Float, y: Float) {
        for (i in models.indices) {
            val model = getModel(i)
            model?.setDragging(x, y)
        }
    }

    /**
     * 画面をタップした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    fun onTap(x: Float, y: Float) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("tap point: {$x, y: $y")
        }

        for (i in models.indices) {
            val model = models[i]

            // 頭をタップした場合表情をランダムで再生する
            if (model.hitTest(LAppDefine.HitAreaName.HEAD.id, x, y)) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: ${LAppDefine.HitAreaName.HEAD.id}")
                }
                model.setRandomExpression()
            }
            // 体をタップした場合ランダムモーションを開始する
            else if (model.hitTest(LAppDefine.HitAreaName.BODY.id, x, y)) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: ${LAppDefine.HitAreaName.BODY.id}")
                }

                model.startRandomMotion(LAppDefine.MotionGroup.TAP_BODY.id, LAppDefine.Priority.NORMAL.priority, finishedMotion, beganMotion)
            }
        }
    }

    /**
     * 現在のシーンで保持しているモデルを返す
     *
     * @param number モデルリストのインデックス値
     * @return モデルのインスタンスを返す。インデックス値が範囲外の場合はnullを返す
     */
    fun getModel(number: Int): LAppModel? {
        return if (number < models.size) {
            models[number]
        } else {
            null
        }
    }

    /**
     * 現在のシーンで保持しているモデル数を返す
     *
     * @return モデル数
     */
    fun getModelNum(): Int {
        return models.size
    }

    /**
     * Add a model to the manager
     *
     * @param model The model to add
     */
    fun addModel(model: LAppModel) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("addModel: Starting - Thread: ${Thread.currentThread().name}")
            LAppPal.printLog("addModel: Model parameter: not null")
            LAppPal.printLog("addModel: Models list: not null")
            LAppPal.printLog("addModel: Adding model, current count: ${models.size}")
            // 打印调用栈来追踪模型创建来源
            val stackTrace = Thread.currentThread().stackTrace
            for (i in 0 until minOf(5, stackTrace.size)) {
                LAppPal.printLog("addModel: Stack[$i]: ${stackTrace[i]}")
            }
        }

        try {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("addModel: About to add model to list")
            }
            models.add(model)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("addModel: Model added successfully, new count: ${models.size}")
                LAppPal.printLog("addModel: Completed successfully")
            }
        } catch (e: Exception) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("addModel: Exception occurred while adding model: ${e.message}")
                e.printStackTrace()
            }
            throw e // 重新抛出异常
        }
    }

    /**
     * モーション再生時に実行されるコールバック関数
     */
    private class BeganMotion : IBeganMotionCallback {
        override fun execute(motion: ACubismMotion) {
            LAppPal.printLog("Motion Began: $motion")
        }
    }

    /**
     * モーション終了時に実行されるコールバック関数
     */
    private class FinishedMotion : IFinishedMotionCallback {
        override fun execute(motion: ACubismMotion) {
            LAppPal.printLog("Motion Finished: $motion")
        }
    }

    companion object {
        private val beganMotion = BeganMotion()
        private val finishedMotion = FinishedMotion()

        /**
         * シングルトンインスタンス - 使用 lazy 委托确保线程安全
         */
        private val _instance: LAppLive2DManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            LAppLive2DManager()
        }

        fun getInstance(): LAppLive2DManager = _instance

        /**
         * 释放所有模型（清理模型列表）
         * 注意：由于使用 lazy 委托，单例实例本身不能被释放
         */
        fun releaseInstance() {
            _instance.releaseAllModel()
        }
    }
}