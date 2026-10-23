package dev.mrrosoff.tv.dot.home

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.data.Inputs
import dev.mrrosoff.tv.dot.dots.DotTextView

/** The glass-panel rows shared by the Inputs menu, quick settings and Home screen settings. */
object MenuRows {

    fun make(activity: Activity, label: String, detail: String? = null, onClick: (View) -> Unit): LinearLayout {
        val res = activity.resources
        val pad = dp(activity, 14f)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.input_row)
            setPadding(pad, 0, pad, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 46f))
        }
        val colors = res.getColorStateList(R.color.pill_text, activity.theme)
        row.addView(DotTextView(activity).apply {
            text = label
            pitch = dp(activity, 2f).toFloat()
            this.colors = colors
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isDuplicateParentStateEnabled = true
        })
        row.addView(DotTextView(activity).apply {
            tag = DETAIL
            text = detail.orEmpty()
            pitch = dp(activity, 1.6f).toFloat()
            this.colors = colors
            alpha = 0.6f
            isDuplicateParentStateEnabled = true
        })
        row.setOnClickListener(onClick)
        return row
    }

    fun setDetail(row: View, detail: String) {
        row.findViewWithTag<DotTextView>(DETAIL)?.text = detail
    }

    /** Up and Down move exactly one row and stop at the ends; Left and Right go nowhere. */
    fun chain(rows: List<View>) {
        rows.forEachIndexed { i, v ->
            v.setOnKeyListener { _, keyCode, event ->
                val dpad = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener dpad
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { if (i > 0) rows[i - 1].requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { if (i < rows.size - 1) rows[i + 1].requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            }
        }
    }

    fun dp(activity: Activity, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics).toInt()

    private const val DETAIL = "detail"
}
