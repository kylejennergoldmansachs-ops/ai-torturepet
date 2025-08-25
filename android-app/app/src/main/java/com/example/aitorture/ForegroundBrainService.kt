package com.example.aitorture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import androidx.core.app.NotificationCompat

class ForegroundBrainService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()

        val chId = "brain_foreground"
        // Create channel on O+ (safe no-op on older versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(chId, "Brain", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }

        // Use NotificationCompat for backward compatibility
        val notif = NotificationCompat.Builder(this, chId)
            .setContentTitle("Brain running")
            .setContentText("AI TorturePet — Debug foreground brain")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Start foreground (we declared the permission in Manifest)
        startForeground(1338, notif)

        serviceScope.launch {
            while (isActive) {
                try {
                    NativeBrain.nativeStep()
                } catch (t: Throwable) {
                    // safe guard to avoid silent coroutine crash
                }
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
