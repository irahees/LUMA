package com.glasswave.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Small foreground playback service so local music can continue while LUMA is in the background.
 * Playback is intentionally limited to user-accessible local audio URIs passed by the app.
 */
class LocalPlaybackService : Service() {
    companion object {
        const val ACTION_PLAY = "com.glasswave.music.action.PLAY"
        const val ACTION_PAUSE = "com.glasswave.music.action.PAUSE"
        const val ACTION_RESUME = "com.glasswave.music.action.RESUME"
        const val EXTRA_URI = "track_uri"
        const val EXTRA_TITLE = "track_title"
        const val EXTRA_ARTIST = "track_artist"
        private const val CHANNEL_ID = "luma_playback"
        private const val NOTIFICATION_ID = 41
    }

    private var player: MediaPlayer? = null
    private var title: String = "Ready to play"
    private var artist: String = "LUMA Music Player"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "LUMA playback", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Playback status for music playing in LUMA"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown track"
                artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown artist"
                startForeground(NOTIFICATION_ID, buildNotification())
                val uriText = intent.getStringExtra(EXTRA_URI)
                if (uriText.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                try {
                    player?.release()
                    player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        val sourceUri = Uri.parse(uriText)
                        if (sourceUri.scheme.equals("http", ignoreCase = true) ||
                            sourceUri.scheme.equals("https", ignoreCase = true)
                        ) {
                            // Network streams must use MediaPlayer's URL overload.
                            setDataSource(uriText)
                        } else {
                            // Local content/file URIs are resolved by Android's ContentResolver.
                            setDataSource(this@LocalPlaybackService, sourceUri)
                        }
                        setOnPreparedListener { it.start() }
                        setOnErrorListener { _, _, _ ->
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            true
                        }
                        prepareAsync()
                    }
                } catch (_: Exception) {
                    player?.release()
                    player = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
            ACTION_PAUSE -> {
                if (player?.isPlaying == true) player?.pause()
                if (player != null) startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_RESUME -> {
                try {
                    if (player != null) {
                        startForeground(NOTIFICATION_ID, buildNotification())
                        player?.start()
                    }
                } catch (_: Exception) { }
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(player?.isPlaying == true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
