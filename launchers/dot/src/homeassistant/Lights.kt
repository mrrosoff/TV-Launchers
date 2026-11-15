package dev.mrrosoff.tv.dot.homeassistant

import android.content.Context
import android.graphics.Color
import dev.mrrosoff.tv.dot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** Lights integration: tints ha_lights in the selected app's colour while the wheel spins, and fades them back. */
class Lights(context: Context) {

    private sealed interface Command {
        data object Check : Command
        data class Tint(val color: Int) : Command
        data object Restore : Command
        data object Close : Command
    }

    private val ha = HomeAssistant.get(context)
    private val lights = context.getString(R.string.ha_lights).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private val enabled = ha.enabled && lights.isNotEmpty()

    private val commands = Channel<Command>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var onLights = emptyList<String>()
    private var brightness = 255
    private var checkedAt: TimeSource.Monotonic.ValueTimeMark? = null
    private var tinting: List<String>? = null

    init {
        if (enabled) scope.launch {
            for (command in commands) {
                runCatching { handle(command) }
                if (command == Command.Close) break
            }
        }
    }

    fun prepare() = send(Command.Check)

    fun preview(color: Int) = send(Command.Tint(color))

    fun restore() = send(Command.Restore)

    fun destroy() = send(Command.Close)

    private fun send(command: Command) {
        if (enabled) commands.trySend(command)
    }

    private suspend fun handle(command: Command) {
        when (command) {
            Command.Check -> checkLights()
            is Command.Tint -> tint(command.color)
            Command.Restore -> restoreNow()
            Command.Close -> restoreNow()
        }
    }

    private suspend fun tint(color: Int) {
        val on = tinting ?: snapshot().also { tinting = it }
        if (on.isEmpty()) return
        ha.service("light", "turn_on", buildJsonObject {
            put("entity_id", JsonArray(on.map(::JsonPrimitive)))
            put("rgb_color", JsonArray(listOf(Color.red(color), Color.green(color), Color.blue(color)).map(::JsonPrimitive)))
            put("brightness", (brightness * TINT_DIM).toInt().coerceAtLeast(1))
            put("transition", TINT_S)
        })
    }

    private suspend fun restoreNow() {
        if (!tinting.isNullOrEmpty()) {
            ha.service("scene", "turn_on", buildJsonObject {
                put("entity_id", SCENE)
                put("transition", RESTORE_S)
            })
        }
        tinting = null
    }

    private suspend fun checkLights() {
        val on = lights.mapNotNull { id -> state(id)?.takeIf { it.state == "on" }?.let { id to it } }
        onLights = on.map { it.first }
        on.mapNotNull { it.second.brightness }.takeIf { it.isNotEmpty() }?.let { brightness = it.average().toInt() }
        checkedAt = TimeSource.Monotonic.markNow()
    }

    private suspend fun snapshot(): List<String> {
        if (checkedAt.let { it == null || it.elapsedNow() > STALE }) checkLights()
        val on = onLights
        if (on.isEmpty()) return on
        val saved = ha.service("scene", "create", buildJsonObject {
            put("scene_id", SCENE.removePrefix("scene."))
            put("snapshot_entities", JsonArray(on.map(::JsonPrimitive)))
        })
        return if (saved) on else emptyList()
    }

    private class LightState(val state: String?, val brightness: Int?)

    private suspend fun state(entity: String): LightState? = ha.state(entity)?.let { json ->
        LightState(
            json["state"]?.jsonPrimitive?.content,
            json["attributes"]?.jsonObject?.get("brightness")?.jsonPrimitive?.intOrNull,
        )
    }

    private companion object {
        const val SCENE = "scene.dot_before_preview"
        const val TINT_S = 0.15
        const val TINT_DIM = 0.6
        const val RESTORE_S = 1.5
        val STALE = 1.minutes
    }
}
