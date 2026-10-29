package eu.kanade.tachiyomi.extension.all.mangavisionbridge

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID

@Serializable
data class PairRequest(val pin: String)

@Serializable
data class PairResponse(val success: Boolean, val token: String? = null, val message: String = "")

@Serializable
data class ProgressUpdateRequest(
    val chapterId: Long,
    val lastPageRead: Int,
    val isRead: Boolean
)

@Serializable
data class PageListItem(
    val index: Int,
    val url: String,
    val mimeType: String,
    val size: Long
)

/**
 * Embedded Ktor HTTP & WebSocket Server hosted on loopback port.
 */
class BridgeHttpServer(
    private val context: Context,
    private val port: Int,
    private val requiredPin: String
) {
    private var engine: ApplicationEngine? = null
    private val dbReader = MihonDatabaseReader(context)
    private val streamer = DownloadStreamer(context)
    private val wsHub = BridgeWebSocketHub()
    private val activeTokens = mutableSetOf<String>()

    fun start() {
        if (engine != null) return

        engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowCredentials = true
            }

            install(WebSockets)

            routing {
                // Public Health Check
                get("/api/v1/ping") {
                    call.respond(
                        mapOf(
                            "status" to "ok",
                            "name" to "Manga Vision Bridge",
                            "version" to "1.0.0",
                            "requiresAuth" to true
                        )
                    )
                }

                // Authentication / Pairing
                post("/api/v1/auth/pair") {
                    val req = try {
                        call.receive<PairRequest>()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, PairResponse(false, message = "Malformed request"))
                        return@post
                    }

                    if (req.pin.trim() == requiredPin.trim()) {
                        val token = UUID.randomUUID().toString().replace("-", "")
                        activeTokens.add(token)
                        call.respond(PairResponse(true, token = token, message = "Paired successfully"))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, PairResponse(false, message = "Invalid PIN"))
                    }
                }

                // Authenticated routes
                get("/api/v1/library") {
                    if (!isAuthorized(call.request.header(HttpHeaders.Authorization))) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@get
                    }
                    val manga = dbReader.getLibraryManga()
                    call.respond(manga)
                }

                get("/api/v1/manga/{id}/chapters") {
                    if (!isAuthorized(call.request.header(HttpHeaders.Authorization))) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@get
                    }
                    val id = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid manga id")
                        return@get
                    }
                    val chapters = dbReader.getChapters(id)
                    call.respond(chapters)
                }

                get("/api/v1/chapter/{id}/pages") {
                    if (!isAuthorized(call.request.header(HttpHeaders.Authorization))) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@get
                    }
                    val chapterId = call.parameters["id"]?.toLongOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Invalid chapter id")
                        return@get
                    }

                    val mangaTitle = call.request.queryParameters["mangaTitle"] ?: ""
                    val chapterName = call.request.queryParameters["chapterName"] ?: ""

                    val localPages = streamer.findChapterPages(mangaTitle, chapterName)
                    val response = localPages.map { asset ->
                        PageListItem(
                            index = asset.index,
                            url = "/api/v1/page?manga=${mangaTitle}&chapter=${chapterName}&index=${asset.index}",
                            mimeType = asset.mimeType,
                            size = asset.size
                        )
                    }

                    call.respond(response)
                }

                get("/api/v1/page") {
                    val mangaTitle = call.request.queryParameters["manga"] ?: ""
                    val chapterName = call.request.queryParameters["chapter"] ?: ""
                    val index = call.request.queryParameters["index"]?.toIntOrNull() ?: 0

                    val pages = streamer.findChapterPages(mangaTitle, chapterName)
                    val target = pages.getOrNull(index)

                    if (target != null) {
                        val stream = target.openStream()
                        if (stream != null) {
                            val bytes = stream.readBytes()
                            stream.close()
                            call.respondBytes(bytes, ContentType.parse(target.mimeType))
                            return@get
                        }
                    }

                    call.respond(HttpStatusCode.NotFound, "Page not found in local storage")
                }

                post("/api/v1/progress") {
                    if (!isAuthorized(call.request.header(HttpHeaders.Authorization))) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@post
                    }
                    val req = call.receive<ProgressUpdateRequest>()
                    val ok = dbReader.updateProgress(req.chapterId, req.lastPageRead, req.isRead)
                    if (ok) {
                        wsHub.broadcast("PROGRESS_SYNCED", req.chapterId.toString())
                        call.respond(mapOf("success" to true))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to update SQLite database")
                    }
                }

                // WebSocket Channel
                webSocket("/ws") {
                    wsHub.register(this)
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                // Echo or dispatch incoming web reader event
                                if (text.contains("PING")) {
                                    send(Frame.Text("PONG"))
                                }
                            }
                        }
                    } finally {
                        wsHub.unregister(this)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }

    private fun isAuthorized(authHeader: String?): Boolean {
        if (activeTokens.isEmpty()) return true // Allow local testing before PIN is configured
        if (authHeader.isNullOrBlank()) return false
        val token = authHeader.removePrefix("Bearer ").trim()
        return activeTokens.contains(token)
    }
}
