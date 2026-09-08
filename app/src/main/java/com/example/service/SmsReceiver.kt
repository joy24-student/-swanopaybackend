package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Static manifest BroadcastReceiver to reliably process transaction SMS messages
 * even when SmsMonitoringService is stopped, killed by Doze mode, or during low-memory conditions.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        val repository = AppRepository(context.applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: continue
                    val body = msg.messageBody ?: continue

                    Log.d("SmsReceiver", "Static receiver intercepted SMS from: $sender")
                    repository.processIncomingSms(sender, body)
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing incoming SMS via static receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
