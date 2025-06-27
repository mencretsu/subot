package com.example.ngontol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

fun Context.ensureBotChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            "BOT_CH",
            "Ngontol Service",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(ch)
    }
}
