package com.animationstudio.ai

import android.graphics.PointF
import com.animationstudio.engine.*
import kotlin.math.*

/**
 * Intelligent Motion Smoothing Engine
 *
 * Provides multiple smoothing techniques:
 * - Temporal Gaussian smoothing
 * - Savitzky-Golay filtering for trajectory smoothing
 * - Adaptive smoothing that preserves sharp corners
 * - Secondary motion prediction (hair, cloth, etc.)
 */
class MotionSmoother {

    data class SmoothingConfig(
        val temporalWindow: Int = 5,       // Frames to consider
        val spatialSigma: Float = 1.5f,     // Spatial smoothing strength
        val temporalSigma: Float = 1.0f,    // Temporal smoothing strength
        val cornerPreservation: Float = 0.7f, // 0=smooth everything, 1=preserve corners
        val secondaryMotion: Boolean = true,
        val secondaryStrength: Float = 0.3f
    )

    /**
     * Apply full motion smoothing pipeline to a sequence of frames
     */
    fun smoothFrameSequence(
        frames: List<FrameData>,
        config: SmoothingConfig = SmoothingConfig()
    ): List<FrameData> {
        if (frames.size < 3) return frames

        // Step 1: Spatial smoothing within each frame
        val spatiallySmoothed = frames.map { frame ->
            applySpatialSmoothing(frame, config.spatialSigma, config.cornerPreservation)
        }

        // Step 2: Temporal smoothing across frames
        val temporallySmoothed = applyTemporalSmoothing(
            spatiallySmoothed,
            config.temporalWindow,
            config.temporalSigma,
            config.cornerPreservation
        )

        // Step 3: Secondary motion simulation
        return if (config.secondaryMotion) {
            addSecondaryMotion(temporallySmoothed, config.secondaryStrength)
        } else {
            temporallySmoothed
        }
    }

    /**
     * Spatial smoothing: removes hand jitter while preserving intentional shapes
     */
    private fun applySpatialSmoothing(
        frame: FrameData,
        sigma: Float,
        cornerPreservation: Float
    ): FrameData {
        val smoothedStrokes = frame.strokePaths.map { stroke ->
            smoothStroke(stroke, sigma, cornerPreservation)
        }
        return frame.copy(strokePaths = smoothedStrokes)
    }

    private fun smoothStroke(
        stroke: StrokePath,
        sigma: Float,
        cornerPreservation: Float
    ): StrokePath {
        if (stroke.path.size < 3) return stroke

        val kernel = createAdaptiveGaussianKernel(sigma, stroke.path.size)
        val smoothed = mutableListOf<PathPoint>()

        for (i in stroke.path.indices) {
            val cornerWeight = detectCorner(stroke.path, i)
            val adjustedSigma = sigma * (1f - cornerWeight * cornerPreservation)

            if (adjustedSigma < 0.1f) {
                // Sharp corner - preserve exactly
                smoothed.add(stroke.path[i])
                continue
            }

            val localKernel = createAdaptiveGaussianKernel(adjustedSigma, stroke.path.size)
            var sumX = 0f
            var sumY = 0f
            var sumPressure = 0f
            var weightSum = 0f

            val radius = localKernel.size / 2
            for (offset in -radius..radius) {
                val idx = (i + offset).coerceIn(0, stroke.path.lastIndex)
                val w = localKernel[offset + radius]
                sumX += stroke.path[idx].x * w
                sumY += stroke.path[idx].y * w
                sumPressure += stroke.path[idx].pressure * w
                weightSum += w
            }

            smoothed.add(
                PathPoint(
                    x = sumX / weightSum,
                    y = sumY / weightSum,
                    pressure = sumPressure / weightSum,
                    tilt = stroke.path[i].tilt
                )
            )
        }

        return stroke.copy(path = smoothed)
    }

