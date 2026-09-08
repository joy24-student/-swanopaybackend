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

object OpenRouterClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

    val FREE_MODELS = listOf(
        "openrouter/free",
        "google/gemini-2.5-flash",
        "meta-llama/llama-3.1-8b-instruct:free",
        "qwen/qwen-2.5-72b-instruct:free",
        "microsoft/phi-3-medium-128k-instruct:free",
        "meta-llama/llama-3-8b-instruct:free"
    )

    suspend fun getChatCompletion(
        keysCsv: String,
        messages: JSONArray,
        model: String = "openrouter/free",
        onSuccess: (response: String) -> Unit,
        onFailure: (error: String) -> Unit
    ) {
        val rawKeys = keysCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawKeys.isEmpty()) {
            onFailure("No OpenRouter API keys configured. Please add keys in Settings.")
            return
        }

        // We will try models in rotation: the requested model first, then fall back to other free models
        val modelsToTry = mutableListOf(model)
        FREE_MODELS.forEach { freeModel ->
            if (!modelsToTry.contains(freeModel)) {
                modelsToTry.add(freeModel)
            }
        }

        var lastError = "Unknown error"
        
        // Loop through keys and models
        for (currentKey in rawKeys) {
            for (currentModel in modelsToTry) {
                Log.d("OpenRouterClient", "Attempting request with model $currentModel")

                val payload = JSONObject().apply {
                    put("model", currentModel)
                    put("messages", messages)
                    put("temperature", 0.3)
                }

                val request = Request.Builder()
                    .url(OPENROUTER_URL)
                    .addHeader("Authorization", "Bearer $currentKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://swapnopay.org")
                    .addHeader("X-Title", "SwapnoPay Smart Ledger")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                try {
                    val contentResult = withContext(Dispatchers.IO) {
                        client.newCall(request).execute().use { response ->
                            val bodyStr = response.body?.string()
                            val code = response.code
                            
                            if (response.isSuccessful && bodyStr != null) {
                                val json = JSONObject(bodyStr)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val messageObj = choice.optJSONObject("message")
                                    val content = messageObj?.optString("content", "") ?: ""
                                    if (content.isNotEmpty()) {
                                        return@use content
                                    }
                                }
                                lastError = "Response was successful but content was empty."
                            } else {
                                val errorDetail = bodyStr ?: "HTTP error $code"
                                lastError = "Model $currentModel failed: Code $code - $errorDetail"
                                Log.w("OpenRouterClient", "Call failed for model $currentModel: $errorDetail")
                            }
                            null
                        }
                    }
                    if (contentResult != null) {
                        onSuccess(contentResult)
                        return
                    }
                } catch (e: Exception) {
                    lastError = "Model $currentModel network exception: ${e.localizedMessage ?: "timeout"}"
                    Log.e("OpenRouterClient", "Exception during OpenRouter call for model $currentModel", e)
                }
            }
        }

        // If we reach here, all combinations failed
        onFailure("All API keys and free models failed. Last error: $lastError")
    }
}
