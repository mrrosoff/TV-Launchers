package dev.mrrosoff.tv.dot.dots

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import dev.mrrosoff.tv.dot.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Text drawn as an LED dot matrix in [DotFont], in [color]; only lit dots are drawn.
 *
 * With [morph], a new text replaces the old dot by dot: dots that stay lit stay, the rest go out and come
 * on in a scattered order, each new one with a small pop. Both texts line up on the right, as the clock does.
 */
class DotTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var text: String = ""
        set(value) {
            val v = DotFont.normalize(value)
            if (v == field) return
            if (morph && field.isNotEmpty() && isLaidOut) {
                previous = field
                morphStart = System.nanoTime()
            }
            field = v
            contentDescription = value
            requestLayout()
            invalidate()
        }

    /** Centre-to-centre distance between dots, in pixels. */
    var pitch: Float = dp(4f)
        set(value) {
            if (value == field) return
            field = value
            requestLayout()
            invalidate()
        }

    var color: Int = Color.WHITE
        set(value) {
            if (value == field) return
            field = value
            invalidate()
        }

    /** When set, [color] follows the view's state, e.g. dark dots on a focused white row. */
    var colors: ColorStateList? = null
        set(value) {
            field = value
            refreshDrawableState()
        }

    var morph = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var previous: String? = null
    private var morphStart = 0L
    private val oldCells = HashSet<Int>()
    private val newCells = HashSet<Int>()

    init {
        context.obtainStyledAttributes(attrs, R.styleable.DotTextView).apply {
            text = getString(R.styleable.DotTextView_dotText).orEmpty()
            pitch = getDimension(R.styleable.DotTextView_dotPitch, pitch)
            colors = getColorStateList(R.styleable.DotTextView_dotColor)
            color = colors?.defaultColor ?: color
            recycle()
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        colors?.let { color = it.getColorForState(drawableState, color) }
    }

    /** Width the text needs at [p]. */
    fun widthAt(p: Float): Float = widthOf(text, p)

    private fun widthOf(s: String, p: Float) = if (s.isEmpty()) 0f else (s.length * DotFont.ADVANCE - 1) * p

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = max(widthAt(pitch), previous?.let { widthOf(it, pitch) } ?: 0f)
        val w = (content + paddingLeft + paddingRight).toInt()
        val h = (DotFont.ROWS * pitch + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return
        val now = System.nanoTime()
        val old = previous
        val progress = if (old == null) 1f else (now - morphStart) / 1e6f / MORPH_MS
        if (old != null && progress >= 1f) {
            previous = null
            requestLayout()
        }
        val radius = pitch * 0.36f
        // Centre vertically; text wider than the view is cut, never squeezed.
        val top = paddingTop + max(0f, (height - paddingTop - paddingBottom - DotFont.ROWS * pitch) / 2f)
        val right = width - paddingRight.toFloat()

        if (old != null && progress < 1f) {
            val oldLeft = right - widthOf(old, pitch)
            val newLeft = right - widthAt(pitch)
            oldCells.clear()
            newCells.clear()
            forEachDot(old, oldLeft, top, right) { _, cell, _, _ -> oldCells += cell }
            forEachDot(text, newLeft, top, right) { _, cell, _, _ -> newCells += cell }
            // Going out: old dots not in the new text.
            forEachDot(old, oldLeft, top, right) { _, cell, cx, cy ->
                if (cell in newCells) return@forEachDot
                val a = 1f - ease((progress - stagger(cell) * 0.5f) / 0.3f)
                if (a > 0f) dot(canvas, cx, cy, radius, color, a)
            }
            // Staying or coming on.
            forEachDot(text, newLeft, top, right) { _, cell, cx, cy ->
                if (cell in oldCells) {
                    dot(canvas, cx, cy, radius, color, 1f)
                } else {
                    val p = ease((progress - 0.2f - stagger(cell) * 0.5f) / 0.3f)
                    if (p > 0f) dot(canvas, cx, cy, radius * (1f + 0.4f * (1f - p) * p * 4f), color, p)
                }
            }
            postInvalidateOnAnimation()
            return
        }

        val left = if (morph) right - widthAt(pitch) else paddingLeft.toFloat()
        forEachDot(text, left, top, right) { _, _, cx, cy -> dot(canvas, cx, cy, radius, color, 1f) }
    }

    /** Calls [draw] for each lit dot of [s] starting at [left]: x within the text, a cell id, and centre. */
    private inline fun forEachDot(s: String, left: Float, top: Float, right: Float, draw: (Float, Int, Float, Float) -> Unit) {
        var x0 = left
        for (ch in s) {
            if (x0 > right) break
            if (ch != ' ') {
                val rows = DotFont.glyph(ch)
                for (r in 0 until DotFont.ROWS) {
                    val bits = rows[r]
                    for (c in 0 until DotFont.COLS) {
                        if ((bits shr (DotFont.COLS - 1 - c)) and 1 == 1) {
                            val cx = x0 + (c + 0.5f) * pitch
                            val column = ((cx - right) / pitch - 0.5f).roundToInt()
                            draw(cx - left, column * 8 + r, cx, top + (r + 0.5f) * pitch)
                        }
                    }
                }
            }
            x0 += DotFont.ADVANCE * pitch
        }
    }

    private fun dot(canvas: Canvas, cx: Float, cy: Float, radius: Float, c: Int, alpha: Float) {
        paint.color = c
        paint.alpha = (Color.alpha(c) * alpha.coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /** A fixed pseudo-random 0..1 per dot, so the scatter is the same each time. */
    private fun stagger(cell: Int): Float {
        var h = cell * -0x61c88647
        h = h xor (h ushr 15)
        return (h and 0xFFFF) / 65535f
    }

    private fun ease(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private companion object {
        const val MORPH_MS = 900f
    }
}
