package com.animationstudio.util

import android.view.InputDevice
import android.view.MotionEvent

/**
 * Samsung S Pen integration helper for Galaxy Tab S10+.
 * Handles pressure, tilt, hover, and air actions.
 */
object SPenHelper {

    data class PenState(
        val isPenDown: Boolean = false,
        val pressure: Float = 0f,
        val tilt: Float = 0f,
        val orientation: Float = 0f,
        val isSideButtonPressed: Boolean = false,
        val isHovering: Boolean = false,
        val hoverX: Float = 0f,
        val hoverY: Float = 0f,
        val isSPen: Boolean = false
    )

    /**
     * Extract S Pen state from motion event
     */
    fun extractPenState(event: MotionEvent, isDown: Boolean): PenState {
        val isSPen = isSPenEvent(event)

        if (!isSPen) {
            return PenState(isPenDown = isDown, isSPen = false)
        }

        val pressure = event.getPressure(event.actionIndex)
        val orientation = event.getOrientation(event.actionIndex)
        val tilt = event.getAxisValue(MotionEvent.AXIS_TILT)
        val isSideButton = event.getButtonState() and MotionEvent.BUTTON_SECONDARY != 0
        val isHovering = event.action == MotionEvent.ACTION_HOVER_MOVE ||
                event.action == MotionEvent.ACTION_HOVER_ENTER

        return PenState(
            isPenDown = isDown && !isHovering,
            pressure = pressure.coerceIn(0f, 1f),
            tilt = tilt.coerceIn(-1f, 1f),
            orientation = orientation,
            isSideButtonPressed = isSideButton,
            isHovering = isHovering,
            hoverX = event.x,
            hoverY = event.y,
            isSPen = true
        )
    }

    /**
     * Check if event is from S Pen
     */
    fun isSPenEvent(event: MotionEvent): Boolean {
        return event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_STYLUS &&
                (event.source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS)
    }

    /**
     * Get cursor style for hover preview
     */
    fun getHoverCursorSize(pressure: Float, baseSize: Float): Float {
        // Show brush preview while hovering
        return baseSize * (0.5f + pressure * 0.5f)
    }

    /**
     * Map side button actions
     */
    enum class SideButtonAction {
        ERASER,      // Hold button for eraser
        EYEDROPPER,  // Hold for color picker
        PAN,         // Hold to pan canvas
        UNDO,        // Double-tap to undo
        REDO,        // Triple-tap to redo
        QUICK_MENU   // Hold to show radial menu
    }

    /**
     * Detect air actions (gestures while hovering)
     */
    fun detectAirAction(
        hoverDeltaX: Float,
        hoverDeltaY: Float,
        gestureStartTime: Long
    ): AirAction? {
        val deltaMagnitude = kotlin.math.sqrt(hoverDeltaX * hoverDeltaX + hoverDeltaY * hoverDeltaY)

        return when {
            // Swipe up -> increase brush size
            hoverDeltaY < -50f && kotlin.math.abs(hoverDeltaX) < 30f -> AirAction.BRUSH_SIZE_UP
            // Swipe down -> decrease brush size
            hoverDeltaY > 50f && kotlin.math.abs(hoverDeltaX) < 30f -> AirAction.BRUSH_SIZE_DOWN
            // Circle -> undo
            deltaMagnitude > 200f -> AirAction.UNDO
            else -> null
        }
    }

    enum class AirAction {
        BRUSH_SIZE_UP,
        BRUSH_SIZE_DOWN,
        UNDO,
        COLOR_CHANGE
    }
}
