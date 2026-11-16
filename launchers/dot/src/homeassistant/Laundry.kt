package dev.mrrosoff.tv.dot.homeassistant

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import dev.mrrosoff.tv.dot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Laundry integration: a banner when the washer (ha_washer) or dryer (ha_dryer) finishes while the TV is on.
 *
 * - Washer done while the dryer runs: nothing yet; the dryer finishing then says "Laundry done".
 * - Dryer done in one of tv.properties' movie_apps: waits until another app is in front.
 *
 * It also keeps [LaundryProgress] current, from the same entities Muni-Display reads.
 */
class Laundry(private val service: AccessibilityService) {

    private val ha = HomeAssistant.get(service)
    private val washer = service.getString(R.string.ha_washer).trim()
    private val dryer = service.getString(R.string.ha_dryer).trim()
    private val banner = Banner(service)
    private val power = service.getSystemService(PowerManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val showing = Mutex()

    private val status = mutableMapOf<String, String>()
    private var inMovie = false
    private var waiting: String? = null

    fun start() {
        if (!ha.enabled || washer.isEmpty() || dryer.isEmpty()) return
        scope.launch {
            var wait = 5.seconds
            while (isActive) {
                val connected = TimeSource.Monotonic.markNow()
                for (sensor in listOf(washer, dryer)) {
                    ha.state(sensor)?.get("state")?.jsonPrimitive?.content?.let { status[sensor] = it }
                }
                refreshProgress()
                ha.subscribe(trigger()) { changed(it) }
                if (connected.elapsedNow() > 1.minutes) wait = 5.seconds
                delay(wait)
                wait = (wait * 2).coerceAtMost(2.minutes)
            }
        }
    }

    fun stop() = scope.cancel()

    /** A movie app came to the front or went; a dryer banner held back for a movie shows once it's gone. */
    fun movieChanged(movie: Boolean) {
        inMovie = movie
        val text = waiting ?: return
        if (movie) return
        waiting = null
        if (status[dryer] == "done") show(text)
    }

    private fun trigger() = buildJsonObject {
        put("platform", "state")
        put("entity_id", JsonArray(listOf(washer, dryer).map(::JsonPrimitive)))
        put("to", JsonNull)
    }

    private fun changed(fired: JsonObject) {
        val sensor = fired["entity_id"]?.jsonPrimitive?.content ?: return
        val to = fired.state("to_state") ?: return
        status[sensor] = to
        scope.launch { refreshProgress() }
        if (to != "done") {
            if (sensor == dryer) waiting = null
            return
        }
        when {
            sensor == washer && status[dryer] == "running" -> Unit
            sensor == washer -> announce("Washer done", always = true)
            status[washer] == "done" -> announce("Laundry done", always = true)
            else -> announce("Dryer done", always = false)
        }
    }

    private fun announce(text: String, always: Boolean) {
        if (!power.isInteractive) return
        if (!always && inMovie) waiting = text else show(text)
    }

    private fun show(text: String) {
        if (power.isInteractive) scope.launch { showing.withLock { banner.show(text) } }
    }

    private suspend fun refreshProgress() {
        val json = ha.template(progressTemplate())?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return
        LaundryProgress.publish(listOf("washer" to "Washer", "dryer" to "Dryer").mapNotNull { (key, name) ->
            if (json[key]?.jsonPrimitive?.content != "running") return@mapNotNull null
            val start = json["${key}_start"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val expected = json["${key}_expected"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            LaundryProgress.Run(name, (start * 1000).toLong(), expected.toInt())
        })
    }

    /** Muni-Display's laundry template, so both read the same entities the same way. */
    private fun progressTemplate() = """
        {% set w = states.binary_sensor.washer_running %}
        {{ {'washer': states('$washer'),
            'washer_start': as_timestamp(w.last_changed) if w else none,
            'washer_expected': states('input_number.washer_expected_min'),
            'dryer': states('$dryer'),
            'dryer_start': state_attr('input_datetime.dryer_cycle_start', 'timestamp'),
            'dryer_expected': states('input_number.dryer_expected_min')} | tojson }}
    """.trimIndent()

    private fun JsonObject.state(key: String) = (this[key] as? JsonObject)?.get("state")?.jsonPrimitive?.content
}
