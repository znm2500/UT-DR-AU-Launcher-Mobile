package com.au.launcher.service

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
import com.au.launcher.MainActivity
import com.au.launcher.R
import com.au.launcher.utils.DownloadManager
import kotlinx.coroutines.*

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var downloadManager: DownloadManager
    
    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_FILE_NAME = "EXTRA_FILE_NAME"
    }

    override fun onCreate() {
        super.onCreate()
        downloadManager = com.au.launcher.utils.DownloadManager.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: return START_NOT_STICKY
            val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
            val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: return START_NOT_STICKY

            startForeground(NOTIFICATION_ID, createNotification(gameId, 0))
            
            serviceScope.launch {
                observeDownloadState(gameId)
                downloadManager.startDownload(gameId, url, fileName)
            }
        }
        return START_NOT_STICKY
    }

    private fun observeDownloadState(gameId: String) {
        serviceScope.launch {
            downloadManager.downloadStates.collect { states ->
                val state = states[gameId]
                if (state != null) {
                    when {
                        state.isDownloading -> {
                            updateNotification(gameId, state.progress)
                        }
                        state.isPaused -> {
                            updateNotification(gameId, state.progress, isPaused = true)
                            stopForeground(STOP_FOREGROUND_DETACH)
                        }
                        state.error != null -> {
                            showFinishedNotification(gameId, false)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                        state.progress >= 100 -> {
                            showFinishedNotification(gameId, true)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.download_notification_channel)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(gameId: String, progress: Int, isPaused: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isPaused) getString(R.string.pause) else getString(R.string.downloading_game, gameId)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setProgress(100, progress, false)
            .setOngoing(!isPaused)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(gameId: String, progress: Int, isPaused: Boolean = false) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(gameId, progress, isPaused))
    }

    private fun showFinishedNotification(gameId: String, success: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val title = if (success) getString(R.string.download_complete) else getString(R.string.download_failed)
        val text = if (success) getString(R.string.click_to_install, gameId) else ""
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
