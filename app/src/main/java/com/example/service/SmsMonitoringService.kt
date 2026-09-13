package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class SmsMonitoringService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: AppRepository
    private var smsReceiver: BroadcastReceiver? = null

    companion object {
        const val CHANNEL_ID = "SmsMonitoringChannel"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground()

        repository = AppRepository(applicationContext)
        startSmsReceiver()
        repository.startSmsQueueAutoRetry(scope)
        startHeartbeatLoop()
        Log.d("SmsMonitoringService", "Service created, foreground notification active, and SMS monitoring initialized.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return START_STICKY
    }

    private fun promoteToForeground() {
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                androidx.core.app.ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("SmsMonitoringService", "Failed to promote service to foreground: ${e.message}", e)
            try {
                stopSelf()
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSmsReceiver() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECEIVE_SMS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("SmsMonitoringService", "RECEIVE_SMS permission not granted; receiver registration aborted.")
            return
        }

        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    for (msg in messages) {
                        val sender = msg.originatingAddress ?: continue
                        val body = msg.messageBody ?: continue
                        
                        // Transaction messages contain financial and personal data;
                        // never include their sender or body in application logs.
                        Log.d("SmsMonitoringService", "Incoming SMS queued for local validation.")
                        
                        scope.launch {
                            repository.processIncomingSms(sender, body)
                        }
                    }
                }
            }
        }
        
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                smsReceiver,
                IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.e("SmsMonitoringService", "Failed to register dynamic SMS receiver: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active background receiver for mobile banking SMS transactions"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SwapnoPay Merchant SMS Active")
            .setContentText("Automated bKash, Nagad, and Rocket monitoring is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startHeartbeatLoop() {
        scope.launch {
            // Immediate registration upon service boot
            try {
                repository.sendDeviceHeartbeat(applicationContext)
            } catch (e: Exception) {
                Log.w("SmsMonitoringService", "Initial device heartbeat error: ${e.message}")
            }

            // Periodic heartbeat every 60 seconds to keep device status active in backend
            while (isActive) {
                delay(60_000L)
                try {
                    val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    val batteryPct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

                    repository.sendDeviceHeartbeat(applicationContext, batteryPct)
                } catch (e: Exception) {
                    Log.w("SmsMonitoringService", "Periodic device heartbeat error: ${e.message}")
                }
            }
        }
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        Log.w("SmsMonitoringService", "Foreground service dataSync timeout reached; stopping gracefully.")
        stopSelf(startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        smsReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w("SmsMonitoringService", "Error unregistering receiver: ${e.message}")
            }
        }
        job.cancel()
        Log.d("SmsMonitoringService", "Service stopped and SMS receiver unregistered.")
    }
}
