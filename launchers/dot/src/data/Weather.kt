package dev.mrrosoff.tv.dot.data

import android.content.Context
import dev.mrrosoff.tv.dot.R
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Current weather from Open-Meteo (no key needed) for the TV's location, which is looked up from its
 * public IP once a day, falling back to config.xml's city. Results are cached in [Prefs], so Home
 * shows the last reading at once and asks again at most every [MAX_AGE_MS].
 */
object Weather {

    class Now(val temperature: Int, val code: Int, val isDay: Boolean, val city: String)

    private const val MAX_AGE_MS = 30 * 60 * 1000L
    private const val LOCATION_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 8000

    fun cached(prefs: Prefs): Now? = prefs.weather

    fun isStale(prefs: Prefs) = System.currentTimeMillis() - prefs.weatherAt > MAX_AGE_MS

    /** Blocking: call off the main thread. Returns null when the network is unavailable. */
    fun fetch(context: Context, prefs: Prefs): Now? {
        val res = context.resources
        val loc = location(prefs) ?: Prefs.Location(
            res.getString(R.string.weather_fallback_latitude).toDouble(),
            res.getString(R.string.weather_fallback_longitude).toDouble(),
            res.getString(R.string.weather_fallback_city),
        )
        val unit = res.getString(R.string.weather_units)
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                "&current=temperature_2m,weather_code,is_day&temperature_unit=$unit",
        ) ?: return null
        return try {
            val current = json.getJSONObject("current")
            Now(
                current.getDouble("temperature_2m").roundToInt(),
                current.getInt("weather_code"),
                current.optInt("is_day", 1) == 1,
                loc.city,
            ).also { prefs.setWeather(it) }
        } catch (e: org.json.JSONException) {
            null
        }
    }

    private fun location(prefs: Prefs): Prefs.Location? {
        prefs.location?.takeIf { System.currentTimeMillis() - prefs.locationAt < LOCATION_MAX_AGE_MS }?.let { return it }
        val found = get("https://ipwho.is/")?.takeIf { it.optBoolean("success") }?.let {
            Prefs.Location(it.getDouble("latitude"), it.getDouble("longitude"), it.optString("city"))
        } ?: get("https://ipapi.co/json/")?.takeIf { it.has("latitude") }?.let {
            Prefs.Location(it.getDouble("latitude"), it.getDouble("longitude"), it.optString("city"))
        }
        found?.let { prefs.setLocation(it) }
        return found ?: prefs.location
    }

    private fun get(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: java.io.IOException) {
            null
        } catch (e: org.json.JSONException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
