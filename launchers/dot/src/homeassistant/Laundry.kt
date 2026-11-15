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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Laundry integration: flags ha_laundry's sensors turning "done" while the TV is on. */
class Laundry(private val service: AccessibilityService) {

    private val ha = HomeAssistant.get(service)
    private val sensors = service.getString(R.string.ha_laundry).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private val banner = Banner(service)
    private val power = service.getSystemService(PowerManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val showing = Mutex()

    fun start() {
        if (!ha.enabled || sensors.isEmpty()) return
        scope.launch {
            var wait = 5.seconds
            while (isActive) {
                val connected = TimeSource.Monotonic.markNow()
                ha.subscribe(trigger()) { fired ->
                    if (power.isInteractive) scope.launch { showing.withLock { banner.show("${name(fired)} done") } }
                }
                if (connected.elapsedNow() > 1.minutes) wait = 5.seconds
                delay(wait)
                wait = (wait * 2).coerceAtMost(2.minutes)
            }
        }
    }

    fun stop() = scope.cancel()

    private fun trigger() = buildJsonObject {
        put("platform", "state")
        put("entity_id", JsonArray(sensors.map(::JsonPrimitive)))
        put("to", "done")
    }

    /** "Washer Status" becomes "Washer". */
    private fun name(fired: JsonObject): String {
        val state = fired["to_state"]?.jsonObject
        val friendly = state?.get("attributes")?.jsonObject?.get("friendly_name")?.jsonPrimitive?.content
        val entity = fired["entity_id"]?.jsonPrimitive?.content.orEmpty()
        return (friendly ?: entity.substringAfter('.').replace('_', ' ')).removeSuffix(" Status").removeSuffix(" status")
    }
}
