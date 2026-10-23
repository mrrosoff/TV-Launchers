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
import kotlin.math.min

/** Text drawn as an LED dot matrix in [DotFont], in [color]; only lit dots are drawn. */
class DotTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var text: String = ""
        set(value) {
            val v = DotFont.normalize(value)
            if (v == field) return
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

    /** When set, [color] follows the view's state, e.g. dark dots on a focused white pill. */
    var colors: ColorStateList? = null
        set(value) {
            field = value
            refreshDrawableState()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

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
    fun widthAt(p: Float): Float = if (text.isEmpty()) 0f else (text.length * DotFont.ADVANCE - 1) * p

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (widthAt(pitch) + paddingLeft + paddingRight).toInt()
        val h = (DotFont.ROWS * pitch + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return
        val radius = pitch * 0.36f
        paint.color = color
        // Centre vertically; text wider than the view is cut, never squeezed.
        val top = paddingTop + max(0f, (height - paddingTop - paddingBottom - DotFont.ROWS * pitch) / 2f)
        var x0 = paddingLeft.toFloat()
        val right = min(width - paddingRight.toFloat(), x0 + widthAt(pitch))
        for (ch in text) {
            if (x0 > right) break
            if (ch == ' ') {
                x0 += DotFont.ADVANCE * pitch
                continue
            }
            val rows = DotFont.glyph(ch)
            for (r in 0 until DotFont.ROWS) {
                val bits = rows[r]
                val cy = top + (r + 0.5f) * pitch
                for (c in 0 until DotFont.COLS) {
                    if ((bits shr (DotFont.COLS - 1 - c)) and 1 == 1) canvas.drawCircle(x0 + (c + 0.5f) * pitch, cy, radius, paint)
                }
            }
            x0 += DotFont.ADVANCE * pitch
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
