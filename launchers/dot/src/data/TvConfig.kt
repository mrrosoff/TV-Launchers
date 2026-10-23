package dev.mrrosoff.tv.dot.data

import android.content.Context
import dev.mrrosoff.tv.dot.R

/** This TV's settings, built in from its tvs/<name>/tv.properties. Empty values on a generic build. */
object TvConfig {

    fun liveTvPackage(context: Context): String = context.getString(R.string.tv_live_tv_package)

    /** Hidden until shown in Home screen settings. The Live TV app is reached from Inputs instead. */
    fun hiddenApps(context: Context): Set<String> =
        context.getString(R.string.tv_hidden_apps).split(',').filter { it.isNotBlank() }.toSet()

    fun inputScancode(context: Context): Int = context.resources.getInteger(R.integer.tv_input_scancode)

    fun settingsScancode(context: Context): Int = context.resources.getInteger(R.integer.tv_settings_scancode)
}
