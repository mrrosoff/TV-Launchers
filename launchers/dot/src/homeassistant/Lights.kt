package dev.mrrosoff.tv.dot.homeassistant

import android.content.Context
import android.graphics.Color
import dev.mrrosoff.tv.dot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Lights integration:
 * - Wheel: ha_lights take the selected app's colour while the wheel spins, and fade back when Home goes.
 * - Movie mode: ha_movie_lights dim while one of tv.properties' movie_apps is in front, and fade back after.
 *
 * Callers set what they want; one worker brings the lights there, so quick changes never cross.
 */
class Lights private constructor(context: Context) {

    private val ha = HomeAssistant.get(context)
    private val wheelLights = list(context, R.string.ha_lights)
    private val movieLights = list(context, R.string.ha_movie_lights)

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var wantColor: Int? = null
    @Volatile private var wantMovie = false
    @Volatile private var wantCheck = false

    private var tinting: List<String>? = null
    private var sentColor: Int? = null
    private var dimmed = false
    private var onLights = emptyList<String>()
    private var brightness = 255
    private var checkedAt: TimeSource.Monotonic.ValueTimeMark? = null

    init {
        if (ha.enabled) scope.launch {
            for (signal in wake) runCatching { settle() }
        }
    }

    /** Home is on screen: learn which wheel lights are on, so the first tint goes out at once. */
    fun prepare() = want { wantCheck = true }

    fun preview(color: Int) = want { wantColor = color }

    fun restore() = want { wantColor = null }

    fun movie(on: Boolean) = want {
        wantMovie = on
        if (on) wantColor = null
    }

    private fun want(change: () -> Unit) {
        if (!ha.enabled) return
        change()
        wake.trySend(Unit)
    }

    private suspend fun settle() {
        if (wantCheck) {
            wantCheck = false
            checkWheelLights()
        }
        if (dimmed && !wantMovie) {
            ha.service("scene", "turn_on", scene(MOVIE_SCENE, MOVIE_S))
            dimmed = false
        }
        val color = wantColor
        if (color == null && tinting != null) {
            if (tinting!!.isNotEmpty()) ha.service("scene", "turn_on", scene(WHEEL_SCENE, RESTORE_S))
            tinting = null
            sentColor = null
        }
        if (wantMovie && !dimmed && movieLights.isNotEmpty()) dimmed = dim()
        if (color != null && color != sentColor && wheelLights.isNotEmpty()) {
            delay(TINT_DELAY)
            val latest = wantColor ?: return
            tint(latest, sentColor)
            sentColor = latest
        }
    }

    private suspend fun tint(color: Int, from: Int?) {
        val on = tinting ?: snapshotWheel().also { tinting = it }
        if (on.isEmpty()) return
        // Hue fades in a straight line across the colour chart, which between distant colours runs through
        // white; those switch at once instead.
        val transition = if (from != null && farApart(from, color)) 0.0 else TINT_S
        ha.service("light", "turn_on", buildJsonObject {
            put("entity_id", JsonArray(on.map(::JsonPrimitive)))
            put("rgb_color", JsonArray(listOf(Color.red(color), Color.green(color), Color.blue(color)).map(::JsonPrimitive)))
            put("brightness", (brightness * TINT_DIM).toInt().coerceAtLeast(1))
            put("transition", transition)
        })
    }

    private fun farApart(a: Int, b: Int): Boolean {
        val ha = FloatArray(3).also { Color.colorToHSV(a, it) }
        val hb = FloatArray(3).also { Color.colorToHSV(b, it) }
        if (ha[1] < SATURATED || hb[1] < SATURATED) return false
        val d = abs(ha[0] - hb[0]).let { minOf(it, 360f - it) }
        return d > FAR_HUE
    }

    private suspend fun snapshotWheel(): List<String> {
        if (checkedAt.let { it == null || it.elapsedNow() > STALE }) checkWheelLights()
        val on = onLights
        if (on.isEmpty() || !save(WHEEL_SCENE, on)) return emptyList()
        return on
    }

    private suspend fun checkWheelLights() {
        val on = wheelLights.mapNotNull { id -> ha.state(id)?.takeIf { it.isOn() }?.let { id to it } }
        onLights = on.map { it.first }
        on.mapNotNull { it.second.brightness() }.takeIf { it.isNotEmpty() }?.let { brightness = it.average().toInt() }
        checkedAt = TimeSource.Monotonic.markNow()
    }

    /** Each movie light that is on and dimmable goes to [MOVIE_DIM] of its brightness; false if none did. */
    private suspend fun dim(): Boolean {
        val on = movieLights.mapNotNull { id -> ha.state(id)?.takeIf { it.isOn() }?.brightness()?.let { id to it } }
        if (on.isEmpty() || !save(MOVIE_SCENE, on.map { it.first })) return false
        for ((id, level) in on) {
            ha.service("light", "turn_on", buildJsonObject {
                put("entity_id", id)
                put("brightness", (level * MOVIE_DIM).toInt().coerceAtLeast(1))
                put("transition", MOVIE_S)
            })
        }
        return true
    }

    private suspend fun save(scene: String, entities: List<String>) = ha.service("scene", "create", buildJsonObject {
        put("scene_id", scene.removePrefix("scene."))
        put("snapshot_entities", JsonArray(entities.map(::JsonPrimitive)))
    })

    private fun scene(id: String, transition: Double) = buildJsonObject {
        put("entity_id", id)
        put("transition", transition)
    }

    private fun JsonObject.isOn() = this["state"]?.jsonPrimitive?.content == "on"

    private fun JsonObject.brightness() = this["attributes"]?.jsonObject?.get("brightness")?.jsonPrimitive?.intOrNull

    companion object {
        private const val WHEEL_SCENE = "scene.dot_before_preview"
        private const val MOVIE_SCENE = "scene.dot_before_movie"
        private const val TINT_S = 0.3
        private val TINT_DELAY = 150.milliseconds
        private const val TINT_DIM = 0.6
        private const val SATURATED = 0.3f
        private const val FAR_HUE = 60f
        private const val RESTORE_S = 1.5
        private const val MOVIE_S = 3.0
        private const val MOVIE_DIM = 0.3
        private val STALE = 1.minutes

        @Volatile private var shared: Lights? = null

        fun get(context: Context): Lights =
            shared ?: synchronized(this) { shared ?: Lights(context.applicationContext).also { shared = it } }

        private fun list(context: Context, id: Int) =
            context.getString(id).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
