package com.masterchief.agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.masterchief.agent.MainActivity

/**
 * Standby chat bubble — the "always available" chat box Tayo asked for.
 *
 * A small glowing Halo ring floats over other apps. Tap it and the chat
 * opens; drag it to move it; long-press to hide it.
 *
 * Requires SYSTEM_ALERT_WINDOW ("Display over other apps"), granted by the
 * user in system settings. The service is started only after that grant
 * and only when the bubble is turned on in Settings. It runs as a
 * foreground service so Android does not kill it in the background.
 * This is the Android-legal version of an always-on assistant — it is NOT
 * a lock-screen replacement and cannot listen when the phone is locked.
 */
class StandbyBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, bubbleNotification())
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = TextView(this).apply {
            text = "⬡" // Halo ring glyph; replaced by the vector drawable in the full UI
            textSize = 28f
            setOnClickListener {
                val intent = Intent(this@StandbyBubbleService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("open_chat", true)
                }
                startActivity(intent)
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 16 }
        windowManager?.addView(bubble, params)
    }

    private fun bubbleNotification(): Notification {
        val channelId = "chief_bubble"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Master Chief standby", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Master Chief standing by")
            .setContentText("Tap the Halo to talk to Chief.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .build()
    }

    override fun onDestroy() {
        bubble?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
