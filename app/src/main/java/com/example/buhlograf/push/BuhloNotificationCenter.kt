package com.example.buhlograf.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.buhlograf.MainActivity
import com.example.buhlograf.R

class BuhloNotificationCenter(private val context: Context) {
    fun show(channel: Channel, idKey: String, title: String, body: String, targetScreen: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager, channel)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_screen", targetScreen)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            idKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(idKey.hashCode(), notification)
    }

    private fun ensureChannel(manager: NotificationManager, channel: Channel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    enum class Channel(val id: String, val title: String) {
        Friends("friends", "Друзья"),
        Catalog("catalog", "Ассортимент"),
        Admin("admin", "Админка")
    }
}
