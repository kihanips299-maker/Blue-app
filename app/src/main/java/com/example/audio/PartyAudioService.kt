package com.example.audio

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
import com.example.MainActivity
import com.example.R

class PartyAudioService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE -> {
                val trackTitle = intent.getStringExtra(EXTRA_TRACK_TITLE) ?: "Neon Pulse"
                val sinkCount = intent.getIntExtra(EXTRA_SINK_COUNT, 1)
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val notification = buildNotification(trackTitle, sinkCount, isPlaying)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(
        trackTitle: String,
        sinkCount: Int,
        isPlaying: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isPlaying) {
            "Synced across $sinkCount speaker${if (sinkCount == 1) "" else "s"}"
        } else {
            "Paused • $sinkCount speaker${if (sinkCount == 1) "" else "s"} ready"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AmpSync • $trackTitle")
            .setContentText(statusText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.party_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.party_channel_desc)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ampsync_party_playback"
        private const val NOTIFICATION_ID = 4041
        const val ACTION_START_OR_UPDATE = "com.example.ampsync.START_OR_UPDATE"
        const val ACTION_STOP = "com.example.ampsync.STOP"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_SINK_COUNT = "extra_sink_count"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun updateNotification(
            context: Context,
            trackTitle: String,
            sinkCount: Int,
            isPlaying: Boolean
        ) {
            val intent = Intent(context, PartyAudioService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TRACK_TITLE, trackTitle)
                putExtra(EXTRA_SINK_COUNT, sinkCount)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            runCatching {
                if (isPlaying) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.startService(Intent(context, PartyAudioService::class.java).apply {
                        action = ACTION_STOP
                    })
                }
            }
        }
    }
}
