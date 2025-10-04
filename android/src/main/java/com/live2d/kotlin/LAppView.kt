/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import android.opengl.GLES20
import android.opengl.GLES20.*
import com.live2d.kotlin.LAppDefine
import com.live2d.kotlin.TouchManager
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.math.CubismViewMatrix
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenSurfaceAndroid

class LAppView : AutoCloseable {
    /**
     * レンダリング先
     */
    enum class RenderingTarget {
        NONE,   // デフォルトのフレームバッファにレンダリング
        MODEL_FRAME_BUFFER,     // LAppModelForSmallDemoが各自持つフレームバッファにレンダリング
        VIEW_FRAME_BUFFER  // LAppViewForSmallDemoが持つフレームバッファにレンダリング
    }

    /**
     * レンダリングターゲットのクリアカラー
     */
    private val clearColor = FloatArray(4)

    init {
        clearColor[0] = 1.0f
        clearColor[1] = 1.0f
        clearColor[2] = 1.0f
        clearColor[3] = 0.0f
    }

    override fun close() {
        spriteShader?.close()
    }

    // ビューを初期化する
    fun initialize() {
        val width = LAppDelegate.getInstance().windowWidth
        val height = LAppDelegate.getInstance().windowHeight

        val ratio = width.toFloat() / height.toFloat()
        val left = -ratio
        val right = ratio
        val bottom = LAppDefine.LogicalView.LEFT.value
        val top = LAppDefine.LogicalView.RIGHT.value

        // デバイスに対応する画面範囲。Xの左端、Xの右端、Yの下端、Yの上端
        viewMatrix.setScreenRect(left, right, bottom, top)
        viewMatrix.scale(LAppDefine.Scale.DEFAULT.value, LAppDefine.Scale.DEFAULT.value)

        // 単位行列に初期化
        deviceToScreen.loadIdentity()

        if (width > height) {
            val screenW = kotlin.math.abs(right - left)
            deviceToScreen.scaleRelative(screenW / width, -screenW / width)
        } else {
            val screenH = kotlin.math.abs(top - bottom)
            deviceToScreen.scaleRelative(screenH / height, -screenH / height)
        }
        deviceToScreen.translateRelative(-width * 0.5f, -height * 0.5f)

        // 表示範囲の設定
        viewMatrix.setMaxScale(LAppDefine.Scale.MAX.value)   // 限界拡大率
        viewMatrix.setMinScale(LAppDefine.Scale.MIN.value)   // 限界縮小率

        // 表示できる最大範囲
        viewMatrix.setMaxScreenRect(
            LAppDefine.MaxLogicalView.LEFT.value,
            LAppDefine.MaxLogicalView.RIGHT.value,
            LAppDefine.MaxLogicalView.BOTTOM.value,
            LAppDefine.MaxLogicalView.TOP.value
        )

        spriteShader = LAppSpriteShader()
    }

    // 画像を初期化する
    fun initializeSprite() {
        val windowWidth = LAppDelegate.getInstance().windowWidth
        val windowHeight = LAppDelegate.getInstance().windowHeight

        val textureManager = LAppDelegate.getInstance().getTextureManager()!!

        // 背景画像の読み込み
        val backgroundPath = LAppDefine.ResourcePath.ROOT.path + LAppDefine.ResourcePath.BACK_IMAGE.path
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            android.util.Log.d("LAppView", "initializeSprite: Loading background texture from: $backgroundPath")
        }
        
        val backgroundTexture = textureManager.createTextureFromPngFile(backgroundPath)
        
