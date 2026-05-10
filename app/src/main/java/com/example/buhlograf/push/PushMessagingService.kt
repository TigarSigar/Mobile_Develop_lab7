package com.example.buhlograf.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.buhlograf.BuhlografApplication
import com.example.buhlograf.MainActivity
import com.example.buhlograf.R
import com.example.buhlograf.data.FcmTokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        FcmTokenStore.save(this, token)

        val app = application as? BuhlografApplication ?: return
        val session = app.authService.getSavedSession() ?: return
        app.cloudSyncService.updateFcmToken(session.userId, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Бухлограф"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Пришло новое событие"
        showNotification(title, body, message.data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = when (data["screen"]) {
            "friends" -> "friends"
            "catalog" -> "catalog"
            "admin" -> "admin"
            else -> CHANNEL_ID
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(
                NotificationChannel("friends", "Друзья", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel("catalog", "Ассортимент", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel("admin", "Админка", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_ID, "Buhlograf push", NotificationManager.IMPORTANCE_DEFAULT)
            ).forEach(manager::createNotificationChannel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_screen", data["screen"] ?: "dashboard")
            putExtra("friend_id", data["friend_id"])
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            7002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val TAG = "PushMessagingService"
        const val CHANNEL_ID = "buhlograf_push"
    }
}
