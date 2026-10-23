package dev.mrrosoff.tv.dot.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.data.Apps
import dev.mrrosoff.tv.dot.data.Occasions
import dev.mrrosoff.tv.dot.data.Prefs
import dev.mrrosoff.tv.dot.data.TvConfig
import dev.mrrosoff.tv.dot.dots.DotTextView
import dev.mrrosoff.tv.dot.remote.KeyService

/** Home screen settings on the TV: burn-in care, the remote buttons, and which apps the wheel shows. */
class HomeSettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.panel)
        prefs = Prefs(this)
        findViewById<DotTextView>(R.id.panel_title).text = getString(R.string.home_settings)
        val list = findViewById<LinearLayout>(R.id.panel_rows)
        val rows = ArrayList<View>()

        fun toggle(label: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
            rows += MenuRows.make(this, getString(label), onOff(get())) { row ->
                set(!get())
                MenuRows.setDetail(row, onOff(get()))
                KeyService.ensureEnabled(this)
            }.also { list.addView(it) }
        }
        rows += MenuRows.make(this, getString(R.string.theme), themeLabel()) { row ->
            val ids = Occasions.previewIds
            prefs.themePreview = ids[(ids.indexOf(prefs.themePreview) + 1) % ids.size]
            MenuRows.setDetail(row, themeLabel())
        }.also { list.addView(it) }
        toggle(R.string.pixel_shift, { prefs.pixelShift }, { prefs.pixelShift = it })
        toggle(R.string.input_key, { prefs.inputKey }, { prefs.inputKey = it })
        toggle(R.string.settings_key, { prefs.settingsKey }, { prefs.settingsKey = it })
        rows += MenuRows.make(this, getString(R.string.default_home)) {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                // Some builds have no home chooser.
            }
        }.also { list.addView(it) }

        list.addView(header(getString(R.string.apps_header)))
        val defaults = TvConfig.hiddenApps(this)
        val liveTv = TvConfig.liveTvPackage(this)
        for (app in Apps.load(this, setOf(liveTv))) {
            rows += MenuRows.make(this, app.label, shownLabel(app.packageName, defaults)) { row ->
                val hidden = prefs.hiddenApps(defaults).toMutableSet()
                if (!hidden.remove(app.packageName)) hidden += app.packageName
                prefs.setHiddenApps(hidden)
                MenuRows.setDetail(row, shownLabel(app.packageName, defaults))
            }.also { list.addView(it) }
        }

        MenuRows.chain(rows)
        rows.firstOrNull()?.requestFocus()
    }

    private fun themeLabel() = prefs.themePreview.ifEmpty { getString(R.string.theme_auto) }

    private fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    private fun shownLabel(pkg: String, defaults: Set<String>) =
        getString(if (pkg in prefs.hiddenApps(defaults)) R.string.hidden else R.string.shown)

    private fun header(text: String) = DotTextView(this).apply {
        this.text = text
        pitch = MenuRows.dp(this@HomeSettingsActivity, 1.6f).toFloat()
        color = getColor(R.color.text_faint)
        val pad = MenuRows.dp(this@HomeSettingsActivity, 14f)
        setPadding(pad, pad, pad, MenuRows.dp(this@HomeSettingsActivity, 8f))
    }
}
