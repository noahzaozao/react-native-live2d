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
import com.live2d.kotlin.LAppTextureManager
import com.live2d.sdk.cubism.framework.CubismDefaultParameterId.ParameterId
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.ICubismModelSetting
import com.live2d.sdk.cubism.framework.effect.CubismBreath
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink
import com.live2d.sdk.cubism.framework.id.CubismId
import com.live2d.sdk.cubism.framework.id.CubismIdManager
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.model.CubismMoc
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.motion.ACubismMotion
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion
import com.live2d.sdk.cubism.framework.motion.CubismMotion
import com.live2d.sdk.cubism.framework.motion.IBeganMotionCallback
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback
import com.live2d.sdk.cubism.framework.rendering.CubismRenderer
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenSurfaceAndroid
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.live2d.sdk.cubism.framework.utils.CubismDebug
import java.util.*
import kotlin.random.Random

class LAppModel : CubismUserModel() {
    
    private var modelSetting: ICubismModelSetting? = null
    
    /**
     * モデルのホームディレクトリ
     */
    private var modelHomeDirectory: String = ""
    
    /**
     * デルタ時間の積算値[秒]
     */
    private var userTimeSeconds: Float = 0.0f

    /**
     * モデルに設定されたまばたき機能用パラメーターID
     */
    private val eyeBlinkIds: MutableList<CubismId> = ArrayList()
    
    /**
     * モデルに設定されたリップシンク機能用パラメーターID
     */
    private val lipSyncIds: MutableList<CubismId> = ArrayList()
    
    /**
     * 読み込まれているモーションのマップ
     */
    private val motions: MutableMap<String, ACubismMotion> = HashMap()
    
    /**
     * 読み込まれている表情のマップ
     */
    private val expressions: MutableMap<String, ACubismMotion> = HashMap()

    /**
     * パラメーターID: ParamAngleX
     */
    private lateinit var idParamAngleX: CubismId
    
    /**
     * パラメーターID: ParamAngleY
     */
    private lateinit var idParamAngleY: CubismId
    
    /**
     * パラメーターID: ParamAngleZ
     */
    private lateinit var idParamAngleZ: CubismId
    
    /**
     * パラメーターID: ParamBodyAngleX
     */
    private lateinit var idParamBodyAngleX: CubismId
    
    /**
     * パラメーターID: ParamEyeBallX
     */
    private lateinit var idParamEyeBallX: CubismId
    
    /**
     * パラメーターID: ParamEyeBallY
     */
    private lateinit var idParamEyeBallY: CubismId

    /**
     * フレームバッファ以外の描画先
     */
    private val renderingBuffer = CubismOffscreenSurfaceAndroid()
    
    /**
     * 延迟绑定的纹理信息（线程安全）
     */
    private val pendingTextures: MutableMap<Int, Int> = java.util.Collections.synchronizedMap(mutableMapOf())

    init {
        // TODO: Fix ConsistencyValidationStrategy access
        // if (LAppDefine.MOC_CONSISTENCY_VALIDATION_ENABLE) {
        //     mocConsistency = CubismMoc.ConsistencyValidationStrategy.ABORT
        // }

        // if (LAppDefine.MOTION_CONSISTENCY_VALIDATION_ENABLE) {
        //     motionConsistency = CubismMotion.ConsistencyValidationStrategy.ABORT
        // }

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            debugMode = true
        }

