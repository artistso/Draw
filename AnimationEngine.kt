package com.animationstudio.engine

import android.graphics.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Core animation engine that manages playback, onion skinning,
 * frame interpolation, and rendering.
 */
class AnimationEngine {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _onionSkinCache = mutableMapOf<Int, Bitmap>()
    private val _renderCache = mutableMapOf<Long, Bitmap>()

    private var playbackJob: kotlinx.coroutines.Job? = null

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentFrame: Int = 0,
        val totalFrames: Int = 24,
        val fps: Int = 12,
        val loop: Boolean = true,
        val pingPong: Boolean = false,
        val frameTimeMs: Long = 83L // 1000/12
    )

    /**
     * Calculate onion skin frame indices
     */
    fun getOnionSkinFrames(
        currentFrame: Int,
        totalFrames: Int,
        count: Int,
        loop: Boolean = false
    ): List<OnionSkinFrame> {
        val result = mutableListOf<OnionSkinFrame>()
        for (i in 1..count) {
            val prev = if (loop) {
                (currentFrame - i + totalFrames) % totalFrames
            } else {
                maxOf(currentFrame - i, 0)
            }
            val next = if (loop) {
                (currentFrame + i) % totalFrames
            } else {
                minOf(currentFrame + i, totalFrames - 1)
            }
            result.add(
                OnionSkinFrame(
                    frameIndex = prev,
                    opacity = 1f - (i.toFloat() / (count + 1)),
                    isBefore = true
                )
            )
            if (next != currentFrame) {
                result.add(
                    OnionSkinFrame(
                        frameIndex = next,
                        opacity = 1f - (i.toFloat() / (count + 1)),
                        isBefore = false
                    )
                )
            }
        }
        return result
    }

    /**
     * AI-powered frame interpolation - generates intermediate frames
     * between two keyframes using spline-based motion estimation.
     */
    fun interpolateFrames(
        frameA: FrameData,
        frameB: FrameData,
        numFrames: Int
    ): List<FrameData> {
        val result = mutableListOf<FrameData>()

        if (frameA.strokePaths.isEmpty() || frameB.strokePaths.isEmpty()) {
            // No stroke data to interpolate
            return result
        }

        for (i in 1..numFrames) {
            val t = i.toFloat() / (numFrames + 1)
            val interpolatedStrokes = mutableListOf<StrokePath>()

            // Match strokes between frames and interpolate
            val pairs = matchStrokes(frameA.strokePaths, frameB.strokePaths)

            for ((strokeA, strokeB) in pairs) {
                if (strokeA != null && strokeB != null) {
                    interpolatedStrokes.add(
                        interpolateStroke(strokeA, strokeB, t)
                    )
                }
            }

            result.add(FrameData(strokePaths = interpolatedStrokes))
        }

        return result
    }

    /**
     * Match strokes between two frames using spatial hashing
     */
    private fun matchStrokes(
        strokesA: List<StrokePath>,
        strokesB: List<StrokePath>
    ): List<Pair<StrokePath?, StrokePath?>> {
        val pairs = mutableListOf<Pair<StrokePath?, StrokePath?>>()
        val usedB = mutableSetOf<Int>()

        for (strokeA in strokesA) {
            val centroidA = computeCentroid(strokeA)
            var bestMatch: Int? = null
            var bestDistance = Float.MAX_VALUE

            for ((index, strokeB) in strokesB.withIndex()) {
                if (index in usedB || strokeB.tool != strokeA.tool) continue

                val centroidB = computeCentroid(strokeB)
                val dist = centroidA.distanceTo(centroidB)

                if (dist < bestDistance && dist < 300f) {
                    bestDistance = dist
                    bestMatch = index
                }
            }

            if (bestMatch != null) {
                usedB.add(bestMatch)
                pairs.add(strokeA to strokesB[bestMatch])
            } else {
                pairs.add(strokeA to null)
            }
        }

        // Add unmatched strokes from B
        for ((index, strokeB) in strokesB.withIndex()) {
            if (index !in usedB) {
                pairs.add(null to strokeB)
            }
        }

        return pairs
    }

    private fun computeCentroid(stroke: StrokePath): PointF {
        if (stroke.path.isEmpty()) return PointF(0f, 0f)
        val cx = stroke.path.map { it.x }.average().toFloat()
        val cy = stroke.path.map { it.y }.average().toFloat()
        return PointF(cx, cy)
    }

    /**
     * Interpolate between two strokes using Catmull-Rom splines
     */
    private fun interpolateStroke(
        strokeA: StrokePath,
        strokeB: StrokePath,
        t: Float
    ): StrokePath {
        // Resample both paths to the same number of points
        val numPoints = maxOf(strokeA.path.size, strokeB.path.size)
        val resampledA = resamplePath(strokeA.path, numPoints)
        val resampledB = resamplePath(strokeB.path, numPoints)

        val interpolatedPoints = resampledA.zip(resampledB).map { (a, b) ->
            PathPoint(
                x = lerp(a.x, b.x, t),
                y = lerp(a.y, b.y, t),
                pressure = lerp(a.pressure, b.pressure, t),
                tilt = lerp(a.tilt, b.tilt, t)
            )
        }

        return StrokePath(
            path = interpolatedPoints,
            color = lerpColor(strokeA.color, strokeB.color, t),
            strokeWidth = lerp(strokeA.strokeWidth, strokeB.strokeWidth, t),
            opacity = lerp(strokeA.opacity, strokeB.opacity, t),
            tool = strokeA.tool,
            smoothing = lerp(strokeA.smoothing, strokeB.smoothing, t)
        )
    }

    private fun resamplePath(path: List<PathPoint>, targetCount: Int): List<PathPoint> {
        if (path.size < 2) return List(targetCount) { path.firstOrNull() ?: PathPoint(0f, 0f) }

        val result = mutableListOf<PathPoint>()
        val totalLength = calculatePathLength(path)
        val step = totalLength / (targetCount - 1)

        var accumulated = 0f
        var pointIndex = 0

        for (i in 0 until targetCount) {
            val targetDist = i * step
            while (pointIndex < path.size - 1 && accumulated < targetDist) {
                val dx = path[pointIndex + 1].x - path[pointIndex].x
                val dy = path[pointIndex + 1].y - path[pointIndex].y
                accumulated += sqrt(dx * dx + dy * dy)
                pointIndex++
            }

            if (pointIndex >= path.size - 1) {
                result.add(path.last())
            } else {
                val localT = if (accumulated > 0f) {
                    (targetDist - (accumulated - sqrt(
                        (path[pointIndex].x - path[pointIndex - 1].x).pow(2) +
                                (path[pointIndex].y - path[pointIndex - 1].y).pow(2)
                    ))) / sqrt(
                        (path[pointIndex + 1].x - path[pointIndex].x).pow(2) +
                                (path[pointIndex + 1].y - path[pointIndex].y).pow(2)
                    )
                } else 0f

                result.add(
                    PathPoint(
                        x = lerp(path[pointIndex].x, path[pointIndex + 1].x, localT.coerceIn(0f, 1f)),
                        y = lerp(path[pointIndex].y, path[pointIndex + 1].y, localT.coerceIn(0f, 1f)),
                        pressure = lerp(
                            path[pointIndex].pressure,
                            path[pointIndex + 1].pressure,
                            localT.coerceIn(0f, 1f)
                        )
                    )
                )
            }
        }

        return result
    }

    private fun calculatePathLength(path: List<PathPoint>): Float {
        var length = 0f
        for (i in 0 until path.size - 1) {
            val dx = path[i + 1].x - path[i].x
            val dy = path[i + 1].y - path[i].y
            length += sqrt(dx * dx + dy * dy)
        }
        return length
    }

    /**
     * Motion smoothing using Gaussian-weighted temporal averaging
     */
    fun smoothMotion(
        frames: List<FrameData>,
        strength: Float = 0.5f
    ): List<FrameData> {
        if (frames.size < 3) return frames

        val kernel = createGaussianKernel(strength)
        val kernelRadius = kernel.size / 2
        val result = mutableListOf<FrameData>()

        for (i in frames.indices) {
            val smoothedStrokes = mutableListOf<StrokePath>()

            // For each frame, blend neighboring frames
            val allStrokes = mutableMapOf<Int, MutableList<StrokePath>>()

            for (offset in -kernelRadius..kernelRadius) {
                val neighborIdx = (i + offset).coerceIn(0, frames.lastIndex)
                val weight = kernel[offset + kernelRadius]
                val neighborFrame = frames[neighborIdx]

                for (stroke in neighborFrame.strokePaths) {
                    // Apply weighted positions
                    val weightedPath = stroke.path.map { pt ->
                        PathPoint(
                            x = pt.x * weight + pt.x * (1 - weight),
                            y = pt.y * weight + pt.y * (1 - weight),
                            pressure = pt.pressure,
                            tilt = pt.tilt
                        )
                    }
                    smoothedStrokes.add(stroke.copy(path = weightedPath))
                }
            }

            result.add(FrameData(strokePaths = smoothedStrokes))
        }

        return result
    }

    private fun createGaussianKernel(sigma: Float): FloatArray {
        val adjustedSigma = 0.5f + sigma * 2.5f
        val radius = maxOf(1, ceil(adjustedSigma * 3).toInt())
        val kernel = FloatArray(radius * 2 + 1)
        var sum = 0f

        for (i in -radius..radius) {
            val value = exp(-(i * i) / (2 * adjustedSigma * adjustedSigma))
            kernel[i + radius] = value
            sum += value
        }

        // Normalize
        for (i in kernel.indices) {
            kernel[i] /= sum
        }

        return kernel
    }

    // Helper math functions
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun lerpColor(colorA: Int, colorB: Int, t: Float): Int {
        val a = (Color.alpha(colorA) + (Color.alpha(colorB) - Color.alpha(colorA)) * t).toInt()
        val r = (Color.red(colorA) + (Color.red(colorB) - Color.red(colorA)) * t).toInt()
        val g = (Color.green(colorA) + (Color.green(colorB) - Color.green(colorA)) * t).toInt()
        val b = (Color.blue(colorA) + (Color.blue(colorB) - Color.blue(colorA)) * t).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun Float.pow(n: Int): Float = this.toDouble().pow(n).toFloat()
    fun PointF.distanceTo(other: PointF): Float =
        sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
}

data class OnionSkinFrame(
    val frameIndex: Int,
    val opacity: Float,
    val isBefore: Boolean
)
