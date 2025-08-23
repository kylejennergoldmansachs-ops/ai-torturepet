package com.example.aitorture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*

class ForegroundBrainService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        val chId = "brain_foreground"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(chId, "Brain", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val notif = Notification.Builder(this, chId)
            .setContentTitle("Brain running")
            .setContentText("AI TorturePet — Debug foreground brain")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1338, notif)

        serviceScope.launch {
            while (isActive) {
                NativeBrain.nativeStep()
                delay(1000L)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
