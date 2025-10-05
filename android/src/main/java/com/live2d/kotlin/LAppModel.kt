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
     * 延迟绑定的纹理信息
     */
    private val pendingTextures: MutableMap<Int, Int> = mutableMapOf()

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

    private fun initializeParameterIds() {
        try {
            val idManager = CubismFramework.getIdManager()
            if (idManager == null) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("CubismFramework.getIdManager() returned null, deferring parameter ID initialization")
                }
                return
            }

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
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("Failed to initialize parameter IDs: ${e.message}")
            }
            throw e
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

    fun deleteModel() {
        renderingBuffer.destroyOffscreenSurface()
        pendingTextures.clear()
    }
    
    /**
     * 绑定延迟的纹理
     */
    fun bindPendingTextures() {
        if (pendingTextures.isEmpty()) {
            return
        }
        
        val renderer = getRenderer<CubismRendererAndroid>()
        if (renderer != null) {
            LAppPal.printLog("bindPendingTextures: Binding ${pendingTextures.size} pending textures")
            for ((modelTextureNumber, glTextureNumber) in pendingTextures) {
                renderer.bindTexture(modelTextureNumber, glTextureNumber)
                renderer.isPremultipliedAlpha(LAppDefine.PREMULTIPLIED_ALPHA_ENABLE)
                LAppPal.printLog("bindPendingTextures: Texture $modelTextureNumber bound successfully")
            }
            pendingTextures.clear()
            LAppPal.printLog("bindPendingTextures: All pending textures bound successfully")
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
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("initializeRenderer: Attempting to initialize renderer")
            }
            
            // 检查模型是否已加载
            if (model == null) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("initializeRenderer: Model is null, cannot initialize renderer")
                }
                return
            }
            
            // 使用官方示例的方法创建和设置渲染器
            try {
                // 方法1: 使用官方示例的方法创建渲染器
                val renderer = CubismRendererAndroid.create()
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("initializeRenderer: renderer created using CubismRendererAndroid.create()")
                }
                
                // 方法2: 使用官方示例的方法设置渲染器
                setupRenderer(renderer)
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("initializeRenderer: renderer set using setupRenderer()")
                }
                
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("initializeRenderer: renderer created and initialized successfully")
                }
            } catch (e: Exception) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("initializeRenderer: renderer creation/initialization failed: ${e.message}")
                }
            }
            
            // 检查渲染器是否现在可用
            val renderer = getRenderer<CubismRendererAndroid>()
            if (renderer != null) {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("initializeRenderer: Renderer is now available")
                }
                // 如果渲染器现在可用，尝试绑定延迟的纹理
                bindPendingTextures()
            } else {
                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("initializeRenderer: Renderer is still null after createRenderer() attempt")
                }
            }
        } catch (e: Exception) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("initializeRenderer: Failed to initialize renderer: ${e.message}")
            }
        }
    }

    /**
     * モデルの更新処理。モデルのパラメーターから描画状態を決定する。
     */
    fun update() {
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
        model?.addParameterValue(idParamAngleX, dragX * 30) // -30から30の値を加える
        model?.addParameterValue(idParamAngleY, dragY * 30)
        model?.addParameterValue(idParamAngleZ, dragX * dragY * -30)

        // ドラッグによる体の向きの調整
        model?.addParameterValue(idParamBodyAngleX, dragX * 10) // -10から10の値を加える

        // ドラッグによる目の向きの調整
        model?.addParameterValue(idParamEyeBallX, dragX) // -1から1の値を加える
        model?.addParameterValue(idParamEyeBallY, dragY)

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
                model?.addParameterValue(lipSyncIds[i], value, 0.8f)
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
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     LAppPal.printLog("draw: Starting draw process, model is ${if (model != null) "not null" else "null"}")
        // }
        
        if (model == null) {
            // if (LAppDefine.DEBUG_LOG_ENABLE) {
            //     LAppPal.printLog("draw: Model is null, skipping draw")
            // }
            return
        }

        // 检查渲染器是否可用
        val renderer = getRenderer<CubismRendererAndroid>()
        if (renderer == null) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("draw: Renderer is null, skipping draw")
            }
            return
        }

        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     LAppPal.printLog("draw: Renderer is available, proceeding with draw")
        // }

        // 使用官方示例的矩阵乘法方法
        // キャッシュ変数の定義を避けるために、multiplyByMatrix()ではなく、multiply()を使用する。
        // 减少调试日志
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     LAppPal.printLog("draw: Before matrix multiplication")
        //     LAppPal.printLog("draw: modelMatrix is null: ${modelMatrix == null}")
        //     if (modelMatrix != null) {
        //         LAppPal.printLog("draw: modelMatrix values: ${modelMatrix.getArray().contentToString()}")
        //     }
        //     LAppPal.printLog("draw: projection matrix values: ${matrix.getArray().contentToString()}")
        // }
        
        if (modelMatrix != null) {
            CubismMatrix44.multiply(
                modelMatrix.getArray(),
                matrix.getArray(),
                matrix.getArray()
            )
            
            // if (LAppDefine.DEBUG_LOG_ENABLE) {
            //     LAppPal.printLog("draw: After matrix multiplication")
            //     LAppPal.printLog("draw: Result matrix values: ${matrix.getArray().contentToString()}")
            // }
        } else {
            // if (LAppDefine.DEBUG_LOG_ENABLE) {
            //     LAppPal.printLog("draw: modelMatrix is null, using identity matrix")
            // }
            // 如果 modelMatrix 为 null，使用单位矩阵
            matrix.loadIdentity()
        }

        renderer.setMvpMatrix(matrix)
        
        // 在绘制前，如果存在延迟纹理，尝试在 GL 线程绑定
        if (pendingTextures.isNotEmpty()) {
            try {
                bindPendingTextures()
            } catch (_: Exception) {
                // 忽略单帧失败，后续帧继续尝试
            }
        }

        // 使用官方示例的方法绘制模型
        renderer.drawModel()
        
        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     LAppPal.printLog("draw: renderer.drawModel() completed")
        // }
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
        isUpdated = true
        isInitialized = false

        modelSetting = setting
        modelHomeDirectory = ""

        // CubismModel
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("setupModel: Starting model setup")
        }

        // Load Cubism Model
        run {
            val fileName = modelSetting?.modelFileName ?: ""
            LAppPal.printLog("setupModel: Model file name: $fileName")
            if (fileName.isNotEmpty()) {
                val path = modelHomeDirectory + fileName
                LAppPal.printLog("setupModel: Full model path: $path")

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("create model: ${modelSetting?.modelFileName}")
                }

                val buffer = createBufferFromFileSystem(path)
                LAppPal.printLog("setupModel: Buffer size: ${buffer?.size ?: "null"}")

                if (buffer != null) {
                    loadModel(buffer, mocConsistency)
                    LAppPal.printLog("setupModel: Model loaded, getModel() result: ${if (model != null) "not null" else "null"}")
                } else {
                    LAppPal.printLog("setupModel: ERROR - Failed to create buffer for model file")
                }
            } else {
                LAppPal.printLog("setupModel: ERROR - Model file name is empty")
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

        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(CubismFramework.getIdManager().getId(ParameterId.BREATH.id), 0.5f, 0.5f, 3.2345f, 0.5f))

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
            LAppPal.printLog("Failed to setupModel().")
            return
        }

        // Set layout
        val layout = mutableMapOf<String, Float>()

        // レイアウト情報が存在すればその情報からモデル行列をセットアップする
        if (modelSetting?.getLayoutMap(layout) == true) {
            modelMatrix?.setupFromLayout(layout)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                android.util.Log.d("LAppModel", "setupModel: Layout found and applied to modelMatrix")
                for ((key, value) in layout) {
                    android.util.Log.d("LAppModel", "Layout: $key = $value")
                }
            }
        } else {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                android.util.Log.d("LAppModel", "setupModel: No layout information found, using default modelMatrix")
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

        isUpdated = false
        isInitialized = true
    }

    // model3.jsonからモデルを生成する (from file system)
    private fun setupModelFromFileSystem(setting: ICubismModelSetting, modelDirectory: String) {
        modelSetting = setting
        modelHomeDirectory = modelDirectory

        isUpdated = true
        isInitialized = false

        // Load Cubism Model
        run {
            val fileName = modelSetting?.modelFileName ?: ""
            if (fileName.isNotEmpty()) {
                val path = modelHomeDirectory + fileName

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("setupModelFromFileSystem: ${modelSetting?.modelFileName}")
                }

                val buffer = createBufferFromFileSystem(path)
                loadModel(buffer, mocConsistency)
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

        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f))
        breathParameters.add(CubismBreath.BreathParameterData(CubismFramework.getIdManager().getId(ParameterId.BREATH.id), 0.5f, 0.5f, 3.2345f, 0.5f))

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
            LAppPal.printLog("Failed to setupModelFromFileSystem().")
            return
        }

        // Set layout
        val layout = mutableMapOf<String, Float>()

        // レイアウト情報が存在すればその情報からモデル行列をセットアップする
        if (modelSetting?.getLayoutMap(layout) == true) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("setupModelFromFileSystem: Layout found and applied to modelMatrix")
                for ((key, value) in layout) {
                    LAppPal.printLog("setupModelFromFileSystem: Layout: $key = $value")
                }
            }
            modelMatrix?.setupFromLayout(layout)
        } else {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("setupModelFromFileSystem: No layout information found, using default modelMatrix")
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

        // 使用官方示例的方法创建和初始化渲染器
        try {
            // 方法1: 使用官方示例的方法创建渲染器
            val renderer = CubismRendererAndroid.create()
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("setupModelFromFileSystem: renderer created using CubismRendererAndroid.create()")
            }
            
            // 方法2: 使用官方示例的方法设置渲染器
            setupRenderer(renderer)
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("setupModelFromFileSystem: renderer set using setupRenderer()")
            }
            
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("setupModelFromFileSystem: renderer created and initialized after model loading")
            }
        } catch (e: Exception) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("setupModelFromFileSystem: renderer creation/initialization failed: ${e.message}")
            }
        }

        isUpdated = false
        isInitialized = true
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