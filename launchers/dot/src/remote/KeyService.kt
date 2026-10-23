package dev.mrrosoff.tv.dot.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.mrrosoff.tv.dot.data.Inputs
import dev.mrrosoff.tv.dot.data.Prefs
import dev.mrrosoff.tv.dot.data.TvConfig
import dev.mrrosoff.tv.dot.home.MainActivity

/**
 * While Home is on screen, Input opens Home's Inputs menu and Settings opens the TV's full Settings.
 *
 * Filtering keys stops held buttons from auto-repeating (holding Volume+ moves one step), so the
 * filter is only requested while Home is visible; in apps and on HDMI the remote is untouched. Some
 * remotes send their own scancodes for these buttons (set in the TV's tv.properties), so scancodes are
 * matched as well as keycodes.
 */
class KeyService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private var inputScancode = 0
    private var settingsScancode = 0

    override fun onServiceConnected() {
        prefs = Prefs(this)
        inputScancode = TvConfig.inputScancode(this)
        settingsScancode = TvConfig.settingsScancode(this)
        instance = this
        applyFilter()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun applyFilter() {
        val info = serviceInfo ?: return
        val on = homeVisible && (prefs.inputKey || prefs.settingsKey)
        val flags = if (on) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        if (flags != info.flags) {
            info.flags = flags
            serviceInfo = info
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::prefs.isInitialized || !homeVisible) return false
        val target = when {
            prefs.inputKey && (matches(event, inputScancode) || event.keyCode == KeyEvent.KEYCODE_TV_INPUT) ->
                Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_INPUTS)
            prefs.settingsKey && (matches(event, settingsScancode) || event.keyCode == KeyEvent.KEYCODE_SETTINGS) ->
                Intent(Settings.ACTION_SETTINGS)
            else -> return false
        }
        // Swallow the release and repeats too, or the stock handler would also act on this press.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }

    private fun matches(event: KeyEvent, scancode: Int) = scancode != 0 && event.scanCode == scancode

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private var instance: KeyService? = null
        private var homeVisible = false

        /** Called by Home as it comes and goes, so the filter only runs while Home is on screen. */
        fun setHomeVisible(visible: Boolean) {
            homeVisible = visible
            instance?.applyFilter()
        }

        private fun component(context: Context) = ComponentName(context, KeyService::class.java).flattenToString()

        /** A force stop or reinstall turns accessibility services off; Home turns this one back on. */
        fun ensureEnabled(context: Context) {
            val prefs = Prefs(context)
            if (!prefs.inputKey && !prefs.settingsKey) return
            val cr = context.contentResolver
            val me = component(context)
            val enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (enabled.split(':').any { it.equals(me, ignoreCase = true) }) return
            try {
                val list = if (enabled.isEmpty()) me else "$enabled:$me"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (e: SecurityException) {
                // No WRITE_SECURE_SETTINGS: install.sh enables the service instead.
            }
        }
    }
}
