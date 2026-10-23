package dev.mrrosoff.braviatv.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import dev.mrrosoff.braviatv.data.App
import dev.mrrosoff.braviatv.dots.DotFont
import dev.mrrosoff.braviatv.dots.DotTextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * App names in LED dots on an arc: the selected one largest and furthest left, neighbours curving right
 * and fading out two rows away. The geometry follows the Wheel mode of Seeker TV by TWBankston (MIT,
 * https://github.com/TWBankston/Seeker-TV-Launcher), reimplemented here with views.
 */
class WheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onSelected(app: App, index: Int)
        fun onOpen(app: App)
        fun onRight()
    }

    var listener: Listener? = null
    var selected = 0
        private set

    private var apps: List<App> = emptyList()
    private var colors: IntArray = IntArray(0)
    private val rows = ArrayList<DotTextView>()
    private val ease = PathInterpolator(.2f, .7f, .2f, 1f)
    private val argb = ArgbEvaluator()

    private val spacing = dp(66f)
    private val maxPitch = dp(7f)
    private val arc = dp(9f)
    private val cursorRadius = dp(5f)
    private val haloRadius = cursorRadius * 2.6f
    /** The halo's full width plus a margin, so it is never cut off at the view's edge. */
    private val cursorX = haloRadius + dp(3f)
    private val textStart = cursorX + haloRadius + dp(10f)

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cursorColor = 0xFFFFFFFF.toInt()
    private var cursorAnim: ValueAnimator? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        defaultFocusHighlightEnabled = false
    }

    fun setApps(list: List<App>, appColors: IntArray, select: Int) {
        apps = list
        colors = appColors
        removeAllViews()
        rows.clear()
        for (app in list) {
            val tv = DotTextView(context).apply {
                text = app.label
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                pivotX = 0f
                alpha = 0f
            }
            addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, spacing.toInt()))
            rows += tv
        }
        selected = select.coerceIn(0, max(0, list.size - 1))
        if (width > 0) layoutRows(animate = false) else post { layoutRows(animate = false) }
        apps.getOrNull(selected)?.let { listener?.onSelected(it, selected) }
    }

    fun select(index: Int, animate: Boolean) {
        if (apps.isEmpty()) return
        selected = index.coerceIn(0, apps.size - 1)
        layoutRows(animate)
        listener?.onSelected(apps[selected], selected)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutRows(animate = false)
    }

    private fun layoutRows(animate: Boolean, fast: Boolean = false) {
        if (height == 0) return
        val centre = height * 0.5f
        val duration = if (fast) 140L else 300L
        val room = width - textStart - dp(8f)
        rows.forEachIndexed { i, tv ->
            val off = i - selected
            val a = abs(off).toFloat()
            val y = centre + off * spacing - spacing / 2f
            val x = textStart + a * a * arc
            val scale = max(0.42f, 1f - a * 0.16f)
            val alpha = if (a > 2) 0f else 1f - a * 0.3f
            tv.pivotY = spacing / 2f
            // Long names get smaller dots rather than being cut.
            tv.pitch = min(maxPitch, room / max(1, tv.text.length * DotFont.ADVANCE - 1))
            tv.color = if (off == 0) colors.getOrElse(i) { 0xFFFFFFFF.toInt() } else 0xFFFFFFFF.toInt()
            tv.showUnlit = off == 0
            tv.animate().cancel()
            if (animate && (a <= 3 || tv.alpha > 0f)) {
                tv.animate().translationX(x).translationY(y).scaleX(scale).scaleY(scale).alpha(alpha)
                    .setInterpolator(ease).setDuration(duration).start()
            } else {
                tv.translationX = x
                tv.translationY = y
                tv.scaleX = scale
                tv.scaleY = scale
                tv.alpha = alpha
            }
        }
        val target = colors.getOrElse(selected) { 0xFFFFFFFF.toInt() }
        cursorAnim?.cancel()
        if (animate) {
            val from = cursorColor
            cursorAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                addUpdateListener { cursorColor = argb.evaluate(it.animatedFraction, from, target) as Int; invalidate() }
                start()
            }
        } else {
            cursorColor = target
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (apps.isEmpty()) return
        val cy = height * 0.5f
        cursorPaint.color = cursorColor
        cursorPaint.alpha = 60
        canvas.drawCircle(cursorX, cy, haloRadius, cursorPaint)
        cursorPaint.alpha = 255
        canvas.drawCircle(cursorX, cy, cursorRadius, cursorPaint)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (apps.isEmpty()) return super.onKeyDown(keyCode, event)
        val held = event.repeatCount > 0
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selected > 0) step(-1, held)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selected < apps.size - 1) step(1, held)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                listener?.onRight()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> return true
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Open on key up, so the press that brought Home forward doesn't also launch an app.
                if (!event.isCanceled) apps.getOrNull(selected)?.let { listener?.onOpen(it) }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun step(delta: Int, fast: Boolean) {
        selected += delta
        layoutRows(animate = true, fast = fast)
        listener?.onSelected(apps[selected], selected)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animate().alpha(if (gainFocus) 1f else 0.55f).setDuration(160).start()
    }
}
