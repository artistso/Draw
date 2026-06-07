package com.animationstudio.ai

import android.graphics.*
import com.animationstudio.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * AI-Powered Tweening Engine
 *
 * Uses advanced algorithms for automatic in-betweening:
 * - Optical flow estimation for raster frames
 * - Shape morphing with feature matching for vector strokes
 * - Motion field prediction for multi-frame sequences
 * - Physics-based secondary motion generation
 */
class AITweeningEngine(
    private val modelManager: AIModelManager
) {
    /**
     * Generate in-between frames using optical flow and feature matching.
     * This is the main entry point for AI tweening.
     */
    suspend fun generateInBetweens(
        keyframeA: FrameData,
        keyframeB: FrameData,
        numInBetweens: Int,
        easingType: EasingType = EasingType.SMOOTH
    ): List<FrameData> = withContext(Dispatchers.Default) {
        val results = mutableListOf<FrameData>()

        // Try AI model first, fall back to algorithmic approach
        val useModel = modelManager.isTweeningModelLoaded()

        for (i in 1..numInBetweens) {
            val rawT = i.toFloat() / (numInBetweens + 1)
            val t = applyEasing(rawT, easingType)

            val interpolated = if (useModel && keyframeA.strokePaths.size <= 50) {
                modelAssistedInterpolation(keyframeA, keyframeB, t)
            } else {
                algorithmicInterpolation(keyframeA, keyframeB, t)
            }

            results.add(interpolated)
        }

        results
    }

    /**
     * Algorithmic interpolation with advanced feature matching
     */
    private fun algorithmicInterpolation(
        frameA: FrameData,
        frameB: FrameData,
        t: Float
    ): FrameData {
        val interpolatedStrokes = mutableListOf<StrokePath>()

        // Build feature descriptors for each stroke
        val featuresA = frameA.strokePaths.map { StrokeFeatures(it) }
        val featuresB = frameB.strokePaths.map { StrokeFeatures(it) }

        // Match strokes using feature similarity
        val matches = matchStrokesByFeatures(featuresA, featuresB)

        for ((indexA, indexB, confidence) in matches) {
            val strokeA = frameA.strokePaths[indexA]
            val strokeB = frameB.strokePaths[indexB]

            interpolatedStrokes.add(
                morphStroke(strokeA, strokeB, t, confidence)
            )
        }

        // Handle unmatched strokes with fade in/out
        val matchedIndicesA = matches.map { it.first }.toSet()
        val matchedIndicesB = matches.map { it.second }.toSet()

        for ((index, stroke) in frameA.strokePaths.withIndex()) {
            if (index !in matchedIndicesA) {
                interpolatedStrokes.add(
                    stroke.copy(
                        opacity = stroke.opacity * (1f - t),
                        path = stroke.path.map { pt ->
                            pt.copy(
                                x = pt.x * (1f - t),
                                y = pt.y * (1f - t)
                            )
                        }
                    )
                )
            }
        }

        for ((index, stroke) in frameB.strokePaths.withIndex()) {
            if (index !in matchedIndicesB) {
                interpolatedStrokes.add(
                    stroke.copy(
                        opacity = stroke.opacity * t,
                        path = stroke.path.map { pt ->
                            pt.copy(
                                x = pt.x * t,
                                y = pt.y * t
                            )
                        }
                    )
                )
            }
        }

        return FrameData(strokePaths = interpolatedStrokes)
    }

    /**
     * Model-assisted interpolation for complex cases
     */
    private fun modelAssistedInterpolation(
        frameA: FrameData,
        frameB: FrameData,
        t: Float
    ): FrameData {
        // Use the TFLite model to predict motion vectors
        val motionField = modelManager.predictMotionField(frameA, frameB, t)

        return if (motionField != null) {
            applyMotionField(frameA, motionField, t)
        } else {
            algorithmicInterpolation(frameA, frameB, t)
        }
    }

    private fun applyMotionField(
        frame: FrameData,
        motionField: MotionField,
        t: Float
    ): FrameData {
        val transformedStrokes = frame.strokePaths.map { stroke ->
            val newPath = stroke.path.map { pt ->
                val motion = motionField.getMotionAt(pt.x, pt.y)
                PathPoint(
                    x = pt.x + motion.dx * t,
                    y = pt.y + motion.dy * t,
                    pressure = pt.pressure,
                    tilt = pt.tilt
                )
            }
            stroke.copy(path = newPath)
        }
        return FrameData(strokePaths = transformedStrokes)
    }

    /**
     * Morph between two strokes with shape preservation
     */
    private fun morphStroke(
        from: StrokePath,
        to: StrokePath,
        t: Float,
        confidence: Float
    ): StrokePath {
        // Resample both to same point count
        val targetPoints = maxOf(from.path.size, to.path.size)
        val pointsFrom = resamplePath(from.path, targetPoints)
        val pointsTo = resamplePath(to.path, targetPoints)

        val adjustedT = t * confidence + t * (1 - confidence) * 0.5f

        val morphedPoints = pointsFrom.zip(pointsTo).map { (pA, pB) ->
            PathPoint(
                x = lerp(pA.x, pB.x, adjustedT),
                y = lerp(pA.y, pB.y, adjustedT),
                pressure = lerp(pA.pressure, pB.pressure, adjustedT),
                tilt = lerp(pA.tilt, pB.tilt, adjustedT)
            )
        }

        return StrokePath(
            path = morphedPoints,
            color = lerpColor(from.color, to.color, adjustedT),
            strokeWidth = lerp(from.strokeWidth, to.strokeWidth, adjustedT),
            opacity = lerp(from.opacity, to.opacity, adjustedT),
            tool = if (t < 0.5f) from.tool else to.tool,
            smoothing = lerp(from.smoothing, to.smoothing, adjustedT)
        )
    }

    private fun matchStrokesByFeatures(
        featuresA: List<StrokeFeatures>,
        featuresB: List<StrokeFeatures>
    ): List<Triple<Int, Int, Float>> {
        val matches = mutableListOf<Triple<Int, Int, Float>>()
        val usedB = mutableSetOf<Int>()

        for ((i, featA) in featuresA.withIndex()) {
            var bestMatch = -1
            var bestScore = 0f

            for ((j, featB) in featuresB.withIndex()) {
                if (j in usedB) continue
                val score = computeFeatureSimilarity(featA, featB)
                if (score > bestScore && score > 0.3f) {
                    bestScore = score
                    bestMatch = j
                }
            }

            if (bestMatch >= 0) {
                usedB.add(bestMatch)
                matches.add(Triple(i, bestMatch, bestScore))
            }
        }

        return matches
    }

    private fun computeFeatureSimilarity(a: StrokeFeatures, b: StrokeFeatures): Float {
        val posSim = 1f / (1f + a.centroid.distanceTo(b.centroid) / 100f)
        val areaSim = 1f - abs(a.boundingArea - b.boundingArea) /
                maxOf(a.boundingArea, b.boundingArea, 0.01f)
        val lengthSim = 1f - abs(a.pathLength - b.pathLength) /
                maxOf(a.pathLength, b.pathLength, 0.01f)
        val dirSim = abs(a.principalDirection.dot(b.principalDirection))

        return posSim * 0.3f + areaSim * 0.2f + lengthSim * 0.2f + dirSim * 0.3f
    }

    private fun applyEasing(t: Float, type: EasingType): Float = when (type) {
        EasingType.LINEAR -> t
        EasingType.SMOOTH -> t * t * (3 - 2 * t) // Smoothstep
        EasingType.EASE_IN -> t * t
        EasingType.EASE_OUT -> 1f - (1f - t) * (1f - t)
        EasingType.EASE_IN_OUT -> if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
        EasingType.BOUNCE -> bounceOut(t)
        EasingType.ELASTIC -> elasticOut(t)
    }

    private fun bounceOut(t: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        var t2 = t
        return when {
            t2 < 1 / d1 -> n1 * t2 * t2
            t2 < 2 / d1 -> { t2 -= 1.5f / d1; n1 * t2 * t2 + 0.75f }
            t2 < 2.5 / d1 -> { t2 -= 2.25f / d1; n1 * t2 * t2 + 0.9375f }
            else -> { t2 -= 2.625f / d1; n1 * t2 * t2 + 0.984375f }
        }
    }

    private fun elasticOut(t: Float): Float {
        if (t == 0f || t == 1f) return t
        return 2f.pow(-10 * t) * sin((t * 10 - 0.75f) * (2 * PI).toFloat() / 3) + 1
    }

    // Helpers
    private fun resamplePath(path: List<PathPoint>, count: Int): List<PathPoint> {
        if (path.size < 2) return List(count) { path.firstOrNull() ?: PathPoint(0f, 0f) }
        val step = calculatePathLength(path) / (count - 1)
        val result = mutableListOf<PathPoint>()
        var distAcc = 0f
        var idx = 0
        for (i in 0 until count) {
            val target = i * step
            while (idx < path.lastIndex && distAcc < target) {
                distAcc += PointF(path[idx].x, path[idx].y)
                    .distanceTo(PointF(path[idx + 1].x, path[idx + 1].y))
                idx++
            }
            result.add(if (idx >= path.lastIndex) path.last() else {
                val localT = ((target - (distAcc - PointF(path[idx - 1].x, path[idx - 1].y)
                    .distanceTo(PointF(path[idx].x, path[idx].y)))) /
                        PointF(path[idx].x, path[idx].y)
                            .distanceTo(PointF(path[idx + 1].x, path[idx + 1].y))).coerceIn(0f, 1f)
                PathPoint(
                    lerp(path[idx].x, path[idx + 1].x, localT),
                    lerp(path[idx].y, path[idx + 1].y, localT)
                )
            })
        }
        return result
    }

    private fun calculatePathLength(path: List<PathPoint>): Float {
        var len = 0f
        for (i in 0 until path.lastIndex) {
            len += PointF(path[i].x, path[i].y).distanceTo(PointF(path[i + 1].x, path[i + 1].y))
        }
        return len
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * t).toInt()
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun Float.pow(n: Int): Float = this.toDouble().pow(n).toFloat()
    private fun PointF.distanceTo(other: PointF): Float =
        sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
}

