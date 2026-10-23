package dev.mrrosoff.braviatv.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.text.format.DateFormat
import android.view.View
import dev.mrrosoff.braviatv.R
import dev.mrrosoff.braviatv.data.Occasion
import dev.mrrosoff.braviatv.data.Occasions
import dev.mrrosoff.braviatv.data.Prefs
import dev.mrrosoff.braviatv.data.Weather
import dev.mrrosoff.braviatv.dots.DotIconView
import dev.mrrosoff.braviatv.dots.DotTextView
import dev.mrrosoff.braviatv.dots.ParticleView
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors

/**
 * The right side of Home: greeting, clock, date and weather, and on themed days the emblem, the
 * corner glow and the ambient particles.
 */
class InfoPanel(
    private val activity: Activity,
    private val prefs: Prefs,
    private val handler: Handler,
) {
    private val greeting: DotTextView = activity.findViewById(R.id.greeting)
    private val clock: DotTextView = activity.findViewById(R.id.clock)
    private val date: DotTextView = activity.findViewById(R.id.date)
    private val weather: View = activity.findViewById(R.id.weather)
    private val weatherIcon: DotIconView = activity.findViewById(R.id.weather_icon)
    private val temperature: DotTextView = activity.findViewById(R.id.temperature)
    private val city: DotTextView = activity.findViewById(R.id.city)
    private val emblem: DotIconView = activity.findViewById(R.id.emblem)
    private val particles: ParticleView = activity.findViewById(R.id.particles)
    private val occasionGlow: View = activity.findViewById(R.id.occasion_glow)

    private val background = Executors.newSingleThreadExecutor()
    private var occasion: Occasion? = null
    private var occasionDay = -1
    private var visible = false

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = tick()
    }
    private val weatherRefresh = Runnable { refreshWeather() }

    init {
        weatherIcon.pitch = activity.dp(3.2f)
        emblem.pitch = activity.dp(3.6f)
        greeting.colors = null
    }

    fun start() {
        applyOccasion()
        activity.registerSystemReceiver(clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        showWeather(Weather.cached(prefs))
        refreshWeather()
    }

    fun stop() {
        activity.unregisterReceiver(clockReceiver)
        handler.removeCallbacks(weatherRefresh)
    }

    fun destroy() = background.shutdownNow()

    /** Particles only move while Home is actually on screen. */
    fun setVisible(visible: Boolean) {
        this.visible = visible
        particles.running = visible && particles.visibility == View.VISIBLE
    }

    fun applyOccasion() {
        val today = Calendar.getInstance()
        occasionDay = today.get(Calendar.DAY_OF_YEAR)
        val birthdays = activity.getString(R.string.birthdays)
        val o = prefs.themePreview.takeIf { it.isNotEmpty() }?.let { Occasions.preview(it, birthdays) }
            ?: Occasions.on(today, birthdays)
        occasion = o
        if (o == null) {
            emblem.visibility = View.GONE
            occasionGlow.visibility = View.GONE
            particles.show(ParticleView.Effect.NONE, intArrayOf())
            greeting.color = activity.getColor(R.color.text_faint)
        } else {
            emblem.pattern = o.emblem
            emblem.color = o.accent
            emblem.visibility = View.VISIBLE
            greeting.color = o.accent
            occasionGlow.background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                setGradientCenter(0.88f, 0.22f)
                gradientRadius = activity.resources.displayMetrics.widthPixels * 0.55f
                colors = intArrayOf((o.secondary and 0x00FFFFFF) or 0x33000000, o.secondary and 0x00FFFFFF)
            }
            occasionGlow.visibility = View.VISIBLE
            particles.show(o.effect, intArrayOf(o.accent, o.secondary, 0xFFFFFFFF.toInt()))
        }
        setVisible(visible)
        tick()
    }

    private fun tick() {
        if (Calendar.getInstance().get(Calendar.DAY_OF_YEAR) != occasionDay) {
            applyOccasion()
            return
        }
        val now = Date()
        clock.text = DateFormat.getTimeFormat(activity).format(now)
        val pattern = DateFormat.getBestDateTimePattern(activity.resources.configuration.locales[0], "EEEEMMMMd")
        date.text = DateFormat.format(pattern, now).toString().replace(",", "")
        greeting.text = occasion?.greeting ?: activity.getString(
            when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> R.string.good_morning
                in 12..16 -> R.string.good_afternoon
                in 17..21 -> R.string.good_evening
                else -> R.string.good_night
            },
        )
    }

    private fun showWeather(now: Weather.Now?) {
        if (now == null) {
            weather.visibility = View.GONE
            city.text = ""
            return
        }
        weatherIcon.pattern = DotIconView.weather(now.code, now.isDay)
        temperature.text = "${now.temperature}°"
        city.text = now.city
        weather.visibility = View.VISIBLE
    }

    private fun refreshWeather() {
        handler.removeCallbacks(weatherRefresh)
        handler.postDelayed(weatherRefresh, WEATHER_CHECK_MS)
        if (!Weather.isStale(prefs)) return
        background.execute {
            val now = Weather.fetch(activity, prefs)
            if (now != null) handler.post { showWeather(now) }
        }
    }

    private companion object {
        const val WEATHER_CHECK_MS = 10 * 60 * 1000L
    }
}
