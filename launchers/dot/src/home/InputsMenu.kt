package dev.mrrosoff.tv.dot.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.data.Inputs
import dev.mrrosoff.tv.dot.data.TvConfig

/** The HDMI / AV / Live TV menu the remote's Input button opens on Home. */
class InputsMenu(
    private val activity: Activity,
    private val panel: ScrollView,
    private val list: LinearLayout,
    /** Where focus goes when the menu closes. */
    private val home: View,
) {
    val isOpen get() = panel.visibility == View.VISIBLE

    fun toggle() = if (isOpen) close() else open()

    fun open() {
        list.removeAllViews()
        val inputs = Inputs.load(activity, TvConfig.liveTvPackage(activity))
        if (inputs.isEmpty()) {
            Toast.makeText(activity, R.string.no_inputs, Toast.LENGTH_SHORT).show()
            return
        }
        val rows = inputs.map { input ->
            MenuRows.make(activity, input.label, if (input.connected) activity.getString(R.string.connected) else null) {
                close()
                try {
                    activity.startActivity(input.intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(activity, input.label, Toast.LENGTH_SHORT).show()
                }
            }.also { list.addView(it) }
        }
        MenuRows.chain(rows)
        panel.alpha = 0f
        panel.translationY = activity.dp(8f)
        panel.visibility = View.VISIBLE
        panel.animate().alpha(1f).translationY(0f).setDuration(160).start()
        rows.first().requestFocus()
    }

    fun close() {
        if (!isOpen) return
        // Move focus off the menu before removing it, or Android hands focus to whatever is nearest.
        home.requestFocus()
        panel.visibility = View.GONE
        list.removeAllViews()
    }
}
