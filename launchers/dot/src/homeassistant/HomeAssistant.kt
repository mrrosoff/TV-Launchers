package dev.mrrosoff.tv.dot.homeassistant

import android.content.Context
import dev.mrrosoff.tv.dot.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

/**
 * The household's Home Assistant, from ha_url and ha_token in the TV's personal.properties. One client for the
 * whole app, shared by the integrations: [Lights] and [Laundry].
 */
class HomeAssistant private constructor(context: Context) {

    private val url = context.getString(R.string.ha_url).trim().trimEnd('/')
    private val token = context.getString(R.string.ha_token).trim()
    val enabled = url.isNotEmpty() && token.isNotEmpty()

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) { connectTimeoutMillis = TIMEOUT_MS }
            install(WebSockets) { pingInterval = 30.seconds.inWholeMilliseconds }
        }
    }

    suspend fun state(entity: String): JsonObject? = runCatching {
        val response = client.get("$url/api/states/$entity") {
            bearerAuth(token)
            timeout { requestTimeoutMillis = TIMEOUT_MS }
        }
        if (!response.status.isSuccess()) return null
        Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }.getOrNull()

    suspend fun service(domain: String, name: String, body: JsonObject): Boolean = runCatching {
        client.post("$url/api/services/$domain/$name") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = TIMEOUT_MS }
        }.status.isSuccess()
    }.getOrDefault(false)

    /** Runs [onFire] with the trigger's variables each time [trigger] fires; returns when the connection drops. */
    suspend fun subscribe(trigger: JsonObject, onFire: suspend (JsonObject) -> Unit) {
        runCatching {
            client.webSocket(url.replaceFirst("http", "ws") + "/api/websocket") {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val message = Json.parseToJsonElement(frame.readText()).jsonObject
                    when (message["type"]?.jsonPrimitive?.content) {
                        "auth_required" -> send(Frame.Text(buildJsonObject {
                            put("type", "auth")
                            put("access_token", token)
                        }.toString()))
                        "auth_ok" -> send(Frame.Text(buildJsonObject {
                            put("id", 1)
                            put("type", "subscribe_trigger")
                            put("trigger", trigger)
                        }.toString()))
                        "auth_invalid" -> return@webSocket
                        "event" -> message["event"]?.jsonObject?.get("variables")?.jsonObject?.get("trigger")?.jsonObject
                            ?.let { onFire(it) }
                    }
                }
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 1500L
        @Volatile private var shared: HomeAssistant? = null

        fun get(context: Context): HomeAssistant =
            shared ?: synchronized(this) { shared ?: HomeAssistant(context.applicationContext).also { shared = it } }
    }
}
