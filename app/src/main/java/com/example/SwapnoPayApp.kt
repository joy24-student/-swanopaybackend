package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

class SwapnoPayApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            // 1. Create system notification channels early so background services & receivers can post immediately
            NotificationHelper.initNotificationChannels(this)
        } catch (e: Exception) {
            Log.e("SwapnoPayApp", "Failed to initialize notification channels: ${e.message}", e)
        }

        try {
            // 2. Enable FCM auto-init for push notifications
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
        } catch (e: Exception) {
            Log.w("SwapnoPayApp", "FCM auto-init not available: ${e.message}")
        }
    }
}
