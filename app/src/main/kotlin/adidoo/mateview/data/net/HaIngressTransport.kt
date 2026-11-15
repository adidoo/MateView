// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

/**
 * Reaches Grafana through Home Assistant and the `hass_ingress` integration
 * (https://github.com/lovelylain/hass_ingress), so nothing has to be exposed
 * to the Internet beyond Home Assistant itself.
 *
 * Handshake, over the Home Assistant WebSocket API authenticated with a
 * long-lived access token:
 *  1. `get_panels` gives the rotating token of the ingress panel;
 *  2. `ha-ingress/session` gives a user session, valid 5 minutes and extended
 *     by every proxied request.
 * Both are then sent as cookies to `/api/ingress/<panel>/...`.
 *
 * This relies on how hass_ingress works internally (checked against 1.4.0):
 * when the proxy refuses a request, the handshake is simply done again.
 */
class HaIngressTransport(
    private val haUrl: HttpUrl,
    private val token: String,
    private val panel: String,
    client: OkHttpClient,
) : Transport {

    // A refused request is answered with a redirect to the HA frontend: it must not be followed.
    private val client = client.newBuilder().followRedirects(false).build()
    private val mutex = Mutex()
    private var cookies: String? = null
    private var handshakeAtMs = 0L

    /** Opens (or renews) the ingress session. [onStage] is called as each stage completes. */
    suspend fun handshake(onStage: (Stage) -> Unit = {}) = mutex.withLock { doHandshake(onStage) }

    override suspend fun execute(request: ApiRequest): ApiResponse {
        var response = send(request, renew = false)
        if (response.code in REFUSED_CODES) response = send(request, renew = true)
        if (response.code in REFUSED_CODES) {
            throw TransportException(
                Stage.GRAFANA,
                "The Home Assistant ingress proxy refused the request (HTTP ${response.code}).",
                problem = Problem.PROXY_REFUSED, detail = response.code.toString(),
            )
        }
        return response
    }

    private suspend fun send(request: ApiRequest, renew: Boolean): ApiResponse {
        val cookieHeader = mutex.withLock {
            val stale = System.currentTimeMillis() - handshakeAtMs > SESSION_REFRESH_MS
            if (renew || cookies == null || stale) doHandshake {}
            cookies!!
        }
        val httpRequest = Request.Builder()
            .url(haUrl.resolveUnder("/api/ingress/$panel${request.path}"))
            .method(request.method, request.jsonBody?.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Cookie", cookieHeader)
            .build()
        return try {
            client.newCall(httpRequest).await().also {
                if (it.code !in REFUSED_CODES) handshakeAtMs = System.currentTimeMillis()
            }
        } catch (e: IOException) {
            throw TransportException(Stage.GRAFANA, e.message ?: e.javaClass.simpleName, e)
        }
    }

    private suspend fun doHandshake(onStage: (Stage) -> Unit) {
        val socket = HaSocket.open(client, haUrl.resolveUnder("/api/websocket"))
        try {
            // 1. Authentication.
            val hello = socket.receive(Stage.REACH_HA)
            if (hello.type != "auth_required" && hello.type != "auth_ok") {
                throw TransportException(Stage.REACH_HA, "This address does not answer like Home Assistant.",
                    problem = Problem.NOT_HOME_ASSISTANT)
            }
            onStage(Stage.REACH_HA)
            if (hello.type == "auth_required") {
                socket.send(buildJsonObject { put("type", "auth"); put("access_token", token) })
                val auth = socket.receive(Stage.AUTH_HA)
                if (auth.type != "auth_ok") {
                    throw TransportException(Stage.AUTH_HA, "Home Assistant rejected the access token.",
                        problem = Problem.TOKEN_REJECTED)
                }
            }
            onStage(Stage.AUTH_HA)

            // 2. Token of the ingress panel.
            val panels = socket.call(1, "get_panels", Stage.FIND_PANEL)
            val config = panels[panel]?.jsonObject?.get("config") as? JsonObject
            val panelToken = (config?.get("token") as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull
            if (config == null || panelToken == null) {
                val available = panels.filterValues { it.isIngressPanel() }.keys.sorted()
                val hint = if (available.isEmpty()) {
                    "No ingress panel is visible to this Home Assistant user."
                } else {
                    "Ingress panels visible to this user: ${available.joinToString()}."
                }
                throw TransportException(
                    Stage.FIND_PANEL, "Panel \"$panel\" not found. $hint",
                    problem = Problem.PANEL_NOT_FOUND, detail = available.joinToString(),
                )
            }
            val cookieName = config["field"]?.jsonPrimitive?.contentOrNull ?: DEFAULT_COOKIE_NAME
            onStage(Stage.FIND_PANEL)

            // 3. User session.
            val session = socket.call(2, "ha-ingress/session", Stage.OPEN_SESSION)["session"]
                ?.jsonPrimitive?.contentOrNull
                ?: throw TransportException(Stage.OPEN_SESSION, "hass_ingress did not return a session.", problem = Problem.NO_SESSION)
            onStage(Stage.OPEN_SESSION)

            cookies = "$cookieName=$panelToken; $SESSION_COOKIE_NAME=$session"
            handshakeAtMs = System.currentTimeMillis()
        } finally {
            socket.close()
        }
    }

    private companion object {
        const val DEFAULT_COOKIE_NAME = "ingress_token"
        const val SESSION_COOKIE_NAME = "ha_ingress_session"

        /** The session lasts 5 minutes without traffic; renew a little earlier. */
        const val SESSION_REFRESH_MS = 4 * 60 * 1000L

        /** What hass_ingress answers when the cookies are missing, expired or rotated. */
        val REFUSED_CODES = setOf(301, 302, 303, 307, 401, 404)
    }
}

private fun kotlinx.serialization.json.JsonElement.isIngressPanel(): Boolean {
    val custom = (this as? JsonObject)?.get("config")?.let { it as? JsonObject }?.get("_panel_custom") as? JsonObject
    return custom?.get("name")?.jsonPrimitive?.contentOrNull == "ha-panel-ingress"
}

/** A message of the Home Assistant WebSocket API. */
private class HaMessage(val json: JsonObject) {
    val type: String? get() = json["type"]?.jsonPrimitive?.contentOrNull
    val id: Int? get() = json["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}

/** Minimal Home Assistant WebSocket client: sequential request / response only. */
private class HaSocket private constructor(private val incoming: Channel<String>) : WebSocketListener() {
    private lateinit var webSocket: WebSocket

    override fun onMessage(webSocket: WebSocket, text: String) {
        incoming.trySend(text)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        val detail = response?.let { "HTTP ${it.code}" } ?: t.message ?: t.javaClass.simpleName
        incoming.close(IOException(detail, t))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        incoming.close()
    }

    fun send(message: JsonObject) {
        webSocket.send(message.toString())
    }

    suspend fun receive(stage: Stage): HaMessage {
        val text = try {
            withTimeoutOrNull(TIMEOUT_MS) { incoming.receiveCatching() }
        } catch (e: IOException) {
            throw TransportException(stage, e.message ?: "Connection failed.", e)
        } ?: throw TransportException(stage, "No answer from Home Assistant.", problem = Problem.NO_ANSWER)
        val value = text.getOrNull() ?: throw TransportException(
            stage,
            text.exceptionOrNull()?.message ?: "Home Assistant closed the connection.",
            text.exceptionOrNull(),
        )
        return HaMessage(Json.parseToJsonElement(value).jsonObject)
    }

    /** Sends a command and returns the `result` object of its answer. */
    suspend fun call(id: Int, type: String, stage: Stage): JsonObject {
        send(buildJsonObject { put("id", id); put("type", type) })
        while (true) {
            val message = receive(stage)
            if (message.id != id || message.type != "result") continue
            val success = message.json["success"]?.jsonPrimitive?.contentOrNull == "true"
            if (!success) {
                val error = (message.json["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                throw TransportException(stage, error ?: "Home Assistant refused \"$type\".")
            }
            return message.json["result"] as? JsonObject
                ?: throw TransportException(stage, "Unexpected answer to \"$type\".")
        }
    }

    fun close() {
        webSocket.close(1000, null)
        incoming.close()
    }

    companion object {
        private const val TIMEOUT_MS = 15_000L

        fun open(client: OkHttpClient, url: HttpUrl): HaSocket {
            val socket = HaSocket(Channel(Channel.UNLIMITED))
            socket.webSocket = client.newWebSocket(Request.Builder().url(url).build(), socket)
            return socket
        }
    }
}
