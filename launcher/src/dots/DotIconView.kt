package dev.mrrosoff.braviatv.dots

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import dev.mrrosoff.braviatv.data.Weather
import kotlin.math.max

/**
 * A small icon drawn as LED dots. In a [pattern], '#' is lit, a digit is lit when it is below [level],
 * and '.' is empty unless [showGrid]. Unlit cells are smaller, dim dots.
 */
class DotIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var pattern: Array<String> = emptyArray()
        set(value) {
            if (value.contentEquals(field)) return
            field = value
            requestLayout()
            invalidate()
        }

    var level = 0
        set(value) {
            if (value == field) return
            field = value
            invalidate()
        }

    var pitch: Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)
        set(value) {
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

    /** Fill empty cells with small dim dots, so the icon sits on a lit-panel grid. */
    var showGrid = false
        set(value) {
            field = value
            invalidate()
        }

    var colors: ColorStateList? = null
        set(value) {
            field = value
            refreshDrawableState()
        }

    private val lit = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unlit = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        colors?.let { color = it.getColorForState(drawableState, color) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cols = pattern.maxOfOrNull { it.length } ?: 0
        val w = (cols * pitch + paddingLeft + paddingRight).toInt()
        val h = (pattern.size * pitch + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val radius = pitch * 0.36f
        lit.color = color
        unlit.color = color
        unlit.alpha = (Color.alpha(color) * 0.22f).toInt()
        val cols = pattern.maxOfOrNull { it.length } ?: return
        val left = paddingLeft + max(0f, (width - paddingLeft - paddingRight - cols * pitch) / 2f)
        val top = paddingTop + max(0f, (height - paddingTop - paddingBottom - pattern.size * pitch) / 2f)
        pattern.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val on = when {
                    ch == '#' -> true
                    ch.isDigit() -> ch.digitToInt() < level
                    showGrid -> false
                    else -> return@forEachIndexed
                }
                canvas.drawCircle(left + (c + 0.5f) * pitch, top + (r + 0.5f) * pitch, if (on) radius else radius * 0.7f, if (on) lit else unlit)
            }
        }
    }

    companion object Icons {
        // Weather icons: sun, cloud, rain, snow and storm are Seeker TV's (TWBankston, MIT); moon and fog match them.
        val SUN = arrayOf(
            "....#....",
            ".#.....#.",
            "...###...",
            "..#####..",
            "#.#####.#",
            "..#####..",
            "...###...",
            ".#.....#.",
            "....#....",
        )
        val MOON = arrayOf(
            "..####...",
            ".###.....",
            "###......",
            "###......",
            "###......",
            "###......",
            "####....#",
            ".#######.",
            "..#####..",
        )
        val CLOUD = arrayOf(
            ".........",
            "....###..",
            "...#####.",
            ".########",
            "#########",
            "#########",
            ".#######.",
            ".........",
            ".........",
        )
        val RAIN = arrayOf(
            "....###..",
            "...#####.",
            ".########",
            "#########",
            ".#######.",
            ".........",
            ".#..#..#.",
            "..#..#..#",
            ".#..#..#.",
        )
        val SNOW = arrayOf(
            "....###..",
            "...#####.",
            ".########",
            "#########",
            ".#######.",
            ".........",
            ".#..#..#.",
            ".........",
            "..#..#..#",
        )
        val FOG = arrayOf(
            ".........",
            "#########",
            ".........",
            ".#######.",
            ".........",
            "#########",
            ".........",
            "..#####..",
            ".........",
        )
        val STORM = arrayOf(
            "....###..",
            "...#####.",
            ".########",
            "#########",
            ".#######.",
            "....##...",
            "...##....",
            "..####...",
            "....#....",
        )

        /** WMO weather codes, as Open-Meteo reports them. */
        fun weather(code: Int, isDay: Boolean): Array<String> = when (code) {
            0, 1 -> if (isDay) SUN else MOON
            2, 3 -> CLOUD
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUD
        }
    }
}
