package dev.mrrosoff.tv.dot.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.data.App
import dev.mrrosoff.tv.dot.data.Apps
import dev.mrrosoff.tv.dot.data.BrandColors
import dev.mrrosoff.tv.dot.data.Occasions
import dev.mrrosoff.tv.dot.data.Prefs
import dev.mrrosoff.tv.dot.data.TvConfig
import dev.mrrosoff.tv.dot.dots.DotFieldView
import dev.mrrosoff.tv.dot.homeassistant.Lights
import dev.mrrosoff.tv.dot.remote.KeyService

/** Home: the app wheel on the left, [InfoPanel] on the right. Hold OK on the wheel for Home screen settings. */
class MainActivity : Activity(), WheelView.Listener {

    private lateinit var prefs: Prefs
    private lateinit var wheel: WheelView
    private lateinit var field: DotFieldView
    private lateinit var info: InfoPanel
    private lateinit var inputs: InputsMenu
    private lateinit var ambience: Ambience
    private lateinit var idle: IdleMode
    private val handler = Handler(Looper.getMainLooper())
    /** The key that woke Home from idle, swallowed until it's released. */
    private var wakeKey = -1

    private var apps: List<App> = emptyList()
    private var brandColors = IntArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        wheel = findViewById(R.id.wheel)
        field = findViewById(R.id.dot_field)
        info = InfoPanel(this, prefs, handler)
        inputs = InputsMenu(this, findViewById(R.id.inputs_panel), findViewById(R.id.inputs_list), wheel)
        val glow = findViewById<View>(R.id.glow)
        ambience = Ambience(this, prefs, handler, glow, listOf(wheel, glow, findViewById(R.id.info)))
        idle = IdleMode(
            this, handler, field, wheel, glow, findViewById(R.id.clock),
            listOf(R.id.occasion, R.id.date, R.id.weather, R.id.laundry).map { findViewById<View>(it) },
        ) { wheel.hasFocus() && !inputs.isOpen }
        previewFrom(intent)

        wheel.listener = this
        loadApps(select = lastUsedIndex())
        wheel.requestFocus()

        registerSystemReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        prefs.register(prefsListener)
        if (intent?.action == ACTION_SHOW_INPUTS) wheel.post { inputs.open() }
    }

    override fun onStart() {
        super.onStart()
        info.start()
        ambience.startDrift()
    }

    override fun onResume() {
        super.onResume()
        KeyService.ensureEnabled(this)
        KeyService.setHomeVisible(true)
        Lights.get(this).prepare()
        info.setVisible(true)
        field.running = true
        idle.touch()
        // Back from an app: the wheel starts on the app last opened.
        if (!inputs.isOpen) {
            wheel.select(lastUsedIndex(), animate = false)
            wheel.requestFocus()
        }
    }

    override fun onPause() {
        idle.stop()
        Lights.get(this).restore()
        KeyService.setHomeVisible(false)
        info.setVisible(false)
        field.running = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        info.stop()
        ambience.stopDrift()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        prefs.unregister(prefsListener)
        handler.removeCallbacksAndMessages(null)
        info.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        previewFrom(intent)
        if (intent.action == ACTION_SHOW_INPUTS) {
            inputs.toggle()
            return
        }
        inputs.close()
        wheel.select(lastUsedIndex(), animate = true)
        wheel.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in PASS_THROUGH) return super.dispatchKeyEvent(event)
        if (event.keyCode == wakeKey) {
            if (event.action == KeyEvent.ACTION_UP) wakeKey = -1
            return true
        }
        if (idle.idle && event.action == KeyEvent.ACTION_DOWN) {
            wakeKey = event.keyCode
            idle.wake()
            return true
        }
        idle.touch()
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (inputs.isOpen) inputs.close() else wheel.requestFocus()
            return true // the home screen never closes
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSelected(app: App, index: Int, byUser: Boolean) {
        val color = brandColors.getOrElse(index) { 0xFFFFFFFF.toInt() }
        ambience.glowTo(color)
        if (byUser) Lights.get(this).preview(color)
    }

    override fun onOpen(app: App) {
        prefs.lastOpened = app.packageName
        try {
            startActivity(app.launchIntent(this))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, app.label, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, app.label, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMenu() {
        startActivity(Intent(this, HomeSettingsActivity::class.java))
    }

    private fun loadApps(select: Int? = null) {
        val keep = apps.getOrNull(wheel.selected)?.packageName
        val hidden = prefs.hiddenApps(TvConfig.hiddenApps(this)) + TvConfig.liveTvPackage(this)
        apps = Apps.load(this, hidden)
        brandColors = IntArray(apps.size) { i -> BrandColors.of(apps[i].packageName) { apps[i].banner(this) } }
        val index = select ?: keep?.let { pkg -> apps.indexOfFirst { it.packageName == pkg } }?.takeIf { it >= 0 } ?: lastUsedIndex()
        wheel.setApps(apps, brandColors, index)
    }

    private fun lastUsedIndex(): Int {
        val last = prefs.lastOpened ?: return 0
        return apps.indexOfFirst { it.packageName == last }.coerceAtLeast(0)
    }

    /** `am start -n dev.mrrosoff.tv.dot/.home.MainActivity --es preview_theme halloween` ("" for the calendar). */
    private fun previewFrom(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_PREVIEW_THEME) ?: return
        if (id in Occasions.previewIds) prefs.themePreview = id
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "hidden_apps" -> loadApps()
            "pixel_shift" -> ambience.startDrift()
            "theme_preview" -> info.applyOccasion()
        }
    }

    companion object {
        /** Sent by KeyService for the remote's Input button. */
        const val ACTION_SHOW_INPUTS = "dev.mrrosoff.tv.dot.SHOW_INPUTS"
        private const val EXTRA_PREVIEW_THEME = "preview_theme"
        /** Volume and mute work as usual, and don't wake Home. */
        private val PASS_THROUGH = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
    }
}
