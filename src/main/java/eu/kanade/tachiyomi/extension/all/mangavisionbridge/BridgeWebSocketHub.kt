package eu.kanade.tachiyomi.extension.all.mangavisionbridge

import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class BridgeWsMessage(
    val type: String,
    val payload: String = ""
)

/**
 * Manages active WebSocket subscriber connections for bidirectional sync events.
 */
class BridgeWebSocketHub {

    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketSession, Boolean>())
    private val json = Json { ignoreUnknownKeys = true }

    fun register(session: DefaultWebSocketSession) {
        sessions.add(session)
    }

    fun unregister(session: DefaultWebSocketSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(type: String, payload: String = "") {
        val frame = Frame.Text(json.encodeToString(BridgeWsMessage(type, payload)))
        val deadSessions = mutableListOf<DefaultWebSocketSession>()

        for (session in sessions) {
            try {
                session.send(frame)
            } catch (e: ClosedSendChannelException) {
                deadSessions.add(session)
            } catch (e: Exception) {
                deadSessions.add(session)
            }
        }

        sessions.removeAll(deadSessions)
    }
}
