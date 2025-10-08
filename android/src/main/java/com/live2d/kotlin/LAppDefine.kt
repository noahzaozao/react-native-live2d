/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.kotlin

import com.live2d.sdk.cubism.framework.CubismFrameworkConfig.LogLevel

/**
 * Constants used in this sample app.
 */
object LAppDefine {
    /**
     * Scaling rate.
     */
    enum class Scale(val value: Float) {
        /**
         * Default scaling rate
         */
        DEFAULT(1.0f),
        /**
         * Maximum scaling rate
         */
        MAX(2.0f),
        /**
         * Minimum scaling rate
         */
        MIN(0.8f)
    }

    /**
     * Logical view coordinate system.
     */
    enum class LogicalView(val value: Float) {
        /**
         * Left end
         */
        LEFT(-1.0f),
        /**
         * Right end
         */
        RIGHT(1.0f),
        /**
         * Bottom end
         */
        BOTTOM(-1.0f),
        /**
         * Top end
         */
        TOP(1.0f)
    }

    /**
     * Maximum logical view coordinate system.
     */
    enum class MaxLogicalView(val value: Float) {
        /**
         * Maximum left end
         */
        LEFT(-2.0f),
        /**
         * Maximum right end
         */
        RIGHT(2.0f),
        /**
         * Maximum bottom end
         */
        BOTTOM(-2.0f),
        /**
         * Maximum top end
         */
        TOP(2.0f)
    }

    /**
     * Path of image materials.
     */
    enum class ResourcePath(val path: String) {
        /**
         * Relative path of the material directory
         */
        ROOT(""),
        /**
         * Relative path of shader directory
         */
        SHADER_ROOT("Shaders"),
        /**
         * Background image file
         */
        BACK_IMAGE("back_class_normal.png"),
        /**
         * Gear image file
         */
        GEAR_IMAGE("icon_gear.png"),
        /**
         * Power button image file
         */
        POWER_IMAGE("close.png"),
        /**
         * Vertex shader file
         */
        VERT_SHADER("VertSprite.vert"),
        /**
         * Fragment shader file
         */
        FRAG_SHADER("FragSprite.frag")
    }

    /**
     * Motion group
     */
    enum class MotionGroup(val id: String) {
        /**
         * ID of the motion to be played at idling.
         */
        IDLE("Idle"),
        /**
         * ID of the motion to be played at tapping body.
         */
        TAP_BODY("TapBody")
    }

    /**
     * [Head] tag for hit detection.
     * (Match with external definition file(json))
     */
    enum class HitAreaName(val id: String) {
        HEAD("Head"),
        BODY("Body")
    }

    /**
     * Motion priority
     */
    enum class Priority(val priority: Int) {
        NONE(0),
        IDLE(1),
        NORMAL(2),
        FORCE(3)
    }

    /**
     * MOC3の整合性を検証するかどうか。有効ならtrue。
     */
    const val MOC_CONSISTENCY_VALIDATION_ENABLE = true

    /**
     * motion3.jsonの整合性を検証するかどうか。有効ならtrue。
     */
    const val MOTION_CONSISTENCY_VALIDATION_ENABLE = true

    /**
     * Enable/Disable debug logging.
     */
    const val DEBUG_LOG_ENABLE = true
    
    /**
     * Enable/Disable debug logging for processing tapping information.
     */
    const val DEBUG_TOUCH_LOG_ENABLE = true
    
    /**
     * Setting the level of the log output from the Framework.
     */
    val cubismLoggingLevel = LogLevel.VERBOSE
    
    /**
     * Enable/Disable premultiplied alpha.
     * Set to false because BitmapFactory.decodeStream() already loads textures as premultiplied alpha.
     * Setting this to true would cause double premultiplied alpha processing, resulting in black shadows.
     */
    const val PREMULTIPLIED_ALPHA_ENABLE = true
    
    /**
     * UI Layout Constants
     */
    object UILayout {
        /**
         * Margin for gear button from right edge (pixels)
         */
        const val GEAR_BUTTON_MARGIN = 96f
        
        /**
         * Margin for power button from right edge (pixels)
         */
        const val POWER_BUTTON_MARGIN = 96f
        
        /**
         * Retry attempts for surface changed initialization
         */
        const val SURFACE_INIT_RETRY_ATTEMPTS = 3
        
        /**
         * Retry delay in milliseconds
         */
        const val SURFACE_INIT_RETRY_DELAY_MS = 100L
    }
}