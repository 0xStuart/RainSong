package com.stuartjohnson.rainsong.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.stuartjohnson.rainsong.MainActivity
import com.stuartjohnson.rainsong.R
import com.stuartjohnson.rainsong.RainSongApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RainSongService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startAsForeground(text = "Listening for rain")
        observeJob = scope.launch {
            (application as RainSongApp).session.state.collect { state ->
                val line = when {
                    !state.connected && state.reconnecting -> "Reconnecting…"
                    state.connected -> {
                        val rain = "%.1f drops/s".format(state.intensity.dropsPerSecond)
                        val title = state.arrangement?.title
                        if (title.isNullOrBlank()) "RainSong · $rain" else "$title · $rain"
                    }
                    else -> state.statusMessage ?: "RainSong"
                }
                startAsForeground(line)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as RainSongApp).session.disconnectToScan()
            stopSelf()
            return START_NOT_STICKY
        }
        val session = (application as RainSongApp).session
        if (!session.isPlaying) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFY_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RainSongService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle("RainSong")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Rain listening",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps RainSong listening and playing after you leave the screen"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "rainsong_listen"
        private const val NOTIFY_ID = 17
        const val ACTION_STOP = "com.stuartjohnson.rainsong.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RainSongService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RainSongService::class.java))
        }
    }
}
