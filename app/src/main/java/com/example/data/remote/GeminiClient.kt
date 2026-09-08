package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    val GEMINI_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-3-flash-preview",
        "gemini-3.1-flash-lite",
        "gemma-4-auto"
    )

    suspend fun getChatCompletion(
        apiKey: String,
        model: String = "gemini-2.5-flash",
        messages: JSONArray,
        onSuccess: (response: String) -> Unit,
        onFailure: (error: String) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onFailure("No Google AI Studio API key configured. Please add a key in Settings.")
            return
        }

        val modelUrl = "$GEMINI_URL$model:generateContent"

        val geminiMessages = JSONArray()
        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            val role = msg.optString("role", "user")
            val content = msg.optString("content", "")
            
            val geminiMsg = JSONObject().apply {
                put("role", if (role == "assistant") "model" else "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", content) })
                })
            }
            geminiMessages.put(geminiMsg)
        }

        val payload = JSONObject().apply {
            put("contents", geminiMessages)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 2048)
            })
        }

        val request = Request.Builder()
            .url(modelUrl)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    val code = response.code
    
                    if (response.isSuccessful && bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        val candidates = json.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val contentObj = candidate.optJSONObject("content")
                            val parts = contentObj?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text", "")
                                if (text.isNotEmpty()) {
                                    onSuccess(text)
                                    return@withContext
                                }
                            }
                        }
                        onFailure("Response was successful but content was empty.")
                    } else {
                        val errorDetail = bodyStr ?: "HTTP error $code"
                        onFailure("Model $model failed: Code $code - $errorDetail")
                    }
                }
            }
        } catch (e: Exception) {
            onFailure("Model $model network exception: ${e.localizedMessage ?: "timeout"}")
        }
    }
}