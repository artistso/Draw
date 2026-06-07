package com.animationstudio.tools

import android.graphics.*
import com.animationstudio.engine.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Advanced brush engine with realistic brush simulation.
 * Supports pressure sensitivity, tilt, tapering, and custom brush shapes.
 */
class BrushEngine {

    data class BrushConfig(
        val type: BrushType = BrushType.ROUND,
        val size: Float = 10f,
        val opacity: Float = 1f,
        val hardness: Float = 0.7f,
        val spacing: Float = 0.3f,
        val smoothing: Float = 0.3f,
        val taper: Boolean = true,
        val pressureSensitive: Boolean = true,
        val tiltSensitive: Boolean = true,
        val scatter: Float = 0f,
        val wetness: Float = 0f,
        val color: Int = Color.BLACK,
        val textureId: Int = 0
    )

    enum class BrushType {
        ROUND, FLAT, AIRBRUSH, PENCIL, INK, CHARCOAL,
        WATERCOLOR, OIL, CALLIGRAPHY, ERASER, CUSTOM
    }

    private val random = Random(System.currentTimeMillis())

    /**
     * Render a stroke path to a canvas using brush simulation
     */
    fun renderStroke(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        existingPaint: Paint? = null
    ) {
        if (points.size < 2) return

        val paint = existingPaint ?: Paint().apply {
            isAntiAliased = true
            isDither = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        when (config.type) {
            BrushType.ROUND -> renderRoundBrush(canvas, points, config, paint)
            BrushType.FLAT -> renderFlatBrush(canvas, points, config, paint)
            BrushType.AIRBRUSH -> renderAirbrush(canvas, points, config, paint)
            BrushType.PENCIL -> renderPencil(canvas, points, config, paint)
            BrushType.INK -> renderInkBrush(canvas, points, config, paint)
            BrushType.CHARCOAL -> renderCharcoal(canvas, points, config, paint)
            BrushType.WATERCOLOR -> renderWatercolor(canvas, points, config, paint)
            BrushType.CALLIGRAPHY -> renderCalligraphy(canvas, points, config, paint)
            BrushType.ERASER -> renderEraser(canvas, points, config, paint)
            else -> renderRoundBrush(canvas, points, config, paint)
        }
    }

    private fun renderRoundBrush(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            val dist = sqrt((p1.x - p0.x).pow(2) + (p1.y - p0.y).pow(2))
            val steps = maxOf(1, (dist / (config.size * config.spacing)).toInt() + 1)

            for (s in 0..steps) {
                val t = s.toFloat() / (steps + 1)
                val x = lerp(p0.x, p1.x, t)
                val y = lerp(p0.y, p1.y, t)
                val pressure = lerp(p0.pressure, p1.pressure, t)

                val brushSize = if (config.pressureSensitive) {
                    config.size * (0.3f + pressure * 0.7f)
                } else config.size

                val alpha = (config.opacity * pressure * 255).toInt().coerceIn(0, 255)
                paint.color = Color.argb(alpha,
                    Color.red(config.color),
                    Color.green(config.color),
                    Color.blue(config.color))

                // Create gradient brush dot
                val gradientRadius = brushSize * config.hardness
                val radialShader = RadialGradient(
                    x, y,
                    brushSize,
                    intArrayOf(paint.color, Color.argb(0, Color.red(config.color),
                        Color.green(config.color), Color.blue(config.color))),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                paint.shader = radialShader
                paint.strokeWidth = brushSize * 2
                canvas.drawPoint(x, y, paint)
            }
        }
        paint.shader = null
    }

    private fun renderFlatBrush(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        paint.strokeCap = Paint.Cap.BUTT

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            val angle = atan2(p1.y - p0.y, p1.x - p0.x)
            val brushWidth = if (config.pressureSensitive) {
                config.size * (0.2f + p0.pressure * 0.8f) * 3f
            } else config.size * 3f

            val brushHeight = config.size * 0.3f

            val alpha = (config.opacity * 255).toInt().coerceIn(0, 255)
            paint.color = Color.argb(alpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color))
            paint.strokeWidth = brushHeight

            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
        }
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun renderAirbrush(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        for (i in points.indices) {
            val p = points[i]
            val pressure = p.pressure
            val brushSize = config.size * (0.5f + pressure * 0.5f)

            val numDots = (brushSize * brushSize * 0.3f * pressure).toInt().coerceIn(5, 500)

            for (d in 0 until numDots) {
                val angle = random.nextFloat() * 2 * PI.toFloat()
                val radius = random.nextFloat() * brushSize

                // Gaussian-like distribution (more dots near center)
                val gaussRadius = abs(random.nextGaussian().toFloat()) * brushSize * 0.4f

                val dx = cos(angle) * gaussRadius
                val dy = sin(angle) * gaussRadius

                val alpha = (config.opacity * pressure * 0.15f * 255).toInt().coerceIn(0, 255)
                paint.color = Color.argb(alpha,
                    Color.red(config.color),
                    Color.green(config.color),
                    Color.blue(config.color))
                paint.strokeWidth = maxOf(1f, brushSize * 0.05f)
                canvas.drawPoint(p.x + dx, p.y + dy, paint)
            }
        }
    }

