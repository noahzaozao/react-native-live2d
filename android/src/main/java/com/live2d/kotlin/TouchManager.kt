/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * タッチマネージャー
 */
class TouchManager {
    /**
     * タッチ開始時のxの値
     */
    private var startX: Float = 0f
    
    /**
     * タッチ開始時のyの値
     */
    private var startY: Float = 0f
    
    /**
     * シングルタッチ時のxの値
     */
    private var lastX: Float = 0f
    
    /**
     * シングルタッチ時のyの値
     */
    private var lastY: Float = 0f
    
    /**
     * ダブルタッチ時の1つ目のxの値
     */
    private var lastX1: Float = 0f
    
    /**
     * ダブルタッチ時の1つ目のyの値
     */
    private var lastY1: Float = 0f
    
    /**
     * ダブルタッチ時の2つ目のxの値
     */
    private var lastX2: Float = 0f
    
    /**
     * ダブルタッチ時の2つ目のyの値
     */
    private var lastY2: Float = 0f
    
    /**
     * 2本以上でタッチしたときの指の距離
     */
    private var lastTouchDistance: Float = 0f
    
    /**
     * 前回の値から今回の値へのxの移動距離
     */
    private var deltaX: Float = 0f
    
    /**
     * 前回の値から今回の値へのyの移動距離
     */
    private var deltaY: Float = 0f
    
    /**
     * このフレームで掛け合わせる拡大率。拡大操作中以外は1
     */
    private var scale: Float = 0f
    
    /**
     * シングルタッチ時はtrue
     */
    private var isTouchSingle: Boolean = false
    
    /**
     * フリップが有効かどうか
     */
    private var isFlipAvailable: Boolean = false

    /**
     * タッチ開始時のイベント
     *
     * @param deviceX タッチした画面のxの値
     * @param deviceY タッチした画面のyの値
     */
    fun touchesBegan(deviceX: Float, deviceY: Float) {
        lastX = deviceX
        lastY = deviceY

        startX = deviceX
        startY = deviceY

        lastTouchDistance = -1.0f

        isFlipAvailable = true
        isTouchSingle = true
    }

    /**
     * ドラッグ時のイベント
     *
     * @param deviceX タッチした画面のxの値
     * @param deviceY タッチした画面のyの値
     */
    fun touchesMoved(deviceX: Float, deviceY: Float) {
        lastX = deviceX
        lastY = deviceY
        lastTouchDistance = -1.0f
        isTouchSingle = true
    }

    /**
     * ドラッグ時のイベント
     *
     * @param deviceX1 1つ目のタッチした画面のxの値
     * @param deviceY1 1つ目のタッチした画面のyの値
     * @param deviceX2 2つ目のタッチした画面のxの値
     * @param deviceY2 2つ目のタッチした画面のyの値
     */
    fun touchesMoved(deviceX1: Float, deviceY1: Float, deviceX2: Float, deviceY2: Float) {
        val distance = calculateDistance(deviceX1, deviceY1, deviceX2, deviceY2)
        val centerX = (deviceX1 + deviceX2) * 0.5f
        val centerY = (deviceY1 + deviceY2) * 0.5f

        if (lastTouchDistance > 0.0f) {
            scale = (distance / lastTouchDistance).pow(0.75f)
            deltaX = calculateMovingAmount(deviceX1 - lastX1, deviceX2 - lastX2)
            deltaY = calculateMovingAmount(deviceY1 - lastY1, deviceY2 - lastY2)
        } else {
            scale = 1.0f
            deltaX = 0.0f
            deltaY = 0.0f
        }

        lastX = centerX
        lastY = centerY
        lastX1 = deviceX1
        lastY1 = deviceY1
        lastX2 = deviceX2
        lastY2 = deviceY2
        lastTouchDistance = distance
        isTouchSingle = false
    }

    /**
     * フリックの距離を測定する
     *
     * @return フリック距離
     */
    fun calculateGetFlickDistance(): Float {
        return calculateDistance(startX, startY, lastX, lastY)
    }

    // ----- getter methods -----
    fun getStartX(): Float = startX

    fun getStartY(): Float = startY

    fun getLastX(): Float = lastX

    fun getLastY(): Float = lastY

    fun getLastX1(): Float = lastX1

    fun getLastY1(): Float = lastY1

    fun getLastX2(): Float = lastX2

    fun getLastY2(): Float = lastY2

    fun getLastTouchDistance(): Float = lastTouchDistance

    fun getDeltaX(): Float = deltaX

    fun getDeltaY(): Float = deltaY

    fun getScale(): Float = scale

    fun isTouchSingle(): Boolean = isTouchSingle

    fun isFlipAvailable(): Boolean = isFlipAvailable

    /**
     * 点1から点2への距離を求める
     *
     * @param x1 1つ目のタッチした画面のxの値
     * @param y1 1つ目のタッチした画面のyの値
     * @param x2 1つ目のタッチした画面のxの値
     * @param y2 1つ目のタッチした画面のyの値
     * @return 2点の距離
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
    }

    /**
     * 2つの値から、移動量を求める
     * 違う方向の場合は移動量0。同じ方向の場合は、絶対値が小さい方の値を参照する
     *
     * @param v1 1つ目の移動量
     * @param v2 2つ目の移動量
     * @return 小さい方の移動量
     */
    private fun calculateMovingAmount(v1: Float, v2: Float): Float {
        if ((v1 > 0.0f) != (v2 > 0.0f)) {
            return 0.0f
        }

        val sign = if (v1 > 0.0f) 1.0f else -1.0f
        val absoluteValue1 = abs(v1)
        val absoluteValue2 = abs(v2)

        return sign * min(absoluteValue1, absoluteValue2)
    }
}