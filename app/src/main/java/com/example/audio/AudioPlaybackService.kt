package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.MainActivity
import com.example.R

class AudioPlaybackService : Service() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSession(this, "PulseMixSession").apply {
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f)
                    .build()
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                NativeAudioEngine.setDeckPlaying(0, false)
                NativeAudioEngine.setDeckPlaying(1, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val deckATitle = intent.getStringExtra(EXTRA_TITLE_A) ?: "Deck A"
                val deckBTitle = intent.getStringExtra(EXTRA_TITLE_B) ?: "Deck B"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val notification = buildNotification(deckATitle, deckBTitle, isPlaying)
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

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PulseMix Live Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active DJ mixing engine session"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(titleA: String, titleB: String, isPlaying: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val status = if (isPlaying) "Mixing Live" else "Paused"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("PulseMix • $status")
            .setContentText("A: $titleA  |  B: $titleB")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Stop Decks",
                    stopIntent
                ).build()
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pulsemix_playback"
        private const val NOTIFICATION_ID = 1042
        const val ACTION_UPDATE = "com.example.action.UPDATE_PLAYBACK"
        const val ACTION_STOP = "com.example.action.STOP_PLAYBACK"
        const val EXTRA_TITLE_A = "extra_title_a"
        const val EXTRA_TITLE_B = "extra_title_b"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun syncService(context: Context, titleA: String, titleB: String, anyPlaying: Boolean) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = if (anyPlaying) ACTION_UPDATE else ACTION_STOP
                putExtra(EXTRA_TITLE_A, titleA)
                putExtra(EXTRA_TITLE_B, titleB)
                putExtra(EXTRA_IS_PLAYING, anyPlaying)
            }
            if (anyPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                try { context.stopService(Intent(context, AudioPlaybackService::class.java)) } catch (_: Exception) {}
            }
        }
    }
}
