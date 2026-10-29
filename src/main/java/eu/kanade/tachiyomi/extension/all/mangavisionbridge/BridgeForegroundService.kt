package eu.kanade.tachiyomi.extension.all.mangavisionbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground Service that keeps the embedded bridge server active in the background.
 */
class BridgeForegroundService : Service() {

    private var server: BridgeHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 8765) ?: 8765
        val pin = intent?.getStringExtra(EXTRA_PIN) ?: "123456"

        startForeground(NOTIFICATION_ID, createNotification(port, pin))

        if (server == null) {
            server = BridgeHttpServer(applicationContext, port, pin).apply {
                start()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    private fun createNotification(port: Int, pin: String): Notification {
        val channelId = "manga_vision_bridge_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Manga Vision Bridge Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the local manga sync server active while reading in browser."
            }
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, BridgeForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Manga Vision Bridge Active")
            .setContentText("Listening on port $port · Pairing PIN: $pin")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop Bridge", stopPending)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 87651
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PIN = "extra_pin"
        const val ACTION_STOP = "action_stop_bridge"
    }
}
