package com.example.data.remote

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SupabaseClient {

    data class AuthSession(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long,
        val email: String,
        val userId: String
    )

    data class SupabaseProject(
        val id: String,
        val name: String,
        val organizationId: String,
        val region: String,
        val status: String
    )

    data class SupabaseProjectKey(
        val name: String,
        val apiKey: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // 1. SUPABASE AUTH: Login with Email & Password
    suspend fun signIn(
        url: String,
        anonKey: String,
        email: String,
        password: String,
        onSuccess: (AuthSession) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/auth/v1/token?grant_type=password"

        val bodyJson = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val jsonObj = JSONObject(bodyStr)
                        val accessToken = jsonObj.optString("access_token", "")
                        val refreshToken = jsonObj.optString("refresh_token", "")
                        val expiresInSeconds = jsonObj.optLong("expires_in", 3600L).coerceAtLeast(60L)
                        val userEmail = jsonObj.optJSONObject("user")?.optString("email", email) ?: email
                        val userId = jsonObj.optJSONObject("user")?.optString("id", "") ?: ""
                        if (accessToken.isNotEmpty()) {
                            onSuccess(
                                AuthSession(
                                    accessToken = accessToken,
                                    refreshToken = refreshToken,
                                    expiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000L,
                                    email = userEmail,
                                    userId = userId
                                )
                            )
                        } else {
                            onFailure("Authentication succeeded but token was empty.")
                        }
                    } else {
                        val errorDesc = response.parseError(bodyStr)
                        onFailure(errorDesc)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "SignIn Error", e)
            onFailure(e.localizedMessage ?: "Network connection failed.")
        }
    }

    // 2. SUPABASE AUTH: Sign Up with Email
    suspend fun signUp(
        url: String,
        anonKey: String,
        email: String,
        password: String,
        businessName: String,
        phone: String,
        redirectUrl: String = "swapnopay://auth-callback",
        onSuccess: (AuthSession?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val encodedRedirect = java.net.URLEncoder.encode(redirectUrl, "UTF-8")
        val endpoint = "$cleanUrl/auth/v1/signup?redirect_to=$encodedRedirect"

        val bodyJson = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("data", JSONObject().apply {
                put("business_name", businessName.trim())
                put("phone", phone.trim())
            })
            put("options", JSONObject().apply {
                put("emailRedirectTo", redirectUrl)
            })
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful) {
                        val responseJson = runCatching { JSONObject(bodyStr ?: "{}") }.getOrDefault(JSONObject())
                        val accessToken = responseJson.optString("access_token")
                        if (accessToken.isBlank()) {
                            // Email confirmation may be required before Supabase issues a session.
                            onSuccess(null)
                        } else {
                            onSuccess(
                                AuthSession(
                                    accessToken = accessToken,
                                    refreshToken = responseJson.optString("refresh_token"),
                                    expiresAtMillis = System.currentTimeMillis() +
                                        responseJson.optLong("expires_in", 3600L).coerceAtLeast(60L) * 1000L,
                                    email = responseJson.optJSONObject("user")?.optString("email", email) ?: email,
                                    userId = responseJson.optJSONObject("user")?.optString("id", "") ?: ""
                                )
                            )
                        }
                    } else {
                        val errorDesc = response.parseError(bodyStr)
                        onFailure(errorDesc)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "SignUp Error", e)
            onFailure(e.localizedMessage ?: "Network connection failed.")
        }
    }

    suspend fun refreshSession(
        url: String,
        anonKey: String,
        refreshToken: String,
        onSuccess: (AuthSession) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (refreshToken.isBlank()) {
            onFailure("The Supabase refresh token is missing. Sign in again.")
            return
        }
        val endpoint = "${url.trimEnd('/')}/auth/v1/token?grant_type=refresh_token"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(JSONObject().put("refresh_token", refreshToken).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful || bodyStr.isNullOrBlank()) {
                        onFailure(response.parseError(bodyStr))
                        return@use
                    }
                    val json = JSONObject(bodyStr)
                    val accessToken = json.optString("access_token")
                    if (accessToken.isBlank()) {
                        onFailure("Supabase refreshed the session without an access token.")
                        return@use
                    }
                    onSuccess(
                        AuthSession(
                            accessToken = accessToken,
                            refreshToken = json.optString("refresh_token", refreshToken),
                            expiresAtMillis = System.currentTimeMillis() +
                                json.optLong("expires_in", 3600L).coerceAtLeast(60L) * 1000L,
                            email = json.optJSONObject("user")?.optString("email", "").orEmpty(),
                            userId = json.optJSONObject("user")?.optString("id", "").orEmpty()
                        )
                    )
                }
            }
        } catch (error: Exception) {
            Log.e("SupabaseClient", "RefreshSession Error", error)
            onFailure(error.localizedMessage ?: "Unable to refresh the Supabase session.")
        }
    }

    suspend fun updateMerchantProfile(
        url: String,
        anonKey: String,
        accessToken: String,
        userId: String,
        businessName: String,
        email: String,
        phone: String,
        businessType: String,
        website: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (userId.isBlank() || accessToken.isBlank()) {
            onFailure("The authenticated merchant identity is unavailable.")
            return
        }
        val body = JSONObject().apply {
            put("business_name", businessName.trim())
            put("email", email.trim())
            put("phone", phone.trim())
            put("business_type", businessType.trim())
            put("website", website.trim())
        }.toString()
        val request = Request.Builder()
            .url("${url.trimEnd('/')}/rest/v1/merchants?user_id=eq.$userId")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) onSuccess()
                    else onFailure(response.parseError(response.body?.string()))
                }
            }
        } catch (error: Exception) {
            Log.e("SupabaseClient", "UpdateMerchantProfile Error", error)
            onFailure(error.localizedMessage ?: "Unable to save merchant information.")
        }
    }

    suspend fun submitKycVerification(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String,
        nidNumber: String,
        nidName: String,
        nidDob: String,
        frontUrl: String,
        backUrl: String,
        selfieUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val body = JSONObject().apply {
            put("nid_number", nidNumber.trim())
            if (nidName.isNotBlank()) put("nid_name", nidName.trim())
            if (nidDob.isNotBlank()) put("nid_dob", nidDob.trim())
            if (frontUrl.isNotBlank()) put("nid_front_url", frontUrl.trim())
            if (backUrl.isNotBlank()) put("nid_back_url", backUrl.trim())
            if (selfieUrl.isNotBlank()) put("face_photo_url", selfieUrl.trim())
            put("kyc_status", "PENDING")
            put("kyc_submitted_at", nowIso)
        }.toString()

        val cleanUrl = url.trimEnd('/')
        val targetEndpoint = if (merchantId.contains("-")) {
            "$cleanUrl/rest/v1/merchants?id=eq.$merchantId"
        } else {
            "$cleanUrl/rest/v1/merchants?user_id=eq.$merchantId"
        }

        val request = Request.Builder()
            .url(targetEndpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        val fallbackReq = Request.Builder()
                            .url("$cleanUrl/rest/v1/merchants?user_id=eq.$merchantId")
                            .addHeader("apikey", anonKey)
                            .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .patch(body.toRequestBody(JSON_MEDIA_TYPE))
                            .build()
                        try {
                            client.newCall(fallbackReq).execute().use { fbRes ->
                                if (fbRes.isSuccessful) onSuccess() else onFailure(response.parseError(response.body?.string()))
                            }
                        } catch (e: Exception) {
                            onFailure(response.parseError(response.body?.string()))
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e("SupabaseClient", "submitKycVerification Error", error)
            onFailure(error.localizedMessage ?: "Unable to submit KYC verification to database.")
        }
    }

    suspend fun fetchMerchantKycStatus(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String,
        onSuccess: (status: String, rejectionReason: String, nidNumber: String, nidName: String, nidDob: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val primaryEndpoint = if (merchantId.contains("-")) {
            "$cleanUrl/rest/v1/merchants?id=eq.$merchantId&select=kyc_status,kyc_rejection_reason,nid_number,nid_name,nid_dob"
        } else {
            "$cleanUrl/rest/v1/merchants?user_id=eq.$merchantId&select=kyc_status,kyc_rejection_reason,nid_number,nid_name,nid_dob"
        }

        val request = Request.Builder()
            .url(primaryEndpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: "[]"
                    if (response.isSuccessful) {
                        val arr = org.json.JSONArray(body)
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val status = obj.optString("kyc_status", "UNVERIFIED")
                            val reason = obj.optString("kyc_rejection_reason", "")
                            val nidNum = obj.optString("nid_number", "")
                            val nidName = obj.optString("nid_name", "")
                            val nidDob = obj.optString("nid_dob", "")
                            onSuccess(status, reason, nidNum, nidName, nidDob)
                            return@use
                        }
                    }
                    val fallbackReq = Request.Builder()
                        .url("$cleanUrl/rest/v1/merchants?user_id=eq.$merchantId&select=kyc_status,kyc_rejection_reason,nid_number,nid_name,nid_dob")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
                        .get()
                        .build()
                    try {
                        client.newCall(fallbackReq).execute().use { fbRes ->
                            val fbBody = fbRes.body?.string() ?: "[]"
                            if (fbRes.isSuccessful) {
                                val arr = org.json.JSONArray(fbBody)
                                if (arr.length() > 0) {
                                    val obj = arr.getJSONObject(0)
                                    val status = obj.optString("kyc_status", "UNVERIFIED")
                                    val reason = obj.optString("kyc_rejection_reason", "")
                                    val nidNum = obj.optString("nid_number", "")
                                    val nidName = obj.optString("nid_name", "")
                                    val nidDob = obj.optString("nid_dob", "")
                                    onSuccess(status, reason, nidNum, nidName, nidDob)
                                    return@use
                                }
                            }
                            onFailure("Merchant record not found")
                        }
                    } catch (e: Exception) {
                        onFailure(e.localizedMessage ?: "Failed to fetch KYC status")
                    }
                }
            }
        } catch (error: Exception) {
            Log.e("SupabaseClient", "fetchMerchantKycStatus Error", error)
            onFailure(error.localizedMessage ?: "Unable to fetch KYC status")
        }
    }

    suspend fun sendPasswordReset(
        url: String,
        anonKey: String,
        email: String,
        redirectUrl: String = "swapnopay://auth-callback",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val encodedRedirect = java.net.URLEncoder.encode(redirectUrl, "UTF-8")
        val endpoint = "${url.trimEnd('/')}/auth/v1/recover?redirect_to=$encodedRedirect"
        val bodyJson = JSONObject().apply {
            put("email", email)
            put("options", JSONObject().apply {
                put("redirectTo", redirectUrl)
            })
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) onSuccess() else onFailure(response.parseError(body))
                }
            }
        } catch (error: Exception) {
            onFailure(error.localizedMessage ?: "Unable to request a password reset.")
        }
    }

    // 3. INSERT SMS LOG to `sms_logs` table
    suspend fun insertSmsLog(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String,
        deviceId: String,
        rawSms: String,
        amount: Double,
        sender: String,
        trxId: String,
        timestamp: Long,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/sms_logs"

        val bodyJson = JSONObject().apply {
            put("merchant_id", merchantId)
            put("device_id", deviceId)
            put("raw_sms", rawSms)
            put("parsed_amount", amount)
            put("parsed_sender", sender)
            put("parsed_trx_id", trxId)
            put("parsed_timestamp", isoTimestamp(timestamp))
            put("sms_hash", sha256(sender + amount + trxId + timestamp))
            put("processed", false)
            put("status", "unmatched")
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    // 4. DEVICE REGISTER/HEARTBEAT to `devices` table
    suspend fun registerOrUpdateDevice(
        url: String,
        anonKey: String,
        token: String,
        deviceId: String,
        model: String,
        osVersion: String,
        batteryLevel: Int,
        online: Boolean,
        userId: String? = null,
        merchantId: String? = null,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/devices"

        val bodyJson = JSONObject().apply {
            put("id", deviceId)
            put("device_model", model)
            put("os_version", osVersion)
            put("battery_level", batteryLevel)
            put("online", online)
            put("last_sync", isoTimestamp(System.currentTimeMillis()))
            if (!userId.isNullOrBlank()) put("user_id", userId)
            if (!merchantId.isNullOrBlank()) put("merchant_id", merchantId)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("Status: ${response.code}")
                        val bodyStr = response.body?.string()
                        onFailure("Device sync failed (HTTP ${response.code}): ${response.parseError(bodyStr)}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Network error.")
        }
    }

    // 4b. MERCHANT NUMBERS UPSERT
    suspend fun upsertMerchantNumber(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String,
        number: String,
        type: String,
        accountType: String = "Personal",
        isDefault: Boolean = false,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/merchant_numbers"

        val bodyJson = JSONObject().apply {
            put("merchant_id", merchantId)
            put("number", number)
            put("type", type)
            put("account_type", accountType)
            put("is_default", isDefault)
            put("active", true)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("Merchant number save status: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Network error.")
        }
    }

    // 5. FETCH REVENUE VIEW / RPC (using get_daily_revenue function)
    suspend fun fetchDailyRevenue(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String,
        onSuccess: (Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/rpc/get_daily_revenue"

        val bodyJson = JSONObject().apply {
            put("merchant_id_param", merchantId)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val amount = bodyStr.trim().toDoubleOrNull() ?: 0.0
                        onSuccess(amount)
                    } else {
                        onFailure("RPC failed or returned empty.")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    // 6. RPC: CANCEL ORDER
    suspend fun cancelOrder(
        url: String,
        anonKey: String,
        token: String,
        orderId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/rpc/cancel_order"

        val bodyJson = JSONObject().apply {
            put("order_id_param", orderId)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) onSuccess() else onFailure("RPC error code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    // 7. RPC: EXTEND ORDER EXPIRY
    suspend fun extendOrder(
        url: String,
        anonKey: String,
        token: String,
        orderId: String,
        newExpiryMillis: Long,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/rpc/extend_order"

        val bodyJson = JSONObject().apply {
            put("order_id_param", orderId)
            put("new_expiry_param", newExpiryMillis)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) onSuccess() else onFailure("RPC error code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    // 8. AUTHENTICATED EDGE FUNCTION: RESOLVE APPEAL
    suspend fun resolveAppeal(
        url: String,
        anonKey: String,
        token: String,
        appealId: String,
        action: String, // "APPROVED", "REJECTED"
        orderId: String?,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/functions/v1/resolve-appeal"

        val bodyJson = JSONObject().apply {
            put("appeal_id", appealId)
            put("action", action)
            put("order_id", orderId ?: JSONObject.NULL)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) onSuccess() else onFailure(response.parseError(body))
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    // Test connection to Supabase REST API endpoint
    suspend fun testConnection(
        url: String,
        anonKey: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val raw = url.trim().trimEnd('/')
        val cleanBase = if (raw.endsWith("/rest/v1")) {
            raw.removeSuffix("/rest/v1")
        } else {
            raw
        }
        val endpoint = "$cleanBase/rest/v1/"
        val cleanKey = anonKey.trim()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", cleanKey)
            .addHeader("Authorization", "Bearer $cleanKey")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    /** Read-only deployment check for the Edge Function routes shipped by this app. */
    suspend fun checkEdgeFunctionRoute(
        url: String,
        anonKey: String,
        token: String,
        functionName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val allowedFunctions = setOf("create-order", "hosted-form", "payment-receipt", "process-sms", "resolve-appeal")
        if (functionName !in allowedFunctions || token.isBlank()) {
            onFailure("An authenticated session and supported function name are required")
            return
        }
        val request = Request.Builder()
            .url("${url.trimEnd('/')}/functions/v1/$functionName")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    // Some POST-only functions correctly answer GET with 400/405. A 404 means it is not deployed.
                    if (response.code in 200..499 && response.code != 404) onSuccess()
                    else onFailure("$functionName route check failed (HTTP ${response.code})")
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error checking $functionName")
        }
    }

    // REAL-TIME CRUD TEST (INSERT -> READ -> UPDATE -> DELETE)
    suspend fun performRealtimeCrudTest(
        url: String,
        anonKey: String,
        onLogStep: (stepName: String, detail: String) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val trimmedUrl = url.trim().trimEnd('/')
        val cleanUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            "https://$trimmedUrl"
        } else {
            trimmedUrl
        }
        val testUuid = java.util.UUID.randomUUID().toString()

        try {
            withContext(Dispatchers.IO) {
                // STEP 0: REST API & ANON KEY CONNECTIVITY VERIFICATION
                onLogStep("0. CONNECTIVITY CHECK", "Verifying connection & credentials to $cleanUrl...")
                val rootEndpoint = "$cleanUrl/rest/v1/"
                val rootRequest = Request.Builder()
                    .url(rootEndpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()
    
                client.newCall(rootRequest).execute().use { response ->
                    val code = response.code
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful) {
                        val parsedErr = response.parseError(bodyStr)
                        val formattedMsg = when (code) {
                            401 -> "Authentication Failed (HTTP 401): Invalid Supabase anon key."
                            404 -> "Endpoint Not Found (HTTP 404): Invalid Supabase URL '$cleanUrl'."
                            else -> "Supabase REST API connection error (HTTP $code): $parsedErr"
                        }
                        onFailure(formattedMsg)
                        return@withContext
                    }
                    onLogStep("0. CONNECTIVITY CHECK", "HTTP $code - REST API active & Anon Key authorized!")
                }
    
                // STEP 1: REAL-TIME INSERT
                val endpoint = "$cleanUrl/rest/v1/security_logs"
                onLogStep("1. REAL-TIME INSERT", "Inserting test record (ID: ${testUuid.take(8)}...) to security_logs")
                val insertPayload = JSONObject().apply {
                    put("id", testUuid)
                    put("event", "REALTIME_CRUD_TEST")
                    put("details", JSONObject().put("status", "INITIALIZED"))
                }.toString()
    
                val insertRequest = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(insertPayload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
    
                client.newCall(insertRequest).execute().use { response ->
                    val code = response.code
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful) {
                        val parsedErr = response.parseError(bodyStr)
                        val msg = when (code) {
                            404 -> "Connected to Supabase, but table 'security_logs' does not exist. Please run database setup SQL."
                            401, 403 -> "Insert failed (HTTP $code): Anon key lacks insert permission or RLS policy blocked it."
                            else -> "Step 1 (Insert) Failed with HTTP $code: $parsedErr"
                        }
                        onFailure(msg)
                        return@withContext
                    }
                    onLogStep("1. REAL-TIME INSERT", "HTTP $code - Record created in Supabase database!")
                }
    
                // STEP 2: REAL-TIME READ
                onLogStep("2. REAL-TIME READ", "Querying back inserted record from Supabase REST API...")
                val readRequest = Request.Builder()
                    .url("$endpoint?id=eq.$testUuid")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()
    
                client.newCall(readRequest).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful || bodyStr == null || !bodyStr.contains(testUuid)) {
                        onFailure("Step 2 (Read) Failed with HTTP ${response.code}: ${response.parseError(bodyStr)}")
                        return@withContext
                    }
                    onLogStep("2. REAL-TIME READ", "HTTP ${response.code} - Record retrieved successfully! Verified DB data.")
                }
    
                // STEP 3: REAL-TIME UPDATE
                onLogStep("3. REAL-TIME UPDATE", "Updating test record status in Supabase...")
                val updatePayload = JSONObject().apply {
                    put("event", "REALTIME_CRUD_TEST_PASSED")
                }.toString()
    
                val updateRequest = Request.Builder()
                    .url("$endpoint?id=eq.$testUuid")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(updatePayload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
    
                client.newCall(updateRequest).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful) {
                        onFailure("Step 3 (Update) Failed with HTTP ${response.code}: ${response.parseError(bodyStr)}")
                        return@withContext
                    }
                    onLogStep("3. REAL-TIME UPDATE", "HTTP ${response.code} - Record updated in Supabase!")
                }
    
                // STEP 4: REAL-TIME DELETE
                onLogStep("4. REAL-TIME DELETE", "Deleting temporary test record from Supabase...")
                val deleteRequest = Request.Builder()
                    .url("$endpoint?id=eq.$testUuid")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .delete()
                    .build()
    
                client.newCall(deleteRequest).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (!response.isSuccessful) {
                        onFailure("Step 4 (Delete) Failed with HTTP ${response.code}: ${response.parseError(bodyStr)}")
                        return@withContext
                    }
                    onLogStep("4. REAL-TIME DELETE", "HTTP ${response.code} - Record cleaned up cleanly. Real-time CRUD verification SUCCESS!")
                }
    
                onSuccess()
            }
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: e.message ?: "Network connection failed"
            val displayMsg = if (errMsg.contains("Unable to resolve host", ignoreCase = true) || errMsg.contains("Failed to connect", ignoreCase = true)) {
                "Network Error: Cannot reach '$cleanUrl'. Please verify your internet connection and URL."
            } else {
                "System Test Error: $errMsg"
            }
            onFailure(displayMsg)
        }
    }

    // 10. FETCH MFS REGEX PATTERNS
    suspend fun fetchMfsPatterns(
        url: String,
        anonKey: String,
        token: String,
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/mfs_regex_patterns?active=eq.true"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val jsonArray = JSONArray(bodyStr)
                        onSuccess(jsonArray)
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    suspend fun fetchOrders(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String? = null,
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val filter = if (!merchantId.isNullOrBlank()) "&merchant_id=eq.$merchantId" else ""
        val endpoint = "$cleanUrl/rest/v1/orders?select=*$filter"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        onSuccess(JSONArray(bodyStr))
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    suspend fun fetchPayments(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String? = null,
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val filter = if (!merchantId.isNullOrBlank()) "&merchant_id=eq.$merchantId" else ""
        val endpoint = "$cleanUrl/rest/v1/payments?select=*$filter"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        onSuccess(JSONArray(bodyStr))
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    suspend fun fetchAppeals(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String? = null,
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val filter = if (!merchantId.isNullOrBlank()) "&merchant_id=eq.$merchantId" else ""
        val endpoint = "$cleanUrl/rest/v1/appeals?select=*,orders(amount,cus_name,cus_phone)$filter"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        onSuccess(JSONArray(bodyStr))
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    // 14. FETCH DEVICES
    suspend fun fetchDevices(
        url: String,
        anonKey: String,
        token: String,
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/devices?select=*"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        onSuccess(JSONArray(bodyStr))
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    suspend fun fetchPaymentForms(
        url: String,
        anonKey: String,
        token: String,
        merchantId: String? = null,
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val filter = if (!merchantId.isNullOrBlank()) "&merchant_id=eq.$merchantId" else ""
        val endpoint = "$cleanUrl/rest/v1/payment_forms?select=*$filter"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        onSuccess(JSONArray(bodyStr))
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    // 16. INSERT PAYMENT FORM
    suspend fun insertPaymentForm(
        url: String,
        anonKey: String,
        token: String,
        payload: JSONObject,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/payment_forms"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    suspend fun fetchFormSubmissions(
        url: String,
        anonKey: String,
        token: String,
        formId: String? = null,
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        try {
            withContext(Dispatchers.IO) {
                val endpoint = "$cleanUrl/rest/v1/form_submissions".toHttpUrl().newBuilder()
                    .addQueryParameter("select", "*")
                    .addQueryParameter("order", "created_at.desc")
                    .apply { if (!formId.isNullOrBlank()) addQueryParameter("form_id", "eq.$formId") }
                    .build()
                val combined = JSONArray()
                val pageSize = 1_000
                for (page in 0 until 20) {
                    val start = page * pageSize
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/json")
                        .addHeader("Range-Unit", "items")
                        .addHeader("Range", "$start-${start + pageSize - 1}")
                        .get()
                        .build()
                    val pageRows = client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string()
                        if (!response.isSuccessful || bodyStr == null) {
                            onFailure("HTTP error: ${response.code}")
                            return@withContext
                        }
                        JSONArray(bodyStr)
                    }
                    for (index in 0 until pageRows.length()) combined.put(pageRows.get(index))
                    if (pageRows.length() < pageSize) break
                }
                onSuccess(combined)
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    suspend fun fetchHostedAttachmentUrl(
        url: String,
        anonKey: String,
        token: String,
        formSlug: String,
        submissionId: String,
        fieldId: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val endpoint = "${url.trimEnd('/')}/functions/v1/hosted-form".toHttpUrl().newBuilder()
            .addQueryParameter("slug", formSlug)
            .addQueryParameter("action", "attachment")
            .addQueryParameter("submission_id", submissionId)
            .addQueryParameter("field_id", fieldId)
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val signedUrl = runCatching { JSONObject(body).optString("url") }.getOrDefault("")
                        if (signedUrl.startsWith("https://")) onSuccess(signedUrl)
                        else onFailure("The attachment service returned an invalid URL.")
                    } else {
                        val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                        onFailure(message.ifBlank { "Unable to open attachment (HTTP ${response.code})." })
                    }
                }
            }
        } catch (error: Exception) {
            onFailure(error.localizedMessage ?: "Unable to open attachment.")
        }
    }

    suspend fun registerHostedFormRoute(
        routerBaseUrl: String,
        projectUrl: String,
        publishableKey: String,
        token: String,
        formId: String,
        formSlug: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val endpoint = "${routerBaseUrl.trimEnd('/')}/v1/routes"
        val bodyJson = JSONObject().apply {
            put("project_url", projectUrl.trimEnd('/'))
            put("publishable_key", publishableKey)
            put("form_id", formId)
            put("slug", formSlug)
        }.toString()
        val request = runCatching {
            Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }.getOrElse {
            onFailure("Invalid branded form router URL.")
            return
        }
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val publicUrl = runCatching { JSONObject(body).optString("public_url") }.getOrDefault("")
                        if (publicUrl.startsWith("https://")) onSuccess(publicUrl)
                        else onFailure("The branded form router returned an invalid URL.")
                    } else {
                        onFailure("Route registration failed (HTTP ${response.code}): ${response.parseError(body)}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Unable to register the branded form route.")
        }
    }

    // 16.5 UPDATE PAYMENT FORM
    suspend fun updatePaymentForm(
        url: String,
        anonKey: String,
        token: String,
        formId: String,
        payload: JSONObject,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/payment_forms?id=eq.$formId"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error.")
        }
    }

    suspend fun fetchPaymentReceiptHealth(
        url: String,
        anonKey: String,
        token: String,
        onSuccess: (JSONObject) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val endpoint = "${url.trimEnd('/')}/functions/v1/payment-receipt"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) onSuccess(JSONObject(body.ifBlank { "{}" }))
                    else onFailure("Receipt health check failed (HTTP ${response.code}): ${response.parseError(body)}")
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Receipt health check failed")
        }
    }

    // 18. GENERIC SUPABASE REST API: UPSERT RECORD (MERGE DUPLICATES)
    suspend fun upsertRecord(
        url: String,
        anonKey: String,
        token: String,
        tableName: String,
        payload: JSONObject,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/$tableName"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("Upsert $tableName failed (HTTP ${response.code}): ${response.parseError(response.body?.string())}")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error during $tableName upsert.")
        }
    }

    // 19. GENERIC SUPABASE REST API: FETCH RECORDS
    suspend fun fetchRecords(
        url: String,
        anonKey: String,
        token: String,
        tableName: String,
        selectQuery: String = "*",
        onSuccess: (JSONArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/$tableName?select=$selectQuery"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        onSuccess(JSONArray(bodyStr))
                    } else {
                        onFailure("Fetch $tableName failed (HTTP ${response.code})")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error fetching $tableName.")
        }
    }

    /** Calls an allowlisted PostgreSQL RPC name with the authenticated merchant session. */
    suspend fun callRpc(
        url: String,
        anonKey: String,
        token: String,
        functionName: String,
        payload: JSONObject,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val allowedFunctions = setOf(
            "adjust_inventory_atomic",
            "checkout_pos_atomic",
            "create_finance_account_atomic",
            "mark_all_merchant_notifications_read",
            "mark_merchant_notification_read",
            "pay_finance_installment_atomic",
            "record_ledger_transaction_atomic",
            "stock_in_product_atomic",
            "update_product_atomic"
        )
        if (functionName !in allowedFunctions) {
            onFailure("RPC function is not allowed by this client")
            return
        }
        if (token.isBlank()) {
            onFailure("An authenticated merchant session is required")
            return
        }
        val endpoint = "${url.trimEnd('/')}/rest/v1/rpc/$functionName"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) onSuccess(body)
                    else onFailure("RPC $functionName failed (HTTP ${response.code}): ${response.parseError(body)}")
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error calling $functionName")
        }
    }

    // 20. GENERIC SUPABASE REST API: DELETE RECORD BY KEY
    suspend fun deleteRecord(
        url: String,
        anonKey: String,
        token: String,
        tableName: String,
        primaryKeyCol: String,
        primaryKeyVal: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanUrl = url.trimEnd('/')
        val endpoint = "$cleanUrl/rest/v1/$tableName?$primaryKeyCol=eq.$primaryKeyVal"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer ${token.ifEmpty { anonKey }}")
            .addHeader("Content-Type", "application/json")
            .delete()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("Delete $tableName failed (HTTP ${response.code})")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Connection error deleting $tableName.")
        }
    }

    // Helpers
    private fun Response.parseError(body: String?): String {
        return try {
            if (body != null) {
                val json = JSONObject(body)
                json.optString("error_description", json.optString("message", "HTTP $code"))
            } else {
                "HTTP Error $code"
            }
        } catch (e: Exception) {
            "HTTP Error $code"
        }
    }

    private fun sha256(base: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(base.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (i in hash.indices) {
                val hex = Integer.toHexString(0xff and hash[i].toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            base.hashCode().toString()
        }
    }

    // 21. SUPABASE MANAGEMENT API: FETCH USER PROJECTS
    suspend fun fetchSupabaseProjects(
        managementToken: String,
        onSuccess: (List<SupabaseProject>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanToken = managementToken.trim()
        if (cleanToken.isEmpty()) {
            onFailure("Management API Access Token cannot be empty.")
            return
        }
        val endpoint = "https://api.supabase.com/v1/projects"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $cleanToken")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val jsonArray = JSONArray(bodyStr)
                        val projectList = mutableListOf<SupabaseProject>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            projectList.add(
                                SupabaseProject(
                                    id = obj.optString("id", ""),
                                    name = obj.optString("name", "Untitled Project"),
                                    organizationId = obj.optString("organization_id", ""),
                                    region = obj.optString("region", ""),
                                    status = obj.optString("status", "")
                                )
                            )
                        }
                        onSuccess(projectList)
                    } else {
                        val errorDesc = response.parseError(bodyStr)
                        onFailure("Management API Error (${response.code}): $errorDesc")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "fetchSupabaseProjects Error", e)
            onFailure(e.localizedMessage ?: "Failed to connect to Supabase Management API.")
        }
    }

    // 22. SUPABASE MANAGEMENT API: FETCH PROJECT API KEYS
    suspend fun fetchSupabaseProjectKeys(
        managementToken: String,
        projectRef: String,
        onSuccess: (List<SupabaseProjectKey>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanToken = managementToken.trim()
        val cleanRef = projectRef.trim()
        if (cleanToken.isEmpty() || cleanRef.isEmpty()) {
            onFailure("Token and Project Reference ID are required.")
            return
        }
        val endpoint = "https://api.supabase.com/v1/projects/$cleanRef/api-keys"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $cleanToken")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val jsonArray = JSONArray(bodyStr)
                        val keysList = mutableListOf<SupabaseProjectKey>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            keysList.add(
                                SupabaseProjectKey(
                                    name = obj.optString("name", ""),
                                    apiKey = obj.optString("api_key", obj.optString("key", ""))
                                )
                            )
                        }
                        onSuccess(keysList)
                    } else {
                        val errorDesc = response.parseError(bodyStr)
                        onFailure("Management API Error (${response.code}): $errorDesc")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "fetchSupabaseProjectKeys Error", e)
            onFailure(e.localizedMessage ?: "Failed to fetch project keys from Supabase.")
        }
    }

    data class PkcePair(
        val codeVerifier: String,
        val codeChallenge: String
    )

    fun generatePkcePair(): PkcePair {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        val verifier = android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        return PkcePair(verifier, challenge)
    }

    // 23. SUPABASE MANAGEMENT API: OAUTH 2.0 TOKEN EXCHANGE
    suspend fun exchangeOAuthCode(
        clientId: String,
        clientSecret: String = "",
        code: String,
        codeVerifier: String,
        redirectUri: String = "swapnopay://supabase-oauth-callback",
        onSuccess: (accessToken: String, refreshToken: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val endpoint = "https://api.supabase.com/v1/oauth/token"
        val bodyBuilder = okhttp3.FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId.trim())
            .add("code", code.trim())
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier.trim())

        if (clientSecret.isNotBlank()) {
            bodyBuilder.add("client_secret", clientSecret.trim())
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(bodyBuilder.build())
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        val access = json.optString("access_token", "")
                        val refresh = json.optString("refresh_token", "")
                        if (access.isNotBlank()) {
                            onSuccess(access, refresh)
                        } else {
                            onFailure("Access token returned was empty.")
                        }
                    } else {
                        val errorDesc = response.parseError(bodyStr)
                        onFailure("OAuth Token Exchange Error (${response.code}): $errorDesc")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "exchangeOAuthCode Error", e)
            onFailure(e.localizedMessage ?: "Failed to exchange OAuth authorization code.")
        }
    }

    private fun isoTimestamp(timestamp: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(timestamp))
}