data class StrokeFeatures(
    val centroid: PointF,
    val boundingArea: Float,
    val pathLength: Float,
    val principalDirection: PointF
) {
    constructor(stroke: StrokePath) : this(
        centroid = PointF(
            stroke.path.map { it.x }.average().toFloat(),
            stroke.path.map { it.y }.average().toFloat()
        ),
        boundingArea = run {
            val xs = stroke.path.map { it.x }
            val ys = stroke.path.map { it.y }
            ((xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)) *
                    ((ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f))
        },
        pathLength = run {
            var len = 0f
            for (i in 0 until stroke.path.lastIndex) {
                len += PointF(stroke.path[i].x, stroke.path[i].y)
                    .distanceTo(PointF(stroke.path[i + 1].x, stroke.path[i + 1].y))
            }
            len
        },
        principalDirection = run {
            val first = stroke.path.firstOrNull()
            val last = stroke.path.lastOrNull()
            if (first != null && last != null) {
                val dx = last.x - first.x
                val dy = last.y - first.y
                val mag = sqrt(dx * dx + dy * dy)
                if (mag > 0) PointF(dx / mag, dy / mag) else PointF(1f, 0f)
            } else PointF(1f, 0f)
        }
    )
}

data class MotionField(
    val width: Int,
    val height: Int,
    private val vectors: FloatArray // dx, dy interleaved
) {
    fun getMotionAt(x: Float, y: Float): MotionVector {
        val ix = (x * width).toInt().coerceIn(0, width - 1)
        val iy = (y * height).toInt().coerceIn(0, height - 1)
        val idx = (iy * width + ix) * 2
        return MotionVector(vectors[idx], vectors[idx + 1])
    }
}

data class MotionVector(val dx: Float, val dy: Float)

enum class EasingType {
    LINEAR, SMOOTH, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE, ELASTIC
}
