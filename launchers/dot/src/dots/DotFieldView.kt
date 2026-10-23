package dev.mrrosoff.tv.dot.dots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The LED panel behind Home: a grid of faint dots that breathe and ripple. A wave rolls slowly across the
 * panel, rings spread from a circling centre, and the whole field swells on an eight-second breath; each
 * dot's brightness and size follow the result. Kept subtle: it should be noticed, not watched.
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
        for (i in 0 until LEVELS) buckets[i] = FloatArray(n * 2)
    }

    override fun onDraw(canvas: Canvas) {
        if (xs.isEmpty()) return
        val t = (System.nanoTime() - start) / 1e9f
        val w = width.toFloat()
        val h = height.toFloat()
        val breath = 0.75f + 0.25f * (0.5f + 0.5f * sin(t * TWO_PI / BREATH_S))
        // The ripple's centre drifts around the middle of the screen once a minute.
        val cx = w * (0.5f + 0.25f * cos(t * TWO_PI / 60f))
        val cy = h * (0.5f + 0.2f * sin(t * TWO_PI / 60f))
        counts.fill(0)
        for (i in xs.indices) {
            val nx = xs[i] / w
            val ny = ys[i] / h
            val wave = 0.5f + 0.5f * sin(TWO_PI * (nx * 1.1f + ny * 0.7f) - t * 0.3f)
            val ring = 0.5f + 0.5f * sin(hypot(xs[i] - cx, ys[i] - cy) / pitch * 0.45f - t * 0.6f)
            val b = (wave * 0.55f + ring * 0.45f) * breath
            val level = (b * LEVELS).toInt().coerceIn(0, LEVELS - 1)
            val k = counts[level] * 2
            buckets[level][k] = xs[i]
            buckets[level][k + 1] = ys[i]
            counts[level]++
        }
        for (level in 0 until LEVELS) {
            if (counts[level] == 0) continue
            val f = (level + 1f) / LEVELS
            paint.alpha = (MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * f * f).toInt()
            paint.strokeWidth = baseDiameter * (0.85f + 0.3f * f)
            canvas.drawPoints(buckets[level], 0, counts[level] * 2, paint)
        }
        if (running) postInvalidateDelayed(FRAME_MS)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private companion object {
        const val LEVELS = 8
        const val MIN_ALPHA = 4f
        const val MAX_ALPHA = 30f
        const val BREATH_S = 8f
        const val FRAME_MS = 50L
        const val TWO_PI = (2 * PI).toFloat()
    }
}
