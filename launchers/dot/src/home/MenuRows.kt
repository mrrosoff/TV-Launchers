package dev.mrrosoff.tv.dot.home

import android.app.Activity
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.data.Inputs
import dev.mrrosoff.tv.dot.dots.DotTextView

/** The glass-panel rows shared by the Inputs menu and Home screen settings. */
object MenuRows {

    fun make(activity: Activity, label: String, detail: String? = null, onClick: (View) -> Unit): LinearLayout {
        val res = activity.resources
        val pad = activity.dp(14f).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.input_row)
            setPadding(pad, 0, pad, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(46f).toInt())
        }
        val colors = res.getColorStateList(R.color.focus_text, activity.theme)
        row.addView(DotTextView(activity).apply {
            text = label
            pitch = activity.dp(2f)
            this.colors = colors
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isDuplicateParentStateEnabled = true
        })
        row.addView(DotTextView(activity).apply {
            tag = DETAIL
            text = detail.orEmpty()
            pitch = activity.dp(1.6f)
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

    private const val DETAIL = "detail"
}