        if (backgroundTexture == null) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                android.util.Log.e("LAppView", "initializeSprite: Failed to load background texture from: $backgroundPath")
            }
            return
        }
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            android.util.Log.d("LAppView", "initializeSprite: Background texture loaded - ID: ${backgroundTexture.id}" + 
                ", Width: ${backgroundTexture.width}, Height: ${backgroundTexture.height}")
        }

        // x,yは画像の中心座標
        var x = windowWidth * 0.5f
        var y = windowHeight * 0.5f
        
        // 计算适合屏幕的背景尺寸
        val aspectRatio = backgroundTexture.width.toFloat() / backgroundTexture.height
        val screenAspectRatio = windowWidth.toFloat() / windowHeight
        
        val fWidth: Float
        val fHeight: Float
        if (aspectRatio > screenAspectRatio) {
            // 背景图片更宽，以高度为准
            fHeight = windowHeight.toFloat()
            fWidth = fHeight * aspectRatio
        } else {
            // 背景图片更高，以宽度为准
            fWidth = windowWidth.toFloat()
            fHeight = fWidth / aspectRatio
        }
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            android.util.Log.d("LAppView", "initializeSprite: Background sizing - " +
                "Original: ${backgroundTexture.width}x${backgroundTexture.height}" + 
                ", Screen: ${windowWidth}x$windowHeight" + 
                ", Final: ${fWidth}x$fHeight")
        }

        val programId = spriteShader!!.getShaderId()
        
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            android.util.Log.d("LAppView", "initializeSprite: Creating background sprite - " +
                "Position: ($x, $y), Size: ($fWidth, $fHeight), ProgramID: $programId")
        }

        if (backSprite == null) {
            backSprite = LAppSprite(x, y, fWidth, fHeight, backgroundTexture.id, programId)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                android.util.Log.d("LAppView", "initializeSprite: Background sprite created")
            }
        } else {
            backSprite!!.resize(x, y, fWidth, fHeight)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                android.util.Log.d("LAppView", "initializeSprite: Background sprite resized")
            }
        }

        // 歯車画像の読み込み
        val gearTexture = textureManager.createTextureFromPngFile(LAppDefine.ResourcePath.ROOT.path + LAppDefine.ResourcePath.GEAR_IMAGE.path)
        
        if (gearTexture != null) {
            x = windowWidth - gearTexture.width * 0.5f - 96f
            y = windowHeight - gearTexture.height * 0.5f
            val gearWidth = gearTexture.width.toFloat()
            val gearHeight = gearTexture.height.toFloat()

            if (gearSprite == null) {
                gearSprite = LAppSprite(x, y, gearWidth, gearHeight, gearTexture.id, programId)
            } else {
                gearSprite!!.resize(x, y, gearWidth, gearHeight)
            }
        }

        // 電源画像の読み込み
        val powerTexture = textureManager.createTextureFromPngFile(LAppDefine.ResourcePath.ROOT.path + LAppDefine.ResourcePath.POWER_IMAGE.path)
        
        if (powerTexture != null) {
            x = windowWidth - powerTexture.width * 0.5f - 96.0f
            y = powerTexture.height * 0.5f
            val powerWidth = powerTexture.width.toFloat()
            val powerHeight = powerTexture.height.toFloat()

            if (powerSprite == null) {
                powerSprite = LAppSprite(x, y, powerWidth, powerHeight, powerTexture.id, programId)
            } else {
                powerSprite!!.resize(x, y, powerWidth, powerHeight)
            }
        }

        // 画面全体を覆うサイズ
        x = windowWidth * 0.5f
        y = windowHeight * 0.5f

        if (renderingSprite == null) {
            renderingSprite = LAppSprite(x, y, windowWidth.toFloat(), windowHeight.toFloat(), 0, programId)
        } else {
            renderingSprite!!.resize(x, y, windowWidth.toFloat(), windowHeight.toFloat())
        }
    }

    // 描画する
    fun render() {
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     android.util.Log.d("LAppView", "render: Starting render process")
        // }
        
        // 画面サイズを取得する。
        val maxWidth = LAppDelegate.getInstance().windowWidth
        val maxHeight = LAppDelegate.getInstance().windowHeight

        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     android.util.Log.d("LAppView", "render: Window size: ${maxWidth}x${maxHeight}")
        // }

        // 设置 OpenGL 状态
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // 首先渲染背景
        backSprite?.let { sprite ->
            // if (LAppDefine.DEBUG_LOG_ENABLE) {
            //     android.util.Log.d("LAppView", "render: Rendering background sprite")
            // }
            sprite.setWindowSize(maxWidth, maxHeight)
            sprite.render()
        } ?: run {
            // if (LAppDefine.DEBUG_LOG_ENABLE) {
            //     android.util.Log.w("LAppView", "render: backSprite is null!")
            // }
        }

        // 然后渲染Live2D模型
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     android.util.Log.d("LAppView", "render: About to call live2dManager.onUpdate()")
        // }
        val live2dManager = LAppLive2DManager.getInstance()
        live2dManager.onUpdate()
        
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     android.util.Log.d("LAppView", "render: live2dManager.onUpdate() completed")
        // }

        // 最后渲染UI元素，显示在最上层
        gearSprite?.let { sprite ->
            sprite.setWindowSize(maxWidth, maxHeight)
            sprite.render()
        }
        powerSprite?.let { sprite ->
            sprite.setWindowSize(maxWidth, maxHeight)
            sprite.render()
        }
        
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     android.util.Log.d("LAppView", "render: Render process completed")
        // }

        if (isChangedModel) {
            isChangedModel = false
            // 场景切换功能已移除，现在统一使用文件系统路径加载模型
            android.util.Log.d("LAppView", "Model switching via gear button is no longer supported")
        }

        // 各モデルが持つ描画ターゲットをテクスチャとする場合
        if (renderingTarget == RenderingTarget.MODEL_FRAME_BUFFER && renderingSprite != null) {
            val uvVertex = floatArrayOf(
                1.0f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
            )

            for (i in 0 until live2dManager.getModelNum()) {
                val model = live2dManager.getModel(i)
                if (model != null) {
                    val alpha = if (i < 1) 1.0f else model.opacity    // 片方のみ不透明度を取得できるようにする。

                    renderingSprite!!.setColor(1.0f * alpha, 1.0f * alpha, 1.0f * alpha, alpha)

                    renderingSprite!!.setWindowSize(maxWidth, maxHeight)
                    renderingSprite!!.renderImmediate(model.getRenderingBuffer().colorBuffer[0], uvVertex)
                }
            }
        }
    }

    /**
     * モデル1体を描画する直前にコールされる
     *
     * @param refModel モデルデータ
     */
    fun preModelDraw(refModel: LAppModel) {
        // 別のレンダリングターゲットへ向けて描画する場合の使用するオフスクリーンサーフェス
        val useTarget: CubismOffscreenSurfaceAndroid

        // 透過設定 - 模型使用预乘Alpha混合
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     android.util.Log.d("LAppView", "preModelDraw: Blend function set to GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA")
        // }

        // 別のレンダリングターゲットへ向けて描画する場合
        if (renderingTarget != RenderingTarget.NONE) {

            // 使用するターゲット
            useTarget = if (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER)
                        refModel.getRenderingBuffer()
                        else refModel.getRenderingBuffer()

            // 描画ターゲット内部未作成の場合はここで作成
            if (!useTarget.isValid) {
                val width = LAppDelegate.getInstance().windowWidth
                val height = LAppDelegate.getInstance().windowHeight

                // モデル描画キャンバス
                useTarget.createOffscreenSurface(width, height, null)
            }
            // レンダリング開始
            useTarget.beginDraw(null)
            useTarget.clear(clearColor[0], clearColor[1], clearColor[2], clearColor[3])   // 背景クリアカラー
        }
    }

    /**
     * モデル1体を描画した直後にコールされる
     *
     * @param refModel モデルデータ
     */
    fun postModelDraw(refModel: LAppModel) {
        var useTarget: CubismOffscreenSurfaceAndroid? = null

        // 別のレンダリングターゲットへ向けて描画する場合
        if (renderingTarget != RenderingTarget.NONE) {
            // 使用するターゲット
            useTarget = if (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER)
                        refModel.getRenderingBuffer()
                        else refModel.getRenderingBuffer()

            // レンダリング終了
            useTarget.endDraw()

            // LAppViewの持つフレームバッファを使うなら、スプライトへの描画はこことなる
            if (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER && renderingSprite != null) {
                val uvVertex = floatArrayOf(
                    1.0f, 1.0f,
                    0.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 0.0f
                )
                renderingSprite!!.setColor(1.0f * getSpriteAlpha(0), 1.0f * getSpriteAlpha(0), 1.0f * getSpriteAlpha(0), getSpriteAlpha(0))

                // 画面サイズを取得する。
                val maxWidth = LAppDelegate.getInstance().windowWidth
                val maxHeight = LAppDelegate.getInstance().windowHeight

                renderingSprite!!.setWindowSize(maxWidth, maxHeight)
                renderingSprite!!.renderImmediate(useTarget.colorBuffer[0], uvVertex)
            }
        }
    }

    /**
     * レンダリング先を切り替える
     *
     * @param targetType レンダリング先
     */
    fun switchRenderingTarget(targetType: RenderingTarget) {
        renderingTarget = targetType
    }

    /**
     * タッチされたときに呼ばれる
     *
     * @param pointX スクリーンX座標
     * @param pointY スクリーンY座標
     */
    fun onTouchesBegan(pointX: Float, pointY: Float) {
        touchManager.touchesBegan(pointX, pointY)
    }

    /**
     * タッチしているときにポインターが動いたら呼ばれる
     *
     * @param pointX スクリーンX座標
     * @param pointY スクリーンY座標
     */
    fun onTouchesMoved(pointX: Float, pointY: Float) {
        val viewX = transformViewX(touchManager.getLastX())
        val viewY = transformViewY(touchManager.getLastY())

        touchManager.touchesMoved(pointX, pointY)

        LAppLive2DManager.getInstance().onDrag(viewX, viewY)
    }

    /**
     * タッチが終了したら呼ばれる
     *
     * @param pointX スクリーンX座標
     * @param pointY スクリーンY座標
     */
    fun onTouchesEnded(pointX: Float, pointY: Float) {
        // タッチ終了
        val live2DManager = LAppLive2DManager.getInstance()
        live2DManager.onDrag(0.0f, 0.0f)

        // シングルタップ
        // 論理座標変換した座標を取得
        val x = deviceToScreen.transformX(touchManager.getLastX())
        // 論理座標変換した座標を取得
        val y = deviceToScreen.transformY(touchManager.getLastY())

        if (LAppDefine.DEBUG_TOUCH_LOG_ENABLE) {
            LAppPal.printLog("Touches ended x: $x, y:$y")
        }

        live2DManager.onTap(x, y)

        // 歯車ボタンにタップしたか
        if (gearSprite!!.isHit(pointX, pointY)) {
            isChangedModel = true
        }

        // 電源ボタンにタップしたか
        if (powerSprite!!.isHit(pointX, pointY)) {
            // アプリを終了する
            LAppDelegate.getInstance().deactivateApp()
        }
    }

    /**
     * X座標をView座標に変換する
     *
     * @param deviceX デバイスX座標
     * @return ViewX座標
     */
    fun transformViewX(deviceX: Float): Float {
        // 論理座標変換した座標を取得
        val screenX = deviceToScreen.transformX(deviceX)
        // 拡大、縮小、移動後の値
        return viewMatrix.invertTransformX(screenX)
    }

    /**
     * Y座標をView座標に変換する
     *
     * @param deviceY デバイスY座標
     * @return ViewY座標
     */
    fun transformViewY(deviceY: Float): Float {
        // 論理座標変換した座標を取得
        val screenY = deviceToScreen.transformY(deviceY)
        // 拡大、縮小、移動後の値
        return viewMatrix.invertTransformX(screenY)
    }

    /**
     * X座標をScreen座標に変換する
     *
     * @param deviceX デバイスX座標
     * @return ScreenX座標
     */
    fun transformScreenX(deviceX: Float): Float = deviceToScreen.transformX(deviceX)

    /**
     * Y座標をScreen座標に変換する
     *
     * @param deviceY デバイスY座標
     * @return ScreenY座標
     */
    fun transformScreenY(deviceY: Float): Float = deviceToScreen.transformX(deviceY)

    /**
     * レンダリング先をデフォルト以外に切り替えた際の背景クリア色設定
     *
     * @param r 赤(0.0~1.0)
     * @param g 緑(0.0~1.0)
     * @param b 青(0.0~1.0)
     */
    fun setRenderingTargetClearColor(r: Float, g: Float, b: Float) {
        clearColor[0] = r
        clearColor[1] = g
        clearColor[2] = b
    }

    /**
     * 別レンダリングターゲットにモデルを描画するサンプルで描画時のαを決定する
     *
     * @param assign
     * @return
     */
    fun getSpriteAlpha(assign: Int): Float {
        // assignの数値に応じて適当な差をつける
        var alpha = 0.4f + assign.toFloat() * 0.5f

        // サンプルとしてαに適当な差をつける
        if (alpha > 1.0f) {
            alpha = 1.0f
        }
        if (alpha < 0.1f) {
            alpha = 0.1f
        }
        return alpha
    }

    /**
     * Return rendering target enum instance.
     *
     * @return rendering target
     */
    fun getRenderingTarget(): RenderingTarget = renderingTarget

    /**
     * 设置视图缩放比例（等比缩放）。
     * 注意：该缩放作用于 `viewMatrix`，用于控制模型整体显示缩放。
     * 建议在 GL 线程调用，调用后需要请求重绘。
     */
    fun setViewScale(scale: Float) {
        if (scale <= 0.0f) {
            return
        }
        // 由 Manager 负责在投影矩阵中叠加缩放
        LAppLive2DManager.getInstance().setUserScale(scale)
    }

    /**
     * 设置视图位置偏移（逻辑坐标系，以屏幕中心为原点，向右为正X，向上为正Y）。
     */
    fun setViewPosition(x: Float, y: Float) {
        LAppLive2DManager.getInstance().setUserPosition(x, y)
    }

    private val deviceToScreen = CubismMatrix44.create() // デバイス座標からスクリーン座標に変換するための行列
    private val viewMatrix = CubismViewMatrix()   // 画面表示の拡縮や移動の変換を行う行列
    private var windowWidth: Int = 0
    private var windowHeight: Int = 0

    /**
     * レンダリング先の選択肢
     */
    private var renderingTarget = RenderingTarget.NONE

    private var backSprite: LAppSprite? = null
    private var gearSprite: LAppSprite? = null
    private var powerSprite: LAppSprite? = null
    private var renderingSprite: LAppSprite? = null

    /**
     * モデルの切り替えフラグ
     */
    private var isChangedModel: Boolean = false

    private val touchManager = TouchManager()

    /**
     * シェーダー作成委譲クラス
     */
    private var spriteShader: LAppSpriteShader? = null
}