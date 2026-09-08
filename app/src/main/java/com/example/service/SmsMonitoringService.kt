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
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        repository = AppRepository(applicationContext)
        startSmsReceiver()
        repository.startSmsQueueAutoRetry(scope)
        startHeartbeatLoop()
        Log.d("SmsMonitoringService", "Service created, foreground notification active, and SMS monitoring initialized.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
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
        
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            smsReceiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
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
                    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 100
                    val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: 100
                    val batteryPct = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else 100

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
            unregisterReceiver(it)
        }
        job.cancel()
        Log.d("SmsMonitoringService", "Service stopped and SMS receiver unregistered.")
    }
}
