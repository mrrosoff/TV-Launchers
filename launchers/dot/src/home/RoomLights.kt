package dev.mrrosoff.tv.dot.home

import android.content.Context
import android.graphics.Color
import dev.mrrosoff.tv.dot.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** Tints the TV's lights through Home Assistant while the wheel spins, and fades them back afterwards. */
class RoomLights(context: Context) {

    private sealed interface Command {
        data object Check : Command
        data class Tint(val color: Int) : Command
        data object Restore : Command
        data object Close : Command
    }

    private val url = context.getString(R.string.ha_url).trim().trimEnd('/')
    private val token = context.getString(R.string.ha_token).trim()
    private val lights = context.getString(R.string.ha_lights).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private val enabled = url.isNotEmpty() && token.isNotEmpty() && lights.isNotEmpty()

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MS
                requestTimeoutMillis = TIMEOUT_MS
            }
        }
    }
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
            Command.Close -> {
                restoreNow()
                client.close()
            }
        }
    }

    private suspend fun tint(color: Int) {
        val on = tinting ?: snapshot().also { tinting = it }
        if (on.isEmpty()) return
        service("light", "turn_on", buildJsonObject {
            put("entity_id", JsonArray(on.map(::JsonPrimitive)))
            put("rgb_color", JsonArray(listOf(Color.red(color), Color.green(color), Color.blue(color)).map(::JsonPrimitive)))
            put("brightness", (brightness * TINT_DIM).toInt().coerceAtLeast(1))
            put("transition", TINT_S)
        })
    }

    private suspend fun restoreNow() {
        if (!tinting.isNullOrEmpty()) {
            service("scene", "turn_on", buildJsonObject {
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
        val saved = service("scene", "create", buildJsonObject {
            put("scene_id", SCENE.removePrefix("scene."))
            put("snapshot_entities", JsonArray(on.map(::JsonPrimitive)))
        })
        return if (saved) on else emptyList()
    }

    private class LightState(val state: String?, val brightness: Int?)

    private suspend fun state(entity: String): LightState? = runCatching {
        val response = client.get("$url/api/states/$entity") { bearerAuth(token) }
        if (!response.status.isSuccess()) return null
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        LightState(
            json["state"]?.jsonPrimitive?.content,
            json["attributes"]?.jsonObject?.get("brightness")?.jsonPrimitive?.intOrNull,
        )
    }.getOrNull()

    private suspend fun service(domain: String, name: String, body: JsonObject): Boolean = runCatching {
        client.post("$url/api/services/$domain/$name") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.status.isSuccess()
    }.getOrDefault(false)

    private companion object {
        const val SCENE = "scene.dot_before_preview"
        const val TINT_S = 0.15
        const val TINT_DIM = 0.6
        const val RESTORE_S = 1.5
        const val TIMEOUT_MS = 1500L
        val STALE = 1.minutes
    }
}
