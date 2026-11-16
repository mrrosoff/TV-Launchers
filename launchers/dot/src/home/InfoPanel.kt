package dev.mrrosoff.tv.dot.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.data.Occasion
import dev.mrrosoff.tv.dot.data.Occasions
import dev.mrrosoff.tv.dot.data.Prefs
import dev.mrrosoff.tv.dot.data.Weather
import dev.mrrosoff.tv.dot.dots.DotTextView
import dev.mrrosoff.tv.dot.dots.ParticleView
import dev.mrrosoff.tv.dot.homeassistant.LaundryProgress
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors

/**
 * The right side of Home: clock, date, the weather now, laundry in progress, and on themed days a
 * greeting with its emblem, the corner glow and the ambient particles.
 */
class InfoPanel(
    private val activity: Activity,
    private val prefs: Prefs,
    private val handler: Handler,
) {
    private val occasionRow: View = activity.findViewById(R.id.occasion)
    private val emblem: ImageView = activity.findViewById(R.id.emblem)
    private val greeting: DotTextView = activity.findViewById(R.id.greeting)
    private val clock: DotTextView = activity.findViewById(R.id.clock)
    private val date: DotTextView = activity.findViewById(R.id.date)
    private val weather: View = activity.findViewById(R.id.weather)
    private val weatherIcon: ImageView = activity.findViewById(R.id.weather_icon)
    private val temperature: DotTextView = activity.findViewById(R.id.temperature)
    private val condition: DotTextView = activity.findViewById(R.id.condition)
    private val particles: ParticleView = activity.findViewById(R.id.particles)
    private val occasionGlow: View = activity.findViewById(R.id.occasion_glow)
    private val laundry: LinearLayout = activity.findViewById(R.id.laundry)
    private var scope = MainScope()

    private val background = Executors.newSingleThreadExecutor()
    private var occasion: Occasion? = null
    private var occasionDay = -1
    private var visible = false

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = tick()
    }
    private val weatherRefresh = Runnable { refreshWeather() }

    fun start() {
        applyOccasion()
        activity.registerSystemReceiver(clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        showWeather(Weather.cached(prefs))
        refreshWeather()
        scope = MainScope()
        scope.launch { LaundryProgress.runs.collect { showLaundry() } }
    }

    fun stop() {
        activity.unregisterReceiver(clockReceiver)
        handler.removeCallbacks(weatherRefresh)
        scope.cancel()
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
            occasionRow.visibility = View.GONE
            occasionGlow.visibility = View.GONE
            particles.show(ParticleView.Effect.NONE, intArrayOf())
        } else {
            emblem.setImageResource(o.emblem)
            emblem.imageTintList = ColorStateList.valueOf(o.accent)
            greeting.text = o.greeting
            greeting.color = o.accent
            occasionRow.visibility = View.VISIBLE
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
        showLaundry()
    }

    /** "Dryer 35 min left", or "12 min over" (red from 10 over), for each machine running. */
    private fun showLaundry() {
        val runs = LaundryProgress.runs.value
        laundry.removeAllViews()
        laundry.visibility = if (runs.isEmpty()) View.GONE else View.VISIBLE
        val now = System.currentTimeMillis()
        for (run in runs) {
            val left = run.minutesLeft(now)
            laundry.addView(DotTextView(activity).apply {
                pitch = activity.dp(2.4f)
                setPadding(0, activity.dp(6f).toInt(), 0, 0)
                text = when {
                    left == null -> activity.getString(R.string.laundry_running, run.name)
                    left >= 0 -> activity.getString(R.string.laundry_left, run.name, left)
                    else -> activity.getString(R.string.laundry_over, run.name, -left)
                }
                color = activity.getColor(if (left != null && left <= -OVERDUE_MIN) R.color.overdue else R.color.text_faint)
            })
        }
    }

    private fun showWeather(now: Weather.Now?) {
        if (now == null) {
            weather.visibility = View.GONE
            return
        }
        weatherIcon.setImageResource(Weather.icon(now.code, now.isDay))
        temperature.text = activity.getString(R.string.degrees, now.temperature)
        condition.text = Weather.describe(activity, now.code)
        weather.visibility = View.VISIBLE
    }

    private fun refreshWeather() {
        handler.removeCallbacks(weatherRefresh)
        handler.postDelayed(weatherRefresh, WEATHER_CHECK_MS)
        if (!Weather.isStale(prefs) && Weather.cached(prefs) != null) return
        background.execute {
            val now = Weather.fetch(activity, prefs)
            if (now != null) handler.post { showWeather(now) }
        }
    }

    private companion object {
        const val WEATHER_CHECK_MS = 10 * 60 * 1000L
        const val OVERDUE_MIN = 10
    }
}
