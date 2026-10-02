package com.example.data.remote

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Native Gemini Multimodal Live API (BidiGenerateContent) WebSocket engine.
 * Streams real-time 16kHz 16-bit PCM audio from the microphone to Gemini Live
 * and plays back 24kHz 16-bit PCM neural audio directly from Gemini Live with
 * full-duplex barge-in (interruption) and live function calling support.
 */
class GeminiLiveSessionManager {

    enum class LiveConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED_LISTENING,
        AI_SPEAKING,
        ERROR
    }

    data class LiveVoiceOption(
        val id: String,
        val labelBn: String,
        val descBn: String
    )

    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000

        val VOICE_OPTIONS = listOf(
            LiveVoiceOption("Aoede", "অইডি (Aoede)", "আন্তরিক ও উষ্ণ কণ্ঠ"),
            LiveVoiceOption("Puck", "পাক (Puck)", "বন্ধুসুলভ ও প্রাণবন্ত কণ্ঠ"),
            LiveVoiceOption("Kore", "কোর (Kore)", "শান্ত ও স্পষ্ট কণ্ঠ"),
            LiveVoiceOption("Fenrir", "ফেনরির (Fenrir)", "দৃঢ় ও চটপটে কণ্ঠ"),
            LiveVoiceOption("Charon", "শ্যারন (Charon)", "গম্ভীর ও পেশাদার কণ্ঠ")
        )

        private val CANDIDATE_ENDPOINTS = listOf(
            "v1alpha" to "models/gemini-2.0-flash-exp",
            "v1beta" to "models/gemini-2.0-flash-live-001",
            "v1alpha" to "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "v1beta" to "models/gemini-live-2.5-flash-preview"
        )
    }

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket stays open indefinitely
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var micJob: Job? = null
    private var playbackJob: Job? = null
    private var audioOutChannel = Channel<ByteArray>(Channel.UNLIMITED)

    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    private val isSessionActive = AtomicBoolean(false)
    private val isSetupCompleted = AtomicBoolean(false)
    private var currentCandidateIndex = 0
    private var lastApiKey: String = ""
    private var lastSystemInstruction: String = ""
    private var toolExecutor: ((String, JSONObject, (String) -> Unit) -> Unit)? = null

    private val _connectionState = MutableStateFlow(LiveConnectionState.IDLE)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _statusText = MutableStateFlow("প্রস্তুত")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _inputAudioLevel = MutableStateFlow(0f)
    val inputAudioLevel: StateFlow<Float> = _inputAudioLevel.asStateFlow()

    private val _outputAudioLevel = MutableStateFlow(0f)
    val outputAudioLevel: StateFlow<Float> = _outputAudioLevel.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow("Aoede")
    val selectedVoiceName: StateFlow<String> = _selectedVoiceName.asStateFlow()

    private val _liveActionLogs = MutableStateFlow<List<String>>(emptyList())
    val liveActionLogs: StateFlow<List<String>> = _liveActionLogs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setVoiceName(voiceName: String) {
        _selectedVoiceName.value = voiceName
    }

    fun toggleMicMute() {
        val next = !_isMicMuted.value
        _isMicMuted.value = next
        if (next) {
            _inputAudioLevel.value = 0f
            _statusText.value = "মিউট"
        } else if (_connectionState.value == LiveConnectionState.CONNECTED_LISTENING) {
            _statusText.value = "শুনছি..."
        }
    }

    fun startSession(
        apiKey: String,
        systemInstruction: String,
        onExecuteTool: (functionName: String, args: JSONObject, onResult: (String) -> Unit) -> Unit
    ) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            _connectionState.value = LiveConnectionState.ERROR
            _errorMessage.value = "Google AI Studio API Key প্রয়োজন"
            _statusText.value = "API Key প্রয়োজন"
            return
        }

        stopSession(resetLogs = true)
        lastApiKey = cleanKey
        lastSystemInstruction = systemInstruction
        toolExecutor = onExecuteTool
        currentCandidateIndex = 0
        isSessionActive.set(true)
        _errorMessage.value = null
        connectWithCandidate(0)
    }

    private fun connectWithCandidate(index: Int) {
        if (!isSessionActive.get()) return
        if (index >= CANDIDATE_ENDPOINTS.size) {
            _connectionState.value = LiveConnectionState.ERROR
            _errorMessage.value = "সংযোগ করা যায়নি। API Key ও ইন্টারনেট যাচাই করুন।"
            _statusText.value = "সংযোগ ব্যর্থ"
            isSessionActive.set(false)
            return
        }

        currentCandidateIndex = index
        isSetupCompleted.set(false)
        _connectionState.value = LiveConnectionState.CONNECTING
        _statusText.value = "সংযোগ হচ্ছে..."

        val (apiVersion, modelName) = CANDIDATE_ENDPOINTS[index]
        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$apiVersion.GenerativeService.BidiGenerateContent?key=$lastApiKey"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket opened ($apiVersion / $modelName)")
                sendSetupMessage(webSocket, modelName, lastSystemInstruction)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerJson(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleServerJson(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Gemini Live WebSocket closing ($code): $reason")
                webSocket.close(code, reason)
                if (isSessionActive.get() && !isSetupCompleted.get()) {
                    scope.launch {
                        delay(250)
                        connectWithCandidate(index + 1)
                    }
                } else if (isSessionActive.get()) {
                    _connectionState.value = LiveConnectionState.IDLE
                    _statusText.value = "কল সমাপ্ত"
                    stopAudioPipeline()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket failure on $modelName: ${t.message}")
                if (isSessionActive.get() && !isSetupCompleted.get() && index + 1 < CANDIDATE_ENDPOINTS.size) {
                    scope.launch {
                        delay(300)
                        connectWithCandidate(index + 1)
                    }
                } else if (isSessionActive.get()) {
                    _connectionState.value = LiveConnectionState.ERROR
                    _errorMessage.value = "সংযোগ বিচ্ছিন্ন: ${t.localizedMessage ?: "নেটওয়ার্ক ত্রুটি"}"
                    _statusText.value = "সংযোগ বিচ্ছিন্ন"
                    stopAudioPipeline()
                }
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket, modelName: String, systemPrompt: String) {
        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", modelName)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", _selectedVoiceName.value)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemPrompt)
                    }))
                })
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", buildToolDeclarations())
                }))
            })
        }
        ws.send(setupPayload.toString())
    }

    private fun buildToolDeclarations(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply {
                put("name", "add_customer")
                put("description", "নতুন কাস্টমার বা খরিদ্দার খাতায় যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().put("type", "STRING").put("description", "কাস্টমারের নাম"))
                        put("phone", JSONObject().put("type", "STRING").put("description", "১১ ডিজিটের মোবাইল নম্বর"))
                        put("initial_balance", JSONObject().put("type", "NUMBER").put("description", "পূর্বের বকেয়া বা ব্যালেন্স"))
                        put("address", JSONObject().put("type", "STRING").put("description", "ঠিকানা"))
                    })
                    put("required", JSONArray().put("name"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_supplier")
                put("description", "নতুন মহাজন বা সাপ্লায়ার খাতায় যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().put("type", "STRING").put("description", "মহাজন বা কোম্পানির নাম"))
                        put("phone", JSONObject().put("type", "STRING").put("description", "মোবাইল নম্বর"))
                        put("address", JSONObject().put("type", "STRING").put("description", "ঠিকানা"))
                        put("initial_balance", JSONObject().put("type", "NUMBER").put("description", "পূর্বের পাওনা বা ব্যালেন্স"))
                    })
                    put("required", JSONArray().put("name"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_product")
                put("description", "দোকানের ক্যাটালগে নতুন পণ্য বা মাল যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().put("type", "STRING").put("description", "পণ্যের নাম"))
                        put("sale_price", JSONObject().put("type", "NUMBER").put("description", "বিক্রয় মূল্য"))
                        put("purchase_price", JSONObject().put("type", "NUMBER").put("description", "কেনা মূল্য"))
                        put("stock", JSONObject().put("type", "NUMBER").put("description", "মালের স্টক বা সংখ্যা"))
                        put("unit", JSONObject().put("type", "STRING").put("description", "পরিমাপের একক যেমন pcs, kg, liter"))
                        put("category", JSONObject().put("type", "STRING").put("description", "পণ্যের ক্যাটাগরি"))
                    })
                    put("required", JSONArray().put("name").put("sale_price"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_inventory")
                put("description", "পণ্যের স্টক বৃদ্ধি বা মাল ইনভেন্টরিতে যোগ (restock) করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("product_name", JSONObject().put("type", "STRING").put("description", "পণ্যের নাম"))
                        put("quantity", JSONObject().put("type", "NUMBER").put("description", "মালের পরিমাণ"))
                        put("type", JSONObject().put("type", "STRING").put("description", "in অথবা out"))
                        put("price", JSONObject().put("type", "NUMBER").put("description", "দর বা কেনা দাম"))
                    })
                    put("required", JSONArray().put("product_name").put("quantity"))
                })
            })
            put(JSONObject().apply {
                put("name", "complete_sale")
                put("description", "দোকানের নতুন বিক্রয় বা POS সেল সম্পন্ন করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("product_name", JSONObject().put("type", "STRING").put("description", "পণ্যের নাম"))
                        put("amount", JSONObject().put("type", "NUMBER").put("description", "মোট বিক্রয় মূল্য বা টাকা"))
                        put("quantity", JSONObject().put("type", "NUMBER").put("description", "পরিমাণ"))
                        put("customer_name", JSONObject().put("type", "STRING").put("description", "কাস্টমারের নাম"))
                        put("customer_phone", JSONObject().put("type", "STRING").put("description", "কাস্টমারের ফোন"))
                        put("payment_type", JSONObject().put("type", "STRING").put("description", "Cash, MFS, অথবা Due"))
                        put("discount", JSONObject().put("type", "NUMBER").put("description", "ছাড়ের টাকা"))
                    })
                    put("required", JSONArray().put("product_name").put("amount"))
                })
            })
            put(JSONObject().apply {
                put("name", "send_sms")
                put("description", "কাস্টমারকে বকেয়া তাগাদা (due reminder), অফার (offer) বা বার্তা এসএমএস পাঠানোর ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("phone", JSONObject().put("type", "STRING").put("description", "মোবাইল নম্বর"))
                        put("customer_name", JSONObject().put("type", "STRING").put("description", "কাস্টমারের নাম"))
                        put("message", JSONObject().put("type", "STRING").put("description", "এসএমএস এর মূল বার্তা"))
                        put("type", JSONObject().put("type", "STRING").put("description", "due, offer, অথবা individual"))
                        put("amount", JSONObject().put("type", "NUMBER").put("description", "বকেয়ার পরিমাণ"))
                    })
                    put("required", JSONArray().put("phone"))
                })
            })
            put(JSONObject().apply {
                put("name", "resolve_appeal")
                put("description", "পেমেন্ট ভেরিফিকেশন আপিল বিশ্লেষণ করে অনুমোদন (approve) বা বাতিল (reject) করে গ্রাহককে ইমেইল পাঠানোর ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("appeal_id", JSONObject().put("type", "STRING").put("description", "আপিল আইডি বা ট্রানজেকশন আইডি"))
                        put("status", JSONObject().put("type", "STRING").put("description", "APPROVED অথবা REJECTED"))
                        put("note", JSONObject().put("type", "STRING").put("description", "সিদ্ধান্তের কারণ বা বিবরণ"))
                        put("send_email", JSONObject().put("type", "BOOLEAN").put("description", "গ্রাহককে ইমেইল পাঠানো"))
                    })
                    put("required", JSONArray().put("status"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_customer_credit")
                put("description", "কাস্টমারের খাতায় বাকি বা বকেয়া (credit/due) যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("customer_name", JSONObject().put("type", "STRING").put("description", "কাস্টমারের নাম"))
                        put("amount", JSONObject().put("type", "NUMBER").put("description", "বাকি টাকার পরিমাণ"))
                        put("product_name", JSONObject().put("type", "STRING").put("description", "পণ্যের নাম (ঐচ্ছিক)"))
                        put("note", JSONObject().put("type", "STRING").put("description", "বিবরণ বা নোট"))
                    })
                    put("required", JSONArray().put("customer_name").put("amount"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_customer_payment")
                put("description", "কাস্টমারের কাছ থেকে বকেয়া আদায় বা জমা (payment received) খাতায় যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("customer_name", JSONObject().put("type", "STRING").put("description", "কাস্টমারের নাম"))
                        put("amount", JSONObject().put("type", "NUMBER").put("description", "জমা টাকার পরিমাণ"))
                        put("note", JSONObject().put("type", "STRING").put("description", "বিবরণ বা নোট"))
                    })
                    put("required", JSONArray().put("customer_name").put("amount"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_supplier_credit")
                put("description", "মহাজন বা সাপ্লায়ারের কাছ থেকে বাকি কেনাকাটা (due purchase) যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("supplier_name", JSONObject().put("type", "STRING").put("description", "মহাজনের নাম"))
                        put("amount", JSONObject().put("type", "NUMBER").put("description", "টাকার পরিমাণ"))
                        put("note", JSONObject().put("type", "STRING").put("description", "বিবরণ বা নোট"))
                    })
                    put("required", JSONArray().put("supplier_name").put("amount"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_supplier_payment")
                put("description", "মহাজন বা সাপ্লায়ারকে বাকি টাকা পরিশোধ বা জমা (payment paid) যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("supplier_name", JSONObject().put("type", "STRING").put("description", "মহাজনের নাম"))
                        put("amount", JSONObject().put("type", "NUMBER").put("description", "টাকার পরিমাণ"))
                        put("note", JSONObject().put("type", "STRING").put("description", "বিবরণ বা নোট"))
                    })
                    put("required", JSONArray().put("supplier_name").put("amount"))
                })
            })
            put(JSONObject().apply {
                put("name", "add_expense")
                put("description", "দোকানের যেকোনো খরচ (যেমন চা-নাস্তা, ভাড়া, বিদ্যুৎ বিল, পরিবহন) খাতায় যোগ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("expense_category", JSONObject().put("type", "STRING").put("description", "খরচের খাত (যেমন: নাস্তা, ভাড়া, বিদ্যুৎ বিল, অন্যান্য)"))
                        put("amount", JSONObject().put("type", "NUMBER").put("description", "খরচের টাকার পরিমাণ"))
                        put("description", JSONObject().put("type", "STRING").put("description", "খরচের বিবরণ"))
                    })
                    put("required", JSONArray().put("expense_category").put("amount"))
                })
            })
            put(JSONObject().apply {
                put("name", "save_ai_memory")
                put("description", "ব্যবসায়ী কোনো নিয়ম, তথ্য বা নির্দেশনা মনে রাখতে বললে তা দীর্ঘমেয়াদী মেমোরিতে সংরক্ষণ করার ফাংশন।")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("memory_fact", JSONObject().put("type", "STRING").put("description", "যে তথ্য বা নিয়মটি মনে রাখতে হবে"))
                    })
                    put("required", JSONArray().put("memory_fact"))
                })
            })
        }
    }

    private fun handleServerJson(rawJson: String) {
        try {
            val json = JSONObject(rawJson)

            if (json.has("setupComplete")) {
                isSetupCompleted.set(true)
                _connectionState.value = LiveConnectionState.CONNECTED_LISTENING
                _statusText.value = "শুনছি..."
                startAudioPipeline()
                sendInitialGreetingTrigger()
                return
            }

            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    flushAudioOutput()
                    _connectionState.value = LiveConnectionState.CONNECTED_LISTENING
                    _statusText.value = "শুনছি..."
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                        if (inlineData != null) {
                            val b64 = inlineData.optString("data", "")
                            if (b64.isNotEmpty()) {
                                val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                                if (pcmBytes.isNotEmpty()) {
                                    _connectionState.value = LiveConnectionState.AI_SPEAKING
                                    _statusText.value = "কথা বলছে..."
                                    audioOutChannel.trySend(pcmBytes)
                                }
                            }
                        }
                    }
                }

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    // Send a marker empty array so playback coroutine knows when turn audio finishes
                    audioOutChannel.trySend(ByteArray(0))
                }
            }

            val toolCall = json.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    handleFunctionCalls(functionCalls)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini Live message: ${e.message}")
        }
    }

    private fun sendInitialGreetingTrigger() {
        val greetingTrigger = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", "আসসালামু আলাইকুম! সংক্ষেপে ১ বাক্যে সালাম দিয়ে জিজ্ঞেস করো আজ ব্যবসায় কীভাবে সাহায্য করতে পারো।")
                    }))
                }))
                put("turnComplete", true)
            })
        }
        webSocket?.send(greetingTrigger.toString())
    }

    private fun handleFunctionCalls(functionCalls: JSONArray) {
        val executor = toolExecutor ?: return
        for (i in 0 until functionCalls.length()) {
            val callObj = functionCalls.getJSONObject(i)
            val callId = callObj.optString("id", "")
            val fnName = callObj.optString("name", "")
            val args = callObj.optJSONObject("args") ?: JSONObject()

            executor(fnName, args) { resultMessage ->
                val updatedLogs = (_liveActionLogs.value + resultMessage).takeLast(6)
                _liveActionLogs.value = updatedLogs

                val toolResponse = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", JSONArray().put(JSONObject().apply {
                            put("id", callId)
                            put("name", fnName)
                            put("response", JSONObject().apply {
                                put("result", JSONObject().apply {
                                    put("status", "success")
                                    put("message", resultMessage)
                                })
                            })
                        }))
                    })
                }
                webSocket?.send(toolResponse.toString())
            }
        }
    }

    /**
     * Allows sending a live image frame (e.g. receipt/voucher/product photo) directly into the active Gemini Live conversation.
     */
    fun sendRealtimeImage(jpegBase64: String) {
        if (!isSessionActive.get() || !isSetupCompleted.get() || jpegBase64.isBlank()) return
        val frameMsg = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", jpegBase64)
                }))
            })
        }
        webSocket?.send(frameMsg.toString())
        val updatedLogs = (_liveActionLogs.value + "📸 ছবি পাঠানো হয়েছে").takeLast(6)
        _liveActionLogs.value = updatedLogs
    }

    @SuppressLint("MissingPermission")
    private fun startAudioPipeline() {
        stopAudioPipeline()
        audioOutChannel = Channel(Channel.UNLIMITED)

        // 1. Start 24kHz PCM16 Output AudioTrack on USAGE_MEDIA for loud main speaker output
        val outMinBuf = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(outMinBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            audioTrack?.setVolume(AudioTrack.getMaxVolume())
        } catch (_: Exception) {}
        audioTrack?.play()

        playbackJob = scope.launch {
            for (chunk in audioOutChannel) {
                if (!isActive || !isSessionActive.get()) break
                if (chunk.isEmpty()) {
                    // Turn completed marker
                    _outputAudioLevel.value = 0f
                    if (_connectionState.value == LiveConnectionState.AI_SPEAKING) {
                        _connectionState.value = LiveConnectionState.CONNECTED_LISTENING
                        _statusText.value = if (_isMicMuted.value) "মিউট" else "শুনছি..."
                    }
                    continue
                }
                val boostedChunk = amplifyPcm16(chunk, 2.5f)
                _outputAudioLevel.value = calculatePcmRmsLevel(boostedChunk, boostedChunk.size)
                try {
                    audioTrack?.write(boostedChunk, 0, boostedChunk.size)
                } catch (e: Exception) {
                    Log.w(TAG, "AudioTrack write error: ${e.message}")
                }
            }
        }

        // 2. Start 16kHz PCM16 Input AudioRecord with hardware echo cancellation
        val inMinBuf = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        try {
            var recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                inMinBuf * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                try { recorder.release() } catch (_: Exception) {}
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    inMinBuf * 2
                )
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                _errorMessage.value = "মাইক্রোফোন চালু করা যায়নি। পারমিশন চেক করুন।"
                return
            }

            val sessionId = recorder.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }

            audioRecord = recorder
            recorder.startRecording()

            micJob = scope.launch {
                val buffer = ByteArray(3200) // 100ms of 16kHz 16-bit mono audio
                while (isActive && isSessionActive.get()) {
                    val readBytes = recorder.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        if (_isMicMuted.value) {
                            _inputAudioLevel.value = 0f
                            continue
                        }
                        val level = calculatePcmRmsLevel(buffer, readBytes)
                        _inputAudioLevel.value = level

                        val chunkBytes = if (readBytes == buffer.size) buffer else buffer.copyOf(readBytes)
                        val b64Audio = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)

                        val realtimePayload = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("mediaChunks", JSONArray().put(JSONObject().apply {
                                    put("mimeType", "audio/pcm;rate=16000")
                                    put("data", b64Audio)
                                }))
                            })
                        }
                        webSocket?.send(realtimePayload.toString())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord initialization failed: ${e.message}")
            _errorMessage.value = "মাইক্রোফোন ত্রুটি: ${e.localizedMessage}"
        }
    }

    private fun amplifyPcm16(bytes: ByteArray, gain: Float): ByteArray {
        if (bytes.size < 2) return bytes
        val out = ByteArray(bytes.size)
        var i = 0
        while (i + 1 < bytes.size) {
            val low = bytes[i].toInt() and 0xFF
            val high = bytes[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val boosted = (sample * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = (boosted and 0xFF).toByte()
            out[i + 1] = ((boosted shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    private fun flushAudioOutput() {
        try {
            while (audioOutChannel.tryReceive().isSuccess) {
                // Drain queued audio chunks on user barge-in
            }
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
            _outputAudioLevel.value = 0f
        } catch (e: Exception) {
            Log.w(TAG, "Flush audio track warning: ${e.message}")
        }
    }

    private fun calculatePcmRmsLevel(bytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSquares = 0.0
        val sampleCount = length / 2
        var i = 0
        while (i + 1 < length) {
            val low = bytes[i].toInt() and 0xFF
            val high = bytes[i + 1].toInt()
            val sample = (high shl 8) or low
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
            i += 2
        }
        val rms = sqrt(sumSquares / sampleCount).toFloat()
        return (rms * 3.8f).coerceIn(0f, 1f)
    }

    private fun stopAudioPipeline() {
        micJob?.cancel()
        micJob = null
        playbackJob?.cancel()
        playbackJob = null
        audioOutChannel.close()

        try {
            aec?.release()
            ns?.release()
            agc?.release()
        } catch (_: Exception) {}
        aec = null
        ns = null
        agc = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        _inputAudioLevel.value = 0f
        _outputAudioLevel.value = 0f
    }

    fun stopSession(resetLogs: Boolean = false) {
        isSessionActive.set(false)
        isSetupCompleted.set(false)
        stopAudioPipeline()
        try {
            webSocket?.close(1000, "User ended live session")
        } catch (_: Exception) {}
        webSocket = null
        _connectionState.value = LiveConnectionState.IDLE
        _statusText.value = "প্রস্তুত"
        _isMicMuted.value = false
        if (resetLogs) {
            _liveActionLogs.value = emptyList()
        }
    }
}
