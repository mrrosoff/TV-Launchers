package dev.mrrosoff.tv.dot.dots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max

/** A small icon drawn as LED dots: each '#' in [pattern] is a dot in [color]. */
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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cols = pattern.maxOfOrNull { it.length } ?: 0
        val w = (cols * pitch + paddingLeft + paddingRight).toInt()
        val h = (pattern.size * pitch + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val radius = pitch * 0.36f
        paint.color = color
        val cols = pattern.maxOfOrNull { it.length } ?: return
        val left = paddingLeft + max(0f, (width - paddingLeft - paddingRight - cols * pitch) / 2f)
        val top = paddingTop + max(0f, (height - paddingTop - paddingBottom - pattern.size * pitch) / 2f)
        pattern.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                if (ch == '#') canvas.drawCircle(left + (c + 0.5f) * pitch, top + (r + 0.5f) * pitch, radius, paint)
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