    /**
     * Detect corners using curvature analysis
     */
    private fun detectCorner(points: List<PathPoint>, index: Int): Float {
        if (index == 0 || index == points.lastIndex) return 1f

        val prev = points[index - 1]
        val curr = points[index]
        val next = points[index + 1]

        val dx1 = curr.x - prev.x
        val dy1 = curr.y - prev.y
        val dx2 = next.x - curr.x
        val dy2 = next.y - curr.y

        val len1 = sqrt(dx1 * dx1 + dy1 * dy1).coerceAtLeast(0.001f)
        val len2 = sqrt(dx2 * dx2 + dy2 * dy2).coerceAtLeast(0.001f)

        val dot = (dx1 * dx2 + dy1 * dy2) / (len1 * len2)
        val angle = acos(dot.coerceIn(-1f, 1f))

        // Angle near PI = sharp corner, near 0 = straight line
        return (angle / PI.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Temporal smoothing using Savitzky-Golay style filtering
     */
    private fun applyTemporalSmoothing(
        frames: List<FrameData>,
        window: Int,
        sigma: Float,
        cornerPreservation: Float
    ): List<FrameData> {
        val halfWindow = window / 2
        val temporalKernel = createTemporalKernel(window, sigma)
        val result = mutableListOf<FrameData>()

        for (frameIdx in frames.indices) {
            val frame = frames[frameIdx]
            val smoothedStrokes = mutableListOf<StrokePath>()

            for (stroke in frame.strokePaths) {
                // Find matching strokes in neighboring frames
                val neighbors = mutableListOf<StrokePath?>()
                for (offset in -halfWindow..halfWindow) {
                    val neighborIdx = (frameIdx + offset).coerceIn(0, frames.lastIndex)
                    val neighborFrame = frames[neighborIdx]

                    if (neighborIdx == frameIdx) {
                        neighbors.add(stroke)
                    } else {
                        // Find best matching stroke
                        val matched = findBestMatchingStroke(stroke, neighborFrame.strokePaths)
                        neighbors.add(matched)
                    }
                }

                // Blend matched strokes
                if (neighbors.any { it != null }) {
                    val blendedPath = mutableListOf<PathPoint>()
                    val maxPoints = neighbors.filterNotNull()
                        .maxOfOrNull { it.path.size } ?: stroke.path.size

                    for (pointIdx in 0 until maxPoints) {
                        var sumX = 0f
                        var sumY = 0f
                        var sumPressure = 0f
                        var weightSum = 0f

                        for ((nIdx, neighbor) in neighbors.withIndex()) {
                            val w = temporalKernel[nIdx]
                            if (neighbor != null && pointIdx < neighbor.path.size) {
                                sumX += neighbor.path[pointIdx].x * w
                                sumY += neighbor.path[pointIdx].y * w
                                sumPressure += neighbor.path[pointIdx].pressure * w
                                weightSum += w
                            }
                        }

                        if (weightSum > 0f) {
                            blendedPath.add(
                                PathPoint(sumX / weightSum, sumY / weightSum, sumPressure / weightSum)
                            )
                        }
                    }

                    smoothedStrokes.add(stroke.copy(path = blendedPath))
                } else {
                    smoothedStrokes.add(stroke)
                }
            }

            result.add(frame.copy(strokePaths = smoothedStrokes))
        }

        return result
    }

    private fun findBestMatchingStroke(
        target: StrokePath,
        candidates: List<StrokePath>
    ): StrokePath? {
        if (candidates.isEmpty()) return null
        val targetCentroid = computeCentroid(target)

        return candidates.minByOrNull { candidate ->
            val candCentroid = computeCentroid(candidate)
            val dist = PointF(targetCentroid.x, targetCentroid.y)
                .distanceTo(PointF(candCentroid.x, candCentroid.y))
            val sizeDiff = abs(target.path.size - candidate.path.size)
            dist + sizeDiff * 0.1f
        }
    }

    private fun computeCentroid(stroke: StrokePath): PointF {
        val cx = stroke.path.map { it.x }.average().toFloat()
        val cy = stroke.path.map { it.y }.average().toFloat()
        return PointF(cx, cy)
    }

    /**
     * Add secondary motion effects (overshoot, follow-through)
     * This simulates physics-based secondary animation like hair bounce
     */
    private fun addSecondaryMotion(
        frames: List<FrameData>,
        strength: Float
    ): List<FrameData> {
        if (frames.size < 3) return frames

        val result = mutableListOf<FrameData>()
        val velocities = mutableListOf<List<FloatArray>>() // per-stroke, per-point velocity

        // Calculate velocities between consecutive frames
        for (i in 0 until frames.lastIndex) {
            val frameVels = mutableListOf<FloatArray>()
            val currFrame = frames[i]
            val nextFrame = frames[i + 1]

            for (strokeIdx in currFrame.strokePaths.indices) {
                if (strokeIdx < nextFrame.strokePaths.size) {
                    val curr = currFrame.strokePaths[strokeIdx]
                    val next = nextFrame.strokePaths[strokeIdx]
                    val vels = FloatArray(minOf(curr.path.size, next.path.size) * 2)

                    for (j in 0 until minOf(curr.path.size, next.path.size)) {
                        vels[j * 2] = next.path[j].x - curr.path[j].x
                        vels[j * 2 + 1] = next.path[j].y - curr.path[j].y
                    }
                    frameVels.add(vels)
                }
            }
            velocities.add(frameVels)
        }

        // Apply secondary motion with spring-damper physics
        for (i in frames.indices) {
            val frame = frames[i]
            val secondaryStrokes = frame.strokePaths.mapIndexed { strokeIdx, stroke ->
                if (i == 0 || i >= velocities.size) {
                    stroke
                } else {
                    val vel = velocities[i - 1].getOrNull(strokeIdx) ?: return@mapIndexed stroke
                    val secondaryPath = stroke.path.mapIndexed { ptIdx, pt ->
                        if (ptIdx * 2 < vel.size) {
                            val dx = vel[ptIdx * 2] * strength * 0.5f
                            val dy = vel[ptIdx * 2 + 1] * strength * 0.5f
                            PathPoint(
                                x = pt.x + dx,
                                y = pt.y + dy,
                                pressure = pt.pressure,
                                tilt = pt.tilt
                            )
                        } else pt
                    }
                    stroke.copy(path = secondaryPath)
                }
            }
            result.add(frame.copy(strokePaths = secondaryStrokes))
        }

        return result
    }

    private fun createAdaptiveGaussianKernel(sigma: Float, dataSize: Int): FloatArray {
        val radius = minOf(maxOf(1, ceil(sigma * 3).toInt()), dataSize / 2)
        val kernel = FloatArray(radius * 2 + 1)
        var sum = 0f
        for (i in -radius..radius) {
            val value = exp(-(i * i) / (2 * sigma * sigma))
            kernel[i + radius] = value
            sum += value
        }
        for (i in kernel.indices) kernel[i] /= sum
        return kernel
    }

    private fun createTemporalKernel(window: Int, sigma: Float): FloatArray {
        val half = window / 2
        val kernel = FloatArray(window)
        var sum = 0f
        for (i in -half..half) {
            val value = exp(-(i * i) / (2 * sigma * sigma))
            kernel[i + half] = value
            sum += value
        }
        for (i in kernel.indices) kernel[i] /= sum
        return kernel
    }

    private fun PointF.distanceTo(other: PointF): Float =
        sqrt((x - other.x).pow(2) + (y - other.y).pow(2))

    private fun Float.pow(n: Int): Float = this.toDouble().pow(n).toFloat()
}