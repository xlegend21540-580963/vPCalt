package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class ContainerSessionService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val gameTitle = intent?.getStringExtra(EXTRA_TITLE) ?: "Windows Session"
    val containerName = intent?.getStringExtra(EXTRA_CONTAINER) ?: "Default Container"
    ensureChannel()

    val openIntent = Intent(this, MainActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    val pendingOpen = PendingIntent.getActivity(
      this,
      0,
      openIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle(getString(R.string.notification_session_title))
      .setContentText(getString(R.string.notification_session_body, gameTitle, containerName))
      .setOngoing(true)
      .setContentIntent(pendingOpen)
      .build()

    startForeground(NOTIFICATION_ID, notification)
    return START_STICKY
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notification_channel_session_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = getString(R.string.notification_channel_session_desc)
      }
      manager.createNotificationChannel(channel)
    }
  }

  companion object {
    const val CHANNEL_ID = "vortexpc_container_session"
    const val NOTIFICATION_ID = 4101
    const val ACTION_START = "com.example.service.START_SESSION"
    const val ACTION_STOP = "com.example.service.STOP_SESSION"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_CONTAINER = "extra_container"

    fun start(context: Context, title: String, containerName: String) {
      val intent = Intent(context, ContainerSessionService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_CONTAINER, containerName)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, ContainerSessionService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }
}