    private fun renderPencil(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        paint.isAntiAliased = false // Choppy on purpose
        paint.strokeWidth = config.size * 0.5f

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            val dist = sqrt((p1.x - p0.x).pow(2) + (p1.y - p0.y).pow(2))
            val steps = maxOf(1, (dist / (config.size * config.spacing * 0.5f)).toInt())

            for (s in 0..steps) {
                val t = s.toFloat() / (steps + 1)
                val pressure = lerp(p0.pressure, p1.pressure, t)
                val alpha = (config.opacity * pressure * pressure * 255).toInt().coerceIn(0, 255)

                paint.color = Color.argb(alpha,
                    Color.red(config.color),
                    Color.green(config.color),
                    Color.blue(config.color))

                // Add slight jitter for pencil texture
                val jx = (random.nextFloat() - 0.5f) * config.size * 0.3f
                val jy = (random.nextFloat() - 0.5f) * config.size * 0.3f

                canvas.drawPoint(
                    lerp(p0.x, p1.x, t) + jx,
                    lerp(p0.y, p1.y, t) + jy,
                    paint
                )
            }
        }
        paint.isAntiAliased = true
    }

    private fun renderInkBrush(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        if (points.size < 2) return

        val path = Path()
        path.moveTo(points[0].x, points[0].y)

        // Build smooth bezier path
        for (i in 0 until points.size - 2) {
            val midX = (points[i].x + points[i + 1].x) / 2
            val midY = (points[i].y + points[i + 1].y) / 2
            path.quadTo(points[i].x, points[i].y, midX, midY)
        }

        if (points.size >= 2) {
            path.lineTo(points.last().x, points.last().y)
        }

        // Vary stroke width based on pressure
        val pressureAvg = points.map { it.pressure }.average().toFloat()
        val baseWidth = if (config.pressureSensitive) {
            config.size * (0.5f + pressureAvg * 0.5f)
        } else config.size

        paint.strokeWidth = baseWidth
        paint.color = config.color
        paint.alpha = (config.opacity * 255).toInt()
        paint.style = Paint.Style.STROKE

        canvas.drawPath(path, paint)
    }

    private fun renderCharcoal(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        for (i in points.indices) {
            val p = points[i]
            val brushSize = config.size * (0.5f + p.pressure * 0.5f)
            val alpha = (config.opacity * p.pressure * 0.3f * 255).toInt().coerceIn(0, 255)

            // Draw multiple textured dots
            for (d in 0 until 3) {
                val angle = random.nextFloat() * 2 * PI.toFloat()
                val radius = random.nextFloat() * brushSize
                paint.color = Color.argb(alpha,
                    Color.red(config.color),
                    Color.green(config.color),
                    Color.blue(config.color))
                paint.strokeWidth = brushSize * 0.2f
                canvas.drawPoint(
                    p.x + cos(angle) * radius,
                    p.y + sin(angle) * radius,
                    paint
                )
            }
        }
    }

    private fun renderWatercolor(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        // Watercolor: soft, transparent washes with irregular edges
        for (i in points.indices) {
            val p = points[i]
            val brushSize = config.size * (1f + p.pressure)

            // Soft color wash
            val alpha = (config.opacity * 0.15f * 255).toInt().coerceIn(0, 255)
            paint.color = Color.argb(alpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color))

            val radialShader = RadialGradient(
                p.x, p.y, brushSize,
                intArrayOf(
                    Color.argb(alpha, Color.red(config.color),
                        Color.green(config.color), Color.blue(config.color)),
                    Color.argb(0, 0, 0, 0)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = radialShader
            paint.strokeWidth = brushSize * 3
            canvas.drawPoint(p.x, p.y, paint)

            // Add darker edge
            val edgeAlpha = (config.opacity * 0.25f * 255).toInt().coerceIn(0, 255)
            paint.shader = null
            paint.color = Color.argb(edgeAlpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color))
            paint.strokeWidth = brushSize * 0.3f
            canvas.drawPoint(p.x, p.y, paint)
        }
        paint.shader = null
    }

    private fun renderCalligraphy(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        paint.strokeCap = Paint.Cap.ROUND

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            val angle = atan2(p1.y - p0.y, p1.x - p0.x)
            val tiltAngle = if (config.tiltSensitive) {
                angle + p0.tilt * PI.toFloat() / 4f
            } else {
                angle
            }

            val width = config.size * (0.5f + (abs(sin(tiltAngle)) * 0.5f))
            val alpha = (config.opacity * p0.pressure * 255).toInt().coerceIn(0, 255)

            paint.color = Color.argb(alpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color))
            paint.strokeWidth = width

            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
        }
    }

    private fun renderEraser(
        canvas: Canvas,
        points: List<PathPoint>,
        config: BrushConfig,
        paint: Paint
    ) {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        paint.strokeWidth = config.size
        paint.strokeCap = Paint.Cap.ROUND

        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }

        canvas.drawPath(path, paint)
        paint.xfermode = null
    }

    /**
     * Create a bitmap texture for custom brush stamps
     */
    fun createBrushTexture(
        size: Float,
        hardness: Float,
        shape: BrushType
    ): Bitmap {
        val texSize = (size * 4).toInt().coerceIn(16, 256)
        val bitmap = Bitmap.createBitmap(texSize, texSize, Bitmap.Config.ARGB_8888)
        val texCanvas = Canvas(bitmap)
        val cx = texSize / 2f
        val cy = texSize / 2f

        val paint = Paint().apply { isAntiAliased = true }

        when (shape) {
            BrushType.ROUND -> {
                val gradient = RadialGradient(cx, cy, texSize / 2f,
                    intArrayOf(
                        Color.argb(255, 0, 0, 0),
                        Color.argb((hardness * 200).toInt(), 0, 0, 0),
                        Color.argb(0, 0, 0, 0)
                    ),
                    floatArrayOf(0f, hardness, 1f),
                    Shader.TileMode.CLAMP
                )
                paint.shader = gradient
                texCanvas.drawRect(0f, 0f, texSize.toFloat(), texSize.toFloat(), paint)
            }
            BrushType.FLAT -> {
                paint.color = Color.BLACK
                texCanvas.drawRoundRect(
                    cx - texSize / 4f, cy - texSize / 2f,
                    cx + texSize / 4f, cy + texSize / 2f,
                    texSize / 8f, texSize / 8f, paint
                )
            }
            else -> {
                paint.color = Color.BLACK
                texCanvas.drawCircle(cx, cy, texSize / 3f, paint)
            }
        }

        return bitmap
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun Float.pow(n: Int): Float = this.toDouble().pow(n).toFloat()
}
