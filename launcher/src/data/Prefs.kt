package dev.mrrosoff.braviatv.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    var lastOpened: String?
        get() = p.getString("last_opened", null)
        set(v) = p.edit().putString("last_opened", v).apply()

    /** Apps left out of the wheel: config.xml's hidden_apps until the owner changes it on the TV. */
    fun hiddenApps(defaults: Set<String>): Set<String> =
        if (p.contains("hidden_apps")) p.getStringSet("hidden_apps", emptySet()).orEmpty() else defaults

    fun setHiddenApps(v: Set<String>) = p.edit().putStringSet("hidden_apps", HashSet(v)).apply()

    var pixelShift: Boolean
        get() = p.getBoolean("pixel_shift", true)
        set(v) = p.edit().putBoolean("pixel_shift", v).apply()

    var inputKey: Boolean
        get() = p.getBoolean("input_key", true)
        set(v) = p.edit().putBoolean("input_key", v).apply()

    var settingsKey: Boolean
        get() = p.getBoolean("settings_key", true)
        set(v) = p.edit().putBoolean("settings_key", v).apply()

    class Location(val latitude: Double, val longitude: Double, val city: String)

    val location: Location?
        get() = if (!p.contains("lat")) null else Location(
            p.getFloat("lat", 0f).toDouble(), p.getFloat("lon", 0f).toDouble(), p.getString("city", "").orEmpty(),
        )
    val locationAt: Long get() = p.getLong("location_at", 0L)

    fun setLocation(l: Location) = p.edit().putFloat("lat", l.latitude.toFloat()).putFloat("lon", l.longitude.toFloat())
        .putString("city", l.city).putLong("location_at", System.currentTimeMillis()).apply()

    val weather: Weather.Now?
        get() = if (!p.contains("temp")) null else Weather.Now(
            p.getInt("temp", 0), p.getInt("weather_code", 3), p.getBoolean("is_day", true), p.getString("weather_city", "").orEmpty(),
        )
    val weatherAt: Long get() = p.getLong("weather_at", 0L)

    fun setWeather(w: Weather.Now) = p.edit().putInt("temp", w.temperature).putInt("weather_code", w.code)
        .putBoolean("is_day", w.isDay).putString("weather_city", w.city).putLong("weather_at", System.currentTimeMillis()).apply()

    /** A theme to show regardless of the date ("" follows the calendar); see [Occasions.previewIds]. */
    var themePreview: String
        get() = p.getString("theme_preview", "").orEmpty()
        set(v) = p.edit().putString("theme_preview", v).apply()

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(listener)
    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(listener)
}
