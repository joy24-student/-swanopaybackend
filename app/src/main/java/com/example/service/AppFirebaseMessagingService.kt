package com.example.service

import android.util.Log
import com.example.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Service to receive Firebase Cloud Messaging (FCM) push notifications
 * when the app is in background, foreground, or closed.
 */
class AppFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AppFCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token generated: $token")
        try {
            val prefs = getSharedPreferences("swapnopay_fcm_prefs", MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist refreshed FCM token: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val type = (data["type"] ?: data["category"] ?: "ADMIN").uppercase()
        val title = data["title"] ?: notification?.title ?: "SwapnoPay Notice"
        val body = data["body"] ?: data["message"] ?: notification?.body ?: ""
        val uniqueId = data["id"] ?: data["batch_id"] ?: data["trx_id"] ?: data["submission_id"] ?: "fcm_${System.currentTimeMillis()}"

        when (type) {
            "PAYMENT", "TRANSACTION", "MFS_PAYMENT", "ORDER_PAYMENT" -> {
                val amount = data["amount"]?.toDoubleOrNull() ?: 0.0
                val method = data["method"] ?: data["payment_method"] ?: "Payment"
                val sender = data["sender"] ?: data["sender_number"] ?: ""
                val trxId = data["trx_id"] ?: uniqueId
                NotificationHelper.showPaymentNotification(
                    context = applicationContext,
                    amount = amount,
                    method = method,
                    sender = sender,
                    trxId = trxId
                )
            }
            "FORM", "FORM_SUBMISSION", "FORM_FILLUP" -> {
                val formTitle = data["form_title"] ?: data["form_name"] ?: title
                val customer = data["customer_info"] ?: data["customer_name"] ?: body
                val subId = data["submission_id"] ?: uniqueId
                NotificationHelper.showFormSubmissionNotification(
                    context = applicationContext,
                    formTitle = formTitle,
                    customerInfo = customer,
                    submissionId = subId
                )
            }
            else -> {
                // Admin Announcements, System Notices, Broadcasts
                val severity = data["severity"] ?: "INFO"
                NotificationHelper.showAdminNotification(
                    context = applicationContext,
                    title = title,
                    message = body,
                    noticeId = uniqueId,
                    severity = severity
                )
            }
        }
    }
}
