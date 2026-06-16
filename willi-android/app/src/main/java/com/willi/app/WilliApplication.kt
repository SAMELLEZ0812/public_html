package com.willi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.willi.app.data.database.WilliDatabase
import com.willi.app.ai.WilliCore

class WilliApplication : Application() {

    companion object {
        lateinit var instance: WilliApplication
            private set
        const val CHANNEL_WAKE = "willi_wake_channel"
        const val CHANNEL_NOTIF = "willi_notif_channel"
    }

    lateinit var database: WilliDatabase
    lateinit var williCore: WilliCore

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = WilliDatabase.getInstance(this)
        williCore = WilliCore(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val wakeChannel = NotificationChannel(
                CHANNEL_WAKE,
                "WILLI Écoute Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WILLI écoute le mot de réveil"
                setShowBadge(false)
            }

            val notifChannel = NotificationChannel(
                CHANNEL_NOTIF,
                "Notifications WILLI",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications de WILLI"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(wakeChannel, notifChannel))
        }
    }
}