        // 延迟初始化ID参数，直到CubismFramework完全初始化
        initializeParameterIds()
    }

    /**
     * 初始化参数ID，确保CubismFramework已就绪
     * @throws IllegalStateException 如果CubismFramework未初始化
     */
    private fun initializeParameterIds() {
        try {
            val idManager = CubismFramework.getIdManager()
                ?: throw IllegalStateException("CubismFramework.getIdManager() returned null - Framework not initialized")

            idParamAngleX = idManager.getId(ParameterId.ANGLE_X.id)
            idParamAngleY = idManager.getId(ParameterId.ANGLE_Y.id)
            idParamAngleZ = idManager.getId(ParameterId.ANGLE_Z.id)
            idParamBodyAngleX = idManager.getId(ParameterId.BODY_ANGLE_X.id)
            idParamEyeBallX = idManager.getId(ParameterId.EYE_BALL_X.id)
            idParamEyeBallY = idManager.getId(ParameterId.EYE_BALL_Y.id)

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("Parameter IDs initialized successfully")
            }
        } catch (e: Exception) {
            LAppPal.printLog("CRITICAL: Failed to initialize parameter IDs: ${e.message}")
            throw e
        }
    }
    
    /**
     * 确保参数ID已初始化，在使用前调用
     */
    private fun ensureParameterIdsInitialized() {
        if (!::idParamAngleX.isInitialized) {
            initializeParameterIds()
        }
    }

    fun loadAssets(dir: String, fileName: String) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("loadAssets: dir=$dir, fileName=$fileName")
        }

        modelHomeDirectory = dir

        val path = "$modelHomeDirectory$fileName"

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("loadAssets: Full path=$path")
        }

        val buffer = createBuffer(path)
        if (buffer != null) {
            val setting = CubismModelSettingJson(buffer)
            setupModel(setting)

            if (model == null) {
                LAppPal.printLog("Failed to loadAssets().")
                return
            }

            setupTextures()
        } else {
            LAppPal.printLog("Failed to create buffer for model setting file: $path")
        }
    }

    fun loadAssetsFromFileSystem(filePath: String) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("loadAssetsFromFileSystem: filePath=$filePath")
        }

        // 确保参数ID已初始化
        if (!::idParamAngleX.isInitialized) {
            initializeParameterIds()
        }

        val lastSlashIndex = filePath.lastIndexOf("/")
        if (lastSlashIndex != -1) {
            modelHomeDirectory = filePath.substring(0, lastSlashIndex + 1)
        } else {
            modelHomeDirectory = ""
        }

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("loadAssetsFromFileSystem: modelHomeDirectory=$modelHomeDirectory")
        }

        val buffer = createBufferFromFileSystem(filePath)
        if (buffer != null) {
            val setting = CubismModelSettingJson(buffer)
            setupModelFromFileSystem(setting, modelHomeDirectory)

            if (model == null) {
                LAppPal.printLog("Failed to loadAssetsFromFileSystem().")
                return
            }

            setupTexturesFromFileSystem()
        } else {
            LAppPal.printLog("Failed to create buffer for model setting file: $filePath")
        }
    }

    /**
     * 释放模型资源
     */
    fun deleteModel() {
        LAppPal.printLogLazy { "deleteModel: Starting model cleanup" }
        
        try {
            // 1. 停止所有动作（优先停止，避免后续访问已清理的资源）
            try {
                motionManager.stopAllMotions()
                LAppPal.printLogLazy { "deleteModel: Motion manager stopped" }
            } catch (e: Exception) {
                LAppPal.printLog("deleteModel: Error stopping motions: ${e.message}")
            }
            
            // 2. 清理表情和动作缓存
            synchronized(expressions) {
                expressions.clear()
            }
            synchronized(motions) {
                motions.clear()
            }
            LAppPal.printLogLazy { "deleteModel: Expressions and motions cleared" }
            
            // 3. 清理离屏渲染缓冲
            try {
                renderingBuffer.destroyOffscreenSurface()
                LAppPal.printLogLazy { "deleteModel: Rendering buffer destroyed" }
            } catch (e: Exception) {
                LAppPal.printLog("deleteModel: Error destroying rendering buffer: ${e.message}")
            }
            
            // 4. 清空待绑定纹理（已是线程安全的集合）
            pendingTextures.clear()
            LAppPal.printLogLazy { "deleteModel: Pending textures cleared" }
            
            // 5. 释放效果系统资源
            eyeBlink = null
            breath = null
            pose = null
            physics = null
            LAppPal.printLogLazy { "deleteModel: Effect systems released" }
            
            // 6. 清空参数ID列表
            eyeBlinkIds.clear()
            lipSyncIds.clear()
            LAppPal.printLogLazy { "deleteModel: Parameter ID lists cleared" }
            
            // 7. 清理模型设置（保留 modelHomeDirectory 以便调试）
            modelSetting = null
            LAppPal.printLogLazy { "deleteModel: Model settings cleared" }
            
            // 注意：纹理资源由 LAppTextureManager 统一管理，在 LAppDelegate.onStop() 中释放
            // 这里不直接删除纹理，避免影响其他可能共享纹理的模型
            
            LAppPal.printLogLazy { "deleteModel: Model cleanup completed successfully" }
        } catch (e: Exception) {
            LAppPal.printLog("deleteModel: Unexpected error during cleanup: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 重新创建 GL 资源（在 GL 上下文丢失后调用）
     * 必须在 GL 线程调用
     */
    fun recreateGLResources() {
        LAppPal.printLogLazy { "recreateGLResources: Starting GL resource recreation for model" }
        
        try {
            // 1. 清空待绑定纹理队列（旧的纹理 ID 已失效）
            pendingTextures.clear()
            
            // 2. 重新创建渲染器
            try {
                val renderer = CubismRendererAndroid.create()
                setupRenderer(renderer)
                LAppPal.printLogLazy { "recreateGLResources: Renderer recreated" }
            } catch (e: Exception) {
                LAppPal.printLog("recreateGLResources: Failed to recreate renderer: ${e.message}")
                e.printStackTrace()
            }
            
            // 3. 重新加载纹理（从文件系统）
            if (modelHomeDirectory.isNotEmpty()) {
                setupTexturesFromFileSystem()
                LAppPal.printLogLazy { "recreateGLResources: Textures reloaded from filesystem" }
            } else {
                LAppPal.printLog("recreateGLResources: No model directory, skipping texture reload")
            }
            
            // 4. 重新创建离屏渲染缓冲
            try {
                if (renderingBuffer.isValid) {
                    renderingBuffer.destroyOffscreenSurface()
                }
                val width = LAppDelegate.getInstance().windowWidth
                val height = LAppDelegate.getInstance().windowHeight
                if (width > 0 && height > 0) {
                    renderingBuffer.createOffscreenSurface(width, height, null)
                    LAppPal.printLogLazy { "recreateGLResources: Offscreen buffer recreated" }
                }
            } catch (e: Exception) {
                LAppPal.printLog("recreateGLResources: Failed to recreate offscreen buffer: ${e.message}")
            }
            
            LAppPal.printLogLazy { "recreateGLResources: GL resource recreation completed" }
        } catch (e: Exception) {
            LAppPal.printLog("recreateGLResources: Unexpected error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 绑定延迟的纹理（线程安全）
     */
    fun bindPendingTextures() {
        // 先检查是否为空，避免不必要的同步开销
        if (pendingTextures.isEmpty()) {
            return
        }
        
        val renderer = getRenderer<CubismRendererAndroid>()
        if (renderer != null) {
            // 创建待绑定纹理的快照，减少同步块持有时间
            val texturesToBind: Map<Int, Int> = synchronized(pendingTextures) {
                if (pendingTextures.isEmpty()) {
                    return  // 双重检查，避免竞态条件
                }
                LAppPal.printLogLazy { "bindPendingTextures: Binding ${pendingTextures.size} pending textures" }
                HashMap(pendingTextures)
            }
            
            // 在同步块外执行绑定操作（GL 调用可能耗时）
            try {
                // 只需要设置一次 premultiplied alpha，而不是每个纹理都设置
                renderer.isPremultipliedAlpha(LAppDefine.PREMULTIPLIED_ALPHA_ENABLE)
                
                for ((modelTextureNumber, glTextureNumber) in texturesToBind) {
                    renderer.bindTexture(modelTextureNumber, glTextureNumber)
                    LAppPal.printLogLazy { "bindPendingTextures: Texture $modelTextureNumber bound successfully" }
                }
                
                // 清除已成功绑定的纹理
                synchronized(pendingTextures) {
                    for (key in texturesToBind.keys) {
                        pendingTextures.remove(key)
                    }
                }
                
                LAppPal.printLogLazy { "bindPendingTextures: All pending textures bound successfully" }
            } catch (e: Exception) {
                LAppPal.printLog("bindPendingTextures: Error binding textures: ${e.message}")
                e.printStackTrace()
            }
        } else {
            LAppPal.printLog("bindPendingTextures: Renderer is still null, cannot bind pending textures")
            // 尝试初始化渲染器
            initializeRenderer()
        }
    }
    
    /**
     * 检查渲染器是否可用
     */
    fun checkRendererAvailability(): Boolean {
        val renderer = getRenderer<CubismRendererAndroid>()
        return renderer != null
    }
    
    /**
     * 初始化渲染器
     */
    private fun initializeRenderer() {
        try {
            LAppPal.printLogLazy { "initializeRenderer: Attempting to initialize renderer" }
            
            // 检查模型是否已加载
            if (model == null) {
                LAppPal.printLogLazy { "initializeRenderer: Model is null, cannot initialize renderer" }
                return
            }
            
            // 使用官方示例的方法创建和设置渲染器
            try {
                // 方法1: 使用官方示例的方法创建渲染器
                val renderer = CubismRendererAndroid.create()
                LAppPal.printLogLazy { "initializeRenderer: renderer created using CubismRendererAndroid.create()" }
                
                // 方法2: 使用官方示例的方法设置渲染器
                setupRenderer(renderer)
                LAppPal.printLogLazy { "initializeRenderer: renderer set using setupRenderer()" }
                LAppPal.printLogLazy { "initializeRenderer: renderer created and initialized successfully" }
            } catch (e: Exception) {
                LAppPal.printLogLazy { "initializeRenderer: renderer creation/initialization failed: ${e.message}" }
            }
            
            // 检查渲染器是否现在可用
            val renderer = getRenderer<CubismRendererAndroid>()
            if (renderer != null) {
                LAppPal.printLogLazy { "initializeRenderer: Renderer is now available" }
                // 如果渲染器现在可用，尝试绑定延迟的纹理
                bindPendingTextures()
            } else {
                LAppPal.printLogLazy { "initializeRenderer: Renderer is still null after createRenderer() attempt" }
            }
        } catch (e: Exception) {
            LAppPal.printLogLazy { "initializeRenderer: Failed to initialize renderer: ${e.message}" }
        }
    }

    /**
     * モデルの更新処理。モデルのパラメーターから描画状態を決定する。
     */
    fun update() {
        // 确保参数ID已初始化
        ensureParameterIdsInitialized()
        
        val deltaTimeSeconds = LAppPal.getDeltaTime()
        userTimeSeconds += deltaTimeSeconds

        dragManager.update(deltaTimeSeconds)
        dragX = dragManager.x
        dragY = dragManager.y

        // モーションによるパラメーター更新の有無
        var motionUpdated = false

        // 前回セーブされた状態をロード
        model?.loadParameters()

        // モーションの更新
        if (motionManager.isFinished()) {
            // モーションの再生がない場合、待機モーションの中からランダムで再生する
            startRandomMotion(LAppDefine.MotionGroup.IDLE.id, LAppDefine.Priority.IDLE.priority)
        } else {
            motionUpdated = motionManager.updateMotion(model, deltaTimeSeconds) // モーションを更新
        }

        // まばたき
        eyeBlink?.let {
            // メインモーションの更新がないとき
            if (!motionUpdated) {
                it.updateParameters(model, deltaTimeSeconds) // 目パチ
            }
        }

        expressionManager?.let {
            it.updateMotion(model, deltaTimeSeconds) // 表情でパラメーター更新（相対変化）
        }

        // ドラッグによる変化
        // ドラッグによる顔の向きの調整
        model?.addParameterValue(idParamAngleX, dragX * DRAG_ANGLE_MULTIPLIER)
        model?.addParameterValue(idParamAngleY, dragY * DRAG_ANGLE_MULTIPLIER)
        model?.addParameterValue(idParamAngleZ, dragX * dragY * -DRAG_ANGLE_MULTIPLIER)

        // ドラッグによる体の向きの調整
        model?.addParameterValue(idParamBodyAngleX, dragX * DRAG_BODY_ANGLE_MULTIPLIER)

        // ドラッグによる目の向きの調整
        model?.addParameterValue(idParamEyeBallX, dragX * DRAG_EYE_BALL_MULTIPLIER)
        model?.addParameterValue(idParamEyeBallY, dragY * DRAG_EYE_BALL_MULTIPLIER)

        // 呼吸など
        breath?.let {
            it.updateParameters(model, deltaTimeSeconds)
        }

        // 物理演算の設定
        physics?.let {
            it.evaluate(model, deltaTimeSeconds)
        }

        // リップシンクの設定
        if (lipSync) {
            // リアルタイムでリップシンクを行う場合、システムから音量を取得して、0〜1の範囲で値を入力します。
            val value = 0.0f // リアルタイムでリップシンクを行う場合、システムから音量を取得して、0〜1の範囲で値を入力します。

            for (i in lipSyncIds.indices) {
                model?.addParameterValue(lipSyncIds[i], value, LIP_SYNC_WEIGHT)
            }
        }

        // ポーズの設定
        pose?.let {
            it.updateParameters(model, deltaTimeSeconds)
        }

        model?.update()
    }

    fun startMotion(group: String, number: Int, priority: Int): Int {
        return startMotion(group, number, priority, null, null)
    }

    /**
     * 引数で指定したモーションの再生を開始する
     *
     * @param group                      モーショングループ名
     * @param number                     グループ内の番号
     * @param priority                   優先度
     * @param onFinishedMotionHandler    モーション再生終了時に呼び出されるコールバック関数。nullの場合、呼び出されない。
     * @param onBeganMotionHandler       モーション再生開始時に呼び出されるコールバック関数。nullの場合、呼び出されない。
     * @return 開始したモーションの識別番号を返す。個別のモーションが識別できない時は-1
     */
    fun startMotion(
        group: String,
        number: Int,
        priority: Int,
        onFinishedMotionHandler: IFinishedMotionCallback?,
        onBeganMotionHandler: IBeganMotionCallback?
    ): Int {
        if (priority == LAppDefine.Priority.FORCE.priority) {
            // setReservePriority 方法不存在，使用 startMotionPriority 替代
            // motionManager.setReservePriority(priority)
        } else if (!motionManager.reserveMotion(priority)) {
            if (debugMode) {
                LAppPal.printLog("can't start motion.")
            }
            return -1
        }

        val fileName = "${group}_$number"

        val motion = motions[fileName] as? CubismMotion
        var autoDelete = false

        if (motion == null) {
            val name = modelSetting?.getMotionFileName(group, number)
            if (name != null && name.isNotEmpty()) {
                val path = "$modelHomeDirectory$name"
                val buffer = createBuffer(path)

                if (buffer != null) {
                    val tmpMotion = loadMotion(buffer, motionConsistency)
                    if (tmpMotion != null) {
                        val fadeInTime = modelSetting?.getMotionFadeInTimeValue(group, number)
                        if (fadeInTime != null && fadeInTime != -1.0f) {
                            tmpMotion.fadeInTime = fadeInTime
                        }

                        val fadeOutTime = modelSetting?.getMotionFadeOutTimeValue(group, number)
                        if (fadeOutTime != null && fadeOutTime != -1.0f) {
                            tmpMotion.fadeOutTime = fadeOutTime
                        }

                        tmpMotion.setEffectIds(eyeBlinkIds, lipSyncIds)
                        autoDelete = true // 終了時にメモリから削除

                        return motionManager.startMotionPriority(tmpMotion, priority)
                    }
                }
            }
        } else {
            motion.setEffectIds(eyeBlinkIds, lipSyncIds)
            return motionManager.startMotionPriority(motion, priority)
        }

        return -1
    }

    fun startRandomMotion(group: String, priority: Int): Int {
        return startRandomMotion(group, priority, null, null)
    }

    fun startRandomMotion(
        group: String,
        priority: Int,
        onFinishedMotionHandler: IFinishedMotionCallback?,
        onBeganMotionHandler: IBeganMotionCallback?
    ): Int {
        val count = modelSetting?.getMotionCount(group) ?: 0
        if (count == 0) {
            return -1
        }

        val number = Random.nextInt(count)
        return startMotion(group, number, priority, onFinishedMotionHandler, onBeganMotionHandler)
    }

    fun draw(matrix: CubismMatrix44) {
        if (model == null) {
            return
        }

        // 检查渲染器是否可用
        val renderer = getRenderer<CubismRendererAndroid>()
        if (renderer == null) {
            LAppPal.printLogLazy { "draw: Renderer is null, skipping draw" }
            return
        }

        // 使用官方示例的矩阵乘法方法
        // キャッシュ変数の定義を避けるために、multiplyByMatrix()ではなく、multiply()を使用する。
        if (modelMatrix != null) {
            CubismMatrix44.multiply(
                modelMatrix.getArray(),
                matrix.getArray(),
                matrix.getArray()
            )
        } else {
            // 如果 modelMatrix 为 null，使用单位矩阵
            matrix.loadIdentity()
        }

        renderer.setMvpMatrix(matrix)
        
        // 在绘制前，如果存在延迟纹理，尝试在 GL 线程绑定
        if (pendingTextures.isNotEmpty()) {
            try {
                bindPendingTextures()
            } catch (e: Exception) {
                // 记录错误但不中断渲染，后续帧继续尝试
                LAppPal.printLogLazy { "draw: Failed to bind pending textures: ${e.message}" }
            }
        }

        // 使用官方示例的方法绘制模型
        renderer.drawModel()
    }

    /**
     * 公共方法：查询主动作是否播放完成
     */
    fun isMainMotionFinished(): Boolean {
        return motionManager.isFinished()
    }

    /**
     * 当たり判定テスト
     * 指定IDの頂点リストから矩形を計算し、座標が矩形範囲内か判定する。
     *
     * @param hitAreaName 当たり判定をテストする対象のID
     * @param x           判定を行うX座標
     * @param y           判定を行うY座標
     */
    fun hitTest(hitAreaName: String, x: Float, y: Float): Boolean {
        // 透明時は当たり判定無し
        if (opacity < 1) {
            return false
        }

        val count = modelSetting?.getHitAreasCount() ?: 0

        for (i in 0 until count) {
            val areaName = modelSetting?.getHitAreaName(i)
            if (areaName == hitAreaName) {
                val drawId = modelSetting?.getHitAreaId(i)
                return isHit(drawId, x, y)
            }
        }

        return false
    }

    /**
     * 引数で指定した表情モーションをセットする
     *
     * @param expressionID 表情モーションのID
     */
    fun setExpression(expressionID: String) {
        val motion = expressions[expressionID] as? CubismExpressionMotion
        if (debugMode) {
            LAppPal.printLog("expression: [$expressionID]")
        }

        if (motion != null) {
            expressionManager.startMotionPriority(motion, LAppDefine.Priority.FORCE.priority)
        } else {
            if (debugMode) {
                LAppPal.printLog("expression[$expressionID] is null")
            }
        }
    }

    /**
     * ランダムに選ばれた表情モーションをセットする
     */
    fun setRandomExpression() {
        if (expressions.isEmpty()) {
            return
        }

        val number = Random.nextInt(expressions.size)
        var i = 0
        for ((key, _) in expressions) {
            if (i == number) {
                setExpression(key)
                return
            }
            i++
        }
    }

    fun getRenderingBuffer(): CubismOffscreenSurfaceAndroid {
        return renderingBuffer
    }

    /**
     * モデルデータがある.mocファイルと整合性があるかチェックする
     *
     * @param mocFileName MOCファイル名
     * @return MOCファイルと整合性があるかどうか
     */
    fun hasMocConsistencyFromFile(mocFileName: String): Boolean {
        val currentMocFileName = modelSetting?.modelFileName
        return currentMocFileName == mocFileName
    }

    companion object {
        // 拖拽参数倍率常量
        private const val DRAG_ANGLE_MULTIPLIER = 30f
        private const val DRAG_BODY_ANGLE_MULTIPLIER = 10f
        private const val DRAG_EYE_BALL_MULTIPLIER = 1f
        
        // 呼吸参数常量
        private const val BREATH_ANGLE_X_OFFSET = 0.0f
        private const val BREATH_ANGLE_X_PEAK = 15.0f
        private const val BREATH_ANGLE_X_CYCLE = 6.5345f
        private const val BREATH_ANGLE_X_WEIGHT = 0.5f
        
        private const val BREATH_ANGLE_Y_OFFSET = 0.0f
        private const val BREATH_ANGLE_Y_PEAK = 8.0f
        private const val BREATH_ANGLE_Y_CYCLE = 3.5345f
        private const val BREATH_ANGLE_Y_WEIGHT = 0.5f
        
        private const val BREATH_ANGLE_Z_OFFSET = 0.0f
        private const val BREATH_ANGLE_Z_PEAK = 10.0f
        private const val BREATH_ANGLE_Z_CYCLE = 5.5345f
        private const val BREATH_ANGLE_Z_WEIGHT = 0.5f
        
        private const val BREATH_BODY_ANGLE_X_OFFSET = 0.0f
        private const val BREATH_BODY_ANGLE_X_PEAK = 4.0f
        private const val BREATH_BODY_ANGLE_X_CYCLE = 15.5345f
        private const val BREATH_BODY_ANGLE_X_WEIGHT = 0.5f
        
        private const val BREATH_PARAM_OFFSET = 0.5f
        private const val BREATH_PARAM_PEAK = 0.5f
        private const val BREATH_PARAM_CYCLE = 3.2345f
        private const val BREATH_PARAM_WEIGHT = 0.5f
        
        // 口型同步参数
        private const val LIP_SYNC_WEIGHT = 0.8f
        
        /**
         * ファイルをバイト配列として読み込む
         *
         * @param path ファイルパス
         * @return ファイルデータ
         */
        private fun createBuffer(path: String): ByteArray? {
            return LAppPal.loadFileAsBytes(path)
        }

        /**
         * ファイルシステムからファイルをバイト配列として読み込む
         *
         * @param path ファイルパス
         * @return ファイルデータ
         */
        private fun createBufferFromFileSystem(path: String): ByteArray? {
            return LAppPal.loadFileFromFileSystem(path)
        }
    }

    private fun setupModel(setting: ICubismModelSetting) {
        modelHomeDirectory = ""
        setupModelInternal(setting, "setupModel", initializeRenderer = false)
    }
    
    /**
     * 内部方法：统一的模型设置逻辑
     * @param setting 模型设置
     * @param logPrefix 日志前缀
     * @param initializeRenderer 是否在设置后初始化渲染器
     */
    private fun setupModelInternal(
        setting: ICubismModelSetting,
        logPrefix: String,
        initializeRenderer: Boolean
    ) {
        isUpdated = true
        isInitialized = false
        modelSetting = setting

        // CubismModel
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("$logPrefix: Starting model setup")
        }

        // Load Cubism Model
        run {
            val fileName = modelSetting?.modelFileName ?: ""
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("$logPrefix: Model file name: $fileName")
            }
            if (fileName.isNotEmpty()) {
                val path = modelHomeDirectory + fileName
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("$logPrefix: Full model path: $path")
                    LAppPal.printLog("$logPrefix: create model: ${modelSetting?.modelFileName}")
                }

                val buffer = createBufferFromFileSystem(path)
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("$logPrefix: Buffer size: ${buffer?.size ?: "null"}")
                }

                if (buffer != null) {
                    loadModel(buffer, mocConsistency)
                    if (LAppDefine.DEBUG_LOG_ENABLE) {
                        LAppPal.printLog("$logPrefix: Model loaded, getModel() result: ${if (model != null) "not null" else "null"}")
                    }
                } else {
                    LAppPal.printLog("$logPrefix: ERROR - Failed to create buffer for model file")
                }
            } else {
                LAppPal.printLog("$logPrefix: ERROR - Model file name is empty")
            }
        }

        // load expression files(.exp3.json)
        run {
            val expressionCount = modelSetting?.expressionCount ?: 0
            if (expressionCount > 0) {
                for (i in 0 until expressionCount) {
                    val name = modelSetting?.getExpressionName(i) ?: ""
                    var path = modelSetting?.getExpressionFileName(i) ?: ""
                    path = modelHomeDirectory + path

                    val buffer = createBufferFromFileSystem(path)
                    val motion = loadExpression(buffer)

                    if (motion != null) {
                        expressions[name] = motion
                    }
                }
            }
        }

        // Physics
        run {
            val path = modelSetting?.physicsFileName ?: ""
            if (path.isNotEmpty()) {
                val modelPath = modelHomeDirectory + path
                val buffer = createBufferFromFileSystem(modelPath)

                loadPhysics(buffer)
            }
        }

        // Pose
        run {
            val path = modelSetting?.poseFileName ?: ""
            if (path.isNotEmpty()) {
                val modelPath = modelHomeDirectory + path
                val buffer = createBufferFromFileSystem(modelPath)
                loadPose(buffer)
            }
        }

        // Load eye blink data
        val eyeBlinkParameterCount = modelSetting?.eyeBlinkParameterCount ?: 0
        if (eyeBlinkParameterCount > 0) {
            eyeBlink = CubismEyeBlink.create(modelSetting)
        }

        // Load Breath Data
        breath = CubismBreath.create()
        val breathParameters = mutableListOf<CubismBreath.BreathParameterData>()

        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleX, BREATH_ANGLE_X_OFFSET, BREATH_ANGLE_X_PEAK, BREATH_ANGLE_X_CYCLE, BREATH_ANGLE_X_WEIGHT))
        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleY, BREATH_ANGLE_Y_OFFSET, BREATH_ANGLE_Y_PEAK, BREATH_ANGLE_Y_CYCLE, BREATH_ANGLE_Y_WEIGHT))
        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleZ, BREATH_ANGLE_Z_OFFSET, BREATH_ANGLE_Z_PEAK, BREATH_ANGLE_Z_CYCLE, BREATH_ANGLE_Z_WEIGHT))
        breathParameters.add(CubismBreath.BreathParameterData(idParamBodyAngleX, BREATH_BODY_ANGLE_X_OFFSET, BREATH_BODY_ANGLE_X_PEAK, BREATH_BODY_ANGLE_X_CYCLE, BREATH_BODY_ANGLE_X_WEIGHT))
        breathParameters.add(CubismBreath.BreathParameterData(CubismFramework.getIdManager().getId(ParameterId.BREATH.id), BREATH_PARAM_OFFSET, BREATH_PARAM_PEAK, BREATH_PARAM_CYCLE, BREATH_PARAM_WEIGHT))

        breath?.setParameters(breathParameters)

        // Load UserData
        run {
            val path = modelSetting?.userDataFile ?: ""
            if (path.isNotEmpty()) {
                val modelPath = modelHomeDirectory + path
                val buffer = createBufferFromFileSystem(modelPath)
                loadUserData(buffer)
            }
        }

        // EyeBlinkIds
        val eyeBlinkIdCount = modelSetting?.eyeBlinkParameterCount ?: 0
        for (i in 0 until eyeBlinkIdCount) {
            val id = modelSetting?.getEyeBlinkParameterId(i)
            if (id != null) {
                eyeBlinkIds.add(id)
            }
        }

        // LipSyncIds
        val lipSyncIdCount = modelSetting?.lipSyncParameterCount ?: 0
        for (i in 0 until lipSyncIdCount) {
            val id = modelSetting?.getLipSyncParameterId(i)
            if (id != null) {
                lipSyncIds.add(id)
            }
        }

        if (modelSetting == null || modelMatrix == null) {
            LAppPal.printLog("Failed to $logPrefix().")
            return
        }

        // Set layout
        val layout = mutableMapOf<String, Float>()

        // レイアウト情報が存在すればその情報からモデル行列をセットアップする
        if (modelSetting?.getLayoutMap(layout) == true) {
            modelMatrix?.setupFromLayout(layout)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("$logPrefix: Layout found and applied to modelMatrix")
                for ((key, value) in layout) {
                    LAppPal.printLog("$logPrefix: Layout: $key = $value")
                }
            }
        } else {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("$logPrefix: No layout information found, using default modelMatrix")
            }
        }

        model?.saveParameters()

        // Load motions
        val motionGroupCount = modelSetting?.motionGroupCount ?: 0
        for (i in 0 until motionGroupCount) {
            val group = modelSetting?.getMotionGroupName(i) ?: ""
            preLoadMotionGroupFromFileSystem(group)
        }

        motionManager.stopAllMotions()

        // 根据参数决定是否初始化渲染器
        if (initializeRenderer) {
            try {
                val renderer = CubismRendererAndroid.create()
                setupRenderer(renderer)
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("$logPrefix: Renderer created and initialized after model loading")
                }
            } catch (e: Exception) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("$logPrefix: Renderer creation/initialization failed: ${e.message}")
                }
            }
        }

        isUpdated = false
        isInitialized = true
    }

    // model3.jsonからモデルを生成する (from file system)
    private fun setupModelFromFileSystem(setting: ICubismModelSetting, modelDirectory: String) {
        modelHomeDirectory = modelDirectory
        setupModelInternal(setting, "setupModelFromFileSystem", initializeRenderer = true)
    }

    /**
     * モーションデータをグループ名から一括でロードする。
     * モーションデータの名前はModelSettingから取得する。
     *
     * @param group モーションデータのグループ名
     */
    private fun preLoadMotionGroup(group: String) {
        val count = modelSetting?.getMotionCount(group) ?: 0

        for (i in 0 until count) {
            // ex) idle_0
            val name = "${group}_$i"

            val path = modelSetting?.getMotionFileName(group, i) ?: ""
            if (path.isNotEmpty()) {
                val modelPath = modelHomeDirectory + path

                if (debugMode) {
                    LAppPal.printLog("load motion: $path==>[$group" + "_$i]")
                }

                val buffer = createBuffer(modelPath)

                // If a motion cannot be loaded, a process is skipped.
                val tmp = loadMotion(buffer, motionConsistency)
                if (tmp == null) {
                    continue
                }

                val fadeInTime = modelSetting?.getMotionFadeInTimeValue(group, i) ?: -1.0f

                if (fadeInTime != -1.0f) {
                    tmp.fadeInTime = fadeInTime
                }

                val fadeOutTime = modelSetting?.getMotionFadeOutTimeValue(group, i) ?: -1.0f

                if (fadeOutTime != -1.0f) {
                    tmp.fadeOutTime = fadeOutTime
                }

                tmp.setEffectIds(eyeBlinkIds, lipSyncIds)
                motions[name] = tmp
            }
        }
    }

    /**
     * OpenGLのテクスチャユニットにテクスチャをロードする (from file system)
     */
    private fun setupTexturesFromFileSystem() {
        LAppPal.printLog("setupTexturesFromFileSystem: Starting texture setup")
        LAppPal.printLog("setupTexturesFromFileSystem: Model home directory: $modelHomeDirectory")
        LAppPal.printLog("setupTexturesFromFileSystem: Texture count: ${modelSetting?.textureCount ?: 0}")

        val textureCount = modelSetting?.textureCount ?: 0
        for (modelTextureNumber in 0 until textureCount) {
            // テクスチャ名が空文字だった場合はロード・バインド処理をスキップ
            val textureFileName = modelSetting?.getTextureFileName(modelTextureNumber) ?: ""
            LAppPal.printLog("setupTexturesFromFileSystem: Texture $modelTextureNumber filename: $textureFileName")

            if (textureFileName.isEmpty()) {
                LAppPal.printLog("setupTexturesFromFileSystem: Skipping empty texture filename")
                continue
            }

            // OpenGL ESのテクスチャユニットにテクスチャをロードする
            val texturePath = modelHomeDirectory + textureFileName
            LAppPal.printLog("setupTexturesFromFileSystem: Full texture path: $texturePath")

            val textureManager = LAppDelegate.getInstance().getTextureManager()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("setupTexturesFromFileSystem: textureManager is ${if (textureManager != null) "not null" else "null"}")
            }
            
            if (textureManager == null) {
                LAppPal.printLog("setupTexturesFromFileSystem: textureManager is null, cannot load texture: $texturePath")
                continue
            }
            
            val texture = textureManager.createTextureFromFileSystem(texturePath)

            if (texture == null) {
                LAppPal.printLog("Failed to load texture from file system: $texturePath")
                continue
            }

            LAppPal.printLog("setupTexturesFromFileSystem: Texture loaded successfully, ID: ${texture.id}, Size: ${texture.width}x${texture.height}")

            val glTextureNumber = texture.id

            // 检查渲染器是否已初始化，如果未初始化则延迟绑定
            val renderer = getRenderer<CubismRendererAndroid>()
            if (renderer != null) {
                renderer.bindTexture(modelTextureNumber, glTextureNumber)
                // 文件系统路径的Bitmap通常为非预乘
                renderer.isPremultipliedAlpha(LAppDefine.PREMULTIPLIED_ALPHA_ENABLE)
                LAppPal.printLog("setupTexturesFromFileSystem: Texture $modelTextureNumber bound to renderer successfully")
            } else {
                LAppPal.printLog("setupTexturesFromFileSystem: Renderer is null, deferring texture binding")
                // 存储纹理信息以便后续绑定
                pendingTextures[modelTextureNumber] = glTextureNumber
            }
        }

        LAppPal.printLog("setupTexturesFromFileSystem: Texture setup completed")
    }

    /**
     * OpenGLのテクスチャユニットにテクスチャをロードする
     */
    private fun setupTextures() {
        val textureCount = modelSetting?.textureCount ?: 0
        for (modelTextureNumber in 0 until textureCount) {
            // テクスチャ名が空文字だった場合はロード・バインド処理をスキップ
            val textureFileName = modelSetting?.getTextureFileName(modelTextureNumber) ?: ""
            if (textureFileName.isEmpty()) {
                continue
            }

            // OpenGL ESのテクスチャユニットにテクスチャをロードする
            var texturePath = modelSetting?.getTextureFileName(modelTextureNumber) ?: ""
            texturePath = modelHomeDirectory + texturePath

            val texture = LAppDelegate.getInstance()
                .getTextureManager()!!
                .createTextureFromPngFile(texturePath)
            
            if (texture == null) {
                LAppPal.printLog("setupTextures: Failed to load texture: $texturePath")
                continue
            }
            
            val glTextureNumber = texture.id

            getRenderer<CubismRendererAndroid>().bindTexture(modelTextureNumber, glTextureNumber)

            if (LAppDefine.PREMULTIPLIED_ALPHA_ENABLE) {
                getRenderer<CubismRendererAndroid>().isPremultipliedAlpha(true)
            } else {
                getRenderer<CubismRendererAndroid>().isPremultipliedAlpha(false)
            }
        }
    }

    private fun preLoadMotionGroupFromFileSystem(group: String) {
        val count = modelSetting?.getMotionCount(group) ?: 0

        for (i in 0 until count) {
            // ex) idle_0
            val name = "${group}_$i"

            val path = modelSetting?.getMotionFileName(group, i) ?: ""
            if (path.isNotEmpty()) {
                val modelPath = modelHomeDirectory + path

                if (debugMode) {
                    LAppPal.printLog("preLoadMotionGroupFromFileSystem: $path==>[$group" + "_$i]")
                }

                val buffer = createBufferFromFileSystem(modelPath)

                // If a motion cannot be loaded, a process is skipped.
                val tmp = loadMotion(buffer, motionConsistency)
                if (tmp == null) {
                    continue
                }

                val fadeInTime = modelSetting?.getMotionFadeInTimeValue(group, i) ?: -1.0f

                if (fadeInTime != -1.0f) {
                    tmp.fadeInTime = fadeInTime
                }

                val fadeOutTime = modelSetting?.getMotionFadeOutTimeValue(group, i) ?: -1.0f

                if (fadeOutTime != -1.0f) {
                    tmp.fadeOutTime = fadeOutTime
                }

                tmp.setEffectIds(eyeBlinkIds, lipSyncIds)
                motions[name] = tmp
            }
        }
    }
}