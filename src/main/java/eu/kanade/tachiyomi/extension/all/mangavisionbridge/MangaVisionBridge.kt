package eu.kanade.tachiyomi.extension.all.mangavisionbridge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.security.SecureRandom

/**
 * Manga Vision Bridge Extension
 *
 * Implements Mihon's HttpSource and ConfigurableSource interfaces so it registers
 * as a native extension inside Mihon. Upon startup and preference activation, it launches
 * a local foreground service hosting an embedded Ktor HTTP + WebSocket server.
 */
class MangaVisionBridge : HttpSource(), ConfigurableSource {

    override val name = "Manga Vision Bridge"
    override val baseUrl = "http://127.0.0.1:8765"
    override val lang = "all"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client

    private val preferences: SharedPreferences by lazy {
        val app = getApplicationContext()
        app.getSharedPreferences("source_$id", Context.MODE_PRIVATE)
    }

    init {
        ensurePairingPin()
        if (isServerEnabled()) {
            startBridgeService()
        }
    }

    private fun isServerEnabled(): Boolean {
        return preferences.getBoolean(KEY_ENABLE_SERVER, true)
    }

    private fun getServerPort(): Int {
        return preferences.getString(KEY_SERVER_PORT, "8765")?.toIntOrNull() ?: 8765
    }

    private fun ensurePairingPin(): String {
        var pin = preferences.getString(KEY_PAIRING_PIN, null)
        if (pin.isNullOrBlank()) {
            pin = String.format("%06d", SecureRandom().nextInt(1_000_000))
            preferences.edit().putString(KEY_PAIRING_PIN, pin).apply()
        }
        return pin
    }

    private fun startBridgeService() {
        val context = getApplicationContext()
        val intent = Intent(context, BridgeForegroundService::class.java).apply {
            putExtra(BridgeForegroundService.EXTRA_PORT, getServerPort())
            putExtra(BridgeForegroundService.EXTRA_PIN, ensurePairingPin())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopBridgeService() {
        val context = getApplicationContext()
        val intent = Intent(context, BridgeForegroundService::class.java)
        context.stopService(intent)
    }

    private fun getApplicationContext(): Context {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApp = activityThread.getMethod("currentApplication").invoke(null) as? Application
            currentApp ?: throw IllegalStateException("Unable to acquire ApplicationContext")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to resolve Android Application Context", e)
        }
    }

    // ==========================================
    // Mihon ConfigurableSource Preference Screen
    // ==========================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        val enablePref = SwitchPreferenceCompat(context).apply {
            key = KEY_ENABLE_SERVER
            title = "Enable Bridge Server"
            summary = "Starts the local HTTP & WebSocket daemon for web companion synchronization."
            setDefaultValue(true)
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    startBridgeService()
                } else {
                    stopBridgeService()
                }
                true
            }
        }
        screen.addPreference(enablePref)

        val portPref = EditTextPreference(context).apply {
            key = KEY_SERVER_PORT
            title = "Server Port"
            summary = "Default: 8765. Requires restart of bridge to apply changes."
            setDefaultValue("8765")
        }
        screen.addPreference(portPref)

        val pinPref = EditTextPreference(context).apply {
            key = KEY_PAIRING_PIN
            title = "Web Pairing PIN"
            summary = "Current PIN: ${ensurePairingPin()}"
            isSelectable = false
        }
        screen.addPreference(pinPref)
    }

    // ==========================================
    // Dummy Source Methods (Bridge acts as host)
    // ==========================================

    override fun popularMangaRequest(page: Int): Request =
        Request.Builder().url("$baseUrl/api/v1/library").build()

    override fun popularMangaParse(response: Response): MangasPage =
        MangasPage(emptyList(), false)

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        Request.Builder().url("$baseUrl/api/v1/search?q=$query").build()

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga =
        SManga.create().apply { title = "Manga Vision Bridge" }

    override fun chapterListParse(response: Response): List<SChapter> = emptyList()

    override fun pageListParse(response: Response): List<Page> = emptyList()

    override fun imageUrlParse(response: Response): String = ""

    companion object {
        const val KEY_ENABLE_SERVER = "pref_bridge_enable"
        const val KEY_SERVER_PORT = "pref_bridge_port"
        const val KEY_PAIRING_PIN = "pref_bridge_pin"
    }
}
