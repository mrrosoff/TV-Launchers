package dev.mrrosoff.tv.dot.home

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import dev.mrrosoff.tv.dot.data.App
import dev.mrrosoff.tv.dot.dots.DotFont
import dev.mrrosoff.tv.dot.dots.DotTextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * App names in LED dots on an endless arc: the selected one largest and furthest left, neighbours curving right and
 * fading out two rows away, and the list wrapping round from the last app to the first. The geometry
 * follows the Wheel mode of Seeker TV by TWBankston (MIT, https://github.com/TWBankston/Seeker-TV-Launcher),
 * reimplemented here with views.
 */
class WheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onSelected(app: App, index: Int)
        fun onOpen(app: App)
        /** OK held on an app. */
        fun onMenu()
        /** Left: start voice search. */
        fun onVoice()
    }

    var listener: Listener? = null
    var selected = 0
        private set

    private var apps: List<App> = emptyList()
    private var colors: IntArray = IntArray(0)
    private val rows = ArrayList<DotTextView>()
    /** Each row's place around the wheel at the last layout, to spot rows that wrap round the back. */
    private var offsets = IntArray(0)
    private val ease = PathInterpolator(.2f, .7f, .2f, 1f)
    private var longPressed = false

    private val spacing = dp(66f)
    private val maxPitch = dp(7f)
    private val arc = dp(10f)
    private val textStart = dp(12f)

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        clipChildren = false
        clipToPadding = false
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
        offsets = IntArray(list.size) { Int.MAX_VALUE }
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

    /** The shortest way round the wheel from the selected app to app [i]: negative above, positive below. */
    private fun offset(i: Int): Int {
        val n = apps.size
        var off = (i - selected) % n
        if (off > n / 2) off -= n
        if (off < -(n - 1) / 2) off += n
        return off
    }

    private fun layoutRows(animate: Boolean, fast: Boolean = false) {
        if (height == 0 || apps.isEmpty()) return
        val centre = height * 0.5f
        val duration = if (fast) 140L else 320L
        val room = width - textStart - dp(8f)
        rows.forEachIndexed { i, tv ->
            val off = offset(i)
            val a = abs(off).toFloat()
            val y = centre + off * spacing - spacing / 2f
            val x = textStart + a * a * arc
            val scale = max(0.42f, 1f - a * 0.17f)
            val alpha = if (a > 2) 0f else 1f - a * 0.32f
            tv.pivotY = spacing / 2f
            // Long names get smaller dots rather than being cut.
            tv.pitch = min(maxPitch, room / max(1, tv.text.length * DotFont.ADVANCE - 1))
            tv.color = if (off == 0) colors.getOrElse(i) { WHITE } else WHITE
            tv.animate().cancel()
            // A row that wrapped round the back of the wheel jumps straight to its new place while hidden.
            val wrapped = abs(off - offsets[i]) > 1
            offsets[i] = off
            if (animate && !wrapped && (a <= 3 || tv.alpha > 0f)) {
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
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (apps.isEmpty()) return super.onKeyDown(keyCode, event)
        val held = event.repeatCount > 0
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                step(-1, held)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                step(1, held)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!held) listener?.onVoice()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> return true
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) {
                    longPressed = false
                    event.startTracking()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode)) {
            longPressed = true
            listener?.onMenu()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode)) {
            // Open on key up, so the press that brought Home forward doesn't also launch an app.
            if (event.isTracking && !event.isCanceled && !longPressed) apps.getOrNull(selected)?.let { listener?.onOpen(it) }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isOk(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun step(delta: Int, fast: Boolean) {
        selected = (selected + delta + apps.size) % apps.size
        layoutRows(animate = true, fast = fast)
        listener?.onSelected(apps[selected], selected)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animate().alpha(if (gainFocus) 1f else 0.55f).setDuration(160).start()
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
