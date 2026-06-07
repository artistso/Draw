package com.animationstudio.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.toArgb

data class AnimationLayer(
    val id: Long = System.nanoTime(),
    val name: String = "Layer",
    val type: LayerType = LayerType.REGULAR,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val frames: MutableMap<Int, FrameData> = mutableMapOf(),
    val transform: LayerTransform = LayerTransform()
)

data class LayerTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f
)

data class FrameData(
    val strokePaths: List<StrokePath> = emptyList(),
    val rasterData: ByteArray? = null,
    val width: Int = 1920,
    val height: Int = 1080
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameData) return false
        return strokePaths == other.strokePaths &&
                rasterData.contentEquals(other.rasterData) &&
                width == other.width &&
                height == other.height
    }

    override fun hashCode(): Int {
        return strokePaths.hashCode() * 31 + width * 31 + height
    }
}

data class StrokePath(
    val path: List<PathPoint>,
    val color: Int = Color.BLACK,
    val strokeWidth: Float = 5f,
    val opacity: Float = 1f,
    val tool: DrawingToolType = DrawingToolType.BRUSH,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val smoothing: Float = 0.3f
)

data class PathPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tilt: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LayerType {
    REGULAR,
    VECTOR,
    AUDIO,
    CAMERA,
    RIGGING_BONE
}

enum class LayerBlendMode(val composable: BlendMode) {
    NORMAL(BlendMode.SrcOver),
    MULTIPLY(BlendMode.Multiply),
    SCREEN(BlendMode.Screen),
    OVERLAY(BlendMode.Overlay),
    DARKEN(BlendMode.Darken),
    LIGHTEN(BlendMode.Lighten),
    COLOR_DODGE(BlendMode.ColorDodge),
    COLOR_BURN(BlendMode.ColorBurn),
    HARD_LIGHT(BlendMode.Hardlight),
    SOFT_LIGHT(BlendMode.Softlight),
    DIFFERENCE(BlendMode.Difference),
    EXCLUSION(BlendMode.Exclusion)
}

enum class DrawingToolType {
    BRUSH, PENCIL, INK_PEN, AIRBRUSH, ERASER, SMUDGE, FILL
}
