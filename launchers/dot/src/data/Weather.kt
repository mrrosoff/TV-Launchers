package dev.mrrosoff.tv.dot.data

import android.content.Context
import dev.mrrosoff.tv.dot.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Current weather from Open-Meteo (no key needed) for the TV's location, which is
 * looked up from its public IP once a day, falling back to the TV's tv.properties city. The last answer is
 * cached in [Prefs], so Home shows it at once and asks again at most every [MAX_AGE_MS].
 */
object Weather {

    class Now(val temperature: Int, val code: Int, val isDay: Boolean)

    private const val MAX_AGE_MS = 30 * 60 * 1000L
    private const val LOCATION_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 8000

    fun cached(prefs: Prefs): Now? = prefs.weatherJson?.let {
        try {
            parse(JSONObject(it))
        } catch (e: org.json.JSONException) {
            null
        }
    }

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
        val body = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                "&current=temperature_2m,weather_code,is_day&temperature_unit=$unit",
        ) ?: return null
        val now = parse(body) ?: return null
        prefs.setWeather(body.toString())
        return now
    }

    /** A Lucide weather icon for a WMO code. */
    fun icon(code: Int, isDay: Boolean = true): Int = when (code) {
        0 -> if (isDay) R.drawable.ic_sun else R.drawable.ic_moon
        1, 2 -> if (isDay) R.drawable.ic_cloud_sun else R.drawable.ic_cloud_moon
        3 -> R.drawable.ic_cloud
        45, 48 -> R.drawable.ic_cloud_fog
        in 51..57 -> R.drawable.ic_cloud_drizzle
        in 61..67, in 80..82 -> R.drawable.ic_cloud_rain
        in 71..77, 85, 86 -> R.drawable.ic_cloud_snow
        in 95..99 -> R.drawable.ic_cloud_lightning
        else -> R.drawable.ic_cloud
    }

    /** WMO weather codes, as Open-Meteo reports them, in a few words. */
    fun describe(context: Context, code: Int): String = context.getString(
        when (code) {
            0 -> R.string.weather_clear
            1 -> R.string.weather_mostly_clear
            2 -> R.string.weather_partly_cloudy
            3 -> R.string.weather_overcast
            45, 48 -> R.string.weather_fog
            in 51..57 -> R.string.weather_drizzle
            in 61..67 -> R.string.weather_rain
            in 71..77 -> R.string.weather_snow
            in 80..82 -> R.string.weather_showers
            85, 86 -> R.string.weather_snow_showers
            in 95..99 -> R.string.weather_thunderstorms
            else -> R.string.weather_cloudy
        },
    )

    private fun parse(body: JSONObject): Now? = try {
        val current = body.getJSONObject("current")
        Now(
            current.getDouble("temperature_2m").roundToInt(),
            current.getInt("weather_code"),
            current.optInt("is_day", 1) == 1,
        )
    } catch (e: org.json.JSONException) {
        null
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
