package dev.mrrosoff.tv.dot.dots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * The LED panel behind Home: a grid of faint dots with slow waves of light flowing across it. Two wave
 * fronts travel the screen at different angles and speeds, so where they meet a soft lattice of brighter
 * dots drifts by, and the whole field breathes on a [BREATH_S]-second rhythm. The dots dim toward the left
 * edge, where the wheel's colour glow already lights the screen.
 *
 * Dots are sorted into [LEVELS] brightness steps and each step is one drawPoints call, so a frame is a
 * handful of draw calls however many dots there are. Runs at about 20 fps, and only while [running].
 */
class DotFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var running = false
        set(value) {
            field = value
            if (value) postInvalidateOnAnimation()
        }

    private val pitch = dp(12f)
    private val baseDiameter = dp(1.8f)
    private val start = System.nanoTime()

    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    /** Each dot's brightness scale: [LEFT_EDGE] at the left edge, rising to full by the middle of the screen. */
    private var fades = FloatArray(0)
    private val buckets = Array(LEVELS) { FloatArray(0) }
    private val counts = IntArray(LEVELS)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cols = (w / pitch).toInt() + 1
        val rows = (h / pitch).toInt() + 1
        val n = cols * rows
        xs = FloatArray(n) { (it % cols) * pitch + pitch / 2 }
        ys = FloatArray(n) { (it / cols) * pitch + pitch / 2 }
        fades = FloatArray(n) {
            val x = (xs[it] / w / FADE_TO).coerceIn(0f, 1f)
            LEFT_EDGE + (1f - LEFT_EDGE) * x * x * (3f - 2f * x)
        }
        for (i in 0 until LEVELS) buckets[i] = FloatArray(n * 2)
    }

    override fun onDraw(canvas: Canvas) {
        if (xs.isEmpty()) return
        val t = (System.nanoTime() - start) / 1e9f
        val w = width.toFloat()
        val h = height.toFloat()
        val breath = 0.7f + 0.3f * (0.5f + 0.5f * sin(t * TWO_PI / BREATH_S))
        counts.fill(0)
        for (i in xs.indices) {
            val nx = xs[i] / w
            val ny = ys[i] / h
            val a = 0.5f + 0.5f * sin(TWO_PI * (nx * 1.2f + ny * 0.6f) - t * 0.3f)
            val c = 0.5f + 0.5f * sin(TWO_PI * (ny * 1.0f - nx * 0.5f) - t * 0.19f)
            val wave = a * 0.6f + c * 0.4f
            val b = (FLOOR + (1f - FLOOR) * wave * wave * breath) * fades[i]
            val level = (b * LEVELS).toInt().coerceIn(0, LEVELS - 1)
            val k = counts[level] * 2
            buckets[level][k] = xs[i]
            buckets[level][k + 1] = ys[i]
            counts[level]++
        }
        for (level in 0 until LEVELS) {
            if (counts[level] == 0) continue
            val f = (level + 0.5f) / LEVELS
            paint.alpha = (MAX_ALPHA * f).toInt()
            paint.strokeWidth = baseDiameter * (0.85f + 0.4f * f)
            canvas.drawPoints(buckets[level], 0, counts[level] * 2, paint)
        }
        if (running) postInvalidateDelayed(FRAME_MS)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private companion object {
        const val LEVELS = 16
        /** Every dot keeps at least this share of its brightness, so no part of the grid goes black. */
        const val FLOOR = 0.35f
        const val LEFT_EDGE = 0.35f
        /** Where the left-edge dimming ends, as a fraction of the screen's width. */
        const val FADE_TO = 0.5f
        const val MAX_ALPHA = 40f
        const val BREATH_S = 10f
        const val FRAME_MS = 50L
        const val TWO_PI = (2 * PI).toFloat()
    }
}
