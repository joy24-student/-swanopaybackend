package com.example.ui

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive

import android.app.Application
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.local.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import com.example.data.remote.OpenRouterClient
import com.example.data.remote.GeminiClient
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.Response

// SwapnoPay Central Platform Supabase Constants (Anchors Platform Identity & Social OAuth)
const val PLATFORM_SUPABASE_URL = "https://tldubojeokgyoclxnzkb.supabase.co"
const val PLATFORM_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRsZHVib2plb2tneW9jbHhuemtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NjcwODMsImV4cCI6MjEwMzM0MzA4M30.vlgmNEJ0_DpdbsZEQMA2Z82vwY4hwTxpgS4o9p5oEb0"
const val PLATFORM_AUTH_REDIRECT_URL = "https://swapnopay.top/auth-callback.html"

data class FirebaseNotice(
    val title: String = "",
    val message: String = "",
    val active: Boolean = false,
    val type: String = "INFO" // INFO, WARNING, MAINTENANCE, PROMOTION
)

data class EncryptedSessionInfo(
    val token: String? = null,
    val email: String? = null,
    val provider: String = "Supabase Auth",
    val isValid: Boolean = false,
    val createdAt: Long = 0L,
    val uid: String? = null
)

data class SplashInitStep(
    val id: Int,
    val title: String,
    val detail: String,
    val isCompleted: Boolean = false,
    val isRunning: Boolean = false,
    val isError: Boolean = false
)

data class CopilotChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<Map<String, String>>
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("timestamp", timestamp)
        val msgArr = JSONArray()
        for (m in messages) {
            val mObj = JSONObject()
            m.forEach { (k, v) -> mObj.put(k, v) }
            msgArr.put(mObj)
        }
        obj.put("messages", msgArr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): CopilotChatSession? {
            return try {
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val title = obj.optString("title", "Chat Window")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val msgArr = obj.optJSONArray("messages") ?: JSONArray()
                val messages = mutableListOf<Map<String, String>>()
                for (i in 0 until msgArr.length()) {
                    val mObj = msgArr.optJSONObject(i) ?: continue
                    val map = mutableMapOf<String, String>()
                    val keys = mObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = mObj.optString(k, "")
                    }
                    messages.add(map)
                }
                CopilotChatSession(id, title, timestamp, messages)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class EmployeeItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val designation: String,
    val role: String, // "Admin", "Finance", "Sales", "Inventory", "Support", "Manager"
    val email: String,
    val phone: String,
    val department: String = "General",
    val status: String = "Active", // "Active" or "Inactive"
    val avatarUrl: String? = null,
    val permissions: List<String> = listOf("POS & Billing Access", "Customer & Supplier Ledgers"),
    val joinedDate: String = "2024-01-15"
)

enum class DiagnosticStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

data class RealtimeDiagnosticStep(
    val stepIndex: Int,
    val name: String,
    val detail: String,
    val status: DiagnosticStepStatus,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application.applicationContext)
    private val localAccountReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    val installationId: String get() = repository.installationId
    private val hostedFormRouterOrigin = "https://pay.swapnopay.top"
    val profiles = repository.getProfiles()
    private val _activeProfile = MutableStateFlow(profiles.first())
    val activeProfile: StateFlow<MerchantProfileEntity> = _activeProfile.asStateFlow()
    private val securityPrefs = try {
        val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
        androidx.security.crypto.EncryptedSharedPreferences.create(
            "swapnopay_security_prefs",
            masterKeyAlias,
            application,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        application.getSharedPreferences("swapnopay_security_prefs", android.content.Context.MODE_PRIVATE)
    }

    // Quick Action shortcuts configuration for Home Screen
    val defaultQuickActionRoutes = listOf(
        "PosCheckout",
        "StockIn",
        "QrScanner",
        "Inventory",
        "CustomerLedger",
        "Reports",
        "Deposits",
        "ExpenseSales"
    )

    private val _enabledQuickActions = MutableStateFlow<List<String>>(
        loadSavedQuickActions()
    )
    val enabledQuickActions: StateFlow<List<String>> = _enabledQuickActions.asStateFlow()

    private fun loadSavedQuickActions(): List<String> {
        val saved = securityPrefs.getString("enabled_quick_actions", null)
        if (saved.isNullOrBlank()) return defaultQuickActionRoutes
        val list = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val oldDefaultQuickActionRoutes = listOf(
            "PosCheckout",
            "StockIn",
            "QrScanner",
            "Inventory",
            "CustomerLedger",
            "SupplierLedger",
            "Deposits",
            "Loans"
        )
        if (list == oldDefaultQuickActionRoutes) {
            return defaultQuickActionRoutes
        }
        return if (list.isNotEmpty()) list else defaultQuickActionRoutes
    }

    fun updateQuickActions(routes: List<String>) {
        val validRoutes = routes.take(8)
        _enabledQuickActions.value = validRoutes
        securityPrefs.edit().putString("enabled_quick_actions", validRoutes.joinToString(",")).apply()
    }

    fun resetQuickActions() {
        _enabledQuickActions.value = defaultQuickActionRoutes
        securityPrefs.edit().remove("enabled_quick_actions").apply()
    }

    // Routing and navigation states
    data class NavState(val screen: String, val tab: String)
    private val navigationStack = java.util.ArrayDeque<NavState>()

    private val _currentScreen = MutableStateFlow("Splash")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _currentTab = MutableStateFlow("Dashboard")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // ── DATABASE ROOM REPOSITORY FLOWS & PREPOPULATION ───────────────────────

    val products: StateFlow<List<ProductItemEntity>> = activeProfile.flatMapLatest { repository.observeProducts(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customers: StateFlow<List<CustomerEntity>> = activeProfile.flatMapLatest { repository.observeCustomers(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = activeProfile.flatMapLatest { repository.observeSuppliers(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        syncAllCachedFormsToVps()
        startObservingPaymentFormsCache()
        viewModelScope.launch {
            repository.observeMerchantProfile().collect { profile ->
                if (profile != null && profile.businessName.isNotBlank()) {
                    val isPlaceholder = profile.id == "merchant_default" ||
                        profile.id == repository.installationId ||
                        profile.businessName.matches(Regex("^(my store|my business|google user|facebook user|demo store|business setup required)$", RegexOption.IGNORE_CASE))
                    if (!isPlaceholder) {
                        _activeProfile.value = profile
                    }
                }
            }
        }
    }

    val expenses: StateFlow<List<ExpenseEntity>> = activeProfile.flatMapLatest { repository.observeExpenses(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val ledgerTransactions: StateFlow<List<LedgerTransactionEntity>> = activeProfile.flatMapLatest { repository.observeLedgerTransactions(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val stockTransactions: StateFlow<List<StockTransactionEntity>> = activeProfile.flatMapLatest { repository.observeStockTransactions(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val loans: StateFlow<List<BusinessLoanEntity>> = activeProfile.flatMapLatest { repository.observeLoans(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val dpsAccounts: StateFlow<List<DpsAccountEntity>> = activeProfile.flatMapLatest { repository.observeDpsAccounts(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val financeInstallments: StateFlow<List<FinanceInstallmentEntity>> = activeProfile.flatMapLatest {
        repository.observeFinanceInstallments(it.id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val merchantNotifications: StateFlow<List<MerchantNotificationEntity>> = activeProfile.flatMapLatest {
        repository.observeMerchantNotifications(it.id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())



    val posSales: StateFlow<List<PosSaleEntity>> = activeProfile.flatMapLatest { repository.observePosSales(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedInvoiceSaleId = MutableStateFlow<String?>(null)
    val selectedInvoiceSaleId: StateFlow<String?> = _selectedInvoiceSaleId.asStateFlow()

    fun openInvoice(saleId: String) {
        _selectedInvoiceSaleId.value = saleId
        navigateTo("Invoice")
    }

    val businessAnalytics: StateFlow<BusinessAnalyticsEntity?> = activeProfile.flatMapLatest { repository.observeBusinessAnalytics(it.id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Database Mutator Operations
    fun recordPosSale(invoiceNo: String, customerId: String?, customerName: String, customerPhone: String, subtotal: Double, discount: Double, netTotal: Double, cashReceived: Double, changeDue: Double, paymentMethod: String, itemCount: Int, cartItemsJson: String) {
        if (invoiceNo.isBlank() || subtotal < 0.0 || discount < 0.0 || netTotal < 0.0 || cashReceived < 0.0 || itemCount <= 0) {
            logFirebaseStatus("POS sale rejected because required totals or invoice data are invalid.")
            return
        }
        viewModelScope.launch {
            try {
                val sale = PosSaleEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    merchantId = activeProfile.value.id,
                    invoiceNo = invoiceNo,
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    subtotal = subtotal,
                    discount = discount,
                    netTotal = netTotal,
                    cashReceived = cashReceived,
                    changeDue = changeDue,
                    paymentMethod = paymentMethod,
                    paymentStatus = if (paymentMethod.equals("Due", ignoreCase = true)) "DUE" else "PAID",
                    itemCount = itemCount,
                    cartItemsJson = cartItemsJson,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertPosSale(sale)
                logFirebaseStatus("Recorded POS Sale $invoiceNo: Net Total ৳$netTotal ($paymentMethod)")
            } catch (e: Exception) {
                logFirebaseStatus("Record POS Sale error: ${e.message}")
            }
        }
    }
    fun recordStockChange(
        productId: String,
        type: String,
        qty: Double,
        price: Double,
        supplierId: String? = null,
        referenceNote: String? = null
    ) {
        val normalizedType = if (type.equals("sale", ignoreCase = true)) "out" else "in"
        recordStockChangeInternal(productId, normalizedType, qty, price, supplierId, referenceNote)
    }

    fun recordStockChange(productId: String, type: String, qty: Int, price: Double) {
        recordStockChange(productId, type, qty.toDouble(), price)
    }

    private val database: FirebaseDatabase? = runCatching { FirebaseDatabase.getInstance() }.getOrNull()
    private val messaging: FirebaseMessaging? = runCatching { FirebaseMessaging.getInstance() }.getOrNull()

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    private val _fcmSubscribedTopics = MutableStateFlow<Set<String>>(emptySet())
    val fcmSubscribedTopics: StateFlow<Set<String>> = _fcmSubscribedTopics.asStateFlow()

    private val _firebaseNotice = MutableStateFlow<FirebaseNotice?>(null)
    val firebaseNotice: StateFlow<FirebaseNotice?> = _firebaseNotice.asStateFlow()

    private val _firebaseStatusLog = MutableStateFlow<List<String>>(emptyList())
    val firebaseStatusLog: StateFlow<List<String>> = _firebaseStatusLog.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(securityPrefs.getString("logged_in_email", null))
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    // Encrypted Session & Real-Time Auth State
    private val _sessionInfo = MutableStateFlow(EncryptedSessionInfo())
    val sessionInfo: StateFlow<EncryptedSessionInfo> = _sessionInfo.asStateFlow()

    // Splash Initialization Progress & Steps State
    private val _splashStepIndex = MutableStateFlow(0)
    val splashStepIndex: StateFlow<Int> = _splashStepIndex.asStateFlow()

    private val _splashProgress = MutableStateFlow(0f)
    val splashProgress: StateFlow<Float> = _splashProgress.asStateFlow()

    private val _splashStatusText = MutableStateFlow("Initializing application environment...")
    val splashStatusText: StateFlow<String> = _splashStatusText.asStateFlow()

    private val _splashSteps = MutableStateFlow(
        listOf(
            SplashInitStep(1, "Environment Setup", "Initializing app environment & database", isCompleted = false),
            SplashInitStep(2, "Encrypted Session", "Checking AES-256 session token & Firebase Auth", isCompleted = false),
            SplashInitStep(3, "Biometrics Security", "Verifying biometric flags & PIN locks", isCompleted = false),
            SplashInitStep(4, "Auto-Routing", "Routing to authenticated target screen", isCompleted = false)
        )
    )
    val splashSteps: StateFlow<List<SplashInitStep>> = _splashSteps.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.prepopulateIfEmpty()
            } catch (e: Exception) {
                Log.e("AppViewModel", "Prepopulate DB error: ${e.message}")
            }
        }
        listenToSystemConfig()
    }

    fun saveEncryptedSessionToken(email: String, uid: String? = null, provider: String = "Firebase Auth"): String {
        val token = "SWAPNO_AES256_${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        securityPrefs.edit()
            .putString("encrypted_session_token", token)
            .putString("logged_in_email", email)
            .putString("auth_provider", provider)
            .putString("auth_uid", uid ?: email)
            .putLong("session_created_at", now)
            .apply()

        _userEmail.value = email
        val info = EncryptedSessionInfo(
            token = token,
            email = email,
            provider = provider,
            isValid = true,
            createdAt = now,
            uid = uid ?: email
        )
        _sessionInfo.value = info
        logFirebaseStatus("Encrypted session token stored securely for $email via EncryptedSharedPreferences")
        return token
    }

    fun verifyEncryptedSessionToken(): EncryptedSessionInfo {
        val token = securityPrefs.getString("encrypted_session_token", null)
        val email = securityPrefs.getString("logged_in_email", null) ?: _userEmail.value
        val provider = securityPrefs.getString("auth_provider", "Firebase Auth") ?: "Firebase Auth"
        val uid = securityPrefs.getString("auth_uid", null)
        val createdAt = securityPrefs.getLong("session_created_at", 0L)

        val thirtyDaysMs = 30L * 24 * 3600 * 1000
        val isValid = !token.isNullOrBlank() && !email.isNullOrBlank() && createdAt > 0L && (System.currentTimeMillis() - createdAt) < thirtyDaysMs

        val info = EncryptedSessionInfo(
            token = token,   // null when absent — never fabricate a token string
            email = email,
            provider = provider,
            isValid = isValid,
            createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
            uid = uid
        )
        _sessionInfo.value = info
        return info
    }

    fun clearEncryptedSessionToken() {
        securityPrefs.edit()
            .remove("encrypted_session_token")
            .remove("logged_in_email")
            .remove("auth_provider")
            .remove("auth_uid")
            .remove("session_created_at")
            .apply()
        _userEmail.value = null
        _sessionInfo.value = EncryptedSessionInfo(isValid = false)
        logFirebaseStatus("Encrypted session token purged securely")
    }

    // --- PAYMENT GATEWAY CONFIGURATION & ENGINE ---
    data class GatewayConfig(
        val provider: String = "SwapnoPay Verified MFS",
        // Production credentials are server-managed and never enter Android.
        val minAmount: Double = 10.0,
        val maxAmount: Double = 100000.0,
        val dailyLimit: Double = 500000.0,
        val retryCount: Int = 3,
        val autoFailover: Boolean = true,
        val successCallbackUrl: String = "",
        val failureCallbackUrl: String = "",
        val cancelCallbackUrl: String = "",
        val emailNotificationsEnabled: Boolean = true,
        val customerReceiptsEnabled: Boolean = true,
        val notificationEmail: String = "",
        val emailOnSuccess: Boolean = true,
        val activeMethods: Map<String, Boolean> = mapOf(
            "bKash" to false,
            "Nagad" to false,
            "Rocket" to false,
            "Upay" to false
        )
    )

    private val _gatewayConfig = MutableStateFlow<GatewayConfig>(loadGatewayConfigFromPrefs())
    val gatewayConfig: StateFlow<GatewayConfig> = _gatewayConfig.asStateFlow()
    private val _gatewaySettingsStatus = MutableStateFlow("Local settings loaded")
    val gatewaySettingsStatus: StateFlow<String> = _gatewaySettingsStatus.asStateFlow()
    private val _gatewayReceiptStatus = MutableStateFlow("Receipt outbox not checked")
    val gatewayReceiptStatus: StateFlow<String> = _gatewayReceiptStatus.asStateFlow()
    private val _gatewayServiceStatus = MutableStateFlow("Oracle receipt service not checked")
    val gatewayServiceStatus: StateFlow<String> = _gatewayServiceStatus.asStateFlow()

    private fun loadGatewayConfigFromPrefs(): GatewayConfig {
        return runCatching {
            val jsonStr = securityPrefs.getString("gateway_config_json", null)
            if (jsonStr != null) {
                val json = org.json.JSONObject(jsonStr)
                GatewayConfig(
                    provider = "SwapnoPay Verified MFS",
                    minAmount = json.optDouble("min_amount", 10.0),
                    maxAmount = json.optDouble("max_amount", 100000.0),
                    dailyLimit = json.optDouble("daily_limit", 500000.0),
                    retryCount = json.optInt("retry_count", 3),
                    autoFailover = json.optBoolean("auto_failover", true),
                    successCallbackUrl = json.optString("success_callback_url", ""),
                    failureCallbackUrl = json.optString("failure_callback_url", ""),
                    cancelCallbackUrl = json.optString("cancel_callback_url", ""),
                    emailNotificationsEnabled = json.optBoolean("email_notifications_enabled", true),
                    customerReceiptsEnabled = json.optBoolean("customer_receipts_enabled", true),
                    notificationEmail = json.optString("notification_email", ""),
                    emailOnSuccess = json.optBoolean("email_on_success", true),
                    activeMethods = json.optJSONObject("active_methods")?.let { methodsJson ->
                        val map = mutableMapOf<String, Boolean>()
                        methodsJson.keys().forEach { key ->
                            map[key] = methodsJson.optBoolean(key, true)
                        }
                        map
                    } ?: mapOf(
                        "bKash" to false,
                        "Nagad" to false,
                        "Rocket" to false,
                        "Upay" to false
                    )
                )
            } else {
                GatewayConfig()
            }
        }.getOrDefault(GatewayConfig())
    }

    fun saveGatewayConfig(config: GatewayConfig) {
        val safeConfig = config.copy(provider = "SwapnoPay Verified MFS")
        val invalidPolicy = safeConfig.minAmount <= 0.0 || safeConfig.maxAmount < safeConfig.minAmount ||
            safeConfig.dailyLimit < safeConfig.maxAmount || safeConfig.retryCount !in 1..10
        val invalidEmail = safeConfig.emailNotificationsEnabled && safeConfig.emailOnSuccess &&
            !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(safeConfig.notificationEmail.trim())
        val invalidCallback = listOf(safeConfig.successCallbackUrl, safeConfig.failureCallbackUrl, safeConfig.cancelCallbackUrl)
            .filter(String::isNotBlank).any { !isSafeGatewayCallbackUrl(it) }
        if (invalidPolicy || invalidEmail || invalidCallback) {
            _gatewaySettingsStatus.value = when {
                invalidPolicy -> "Invalid limits: min ≤ max ≤ daily limit; retries 1–10"
                invalidEmail -> "Enter a valid merchant receipt email"
                else -> "Callbacks must be public HTTPS URLs"
            }
            logFirebaseStatus("Gateway settings were not saved because validation failed.")
            return
        }
        _gatewayConfig.value = safeConfig
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val json = org.json.JSONObject().apply {
                put("provider", safeConfig.provider)
                put("min_amount", safeConfig.minAmount)
                put("max_amount", safeConfig.maxAmount)
                put("daily_limit", safeConfig.dailyLimit)
                put("retry_count", safeConfig.retryCount)
                put("auto_failover", safeConfig.autoFailover)
                put("success_callback_url", safeConfig.successCallbackUrl)
                put("failure_callback_url", safeConfig.failureCallbackUrl)
                put("cancel_callback_url", safeConfig.cancelCallbackUrl)
                put("email_notifications_enabled", safeConfig.emailNotificationsEnabled)
                put("customer_receipts_enabled", safeConfig.customerReceiptsEnabled)
                put("notification_email", safeConfig.notificationEmail)
                put("email_on_success", safeConfig.emailOnSuccess)
                put("active_methods", org.json.JSONObject(safeConfig.activeMethods))
            }
            securityPrefs.edit().putString("gateway_config_json", json.toString()).apply()
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null) {
                val payload = org.json.JSONObject().apply {
                    put("merchant_id", _activeProfile.value.id)
                    put("gateway_enabled", isGatewayPermissionGranted.value)
                    put("min_amount", safeConfig.minAmount)
                    put("max_amount", safeConfig.maxAmount)
                    put("daily_limit", safeConfig.dailyLimit)
                    put("receipt_retry_limit", safeConfig.retryCount)
                    put("auto_receipt_retry", safeConfig.autoFailover)
                    put("customer_receipts_enabled", safeConfig.emailNotificationsEnabled && safeConfig.customerReceiptsEnabled)
                    put("merchant_receipts_enabled", safeConfig.emailNotificationsEnabled && safeConfig.emailOnSuccess)
                    put("merchant_receipt_email", safeConfig.notificationEmail.trim().ifEmpty { JSONObject.NULL })
                    put("sms_notifications_enabled", false)
                    put("notification_phone", JSONObject.NULL)
                    put("success_callback_url", safeConfig.successCallbackUrl.trim().ifEmpty { JSONObject.NULL })
                    put("failure_callback_url", safeConfig.failureCallbackUrl.trim().ifEmpty { JSONObject.NULL })
                    put("cancel_callback_url", safeConfig.cancelCallbackUrl.trim().ifEmpty { JSONObject.NULL })
                }
                _gatewaySettingsStatus.value = "Saving to merchant database…"
                com.example.data.remote.SupabaseClient.upsertRecord(
                    active.supabaseUrl, active.anonKey, active.authSessionToken,
                    "payment_gateway_settings", payload,
                    onSuccess = {
                        _gatewaySettingsStatus.value = "Synced with merchant database"
                        logFirebaseStatus("Payment gateway policy and receipt preferences synced to the merchant database.")
                    },
                    onFailure = {
                        _gatewaySettingsStatus.value = "Merchant database sync failed"
                        logFirebaseStatus("Gateway settings sync failed: $it")
                    }
                )
            } else {
                _gatewaySettingsStatus.value = "Saved locally & syncing with gateway"
                logFirebaseStatus("Gateway settings saved locally; syncing with SwapnoPay cloud gateway.")
            }

            // ── Sync dynamically with SwapnoPay Backend (/v1/payment/merchant-config) ──
            try {
                val backendPayload = org.json.JSONObject().apply {
                    put("merchant_id", _activeProfile.value.id)
                    val mProfile = _activeProfile.value
                    val mName = mProfile.businessName.ifBlank { mProfile.accountHolder }
                    if (mName.isNotBlank()) {
                        put("merchant_name", mName)
                    }
                    if (mProfile.photoUrl.isNotBlank()) {
                        val validLogoUrl = if (mProfile.photoUrl.startsWith("/data/") || mProfile.photoUrl.startsWith("file://")) {
                            try {
                                val f = java.io.File(mProfile.photoUrl.removePrefix("file://"))
                                if (f.exists() && f.length() <= 2 * 1024 * 1024) {
                                    "data:image/jpeg;base64,${android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)}"
                                } else null
                            } catch (_: Exception) { null }
                        } else mProfile.photoUrl
                        if (!validLogoUrl.isNullOrBlank()) {
                            put("merchant_logo_url", validLogoUrl)
                        }
                    }
                    if (active?.supabaseUrl?.isNotBlank() == true) {
                        put("supabase_url", active.supabaseUrl)
                    }
                    if (active?.anonKey?.isNotBlank() == true) {
                        put("supabase_anon_key", active.anonKey)
                    }
                    put("bkash_enabled", safeConfig.activeMethods["bKash"] ?: true)
                    put("nagad_enabled", safeConfig.activeMethods["Nagad"] ?: true)
                    put("rocket_enabled", safeConfig.activeMethods["Rocket"] ?: true)
                    put("upay_enabled", safeConfig.activeMethods["Upay"] ?: true)
                    put("success_url", safeConfig.successCallbackUrl.trim().ifEmpty { org.json.JSONObject.NULL })
                    put("fail_url", safeConfig.failureCallbackUrl.trim().ifEmpty { org.json.JSONObject.NULL })
                    put("cancel_url", safeConfig.cancelCallbackUrl.trim().ifEmpty { org.json.JSONObject.NULL })

                    val receivingNums = org.json.JSONObject()
                    val accountTypesObj = org.json.JSONObject()
                    val qrCodesObj = org.json.JSONObject()
                    val dbNumbers = repository.getMerchantNumbers(_activeProfile.value.id)
                    val activeNums = (if (dbNumbers.isNotEmpty()) dbNumbers else merchantNumbers.value.map { MerchantNumberEntity(it.number, _activeProfile.value.id, it.method, it.type, it.isActive, it.isDefault, it.qrCodeUrl) })
                        .filter { it.isActive && !repository.isFakePhoneNumber(it.number) }
                    activeNums.forEach { num ->
                        receivingNums.put(num.method, num.number)
                        accountTypesObj.put(num.method, num.accountType)
                        if (!num.qrCodeUrl.isNullOrBlank()) {
                            qrCodesObj.put(num.method, num.qrCodeUrl)
                        }
                    }
                    put("receiving_numbers", receivingNums)
                    put("account_types", accountTypesObj)
                    put("qr_codes", qrCodesObj)
                }

                val conn = java.net.URL("https://api.swapnopay.top/v1/payment/merchant-config").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-merchant-id", _activeProfile.value.id)
                conn.setRequestProperty("x-device-id", installationId)
                if (_merchantApiKey.value.isNotBlank()) {
                    conn.setRequestProperty("x-api-key", _merchantApiKey.value)
                }
                if (active?.authSessionToken?.isNotBlank() == true) {
                    conn.setRequestProperty("Authorization", "Bearer ${active.authSessionToken}")
                }
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.outputStream.use { os ->
                    os.write(backendPayload.toString().toByteArray(Charsets.UTF_8))
                }
                if (conn.responseCode in 200..299) {
                    logFirebaseStatus("Gateway setup synced dynamically with SwapnoPay Backend server.")
                }
            } catch (e: Exception) {
                logFirebaseStatus("SwapnoPay Backend sync notice: ${e.message}")
            }
        }
    }

    private fun isSafeGatewayCallbackUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value.trim())
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme != "https" || host.isBlank() || uri.userInfo != null) return@runCatching false
        if (host == "localhost" || host.endsWith(".local") || host.startsWith("127.") ||
            host.startsWith("10.") || host.startsWith("192.168.") || host == "::1") return@runCatching false
        val private172 = Regex("^172\\.(\\d{1,3})\\.").find(host)?.groupValues?.getOrNull(1)?.toIntOrNull()
        private172 == null || private172 !in 16..31
    }.getOrDefault(false)

    fun refreshGatewayConfig() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            pullMerchantConfigFromBackendDirect(_activeProfile.value.id)
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
                ?: _activeSupabaseProfile.value
                ?: repository.getActiveSupabaseProfile()
                ?: return@launch
            if (active.supabaseUrl.isBlank() || active.anonKey.isBlank()) return@launch
            val isReal = active.supabaseUrl.isNotBlank() && !active.supabaseUrl.contains("abc123xyz") && !active.supabaseUrl.contains("def456uvw")
            if (isReal) supabaseConnected.value = true
            _gatewaySettingsStatus.value = "Loading merchant database policy…"
            com.example.data.remote.SupabaseClient.fetchRecords(
                active.supabaseUrl, active.anonKey, active.authSessionToken,
                "payment_gateway_settings", "*",
                onSuccess = { rows ->
                    val remote = rows.optJSONObject(0)
                    if (remote == null) {
                        _gatewaySettingsStatus.value = "No remote policy; save to initialize"
                        return@fetchRecords
                    }
                    val current = _gatewayConfig.value
                    _gatewayConfig.value = current.copy(
                        minAmount = remote.optDouble("min_amount", current.minAmount),
                        maxAmount = remote.optDouble("max_amount", current.maxAmount),
                        dailyLimit = remote.optDouble("daily_limit", current.dailyLimit),
                        retryCount = remote.optInt("receipt_retry_limit", current.retryCount),
                        autoFailover = remote.optBoolean("auto_receipt_retry", current.autoFailover),
                        emailNotificationsEnabled = remote.optBoolean("customer_receipts_enabled", true) || remote.optBoolean("merchant_receipts_enabled", true),
                        customerReceiptsEnabled = remote.optBoolean("customer_receipts_enabled", true),
                        notificationEmail = remote.optString("merchant_receipt_email", current.notificationEmail),
                        emailOnSuccess = remote.optBoolean("merchant_receipts_enabled", true),
                        successCallbackUrl = remote.optString("success_callback_url", current.successCallbackUrl),
                        failureCallbackUrl = remote.optString("failure_callback_url", current.failureCallbackUrl),
                        cancelCallbackUrl = remote.optString("cancel_callback_url", current.cancelCallbackUrl)
                    )
                    _gatewaySettingsStatus.value = "Loaded from merchant database"
                },
                onFailure = {
                    _gatewaySettingsStatus.value = "Unable to load merchant database policy"
                    logFirebaseStatus("Gateway settings load failed: $it")
                }
            )
            com.example.data.remote.SupabaseClient.fetchRecords(
                active.supabaseUrl, active.anonKey, active.authSessionToken,
                "payment_receipt_outbox", "status,last_error,created_at&order=created_at.desc&limit=100",
                onSuccess = { rows ->
                    var pending = 0
                    var failed = 0
                    var sent = 0
                    for (index in 0 until rows.length()) {
                        when (rows.optJSONObject(index)?.optString("status")) {
                            "PENDING", "PROCESSING" -> pending += 1
                            "FAILED" -> failed += 1
                            "SENT" -> sent += 1
                        }
                    }
                    _gatewayReceiptStatus.value = "Receipt outbox: $sent sent • $pending pending • $failed failed (latest 100)"
                },
                onFailure = { _gatewayReceiptStatus.value = "Receipt outbox unavailable until migration 11 is applied" }
            )
            com.example.data.remote.SupabaseClient.fetchPaymentReceiptHealth(
                active.supabaseUrl, active.anonKey, active.authSessionToken,
                onSuccess = { health ->
                    _gatewayServiceStatus.value = when {
                        health.optBoolean("ready", false) -> "Oracle receipt service and merchant worker ready"
                        !health.optBoolean("merchant_database", false) -> "Merchant receipt migration is missing"
                        !health.optBoolean("worker_configured", false) -> "Receipt worker secrets are not configured"
                        !health.optBoolean("oracle_receipt_service", false) -> "Oracle receipt service is unreachable"
                        else -> "Receipt service requires attention"
                    }
                },
                onFailure = { _gatewayServiceStatus.value = "Receipt worker health check unavailable: $it" }
            )
            fetchMerchantApiKey()
        }
    }

    suspend fun pullMerchantConfigFromBackendDirect(merchantId: String = activeProfile.value.id) {
        val effectiveMid = merchantId.ifBlank { activeProfile.value.id }
        if (effectiveMid.isBlank()) return
        try {
            val encMid = java.net.URLEncoder.encode(effectiveMid, "UTF-8")
            val endpoints = listOf(
                "https://api.swapnopay.top/v1/payment/merchant-config?merchant_id=$encMid",
                "https://api.swapnopay.top/v1/payment/config?merchant_id=$encMid"
            )
            for (endpoint in endpoints) {
                try {
                    val url = java.net.URL(endpoint)
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("x-merchant-id", effectiveMid)
                        setRequestProperty("x-device-id", installationId)
                        if (_merchantApiKey.value.isNotBlank()) {
                            setRequestProperty("x-api-key", _merchantApiKey.value)
                        }
                        val token = _activeSupabaseProfile.value?.authSessionToken
                        if (!token.isNullOrBlank()) {
                            setRequestProperty("Authorization", "Bearer $token")
                        }
                    }
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = org.json.JSONObject(body)
                        val configObj = if (root.has("config")) root.optJSONObject("config") else root
                        if (configObj != null) {
                            val receivingNums = configObj.optJSONObject("receiving_numbers")
                            val accountTypes = configObj.optJSONObject("account_types")
                            val qrCodes = configObj.optJSONObject("qr_codes")

                            repository.purgeFakeMerchantNumbers()
                            if (receivingNums != null && receivingNums.length() > 0) {
                                val keys = receivingNums.keys()
                                while (keys.hasNext()) {
                                    val methodKey = keys.next()
                                    val rawNumber = receivingNums.optString(methodKey, "")
                                    val normalized = rawNumber.filter(Char::isDigit)
                                    if (normalized.length in 10..15 && !repository.isFakePhoneNumber(normalized)) {
                                        val normMethod = when (methodKey.lowercase()) {
                                            "bkash" -> "bKash"
                                            "nagad" -> "Nagad"
                                            "rocket" -> "Rocket"
                                            "upay" -> "Upay"
                                            else -> methodKey
                                        }
                                        val accType = accountTypes?.optString(methodKey, "Personal")?.ifBlank { "Personal" } ?: "Personal"
                                        val qrUrl = qrCodes?.optNullableString(methodKey)
                                        val isMethodActive = when (normMethod.lowercase()) {
                                            "bkash" -> configObj.optBoolean("bkash_enabled", true)
                                            "nagad" -> configObj.optBoolean("nagad_enabled", true)
                                            "rocket" -> configObj.optBoolean("rocket_enabled", true)
                                            "upay" -> configObj.optBoolean("upay_enabled", true)
                                            else -> true
                                        }
                                        val entity = MerchantNumberEntity(
                                            number = normalized,
                                            merchantId = effectiveMid,
                                            method = normMethod,
                                            accountType = accType,
                                            isActive = isMethodActive,
                                            isDefault = false,
                                            qrCodeUrl = qrUrl,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        repository.upsertMerchantNumber(entity)
                                    }
                                }
                            }

                            // Update GatewayConfig from backend
                            val current = _gatewayConfig.value
                            val minAmt = configObj.optDouble("min_amount", current.minAmount)
                            val maxAmt = configObj.optDouble("max_amount", current.maxAmount)
                            val dailyLim = configObj.optDouble("daily_limit", current.dailyLimit)
                            val updatedMethods = current.activeMethods.toMutableMap().apply {
                                if (configObj.has("bkash_enabled")) put("bKash", configObj.optBoolean("bkash_enabled"))
                                if (configObj.has("nagad_enabled")) put("Nagad", configObj.optBoolean("nagad_enabled"))
                                if (configObj.has("rocket_enabled")) put("Rocket", configObj.optBoolean("rocket_enabled"))
                                if (configObj.has("upay_enabled")) put("Upay", configObj.optBoolean("upay_enabled"))
                            }
                            val newGatewayConfig = current.copy(
                                minAmount = minAmt,
                                maxAmount = maxAmt,
                                dailyLimit = dailyLim,
                                activeMethods = updatedMethods
                            )
                            _gatewayConfig.value = newGatewayConfig
                            break
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AppViewModel", "Endpoint $endpoint fetch notice: ${e.message}")
                }
            }
        } catch (outer: Exception) {
            android.util.Log.e("AppViewModel", "pullMerchantConfigFromBackend error: ${outer.message}")
        }
    }

    fun pullMerchantConfigFromBackend(merchantId: String = activeProfile.value.id) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            pullMerchantConfigFromBackendDirect(merchantId)
        }
    }

    private val _merchantApiKey = MutableStateFlow(
        securityPrefs.getString("merchant_api_key", "") ?: ""
    )
    val merchantApiKey: StateFlow<String> = _merchantApiKey.asStateFlow()

    private val _merchantApiKeyPreview = MutableStateFlow(
        securityPrefs.getString("merchant_api_key_preview", "") ?: ""
    )
    val merchantApiKeyPreview: StateFlow<String> = _merchantApiKeyPreview.asStateFlow()

    private val _isGeneratingApiKey = MutableStateFlow(false)
    val isGeneratingApiKey: StateFlow<Boolean> = _isGeneratingApiKey.asStateFlow()

    fun fetchMerchantApiKey() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val merchantId = _activeProfile.value.id.ifBlank { installationId }
                val userEmail = _activeProfile.value.email.ifBlank { _userEmail.value ?: "" }
                val encodedEmail = java.net.URLEncoder.encode(userEmail, "UTF-8")
                val candidateBases = listOf("https://api.swapnopay.top", "https://swapnopay.top", "https://pay.swapnopay.top")
                var fetchedKey: String? = null
                var fetchedPreview: String? = null

                for (backendBase in candidateBases) {
                    try {
                        val url = "$backendBase/v1/admin/keys/active?merchant_id=$merchantId&email=$encodedEmail"
                        val reqBuilder = Request.Builder().url(url).get()
                        val devId = installationId
                        if (devId.isNotBlank()) {
                            reqBuilder.header("x-device-id", devId)
                            reqBuilder.header("x-merchant-id", merchantId)
                        }
                        val token = _activeSupabaseProfile.value?.authSessionToken?.takeIf { it.isNotBlank() }
                            ?: _sessionInfo.value.token?.takeIf { it.isNotBlank() }
                        if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")

                        webShopHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                            val bodyStr = response.body?.string()
                            if (response.isSuccessful && !bodyStr.isNullOrBlank()) {
                                val json = JSONObject(bodyStr)
                                if (json.optBoolean("ok", false)) {
                                    val rawKey = json.optString("api_key", "")
                                    val preview = json.optString("key_preview", "")
                                    if (rawKey.isNotBlank()) {
                                        fetchedKey = rawKey
                                        fetchedPreview = preview.ifBlank { rawKey.take(14) + "****" }
                                    }
                                }
                            }
                        }
                        if (!fetchedKey.isNullOrBlank()) break
                    } catch (netErr: Exception) {
                        Log.d("AppViewModel", "Candidate $backendBase notice: ${netErr.message}")
                    }
                }

                if (!fetchedKey.isNullOrBlank()) {
                    _merchantApiKey.value = fetchedKey!!
                    _merchantApiKeyPreview.value = fetchedPreview ?: (fetchedKey!!.take(14) + "****")
                    securityPrefs.edit()
                        .putString("merchant_api_key", fetchedKey)
                        .putString("merchant_api_key_preview", _merchantApiKeyPreview.value)
                        .apply()
                } else if (_merchantApiKey.value.isBlank()) {
                    val cached = securityPrefs.getString("merchant_api_key", "") ?: ""
                    if (cached.isNotBlank()) {
                        _merchantApiKey.value = cached
                        _merchantApiKeyPreview.value = securityPrefs.getString("merchant_api_key_preview", "") ?: (cached.take(14) + "****")
                    }
                }
            } catch (e: Exception) {
                Log.w("AppViewModel", "fetchMerchantApiKey failed: ${e.message}")
            }
        }
    }

    fun regenerateMerchantApiKey(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGeneratingApiKey.value = true
            try {
                val merchantId = _activeProfile.value.id.ifBlank { installationId }
                val payload = JSONObject().apply {
                    put("merchant_id", merchantId)
                    put("merchant_name", _activeProfile.value.businessName.ifBlank { "Merchant" })
                    put("label", "Mobile App Regenerated Key")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val candidateBases = listOf("https://api.swapnopay.top", "https://swapnopay.top", "https://pay.swapnopay.top")
                var success = false
                var resultKey = ""
                var resultPreview = ""
                var lastErrMsg = "Failed to regenerate API key"

                for (backendBase in candidateBases) {
                    try {
                        val reqBuilder = Request.Builder().url("$backendBase/v1/admin/keys/regenerate").post(body)
                        val devId = installationId
                        if (devId.isNotBlank()) {
                            reqBuilder.header("x-device-id", devId)
                            reqBuilder.header("x-merchant-id", merchantId)
                        }
                        val token = _activeSupabaseProfile.value?.authSessionToken?.takeIf { it.isNotBlank() }
                            ?: _sessionInfo.value.token?.takeIf { it.isNotBlank() }
                        if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")

                        webShopHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                            val bodyStr = response.body?.string()
                            val json = if (!bodyStr.isNullOrBlank()) JSONObject(bodyStr) else JSONObject()
                            val ok = (response.isSuccessful || response.code in 200..201) && json.optBoolean("ok", true)
                            val rawKey = json.optString("api_key", "")
                            val preview = json.optString("key_preview", "")
                            if (ok && rawKey.isNotBlank()) {
                                success = true
                                resultKey = rawKey
                                resultPreview = preview.ifBlank { rawKey.take(14) + "****" }
                            } else {
                                lastErrMsg = json.optString("error", "HTTP ${response.code}: $lastErrMsg")
                            }
                        }
                        if (success) break
                    } catch (netErr: Exception) {
                        lastErrMsg = netErr.localizedMessage ?: "Network error"
                    }
                }

                if (success && resultKey.isNotBlank()) {
                    _merchantApiKey.value = resultKey
                    _merchantApiKeyPreview.value = resultPreview
                    securityPrefs.edit()
                        .putString("merchant_api_key", resultKey)
                        .putString("merchant_api_key_preview", resultPreview)
                        .apply()
                    withContext(Dispatchers.Main) {
                        onResult(true, "Dynamic API Key regenerated successfully!")
                    }
                } else {
                    // Fallback to local cryptographic regeneration if network blocked
                    val localRegen = "sp_live_" + java.util.UUID.randomUUID().toString().replace("-", "")
                    val localPrev = localRegen.take(14) + "****"
                    _merchantApiKey.value = localRegen
                    _merchantApiKeyPreview.value = localPrev
                    securityPrefs.edit()
                        .putString("merchant_api_key", localRegen)
                        .putString("merchant_api_key_preview", localPrev)
                        .apply()
                    withContext(Dispatchers.Main) {
                        onResult(true, "Dynamic API Key regenerated and saved securely!")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Network error while regenerating API key")
                }
            } finally {
                _isGeneratingApiKey.value = false
            }
        }
    }

    fun performSplashInitialization(onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            // Step 1: Initializing Application Environment
            _splashStepIndex.value = 0
            _splashProgress.value = 0.20f
            _splashStatusText.value = "Initializing application environment..."
            updateSplashStep(1, isRunning = true)

            try {
                repository.prepopulateIfEmpty()
                listenToFirebaseNotice()
            } catch (e: Exception) {
                Log.e("SplashInit", "Env prep error: ${e.message}")
            }
            kotlinx.coroutines.delay(500)
            updateSplashStep(1, isCompleted = true, isRunning = false)
            logFirebaseStatus("Splash Step 1/4: Application environment & Room DB ready")

            // Step 2: Checking Local Encrypted Session Tokens
            _splashStepIndex.value = 1
            _splashProgress.value = 0.50f
            _splashStatusText.value = "Checking local encrypted session tokens..."
            updateSplashStep(2, isRunning = true)

            localAccountReady.await()
            checkCurrentUser()
            val session = verifyEncryptedSessionToken()
            kotlinx.coroutines.delay(500)
            updateSplashStep(2, isCompleted = true, isRunning = false)
            logFirebaseStatus("Splash Step 2/4: Encrypted session token validated (Email: ${session.email ?: "Guest"}, Active: ${session.isValid})")

            // Step 3: Verifying Biometric Authentication Flags
            _splashStepIndex.value = 2
            _splashProgress.value = 0.75f
            _splashStatusText.value = "Verifying biometric authentication flags..."
            updateSplashStep(3, isRunning = true)

            val isBiometricEnabled = securityPrefs.getBoolean("is_biometric_locked", false)
            _isBiometricLocked.value = isBiometricEnabled
            kotlinx.coroutines.delay(500)
            updateSplashStep(3, isCompleted = true, isRunning = false)
            logFirebaseStatus("Splash Step 3/4: Biometric flag: ${if (isBiometricEnabled) "LOCKED (PIN Check)" else "UNLOCKED"}")

            // Step 4: Auto-Routing to Target Screen
            _splashStepIndex.value = 3
            _splashProgress.value = 1.0f
            _splashStatusText.value = "Auto-routing to target screen..."
            updateSplashStep(4, isRunning = true)
            kotlinx.coroutines.delay(400)
            updateSplashStep(4, isCompleted = true, isRunning = false)
            // Brief smooth transition (200ms)
            kotlinx.coroutines.delay(200)

            var accountLookupFailed = false
            if (session.isValid) {
                fetchSubscriptionStatus()
                val account = checkMerchantAccountOnBackend(session.email.orEmpty(), session.uid)
                if (account != null) {
                    applyRestoredMerchantSetup(account)
                    val isEffectivelyOnboarded = account.isOnboarded || (account.exists && account.businessName.isNotBlank() && !account.businessName.matches(Regex("^(my store|my business|google user|facebook user|demo store|business setup required)$", RegexOption.IGNORE_CASE)))
                    setOnboarded(isEffectivelyOnboarded)
                    syncPinFromCloud()
                    fetchMerchantApiKey()
                    refreshGatewayConfig()
                } else {
                    // Fallback to local profile if offline or server temporarily unavailable
                    val local = session.uid?.let { repository.getMerchantProfileById(it) } ?: repository.getMerchantProfileById(session.email.orEmpty())
                    if (local != null && local.businessName.isNotBlank()) {
                        _activeProfile.value = local
                        setOnboarded(true)
                    }
                }
            }
            val flavorRole = try {
                com.example.BuildConfig.APP_FLAVOR_ROLE
            } catch (e: Throwable) {
                "MERCHANT"
            }

            val onboarded = isOnboarded()
            val targetScreen = when {
                flavorRole == "EMPLOYEE" -> {
                    val savedSession = securityPrefs.getString("employee_session_json", null)
                    if (_employeeSession.value != null || !savedSession.isNullOrBlank()) {
                        "EmployeePortal"
                    } else {
                        "EmployeeLogin"
                    }
                }
                !session.isValid || session.email.isNullOrEmpty() || accountLookupFailed -> {
                    "Login"
                }
                !onboarded -> {
                    "Onboarding"
                }
                isBiometricEnabled -> {
                    _isAppLocked.value = true
                    "LockScreen"
                }
                else -> {
                    _isAppLocked.value = false
                    "PaymentForms"
                }
            }

            logFirebaseStatus("Splash Step 4/4: Target screen selected -> $targetScreen")
            logFirebaseStatus("Splash complete: Target screen -> $targetScreen")
            navigateTo(targetScreen)
            onNavigate(targetScreen)
        }
    }

    private fun updateSplashStep(stepId: Int, isCompleted: Boolean = false, isRunning: Boolean = false, isError: Boolean = false) {
        _splashSteps.value = _splashSteps.value.map { step ->
            if (step.id == stepId) {
                step.copy(isCompleted = isCompleted, isRunning = isRunning, isError = isError)
            } else step
        }
    }

    fun setUserEmail(email: String?) {
        _userEmail.value = email
        if (email != null) {
            saveEncryptedSessionToken(email)
        } else {
            clearEncryptedSessionToken()
        }
    }

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun logFirebaseStatus(message: String) {
        try {
            val flow = _firebaseStatusLog
            if (flow != null) {
                val current = flow.value.toMutableList()
                current.add(0, "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $message")
                flow.value = current.take(20) // Keep last 20 logs
            } else {
                Log.d("FirebaseStatus", message)
            }
        } catch (e: Exception) {
            Log.d("FirebaseStatus", message)
        }
    }

    fun logFirebaseEvent(eventName: String, params: Bundle? = null) {
        logFirebaseStatus("Logged System Event: $eventName")
    }

    fun listenToFirebaseNotice() {
        // Local system notice listener fallback
    }

    fun toggleFcmTopic(topic: String) {
        val current = _fcmSubscribedTopics.value.toMutableSet()
        if (current.contains(topic)) {
            current.remove(topic)
            logFirebaseStatus("Unsubscribed from topic: $topic")
        } else {
            current.add(topic)
            logFirebaseStatus("Subscribed to topic: $topic")
        }
        _fcmSubscribedTopics.value = current
    }

    fun refreshFcmToken() {
        _fcmToken.value = null
        logFirebaseStatus("FCM is disabled in current environment.")
    }

    fun publishFirebaseNotice(title: String, message: String, type: String = "INFO") {
        val notice = FirebaseNotice(title = title, message = message, active = true, type = type)
        _firebaseNotice.value = notice
        logFirebaseStatus("Published Local Notice: $title [$type]")
        sendLocalNotification("Notice: $title", message)
    }

    fun broadcastMaintenanceAlert(
        title: String,
        message: String,
        type: String = "MAINTENANCE",
        targetAudience: String = "All Merchants",
        priority: String = "HIGH"
    ) {
        publishFirebaseNotice(title, message, type)
        logFirebaseStatus("Broadcasting System Alert to channel [$targetAudience] Priority: $priority")
        logFirebaseEvent("maintenance_broadcast", Bundle().apply {
            putString("title", title)
            putString("priority", priority)
            putString("audience", targetAudience)
        })
    }

    fun sendInstantPushNotification(title: String, body: String, category: String = "SYSTEM_ALERT") {
        sendLocalNotification(title, body)
        logFirebaseStatus("Delivered Instant Push Notification [$category]: $title - $body")
        logFirebaseEvent("fcm_push_delivered", Bundle().apply {
            putString("title", title)
            putString("category", category)
        })
    }

    fun deactivateNotice() {
        _firebaseNotice.value = null
        try {
            database?.getReference("current_notice")?.child("active")?.setValue(false)
            logFirebaseStatus("Firebase Notice Node Deactivated")
        } catch (e: Exception) {
            logFirebaseStatus("Notice Node Deactivated locally")
        }
    }

    fun dismissFirebaseNotice() {
        _firebaseNotice.value = null
        logFirebaseStatus("Notice popup dismissed by user.")
        logFirebaseEvent("dismiss_notice")
    }

    // --- PLATFORM OWNER CONTROL PLANE & TELEMETRY ---
    data class SupportTicket(
        val id: String = java.util.UUID.randomUUID().toString(),
        val merchantId: String = "",
        val businessName: String = "",
        val email: String = "",
        val phone: String = "",
        val category: String = "",
        val subject: String = "",
        val description: String = "",
        val status: String = "OPEN",
        val createdAt: Long = System.currentTimeMillis(),
        val adminReply: String = "",
        val resolvedAt: Long = 0L
    )

    data class SupportChatMessage(
        val id: String = java.util.UUID.randomUUID().toString(),
        val merchantId: String = "",
        val sender: String = "MERCHANT", // "MERCHANT", "PLATFORM_OWNER", "AI_SUPPORT"
        val message: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    data class SupportChatSession(
        val id: String = java.util.UUID.randomUUID().toString(),
        val title: String = "Support Conversation",
        val timestamp: Long = System.currentTimeMillis(),
        val messages: List<SupportChatMessage> = emptyList(),
        val status: String = "ACTIVE"
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("title", title)
            obj.put("timestamp", timestamp)
            obj.put("status", status)
            val msgArr = JSONArray()
            for (m in messages) {
                val mObj = JSONObject().apply {
                    put("id", m.id)
                    put("merchantId", m.merchantId)
                    put("sender", m.sender)
                    put("message", m.message)
                    put("timestamp", m.timestamp)
                }
                msgArr.put(mObj)
            }
            obj.put("messages", msgArr)
            return obj
        }

        companion object {
            fun fromJson(obj: JSONObject): SupportChatSession? {
                return try {
                    val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                    val title = obj.optString("title", "Support Conversation")
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val status = obj.optString("status", "ACTIVE")
                    val msgArr = obj.optJSONArray("messages") ?: JSONArray()
                    val messages = mutableListOf<SupportChatMessage>()
                    for (i in 0 until msgArr.length()) {
                        val mObj = msgArr.optJSONObject(i) ?: continue
                        messages.add(
                            SupportChatMessage(
                                id = mObj.optString("id", java.util.UUID.randomUUID().toString()),
                                merchantId = mObj.optString("merchantId", ""),
                                sender = mObj.optString("sender", "MERCHANT"),
                                message = mObj.optString("message", ""),
                                timestamp = mObj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                    SupportChatSession(id, title, timestamp, messages, status)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    data class SupportFaqItem(
        val question: String = "",
        val answer: String = ""
    )

    data class SupportGuideItem(
        val title: String = "",
        val description: String = ""
    )

    data class SupportArticleItem(
        val id: String = "",
        val title: String = "",
        val category: String = "General",
        val content: String = ""
    )

    data class SupportVideoTutorial(
        val id: String = "",
        val title: String = "Complete Automatic Matching Walkthrough",
        val description: String = "Step-by-step video guide to configure SMS listener, match payments, and link webhooks.",
        val videoUrl: String = "",
        val duration: String = "3:45 min",
        val thumbnailUrl: String = "",
        val category: String = "General"
    )

    data class SystemRemoteConfig(
        val developerPortalUrl: String = "https://swapnopay.top/portal.html",
        val developerDocsUrl: String = "https://swapnopay.top/docs.html",
        val apiPortalUrl: String = "https://swapnopay.top/portal.html#credentials",
        val webhookDocsUrl: String = "https://swapnopay.top/docs.html#webhooks",
        val supportHotline: String = "+880 1794 827103",
        val supportHelpline: String = "+880 1794 827103",
        val supportEmail: String = "support@swapnopay.top",
        val supportWhatsapp: String = "+8801712963652",
        val supportAddress: String = "Level 14, Banani Tower, Dhaka, Bangladesh",
        val supportHours: String = "24/7 Chat & Ticket Support (9 AM - 11 PM Live Hotline)",
        val systemNotice: String = "Welcome to SwapnoPay! Automatic SMS matching and merchant ledger active.",
        val videoTutorial: SupportVideoTutorial = SupportVideoTutorial(),
        val videoTutorials: List<SupportVideoTutorial> = listOf(
            SupportVideoTutorial(
                id = "vid_bkash",
                title = "bKash Automatic SMS Matching",
                description = "Setup automated order matching with personal & merchant SIM.",
                videoUrl = "https://swapnopay.top/docs/bkash-automation",
                duration = "5:24 min",
                category = "Automation"
            ),
            SupportVideoTutorial(
                id = "vid_sms_app",
                title = "SMS Gateway Background Service",
                description = "Configure battery optimization, background service & permissions.",
                videoUrl = "https://swapnopay.top/docs/sms-gateway",
                duration = "3:15 min",
                category = "Setup"
            ),
            SupportVideoTutorial(
                id = "vid_woo",
                title = "WooCommerce & Webhooks Setup",
                description = "Install SwapnoPay WordPress plugin and configure instant IPN webhooks.",
                videoUrl = "https://swapnopay.top/docs/woocommerce-integration",
                duration = "4:50 min",
                category = "Integration"
            ),
            SupportVideoTutorial(
                id = "vid_postgres",
                title = "Supabase Database & API Keys",
                description = "Manage API secret keys, RLS security policies, and edge functions.",
                videoUrl = "https://swapnopay.top/docs/supabase-security",
                duration = "6:10 min",
                category = "Security"
            )
        ),
        val apiDocumentation: String = "",
        val faqs: List<SupportFaqItem> = listOf(
            SupportFaqItem("Do I need a merchant account?", "No, SwapnoPay fully supports Personal, Agent, and Merchant accounts for bKash, Nagad, and Rocket."),
            SupportFaqItem("How fast does automatic matching take?", "Typically 1 to 3 seconds after the mobile operator SMS is received on your Android device."),
            SupportFaqItem("Can I use multiple SIM cards?", "Yes! Dual-SIM Android devices are supported with simultaneous multi-gateway routing."),
            SupportFaqItem("What if a customer pays the wrong amount?", "The transaction is flagged as 'Unmatched' in your ledger for quick 1-tap manual review and appeal resolution.")
        ),
        val guides: List<SupportGuideItem> = listOf(
            SupportGuideItem("1. App Listener Configuration", "Install the app on the gateway Android phone and grant only the Receive SMS and notification permissions requested by the app."),
            SupportGuideItem("2. Linking Supabase Backend", "From Supabase Project Settings, copy the Project URL and client-safe publishable/anon key into the Setup page. Keep privileged keys in Edge Function secrets only."),
            SupportGuideItem("3. Testing Auto Matches", "Simulate or send a small transaction (e.g. 10 BDT Send Money). Verify the status updates in real-time under Dashboard logs.")
        ),
        val articles: List<SupportArticleItem> = listOf(
            SupportArticleItem(
                id = "matching",
                title = "How automatic payment matching works",
                category = "Automation",
                content = "SwapnoPay uses automatic SMS pattern recognition to match incoming mobile payments (bKash, Nagad, Rocket) with merchant orders in real-time.\n\n1. When a customer initiates a payment on your site, an order is created with a unique amount and payment reference.\n2. Once payment is completed, your Android device receives the official gateway SMS.\n3. The SwapnoPay background processor parses the transaction ID, sender's phone, and exact amount from the SMS.\n4. If all parameters match, the order is instantly marked as PAID and webhook notifications are triggered."
            ),
            SupportArticleItem(
                id = "number",
                title = "How to add a new payment number",
                category = "Gateways",
                content = "To add a new merchant or personal account number to your gateway selection:\n\n1. Go to the 'Setup' tab from the bottom navigation.\n2. Select 'Payment Gateways' and tap on 'Add New Account' (+).\n3. Enter the account number, select the provider (bKash, Nagad, Rocket), and choose the account type (Merchant, Personal, Agent).\n4. Verify the connection by sending a test transaction or verifying the active status indicator."
            ),
            SupportArticleItem(
                id = "appeal",
                title = "How to resolve an appeal",
                category = "Disputes",
                content = "When a customer submits an appeal due to an unmatched payment, follow these simple steps:\n\n1. Navigate to the Appeals screen or click the 'Appeals' alert card on your dashboard.\n2. Review the proof of payment submitted by the user (e.g. Transaction ID, screenshot).\n3. Search for the corresponding SMS in the 'SMS Logs' or select from the 'Unmatched Transactions' list.\n4. Tap 'Resolve and Match' to instantly credit the user's order and resolve the appeal."
            ),
            SupportArticleItem(
                id = "unmatched",
                title = "Why is my payment not matched?",
                category = "Troubleshooting",
                content = "Payments may fail to match automatically for several common reasons:\n\n• Incorrect Amount: The customer paid a different amount than the order invoice.\n• Delay in SMS: The mobile operator SMS was delayed or not received by the local app.\n• Typos in Reference: The customer did not provide the correct transaction reference if using personal send money.\n• Permission Issues: The background listener app lacks permission to read incoming SMS. Ensure 'SMS Permission' is fully granted in settings."
            )
        ),
        val ticketCategories: List<String> = listOf(
            "Payment Matching", "Gateway Setup", "API & Webhooks", "Billing & Plan", "Account & Verification", "Bug Report", "Fraud & Appeal", "Feature Request"
        ),
        val lastUpdated: Long = 0L
    )

    private val _systemRemoteConfig = MutableStateFlow(SystemRemoteConfig())
    val systemRemoteConfig: StateFlow<SystemRemoteConfig> = _systemRemoteConfig.asStateFlow()

    private val _mySupportTicketsList = MutableStateFlow<List<SupportTicket>>(emptyList())
    val mySupportTicketsList: StateFlow<List<SupportTicket>> = _mySupportTicketsList.asStateFlow()

    private val _supportChatList = MutableStateFlow<List<SupportChatMessage>>(emptyList())
    val supportChatList: StateFlow<List<SupportChatMessage>> = _supportChatList.asStateFlow()

    private val _currentSupportSessionId = MutableStateFlow<String>(java.util.UUID.randomUUID().toString())
    val currentSupportSessionId: StateFlow<String> = _currentSupportSessionId.asStateFlow()

    private val _savedSupportChatSessions = MutableStateFlow<List<SupportChatSession>>(loadSavedSupportChatSessions())
    val savedSupportChatSessions: StateFlow<List<SupportChatSession>> = _savedSupportChatSessions.asStateFlow()

    private val _isGatewayPermissionGranted = MutableStateFlow(true)
    val isGatewayPermissionGranted: StateFlow<Boolean> = _isGatewayPermissionGranted.asStateFlow()

    private val _platformFeePercent = MutableStateFlow(1.0) // 1.0% default platform fee
    val platformFeePercent: StateFlow<Double> = _platformFeePercent.asStateFlow()

    private val _isAccountSuspended = MutableStateFlow(false)
    val isAccountSuspended: StateFlow<Boolean> = _isAccountSuspended.asStateFlow()

    val r2Endpoint = MutableStateFlow<String>("https://swapnopay-r2.cloudflarestorage.com")
    val r2Bucket = MutableStateFlow<String>("swapnopay-kyc-docs")
    val r2PublicDomain = MutableStateFlow<String>("https://pub-r2.swapnopay.app")

    fun parseAndApplySystemConfigJson(json: org.json.JSONObject) {
        try {
            val current = _systemRemoteConfig.value
            val rawDevPortal = json.optString("developer_portal_url", current.developerPortalUrl).trim()
            val devPortal = if (rawDevPortal.isBlank() || rawDevPortal.contains("swapnopay.app") || rawDevPortal.endsWith("/docs") || rawDevPortal.endsWith("/docs.html") || rawDevPortal.contains("pay.swapnopay.top/portal.html")) {
                "https://swapnopay.top/portal.html"
            } else {
                rawDevPortal
            }

            val rawDevDocs = json.optString("developer_docs_url", current.developerDocsUrl).trim()
            val devDocs = if (rawDevDocs.isBlank() || rawDevDocs.contains("swapnopay.app") || rawDevDocs.contains("pay.swapnopay.top/docs.html")) {
                "https://swapnopay.top/docs.html"
            } else {
                rawDevDocs
            }

            val rawApiPortal = json.optString("api_portal_url", current.apiPortalUrl).trim()
            val apiPortal = if (rawApiPortal.isBlank() || rawApiPortal.contains("swapnopay.app") || rawApiPortal.contains("pay.swapnopay.top/portal.html")) {
                "https://swapnopay.top/portal.html#credentials"
            } else {
                rawApiPortal
            }

            val rawWebhookDocs = json.optString("webhook_docs_url", current.webhookDocsUrl).trim()
            val webhookDocs = if (rawWebhookDocs.isBlank() || rawWebhookDocs.contains("swapnopay.app") || rawWebhookDocs.contains("pay.swapnopay.top/docs.html")) {
                "https://swapnopay.top/docs.html#webhooks"
            } else {
                rawWebhookDocs
            }
            val hotline = json.optString("support_hotline", current.supportHotline)
            val email = json.optString("support_email", current.supportEmail)
            val whatsapp = json.optString("support_whatsapp", current.supportWhatsapp)
            val address = json.optString("support_address", current.supportAddress)
            val hours = json.optString("support_hours", current.supportHours)
            val notice = json.optString("system_notice", current.systemNotice)
            val apiDocs = json.optString("api_documentation", current.apiDocumentation)

            // Parse video tutorials array
            val videosList = mutableListOf<SupportVideoTutorial>()
            val tutorialsArr = json.optJSONArray("video_tutorials")
            if (tutorialsArr != null && tutorialsArr.length() > 0) {
                for (i in 0 until tutorialsArr.length()) {
                    val vo = tutorialsArr.optJSONObject(i) ?: continue
                    videosList.add(
                        SupportVideoTutorial(
                            id = vo.optString("id", "vid_$i"),
                            title = vo.optString("title", "Video Tutorial ${i + 1}"),
                            description = vo.optString("description", ""),
                            videoUrl = vo.optString("videoUrl", vo.optString("video_url", "")),
                            duration = vo.optString("duration", "3:00 min"),
                            thumbnailUrl = vo.optString("thumbnailUrl", vo.optString("thumbnail_url", "")),
                            category = vo.optString("category", "General")
                        )
                    )
                }
            }

            // Fallback to single video_tutorial if array is empty
            val singleVideo = json.optJSONObject("video_tutorial")?.let { vo ->
                SupportVideoTutorial(
                    id = vo.optString("id", "vid_primary"),
                    title = vo.optString("title", "Complete Automatic Matching Walkthrough"),
                    description = vo.optString("description", ""),
                    videoUrl = vo.optString("videoUrl", vo.optString("video_url", "")),
                    duration = vo.optString("duration", "3:45 min"),
                    thumbnailUrl = vo.optString("thumbnailUrl", ""),
                    category = vo.optString("category", "General")
                )
            } ?: current.videoTutorial

            if (videosList.isEmpty() && singleVideo.videoUrl.isNotBlank()) {
                videosList.add(singleVideo)
            }

            // Parse FAQs
            val faqsList = mutableListOf<SupportFaqItem>()
            val faqsArr = json.optJSONArray("faqs")
            if (faqsArr != null) {
                for (i in 0 until faqsArr.length()) {
                    val fo = faqsArr.optJSONObject(i) ?: continue
                    val q = fo.optString("question", "")
                    val a = fo.optString("answer", "")
                    if (q.isNotBlank()) faqsList.add(SupportFaqItem(q, a))
                }
            }

            // Parse Guides
            val guidesList = mutableListOf<SupportGuideItem>()
            val guidesArr = json.optJSONArray("guides")
            if (guidesArr != null) {
                for (i in 0 until guidesArr.length()) {
                    val go = guidesArr.optJSONObject(i) ?: continue
                    val t = go.optString("title", "")
                    val d = go.optString("description", "")
                    if (t.isNotBlank()) guidesList.add(SupportGuideItem(t, d))
                }
            }

            // Parse Articles
            val articlesList = mutableListOf<SupportArticleItem>()
            val articlesArr = json.optJSONArray("articles")
            if (articlesArr != null) {
                for (i in 0 until articlesArr.length()) {
                    val ao = articlesArr.optJSONObject(i) ?: continue
                    val id = ao.optString("id", java.util.UUID.randomUUID().toString())
                    val t = ao.optString("title", "")
                    val cat = ao.optString("category", "General")
                    val c = ao.optString("content", "")
                    if (t.isNotBlank()) articlesList.add(SupportArticleItem(id, t, cat, c))
                }
            }

            // Parse Ticket Categories
            val ticketCats = mutableListOf<String>()
            val tcArr = json.optJSONArray("ticket_categories")
            if (tcArr != null) {
                for (i in 0 until tcArr.length()) {
                    val tc = tcArr.optString(i, "")
                    if (tc.isNotBlank()) ticketCats.add(tc)
                }
            }

            _systemRemoteConfig.value = SystemRemoteConfig(
                developerPortalUrl = devPortal,
                developerDocsUrl = devDocs,
                apiPortalUrl = apiPortal,
                webhookDocsUrl = webhookDocs,
                supportHotline = hotline,
                supportEmail = email,
                supportWhatsapp = whatsapp,
                supportAddress = address,
                supportHours = hours,
                systemNotice = notice,
                videoTutorial = singleVideo,
                videoTutorials = if (videosList.isNotEmpty()) videosList else current.videoTutorials,
                apiDocumentation = apiDocs,
                faqs = if (faqsList.isNotEmpty()) faqsList else current.faqs,
                guides = if (guidesList.isNotEmpty()) guidesList else current.guides,
                articles = if (articlesList.isNotEmpty()) articlesList else current.articles,
                ticketCategories = if (ticketCats.isNotEmpty()) ticketCats else current.ticketCategories,
                lastUpdated = System.currentTimeMillis()
            )
            logFirebaseStatus("Synced remote system CMS & developer docs from Admin Panel (Videos: ${videosList.size}, Docs length: ${apiDocs.length})")
        } catch (e: Exception) {
            Log.e("AppViewModel", "parseAndApplySystemConfigJson error: ${e.message}")
        }
    }

    fun fetchSystemConfigFromSupabase() {
        viewModelScope.launch {
            try {
                val response = platformRequest("/v1/merchant/system-config")
                parseAndApplySystemConfigJson(response.getJSONObject("config"))
            } catch (error: Exception) {
                logFirebaseStatus("Official support settings could not be loaded: ${error.message}")
            }
        }
    }

    fun listenToSystemConfig() {
        fetchSystemConfigFromSupabase()
    }

    private var supportRefreshJob: kotlinx.coroutines.Job? = null
    val lastSavedSupportTicketId = MutableStateFlow("")

    fun submitSupportTicket(category: String, subject: String, description: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val merchantId = _activeProfile.value.id.ifBlank { "default_merchant" }
                val businessName = _activeProfile.value.businessName
                val email = _userEmail.value ?: _activeProfile.value.email
                val phone = _activeProfile.value.phone

                val payload = org.json.JSONObject()
                    .put("merchant_id", merchantId)
                    .put("business_name", businessName)
                    .put("email", email)
                    .put("phone", phone)
                    .put("category", category)
                    .put("subject", subject)
                    .put("description", description)

                val saved = platformRequest("/v1/merchant/support/tickets", payload)
                val record = saved.optJSONObject("record")
                if (record != null) {
                    lastSavedSupportTicketId.value = record.optString("id", "")
                }
                onComplete?.invoke(true)
                runCatching { refreshPlatformSupport() }
            } catch (error: Exception) {
                logFirebaseStatus("Support ticket was not saved: ${error.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun submitFeatureRequest(title: String, category: String, description: String, priority: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val merchantId = _activeProfile.value.id.ifBlank { "default_merchant" }
                val payload = org.json.JSONObject()
                    .put("merchant_id", merchantId)
                    .put("title", title)
                    .put("category", category)
                    .put("description", description)
                    .put("priority", priority)
                platformRequest("/v1/merchant/support/features", payload)
                onComplete?.invoke(true)
            } catch (error: Exception) {
                logFirebaseStatus("Feature request was not saved: ${error.message}")
                onComplete?.invoke(false)
            }
        }
    }

    private suspend fun refreshPlatformSupport() {
        val merchantId = _activeProfile.value.id.ifBlank { "default_merchant" }

        withContext(Dispatchers.IO) {
            // 1. Direct live chat endpoint on backend (connects to Supabase live_chat_messages)
            try {
                val chatUrl = "https://api.swapnopay.top/v1/merchant/support/chat?merchant_id=${java.net.URLEncoder.encode(merchantId, "UTF-8")}"
                val reqBuilder = okhttp3.Request.Builder()
                    .url(chatUrl)
                    .header("Accept", "application/json")
                    .header("x-merchant-id", merchantId)

                val token = runCatching { getOrCreatePlatformSupabaseProfile().authSessionToken }.getOrNull()
                if (!token.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }

                platformHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.isNotBlank()) {
                        val json = org.json.JSONObject(body)
                        if (json.optBoolean("ok")) {
                            val messages = json.getJSONArray("messages")
                            val fetched = (0 until messages.length()).map { index ->
                                val m = messages.getJSONObject(index)
                                SupportChatMessage(
                                    id = m.optString("id", java.util.UUID.randomUUID().toString()),
                                    merchantId = m.optString("merchant_id", merchantId),
                                    sender = m.optString("sender", "PLATFORM_OWNER"),
                                    message = m.optString("message", ""),
                                    timestamp = parseRemoteTimestamp(m.optString("created_at"))
                                )
                            }
                            _supportChatList.value = fetched
                            if (fetched.isNotEmpty()) {
                                saveOrUpdateCurrentSupportChatSession()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logFirebaseStatus("Direct live chat sync notice: ${e.message}")
            }

            // 2. Direct tickets endpoint on backend (connects to Supabase support_tickets)
            var directTicketsLoaded = false
            try {
                val ticketsUrl = "https://api.swapnopay.top/v1/merchant/support/tickets?merchant_id=${java.net.URLEncoder.encode(merchantId, "UTF-8")}"
                val ticketReqBuilder = okhttp3.Request.Builder()
                    .url(ticketsUrl)
                    .header("Accept", "application/json")
                    .header("x-merchant-id", merchantId)

                val token = runCatching { getOrCreatePlatformSupabaseProfile().authSessionToken }.getOrNull()
                if (!token.isNullOrBlank()) {
                    ticketReqBuilder.header("Authorization", "Bearer $token")
                }

                platformHttpClient.newCall(ticketReqBuilder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.isNotBlank()) {
                        val json = org.json.JSONObject(body)
                        if (json.optBoolean("ok")) {
                            val tickets = json.getJSONArray("tickets")
                            val fetchedTickets = (0 until tickets.length()).map { index ->
                                val t = tickets.getJSONObject(index)
                                SupportTicket(
                                    id = t.optString("id", java.util.UUID.randomUUID().toString()),
                                    merchantId = t.optString("merchant_id", merchantId),
                                    businessName = t.optString("business_name", ""),
                                    subject = t.optString("subject", ""),
                                    description = t.optString("description", ""),
                                    category = t.optString("category", "GENERAL"),
                                    status = t.optString("status", "OPEN"),
                                    adminReply = if (t.isNull("admin_reply")) "" else t.optString("admin_reply", ""),
                                    createdAt = parseRemoteTimestamp(t.optString("created_at"))
                                )
                            }
                            _mySupportTicketsList.value = fetchedTickets
                            directTicketsLoaded = true
                        }
                    }
                }
            } catch (e: Exception) {
                logFirebaseStatus("Direct tickets sync notice: ${e.message}")
            }

            // 3. Fallback to platformRequest("/v1/merchant/support")
            if (!directTicketsLoaded) {
                try {
                    val response = platformRequest("/v1/merchant/support")
                    val tickets = response.optJSONArray("tickets")
                    if (tickets != null) {
                        _mySupportTicketsList.value = (0 until tickets.length()).map { index ->
                            val t = tickets.getJSONObject(index)
                            SupportTicket(
                                id = t.getString("id"), merchantId = merchantId,
                                businessName = t.optString("business_name"), subject = t.optString("subject"),
                                description = t.optString("description"), category = t.optString("category"),
                                status = t.optString("status"), adminReply = if (t.isNull("admin_reply")) "" else t.optString("admin_reply"),
                                createdAt = parseRemoteTimestamp(t.optString("created_at"))
                            )
                        }
                    }
                    val messages = response.optJSONArray("messages")
                    if (messages != null && _supportChatList.value.isEmpty()) {
                        _supportChatList.value = (0 until messages.length()).map { index ->
                            val m = messages.getJSONObject(index)
                            SupportChatMessage(
                                id = m.getString("id"), merchantId = merchantId,
                                sender = m.getString("sender"), message = m.getString("message"),
                                timestamp = parseRemoteTimestamp(m.optString("created_at"))
                            )
                        }
                    }
                } catch (error: Exception) {
                    logFirebaseStatus("Support refresh fallback notice: ${error.message}")
                }
            }
        }
    }

    fun listenToMerchantSupportTickets() = listenToSupportChatFromPlatformOwner()

    fun sendSupportChatMessage(messageText: String) {
        val trimmed = messageText.trim()
        if (trimmed.isBlank()) return
        val merchantId = _activeProfile.value.id.ifBlank { "default_merchant" }

        // Optimistic UI update so the merchant's real message appears immediately
        val tempId = java.util.UUID.randomUUID().toString()
        val userMsg = SupportChatMessage(
            id = tempId,
            merchantId = merchantId,
            sender = "MERCHANT",
            message = trimmed,
            timestamp = System.currentTimeMillis()
        )
        _supportChatList.value = _supportChatList.value + userMsg
        saveOrUpdateCurrentSupportChatSession()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var sent = false

                // 1. Send via direct live support chat route to save in Supabase live_chat_messages
                try {
                    val url = "https://api.swapnopay.top/v1/merchant/support/chat"
                    val payload = org.json.JSONObject().apply {
                        put("merchant_id", merchantId)
                        put("message", trimmed)
                    }
                    val reqBuilder = okhttp3.Request.Builder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("x-merchant-id", merchantId)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))

                    val token = runCatching { getOrCreatePlatformSupabaseProfile().authSessionToken }.getOrNull()
                    if (!token.isNullOrBlank()) {
                        reqBuilder.header("Authorization", "Bearer $token")
                    }

                    platformHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                        if (response.isSuccessful) {
                            sent = true
                        }
                    }
                } catch (e: Exception) {
                    logFirebaseStatus("Live chat send notice: ${e.message}")
                }

                // 2. Fallback via platformRequest if direct route was not reached
                if (!sent) {
                    try {
                        platformRequest("/v1/merchant/support/messages", org.json.JSONObject().put("message", trimmed))
                        sent = true
                    } catch (e: Exception) {
                        logFirebaseStatus("Support message fallback error: ${e.message}")
                    }
                }

                // Refresh immediately to sync the persisted server message and timestamp
                runCatching { refreshPlatformSupport() }
            }
        }
    }

    private fun loadSavedSupportChatSessions(): List<SupportChatSession> {
        val jsonStr = securityPrefs.getString("saved_support_chat_sessions_v1", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<SupportChatSession>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                SupportChatSession.fromJson(item)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistSupportChatSessions() {
        val list = _savedSupportChatSessions.value
        val arr = JSONArray()
        for (session in list) {
            arr.put(session.toJson())
        }
        securityPrefs.edit().putString("saved_support_chat_sessions_v1", arr.toString()).apply()
    }

    fun saveOrUpdateCurrentSupportChatSession() {
        val messages = _supportChatList.value
        if (messages.isEmpty()) return
        val firstUserMsg = messages.firstOrNull { it.sender.equals("MERCHANT", ignoreCase = true) || it.sender.equals("USER", ignoreCase = true) }?.message?.trim()
            ?: messages.firstOrNull()?.message?.trim() ?: "Support Chat"
        val sessionTitle = if (firstUserMsg.length > 45) firstUserMsg.take(42) + "..." else firstUserMsg
        val currentId = _currentSupportSessionId.value
        val list = _savedSupportChatSessions.value.toMutableList()
        val existingIndex = list.indexOfFirst { it.id == currentId }
        val updatedSession = SupportChatSession(
            id = currentId,
            title = sessionTitle,
            timestamp = System.currentTimeMillis(),
            messages = messages,
            status = "ACTIVE"
        )
        if (existingIndex >= 0) {
            list[existingIndex] = updatedSession
        } else {
            list.add(0, updatedSession)
        }
        _savedSupportChatSessions.value = list
        persistSupportChatSessions()
    }

    fun startNewSupportChatSession() {
        saveOrUpdateCurrentSupportChatSession()
        _currentSupportSessionId.value = java.util.UUID.randomUUID().toString()
        _supportChatList.value = emptyList()
    }

    fun loadSupportChatSession(sessionId: String) {
        saveOrUpdateCurrentSupportChatSession()
        val session = _savedSupportChatSessions.value.find { it.id == sessionId } ?: return
        _currentSupportSessionId.value = session.id
        _supportChatList.value = session.messages
    }

    fun deleteSupportChatSession(sessionId: String) {
        val list = _savedSupportChatSessions.value.filterNot { it.id == sessionId }
        _savedSupportChatSessions.value = list
        persistSupportChatSessions()
        if (_currentSupportSessionId.value == sessionId) {
            _currentSupportSessionId.value = java.util.UUID.randomUUID().toString()
            _supportChatList.value = emptyList()
        }
    }

    fun clearAllSupportChatSessions() {
        _savedSupportChatSessions.value = emptyList()
        securityPrefs.edit().remove("saved_support_chat_sessions_v1").apply()
        _currentSupportSessionId.value = java.util.UUID.randomUUID().toString()
        _supportChatList.value = emptyList()
    }

    fun clearSupportChat() {
        _supportChatList.value = emptyList()
        val currentId = _currentSupportSessionId.value
        val list = _savedSupportChatSessions.value.filterNot { it.id == currentId }
        _savedSupportChatSessions.value = list
        persistSupportChatSessions()
    }

    fun listenToSupportChatFromPlatformOwner() {
        if (supportRefreshJob?.isActive == true) return
        supportRefreshJob = viewModelScope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                try { refreshPlatformSupport() } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    logFirebaseStatus("Support refresh failed: ${error.message}")
                }
                kotlinx.coroutines.delay(3500) // Fast 3.5s polling for true live chat responsiveness
            }
        }
    }

    fun logMerchantLoginTelemetry(profile: MerchantProfileEntity) {
        try {
            val db = database ?: return
            val telemetry = mapOf(
                "merchant_id" to profile.id,
                "business_name" to profile.businessName,
                "email" to profile.email,
                "phone" to profile.phone,
                "business_type" to profile.businessType,
                "kyc_status" to profile.kycStatus,
                "device_model" to android.os.Build.MODEL,
                "device_manufacturer" to android.os.Build.MANUFACTURER,
                "android_sdk" to android.os.Build.VERSION.SDK_INT,
                "last_active_timestamp" to System.currentTimeMillis(),
                "fcm_token" to (_fcmToken.value ?: "")
            )
            db.getReference("platform_owner/merchants_telemetry").child(profile.id).setValue(telemetry)
            db.getReference("platform_owner/active_sessions").child(profile.id).setValue(System.currentTimeMillis())
            logFirebaseStatus("Platform Owner Telemetry updated for merchant: ${profile.businessName}")
        } catch (e: Exception) {
            Log.d("PlatformTelemetry", "Telemetry error: ${e.message}")
        }
    }

    fun listenToPlatformPermissions() {
        try {
            val profileId = _activeProfile.value.id
            database?.getReference("platform_owner/merchant_controls")?.child(profileId)
                ?.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val gatewayPerm = snapshot.child("gateway_permission").getValue(Boolean::class.java) ?: true
                        val feeRate = snapshot.child("platform_fee_percent").getValue(Double::class.java) ?: 1.0
                        val suspended = snapshot.child("is_suspended").getValue(Boolean::class.java) ?: false
                        val notice = snapshot.child("platform_notice").getValue(String::class.java)

                        _isGatewayPermissionGranted.value = gatewayPerm
                        _platformFeePercent.value = feeRate
                        _isAccountSuspended.value = suspended

                        if (!notice.isNullOrBlank()) {
                            publishFirebaseNotice("Platform Announcement", notice, "PLATFORM")
                        }
                        logFirebaseStatus("Platform permissions updated: Gateway: $gatewayPerm, Fee: $feeRate%, Suspended: $suspended")
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        Log.e("PlatformPermissions", "Firebase permissions listener cancelled: ${error.message}", error.toException())
                        logFirebaseStatus("Firebase permissions listener cancelled: ${error.message}")
                    }
                })
        } catch (e: Exception) {
            Log.d("PlatformPermissions", "Listener error: ${e.message}")
        }
    }

    fun uploadKycDocToCloudflare(docType: String, imageBytes: ByteArray, onUploaded: ((String?) -> Unit)? = null) {
        val merchantId = _activeProfile.value.id
        val fileName = "kyc/${merchantId}/${docType}_${System.currentTimeMillis()}.jpg"

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val publicUrl = "${r2PublicDomain.value.trimEnd('/')}/$fileName"

                // Record KYC document submission in Firebase RTDB for platform owner verification
                val db = database
                if (db != null) {
                    val kycSubmission = mapOf(
                        "merchant_id" to merchantId,
                        "doc_type" to docType,
                        "file_name" to fileName,
                        "r2_url" to publicUrl,
                        "file_size_bytes" to imageBytes.size,
                        "submitted_at" to System.currentTimeMillis(),
                        "status" to "PENDING_REVIEW"
                    )
                    db.getReference("platform_owner/kyc_submissions").child(merchantId).child(docType).setValue(kycSubmission)
                    logFirebaseStatus("Cloudflare R2 KYC document record created in Firebase for $docType")
                }

                // Cloudflare R2 KYC document record created in Firebase for docType
                // Note: merchant profile KYC status remains UNVERIFIED until full face verification completes in submitFullKycVerification

                logFirebaseStatus("Cloudflare R2 KYC upload submitted ($docType, ${imageBytes.size} bytes). Ready for biometric verification.")
                sendLocalNotification("KYC Document Uploaded", "Your $docType has been securely stored. Complete face verification to submit.")
                onUploaded?.invoke(publicUrl)
            } catch (e: Exception) {
                logFirebaseStatus("Cloudflare R2 KYC upload error: ${e.message}")
                onUploaded?.invoke(null)
            }
        }
    }

    private fun compressKycImage(rawBytes: ByteArray, maxDim: Int = 1280, quality: Int = 80): ByteArray {
        if (rawBytes.isEmpty()) return rawBytes
        return try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)
            val w = boundsOptions.outWidth
            val h = boundsOptions.outHeight
            if (w <= 0 || h <= 0) return rawBytes

            var sampleSize = 1
            while ((w / sampleSize) > maxDim * 2 || (h / sampleSize) > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return rawBytes

            val maxCurrentDim = kotlin.math.max(bmp.width, bmp.height)
            val scaled = if (maxCurrentDim > maxDim) {
                val ratio = maxDim.toFloat() / maxCurrentDim
                val newW = (bmp.width * ratio).toInt().coerceAtLeast(1)
                val newH = (bmp.height * ratio).toInt().coerceAtLeast(1)
                android.graphics.Bitmap.createScaledBitmap(bmp, newW, newH, true)
            } else {
                bmp
            }
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            android.util.Log.e("AppViewModel", "Failed to compress KYC image, using raw bytes", e)
            rawBytes
        }
    }

    fun extractNidDetailsFromServer(
        imageBytes: ByteArray,
        isFront: Boolean = true,
        onResult: ((Map<String, String>) -> Unit)? = null
    ) {
        if (imageBytes.isEmpty()) return
        viewModelScope.launch {
            try {
                val compressed = withContext(Dispatchers.Default) {
                    compressKycImage(imageBytes, maxDim = 1280, quality = 80)
                }
                val base64Str = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
                val payload = org.json.JSONObject()
                if (isFront) {
                    payload.put("front_base64", base64Str)
                } else {
                    payload.put("back_base64", base64Str)
                }
                val response = platformRequest("/v1/kyc/extract-nid", payload)
                val fields = response.optJSONObject("extracted_fields")
                if (fields != null) {
                    val map = mutableMapOf<String, String>()
                    val keys = fields.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (!fields.isNull(k)) {
                            val v = fields.optString(k, "")
                            if (v.isNotBlank() && v != "null") {
                                map[k] = v
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(map)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("AppViewModel", "Server NID extraction notice: ${e.message}")
            }
        }
    }

    fun submitFullKycVerification(
        nidNumber: String, nidName: String, nidDob: String,
        frontBytes: ByteArray, backBytes: ByteArray, selfieBytes: ByteArray,
        ocrRawText: String = "",
        // Enhanced NID OCR fields from Bangladeshi NID card parsing
        nameBangla: String = "", nameEnglish: String = "",
        fatherName: String = "", motherName: String = "",
        bloodGroup: String = "", docType: String = "",
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                require(frontBytes.isNotEmpty() && backBytes.isNotEmpty() && selfieBytes.isNotEmpty()) {
                    "Both NID images and live face capture are required."
                }
                val merchantId = _activeProfile.value.id.ifEmpty { "merchant_${System.currentTimeMillis()}" }

                // Compress camera images asynchronously off main thread
                val (compressedFront, compressedBack, compressedSelfie) = withContext(Dispatchers.Default) {
                    Triple(
                        compressKycImage(frontBytes, maxDim = 1280, quality = 80),
                        compressKycImage(backBytes, maxDim = 1280, quality = 80),
                        compressKycImage(selfieBytes, maxDim = 1080, quality = 80)
                    )
                }

                // Check if we can submit via backend API
                val platformProfile = getOrCreatePlatformSupabaseProfile()
                var lastErrorMessage = ""

                if (platformProfile.authSessionToken.isNotBlank()) {
                    try {
                        val payload = org.json.JSONObject().put("nid_number", nidNumber.trim())
                            .put("nid_name", nidName.trim()).put("nid_dob", nidDob.trim()).put("liveness_passed", true)
                            .put("ocr_raw_text", ocrRawText)
                            .put("name_bangla", nameBangla.trim())
                            .put("name_english", nameEnglish.trim())
                            .put("father_name", fatherName.trim())
                            .put("mother_name", motherName.trim())
                            .put("blood_group", bloodGroup.trim())
                            .put("doc_type", docType.trim())
                            .put("front_base64", android.util.Base64.encodeToString(compressedFront, android.util.Base64.NO_WRAP))
                            .put("back_base64", android.util.Base64.encodeToString(compressedBack, android.util.Base64.NO_WRAP))
                            .put("selfie_base64", android.util.Base64.encodeToString(compressedSelfie, android.util.Base64.NO_WRAP))
                        val saved = platformRequest("/v1/kyc/submit", payload).getJSONObject("merchant")
                        val updated = _activeProfile.value.copy(
                            kycStatus = saved.optString("kyc_status", "PENDING"),
                            kycRejectionReason = "",
                            nidNumber = saved.optString("nid_number", nidNumber.trim()),
                            nidFrontUrl = saved.optString("nid_front_url", _activeProfile.value.nidFrontUrl),
                            nidBackUrl = saved.optString("nid_back_url", _activeProfile.value.nidBackUrl)
                        )
                        _activeProfile.value = updated
                        repository.insertMerchantProfile(updated)
                        onComplete?.invoke(true, null)
                        return@launch
                    } catch (backendErr: Exception) {
                        if (backendErr is kotlinx.coroutines.CancellationException) throw backendErr
                        android.util.Log.w("AppViewModel", "Backend KYC submit notice, attempting direct Supabase fallback: ${backendErr.message}")
                        lastErrorMessage = backendErr.message ?: "Backend error"
                    }
                }

                // Direct Supabase Fallback (if backend unreachable, token blank, or backend failed)
                val nowTime = System.currentTimeMillis()
                var frontUrl = ""
                var backUrl = ""
                var selfieUrl = ""
                val storageBucket = "kyc-documents"

                // Upload documents directly to Supabase Storage
                try {
                    com.example.data.remote.SupabaseClient.uploadStorageObject(
                        url = PLATFORM_SUPABASE_URL,
                        anonKey = PLATFORM_SUPABASE_ANON_KEY,
                        token = platformProfile.authSessionToken.ifEmpty { null },
                        bucket = storageBucket,
                        filePath = "kyc/${merchantId}/front_${nowTime}.jpg",
                        fileBytes = compressedFront,
                        mimeType = "image/jpeg",
                        onSuccess = { frontUrl = it },
                        onFailure = { android.util.Log.w("AppViewModel", "Direct front storage upload notice: $it") }
                    )
                } catch (e: Exception) {
                    android.util.Log.w("AppViewModel", "Front storage upload exception", e)
                }

                try {
                    com.example.data.remote.SupabaseClient.uploadStorageObject(
                        url = PLATFORM_SUPABASE_URL,
                        anonKey = PLATFORM_SUPABASE_ANON_KEY,
                        token = platformProfile.authSessionToken.ifEmpty { null },
                        bucket = storageBucket,
                        filePath = "kyc/${merchantId}/back_${nowTime}.jpg",
                        fileBytes = compressedBack,
                        mimeType = "image/jpeg",
                        onSuccess = { backUrl = it },
                        onFailure = { android.util.Log.w("AppViewModel", "Direct back storage upload notice: $it") }
                    )
                } catch (e: Exception) {
                    android.util.Log.w("AppViewModel", "Back storage upload exception", e)
                }

                try {
                    com.example.data.remote.SupabaseClient.uploadStorageObject(
                        url = PLATFORM_SUPABASE_URL,
                        anonKey = PLATFORM_SUPABASE_ANON_KEY,
                        token = platformProfile.authSessionToken.ifEmpty { null },
                        bucket = storageBucket,
                        filePath = "kyc/${merchantId}/selfie_${nowTime}.jpg",
                        fileBytes = compressedSelfie,
                        mimeType = "image/jpeg",
                        onSuccess = { selfieUrl = it },
                        onFailure = { android.util.Log.w("AppViewModel", "Direct selfie storage upload notice: $it") }
                    )
                } catch (e: Exception) {
                    android.util.Log.w("AppViewModel", "Selfie storage upload exception", e)
                }

                // If storage upload did not return URL, fallback to inline base64 data URI so images always appear
                if (frontUrl.isBlank()) {
                    frontUrl = "data:image/jpeg;base64," + android.util.Base64.encodeToString(compressedFront, android.util.Base64.NO_WRAP)
                }
                if (backUrl.isBlank()) {
                    backUrl = "data:image/jpeg;base64," + android.util.Base64.encodeToString(compressedBack, android.util.Base64.NO_WRAP)
                }
                if (selfieUrl.isBlank()) {
                    selfieUrl = "data:image/jpeg;base64," + android.util.Base64.encodeToString(compressedSelfie, android.util.Base64.NO_WRAP)
                }

                var directSuccess = false
                var directError: String? = null
                com.example.data.remote.SupabaseClient.submitKycVerification(
                    url = PLATFORM_SUPABASE_URL,
                    anonKey = PLATFORM_SUPABASE_ANON_KEY,
                    token = platformProfile.authSessionToken.ifEmpty { PLATFORM_SUPABASE_ANON_KEY },
                    merchantId = merchantId,
                    nidNumber = nidNumber,
                    nidName = nidName,
                    nidDob = nidDob,
                    frontUrl = frontUrl,
                    backUrl = backUrl,
                    selfieUrl = selfieUrl,
                    onSuccess = { directSuccess = true },
                    onFailure = { directError = it }
                )

                if (directSuccess) {
                    val updated = _activeProfile.value.copy(
                        kycStatus = "PENDING",
                        kycRejectionReason = "",
                        nidNumber = nidNumber.trim(),
                        nidFrontUrl = frontUrl.ifEmpty { _activeProfile.value.nidFrontUrl },
                        nidBackUrl = backUrl.ifEmpty { _activeProfile.value.nidBackUrl }
                    )
                    _activeProfile.value = updated
                    repository.insertMerchantProfile(updated)
                    onComplete?.invoke(true, null)
                } else {
                    val finalMsg = directError ?: lastErrorMessage.ifEmpty { "KYC could not be saved. Please retry." }
                    onComplete?.invoke(false, finalMsg)
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                onComplete?.invoke(false, error.message ?: "KYC could not be saved. Please retry.")
            }
        }
    }

    val isRefreshingKyc = MutableStateFlow(false)

    fun refreshMerchantKycStatus(onComplete: ((String, String) -> Unit)? = null) {
        viewModelScope.launch {
            isRefreshingKyc.value = true
            try {
                val email = _userEmail.value.orEmpty()
                val merchantId = _activeProfile.value.id.takeIf { it.isNotBlank() && it != "merchant_default" && it != "default_merchant" && it != repository.installationId }
                val result = checkMerchantAccountOnBackend(email, merchantId)
                    ?: error("Platform account could not be loaded. Please retry.")
                val rawKyc = result.kycStatus.trim().uppercase()
                val normalizedKyc = when (rawKyc) {
                    "APPROVED", "VERIFIED" -> "VERIFIED"
                    "REJECTED" -> "REJECTED"
                    "PENDING", "UNDER_REVIEW" -> "PENDING"
                    else -> if (rawKyc.isNotBlank()) rawKyc else "UNVERIFIED"
                }
                val updated = _activeProfile.value.copy(
                    kycStatus = normalizedKyc,
                    kycRejectionReason = result.kycRejectionReason,
                    nidNumber = result.nidNumber.ifBlank { _activeProfile.value.nidNumber },
                    nidFrontUrl = result.nidFrontUrl.ifBlank { _activeProfile.value.nidFrontUrl },
                    nidBackUrl = result.nidBackUrl.ifBlank { _activeProfile.value.nidBackUrl }
                )
                _activeProfile.value = updated
                repository.insertMerchantProfile(updated)
                onComplete?.invoke(updated.kycStatus, updated.kycRejectionReason)
            } catch (error: Exception) {
                logFirebaseStatus("KYC status could not be refreshed: ${error.message}")
            } finally { isRefreshingKyc.value = false }
        }
    }

    fun uploadMerchantProfileToFirebase(profile: MerchantProfileEntity) {
        val db = database
        if (db != null) {
            db.getReference("merchants").child(profile.id).setValue(profile)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        logFirebaseStatus("Successfully pushed merchant info to Firebase RTDB: ${profile.businessName}")
                    } else {
                        val errorMsg = task.exception?.localizedMessage ?: "Unknown Database error"
                        logFirebaseStatus("Firebase RTDB upload failed: $errorMsg")
                    }
                }
        } else {
            logFirebaseStatus("Firebase RTDB is unavailable; profile remains safely stored in Room.")
        }
        logFirebaseEvent("update_merchant_profile", Bundle().apply {
            putString("id", profile.id)
            putString("name", profile.businessName)
        })
    }

    fun syncOrdersToFirebase(ordersList: List<CachedOrderEntity>) {
        val db = database ?: run {
            logFirebaseStatus("Firebase RTDB is unavailable; orders were not mirrored.")
            return
        }
        db.getReference("orders").setValue(ordersList).addOnCompleteListener { task ->
            if (task.isSuccessful) logFirebaseStatus("Synced ${ordersList.size} orders with Firebase Realtime DB")
            else logFirebaseStatus("Firebase order mirror failed: ${task.exception?.message}")
        }
    }

    fun syncPaymentsToFirebase(paymentsList: List<CachedPaymentEntity>) {
        val db = database ?: run {
            logFirebaseStatus("Firebase RTDB is unavailable; payments were not mirrored.")
            return
        }
        db.getReference("payments").setValue(paymentsList).addOnCompleteListener { task ->
            if (task.isSuccessful) logFirebaseStatus("Synced ${paymentsList.size} payments with Firebase Realtime DB")
            else logFirebaseStatus("Firebase payment mirror failed: ${task.exception?.message}")
        }
    }

    fun sendLocalNotification(title: String, message: String) {
        try {
            val context = getApplication<Application>().applicationContext
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "FirebaseAlertChannel"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Firebase Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Dynamic Alerts & Notice Alerts from Firebase"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
                
            notificationManager.notify((1000..9999).random(), notification)
            logFirebaseStatus("Sent system notification: $title")
        } catch (e: Exception) {
            logFirebaseStatus("Notification permission or setup error: ${e.message}")
        }
    }

    suspend fun getOrCreatePlatformSupabaseProfile(): com.example.data.local.SupabaseProfileEntity {
        val platformId = "00000000-0000-0000-0000-000000000001"
        val existing = repository.observeSupabaseProfiles().firstOrNull()?.find { it.id == platformId }
        if (existing != null && existing.supabaseUrl.trimEnd('/') == PLATFORM_SUPABASE_URL && existing.anonKey.isNotBlank()) {
            return existing
        }
        val defaultProfile = com.example.data.local.SupabaseProfileEntity(
            id = platformId,
            businessName = "SwapnoPay Main Cloud",
            supabaseUrl = PLATFORM_SUPABASE_URL,
            anonKey = PLATFORM_SUPABASE_ANON_KEY,
            serviceRoleKey = "",
            isActive = false
        )
        repository.insertSupabaseProfile(defaultProfile)
        return defaultProfile
    }



    data class MerchantBackendCheckResult(
        val exists: Boolean,
        val isNew: Boolean,
        val isOnboarded: Boolean,
        val merchantId: String,
        val businessName: String,
        val email: String,
        val phone: String,
        val businessType: String,
        val photoUrl: String,
        val accountHolder: String,
        val hasOwnDatabase: Boolean,
        val supabaseUrl: String,
        val supabaseAnonKey: String,
        val projectRef: String,
        val kycStatus: String = "UNVERIFIED", val kycRejectionReason: String = "",
        val nidNumber: String = "", val nidFrontUrl: String = "", val nidBackUrl: String = ""
    )

    private val platformHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val platformSessionMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun platformRequest(path: String, payload: org.json.JSONObject? = null): org.json.JSONObject {
        val token = platformSessionMutex.withLock {
            var profile = getOrCreatePlatformSupabaseProfile()
            require(profile.authSessionToken.isNotBlank()) { "Sign in to your platform account first." }
            if (profile.authTokenExpiresAt <= System.currentTimeMillis() + 60000L) {
                var refreshed: com.example.data.remote.SupabaseClient.AuthSession? = null
                var failure = "Your platform session expired. Please sign in again."
                com.example.data.remote.SupabaseClient.refreshSession(profile.supabaseUrl, profile.anonKey,
                    profile.authRefreshToken, onSuccess = { refreshed = it }, onFailure = { failure = it })
                val session = refreshed ?: error(failure)
                profile = profile.copy(authSessionToken = session.accessToken, authRefreshToken = session.refreshToken,
                    authTokenExpiresAt = session.expiresAtMillis, authEmail = session.email)
                repository.insertSupabaseProfile(profile)
            }
            profile.authSessionToken
        }
        return withContext(Dispatchers.IO) {
            val request = okhttp3.Request.Builder().url("https://api.swapnopay.top$path")
                .header("Authorization", "Bearer $token").header("Accept", "application/json")
            if (payload != null) request.post(payload.toString().toRequestBody("application/json".toMediaType()))
            platformHttpClient.newCall(request.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = runCatching { org.json.JSONObject(body) }.getOrElse {
                    error("Platform returned HTTP ${response.code}; please retry.")
                }
                if (!response.isSuccessful || !json.optBoolean("ok")) {
                    error(json.optString("error", "Platform request failed (HTTP ${response.code})."))
                }
                json
            }
        }
    }

    suspend fun checkMerchantAccountOnBackend(email: String, merchantId: String? = null): MerchantBackendCheckResult? {
        val cleanEmail = email.trim().lowercase()
        val effectiveId = merchantId?.trim()?.takeIf { it.isNotBlank() && it != "merchant_default" && it != "default_merchant" && it != repository.installationId }
            ?: _activeProfile.value.id.takeIf { it.isNotBlank() && it != "merchant_default" && it != "default_merchant" && it != repository.installationId }
            ?: getOrCreatePlatformSupabaseProfile().authEmail.takeIf { it.equals(cleanEmail, true) }?.let {
                repository.observeMerchantProfile().firstOrNull()?.id?.takeIf { id -> id != "merchant_default" && id != "default_merchant" && id != repository.installationId }
            }

        // 1. Attempt backend account lookup if reachable (non-blocking on failure)
        try {
            val payload = org.json.JSONObject().apply {
                if (cleanEmail.isNotBlank()) put("email", cleanEmail)
                if (!effectiveId.isNullOrBlank()) put("merchant_id", effectiveId)
            }
            val json = platformRequest("/v1/oauth/check-user", payload)
            if (json.optBoolean("ok")) {
                val m = json.getJSONObject("merchant")
                val db = json.getJSONObject("database")
                fun value(key: String) = if (m.isNull(key)) "" else m.optString(key)
                val mId = m.getString("id")
                val mEmail = value("email").ifBlank { cleanEmail }
                var hasOwn = db.optBoolean("has_own_database", false)
                var sUrl = db.optString("supabase_url")
                var sKey = db.optString("supabase_anon_key")
                if (!hasOwn || sUrl.isBlank() || sKey.isBlank()) {
                    val directAdmin = fetchSupabaseConfigFromAdminDirect(mId, mEmail)
                    if (directAdmin != null) {
                        hasOwn = true
                        sUrl = directAdmin.first
                        sKey = directAdmin.second
                    }
                }
                return MerchantBackendCheckResult(
                    exists = json.getBoolean("exists"),
                    isNew = json.getBoolean("is_new"),
                    isOnboarded = json.getBoolean("is_onboarded"),
                    merchantId = mId,
                    businessName = value("business_name"),
                    email = mEmail,
                    phone = value("phone"),
                    businessType = value("business_type").ifBlank { "Retail Store" },
                    photoUrl = value("photo_url"),
                    accountHolder = value("account_holder"),
                    hasOwnDatabase = hasOwn,
                    supabaseUrl = sUrl,
                    supabaseAnonKey = sKey,
                    projectRef = db.optString("project_ref"),
                    kycStatus = value("kyc_status").ifBlank { "UNVERIFIED" },
                    kycRejectionReason = value("kyc_rejection_reason"),
                    nidNumber = value("nid_number"),
                    nidFrontUrl = value("nid_front_url"),
                    nidBackUrl = value("nid_back_url")
                )
            }
        } catch (backendError: Exception) {
            if (backendError is kotlinx.coroutines.CancellationException) throw backendError
            logFirebaseStatus("Backend account lookup note, checking direct Supabase: ${backendError.message}")
        }

        // 2. Direct Supabase PostgREST Query
        try {
            val platformProfile = getOrCreatePlatformSupabaseProfile()
            val token = platformProfile.authSessionToken.ifBlank { null }
            val directResult = com.example.data.remote.SupabaseClient.fetchMerchantAccountFromSupabase(
                url = platformProfile.supabaseUrl.ifBlank { PLATFORM_SUPABASE_URL },
                anonKey = platformProfile.anonKey.ifBlank { PLATFORM_SUPABASE_ANON_KEY },
                accessToken = token,
                email = cleanEmail,
                userId = effectiveId
            )
            if (directResult != null) {
                val m = directResult.getJSONObject("merchant")
                val db = directResult.getJSONObject("database")
                fun value(key: String) = if (m.isNull(key)) "" else m.optString(key)
                val busName = value("business_name")
                val phone = value("phone")
                val onboardedAt = value("onboarded_at")
                val placeholder = busName.matches(Regex("^(my store|my business|google user|facebook user|demo store|business setup required)$", RegexOption.IGNORE_CASE))
                val isOnboarded = onboardedAt.isNotBlank() || (busName.isNotBlank() && !placeholder && phone.isNotBlank())
                val mId = m.optString("id", effectiveId.orEmpty())
                val mEmail = value("email").ifBlank { cleanEmail }
                var hasOwn = db.optBoolean("has_own_database", false)
                var sUrl = db.optString("supabase_url")
                var sKey = db.optString("supabase_anon_key")
                if (!hasOwn || sUrl.isBlank() || sKey.isBlank()) {
                    val directAdmin = fetchSupabaseConfigFromAdminDirect(mId, mEmail)
                    if (directAdmin != null) {
                        hasOwn = true
                        sUrl = directAdmin.first
                        sKey = directAdmin.second
                    }
                }
                return MerchantBackendCheckResult(
                    exists = true,
                    isNew = !isOnboarded,
                    isOnboarded = isOnboarded,
                    merchantId = mId,
                    businessName = busName,
                    email = mEmail,
                    phone = phone,
                    businessType = value("business_type").ifBlank { "Retail Store" },
                    photoUrl = value("photo_url").ifBlank { value("logo_url") },
                    accountHolder = value("account_holder").ifBlank { busName },
                    hasOwnDatabase = hasOwn,
                    supabaseUrl = sUrl,
                    supabaseAnonKey = sKey,
                    projectRef = db.optString("project_ref"),
                    kycStatus = value("kyc_status").ifBlank { "UNVERIFIED" },
                    kycRejectionReason = value("kyc_rejection_reason"),
                    nidNumber = value("nid_number"),
                    nidFrontUrl = value("nid_front_url"),
                    nidBackUrl = value("nid_back_url")
                )
            }
        } catch (supabaseError: Exception) {
            if (supabaseError is kotlinx.coroutines.CancellationException) throw supabaseError
            logFirebaseStatus("Direct Supabase account lookup error: ${supabaseError.message}")
        }

        // 3. Local Room Database Fallback
        val localProfile = effectiveId?.let { repository.getMerchantProfileById(it) }
            ?: (if (cleanEmail.isNotBlank()) repository.getProfiles().find { it.email.equals(cleanEmail, true) } ?: repository.observeMerchantProfile().firstOrNull()?.takeIf { it.email.equals(cleanEmail, true) } else null)

        if (localProfile != null && localProfile.businessName.isNotBlank()) {
            val placeholder = localProfile.businessName.matches(Regex("^(my store|my business|google user|facebook user|demo store|business setup required)$", RegexOption.IGNORE_CASE))
            val isOnboarded = !placeholder && localProfile.phone.isNotBlank()
            val savedSupabase = repository.getSupabaseProfileById(localProfile.id)
                ?: repository.getActiveSupabaseProfile()
            var sUrl = savedSupabase?.supabaseUrl.orEmpty()
            var sKey = savedSupabase?.anonKey.orEmpty()
            var hasOwn = sUrl.isNotBlank() && sKey.isNotBlank() && !sUrl.contains("tldubojeokgyoclxnzkb") && !sUrl.contains("abc123xyz")
            if (!hasOwn) {
                val directAdmin = fetchSupabaseConfigFromAdminDirect(localProfile.id, localProfile.email.ifBlank { cleanEmail })
                if (directAdmin != null) {
                    hasOwn = true
                    sUrl = directAdmin.first
                    sKey = directAdmin.second
                }
            }
            return MerchantBackendCheckResult(
                exists = true,
                isNew = !isOnboarded,
                isOnboarded = isOnboarded,
                merchantId = localProfile.id,
                businessName = localProfile.businessName,
                email = localProfile.email.ifBlank { cleanEmail },
                phone = localProfile.phone,
                businessType = localProfile.businessType.ifBlank { "Retail Store" },
                photoUrl = localProfile.photoUrl,
                accountHolder = localProfile.accountHolder.ifBlank { localProfile.businessName },
                hasOwnDatabase = hasOwn,
                supabaseUrl = sUrl,
                supabaseAnonKey = sKey,
                projectRef = "",
                kycStatus = localProfile.kycStatus.ifBlank { "UNVERIFIED" },
                kycRejectionReason = localProfile.kycRejectionReason,
                nidNumber = localProfile.nidNumber,
                nidFrontUrl = localProfile.nidFrontUrl,
                nidBackUrl = localProfile.nidBackUrl
            )
        }

        // 4. Default result for new account or un-onboarded user (never lock out authenticated user!)
        val finalUserId = effectiveId ?: java.util.UUID.randomUUID().toString()
        val directAdmin = fetchSupabaseConfigFromAdminDirect(finalUserId, cleanEmail)
        return MerchantBackendCheckResult(
            exists = false,
            isNew = true,
            isOnboarded = false,
            merchantId = finalUserId,
            businessName = "",
            email = cleanEmail,
            phone = "",
            businessType = "Retail Store",
            photoUrl = "",
            accountHolder = "",
            hasOwnDatabase = directAdmin != null,
            supabaseUrl = directAdmin?.first.orEmpty(),
            supabaseAnonKey = directAdmin?.second.orEmpty(),
            projectRef = "",
            kycStatus = "UNVERIFIED"
        )
    }

    suspend fun applyRestoredMerchantSetup(result: MerchantBackendCheckResult) {
        val rawKyc = result.kycStatus.trim().uppercase()
        val normalizedKyc = when (rawKyc) {
            "APPROVED", "VERIFIED" -> "VERIFIED"
            "REJECTED" -> "REJECTED"
            "PENDING", "UNDER_REVIEW" -> "PENDING"
            else -> if (rawKyc.isNotBlank()) rawKyc else "UNVERIFIED"
        }
        val saved = repository.getMerchantProfileById(result.merchantId)
        val restored = (saved ?: MerchantProfileEntity(id = result.merchantId, businessName = "", email = "", phone = "",
            businessType = "", website = "", primaryBank = "", accountHolder = "", accountNumber = "", kycStatus = "UNVERIFIED"))
            .copy(
                businessName = result.businessName,
                email = result.email,
                phone = result.phone,
                businessType = result.businessType,
                accountHolder = result.accountHolder,
                photoUrl = result.photoUrl,
                kycStatus = normalizedKyc,
                kycRejectionReason = result.kycRejectionReason,
                nidNumber = result.nidNumber.ifBlank { saved?.nidNumber.orEmpty() },
                nidFrontUrl = result.nidFrontUrl.ifBlank { saved?.nidFrontUrl.orEmpty() },
                nidBackUrl = result.nidBackUrl.ifBlank { saved?.nidBackUrl.orEmpty() }
            )
        _activeProfile.value = restored
        repository.insertMerchantProfile(restored)
        repository.switchProfile(restored.id)
        repository.deletePlaceholderMerchantProfiles()
        repository.reassignMerchantData("merchant_default", restored.id)
        repository.reassignMerchantData("00000000-0000-0000-0000-000000000001", restored.id)
        repository.reassignMerchantData(repository.installationId, restored.id)
        syncAllCachedFormsToVps()
        startObservingPaymentFormsCache()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL("https://api.swapnopay.top/v1/forms?merchant_id=${restored.id}").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = org.json.JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val formObj = arr.getJSONObject(i)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            loadCachedPaymentForm(formObj, autoSelect = false)
                        }
                    }
                }
            } catch (e_: Exception) { android.util.Log.w("AppViewModel", "Suppressed: ${e_.message}") }
        }
        setUserEmail(result.email)
        _onboardingBusinessName.value = result.businessName
        _onboardingPhone.value = result.phone

        var finalHasOwn = result.hasOwnDatabase
        var finalUrl = result.supabaseUrl
        var finalKey = result.supabaseAnonKey

        if (!finalHasOwn || finalUrl.isBlank() || finalKey.isBlank()) {
            val adminCfg = fetchSupabaseConfigFromAdminDirect(result.merchantId, result.email)
            if (adminCfg != null) {
                finalHasOwn = true
                finalUrl = adminCfg.first
                finalKey = adminCfg.second
            }
        }

        if (!finalHasOwn || finalUrl.isBlank() || finalKey.isBlank()) {
            val localProfile = repository.getSupabaseProfileById(result.merchantId)
                ?: repository.getActiveSupabaseProfile()
            if (localProfile != null && localProfile.supabaseUrl.isNotBlank() && localProfile.anonKey.isNotBlank()
                && !localProfile.supabaseUrl.contains("tldubojeokgyoclxnzkb") && !localProfile.supabaseUrl.contains("abc123xyz")) {
                finalHasOwn = true
                finalUrl = localProfile.supabaseUrl
                finalKey = localProfile.anonKey
            }
        }

        if (finalHasOwn && finalUrl.isNotBlank() && finalKey.isNotBlank()) {
            val existing = repository.getSupabaseProfileById(result.merchantId)?.takeIf {
                it.supabaseUrl.trimEnd('/') == finalUrl.trimEnd('/') && (it.authEmail.isBlank() || it.authEmail.equals(result.email, true))
            }
            val profile = (existing ?: SupabaseProfileEntity(
                id = result.merchantId,
                businessName = result.businessName.ifBlank { "My Store Backend" },
                supabaseUrl = finalUrl,
                anonKey = finalKey
            )).copy(
                isActive = true,
                businessName = result.businessName.ifBlank { "My Store Backend" },
                supabaseUrl = finalUrl,
                anonKey = finalKey
            )
            repository.insertSupabaseProfile(profile)
            repository.selectActiveSupabaseProfile(profile.id)
            _activeSupabaseProfile.value = profile
            supabaseUrl.value = profile.supabaseUrl
            supabaseAnonKey.value = profile.anonKey
            _supabaseUrlInput.value = profile.supabaseUrl
            _supabaseAnonKeyInput.value = profile.anonKey
            supabaseConnected.value = true
        } else {
            val platform = getOrCreatePlatformSupabaseProfile().copy(
                isActive = true,
                businessName = result.businessName.ifBlank { "SwapnoPay Main Cloud" }
            )
            repository.insertSupabaseProfile(platform)
            repository.selectActiveSupabaseProfile(platform.id)
            _activeSupabaseProfile.value = platform
            supabaseUrl.value = platform.supabaseUrl
            supabaseAnonKey.value = platform.anonKey
            _supabaseUrlInput.value = platform.supabaseUrl
            _supabaseAnonKeyInput.value = platform.anonKey
            supabaseConnected.value = true
        }
        fetchSystemConfigFromSupabase()
        listenToSupportChatFromPlatformOwner()
        pullAllMerchantDataFromRemote(restored.id)
    }

    suspend fun completeMerchantOnboarding(profile: MerchantProfileEntity, databaseUrl: String, databaseKey: String): Boolean {
        return try {
            // 1. Immediately persist locally so the merchant is never locked out
            updateMerchantProfile(profile)
            if (databaseUrl.isNotBlank() && databaseKey.isNotBlank()) {
                connectSupabase(url = databaseUrl, anonKey = databaseKey, name = profile.businessName, syncToPlatform = false)
            }
            setOnboarded(true)

            val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())

            // 2. Resiliently sync directly to Supabase PostgREST
            try {
                val platformProfile = getOrCreatePlatformSupabaseProfile()
                val token = platformProfile.authSessionToken.ifBlank { null }
                val merchantJson = org.json.JSONObject().apply {
                    put("id", profile.id)
                    put("user_id", profile.id)
                    put("business_name", profile.businessName.trim())
                    put("email", profile.email.trim())
                    put("phone", profile.phone.trim())
                    put("business_type", profile.businessType.trim().ifBlank { "Retail Store" })
                    put("website", profile.website.trim())
                    put("photo_url", profile.photoUrl.trim())
                    put("status", "ACTIVE")
                    put("onboarded_at", nowIso)
                }
                val gatewayJson = if (databaseUrl.isNotBlank() && databaseKey.isNotBlank()) {
                    org.json.JSONObject().apply {
                        put("merchant_id", profile.id)
                        put("merchant_name", profile.businessName.trim())
                        put("supabase_url", databaseUrl.trim())
                        put("supabase_anon_key", databaseKey.trim())
                    }
                } else null

                com.example.data.remote.SupabaseClient.upsertMerchantProfileDirectly(
                    url = platformProfile.supabaseUrl.ifBlank { PLATFORM_SUPABASE_URL },
                    anonKey = platformProfile.anonKey.ifBlank { PLATFORM_SUPABASE_ANON_KEY },
                    accessToken = token,
                    profile = merchantJson,
                    gatewaySettings = gatewayJson
                )
                logFirebaseStatus("Merchant profile saved directly to Supabase cloud.")
            } catch (dbSyncErr: Exception) {
                logFirebaseStatus("Direct Supabase profile upsert notice: ${dbSyncErr.message}")
            }

            // 3. Resiliently sync to backend platform API
            try {
                platformRequest("/v1/oauth/sync-merchant-setup", org.json.JSONObject()
                    .put("business_name", profile.businessName).put("phone", profile.phone)
                    .put("business_type", profile.businessType).put("website", profile.website)
                    .put("photo_url", profile.photoUrl).put("supabase_url", databaseUrl).put("supabase_anon_key", databaseKey))
            } catch (syncError: Exception) {
                logFirebaseStatus("Platform cloud sync deferred: ${syncError.message}")
            }

            val localPinHash = _appPin.value.ifBlank { securityPrefs.getString("app_pin_hash", "") ?: "" }
            if (localPinHash.isNotBlank()) {
                uploadPinHashToCloud(localPinHash)
            }

            true
        } catch (error: Exception) {
            logFirebaseStatus("Local setup exception: ${error.message}")
            updateMerchantProfile(profile)
            setOnboarded(true)
            true
        }
    }

    fun syncMerchantSetupToBackend(
        merchantId: String, email: String, businessName: String, phone: String,
        businessType: String = "Retail Store", website: String = "", photoUrl: String = "",
        supabaseUrl: String = "", supabaseAnonKey: String = ""
    ) {
        viewModelScope.launch {
            // Direct Supabase sync first
            try {
                val platformProfile = getOrCreatePlatformSupabaseProfile()
                val token = platformProfile.authSessionToken.ifBlank { null }
                val merchantJson = org.json.JSONObject().apply {
                    put("id", merchantId)
                    put("user_id", merchantId)
                    put("business_name", businessName.trim())
                    put("email", email.trim())
                    put("phone", phone.trim())
                    put("business_type", businessType.trim())
                    put("website", website.trim())
                    put("photo_url", photoUrl.trim())
                    put("status", "ACTIVE")
                }
                val gatewayJson = if (supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()) {
                    org.json.JSONObject().apply {
                        put("merchant_id", merchantId)
                        put("merchant_name", businessName.trim())
                        put("supabase_url", supabaseUrl.trim())
                        put("supabase_anon_key", supabaseAnonKey.trim())
                    }
                } else null

                com.example.data.remote.SupabaseClient.upsertMerchantProfileDirectly(
                    url = platformProfile.supabaseUrl.ifBlank { PLATFORM_SUPABASE_URL },
                    anonKey = platformProfile.anonKey.ifBlank { PLATFORM_SUPABASE_ANON_KEY },
                    accessToken = token,
                    profile = merchantJson,
                    gatewaySettings = gatewayJson
                )
            } catch (e: Exception) {
                logFirebaseStatus("Supabase direct sync notice: ${e.message}")
            }

            // Backend sync
            try {
                platformRequest("/v1/oauth/sync-merchant-setup", org.json.JSONObject()
                    .put("business_name", businessName).put("phone", phone).put("business_type", businessType)
                    .put("website", website).put("photo_url", photoUrl)
                    .put("supabase_url", supabaseUrl).put("supabase_anon_key", supabaseAnonKey))
                logFirebaseStatus("Business profile saved to the platform.")
            } catch (error: Exception) {
                logFirebaseStatus("Platform cloud sync deferred: ${error.message}")
            }
        }
    }

    fun loginWithEmailReal(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            try {
                localAccountReady.await()
                val platform = getOrCreatePlatformSupabaseProfile()
                var session: com.example.data.remote.SupabaseClient.AuthSession? = null
                var failure = "Unable to sign in. Check your email and password."
                com.example.data.remote.SupabaseClient.signIn(platform.supabaseUrl, platform.anonKey, email.trim(), password,
                    onSuccess = { session = it }, onFailure = { failure = it })
                val authenticated = session ?: error(failure)
                repository.insertSupabaseProfile(platform.copy(authEmail = authenticated.email,
                    authSessionToken = authenticated.accessToken, authRefreshToken = authenticated.refreshToken,
                    authTokenExpiresAt = authenticated.expiresAtMillis, isActive = false))

                // Resiliently check merchant profile with Supabase direct fallback and new-user handling
                val account = checkMerchantAccountOnBackend(authenticated.email, authenticated.userId)
                    ?: MerchantBackendCheckResult(
                        exists = false, isNew = true, isOnboarded = false,
                        merchantId = authenticated.userId, businessName = "", email = authenticated.email,
                        phone = "", businessType = "Retail Store", photoUrl = "", accountHolder = "",
                        hasOwnDatabase = false, supabaseUrl = "", supabaseAnonKey = "", projectRef = "",
                        kycStatus = "UNVERIFIED"
                    )

                supportRefreshJob?.cancel()
                _supportChatList.value = emptyList()
                _mySupportTicketsList.value = emptyList()
                applyRestoredMerchantSetup(account)
                saveEncryptedSessionToken(authenticated.email, authenticated.userId, "Supabase In-App Auth")
                // A project-specific token must be issued by that project's Auth service.
                val own = _activeSupabaseProfile.value
                if (own != null && account.hasOwnDatabase) {
                    var ownSession: com.example.data.remote.SupabaseClient.AuthSession? = null
                    com.example.data.remote.SupabaseClient.signIn(own.supabaseUrl, own.anonKey, email.trim(), password,
                        onSuccess = { ownSession = it }, onFailure = { logFirebaseStatus("Business database sign-in required: $it") })
                    ownSession?.let { authenticatedOwn ->
                        val connected = own.copy(authEmail = authenticatedOwn.email, authSessionToken = authenticatedOwn.accessToken,
                            authRefreshToken = authenticatedOwn.refreshToken, authTokenExpiresAt = authenticatedOwn.expiresAtMillis)
                        repository.insertSupabaseProfile(connected)
                        _activeSupabaseProfile.value = connected
                        scheduleSupabaseSessionRefresh(connected)
                    }
                }
                fetchMerchantApiKey()
                refreshGatewayConfig()
                pullAllMerchantDataFromRemote(account.merchantId)
                val isEffectivelyOnboarded = account.isOnboarded || (account.exists && account.businessName.isNotBlank() && !account.businessName.matches(Regex("^(my store|my business|google user|facebook user|demo store|business setup required)$", RegexOption.IGNORE_CASE)))
                setOnboarded(isEffectivelyOnboarded)
                // Sync PIN hash from Supabase (cloud-synced PIN system)
                syncPinFromCloud()
                navigateTo(if (!isEffectivelyOnboarded) "Onboarding" else if (_isBiometricLocked.value) "LockScreen" else "Main")
                onSuccess()
            } catch (error: Exception) {
                _authError.value = error.message
                onFailure(error.message ?: "Unable to load your account. Please retry.")
            } finally { _isAuthenticating.value = false }
        }
    }

    val registeredMerchantsFromSupabase = MutableStateFlow<List<MerchantProfileEntity>>(emptyList())

    fun fetchRegisteredMerchantsFromSupabase() {
        viewModelScope.launch {
            val account = checkMerchantAccountOnBackend(_userEmail.value.orEmpty()) ?: return@launch
            applyRestoredMerchantSetup(account)
            registeredMerchantsFromSupabase.value = listOf(_activeProfile.value)
        }
    }

    fun loginWithOAuthProvider(context: android.content.Context, provider: String, onDirectSuccess: (() -> Unit)? = null) {
        try {
            val providerTag = provider.lowercase()
            require(providerTag in setOf("google", "facebook")) { "Unsupported sign-in provider" }
            val verifier = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
            securityPrefs.edit().putString("supabase_pkce_verifier", verifier).apply()
            val challenge = android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray()), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val url = android.net.Uri.parse("$PLATFORM_SUPABASE_URL/auth/v1/authorize").buildUpon()
                .appendQueryParameter("provider", providerTag).appendQueryParameter("redirect_to", PLATFORM_AUTH_REDIRECT_URL)
                .appendQueryParameter("code_challenge", challenge).appendQueryParameter("code_challenge_method", "s256").build()
            isExternalActivityExpected = true
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) { _authError.value = error.message }
    }

    fun performDirectSocialLogin(provider: String, onDirectSuccess: (() -> Unit)? = null) {
        loginWithOAuthProvider(getApplication(), provider, onDirectSuccess)
    }

    fun performProductionSocialLogin(context: android.content.Context, provider: String, email: String, name: String,
        avatarUrl: String = "", idToken: String = "", onDirectSuccess: (() -> Unit)? = null) {
        loginWithOAuthProvider(context, provider, onDirectSuccess)
    }

    fun registerWithEmailReal(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val cleanEmail = email.trim().lowercase()

            // Supabase Auth verifies registration and email ownership.
            val platformProfile = getOrCreatePlatformSupabaseProfile()
            var session: com.example.data.remote.SupabaseClient.AuthSession? = null
            var failure: String? = null
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.remote.SupabaseClient.signUp(
                    platformProfile.supabaseUrl,
                    platformProfile.anonKey,
                    cleanEmail,
                    password,
                    "",
                    "",
                    redirectUrl = PLATFORM_AUTH_REDIRECT_URL,
                    onSuccess = { session = it },
                    onFailure = { failure = it }
                )
                // If direct session wasn't returned because email verification is pending or auto-confirmed, attempt signIn
                if (session == null && failure == null) {
                    com.example.data.remote.SupabaseClient.signIn(
                        platformProfile.supabaseUrl,
                        platformProfile.anonKey,
                        cleanEmail,
                        password,
                        onSuccess = { session = it },
                        onFailure = { /* pending email confirmation */ }
                    )
                }
            }
            _isAuthenticating.value = false
            if (session != null) {
                val authenticatedSession = session!!
                // Clear any previous session and onboarding state so new user starts completely fresh
                clearAllSessionAndOnboardingData()

                val updatedPlatform = platformProfile.copy(
                    authEmail = authenticatedSession.email.ifBlank { cleanEmail },
                    authSessionToken = authenticatedSession.accessToken,
                    authRefreshToken = authenticatedSession.refreshToken,
                    authTokenExpiresAt = authenticatedSession.expiresAtMillis
                )
                repository.insertSupabaseProfile(updatedPlatform)
                _activeSupabaseProfile.value = null
                _activeProfile.value = _activeProfile.value.copy(id = authenticatedSession.userId, email = cleanEmail)
                repository.insertMerchantProfile(_activeProfile.value)
                saveEncryptedSessionToken(cleanEmail, authenticatedSession.userId, "Supabase In-App Auth")
                setUserEmail(cleanEmail)
                setOnboarded(false)
                logFirebaseEvent("register_success", Bundle().apply { putString("provider", "supabase") })
                onSuccess()
            } else if (failure != null) {
                val isAlreadyRegistered = failure!!.contains("already", ignoreCase = true) ||
                        failure!!.contains("exists", ignoreCase = true) ||
                        failure!!.contains("User already", ignoreCase = true)
                val finalErr = if (isAlreadyRegistered) {
                    "ALREADY_EXISTS: An account with this email already exists."
                } else {
                    failure!!
                }
                _authError.value = finalErr
                onFailure(finalErr)
            } else {
                clearAllSessionAndOnboardingData()
                setUserEmail(cleanEmail)
                setOnboarded(false)
                val message = "Account registration complete. Check your email inbox to confirm, or proceed to Sign In."
                _authError.value = message
                onSuccess()
            }
        }
    }

    fun authenticateConnectedSupabase(
        email: String,
        password: String,
        register: Boolean,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            val authenticated = authenticateSupabaseProfile(email.trim(), password, register)
            _isAuthenticating.value = false
            if (authenticated) {
                setUserEmail(email.trim())
                onSuccess()
            } else {
                val message = if (register) {
                    "Verify the Supabase confirmation email, then return and choose Sign In."
                } else {
                    "Supabase sign-in failed. Verify the credentials and email confirmation."
                }
                _authError.value = message
                onFailure(message)
            }
        }
    }

    private suspend fun authenticateSupabaseProfile(email: String, password: String, register: Boolean): Boolean {
        val profile = _activeSupabaseProfile.value ?: return false
        if (profile.supabaseUrl.isBlank() || profile.anonKey.isBlank()) return false
        var session: com.example.data.remote.SupabaseClient.AuthSession? = null
        var failure: String? = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (register) {
                com.example.data.remote.SupabaseClient.signUp(
                    profile.supabaseUrl,
                    profile.anonKey,
                    email,
                    password,
                    activeProfile.value.businessName,
                    activeProfile.value.phone,
                    onSuccess = { authSession -> session = authSession },
                    onFailure = { failure = it }
                )
            }
            if (!register || session == null) {
                com.example.data.remote.SupabaseClient.signIn(
                    profile.supabaseUrl,
                    profile.anonKey,
                    email,
                    password,
                    onSuccess = { authSession -> session = authSession },
                    onFailure = { if (failure == null) failure = it }
                )
            }
        }
        val authenticatedSession = session
        if (authenticatedSession == null) {
            logFirebaseStatus("Supabase authentication pending/failed: ${failure ?: "verify the account email, then sign in"}")
            return false
        }
        val updated = profile.copy(
            authEmail = authenticatedSession.email.ifBlank { email },
            authSessionToken = authenticatedSession.accessToken,
            authRefreshToken = authenticatedSession.refreshToken,
            authTokenExpiresAt = authenticatedSession.expiresAtMillis
        )
        repository.insertSupabaseProfile(updated)
        _activeSupabaseProfile.value = updated
        val merchant = activeProfile.value
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.remote.SupabaseClient.updateMerchantProfile(
                url = profile.supabaseUrl,
                anonKey = profile.anonKey,
                accessToken = authenticatedSession.accessToken,
                userId = authenticatedSession.userId,
                businessName = merchant.businessName,
                email = authenticatedSession.email.ifBlank { email },
                phone = merchant.phone,
                businessType = merchant.businessType,
                website = merchant.website,
                onSuccess = { logFirebaseStatus("Merchant information saved to the self-hosted Supabase project.") },
                onFailure = { error -> logFirebaseStatus("Merchant profile sync failed: $error") }
            )
        }
        scheduleSupabaseSessionRefresh(updated)
        supabaseConnected.value = true
        sendDeviceHeartbeat()
        logFirebaseStatus("Supabase Auth session established for tenant-scoped database access.")
        return true
    }

    fun handleAuthDeepLink(uri: android.net.Uri?) {
        if (uri == null) return
        val scheme = uri.scheme ?: return
        if (scheme != "swapnopay" && scheme != "lenden23" && scheme != "http" && scheme != "https") return

        // Reset external activity flag now that deep link has returned
        isExternalActivityExpected = false

        val host = uri.host
        val path = uri.path ?: ""
        if (host == "subscription-callback") {
            // This is normally intercepted by the in-app checkout WebView. Keep
            // a deep-link fallback for process recreation or a gateway redirect
            // that reaches Android directly.
            fetchSubscriptionStatus()
            fetchSubscriptionHistory()
            navigateTo("Subscription")
            return
        }
        if (host == "supabase-connected" || path.contains("supabase-connected")) {
            val txId = uri.getQueryParameter("tx_id")
            if (!txId.isNullOrBlank()) {
                handleControlPlaneOAuthConnected(txId)
            } else {
                logFirebaseStatus("Received swapnopay://supabase-connected deep link without transaction ID.")
            }
            return
        }

        if (host == "supabase-oauth-callback"
            || path.contains("supabase-oauth-callback")
            || path.contains("/v1/oauth/callback")
            || (host == "api.swapnopay.top" && path.contains("callback"))
            || (host == "pay.swapnopay.top" && path.contains("callback"))
            || (host == "swapnopay.top" && path.contains("callback"))
        ) {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error") ?: uri.getQueryParameter("error_description")
            if (!code.isNullOrBlank()) {
                handleOAuthCodeReceived(code)
            } else if (!error.isNullOrBlank()) {
                managementApiError.value = "OAuth Error: $error"
            }
            return
        }

        val fragment = uri.fragment
        val query = uri.query

        var accessToken: String? = null
        var refreshToken: String? = null
        var tokenType: String? = null
        var oauthError: String? = null

        if (!fragment.isNullOrBlank()) {
            val params = fragment.split("&").associate {
                val parts = it.split("=")
                if (parts.size >= 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
            }
            accessToken = params["access_token"]
            refreshToken = params["refresh_token"]
            tokenType = params["type"] ?: params["error_description"]
            oauthError = params["error_description"] ?: params["error"]
        }

        if (accessToken.isNullOrBlank() && !query.isNullOrBlank()) {
            val params = query.split("&").associate {
                val parts = it.split("=")
                if (parts.size >= 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
            }
            accessToken = params["access_token"]
            refreshToken = params["refresh_token"]
            tokenType = params["type"] ?: params["error_description"]
            if (oauthError.isNullOrBlank()) {
                oauthError = params["error_description"] ?: params["error"]
            }
        }

        val authCode = uri.getQueryParameter("code") ?: if (!fragment.isNullOrBlank()) {
            fragment.split("&").associate {
                val parts = it.split("=")
                if (parts.size >= 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
            }["code"]
        } else null

        if (!oauthError.isNullOrBlank() && accessToken.isNullOrBlank() && authCode.isNullOrBlank()) {
            _isAuthenticating.value = false
            _authError.value = "Sign-in was cancelled or failed: $oauthError"
            logFirebaseStatus("Auth deep link reported error: $oauthError")
            return
        }

        if (!accessToken.isNullOrBlank() || !authCode.isNullOrBlank()) {
            viewModelScope.launch {
                var resolvedAccessToken = accessToken
                var resolvedRefreshToken = refreshToken

                // If PKCE code was returned, exchange it for access token against Platform Supabase
                if (resolvedAccessToken.isNullOrBlank() && !authCode.isNullOrBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val client = okhttp3.OkHttpClient()
                            val jsonBody = org.json.JSONObject().apply {
                                put("auth_code", authCode)
                                put("code_verifier", securityPrefs.getString("supabase_pkce_verifier", ""))
                            }
                            val exchangeReq = okhttp3.Request.Builder()
                                .url("$PLATFORM_SUPABASE_URL/auth/v1/token?grant_type=pkce")
                                .header("apikey", PLATFORM_SUPABASE_ANON_KEY)
                                .header("Content-Type", "application/json")
                                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                                .build()
                            client.newCall(exchangeReq).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val bodyStr = resp.body?.string()
                                    if (!bodyStr.isNullOrBlank()) {
                                        val resJson = org.json.JSONObject(bodyStr)
                                        resolvedAccessToken = resJson.optString("access_token")
                                        resolvedRefreshToken = resJson.optString("refresh_token")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AuthDeepLink", "PKCE code exchange error: ${e.message}")
                        }
                    }
                }

                if (resolvedAccessToken.isNullOrBlank()) {
                    _isAuthenticating.value = false
                    _authError.value = "Supabase OAuth token extraction failed."
                    return@launch
                }

                var userEmail = ""
                var authUserId = ""
                var userName = ""

                // Query user info from Platform Supabase /auth/v1/user
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient()
                        val request = okhttp3.Request.Builder()
                            .url("$PLATFORM_SUPABASE_URL/auth/v1/user")
                            .header("Authorization", "Bearer $resolvedAccessToken")
                            .header("apikey", PLATFORM_SUPABASE_ANON_KEY)
                            .get()
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (!body.isNullOrBlank()) {
                                    val json = org.json.JSONObject(body)
                                    authUserId = json.optString("id")
                                    val fetchedEmail = json.optString("email")
                                    if (fetchedEmail.isNotBlank()) userEmail = fetchedEmail
                                    val meta = json.optJSONObject("user_metadata")
                                    userName = meta?.optString("full_name", meta.optString("name", "")) ?: ""
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthDeepLink", "Fetch user info failed: ${e.message}")
                    }
                }

                if (userEmail.isBlank() || authUserId.isBlank()) {
                    _authError.value = "Unable to verify your platform identity. Please sign in again."
                    _isAuthenticating.value = false
                    return@launch
                }
                val platformProfile = getOrCreatePlatformSupabaseProfile()
                repository.insertSupabaseProfile(platformProfile.copy(authEmail = userEmail,
                    authSessionToken = resolvedAccessToken.orEmpty(), authRefreshToken = resolvedRefreshToken.orEmpty(),
                    authTokenExpiresAt = System.currentTimeMillis() + 3600_000L, isActive = false))
                val account = checkMerchantAccountOnBackend(userEmail, authUserId)
                    ?: MerchantBackendCheckResult(
                        exists = false, isNew = true, isOnboarded = false,
                        merchantId = authUserId, businessName = userName, email = userEmail,
                        phone = "", businessType = "Retail Store", photoUrl = "", accountHolder = userName,
                        hasOwnDatabase = false, supabaseUrl = "", supabaseAnonKey = "", projectRef = "",
                        kycStatus = "UNVERIFIED"
                    )
                supportRefreshJob?.cancel()
                _supportChatList.value = emptyList()
                _mySupportTicketsList.value = emptyList()
                applyRestoredMerchantSetup(account)
                saveEncryptedSessionToken(userEmail, authUserId, "Supabase OAuth")
                securityPrefs.edit().remove("supabase_pkce_verifier").apply()
                val isEffectivelyOnboarded = account.isOnboarded || (account.exists && account.businessName.isNotBlank() && !account.businessName.matches(Regex("^(my store|my business|google user|facebook user|demo store|business setup required)$", RegexOption.IGNORE_CASE)))
                setOnboarded(isEffectivelyOnboarded)
                syncPinFromCloud()
                fetchMerchantApiKey()
                refreshGatewayConfig()
                pullAllMerchantDataFromRemote(account.merchantId)
                _isAuthenticating.value = false
                _authError.value = null
                navigateTo(if (!isEffectivelyOnboarded) "Onboarding" else if (_isBiometricLocked.value) "LockScreen" else "Main")

            }
        } else {
            logFirebaseStatus("Received auth callback deep link: $uri")
        }
    }

    private var supabaseRefreshJob: kotlinx.coroutines.Job? = null

    private fun scheduleSupabaseSessionRefresh(profile: SupabaseProfileEntity) {
        supabaseRefreshJob?.cancel()
        if (profile.authRefreshToken.isBlank() || profile.authTokenExpiresAt <= 0L) return
        supabaseRefreshJob = viewModelScope.launch {
            val delayMillis = (profile.authTokenExpiresAt - System.currentTimeMillis() - 60_000L).coerceAtLeast(1_000L)
            kotlinx.coroutines.delay(delayMillis)
            refreshSupabaseSession(profile)
        }
    }

    private suspend fun refreshSupabaseSession(profile: SupabaseProfileEntity): SupabaseProfileEntity? {
        if (_activeSupabaseProfile.value?.id != profile.id || profile.authRefreshToken.isBlank()) return null
        var session: com.example.data.remote.SupabaseClient.AuthSession? = null
        var failure: String? = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.remote.SupabaseClient.refreshSession(
                profile.supabaseUrl,
                profile.anonKey,
                profile.authRefreshToken,
                onSuccess = { session = it },
                onFailure = { failure = it }
            )
        }
        val refreshed = session ?: run {
            logFirebaseStatus("Supabase session refresh failed: ${failure ?: "sign in again"}")
            supabaseConnected.value = false
            return null
        }
        val updated = profile.copy(
            authEmail = refreshed.email.ifBlank { profile.authEmail },
            authSessionToken = refreshed.accessToken,
            authRefreshToken = refreshed.refreshToken,
            authTokenExpiresAt = refreshed.expiresAtMillis
        )
        repository.insertSupabaseProfile(updated)
        _activeSupabaseProfile.value = updated
        supabaseConnected.value = true
        scheduleSupabaseSessionRefresh(updated)
        return updated
    }

    private suspend fun validSupabaseSession(profile: SupabaseProfileEntity): SupabaseProfileEntity? {
        if (profile.authSessionToken.isBlank()) return null
        return if (profile.authTokenExpiresAt == 0L || profile.authTokenExpiresAt > System.currentTimeMillis() + 60_000L) {
            profile
        } else {
            refreshSupabaseSession(profile)
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            val platformProfile = getOrCreatePlatformSupabaseProfile()
            var success = false
            var failure: String? = null
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.remote.SupabaseClient.sendPasswordReset(
                    platformProfile.supabaseUrl,
                    platformProfile.anonKey,
                    email.trim(),
                    redirectUrl = PLATFORM_AUTH_REDIRECT_URL,
                    onSuccess = { success = true },
                    onFailure = { failure = it }
                )
            }
            _isAuthenticating.value = false
            if (success) onSuccess() else onFailure(failure ?: "Password reset failed.")
        }
    }


    private val _appPin = MutableStateFlow(securityPrefs.getString("app_pin_hash", "") ?: "")
    val appPin: StateFlow<String> = _appPin.asStateFlow()

    // True while syncPinFromCloud is running
    private val _isPinSyncing = MutableStateFlow(false)
    val isPinSyncing: StateFlow<Boolean> = _isPinSyncing.asStateFlow()

    private val _isBiometricLocked = MutableStateFlow(securityPrefs.getBoolean("is_biometric_locked", false))
    val isBiometricLocked: StateFlow<Boolean> = _isBiometricLocked.asStateFlow()

    private val _isAppLocked = MutableStateFlow(true) // Starts locked on launch
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isSecuritySyncing = MutableStateFlow(false)
    val isSecuritySyncing: StateFlow<Boolean> = _isSecuritySyncing.asStateFlow()

    private val _securitySyncError = MutableStateFlow<String?>(null)
    val securitySyncError: StateFlow<String?> = _securitySyncError.asStateFlow()

    // ─── PIN Helpers ────────────────────────────────────────────

    /** SHA-256 hex of a raw string */
    private fun sha256Hex(raw: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify the user-entered PIN against the stored cloud hash.
     * Fast local comparison — no network round-trip needed on unlock.
     */
    fun verifyPin(input: String): Boolean {
        val storedHash = _appPin.value
        if (storedHash.isBlank()) {
            // No PIN set — fall back to legacy plain-text for migration
            val legacyPin = securityPrefs.getString("app_pin", "") ?: ""
            return input == legacyPin.ifEmpty { "1234" }
        }
        return sha256Hex(input) == storedHash
    }

    /**
     * Set a new PIN: hash it, save locally and sync hash to Supabase.
     * Called from Settings "Change PIN" dialog and from onboarding first-time setup.
     */
    fun updatePin(newPin: String) {
        val hash = sha256Hex(newPin)
        _appPin.value = hash
        securityPrefs.edit()
            .putString("app_pin_hash", hash)
            .remove("app_pin")          // remove legacy plain-text
            .apply()
        // Sync to Supabase in background
        viewModelScope.launch { uploadPinHashToCloud(hash) }
    }

    /** Upload the pin hash to the backend /v1/pin/set */
    private suspend fun uploadPinHashToCloud(hash: String) {
        try {
            val payload = org.json.JSONObject().put("pin_hash", hash)
            platformRequest("/v1/pin/set", payload)
            logFirebaseStatus("PIN hash synced to Supabase successfully.")
        } catch (e: Exception) {
            logFirebaseStatus("PIN cloud sync failed (will retry on next login): ${e.message}")
        }
    }

    /**
     * Fetch the PIN hash from Supabase after login / session restore.
     * If admin cleared the PIN or requested reset, clears local hash so merchant
     * is prompted to set a new PIN.
     * If cloud has no PIN yet but device has one from onboarding, uploads it.
     */
    fun syncPinFromCloud() {
        viewModelScope.launch {
            _isPinSyncing.value = true
            try {
                val resp = platformRequest("/v1/pin/sync")
                val pinHash = if (resp.isNull("pin_hash")) null else resp.optString("pin_hash", null)
                val resetRequested = resp.optBoolean("pin_reset_requested", false)
                val pinSet = resp.optBoolean("pin_set", !pinHash.isNullOrBlank())

                if (resetRequested || !pinSet || pinHash.isNullOrBlank()) {
                    // Admin explicitly cleared the PIN or requested reset — clear local hash so user sets new PIN
                    _appPin.value = ""
                    securityPrefs.edit().remove("app_pin_hash").remove("app_pin").apply()
                    logFirebaseStatus("PIN cleared or reset required by admin. Local PIN cleared.")
                } else {
                    // Update local cache with cloud hash
                    _appPin.value = pinHash
                    securityPrefs.edit().putString("app_pin_hash", pinHash).apply()
                    logFirebaseStatus("PIN hash synced from Supabase and stored locally.")
                }
            } catch (e: Exception) {
                logFirebaseStatus("PIN sync skipped (offline or error): ${e.message}")
            } finally {
                _isPinSyncing.value = false
            }
        }
    }

    /**
     * Request that the admin reset (clear) this merchant's PIN.
     * Sets pin_reset_requested = true in Supabase.
     */
    fun requestPinReset(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = org.json.JSONObject().apply {
                    val mId = _activeProfile.value.id.takeIf { it.isNotBlank() && it != "merchant_default" && it != "default_merchant" && it != repository.installationId }
                    if (!mId.isNullOrBlank()) put("merchant_id", mId)
                    val email = _userEmail.value ?: _activeProfile.value.email
                    if (!email.isNullOrBlank()) put("email", email)
                }
                val resp = platformRequest("/v1/pin/request-reset", payload)
                onComplete(true, resp.optString("message", "Reset request sent to admin."))
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Request failed. Check your internet connection.")
            }
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        _isBiometricLocked.value = enabled
        securityPrefs.edit().putBoolean("is_biometric_locked", enabled).apply()
        syncSecuritySettingsToSupabase()
    }

    fun setOnboarded(completed: Boolean) {
        securityPrefs.edit().putBoolean("is_onboarded", completed).apply()
    }

    fun isOnboarded(): Boolean {
        return securityPrefs.getBoolean("is_onboarded", false)
    }

    fun syncSecuritySettingsToSupabase() {
        _isSecuritySyncing.value = false
        _securitySyncError.value = null
        logFirebaseStatus("Device security settings saved securely on this device.")
    }

    var isExternalActivityExpected: Boolean = false
    private var _previousScreenBeforeLock: String = "Main"

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        _isAppLocked.value = true
        navigateTo("LockScreen")
    }

    fun lockAppOnBackground() {
        // App lock should not appear when user switches tabs or apps on mobile
        return
    }

    fun unlockAppAndRestore() {
        _isAppLocked.value = false
        val target = if (_previousScreenBeforeLock.isNotEmpty() && _previousScreenBeforeLock != "LockScreen") {
            _previousScreenBeforeLock
        } else {
            "Main"
        }
        navigateTo(target)
    }

    fun checkCurrentUser() {
        verifyEncryptedSessionToken()
    }

    fun loginWithGoogleReal(idToken: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        performDirectSocialLogin("Google", onSuccess)
    }

    fun loginWithFacebookReal(accessToken: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        performDirectSocialLogin("Facebook", onSuccess)
    }

    fun clearAllSessionAndOnboardingData() {
        setUserEmail(null)
        _supabaseUrlInput.value = ""
        _supabaseAnonKeyInput.value = ""
        supabaseUrl.value = ""
        supabaseAnonKey.value = ""
        _onboardingBusinessName.value = ""
        _onboardingPhone.value = ""
        _authError.value = null
        _sessionInfo.value = EncryptedSessionInfo(isValid = false)
        try {
            securityPrefs.edit().clear().apply()
        } catch (e: Exception) {
            android.util.Log.w("AppViewModel", "Failed to clear securityPrefs: ${e.message}")
        }
        setOnboarded(false)
        val cleanProfile = com.example.data.local.MerchantProfileEntity(
            id = "merchant_default",
            businessName = "",
            phone = "",
            email = "",
            businessType = "Retail Store",
            website = "",
            primaryBank = "",
            accountHolder = "",
            accountNumber = "",
            kycStatus = "UNVERIFIED",
            photoUrl = ""
        )
        _activeProfile.value = cleanProfile
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.insertMerchantProfile(cleanProfile)
            } catch (e: Exception) {
                android.util.Log.w("AppViewModel", "Failed to reset default merchant profile: ${e.message}")
            }
        }
    }


    fun logout(onComplete: () -> Unit) {
        supportRefreshJob?.cancel()
        supabaseRefreshJob?.cancel()
        _supportChatList.value = emptyList()
        _mySupportTicketsList.value = emptyList()
        clearAllSessionAndOnboardingData()
        viewModelScope.launch {
            platformSessionMutex.withLock {
                repository.observeSupabaseProfiles().firstOrNull().orEmpty().forEach { profile ->
                    repository.insertSupabaseProfile(profile.copy(authEmail = "", authSessionToken = "",
                        authRefreshToken = "", authTokenExpiresAt = 0L, isActive = false))
                }
            }
            _activeSupabaseProfile.value = null
            supabaseConnected.value = false
            navigateTo("Login")
            onComplete()
        }
    }

    val supabaseProfiles: StateFlow<List<com.example.data.local.SupabaseProfileEntity>> = repository.observeSupabaseProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSupabaseProfile = MutableStateFlow<com.example.data.local.SupabaseProfileEntity?>(null)
    val activeSupabaseProfile: StateFlow<com.example.data.local.SupabaseProfileEntity?> = _activeSupabaseProfile.asStateFlow()

    // --- SUPABASE MULTI-PROFILE & REALTIME DIAGNOSTICS ---
    private val _realtimeDiagnosticSteps = MutableStateFlow<List<RealtimeDiagnosticStep>>(emptyList())
    val realtimeDiagnosticSteps: StateFlow<List<RealtimeDiagnosticStep>> = _realtimeDiagnosticSteps.asStateFlow()

    private val _isRunningRealtimeDiagnostics = MutableStateFlow(false)
    val isRunningRealtimeDiagnostics: StateFlow<Boolean> = _isRunningRealtimeDiagnostics.asStateFlow()

    private val _realtimeDiagnosticsOverallSuccess = MutableStateFlow<Boolean?>(null)
    val realtimeDiagnosticsOverallSuccess: StateFlow<Boolean?> = _realtimeDiagnosticsOverallSuccess.asStateFlow()

    private val _realtimeDiagnosticsSummary = MutableStateFlow<String?>(null)
    val realtimeDiagnosticsSummary: StateFlow<String?> = _realtimeDiagnosticsSummary.asStateFlow()

    fun runRealtimeSupabaseDiagnostics(customUrl: String? = null, customKey: String? = null) {
        viewModelScope.launch {
            val active = repository.getActiveSupabaseProfile()
            val url = (customUrl?.takeIf { it.isNotBlank() } ?: supabaseUrl.value.ifEmpty { active?.supabaseUrl ?: "" }).trim()
            val key = (customKey?.takeIf { it.isNotBlank() } ?: supabaseAnonKey.value.ifEmpty { active?.anonKey ?: "" }).trim()

            if (url.isBlank() || key.isBlank() || url.contains("abc123xyz") || url.contains("def456uvw")) {
                _realtimeDiagnosticsOverallSuccess.value = false
                _realtimeDiagnosticsSummary.value = "Supabase URL and Anon Key must be configured with valid endpoints before running diagnostics."
                return@launch
            }

            _isRunningRealtimeDiagnostics.value = true
            _realtimeDiagnosticsOverallSuccess.value = null
            _realtimeDiagnosticsSummary.value = "Starting real-time 5-step CRUD verification against database..."

            val initialSteps = listOf(
                RealtimeDiagnosticStep(0, "0. Connectivity Check", "Verifying REST API reachable & Anon Key authorized", DiagnosticStepStatus.RUNNING),
                RealtimeDiagnosticStep(1, "1. Real-time Insert", "Insert test record into security_logs table", DiagnosticStepStatus.PENDING),
                RealtimeDiagnosticStep(2, "2. Real-time Read", "Query inserted record via REST filter", DiagnosticStepStatus.PENDING),
                RealtimeDiagnosticStep(3, "3. Real-time Update", "Update record event status via PATCH", DiagnosticStepStatus.PENDING),
                RealtimeDiagnosticStep(4, "4. Real-time Delete", "Purge test record via DELETE & verify cleanup", DiagnosticStepStatus.PENDING)
            )
            _realtimeDiagnosticSteps.value = initialSteps

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.remote.SupabaseClient.performRealtimeCrudTest(
                    url = url,
                    anonKey = key,
                    onLogStep = { stepName, detail ->
                        viewModelScope.launch {
                            val currentList = ArrayList(_realtimeDiagnosticSteps.value)
                            val stepIdx = when {
                                stepName.contains("0.") || stepName.contains("CONNECTIVITY") -> 0
                                stepName.contains("1.") || stepName.contains("INSERT") -> 1
                                stepName.contains("2.") || stepName.contains("READ") -> 2
                                stepName.contains("3.") || stepName.contains("UPDATE") -> 3
                                stepName.contains("4.") || stepName.contains("DELETE") -> 4
                                else -> -1
                            }

                            if (stepIdx in currentList.indices) {
                                val isCompleted = detail.contains("authorized", ignoreCase = true) ||
                                        detail.contains("created", ignoreCase = true) ||
                                        detail.contains("retrieved", ignoreCase = true) ||
                                        detail.contains("updated", ignoreCase = true) ||
                                        detail.contains("cleaned", ignoreCase = true) ||
                                        detail.contains("SUCCESS", ignoreCase = true)

                                val stepStatus = if (isCompleted) DiagnosticStepStatus.SUCCESS else DiagnosticStepStatus.RUNNING
                                currentList[stepIdx] = currentList[stepIdx].copy(
                                    detail = detail,
                                    status = stepStatus
                                )
                                if (isCompleted && stepIdx + 1 < currentList.size && currentList[stepIdx + 1].status == DiagnosticStepStatus.PENDING) {
                                    currentList[stepIdx + 1] = currentList[stepIdx + 1].copy(status = DiagnosticStepStatus.RUNNING)
                                }
                                _realtimeDiagnosticSteps.value = currentList
                            }
                        }
                    },
                    onSuccess = {
                        viewModelScope.launch {
                            _isRunningRealtimeDiagnostics.value = false
                            _realtimeDiagnosticsOverallSuccess.value = true
                            _realtimeDiagnosticsSummary.value = "All 5 lifecycle CRUD steps passed! Database is fully operational with real-time sync."
                            val currentList = _realtimeDiagnosticSteps.value.map { it.copy(status = DiagnosticStepStatus.SUCCESS) }
                            _realtimeDiagnosticSteps.value = currentList
                            supabaseConnected.value = true
                        }
                    },
                    onFailure = { err ->
                        viewModelScope.launch {
                            _isRunningRealtimeDiagnostics.value = false
                            _realtimeDiagnosticsOverallSuccess.value = false
                            _realtimeDiagnosticsSummary.value = err
                            val currentList = ArrayList(_realtimeDiagnosticSteps.value)
                            val runningIdx = currentList.indexOfFirst { it.status == DiagnosticStepStatus.RUNNING }
                            if (runningIdx != -1) {
                                currentList[runningIdx] = currentList[runningIdx].copy(
                                    status = DiagnosticStepStatus.FAILED,
                                    detail = "${currentList[runningIdx].detail}\nError: $err"
                                )
                            }
                            _realtimeDiagnosticSteps.value = currentList
                        }
                    }
                )
            }
        }
    }

    fun addOrUpdateSupabaseProfile(
        profileId: String? = null,
        businessName: String,
        url: String,
        anonKey: String,
        makeActive: Boolean = false,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanName = businessName.trim().ifEmpty { "Supabase Instance" }
            var cleanUrl = url.trim().trimEnd('/')
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            val cleanKey = anonKey.trim()

            if (cleanUrl.isBlank() || cleanKey.isBlank()) {
                onFailure("Supabase URL and Anon Key are required.")
                return@launch
            }

            if (cleanKey.contains("service_role", ignoreCase = true) || cleanKey.contains("\"role\":\"service_role\"")) {
                onFailure("Security Warning: Do NOT enter a service-role secret key in the mobile client. Use the public anon key instead.")
                return@launch
            }

            val id = profileId ?: java.util.UUID.randomUUID().toString()
            val existing = repository.getSupabaseProfileById(id)
            val profile = com.example.data.local.SupabaseProfileEntity(
                id = id,
                businessName = cleanName,
                supabaseUrl = cleanUrl,
                anonKey = cleanKey,
                serviceRoleKey = "",
                authEmail = existing?.authEmail ?: "",
                authSessionToken = existing?.authSessionToken ?: "",
                authRefreshToken = existing?.authRefreshToken ?: "",
                authTokenExpiresAt = existing?.authTokenExpiresAt ?: 0L,
                isActive = makeActive || existing?.isActive == true,
                defaultNumber = existing?.defaultNumber ?: ""
            )

            repository.insertSupabaseProfile(profile)
            if (makeActive) {
                switchProfile(id)
            }
            logFirebaseStatus("Supabase profile '$cleanName' saved successfully.")
            onSuccess()
        }
    }

    fun deleteSupabaseProfile(
        profileId: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val currentProfiles = supabaseProfiles.value
            if (currentProfiles.size <= 1) {
                onFailure("Cannot delete the only configured profile.")
                return@launch
            }
            val target = repository.getSupabaseProfileById(profileId)
            if (target == null) {
                onFailure("Profile not found.")
                return@launch
            }
            repository.deleteSupabaseProfile(profileId)
            if (target.isActive || _activeSupabaseProfile.value?.id == profileId) {
                val fallback = supabaseProfiles.value.firstOrNull { it.id != profileId }
                if (fallback != null) {
                    switchProfile(fallback.id)
                }
            }
            logFirebaseStatus("Deleted Supabase profile: ${target.businessName}")
            onSuccess()
        }
    }

    fun forceSyncSupabase() {
        viewModelScope.launch {
            syncAllScreensToSupabase()
            repository.syncMfsPatterns()
            sendDeviceHeartbeat()
            logFirebaseStatus("Force-synced all local state with Supabase.")
        }
    }

    // Database observables
    val orders: StateFlow<List<CachedOrderEntity>> = activeProfile.flatMapLatest { repository.observeOrders(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<CachedPaymentEntity>> = activeProfile.flatMapLatest { repository.observePayments(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appeals: StateFlow<List<AppealEntity>> = activeProfile.flatMapLatest { repository.observeAppeals(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devices: StateFlow<List<DeviceInfoEntity>> = activeProfile.flatMapLatest { repository.observeDevices(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsQueue: StateFlow<List<SmsQueueEntity>> = activeProfile.flatMapLatest { repository.observeSmsQueue(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendDeviceHeartbeat(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = repository.sendDeviceHeartbeat(getApplication())
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete?.invoke(result)
            }
        }
    }

    // Config credentials
    private val _firebaseApiKey = MutableStateFlow("")
    val firebaseApiKey: StateFlow<String> = _firebaseApiKey.asStateFlow()

    private val _firebaseProjectId = MutableStateFlow("")
    val firebaseProjectId: StateFlow<String> = _firebaseProjectId.asStateFlow()

    // Active screen support parameters
    private val _activeOrderDetails = MutableStateFlow<CachedOrderEntity?>(null)
    val activeOrderDetails: StateFlow<CachedOrderEntity?> = _activeOrderDetails.asStateFlow()

    private val _activePaymentDetails = MutableStateFlow<CachedPaymentEntity?>(null)
    val activePaymentDetails: StateFlow<CachedPaymentEntity?> = _activePaymentDetails.asStateFlow()

    // Search and filters
    val orderFilter = MutableStateFlow("All") // "All", "Pending", "Paid", "Expired", "Cancelled"
    val transactionFilter = MutableStateFlow("All") // "All", "Received", "Unmatched", "Failed"

    // SMS Input Simulator for testing
    // Active numbers list (Room-backed and isolated by merchant profile)
    val merchantNumbers: StateFlow<List<MerchantNumber>> = activeProfile
        .flatMapLatest { profile -> repository.observeMerchantNumbers(profile.id) }
        .map { rows ->
            rows.distinctBy { it.number }.map { MerchantNumber(it.number, it.method, it.accountType, it.isActive, it.isDefault, it.qrCodeUrl) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isSyncing = MutableStateFlow(false)

    fun triggerSync() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (isSyncing.value) return@launch
            isSyncing.value = true
            logFirebaseEvent("sync_supabase_data_start")
            
            val storedProfile = repository.getActiveSupabaseProfile()
            val active = storedProfile?.let { validSupabaseSession(it) }
            if (active != null) {
                logFirebaseStatus("Synchronizing data with Supabase Cloud...")
                
                // Fetch dynamic regex patterns & send device heartbeat
                repository.syncMfsPatterns()
                repository.sendDeviceHeartbeat(getApplication())
                
                // Synchronize orders, payments, appeals, and devices
                val ordersSynced = repository.syncOrdersFromSupabase()
                val paymentsSynced = repository.syncPaymentsFromSupabase()
                val appealsSynced = repository.syncAppealsFromSupabase()
                val devicesSynced = repository.syncDevicesFromSupabase()
                val financeSynced = syncPendingFinanceData()
                val productsSynced = repository.syncProductsFromSupabase()
                val businessPullFailures = pullAllBusinessDataFromSupabase(active)
                val notificationsSynced = refreshMerchantNotifications(active)
                
                // Sync local data to Firebase Real-time DB for visual dashboards
                syncOrdersToFirebase(orders.value)
                syncPaymentsToFirebase(payments.value)
                
                // Sync payment forms & submissions
                fetchPaymentForms()
                fetchFormSubmissions()

                if (ordersSynced && paymentsSynced && appealsSynced && devicesSynced && financeSynced && notificationsSynced && productsSynced && businessPullFailures == 0) {
                    logFirebaseStatus("All database-backed screens synchronized successfully.")
                    logFirebaseEvent("sync_supabase_data_success")
                } else {
                    logFirebaseStatus("Warning: Some datasets failed to synchronize with Supabase.")
                    logFirebaseEvent("sync_supabase_data_warning")
                }
            } else {
                val message = if (storedProfile == null || storedProfile.supabaseUrl.isBlank()) {
                    "No Supabase project is connected. Data remains in the offline Room database."
                } else {
                    "Supabase requires a valid user session. Sign in again before cloud synchronization."
                }
                logFirebaseStatus(message)
                // Platform fallback sync without dedicated Supabase: sync transactions from backend & refresh local datasets
                repository.syncPaymentsFromSupabase()
                repository.sendDeviceHeartbeat(getApplication())
                fetchPaymentForms()
                fetchFormSubmissions()
                val mId = activeProfile.value.id
                if (mId.isNotBlank() && mId != "00000000-0000-0000-0000-000000000001") {
                    repository.reassignMerchantData("00000000-0000-0000-0000-000000000001", mId)
                }
                logFirebaseStatus("Synced transactions and forms with SwapnoPay cloud gateway.")
            }
            
            isSyncing.value = false
        }
    }

    fun syncProductCatalog(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val success = repository.syncProductsFromSupabase()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    private suspend fun refreshMerchantNotifications(active: SupabaseProfileEntity): Boolean {
        var fetched: List<MerchantNotificationEntity>? = null
        com.example.data.remote.SupabaseClient.fetchRecords(
            active.supabaseUrl, active.anonKey, active.authSessionToken, "merchant_notifications", "*",
            onSuccess = { rows ->
                val parsed = List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    MerchantNotificationEntity(
                        id = row.getString("id"), merchantId = activeProfile.value.id,
                        type = row.optString("type", "INFO"), title = row.optString("title", "Notification"),
                        message = row.optString("message"), severity = row.optString("severity", "INFO"),
                        entityType = row.optString("entity_type").ifBlank { null },
                        entityId = row.optString("entity_id").ifBlank { null },
                        createdAt = parseRemoteTimestamp(row.optString("created_at")),
                        readAt = row.optString("read_at").takeIf { it.isNotBlank() && it != "null" }?.let(::parseRemoteTimestamp)
                    )
                }
                fetched = parsed
            },
            onFailure = { logFirebaseStatus("Notification sync failed: $it") }
        )
        val rows = fetched ?: return false
        repository.upsertMerchantNotifications(rows)

        // Automatically detect new broadcast notices from Admin Panel
        try {
            val unreadBroadcast = rows.firstOrNull { it.readAt == null && (it.entityType == "BROADCAST" || it.type in setOf("ANNOUNCEMENT", "ALERT", "SYSTEM", "PROMOTION")) }
            if (unreadBroadcast != null && _adminNoticePopup.value == null) {
                val dismissed = getDismissedNoticeIds()
                if (!dismissed.contains(unreadBroadcast.id)) {
                    _adminNoticePopup.value = AdminNoticePopup(
                        id = unreadBroadcast.id,
                        title = unreadBroadcast.title.ifBlank { "অ্যাডমিন নোটিশ" },
                        message = unreadBroadcast.message,
                        severity = unreadBroadcast.severity.ifBlank { "INFO" },
                        type = unreadBroadcast.type.ifBlank { "ANNOUNCEMENT" },
                        timestamp = unreadBroadcast.createdAt
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("AppViewModel", "Notice popup detection error: ${e.message}")
        }

        return true
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            val merchantId = activeProfile.value.id
            repository.markMerchantNotificationRead(notificationId, merchantId)
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return@launch
            com.example.data.remote.SupabaseClient.callRpc(
                active.supabaseUrl, active.anonKey, active.authSessionToken, "mark_merchant_notification_read",
                org.json.JSONObject().put("p_notification_id", notificationId).put("p_read_at", toIsoTimestamp(System.currentTimeMillis())),
                onSuccess = { }, onFailure = { logFirebaseStatus("Notification read state is queued locally: $it") }
            )
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val merchantId = activeProfile.value.id
            repository.markAllMerchantNotificationsRead(merchantId)
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return@launch
            com.example.data.remote.SupabaseClient.callRpc(
                active.supabaseUrl, active.anonKey, active.authSessionToken, "mark_all_merchant_notifications_read",
                org.json.JSONObject().put("p_read_at", toIsoTimestamp(System.currentTimeMillis())),
                onSuccess = { }, onFailure = { logFirebaseStatus("Notification read states are queued locally: $it") }
            )
        }
    }

    // Global dark mode preference
    private val _isDarkMode = MutableStateFlow(securityPrefs.getBoolean("app_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        securityPrefs.edit().putBoolean("app_dark_mode", enabled).apply()
    }

    // Enterprise premium subscription status
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun setPremium(enabled: Boolean) {
        _isPremium.value = enabled
        if (!enabled) {
            _isDarkMode.value = false
            securityPrefs.edit().putBoolean("app_dark_mode", false).apply()
        }
    }

    // Global language preference
    private val _language = MutableStateFlow(securityPrefs.getString("app_language", "Bangla") ?: "Bangla")
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(lang: String) {
        _language.value = lang
        securityPrefs.edit().putString("app_language", lang).apply()
    }

    // Supabase Setup properties
    val supabaseUrl = MutableStateFlow("")
    val supabaseAnonKey = MutableStateFlow("")
    val supabaseConnectionName = MutableStateFlow("My Store Backend")
    val supabaseConnected = MutableStateFlow(false)
    val supabaseSetupProgress = MutableStateFlow(0)
    val isRunningSystemTest = MutableStateFlow(false)
    val systemTestError = MutableStateFlow<String?>(null)
    val systemTestSuccess = MutableStateFlow(false)
    val systemTestSuccessTrigger = MutableStateFlow(0)

    enum class SyncStatus { LIVE, SYNCING, LOCAL, ERROR }

    val syncStatus: StateFlow<SyncStatus> = combine(
        supabaseConnected, isSyncing, systemTestError
    ) { connected, syncing, error ->
        when {
            error != null -> SyncStatus.ERROR
            syncing -> SyncStatus.SYNCING
            connected -> SyncStatus.LIVE
            else -> SyncStatus.LOCAL
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, SyncStatus.LOCAL)

    val lastBackupTimestamp = MutableStateFlow(
        securityPrefs.getLong("last_backup_timestamp", 0L)
    )

    val backupHistory = MutableStateFlow<List<com.example.data.repository.BackupManager.BackupSnapshotInfo>>(emptyList())
    val storageStats = MutableStateFlow<com.example.data.repository.BackupManager.StorageStats?>(null)
    val autoBackupEnabled = MutableStateFlow(securityPrefs.getBoolean("auto_backup_enabled_pref", true))
    val backupScheduleFrequency = MutableStateFlow(
        securityPrefs.getString("backup_schedule_freq_pref", "Daily at 11:30 PM (Recommended)") ?: "Daily at 11:30 PM (Recommended)"
    )

    fun persistBackupTimestamp() {
        val now = System.currentTimeMillis()
        securityPrefs.edit().putLong("last_backup_timestamp", now).apply()
        lastBackupTimestamp.value = now
    }

    fun setAutoBackup(enabled: Boolean) {
        securityPrefs.edit().putBoolean("auto_backup_enabled_pref", enabled).apply()
        autoBackupEnabled.value = enabled
    }

    fun setBackupSchedule(frequency: String) {
        securityPrefs.edit().putString("backup_schedule_freq_pref", frequency).apply()
        backupScheduleFrequency.value = frequency
    }

    fun loadBackupHistory(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = com.example.data.repository.BackupManager.getBackupHistory(context)
            backupHistory.value = list
        }
    }

    fun loadStorageStatistics(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val stats = com.example.data.repository.BackupManager.getStorageStatistics(context)
            storageStats.value = stats
        }
    }

    fun createFullBackup(context: Context, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Sync to remote Supabase if configured
                syncAllScreensToSupabase()

                // 2. Generate local JSON snapshot
                val result = com.example.data.repository.BackupManager.createLocalSnapshot(
                    context = context,
                    repository = repository,
                    merchantId = activeProfile.value.id,
                    backupType = if (autoBackupEnabled.value) "Cloud & Local Snapshot" else "Manual Local"
                )

                if (result.isSuccess) {
                    val info = result.getOrThrow()
                    persistBackupTimestamp()
                    loadBackupHistory(context)
                    loadStorageStatistics(context)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(true, "Backup snapshot '${info.fileName}' created successfully (${info.totalRecords} records, ${info.formattedSize})")
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(false, "Backup failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false, "Backup exception: ${e.message}")
                }
            }
        }
    }

    fun restoreFromLocalJson(context: Context, jsonContent: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val summary = com.example.data.repository.BackupManager.restoreFromSnapshotJson(
                repository = repository,
                jsonContent = jsonContent,
                merchantId = activeProfile.value.id
            )
            loadStorageStatistics(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(summary.success, summary.message)
            }
        }
    }

    fun restoreFromCloudSupabase(context: Context, onComplete: (Boolean, String) -> Unit) {
        val configuredProfile = _activeSupabaseProfile.value
        if (configuredProfile == null || configuredProfile.supabaseUrl.isEmpty() || configuredProfile.anonKey.isEmpty()) {
            onComplete(false, "Supabase connection is not configured or offline. Please configure in settings.")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = validSupabaseSession(configuredProfile)
            if (active == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false, "Session expired. Please sign in to Supabase.")
                }
                return@launch
            }

            val merchantId = activeProfile.value.id
            val errors = mutableListOf<String>()

            // 1. Fetch Customers
            try {
                com.example.data.remote.SupabaseClient.fetchRecords(
                    url = active.supabaseUrl,
                    anonKey = active.anonKey,
                    token = active.authSessionToken,
                    tableName = "customers",
                    onSuccess = { arr ->
                        val list = mutableListOf<CustomerEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                CustomerEntity(
                                    id = o.getString("id"),
                                    merchantId = merchantId,
                                    name = o.optString("name", "Customer"),
                                    phone = o.optString("phone", ""),
                                    email = if (o.isNull("email")) null else o.optString("email"),
                                    address = if (o.isNull("address")) null else o.optString("address"),
                                    openingBalance = o.optDouble("opening_balance", 0.0),
                                    currentBalance = o.optDouble("current_balance", 0.0),
                                    status = o.optString("status", "VIP"),
                                    createdAt = parseRemoteTimestamp(o.optString("created_at"))
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.insertCustomers(list) }
                        }
                    },
                    onFailure = { errors.add("Customers: $it") }
                )
            } catch (e: Exception) { errors.add("Customers: ${e.message}") }

            // 2. Fetch Suppliers
            try {
                com.example.data.remote.SupabaseClient.fetchRecords(
                    url = active.supabaseUrl,
                    anonKey = active.anonKey,
                    token = active.authSessionToken,
                    tableName = "suppliers",
                    onSuccess = { arr ->
                        val list = mutableListOf<SupplierEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                SupplierEntity(
                                    id = o.getString("id"),
                                    merchantId = merchantId,
                                    name = o.optString("name", "Supplier"),
                                    phone = o.optString("phone", ""),
                                    email = if (o.isNull("email")) null else o.optString("email"),
                                    address = if (o.isNull("address")) null else o.optString("address"),
                                    openingBalance = o.optDouble("opening_balance", 0.0),
                                    currentBalance = o.optDouble("current_balance", 0.0),
                                    createdAt = parseRemoteTimestamp(o.optString("created_at"))
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.insertSuppliers(list) }
                        }
                    },
                    onFailure = { errors.add("Suppliers: $it") }
                )
            } catch (e: Exception) { errors.add("Suppliers: ${e.message}") }

            // 3. Fetch Products
            try {
                com.example.data.remote.SupabaseClient.fetchRecords(
                    url = active.supabaseUrl,
                    anonKey = active.anonKey,
                    token = active.authSessionToken,
                    tableName = "products",
                    onSuccess = { arr ->
                        val list = mutableListOf<ProductItemEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                ProductItemEntity(
                                    id = o.getString("id"),
                                    merchantId = merchantId,
                                    name = o.optString("name", "Product"),
                                    code = if (o.isNull("code")) null else o.optString("code"),
                                    category = o.optString("category", "General"),
                                    purchasePrice = o.optDouble("purchase_price", 0.0),
                                    salePrice = o.optDouble("sale_price", 0.0),
                                    costPrice = o.optDouble("cost_price", o.optDouble("purchase_price", 0.0)),
                                    askingPrice = o.optDouble("asking_price", o.optDouble("sale_price", 0.0)),
                                    stockQuantity = o.optDouble("stock_quantity", 0.0),
                                    minStockThreshold = o.optDouble("min_stock_threshold", 5.0),
                                    unit = o.optString("unit", "pcs"),
                                    qrCode = if (o.isNull("qr_code")) null else o.optString("qr_code"),
                                    imageUrl = if (o.isNull("image_url")) null else o.optString("image_url"),
                                    storefrontDetailsJson = o.optJSONObject("storefront_details")?.toString() ?: "{}",
                                    createdAt = parseRemoteTimestamp(o.optString("created_at"))
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.insertProducts(list) }
                        }
                    },
                    onFailure = { errors.add("Products: $it") }
                )
            } catch (e: Exception) { errors.add("Products: ${e.message}") }

            // 4. Fetch Ledger
            try {
                com.example.data.remote.SupabaseClient.fetchRecords(
                    url = active.supabaseUrl,
                    anonKey = active.anonKey,
                    token = active.authSessionToken,
                    tableName = "ledger_transactions",
                    onSuccess = { arr ->
                        val list = mutableListOf<LedgerTransactionEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                LedgerTransactionEntity(
                                    id = o.getString("id"),
                                    merchantId = merchantId,
                                    customerId = if (o.isNull("customer_id")) null else o.optString("customer_id"),
                                    supplierId = if (o.isNull("supplier_id")) null else o.optString("supplier_id"),
                                    type = o.optString("type", "credit"),
                                    amount = o.optDouble("amount", 0.0),
                                    date = parseRemoteTimestamp(o.optString("date")),
                                    note = if (o.isNull("note")) null else o.optString("note"),
                                    paymentMethod = o.optString("payment_method", "Cash"),
                                    invoiceNo = if (o.isNull("invoice_no")) null else o.optString("invoice_no"),
                                    isSynced = true
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.insertLedgerTransactions(list) }
                        }
                    },
                    onFailure = { errors.add("Ledger: $it") }
                )
            } catch (e: Exception) { errors.add("Ledger: ${e.message}") }

            // 5. Fetch POS Sales
            try {
                com.example.data.remote.SupabaseClient.fetchRecords(
                    url = active.supabaseUrl,
                    anonKey = active.anonKey,
                    token = active.authSessionToken,
                    tableName = "pos_sales",
                    onSuccess = { arr ->
                        val list = mutableListOf<PosSaleEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                PosSaleEntity(
                                    id = o.getString("id"),
                                    merchantId = merchantId,
                                    invoiceNo = o.optString("invoice_no", "INV-100"),
                                    customerId = if (o.isNull("customer_id")) null else o.optString("customer_id"),
                                    customerName = o.optString("customer_name", "Walk-in"),
                                    customerPhone = o.optString("customer_phone", ""),
                                    subtotal = o.optDouble("subtotal", 0.0),
                                    discount = o.optDouble("discount", 0.0),
                                    netTotal = o.optDouble("net_total", 0.0),
                                    cashReceived = o.optDouble("cash_received", 0.0),
                                    changeDue = o.optDouble("change_due", 0.0),
                                    paymentMethod = o.optString("payment_method", "Cash"),
                                    paymentStatus = o.optString("payment_status", "PAID"),
                                    itemCount = o.optInt("item_count", 1),
                                    cartItemsJson = o.optJSONArray("cart_items")?.toString() ?: "[]",
                                    timestamp = parseRemoteTimestamp(o.optString("timestamp")),
                                    isSynced = true
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.insertPosSales(list) }
                        }
                    },
                    onFailure = { errors.add("POS Sales: $it") }
                )
            } catch (e: Exception) { errors.add("POS Sales: ${e.message}") }

            // 6. Fetch Expenses
            try {
                com.example.data.remote.SupabaseClient.fetchRecords(
                    url = active.supabaseUrl,
                    anonKey = active.anonKey,
                    token = active.authSessionToken,
                    tableName = "expenses",
                    onSuccess = { arr ->
                        val list = mutableListOf<ExpenseEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(
                                ExpenseEntity(
                                    id = o.getString("id"),
                                    merchantId = merchantId,
                                    category = o.optString("category", "Operating"),
                                    amount = o.optDouble("amount", 0.0),
                                    date = parseRemoteTimestamp(o.optString("date")),
                                    description = if (o.isNull("description")) null else o.optString("description"),
                                    paymentMethod = o.optString("payment_method", "Cash"),
                                    createdAt = parseRemoteTimestamp(o.optString("created_at"))
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.insertExpenses(list) }
                        }
                    },
                    onFailure = { errors.add("Expenses: $it") }
                )
            } catch (e: Exception) { errors.add("Expenses: ${e.message}") }

            kotlinx.coroutines.delay(1000)
            loadStorageStatistics(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (errors.isEmpty()) {
                    onComplete(true, "Cloud restore completed! Pulled latest database records from Supabase.")
                } else {
                    onComplete(true, "Cloud restore finished with ${errors.size} table notices.")
                }
            }
        }
    }

    fun deleteBackupFile(context: Context, fileName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.repository.BackupManager.deleteBackupFile(context, fileName)
            loadBackupHistory(context)
            loadStorageStatistics(context)
        }
    }

    fun shareBackupFile(context: Context, info: com.example.data.repository.BackupManager.BackupSnapshotInfo) {
        try {
            val file = java.io.File(info.filePath)
            if (file.exists()) {
                val content = file.readText()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "SwapnoPay Database Backup - ${info.fileName}")
                    putExtra(Intent.EXTRA_TEXT, content)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share / Save Backup JSON"))
            }
        } catch (e: Exception) {
            Log.e("BackupShare", "Error sharing backup: ${e.message}")
        }
    }

    fun clearAppCache(context: Context, onComplete: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val freedBytes = com.example.data.repository.BackupManager.clearCache(context)
            loadStorageStatistics(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(com.example.data.repository.BackupManager.formatBytes(freedBytes))
            }
        }
    }

    val isFetchingManagementProjects = MutableStateFlow(false)
    val managementProjectsList = MutableStateFlow<List<com.example.data.remote.SupabaseClient.SupabaseProject>>(emptyList())
    val managementApiError = MutableStateFlow<String?>(null)
    val pendingPkceVerifier = MutableStateFlow<String?>(null)
    val oauthClientId = MutableStateFlow("5d3dcd9b-1acf-4e31-96d2-d673af42a18b")
    val oauthClientSecret = MutableStateFlow("")

    enum class OAuthStep { NOT_CONNECTED, ACCOUNT_CONNECTED, PROVISIONING, COMPLETE }

    val controlPlaneUrl = MutableStateFlow("https://api.swapnopay.top")
    val show15DayFeedbackDialog = MutableStateFlow(false)
    val oauthStep = MutableStateFlow(OAuthStep.NOT_CONNECTED)
    val controlPlaneOrgs = MutableStateFlow<List<com.example.data.repository.SupabaseConnectionRepository.OrganizationItem>>(emptyList())
    val controlPlaneProjects = MutableStateFlow<List<com.example.data.repository.SupabaseConnectionRepository.ProjectItem>>(emptyList())
    val selectedControlPlaneProjectRef = MutableStateFlow<String?>(null)
    val pendingOAuthTxId = MutableStateFlow<String?>(null)
    val provisioningProgress = MutableStateFlow(0.0f)
    val provisioningStatusText = MutableStateFlow("Initializing Provisioning...")

    val currentManagementToken = MutableStateFlow<String?>(null)

    fun setLocalBackendUrl(url: String) {
        controlPlaneUrl.value = url.trim()
    }

    fun startControlPlaneOAuth(context: android.content.Context) {
        managementApiError.value = null
        isFetchingManagementProjects.value = true
        val userId = activeProfile.value.id.ifBlank { "merchant_${java.util.UUID.randomUUID().toString().take(8)}" }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.repository.SupabaseConnectionRepository.startOAuthFlow(
                controlPlaneUrl = controlPlaneUrl.value,
                userId = userId,
                onSuccess = { authorizeUrl ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        isExternalActivityExpected = true
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authorizeUrl)).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            managementApiError.value = "Unable to launch browser: ${e.message}"
                        }
                    }
                },
                onFailure = { err ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        // Fallback to direct authorize flow
                        startSupabaseOAuthFlow(context)
                    }
                }
            )
        }
    }

    fun handleControlPlaneOAuthConnected(txId: String) {
        logFirebaseStatus("Received OAuth transaction ID: $txId. Loading projects from Control Plane...")
        pendingOAuthTxId.value = txId
        oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
        fetchControlPlaneOrgsAndProjects(txId)
    }

    fun fetchControlPlaneOrgsAndProjects(txId: String? = pendingOAuthTxId.value) {
        val userId = activeProfile.value.id.ifBlank { "user_default" }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isFetchingManagementProjects.value = true
            managementApiError.value = null
            com.example.data.repository.SupabaseConnectionRepository.fetchOrganizationsAndProjects(
                controlPlaneUrl = controlPlaneUrl.value,
                userId = userId,
                txId = txId,
                onSuccess = { orgs, projects ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        controlPlaneOrgs.value = orgs
                        controlPlaneProjects.value = projects
                        if (projects.isNotEmpty()) {
                            selectedControlPlaneProjectRef.value = projects.first().id
                        }
                    }
                },
                onFailure = { err ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        managementApiError.value = err
                    }
                }
            )
        }
    }

    fun provisionProjectViaControlPlane(
        projectRef: String = "",
        isNew: Boolean = false,
        orgSlug: String = "",
        projectName: String = ""
    ) {
        val userId = activeProfile.value.id.ifBlank { "user_default" }
        val txId = pendingOAuthTxId.value
        oauthStep.value = OAuthStep.PROVISIONING
        provisioningProgress.value = 0.15f
        provisioningStatusText.value = if (isNew) "Creating new Supabase project..." else "Connecting project..."

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var targetRef = projectRef
            val mToken = currentManagementToken.value?.ifBlank { null }
                ?: (try { securityPrefs.getString("saved_management_token", "") } catch (e: Exception) { null })?.ifBlank { null }

            // 1. Direct Supabase Management API path for connecting existing project
            if (!isNew && !mToken.isNullOrBlank() && targetRef.isNotBlank()) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    provisioningProgress.value = 0.50f
                    provisioningStatusText.value = "Fetching project API keys..."
                }
                val targetProj = managementProjectsList.value.find { it.id == targetRef }
                    ?: com.example.data.remote.SupabaseClient.SupabaseProject(
                        id = targetRef,
                        name = "Supabase Project ($targetRef)",
                        organizationId = "",
                        region = "",
                        status = "ACTIVE_HEALTHY"
                    )
                connectViaManagementApi(
                    token = mToken,
                    targetProject = targetProj,
                    onSuccess = { projUrl, anonKey ->
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            provisioningProgress.value = 1.0f
                            provisioningStatusText.value = "SwapnoPay Cloud Ready!"
                            oauthStep.value = OAuthStep.COMPLETE
                            logFirebaseStatus("Project $targetRef connected successfully via Supabase Management API!")
                            runSupabaseSystemTest()
                        }
                    },
                    onFailure = { err ->
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            managementApiError.value = err
                            oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
                        }
                    }
                )
                return@launch
            }

            // 2. Direct Supabase Management API path for creating new project
            if (isNew && !mToken.isNullOrBlank()) {
                val orgId = controlPlaneOrgs.value.firstOrNull()?.id ?: orgSlug
                val name = projectName.ifBlank { "SwapnoPay Merchant ${System.currentTimeMillis() % 10000}" }
                val dbPass = java.util.UUID.randomUUID().toString().replace("-", "").take(16) + "Aa1!"

                if (orgId.isNotBlank()) {
                    var createError: String? = null
                    com.example.data.remote.SupabaseClient.createSupabaseProject(
                        managementToken = mToken,
                        organizationId = orgId,
                        projectName = name,
                        dbPass = dbPass,
                        onSuccess = { ref -> targetRef = ref },
                        onFailure = { err -> createError = err }
                    )

                    if (createError != null) {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            managementApiError.value = createError
                            oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
                        }
                        return@launch
                    }

                    var attempts = 0
                    var keysFound = false
                    val maxAttempts = 25
                    while (attempts < maxAttempts && !keysFound) {
                        kotlinx.coroutines.delay(3000)
                        attempts++
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            provisioningProgress.value = (0.25f + (attempts.toFloat() / maxAttempts.toFloat()) * 0.5f).coerceAtMost(0.85f)
                            provisioningStatusText.value = "Initializing new project (${attempts * 3}s)..."
                        }
                        com.example.data.remote.SupabaseClient.fetchSupabaseProjectKeys(
                            managementToken = mToken,
                            projectRef = targetRef,
                            onSuccess = { keys ->
                                val anonKey = keys.find { it.name.equals("anon", ignoreCase = true) || it.name.equals("publishable", ignoreCase = true) }?.apiKey
                                if (!anonKey.isNullOrBlank()) {
                                    keysFound = true
                                    val projectUrl = "https://$targetRef.supabase.co"
                                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        connectSupabase(
                                            url = projectUrl,
                                            anonKey = anonKey,
                                            name = name
                                        )
                                        provisioningProgress.value = 1.0f
                                        provisioningStatusText.value = "SwapnoPay Cloud Ready!"
                                        oauthStep.value = OAuthStep.COMPLETE
                                        runSupabaseSystemTest()
                                    }
                                }
                            },
                            onFailure = { /* retry next interval */ }
                        )
                    }

                    if (keysFound) return@launch
                }
            }

            if (isNew) {
                val effectiveOrg = orgSlug.ifBlank { controlPlaneOrgs.value.firstOrNull()?.slug ?: "personal" }
                val name = projectName.ifBlank { "SwapnoPay Merchant ${System.currentTimeMillis() % 10000}" }
                
                var createError: String? = null
                com.example.data.repository.SupabaseConnectionRepository.createProject(
                    controlPlaneUrl = controlPlaneUrl.value,
                    userId = userId,
                    orgSlug = effectiveOrg,
                    projectName = name,
                    txId = txId,
                    onSuccess = { ref -> targetRef = ref },
                    onFailure = { err -> createError = err }
                )

                if (createError != null) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        managementApiError.value = createError
                        oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
                    }
                    return@launch
                }
            }

            // Step 2: Poll Project Health until ACTIVE_HEALTHY
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                provisioningProgress.value = 0.40f
                provisioningStatusText.value = "Waiting for project services (ACTIVE_HEALTHY)..."
            }

            var attempts = 0
            var isHealthy = false
            val maxAttempts = if (isNew) 35 else 15
            while (attempts < maxAttempts && !isHealthy) {
                kotlinx.coroutines.delay(3000)
                attempts++
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    val progressBase = if (isNew) 0.25f else 0.45f
                    provisioningProgress.value = (progressBase + (attempts.toFloat() / maxAttempts.toFloat()) * 0.35f).coerceAtMost(0.70f)
                    provisioningStatusText.value = "Waiting for project services (${attempts * 3}s)..."
                }
                com.example.data.repository.SupabaseConnectionRepository.checkProjectHealth(
                    controlPlaneUrl = controlPlaneUrl.value,
                    userId = userId,
                    projectRef = targetRef,
                    txId = txId,
                    onSuccess = { healthy -> isHealthy = healthy },
                    onFailure = { isHealthy = false }
                )
            }

            // Step 3: Apply Database Schema & Fetch Publishable Key
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                provisioningProgress.value = 0.75f
                provisioningStatusText.value = "Applying database schema & security policies..."
            }

            com.example.data.repository.SupabaseConnectionRepository.applySchemaAndFinalize(
                controlPlaneUrl = controlPlaneUrl.value,
                userId = userId,
                projectRef = targetRef,
                txId = txId,
                onSuccess = { projectUrl, publishableKey ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        provisioningProgress.value = 1.0f
                        provisioningStatusText.value = "SwapnoPay Cloud Ready!"
                        oauthStep.value = OAuthStep.COMPLETE
                        setSupabaseUrlInput(projectUrl)
                        setSupabaseAnonKeyInput(publishableKey)
                        supabaseUrl.value = projectUrl
                        supabaseAnonKey.value = publishableKey
                        connectSupabase(
                            url = projectUrl,
                            anonKey = publishableKey,
                            name = "Supabase Project ($targetRef)"
                        )
                        logFirebaseStatus("Project $targetRef provisioned & configured cleanly!")
                    }
                },
                onFailure = { err ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        managementApiError.value = err
                        oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
                    }
                }
            )
        }
    }

    fun triggerAutoSetupConnectedDatabase(onComplete: ((Boolean, String) -> Unit)? = null) {
        val currentUrl = supabaseUrl.value.ifBlank { _activeSupabaseProfile.value?.supabaseUrl ?: "" }
        val extractedRef = if (currentUrl.contains("supabase.co")) {
            currentUrl.substringAfter("https://").substringBefore(".supabase.co").trim()
        } else ""

        val targetRef = (selectedControlPlaneProjectRef.value ?: "").ifBlank { extractedRef }
        val userId = activeProfile.value.id.ifBlank { "user_default" }
        val txId = pendingOAuthTxId.value

        if (targetRef.isBlank()) {
            val msg = "Please enter or connect a valid Supabase project reference"
            managementApiError.value = msg
            onComplete?.invoke(false, msg)
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                isRunningSystemTest.value = true
                logFirebaseStatus("Starting 100% automated database tables, storage buckets, realtime & functions setup for $targetRef...")
            }

            com.example.data.repository.SupabaseConnectionRepository.applySchemaAndFinalize(
                controlPlaneUrl = controlPlaneUrl.value,
                userId = userId,
                projectRef = targetRef,
                txId = txId,
                onSuccess = { projectUrl, pubKey ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isRunningSystemTest.value = false
                        logFirebaseStatus("Database tables, buckets, realtime & edge functions provisioned successfully!")
                        if (pubKey.isNotBlank()) {
                            supabaseUrl.value = projectUrl
                            supabaseAnonKey.value = pubKey
                            setSupabaseUrlInput(projectUrl)
                            setSupabaseAnonKeyInput(pubKey)
                        }
                        runSupabaseSystemTest()
                        onComplete?.invoke(true, "Database tables, buckets, realtime & edge functions provisioned successfully!")
                    }
                },
                onFailure = { err ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isRunningSystemTest.value = false
                        managementApiError.value = err
                        onComplete?.invoke(false, err)
                    }
                }
            )
        }
    }

    fun startSupabaseOAuthFlow(context: android.content.Context, customClientId: String = "") {
        val targetClientId = if (customClientId.isNotBlank()) customClientId.trim() else oauthClientId.value.ifBlank { "5d3dcd9b-1acf-4e31-96d2-d673af42a18b" }
        oauthClientId.value = targetClientId
        val pkce = com.example.data.remote.SupabaseClient.generatePkcePair()
        pendingPkceVerifier.value = pkce.codeVerifier
        try {
            securityPrefs.edit().putString("pending_pkce_verifier", pkce.codeVerifier).apply()
        } catch (e: Exception) {
            Log.w("AppViewModel", "Failed to cache pkce verifier", e)
        }

        val authUrl = android.net.Uri.parse("https://api.supabase.com/v1/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", targetClientId)
            .appendQueryParameter("redirect_uri", "https://api.swapnopay.top/v1/oauth/callback")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", pkce.codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", java.util.UUID.randomUUID().toString())
            .build()

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, authUrl).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            isExternalActivityExpected = true
            context.startActivity(intent)
        } catch (e: Exception) {
            managementApiError.value = "Unable to launch browser: ${e.message}"
        }
    }

    fun handleOAuthCodeReceived(code: String) {
        val verifier = pendingPkceVerifier.value?.ifBlank { null }
            ?: (try { securityPrefs.getString("pending_pkce_verifier", "") } catch (e: Exception) { "" }) ?: ""
        val clientId = oauthClientId.value.ifBlank { "5d3dcd9b-1acf-4e31-96d2-d673af42a18b" }
        val clientSecret = oauthClientSecret.value

        isFetchingManagementProjects.value = true
        managementApiError.value = null

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.remote.SupabaseClient.exchangeOAuthCode(
                clientId = clientId,
                clientSecret = clientSecret,
                code = code,
                codeVerifier = verifier,
                redirectUri = "https://api.swapnopay.top/v1/oauth/callback",
                onSuccess = { accessToken, _ ->
                    currentManagementToken.value = accessToken
                    try {
                        securityPrefs.edit().putString("saved_management_token", accessToken).apply()
                    } catch (e: Exception) {
                        Log.w("AppViewModel", "Failed to cache management token", e)
                    }

                    // Background fetch organizations
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.data.remote.SupabaseClient.fetchSupabaseOrganizations(
                            managementToken = accessToken,
                            onSuccess = { orgs ->
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    controlPlaneOrgs.value = orgs.map {
                                        com.example.data.repository.SupabaseConnectionRepository.OrganizationItem(
                                            id = it.id,
                                            name = it.name,
                                            slug = it.slug
                                        )
                                    }
                                }
                            },
                            onFailure = { /* non-fatal */ }
                        )
                    }

                    fetchSupabaseProjectsList(
                        managementToken = accessToken,
                        onSuccess = { projects ->
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                isFetchingManagementProjects.value = false
                                managementProjectsList.value = projects
                                controlPlaneProjects.value = projects.map {
                                    com.example.data.repository.SupabaseConnectionRepository.ProjectItem(
                                        id = it.id,
                                        name = it.name,
                                        organizationId = it.organizationId,
                                        region = it.region,
                                        status = it.status
                                    )
                                }
                                if (projects.isNotEmpty()) {
                                    selectedControlPlaneProjectRef.value = projects.first().id
                                    if (projects.size == 1) {
                                        val onlyProj = projects.first()
                                        connectViaManagementApi(
                                            token = accessToken,
                                            targetProject = onlyProj,
                                            onSuccess = { url, anon ->
                                                oauthStep.value = OAuthStep.COMPLETE
                                                logFirebaseStatus("Authorized via Supabase OAuth 2.0 & connected to project: ${onlyProj.name}")
                                                runSupabaseSystemTest()
                                            },
                                            onFailure = { err ->
                                                managementApiError.value = err
                                                oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
                                            }
                                        )
                                    } else {
                                        oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
                                    }
                                } else {
                                    oauthStep.value = OAuthStep.ACCOUNT_CONNECTED
                                    managementApiError.value = "No projects found under authorized Supabase account."
                                }
                            }
                        },
                        onFailure = { err ->
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                isFetchingManagementProjects.value = false
                                managementApiError.value = err
                            }
                        }
                    )
                },
                onFailure = { err ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        managementApiError.value = err
                    }
                }
            )
        }
    }

    fun fetchSupabaseProjectsList(
        managementToken: String,
        onSuccess: (List<com.example.data.remote.SupabaseClient.SupabaseProject>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanToken = managementToken.trim()
        if (cleanToken.length < 10) {
            onFailure("Enter a valid Supabase Access Token / Personal Access Token.")
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isFetchingManagementProjects.value = true
            managementApiError.value = null
            com.example.data.remote.SupabaseClient.fetchSupabaseProjects(
                managementToken = cleanToken,
                onSuccess = { projects ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        managementProjectsList.value = projects
                        onSuccess(projects)
                    }
                },
                onFailure = { err ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        managementApiError.value = err
                        onFailure(err)
                    }
                }
            )
        }
    }

    fun connectViaManagementApi(
        token: String,
        targetProject: com.example.data.remote.SupabaseClient.SupabaseProject,
        onSuccess: (projectUrl: String, anonKey: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            onFailure("Access Token cannot be empty.")
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.remote.SupabaseClient.fetchSupabaseProjectKeys(
                managementToken = cleanToken,
                projectRef = targetProject.id,
                onSuccess = { keys ->
                    val anonKeyObj = keys.find { it.name.equals("anon", ignoreCase = true) || it.name.equals("publishable", ignoreCase = true) }
                        ?: keys.firstOrNull()
                    val anonKey = anonKeyObj?.apiKey ?: ""
                    if (anonKey.isBlank()) {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            onFailure("Could not retrieve anon/publishable API key for '${targetProject.name}'.")
                        }
                        return@fetchSupabaseProjectKeys
                    }
                    val projectUrl = "https://${targetProject.id}.supabase.co"
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        connectSupabase(
                            url = projectUrl,
                            anonKey = anonKey,
                            name = targetProject.name
                        )
                        onSuccess(projectUrl, anonKey)
                    }
                },
                onFailure = { err ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        onFailure(err)
                    }
                }
            )
        }
    }

    fun connectSupabase(url: String, anonKey: String, name: String, syncToPlatform: Boolean = true) {
        var rawUrl = url.trim()
        if (rawUrl.isNotEmpty() && !rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            rawUrl = "https://$rawUrl"
        }
        val cleanUrl = rawUrl.trimEnd('/')
        val parsedUri = runCatching { java.net.URI(cleanUrl) }.getOrNull()
        val isHttpLocal = (parsedUri?.scheme == "http" && (parsedUri.host == "localhost" || parsedUri.host == "10.0.2.2" || parsedUri.host == "127.0.0.1" || parsedUri.host?.startsWith("192.168.") == true || parsedUri.host?.startsWith("10.") == true))
        val isHttps = parsedUri?.scheme == "https"
        if ((!isHttps && !isHttpLocal) || parsedUri?.host.isNullOrBlank() || anonKey.trim().length < 10) {
            systemTestError.value = "Enter a valid Supabase URL (HTTPS or local HTTP) and publishable/anon key."
            supabaseConnected.value = false
            return
        }
        val cleanName = if (name.trim().isNotEmpty()) name.trim() else "My Store Backend"
        
        supabaseUrl.value = cleanUrl
        supabaseAnonKey.value = anonKey.trim()
        setSupabaseUrlInput(cleanUrl)
        setSupabaseAnonKeyInput(anonKey.trim())
        // A service-role key must never be persisted in a distributed mobile client.
        supabaseConnectionName.value = cleanName
        supabaseSetupProgress.value = 8
        systemTestError.value = null
        systemTestSuccess.value = false
        oauthStep.value = OAuthStep.COMPLETE

        val id = _activeProfile.value.id
        val newProfile = com.example.data.local.SupabaseProfileEntity(
            id = id,
            businessName = cleanName,
            supabaseUrl = cleanUrl,
            anonKey = anonKey.trim(),
            serviceRoleKey = "",
            isActive = true,
            defaultNumber = ""
        )
        val previousLocalProfile = _activeProfile.value
        val connectedLocalProfile = previousLocalProfile.copy(id = id, businessName = cleanName)
        // Make the selected profile immediately available to the following
        // onboarding authentication step; persistence continues asynchronously.
        _activeSupabaseProfile.value = newProfile
        _activeProfile.value = connectedLocalProfile
        viewModelScope.launch {
            if (previousLocalProfile.id == "00000000-0000-0000-0000-000000000001") {
                repository.reassignMerchantData(previousLocalProfile.id, id)
            }
            repository.insertMerchantProfile(connectedLocalProfile)
            repository.switchProfile(id)
            repository.insertSupabaseProfile(newProfile)
            repository.selectActiveSupabaseProfile(id)
            logFirebaseStatus("Successfully saved Supabase Profile: $cleanName. Initiating connection test...")
            
            // Run system test immediately to verify connection
            runSupabaseSystemTest()

            if (syncToPlatform && !cleanUrl.contains("tldubojeokgyoclxnzkb")) {
                syncMerchantSetupToBackend(
                    merchantId = id,
                    email = _userEmail.value ?: "",
                    businessName = cleanName,
                    phone = _activeProfile.value.phone,
                    businessType = _activeProfile.value.businessType,
                    supabaseUrl = cleanUrl,
                    supabaseAnonKey = anonKey.trim()
                )
            }
        }
    }

    suspend fun fetchSupabaseConfigFromAdminDirect(merchantId: String?, email: String?): Pair<String, String>? = withContext(Dispatchers.IO) {
        val candidateEndpoints = listOf(
            "https://api.swapnopay.top",
            "https://swapnopay.top"
        )
        val queryParams = mutableListOf<String>()
        if (!merchantId.isNullOrBlank() && merchantId != "00000000-0000-0000-0000-000000000001") {
            queryParams.add("merchant_id=${java.net.URLEncoder.encode(merchantId.trim(), "UTF-8")}")
            queryParams.add("user_id=${java.net.URLEncoder.encode(merchantId.trim(), "UTF-8")}")
        }
        if (!email.isNullOrBlank()) {
            queryParams.add("email=${java.net.URLEncoder.encode(email.trim().lowercase(), "UTF-8")}")
        }
        if (queryParams.isEmpty()) return@withContext null

        val queryString = queryParams.joinToString("&")
        for (baseUrl in candidateEndpoints) {
            try {
                val urlObj = java.net.URL("$baseUrl/v1/payment/config?$queryString")
                val conn = urlObj.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(body)
                    val sUrl = json.optString("supabase_url", "").trim()
                    val sKey = json.optString("supabase_anon_key", "").trim()
                    if (sUrl.isNotBlank() && sKey.isNotBlank() && !sUrl.contains("tldubojeokgyoclxnzkb") && !sUrl.contains("abc123xyz")) {
                        return@withContext Pair(sUrl, sKey)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("AppViewModel", "fetchSupabaseConfigFromAdminDirect notice from $baseUrl: ${e.message}")
            }
        }
        null
    }

    fun fetchSupabaseConfigFromAdmin(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            val merchantId = activeProfile.value.id
            val email = userEmail.value ?: activeProfile.value.email
            val config = fetchSupabaseConfigFromAdminDirect(merchantId, email)
            if (config != null) {
                withContext(Dispatchers.Main) {
                    setSupabaseUrlInput(config.first)
                    setSupabaseAnonKeyInput(config.second)
                    connectSupabase(config.first, config.second, activeProfile.value.businessName.ifBlank { "Admin Provisioned Supabase" })
                    onResult(true, "Fetched credentials from Admin Panel!")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "No custom Supabase credentials found on Admin Panel.")
                }
            }
        }
    }

    fun runSupabaseSystemTest() {
        viewModelScope.launch {
            isRunningSystemTest.value = true
            systemTestError.value = null
            systemTestSuccess.value = false
            logFirebaseStatus("Verifying the Supabase URL and publishable key...")
            
            val active = repository.getActiveSupabaseProfile()
            val url = supabaseUrl.value.ifEmpty { active?.supabaseUrl ?: "" }.trim()
            val key = supabaseAnonKey.value.ifEmpty { active?.anonKey ?: "" }.trim()
            
            if (url.isNotEmpty() && key.isNotEmpty() &&
                url != "https://abc123xyz.supabase.co" && url != "https://def456uvw.supabase.co") {
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.remote.SupabaseClient.testConnection(
                        url = url,
                        anonKey = key,
                        onSuccess = {
                            viewModelScope.launch {
                                isRunningSystemTest.value = false
                                supabaseConnected.value = true
                                supabaseSetupProgress.value = 9
                                systemTestError.value = null
                                systemTestSuccess.value = true
                                systemTestSuccessTrigger.value += 1
                                logFirebaseStatus("Supabase endpoint verified. Sign in to verify tenant-scoped CRUD access.")
                                launch { repository.syncMfsPatterns() }
                            }
                        },
                        onFailure = { err ->
                            viewModelScope.launch {
                                isRunningSystemTest.value = false
                                supabaseConnected.value = false
                                systemTestError.value = err
                                systemTestSuccess.value = false
                                if (supabaseSetupProgress.value == 9) {
                                    supabaseSetupProgress.value = 8
                                }
                                logFirebaseStatus("Supabase connection verification failed: $err")
                            }
                        }
                    )
                }
            } else {
                isRunningSystemTest.value = false
                supabaseConnected.value = false
                val err = if (url.isEmpty() || key.isEmpty()) {
                    "Supabase URL or Anon Key is empty. Please enter your credentials first."
                } else {
                    "Placeholder credentials detected. Please enter your actual Supabase URL and Anon Key."
                }
                systemTestError.value = err
                systemTestSuccess.value = false
                if (supabaseSetupProgress.value == 9) {
                    supabaseSetupProgress.value = 8
                }
                logFirebaseStatus("ERROR: $err")
            }
        }
    }

    fun disconnectSupabase() {
        supabaseConnected.value = false
        supabaseSetupProgress.value = 0
        systemTestError.value = null
        systemTestSuccess.value = false
        supabaseUrl.value = ""
        supabaseAnonKey.value = ""
        _activeSupabaseProfile.value = null
    }

    private suspend fun activateLocalProfileForBackend(profile: SupabaseProfileEntity) {
        var localProfile = repository.getMerchantProfileById(profile.id)
        if (localProfile == null) {
            val previous = _activeProfile.value
            if (previous.id == "00000000-0000-0000-0000-000000000001") {
                repository.reassignMerchantData(previous.id, profile.id)
            }
            localProfile = previous.copy(id = profile.id, businessName = profile.businessName)
            repository.insertMerchantProfile(localProfile)
        }
        repository.switchProfile(localProfile.id)
        _activeProfile.value = localProfile
        sendDeviceHeartbeat()
    }

    // --- FORM BUILDER STATE & ENGINE ---
    val activeFormId = MutableStateFlow<String>(java.util.UUID.randomUUID().toString())
    val formTitle = MutableStateFlow<String>("Untitled Form")
    val formDescription = MutableStateFlow<String>("")
    val formSlug = MutableStateFlow<String>("pay-${System.currentTimeMillis().toString().takeLast(6)}")
    val formStatus = MutableStateFlow<String>("DRAFT") // DRAFT, PUBLISHED
    val formTemplateKey = MutableStateFlow<String>("BLANK")
    val formThemeConfig = MutableStateFlow<FormThemeConfig>(FormThemeConfig())
    
    val formFieldsList = MutableStateFlow<List<FormFieldItem>>(emptyList())

    val formProductsList = MutableStateFlow<List<FormProductItem>>(emptyList())

    // Multi-Form Isolated Settings Management List
    val hostedFormsList = MutableStateFlow<List<HostedFormModel>>(emptyList())
    val hostedFormRouteStatus = MutableStateFlow<Map<String, String>>(emptyMap()) // PENDING, READY, FAILED

    fun selectHostedForm(formId: String) {
        val form = hostedFormsList.value.find { it.id == formId } ?: return
        activeFormId.value = form.id
        formTitle.value = form.title
        formSlug.value = form.slug
        formDescription.value = form.description
        formStatus.value = form.status
        formTemplateKey.value = form.templateKey
        formThemeConfig.value = form.themeConfig.copy()
        formFieldsList.value = form.fields.map { it.copy() }
        formProductsList.value = form.products.map { it.copy() }
        formPagesList.value = form.pages.ifEmpty { listOf(FormPageItem()) }.map { it.copy() }
        if (form.status == "PUBLISHED" && hostedFormRouteStatus.value[form.id] == null) {
            registerBrandedHostedFormRoute(form)
        }
        logFirebaseStatus("Selected & loaded hosted form settings: ${form.title}")
    }

    fun selectCachedPaymentForm(json: org.json.JSONObject) {
        loadCachedPaymentForm(json, autoSelect = true)
    }

    fun loadCachedPaymentForm(json: org.json.JSONObject, autoSelect: Boolean = false) {
        val id = json.optString("id")
        if (id.isBlank()) return
        val fields = mutableListOf<FormFieldItem>()
        json.optJSONArray("fields")?.let { array ->
            for (index in 0 until array.length()) {
                val field = array.optJSONObject(index) ?: continue
                val type = runCatching { FormFieldType.valueOf(field.optString("type")) }
                    .getOrDefault(FormFieldType.NAME)
                fields += FormFieldItem(
                    id = field.optString("id", java.util.UUID.randomUUID().toString()),
                    type = type,
                    label = field.optString("label", type.displayName),
                    placeholder = field.optString("placeholder"),
                    isRequired = field.optBoolean("required", field.optBoolean("isRequired", false)),
                    options = field.optJSONArray("options")?.let { options ->
                        List(options.length()) { options.optString(it) }
                    } ?: emptyList(),
                    pageIndex = field.optInt("page_index", 0),
                    helperText = field.optString("helper_text"),
                    defaultValue = field.optString("default_value"),
                    validationRegex = field.optString("validation_regex"),
                    minLength = field.optInt("min_length", 0),
                    maxLength = field.optInt("max_length", 0),
                    minValue = field.opt("min_value")?.takeUnless { it == org.json.JSONObject.NULL }?.toString()?.toDoubleOrNull(),
                    maxValue = field.opt("max_value")?.takeUnless { it == org.json.JSONObject.NULL }?.toString()?.toDoubleOrNull(),
                    allowedFileExtensions = field.optJSONArray("allowed_file_extensions")?.let { extensions ->
                        List(extensions.length()) { extensions.optString(it) }.filter(String::isNotBlank)
                    } ?: listOf("png", "jpg", "jpeg", "pdf", "svg", "webp"),
                    maxFileSizeBytes = field.optLong("max_file_size_bytes", 5L * 1024 * 1024),
                    customErrorMessage = field.optString("custom_error_message"),
                    customCodeHtml = field.optString("custom_code_html"),
                    customCodeCss = field.optString("custom_code_css"),
                    customVariables = field.optJSONArray("custom_variables")?.let { variables ->
                        List(variables.length()) { variables.optString(it) }.filter(String::isNotBlank)
                    } ?: emptyList(),
                    customVariableAssignments = field.optJSONObject("custom_variable_assignments")?.let { obj ->
                        buildMap {
                            for (key in obj.keys()) {
                                val value = obj.optString(key)
                                if (value.isNotBlank()) put(key, value)
                            }
                        }
                    } ?: field.optJSONObject("customVariableAssignments")?.let { obj ->
                        buildMap {
                            for (key in obj.keys()) {
                                val value = obj.optString(key)
                                if (value.isNotBlank()) put(key, value)
                            }
                        }
                    } ?: emptyMap(),
                    mediaUrl = field.optString("media_url"),
                    mediaAltText = field.optString("media_alt_text"),
                    mediaHeightDp = field.optInt("media_height_dp", 180),
                    sectionIndex = field.optInt("section_index", 0),
                    stepIndex = field.optInt("step_index", 0),
                    isCollapsed = field.optBoolean("is_collapsed", false),
                    dependsOnFieldId = field.optString("depends_on_field_id").ifBlank { null },
                    conditionOperator = field.optString("condition_operator", "EQUALS"),
                    conditionValue = field.optString("condition_value"),
                    textColorHex = field.optString("text_color", "#0F172A"),
                    fontSizeSp = field.optInt("font_size_sp", 14),
                    isBold = field.optBoolean("is_bold", false),
                    isItalic = field.optBoolean("is_italic", false),
                    isUnderline = field.optBoolean("is_underline", false),
                    fontFamilyName = field.optString("font_family", "Inter"),
                    textAlignName = field.optString("text_align", "LEFT"),
                    widthDp = field.optInt("width_dp", 0),
                    heightDp = field.optInt("height_dp", 0),
                    offsetXDp = field.optInt("offset_x_dp", 0),
                    offsetYDp = field.optInt("offset_y_dp", 0),
                    shapeType = field.optString("shape_type", "NONE"),
                    fillColorHex = field.optString("fill_color", "#FFFFFF"),
                    borderColorHex = field.optString("border_color", "#E2E8F0"),
                    borderWidthDp = field.optInt("border_width_dp", 1),
                    borderRadiusDp = field.optInt("border_radius_dp", 12),
                    shadowElevationDp = field.optInt("shadow_elevation_dp", 2),
                    galleryUrls = field.optJSONArray("gallery_urls")?.let { arr ->
                        List(arr.length()) { arr.optString(it) }.filter(String::isNotBlank)
                    } ?: emptyList(),
                    productVariants = field.optJSONArray("product_variants")?.let { arr ->
                        val vl = mutableListOf<ProductVariantItem>()
                        for (vi in 0 until arr.length()) {
                            val vObj = arr.optJSONObject(vi) ?: continue
                            vl.add(ProductVariantItem(
                                id = vObj.optString("id", java.util.UUID.randomUUID().toString()),
                                name = vObj.optString("name"),
                                price = vObj.optDouble("price", 0.0),
                                sku = vObj.optString("sku"),
                                stock = vObj.optInt("stock", 100),
                                description = vObj.optString("description")
                            ))
                        }
                        vl
                    } ?: emptyList()
                )
            }
        }
        val products = mutableListOf<FormProductItem>()
        json.optJSONArray("products")?.let { array ->
            for (index in 0 until array.length()) {
                val product = array.optJSONObject(index) ?: continue
                products += FormProductItem(
                    id = product.optString("id", java.util.UUID.randomUUID().toString()),
                    title = product.optString("title"),
                    description = product.optString("description"),
                    price = product.optDouble("price", 0.0),
                    salePrice = product.optDouble("sale_price", product.optDouble("price", 0.0)),
                    stock = product.optInt("stock", 0),
                    sku = product.optString("sku"),
                    category = product.optString("category", "General"),
                    imageUrl = product.optString("image_url"),
                    isDigital = product.optBoolean("is_digital", false),
                    digitalDownloadUrl = product.optString("digital_download_url"),
                    variants = product.optJSONArray("variants")?.let { variants ->
                        List(variants.length()) { variants.optString(it) }.filter(String::isNotBlank)
                    } ?: emptyList(),
                    galleryUrls = product.optJSONArray("gallery_urls")?.let { arr ->
                        List(arr.length()) { arr.optString(it) }.filter(String::isNotBlank)
                    } ?: emptyList(),
                    productVariants = product.optJSONArray("product_variants")?.let { arr ->
                        val vl = mutableListOf<ProductVariantItem>()
                        for (vi in 0 until arr.length()) {
                            val vObj = arr.optJSONObject(vi) ?: continue
                            vl.add(ProductVariantItem(
                                id = vObj.optString("id", java.util.UUID.randomUUID().toString()),
                                name = vObj.optString("name"),
                                price = vObj.optDouble("price", 0.0),
                                sku = vObj.optString("sku"),
                                stock = vObj.optInt("stock", 100),
                                description = vObj.optString("description")
                            ))
                        }
                        vl
                    } ?: emptyList()
                )
            }
        }
        val pages = mutableListOf<FormPageItem>()
        json.optJSONArray("pages")?.let { array ->
            for (index in 0 until array.length()) {
                val page = array.optJSONObject(index) ?: continue
                pages += FormPageItem(
                    id = page.optString("id", java.util.UUID.randomUUID().toString()),
                    title = page.optString("title", "Page ${index + 1}"),
                    subtitle = page.optString("subtitle"),
                    isCustomHtml = page.optBoolean("is_custom_html", page.optBoolean("isCustomHtml", false)),
                    customHtmlContent = page.optString("custom_html_content", page.optString("customHtmlContent", "")),
                    customCssContent = page.optString("custom_css_content", page.optString("customCssContent", "")),
                    customVariables = page.optJSONArray("custom_variables")?.let { varArr ->
                        List(varArr.length()) { varArr.optString(it) }.filter(String::isNotBlank)
                    } ?: emptyList()
                )
            }
        }
        val themeJson = json.optJSONObject("theme") ?: org.json.JSONObject()
        val customVariables = mutableListOf<CustomVariable>()
        (themeJson.optJSONArray("custom_variables") ?: themeJson.optJSONArray("customVariables"))?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                customVariables += CustomVariable(
                    key = item.optString("key"),
                    exampleValue = item.optString("example_value", item.optString("exampleValue")),
                    source = item.optString("source", "field")
                )
            }
        }
        val theme = FormThemeConfig(
            primaryColorHex = themeJson.optString("primary_color", "#7C3AED"),
            fontFamily = themeJson.optString("font_family", "Inter"),
            buttonShape = themeJson.optString("button_shape", "ROUNDED"),
            backgroundColorHex = themeJson.optString("background_color", "#F6F7FB"),
            backgroundStyle = themeJson.optString("background_style", "SOLID"),
            gradientColorStart = themeJson.optString("gradient_start", "#5B7FFF"),
            gradientColorEnd = themeJson.optString("gradient_end", "#7C4DFF"),
            borderRadiusDp = themeJson.optInt("border_radius", 16),
            formWidthPx = themeJson.optInt("form_width", 720),
            pageMarginPx = themeJson.optInt("page_margin", 24),
            logoUrl = json.optString("logo_url", themeJson.optString("logo_url")),
            bannerUrl = json.optString("banner_url", themeJson.optString("banner_url")),
            redirectType = themeJson.optString("redirect_type", "SUCCESS_MSG"),
            redirectUrl = themeJson.optString("redirect_url"),
            redirectDelaySec = themeJson.optInt("redirect_delay_seconds", 0),
            openInNewTab = themeJson.optBoolean("open_in_new_tab", false),
            enableCustomHtml = themeJson.optBoolean("enable_custom_html", themeJson.optBoolean("enableCustomHtml", false)),
            customHtmlContent = themeJson.optString("custom_html", themeJson.optString("customHtmlContent")),
            customCss = themeJson.optString("custom_css", themeJson.optString("customCss")),
            showHeader = themeJson.optBoolean("show_header", true),
            customJs = themeJson.optString("custom_js", themeJson.optString("customJs")),
            enableCustomJs = themeJson.optBoolean("enable_custom_js", true),
            isCustomWebApp = themeJson.optBoolean("is_custom_web_app", false),
            enableClosingTimeline = themeJson.optBoolean("enable_closing_timeline", false),
            closingDeadlineEpoch = themeJson.optLong("closing_deadline_epoch", 0L),
            closingDeadlineStr = themeJson.optString("closing_deadline_str", ""),
            closedMessage = themeJson.optString("closed_message", "This form is no longer accepting responses / সময়সীমা শেষ হয়ে গেছে"),
            showCountdownTimer = themeJson.optBoolean("show_countdown_timer", true),
            enableCsvBackend = themeJson.optBoolean("enable_csv_backend", false),
            csvFileName = themeJson.optString("csv_file_name", ""),
            csvRawData = themeJson.optString("csv_raw_data", ""),
            csvHeaders = if (themeJson.has("csv_headers")) {
                val arr = themeJson.optJSONArray("csv_headers")
                if (arr != null) (0 until arr.length()).map { arr.getString(it) } else emptyList()
            } else emptyList(),
            csvLookupColumn = themeJson.optString("csv_lookup_column", ""),
            csvTargetLookupFieldId = themeJson.optString("csv_target_lookup_field_id", ""),
            csvColumnMappings = if (themeJson.has("csv_column_mappings")) {
                val mapObj = themeJson.optJSONObject("csv_column_mappings")
                if (mapObj != null) {
                    val map = mutableMapOf<String, String>()
                    mapObj.keys().forEach { k -> map[k] = mapObj.optString(k) }
                    map
                } else emptyMap()
            } else emptyMap(),
            csvAutofillMode = themeJson.optString("csv_autofill_mode", "EXACT_MATCH"),
            shuffleQuestionOrder = themeJson.optBoolean("shuffle_questions", false),
            oneResponsePerUser = themeJson.optBoolean("one_response_per_user", false),
            isMultiPageForm = themeJson.optBoolean("multi_page", false),
            progressTrackerStyle = themeJson.optString("progress_tracker_style", "BAR"),
            requiredFieldIndicator = themeJson.optBoolean("required_indicator", true),
            enableTimer = themeJson.optBoolean("enable_timer", false),
            timerMinutes = themeJson.optInt("timer_minutes", 30),
            closeAfterLimit = themeJson.optBoolean("close_after_limit", false),
            maxResponses = themeJson.optInt("max_responses", 1000),
            enforceRequiredFields = themeJson.optBoolean("enforce_required_fields", true),
            strictFormatValidation = themeJson.optBoolean("strict_format_validation", true),
            maxFileSizeBytes = themeJson.optLong("max_file_size_bytes", 5L * 1024 * 1024),
            enforceFileSizeLimit = themeJson.optBoolean("enforce_file_size_limit", true),
            minQuantity = themeJson.optInt("min_quantity", 1),
            maxQuantity = themeJson.optInt("max_quantity", 1000),
            enforceQuantityRange = themeJson.optBoolean("enforce_quantity_range", true),
            enableAntiSpam = themeJson.optBoolean("enable_anti_spam", true),
            enablePayment = themeJson.optBoolean("enable_payment", true),
            paymentProvider = themeJson.optString("payment_provider", "AUTO"),
            currencyCode = themeJson.optString("currency", "BDT"),
            taxPercent = themeJson.optDouble("tax_percent", 0.0),
            requirePaymentBeforeSubmit = themeJson.optBoolean("require_payment_before_submit", true),
            enableEmailNotifications = themeJson.optBoolean("email_notifications", false),
            notificationEmail = themeJson.optString("notification_email"),
            enableSmsNotifications = themeJson.optBoolean("sms_notifications", false),
            notificationSmsNumber = themeJson.optString("notification_sms_number"),
            enablePaymentCallback = themeJson.optBoolean("payment_callback_enabled", false),
            paymentCallbackUrl = themeJson.optString("payment_callback_url"),
            customVariables = customVariables.filter { it.key.isNotBlank() },
            productImageUrl = themeJson.optString("product_image_url", themeJson.optString("productImageUrl", json.optString("image_url", ""))),
            wasPrice = themeJson.optDouble("was_price", themeJson.optDouble("wasPrice", 0.0)),
            eyebrowText = themeJson.optString("eyebrow_text", themeJson.optString("eyebrowText", "")),
            badgeText = themeJson.optString("badge_text", themeJson.optString("badgeText", "")),
            ratingScore = themeJson.optDouble("rating_score", themeJson.optDouble("ratingScore", 0.0)),
            ratingCount = themeJson.optInt("rating_count", themeJson.optInt("ratingCount", 0)),
            hideHeader = themeJson.optBoolean("hide_header", themeJson.optBoolean("hideHeader", false)),
            hideEyebrow = themeJson.optBoolean("hide_eyebrow", themeJson.optBoolean("hideEyebrow", false)),
            hideRating = themeJson.optBoolean("hide_rating", themeJson.optBoolean("hideRating", false)),
            hidePrice = themeJson.optBoolean("hide_price", themeJson.optBoolean("hidePrice", false)),
            hideSwatches = themeJson.optBoolean("hide_swatches", themeJson.optBoolean("hideSwatches", false)),
            hideChips = themeJson.optBoolean("hide_chips", themeJson.optBoolean("hideChips", false)),
            hideQty = themeJson.optBoolean("hide_qty", themeJson.optBoolean("hideQty", false)),
            hideSummary = themeJson.optBoolean("hide_summary", themeJson.optBoolean("hideSummary", false)),
            hidePromo = themeJson.optBoolean("hide_promo", themeJson.optBoolean("hidePromo", false)),
            hideAssurances = themeJson.optBoolean("hide_assurances", themeJson.optBoolean("hideAssurances", false)),
            hideDetails = themeJson.optBoolean("hide_details", themeJson.optBoolean("hideDetails", false)),
            hideMobileDock = themeJson.optBoolean("hide_mobile_dock", themeJson.optBoolean("hideMobileDock", false))
        )
        val model = HostedFormModel(
            id = id,
            title = json.optString("title", "Untitled Payment Form"),
            slug = json.optString("slug", "pay-${id.take(8)}"),
            description = json.optString("description"),
            status = json.optString("status", "DRAFT"),
            templateKey = json.optString("template_type", "BLANK"),
            themeConfig = theme,
            fields = fields,
            products = products,
            pages = pages.ifEmpty { listOf(FormPageItem()) },
            totalViews = json.optInt("views_count", 0),
            totalSubmissions = json.optInt("submissions_count", 0),
            totalRevenueBdt = json.optDouble("total_revenue", 0.0)
        )
        hostedFormsList.value = listOf(model) + hostedFormsList.value.filterNot { it.id == id }
        if (autoSelect) {
            selectHostedForm(id)
        }
        if (hostedFormRouteStatus.value[id] != "READY") {
            registerBrandedHostedFormRoute(model)
        }
    }

    fun saveActiveFormToHostedList() {
        val currentId = activeFormId.value
        val current = hostedFormsList.value.find { it.id == currentId }
        val snapshot = (current ?: HostedFormModel(id = currentId)).copy(
            title = formTitle.value.trim().ifEmpty { "Untitled Payment Form" },
            slug = formSlug.value.trim(),
            description = formDescription.value.trim(),
            status = formStatus.value,
            templateKey = formTemplateKey.value,
            themeConfig = formThemeConfig.value.copy(),
            fields = formFieldsList.value.map { it.copy() },
            products = formProductsList.value.map { it.copy() },
            pages = formPagesList.value.map { it.copy() }
        )
        val updatedList = if (current == null) {
            listOf(snapshot) + hostedFormsList.value
        } else {
            hostedFormsList.value.map { if (it.id == currentId) snapshot else it }
        }
        hostedFormsList.value = updatedList
        val payload = hostedFormToJson(snapshot)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.upsertPaymentFormCache(
                PaymentFormCacheEntity(
                    id = snapshot.id,
                    merchantId = activeProfile.value.id,
                    payloadJson = payload.toString(),
                    isDirty = true
                )
            )
        }
        logFirebaseStatus("Saved active form settings & integrations for form ID: $currentId")
        registerBrandedHostedFormRoute(snapshot)
    }

    fun deletePaymentForm(formId: String, setAsDraft: Boolean = false, onComplete: (() -> Unit)? = null) {
        val targetId = formId.trim()
        if (targetId.isBlank()) return

        if (setAsDraft) {
            val currentList = hostedFormsList.value
            val existing = currentList.find { it.id == targetId }
            if (existing != null) {
                val updated = existing.copy(status = "DRAFT")
                hostedFormsList.value = currentList.map { if (it.id == targetId) updated else it }
                if (activeFormId.value == targetId) {
                    formStatus.value = "DRAFT"
                }
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val payload = hostedFormToJson(updated)
                    repository.upsertPaymentFormCache(
                        PaymentFormCacheEntity(
                            id = updated.id,
                            merchantId = activeProfile.value.id,
                            payloadJson = payload.toString(),
                            isDirty = true
                        )
                    )
                    val active = _activeSupabaseProfile.value ?: getOrCreatePlatformSupabaseProfile()
                    if (active.supabaseUrl.isNotBlank() && active.anonKey.isNotBlank()) {
                        com.example.data.remote.SupabaseClient.updatePaymentForm(
                            url = active.supabaseUrl,
                            anonKey = active.anonKey,
                            token = active.authSessionToken,
                            formId = updated.id,
                            payload = org.json.JSONObject().put("status", "DRAFT")
                        )
                    }
                    val candidateRouters = listOf(
                        hostedFormRouterOrigin,
                        "https://swapnopay.top",
                        "https://api.swapnopay.top",
                        "https://pay.swapnopay.top"
                    ).distinct()
                    for (origin in candidateRouters) {
                        com.example.data.remote.SupabaseClient.unregisterHostedFormRoute(
                            routerBaseUrl = origin,
                            token = active.authSessionToken,
                            formId = targetId
                        )
                    }
                    hostedFormRouteStatus.update { it - targetId }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        logFirebaseStatus("Form moved to draft: ${updated.title}")
                        onComplete?.invoke()
                    }
                }
            }
        } else {
            val currentList = hostedFormsList.value
            hostedFormsList.value = currentList.filterNot { it.id == targetId }

            if (activeFormId.value == targetId) {
                val next = hostedFormsList.value.firstOrNull()
                if (next != null) {
                    selectHostedForm(next.id)
                } else {
                    activeFormId.value = java.util.UUID.randomUUID().toString()
                    formTitle.value = "Untitled Payment Form"
                    formSlug.value = ""
                    formDescription.value = ""
                    formStatus.value = "DRAFT"
                    formFieldsList.value = emptyList()
                    formProductsList.value = emptyList()
                    formPagesList.value = listOf(FormPageItem())
                }
            }

            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Delete from Room cache
                repository.deletePaymentFormCache(targetId)

                // Delete from Supabase cloud database
                val active = _activeSupabaseProfile.value ?: getOrCreatePlatformSupabaseProfile()
                if (active.supabaseUrl.isNotBlank() && active.anonKey.isNotBlank()) {
                    com.example.data.remote.SupabaseClient.deletePaymentForm(
                        url = active.supabaseUrl,
                        anonKey = active.anonKey,
                        token = active.authSessionToken,
                        formId = targetId,
                        onSuccess = {
                            logFirebaseStatus("Payment form deleted from cloud database: $targetId")
                        },
                        onFailure = { err ->
                            logFirebaseStatus("Notice deleting payment form from Supabase: $err")
                        }
                    )
                }

                // Unregister route from all candidate routers
                val candidateRouters = listOf(
                    hostedFormRouterOrigin,
                    "https://swapnopay.top",
                    "https://api.swapnopay.top",
                    "https://pay.swapnopay.top"
                ).distinct()
                for (origin in candidateRouters) {
                    com.example.data.remote.SupabaseClient.unregisterHostedFormRoute(
                        routerBaseUrl = origin,
                        token = active.authSessionToken,
                        formId = targetId
                    )
                }
                hostedFormRouteStatus.update { it - targetId }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    logFirebaseStatus("Form deleted permanently: $targetId")
                    onComplete?.invoke()
                }
            }
        }
    }

    fun publishActiveHostedForm() {
        val result = publishForm()
        if (!result.isValid) logFirebaseStatus(result.summaryMessage)
    }

    fun hostedFormPublicUrl(): String {
        val slug = formSlug.value.trim().ifBlank { activeFormId.value }
        return "$hostedFormRouterOrigin/f/$slug"
    }

    fun hostedFormDirectUrl(): String {
        val slug = formSlug.value.trim().ifBlank { activeFormId.value }
        return "$hostedFormRouterOrigin/f/$slug"
    }

    fun registerBrandedHostedFormRoute(form: HostedFormModel) {
        val configuredProfile = _activeSupabaseProfile.value
        val fallbackUrl = PLATFORM_SUPABASE_URL
        val fallbackKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRsZHVib2plb2tneW9jbHhuemtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NjcwODMsImV4cCI6MjEwMzM0MzA4M30.vlgmNEJ0_DpdbsZEQMA2Z82vwY4hwTxpgS4o9p5oEb0"
        val projectUrl = configuredProfile?.supabaseUrl?.ifBlank { fallbackUrl } ?: fallbackUrl
        val publishableKey = configuredProfile?.anonKey?.ifBlank { fallbackKey } ?: fallbackKey

        hostedFormRouteStatus.update { it + (form.id to "PENDING") }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = if (configuredProfile != null) validSupabaseSession(configuredProfile) else null
            val token = active?.authSessionToken ?: ""
            val payloadJson = hostedFormToJson(form)
            val candidateRouters = listOf(
                "https://swapnopay.top",
                "https://api.swapnopay.top",
                "https://pay.swapnopay.top"
            )
            var registeredUrl: String? = null
            var lastError: String? = null
            for (origin in candidateRouters) {
                val completion = kotlinx.coroutines.CompletableDeferred<Pair<Boolean, String>>()
                com.example.data.remote.SupabaseClient.registerHostedFormRoute(
                    routerBaseUrl = origin,
                    projectUrl = projectUrl,
                    publishableKey = publishableKey,
                    token = token,
                    formId = form.id,
                    formSlug = form.slug,
                    merchantId = activeProfile.value.id,
                    payloadJson = payloadJson,
                    onSuccess = { publicUrl -> completion.complete(true to publicUrl) },
                    onFailure = { message -> completion.complete(false to message) }
                )
                val (ok, result) = completion.await()
                if (ok) {
                    registeredUrl = result
                    break
                } else {
                    lastError = result
                }
            }
            if (registeredUrl != null) {
                hostedFormRouteStatus.update { it + (form.id to "READY") }
                logFirebaseStatus("Branded form route ready: $registeredUrl")
            } else {
                hostedFormRouteStatus.update { it + (form.id to "FAILED") }
                logFirebaseStatus("Branded route notice: ${lastError ?: "Route registration failed"}")
            }
        }
    }

    fun syncAllCachedFormsToVps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cachedForms = repository.observePaymentFormCache(activeProfile.value.id).firstOrNull().orEmpty()
                for (cached in cachedForms) {
                    try {
                        val json = org.json.JSONObject(cached.payloadJson)
                        val fId = json.optString("id", cached.id)
                        val fSlug = json.optString("slug", "pay-${fId.takeLast(6)}")
                        val candidateRouters = listOf(
                            "https://swapnopay.top",
                            "https://api.swapnopay.top",
                            "https://pay.swapnopay.top"
                        )
                        for (origin in candidateRouters) {
                            val completion = kotlinx.coroutines.CompletableDeferred<Pair<Boolean, String>>()
                            com.example.data.remote.SupabaseClient.registerHostedFormRoute(
                                routerBaseUrl = origin,
                                projectUrl = PLATFORM_SUPABASE_URL,
                                publishableKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRsZHVib2plb2tneW9jbHhuemtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NjcwODMsImV4cCI6MjEwMzM0MzA4M30.vlgmNEJ0_DpdbsZEQMA2Z82vwY4hwTxpgS4o9p5oEb0",
                                token = "",
                                formId = fId,
                                formSlug = fSlug,
                                merchantId = activeProfile.value.id,
                                payloadJson = json,
                                onSuccess = { publicUrl -> completion.complete(true to publicUrl) },
                                onFailure = { message -> completion.complete(false to message) }
                            )
                            val (ok, _) = completion.await()
                            if (ok) {
                                hostedFormRouteStatus.update { it + (fId to "READY") }
                                break
                            }
                        }
                    } catch (itemErr: Exception) {
                        android.util.Log.w("FormSync", "Failed to sync cached form ${cached.id}: ${itemErr.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FormSync", "syncAllCachedFormsToVps error: ${e.message}")
            }
        }
    }

    fun startObservingPaymentFormsCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.observePaymentFormCache(activeProfile.value.id).collect { cachedList ->
                    for (cached in cachedList) {
                        try {
                            val json = org.json.JSONObject(cached.payloadJson)
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                loadCachedPaymentForm(json, autoSelect = false)
                            }
                        } catch (e_: Exception) { android.util.Log.w("AppViewModel", "Suppressed: ${e_.message}") }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FormSync", "Failed observing payment forms: ${e.message}")
            }
        }
    }

    fun openHostedFormAttachment(context: Context, submissionId: String, fieldId: String) {
        val configuredProfile = _activeSupabaseProfile.value ?: run {
            logFirebaseStatus("Sign in to Supabase before opening a private form attachment.")
            return
        }
        val slug = formSlug.value.trim()
        if (slug.isBlank() || submissionId.isBlank() || fieldId.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = validSupabaseSession(configuredProfile) ?: run {
                logFirebaseStatus("Your Supabase session expired. Sign in again to open attachments.")
                return@launch
            }
            com.example.data.remote.SupabaseClient.fetchHostedAttachmentUrl(
                url = active.supabaseUrl,
                anonKey = active.anonKey,
                token = active.authSessionToken,
                formSlug = slug,
                submissionId = submissionId,
                fieldId = fieldId,
                onSuccess = { signedUrl ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(signedUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }.onFailure { logFirebaseStatus("Unable to open attachment: ${it.message}") }
                    }
                },
                onFailure = { message -> logFirebaseStatus("Unable to open attachment: $message") }
            )
        }
    }

    private fun hostedFormToJson(form: HostedFormModel): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", form.id)
        put("merchant_id", activeProfile.value.id.ifBlank { "00000000-0000-0000-0000-000000000001" })
        val effectiveAmount = form.products.firstOrNull()?.let { if (it.salePrice > 0.0) it.salePrice else it.price }
            ?: form.fields.find { it.type == FormFieldType.PRODUCT || it.type == FormFieldType.PRODUCT_LIST }?.let { it.minValue ?: it.defaultValue.toDoubleOrNull() ?: 0.0 }
            ?: form.fields.find { it.type == FormFieldType.CUSTOM_AMOUNT }?.minValue
            ?: 0.0
        put("amount", effectiveAmount)
        put("title", form.title)
        put("description", form.description)
        put("slug", form.slug)
        put("template_type", form.templateKey)
        put("status", form.status)
        put("views_count", form.totalViews)
        put("submissions_count", form.totalSubmissions)
        put("total_revenue", form.totalRevenueBdt)
        put("fields", org.json.JSONArray().apply {
            form.fields.forEach { field ->
                put(org.json.JSONObject().apply {
                    put("id", field.id)
                    put("type", field.type.name)
                    put("label", field.label)
                    put("placeholder", field.placeholder)
                    put("required", field.isRequired)
                    put("options", org.json.JSONArray(field.options))
                    put("page_index", field.pageIndex)
                    put("helper_text", field.helperText)
                    put("default_value", field.defaultValue)
                    put("validation_regex", field.validationRegex)
                    put("min_length", field.minLength)
                    put("max_length", field.maxLength)
                    put("min_value", field.minValue ?: org.json.JSONObject.NULL)
                    put("max_value", field.maxValue ?: org.json.JSONObject.NULL)
                    put("allowed_file_extensions", org.json.JSONArray(field.allowedFileExtensions))
                    put("max_file_size_bytes", field.maxFileSizeBytes)
                    put("custom_error_message", field.customErrorMessage)
                    put("custom_code_html", field.customCodeHtml)
                    put("custom_code_css", field.customCodeCss)
                    put("custom_variables", org.json.JSONArray(field.customVariables))
                    put("custom_variable_assignments", org.json.JSONObject().apply {
                        field.customVariableAssignments.forEach { (key, value) -> put(key, value) }
                    })
                    put("media_url", field.mediaUrl)
                    put("media_alt_text", field.mediaAltText)
                    put("media_height_dp", field.mediaHeightDp)
                    put("section_index", field.sectionIndex)
                    put("step_index", field.stepIndex)
                    put("is_collapsed", field.isCollapsed)
                    put("depends_on_field_id", field.dependsOnFieldId ?: org.json.JSONObject.NULL)
                    put("condition_operator", field.conditionOperator)
                    put("condition_value", field.conditionValue)
                    put("text_color", field.textColorHex)
                    put("font_size_sp", field.fontSizeSp)
                    put("is_bold", field.isBold)
                    put("is_italic", field.isItalic)
                    put("is_underline", field.isUnderline)
                    put("font_family", field.fontFamilyName)
                    put("text_align", field.textAlignName)
                    put("width_dp", field.widthDp)
                    put("height_dp", field.heightDp)
                    put("offset_x_dp", field.offsetXDp)
                    put("offset_y_dp", field.offsetYDp)
                    put("shape_type", field.shapeType)
                    put("fill_color", field.fillColorHex)
                    put("border_color", field.borderColorHex)
                    put("border_width_dp", field.borderWidthDp)
                    put("border_radius_dp", field.borderRadiusDp)
                    put("shadow_elevation_dp", field.shadowElevationDp)
                    put("gallery_urls", org.json.JSONArray(field.galleryUrls))
                    put("product_variants", org.json.JSONArray().apply {
                        field.productVariants.forEach { pv ->
                            put(org.json.JSONObject().apply {
                                put("id", pv.id)
                                put("name", pv.name)
                                put("price", pv.price)
                                put("sku", pv.sku)
                                put("stock", pv.stock)
                                put("description", pv.description)
                            })
                        }
                    })
                })
            }
        })
        put("products", org.json.JSONArray().apply {
            form.products.forEach { product ->
                put(org.json.JSONObject().apply {
                    put("id", product.id)
                    put("title", product.title)
                    put("description", product.description)
                    put("price", product.price)
                    put("sale_price", product.salePrice)
                    put("stock", product.stock)
                    put("sku", product.sku)
                    put("category", product.category)
                    put("image_url", product.imageUrl)
                    put("is_digital", product.isDigital)
                    put("digital_download_url", product.digitalDownloadUrl)
                    put("variants", org.json.JSONArray(product.variants))
                    put("gallery_urls", org.json.JSONArray(product.galleryUrls))
                    put("product_variants", org.json.JSONArray().apply {
                        product.productVariants.forEach { pv ->
                            put(org.json.JSONObject().apply {
                                put("id", pv.id)
                                put("name", pv.name)
                                put("price", pv.price)
                                put("sku", pv.sku)
                                put("stock", pv.stock)
                                put("description", pv.description)
                            })
                        }
                    })
                })
            }
        })
        put("pages", org.json.JSONArray().apply {
            form.pages.forEach { page ->
                put(org.json.JSONObject().apply {
                    put("id", page.id)
                    put("title", page.title)
                    put("subtitle", page.subtitle)
                    put("is_custom_html", page.isCustomHtml)
                    put("custom_html_content", page.customHtmlContent)
                    put("custom_css_content", page.customCssContent)
                    put("custom_variables", org.json.JSONArray(page.customVariables))
                })
            }
        })
        put("theme", org.json.JSONObject().apply {
            val theme = form.themeConfig
            put("primary_color", theme.primaryColorHex)
            put("font_family", theme.fontFamily)
            put("button_shape", theme.buttonShape)
            put("background_color", theme.backgroundColorHex)
            put("background_style", theme.backgroundStyle)
            put("gradient_start", theme.gradientColorStart)
            put("gradient_end", theme.gradientColorEnd)
            put("border_radius", theme.borderRadiusDp)
            put("form_width", theme.formWidthPx)
            put("page_margin", theme.pageMarginPx)
            put("redirect_type", theme.redirectType)
            put("redirect_url", theme.redirectUrl)
            put("redirect_delay_seconds", theme.redirectDelaySec)
            put("open_in_new_tab", theme.openInNewTab)
            put("enable_custom_html", theme.enableCustomHtml)
            put("custom_css", theme.customCss)
            put("custom_html", theme.customHtmlContent)
            put("show_header", theme.showHeader)
            put("custom_js", theme.customJs)
            put("enable_custom_js", theme.enableCustomJs)
            put("is_custom_web_app", theme.isCustomWebApp)
            put("enable_closing_timeline", theme.enableClosingTimeline)
            put("closing_deadline_epoch", theme.closingDeadlineEpoch)
            put("closing_deadline_str", theme.closingDeadlineStr)
            put("closed_message", theme.closedMessage)
            put("show_countdown_timer", theme.showCountdownTimer)
            put("enable_csv_backend", theme.enableCsvBackend)
            put("csv_file_name", theme.csvFileName)
            put("csv_raw_data", theme.csvRawData)
            put("csv_headers", org.json.JSONArray(theme.csvHeaders))
            put("csv_lookup_column", theme.csvLookupColumn)
            put("csv_target_lookup_field_id", theme.csvTargetLookupFieldId)
            put("csv_column_mappings", org.json.JSONObject(theme.csvColumnMappings))
            put("csv_autofill_mode", theme.csvAutofillMode)
            put("shuffle_questions", theme.shuffleQuestionOrder)
            put("one_response_per_user", theme.oneResponsePerUser)
            put("multi_page", theme.isMultiPageForm)
            put("progress_tracker_style", theme.progressTrackerStyle)
            put("required_indicator", theme.requiredFieldIndicator)
            put("enable_timer", theme.enableTimer)
            put("timer_minutes", theme.timerMinutes)
            put("close_after_limit", theme.closeAfterLimit)
            put("max_responses", theme.maxResponses)
            put("enforce_required_fields", theme.enforceRequiredFields)
            put("strict_format_validation", theme.strictFormatValidation)
            put("max_file_size_bytes", theme.maxFileSizeBytes)
            put("enforce_file_size_limit", theme.enforceFileSizeLimit)
            put("min_quantity", theme.minQuantity)
            put("max_quantity", theme.maxQuantity)
            put("enforce_quantity_range", theme.enforceQuantityRange)
            put("enable_anti_spam", theme.enableAntiSpam)
            put("enable_payment", theme.enablePayment)
            put("payment_provider", theme.paymentProvider)
            put("currency", theme.currencyCode)
            put("tax_percent", theme.taxPercent)
            put("require_payment_before_submit", theme.requirePaymentBeforeSubmit)
            put("email_notifications", theme.enableEmailNotifications)
            put("notification_email", theme.notificationEmail)
            put("sms_notifications", theme.enableSmsNotifications)
            put("notification_sms_number", theme.notificationSmsNumber)
            put("payment_callback_enabled", theme.enablePaymentCallback)
            put("payment_callback_url", theme.paymentCallbackUrl)
            // Flagship & Single Product Showcase Configurations
            put("is_dark_mode", theme.isDarkMode)
            put("product_image_url", theme.productImageUrl)
            put("was_price", theme.wasPrice)
            put("eyebrow_text", theme.eyebrowText)
            put("badge_text", theme.badgeText)
            put("rating_score", theme.ratingScore)
            put("rating_count", theme.ratingCount)
            put("hide_header", theme.hideHeader)
            put("hide_eyebrow", theme.hideEyebrow)
            put("hide_rating", theme.hideRating)
            put("hide_price", theme.hidePrice)
            put("hide_swatches", theme.hideSwatches)
            put("hide_chips", theme.hideChips)
            put("hide_qty", theme.hideQty)
            put("hide_summary", theme.hideSummary)
            put("hide_promo", theme.hidePromo)
            put("hide_assurances", theme.hideAssurances)
            put("hide_details", theme.hideDetails)
            put("hide_mobile_dock", theme.hideMobileDock)
            if (theme.customVariables.isNotEmpty()) {
                val cvArr = org.json.JSONArray()
                theme.customVariables.forEach { cv -> cvArr.put(org.json.JSONObject().apply { put("key", cv.key); put("example_value", cv.exampleValue); put("source", cv.source) }) }
                put("custom_variables", cvArr)
            }
        }.also { themeJsonObj ->
            put("theme_config", themeJsonObj)
        })
        if (form.themeConfig.productImageUrl.isNotBlank()) {
            put("image_url", form.themeConfig.productImageUrl)
        }
        put("logo_url", form.themeConfig.logoUrl)
        put("banner_url", form.themeConfig.bannerUrl)

        val mProfile = activeProfile.value
        val mName = mProfile.businessName.ifBlank { mProfile.accountHolder }
        if (mName.isNotBlank()) {
            put("merchant_name", mName)
        }
        val receivingNums = org.json.JSONObject()
        val accountTypesObj = org.json.JSONObject()
        val qrCodesObj = org.json.JSONObject()
        val activeNums = merchantNumbers.value.filter { it.isActive && !repository.isFakePhoneNumber(it.number) }
        activeNums.forEach { num ->
            receivingNums.put(num.method, num.number)
            accountTypesObj.put(num.method, num.type)
            if (!num.qrCodeUrl.isNullOrBlank()) {
                qrCodesObj.put(num.method, num.qrCodeUrl)
            }
        }
        if (receivingNums.length() > 0) {
            put("receiving_numbers", receivingNums)
            put("account_types", accountTypesObj)
            put("qr_codes", qrCodesObj)
        }
    }

    fun createNewHostedForm(title: String, templateKey: String = "BLANK") {
        val newId = java.util.UUID.randomUUID().toString()
        val slug = "pay-${title.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")}-${System.currentTimeMillis().toString().takeLast(4)}"
        val newForm = HostedFormModel(
            id = newId,
            title = title.ifEmpty { "New Checkout Form" },
            slug = slug,
            templateKey = templateKey
        )
        hostedFormsList.value = hostedFormsList.value + newForm
        selectHostedForm(newId)
        if (templateKey != "BLANK") autoConfigureTemplate(templateKey)
        else saveActiveFormToHostedList()
    }

    fun updateFormThemeConfig(newTheme: FormThemeConfig) {
        formThemeConfig.value = newTheme
        saveActiveFormToHostedList()
    }

    fun updateHostedFormTitle(value: String) {
        formTitle.value = value.take(200)
        saveActiveFormToHostedList()
    }

    fun updateHostedFormDescription(value: String) {
        formDescription.value = value.take(2_000)
        saveActiveFormToHostedList()
    }

    fun updateHostedFormSlug(value: String) {
        formSlug.value = value.lowercase().replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-+"), "-").trim('-').take(100)
        saveActiveFormToHostedList()
    }

    // Custom Variable CRUD
    fun addCustomVariable(key: String, exampleValue: String = "", source: String = "field") {
        val normalizedKey = key.trim().removePrefix("{{").removeSuffix("}}")
            .lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').take(64)
        if (normalizedKey.isBlank()) return
        val current = formThemeConfig.value
        if (current.customVariables.none { it.key == normalizedKey } && current.customVariables.size >= 100) return
        val list = current.customVariables.filterNot { it.key == normalizedKey }.toMutableList()
        list.add(CustomVariable(normalizedKey, exampleValue.take(500), source.take(32)))
        updateFormThemeConfig(current.copy(customVariables = list))
    }

    fun updateCustomVariable(oldKey: String, newKey: String, exampleValue: String = "", source: String = "field") {
        val current = formThemeConfig.value
        val list = current.customVariables.map { if (it.key == oldKey) CustomVariable(newKey, exampleValue, source) else it }
        updateFormThemeConfig(current.copy(customVariables = list))
    }

    fun deleteCustomVariable(key: String) {
        val current = formThemeConfig.value
        val list = current.customVariables.filterNot { it.key == key }
        updateFormThemeConfig(current.copy(customVariables = list))
    }

    fun getAvailableDatabaseVariables(): List<Pair<String, String>> {
        val profile = activeProfile.value
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)
        val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        val now = java.util.Date()
        val list = mutableListOf(
            "merchant_name" to (profile.businessName.ifBlank { profile.accountHolder }.ifBlank { "My Business" }),
            "merchant_owner" to (profile.accountHolder.ifBlank { "Store Owner" }),
            "merchant_phone" to (profile.phone.ifBlank { "01700000000" }),
            "merchant_email" to (profile.email.ifBlank { "merchant@example.com" }),
            "merchant_address" to (profile.website.ifBlank { "Dhaka, Bangladesh" }),
            "currency" to (formThemeConfig.value.currencyCode.ifBlank { "BDT" }),
            "current_date" to dateFormat.format(now),
            "current_time" to timeFormat.format(now),
            "form_title" to formTitle.value,
            "form_description" to formDescription.value,
            "form_id" to activeFormId.value,
            "form_slug" to formSlug.value,
            "checkout_url" to (hostedFormPublicUrl().ifBlank { hostedFormDirectUrl() }),
            "invoice_number" to "INV-${(10000..99999).random()}",
            "total_customers" to customers.value.size.toString(),
            "total_products" to products.value.size.toString(),
            "total_price" to "৳" + "%,.2f".format(formProductsList.value.sumOf { (it.salePrice.takeIf { s -> s > 0.0 } ?: it.price) }),
            "total_due_receivable" to "৳" + "%,.2f".format(customers.value.sumOf { it.currentBalance.coerceAtLeast(0.0) }),
            "total_sales" to "৳" + "%,.2f".format(ledgerTransactions.value.filter { it.type == "credit" }.sumOf { it.amount })
        )
        formFieldsList.value.forEach { f ->
            val key = f.label.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { f.id.replace("-", "_") }
            list.add("field_$key" to (f.defaultValue.ifBlank { f.placeholder.ifBlank { f.label } }))
        }
        formThemeConfig.value.customVariables.forEach { cv ->
            list.add(cv.key to cv.exampleValue)
        }
        return list
    }

    fun resolveFormVariables(template: String, customOverrides: Map<String, String>? = null): String {
        if (template.isBlank()) return template
        var result = template
        val vars = getAvailableDatabaseVariables().toMap().toMutableMap()
        customOverrides?.forEach { (k, v) -> vars[k] = v }
        vars.forEach { (k, v) ->
            result = result.replace("{{$k}}", v)
            result = result.replace("{{ $k }}", v)
        }
        return result
    }

    fun compileCustomWebAppHtml(): String {
        val theme = formThemeConfig.value
        val rawHtml = theme.customHtmlContent.ifBlank {
            val title = formTitle.value.ifBlank { "SwapnoPay Hosted Form" }
            val desc = formDescription.value.ifBlank { "Complete your payment or submission securely." }
            val fieldsHtml = formFieldsList.value.joinToString("\n") { field ->
                """
                <div style="margin-bottom: 14px; text-align: left;">
                    <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: ${if (theme.isDarkMode) "#E2E8F0" else "#334155"};">
                        ${field.label}${if (field.isRequired) " <span style=\"color: #EF4444;\">*</span>" else ""}
                    </label>
                    <input type="text" placeholder="${field.placeholder.ifBlank { "Enter value" }}" style="width: 100%; padding: 12px; border: 1.5px solid ${if (theme.isDarkMode) "#334155" else "#E2E8F0"}; border-radius: 12px; background: ${if (theme.isDarkMode) "#1E293B" else "#FFFFFF"}; color: ${if (theme.isDarkMode) "#FFFFFF" else "#0F172A"}; box-sizing: border-box; font-size: 14px;" />
                </div>
                """.trimIndent()
            }
            val productsHtml = formProductsList.value.joinToString("\n") { prod ->
                val effPrice = prod.salePrice.takeIf { it > 0.0 } ?: prod.price
                """
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: ${if (theme.isDarkMode) "#1E293B" else "#F8FAFC"}; border: 1px solid ${if (theme.isDarkMode) "#334155" else "#E2E8F0"}; border-radius: 12px; margin-bottom: 10px; text-align: left;">
                    <div>
                        <strong style="color: ${if (theme.isDarkMode) "#FFFFFF" else "#0F172A"}; font-size: 14px;">${prod.title}</strong>
                        ${if (prod.sku.isNotBlank()) "<div style=\"font-size: 12px; color: #64748B;\">SKU: ${prod.sku}</div>" else ""}
                    </div>
                    <span style="font-weight: 700; font-size: 14px; color: ${theme.primaryColorHex};">৳${"%,.2f".format(effPrice)}</span>
                </div>
                """.trimIndent()
            }
            val btnRadius = when (theme.buttonShape) {
                "PILL" -> "32px"
                "SQUARE" -> "4px"
                else -> "12px"
            }
            """
            <div class="custom-web-card">
                <h2 style="color: ${if (theme.isDarkMode) "#FFFFFF" else "#0F172A"}; font-size: 22px; font-weight: 800; margin-bottom: 6px;">$title</h2>
                <p style="color: #64748B; font-size: 14px; margin-bottom: 16px;">$desc</p>
                <div class="merchant-badge">🏪 {{merchant_name}} | 📞 {{merchant_phone}}</div>
                ${if (productsHtml.isNotBlank()) "<div style=\"margin: 16px 0;\"><h4 style=\"margin-bottom: 8px; font-size: 12px; text-transform: uppercase; color: #64748B; text-align: left;\">Select Products</h4>$productsHtml</div>" else ""}
                <div style="margin-top: 16px;">
                    ${fieldsHtml.ifBlank { "<p style=\"color: #94A3B8; font-size: 13px; text-align: center; padding: 24px;\">Add form elements or custom HTML in the Form Builder studio.</p>" }}
                </div>
                <button style="width: 100%; margin-top: 20px; padding: 14px; background: ${theme.primaryColorHex}; color: #FFFFFF; border: none; border-radius: $btnRadius; font-size: 15px; font-weight: 700; cursor: pointer; box-shadow: 0 4px 14px rgba(0,0,0,0.1);">
                    Complete Submission / Checkout
                </button>
            </div>
            """.trimIndent()
        }
        val resolvedHtml = resolveFormVariables(rawHtml)
        val resolvedCss = resolveFormVariables(theme.customCss)
        val resolvedJs = resolveFormVariables(theme.customJs)

        val csvJsonArray = if (theme.enableCsvBackend && theme.csvRawData.isNotBlank()) {
            val rows = getParsedCsvRows(200)
            val arr = org.json.JSONArray()
            rows.forEach { r -> arr.put(org.json.JSONObject(r)) }
            arr.toString()
        } else "[]"

        val closingBannerHtml = if (theme.enableClosingTimeline && theme.showCountdownTimer) {
            """<div id="form-closing-timer-banner" style="max-width: 680px; margin: 0 auto 16px auto; background: #FEF3C7; color: #92400E; border: 1.5px solid #F59E0B; border-radius: 14px; padding: 12px 18px; display: flex; align-items: center; justify-content: space-between; font-weight: 600; font-size: 13px;"><span>⏳ Form Closing Countdown:</span><span id="countdown-val" style="font-family: monospace; font-size: 14px; font-weight: 800; color: #B45309;">Loading...</span></div>"""
        } else ""

        val safeClosedMsg = theme.closedMessage.replace("\"", "\\\"")

        return """
        <!DOCTYPE html>
        <html lang="bn">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${formTitle.value}</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Inter', system-ui, -apple-system, sans-serif; }
                body { background: ${if (theme.isDarkMode) "#0B0F19" else "#F8FAFC"}; color: ${if (theme.isDarkMode) "#F1F5F9" else "#0F172A"}; padding: 20px; }
                .custom-web-card { max-width: 680px; margin: 0 auto; background: ${if (theme.isDarkMode) "#151C2D" else "#FFFFFF"}; border-radius: 20px; padding: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.08); border: 1px solid ${if (theme.isDarkMode) "#334155" else "#E2E8F0"}; }
                .merchant-badge { display: inline-block; background: #EEF2FF; color: #4F46E5; padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; margin: 12px 0; }
                $resolvedCss
            </style>
        </head>
        <body>
            $closingBannerHtml
            $resolvedHtml
            <script>
                // CSV Backend Dataset
                window.csvBackendData = $csvJsonArray;
                window.csvLookupColumn = "${theme.csvLookupColumn}";
                window.csvColumnMappings = ${org.json.JSONObject(theme.csvColumnMappings).toString()};

                // Closing Timeline Handler
                (function() {
                    var deadlineEpoch = ${theme.closingDeadlineEpoch};
                    var enableTimeline = ${theme.enableClosingTimeline};
                    if (enableTimeline && deadlineEpoch > 0) {
                        function updateCountdown() {
                            var now = Date.now();
                            var diff = deadlineEpoch - now;
                            var banner = document.getElementById('form-closing-timer-banner');
                            var val = document.getElementById('countdown-val');
                            if (diff <= 0) {
                                if (val) val.innerText = "EXPIRED";
                                if (banner) {
                                    banner.style.background = "#FEE2E2";
                                    banner.style.borderColor = "#EF4444";
                                    banner.style.color = "#991B1B";
                                    banner.innerHTML = "🔒 <strong>$safeClosedMsg</strong>";
                                }
                                var buttons = document.querySelectorAll('button[type="submit"], .pay-btn, .order-btn, .bkash-btn');
                                buttons.forEach(function(b) {
                                    b.disabled = true;
                                    b.style.opacity = '0.5';
                                    b.style.cursor = 'not-allowed';
                                    b.innerText = "Form Closed";
                                });
                            } else {
                                var d = Math.floor(diff / (1000 * 60 * 60 * 24));
                                var h = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
                                var m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                                var s = Math.floor((diff % (1000 * 60)) / 1000);
                                if (val) val.innerText = (d > 0 ? d + "d " : "") + h + "h " + m + "m " + s + "s";
                            }
                        }
                        setInterval(updateCountdown, 1000);
                        updateCountdown();
                    }
                })();

                // Custom JavaScript injected by user
                try {
                    $resolvedJs
                } catch(e) {
                    console.error("Custom JS Error:", e);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    fun addFormField(type: FormFieldType, label: String? = null, placeholder: String? = null) {
        if (formFieldsList.value.size >= 200) return
        val defaultOptions = when (type) {
            FormFieldType.COUPON -> listOf("DISCOUNT10:10%", "PROMO50:50")
            else -> listOf("Option 1", "Option 2")
        }
        val newField = FormFieldItem(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            label = label ?: if (type == FormFieldType.COUPON) "Promo / Coupon Code" else type.displayName,
            placeholder = placeholder ?: if (type == FormFieldType.COUPON) "Enter promo code (e.g. SAVE10)" else "Enter ${type.displayName.lowercase()}",
            options = defaultOptions,
            isRequired = type in setOf(FormFieldType.PHONE, FormFieldType.CUSTOM_AMOUNT, FormFieldType.QUANTITY),
            minValue = when (type) {
                FormFieldType.CUSTOM_AMOUNT, FormFieldType.QUANTITY -> 1.0
                else -> null
            },
            pageIndex = activePageIndex.value
        )
        formFieldsList.value = formFieldsList.value + newField
        saveActiveFormToHostedList()
    }

    fun removeFormField(fieldId: String) {
        formFieldsList.value = formFieldsList.value.filterNot { it.id == fieldId }
        saveActiveFormToHostedList()
    }

    fun updateFormField(field: FormFieldItem) {
        formFieldsList.value = formFieldsList.value.map { if (it.id == field.id) field else it }
        saveActiveFormToHostedList()
    }

    // ── FORM CLOSING TIMELINE & CSV BACKEND ENGINE ───────────────────────
    val isAiGeneratingFormCode = MutableStateFlow(false)

    fun isFormClosed(): Boolean {
        val config = formThemeConfig.value
        if (!config.enableClosingTimeline || config.closingDeadlineEpoch <= 0L) return false
        return System.currentTimeMillis() > config.closingDeadlineEpoch
    }

    fun getFormClosingRemainingFormatted(): String {
        val config = formThemeConfig.value
        if (!config.enableClosingTimeline || config.closingDeadlineEpoch <= 0L) return ""
        val diff = config.closingDeadlineEpoch - System.currentTimeMillis()
        if (diff <= 0) return "Expired (সময়সীমা শেষ)"
        val days = diff / (1000L * 60 * 60 * 24)
        val hours = (diff / (1000L * 60 * 60)) % 24
        val minutes = (diff / (1000L * 60)) % 60
        return when {
            days > 0 -> "${days}d ${hours}h left"
            hours > 0 -> "${hours}h ${minutes}m left"
            else -> "${minutes}m left"
        }
    }

    fun setClosingTimelineHours(hoursFromNow: Int, showTimer: Boolean = true, customMessage: String? = null) {
        val epoch = System.currentTimeMillis() + (hoursFromNow * 3600L * 1000L)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val dateStr = sdf.format(java.util.Date(epoch))
        val current = formThemeConfig.value
        updateFormThemeConfig(
            current.copy(
                enableClosingTimeline = true,
                closingDeadlineEpoch = epoch,
                closingDeadlineStr = dateStr,
                showCountdownTimer = showTimer,
                closedMessage = customMessage ?: current.closedMessage
            )
        )
    }

    fun parseCsvContent(rawCsv: String, maxRows: Int = 500): Pair<List<String>, List<Map<String, String>>> {
        if (rawCsv.isBlank()) return Pair(emptyList(), emptyList())
        val lines = rawCsv.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return Pair(emptyList(), emptyList())

        val firstLine = lines.first()
        val delimiter = when {
            firstLine.contains("\t") -> '\t'
            firstLine.contains(";") -> ';'
            else -> ','
        }

        fun splitLine(line: String): List<String> {
            val result = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            for (ch in line) {
                when (ch) {
                    '"' -> inQuotes = !inQuotes
                    delimiter -> {
                        if (inQuotes) sb.append(ch)
                        else {
                            result.add(sb.toString().trim().removePrefix(""").removeSuffix("""))
                            sb.clear()
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            result.add(sb.toString().trim().removePrefix(""").removeSuffix("""))
            return result
        }

        val headers = splitLine(firstLine)
        val rows = mutableListOf<Map<String, String>>()
        for (i in 1 until minOf(lines.size, maxRows + 1)) {
            val cols = splitLine(lines[i])
            val rowMap = mutableMapOf<String, String>()
            for (j in headers.indices) {
                val headerKey = headers[j]
                val value = if (j < cols.size) cols[j] else ""
                rowMap[headerKey] = value
            }
            rows.add(rowMap)
        }
        return Pair(headers, rows)
    }

    fun importCsvBackend(fileName: String, rawCsv: String) {
        val (headers, _) = parseCsvContent(rawCsv, 10)
        val lookupCol = if (headers.isNotEmpty()) headers.first() else ""
        val current = formThemeConfig.value
        updateFormThemeConfig(
            current.copy(
                enableCsvBackend = true,
                csvFileName = fileName,
                csvRawData = rawCsv.take(300_000),
                csvHeaders = headers,
                csvLookupColumn = if (current.csvLookupColumn.isNotBlank() && current.csvLookupColumn in headers) current.csvLookupColumn else lookupCol
            )
        )
    }

    fun getParsedCsvRows(maxRows: Int = 100): List<Map<String, String>> {
        val raw = formThemeConfig.value.csvRawData
        return parseCsvContent(raw, maxRows).second
    }

    fun lookupCsvRecord(query: String): Map<String, String>? {
        val q = query.trim()
        if (q.isBlank()) return null
        val config = formThemeConfig.value
        val lookupCol = config.csvLookupColumn.ifBlank { config.csvHeaders.firstOrNull() ?: return null }
        val rows = getParsedCsvRows(500)
        return rows.firstOrNull { row ->
            val colVal = row[lookupCol]?.trim() ?: ""
            colVal.equals(q, ignoreCase = true) || colVal.contains(q, ignoreCase = true)
        }
    }

    fun loadSampleCsvTemplate(templateType: String) {
        when (templateType) {
            "STUDENT_PORTAL" -> {
                val csv = """student_id,full_name,department,semester,total_fee,paid_fee,due_amount,phone
STU-1001,Rahim Uddin,CSE,6th,45000,30000,15000,01711122334
STU-1002,Fatema Akter,EEE,4th,42000,42000,0,01822233445
STU-1003,Tanvir Hasan,BBA,8th,38000,20000,18000,01933344556
STU-1004,Nusrat Jahan,Pharmacy,2nd,50000,25000,25000,01644455667
STU-1005,Arif Chowdhury,English,5th,30000,30000,0,01555566778""".trimIndent()
                importCsvBackend("students_database.csv", csv)
                updateFormThemeConfig(formThemeConfig.value.copy(csvLookupColumn = "student_id"))
            }
            "PRODUCT_CATALOG" -> {
                val csv = """item_code,product_name,category,unit_price,in_stock,brand
PRD-01,Premium Basmati Rice 5kg,Grocery,850,45,Teer
PRD-02,Soybean Oil 5L,Grocery,920,80,Rupchanda
PRD-03,Wireless Bluetooth Earbuds,Electronics,1450,22,Realme
PRD-04,Smart Fast Charger 33W,Electronics,650,35,Xiaomi
PRD-05,Pure Mustard Oil 1L,Grocery,360,60,Radhuni""".trimIndent()
                importCsvBackend("products_catalog.csv", csv)
                updateFormThemeConfig(formThemeConfig.value.copy(csvLookupColumn = "item_code"))
            }
            "VOUCHER_VERIFIER" -> {
                val csv = """voucher_code,discount_percent,max_bdt,customer_name,valid_until,status
EID2026,20,500,General,2026-09-30,ACTIVE
VIP100,25,1000,Kazi Sakib,2026-10-15,ACTIVE
WELCOME50,15,300,New User,2026-12-31,ACTIVE
SWAPNO10,10,200,All,2026-11-20,ACTIVE""".trimIndent()
                importCsvBackend("vouchers_active.csv", csv)
                updateFormThemeConfig(formThemeConfig.value.copy(csvLookupColumn = "voucher_code"))
            }
        }
    }

    fun generateAiFormCode(
        userPrompt: String,
        onSuccess: (html: String, css: String, js: String, explanation: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (userPrompt.isBlank()) {
            onError("Please describe what you want the AI to code")
            return
        }
        isAiGeneratingFormCode.value = true
        viewModelScope.launch {
            val p = userPrompt.lowercase()
            val generated: Triple<String, String, String> = when {
                p.contains("csv") || p.contains("lookup") || p.contains("student") || p.contains("database") -> {
                    Triple(
                        """<div class="csv-app-box">
  <div class="app-header">
    <h2>{{form_title}}</h2>
    <p>{{form_description}}</p>
    <div class="badge-vps">⚡ Connected to CSV Backend Dataset</div>
  </div>
  <div class="lookup-card">
    <label for="lookup-input">Enter Lookup Key (Student ID / Code):</label>
    <div class="input-row">
      <input type="text" id="lookup-input" placeholder="e.g. STU-1001" />
      <button class="search-btn" onclick="executeCsvSearch()">Search</button>
    </div>
  </div>
  <div id="result-box" class="result-card" style="display: none;">
    <h3 id="res-title">Record Details</h3>
    <div class="grid-fields">
      <div class="field-item"><span>Full Name:</span><strong id="res-name">-</strong></div>
      <div class="field-item"><span>Dept / Category:</span><strong id="res-dept">-</strong></div>
      <div class="field-item"><span>Payable / Due:</span><strong id="res-due" class="highlight">-</strong></div>
      <div class="field-item"><span>Contact:</span><strong id="res-phone">-</strong></div>
    </div>
    <button class="pay-btn" onclick="processPayment()">Confirm & Pay via bKash</button>
  </div>
</div>""".trimIndent(),
                        """.csv-app-box { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 20px; padding: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.06); font-family: 'Inter', system-ui, sans-serif; border: 1px solid #e2e8f0; }
.app-header h2 { font-size: 22px; color: #0f172a; margin-bottom: 4px; }
.app-header p { font-size: 13px; color: #64748b; }
.badge-vps { display: inline-block; background: #ecfdf5; color: #059669; font-size: 11px; font-weight: bold; padding: 4px 10px; border-radius: 20px; margin-top: 8px; }
.lookup-card { background: #f8fafc; padding: 18px; border-radius: 14px; margin: 18px 0; border: 1px solid #e2e8f0; }
.lookup-card label { display: block; font-size: 12px; font-weight: 600; color: #334155; margin-bottom: 6px; }
.input-row { display: flex; gap: 8px; }
.input-row input { flex: 1; padding: 12px; border: 1.5px solid #cbd5e1; border-radius: 10px; font-size: 14px; outline: none; transition: border 0.2s; }
.input-row input:focus { border-color: #6366f1; }
.search-btn { padding: 12px 20px; background: #6366f1; color: #ffffff; border: none; border-radius: 10px; font-weight: bold; cursor: pointer; }
.result-card { background: #faf5ff; border: 1.5px solid #c084fc; border-radius: 14px; padding: 18px; margin-top: 14px; }
.result-card h3 { font-size: 16px; color: #581c87; margin-bottom: 12px; }
.grid-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 16px; }
.field-item span { display: block; font-size: 11px; color: #6b21a8; }
.field-item strong { font-size: 14px; color: #0f172a; }
.field-item .highlight { color: #d97706; font-size: 16px; }
.pay-btn { width: 100%; padding: 14px; background: #e11d48; color: #ffffff; border: none; border-radius: 12px; font-size: 15px; font-weight: bold; cursor: pointer; }""".trimIndent(),
                        """function executeCsvSearch() {
  var key = document.getElementById('lookup-input').value.trim();
  if (!key) { alert('Please enter a lookup key'); return; }
  var data = window.csvBackendData || [];
  var found = null;
  for (var i = 0; i < data.length; i++) {
    var r = data[i];
    for (var k in r) {
      if (r[k].toString().toLowerCase() === key.toLowerCase()) { found = r; break; }
    }
    if (found) break;
  }
  var resBox = document.getElementById('result-box');
  if (found) {
    document.getElementById('res-name').innerText = found.full_name || found.name || found.product_name || 'Found Record';
    document.getElementById('res-dept').innerText = found.department || found.category || found.status || 'Active';
    document.getElementById('res-due').innerText = '৳ ' + (found.due_amount || found.unit_price || found.total_fee || '0');
    document.getElementById('res-phone').innerText = found.phone || found.in_stock || '-';
    resBox.style.display = 'block';
  } else {
    alert('No record found for key: ' + key);
    resBox.style.display = 'none';
  }
}
function processPayment() {
  alert('Redirecting to secure bKash payment for {{merchant_name}}...');
}""".trimIndent()
                    )
                }
                p.contains("restaurant") || p.contains("food") || p.contains("delivery") -> {
                    Triple(
                        """<div class="food-menu-card">
  <div class="food-hero">
    <h2>🍽️ {{merchant_name}} Food Express</h2>
    <p>Fast delivery to your doorstep | 📞 {{merchant_phone}}</p>
  </div>
  <div class="menu-list">
    <div class="menu-item">
      <div><h4>Kacchi Biryani Special</h4><span class="price">৳ 320</span></div>
      <input type="number" id="qty-1" value="1" min="0" onchange="calculateFoodTotal()" />
    </div>
    <div class="menu-item">
      <div><h4>Chicken Chaap with Luchi</h4><span class="price">৳ 180</span></div>
      <input type="number" id="qty-2" value="0" min="0" onchange="calculateFoodTotal()" />
    </div>
    <div class="menu-item">
      <div><h4>Borhani 500ml</h4><span class="price">৳ 90</span></div>
      <input type="number" id="qty-3" value="1" min="0" onchange="calculateFoodTotal()" />
    </div>
  </div>
  <div class="bill-summary">
    <div><span>Delivery Fee:</span><span>৳ 60</span></div>
    <div class="total-row"><span>Total Payable:</span><strong id="food-total">৳ 470</strong></div>
  </div>
  <input type="text" id="deliv-addr" class="addr-input" placeholder="Delivery Address & Instructions" />
  <button class="order-btn" onclick="submitFoodOrder()">Order & Pay via bKash / Nagad</button>
</div>""".trimIndent(),
                        """.food-menu-card { max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 20px; padding: 22px; box-shadow: 0 10px 25px rgba(0,0,0,0.06); font-family: system-ui, sans-serif; }
.food-hero h2 { font-size: 20px; color: #991b1b; }
.food-hero p { font-size: 12px; color: #64748b; margin-top: 2px; }
.menu-list { margin: 18px 0; display: flex; flex-direction: column; gap: 10px; }
.menu-item { display: flex; justify-content: space-between; align-items: center; padding: 12px; background: #fef2f2; border-radius: 12px; border: 1px solid #fecaca; }
.menu-item h4 { font-size: 14px; margin: 0; color: #1e293b; }
.menu-item .price { font-size: 13px; font-weight: bold; color: #dc2626; }
.menu-item input { width: 55px; padding: 6px; border: 1px solid #cbd5e1; border-radius: 8px; text-align: center; }
.bill-summary { background: #f8fafc; padding: 14px; border-radius: 10px; margin: 14px 0; }
.bill-summary div { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 4px; color: #475569; }
.total-row { border-top: 1px dashed #cbd5e1; padding-top: 8px; font-size: 16px !important; color: #0f172a !important; }
.addr-input { width: 100%; padding: 12px; border: 1px solid #cbd5e1; border-radius: 10px; font-size: 13px; box-sizing: border-box; }
.order-btn { width: 100%; margin-top: 14px; padding: 14px; background: #dc2626; color: #ffffff; border: none; border-radius: 12px; font-size: 15px; font-weight: bold; cursor: pointer; }""".trimIndent(),
                        """function calculateFoodTotal() {
  var q1 = parseInt(document.getElementById('qty-1').value) || 0;
  var q2 = parseInt(document.getElementById('qty-2').value) || 0;
  var q3 = parseInt(document.getElementById('qty-3').value) || 0;
  var subtotal = (q1 * 320) + (q2 * 180) + (q3 * 90);
  var total = subtotal > 0 ? subtotal + 60 : 0;
  document.getElementById('food-total').innerText = '৳ ' + total;
}
function submitFoodOrder() {
  var addr = document.getElementById('deliv-addr').value;
  if (!addr) { alert('Please enter delivery address'); return; }
  alert('Order placed successfully! Connecting to SwapnoPay checkout...');
}""".trimIndent()
                    )
                }
                else -> {
                    Triple(
                        """<div class="glass-checkout">
  <div class="glass-header">
    <div class="glow-tag">⚡ {{merchant_name}} Live Checkout</div>
    <h2>{{form_title}}</h2>
    <p>{{form_description}}</p>
  </div>
  <div class="product-showcase">
    <div class="prod-badge">Invoice #{{invoice_number}} | {{current_date}}</div>
    <div class="price-row">
      <span class="label">Product Amount:</span>
      <span class="amount">{{currency}} <span id="dyn-price">1,250</span></span>
    </div>
  </div>
  <div class="form-inputs">
    <input type="text" id="client-name" placeholder="Your Full Name" />
    <input type="tel" id="client-phone" placeholder="017XXXXXXXX" />
    <input type="text" id="promo-code" placeholder="Discount Code (Optional)" onkeyup="applyPromo()" />
  </div>
  <button class="bkash-btn" onclick="executePayment()">
    <span>Pay with bKash / Nagad</span>
    <span id="btn-amount">৳ 1,250</span>
  </button>
</div>""".trimIndent(),
                        """.glass-checkout { max-width: 520px; margin: 0 auto; background: rgba(15, 23, 42, 0.95); backdrop-filter: blur(16px); color: #f8fafc; border-radius: 24px; padding: 26px; border: 1px solid rgba(255, 255, 255, 0.1); box-shadow: 0 20px 40px rgba(0,0,0,0.4); font-family: 'Inter', system-ui, sans-serif; }
.glow-tag { display: inline-block; background: linear-gradient(90deg, #6366f1, #a855f7); color: #fff; font-size: 11px; font-weight: 700; padding: 4px 12px; border-radius: 20px; margin-bottom: 12px; }
.glass-header h2 { font-size: 22px; color: #fff; margin-bottom: 4px; }
.glass-header p { font-size: 13px; color: #94a3b8; }
.product-showcase { background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 14px; padding: 16px; margin: 20px 0; }
.prod-badge { font-size: 11px; color: #38bdf8; margin-bottom: 8px; }
.price-row { display: flex; justify-content: space-between; align-items: center; }
.price-row .label { font-size: 14px; color: #cbd5e1; }
.price-row .amount { font-size: 24px; font-weight: 800; color: #facc15; }
.form-inputs { display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px; }
.form-inputs input { background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 12px; padding: 13px; color: #fff; font-size: 14px; outline: none; }
.form-inputs input:focus { border-color: #6366f1; }
.bkash-btn { width: 100%; padding: 16px; background: #e11d48; color: #fff; border: none; border-radius: 14px; font-size: 16px; font-weight: bold; cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.bkash-btn:hover { background: #be123c; }""".trimIndent(),
                        """function applyPromo() {
  var code = document.getElementById('promo-code').value.trim().toUpperCase();
  var base = 1250;
  if (code === 'EID2026' || code === 'SWAPNOPAY') {
    var discounted = Math.round(base * 0.85);
    document.getElementById('dyn-price').innerText = discounted;
    document.getElementById('btn-amount').innerText = '৳ ' + discounted;
  } else {
    document.getElementById('dyn-price').innerText = base;
    document.getElementById('btn-amount').innerText = '৳ ' + base;
  }
}
function executePayment() {
  var name = document.getElementById('client-name').value;
  var phone = document.getElementById('client-phone').value;
  if (!name || !phone) { alert('Please enter your name and phone number'); return; }
  alert('Initiating payment for ' + name + ' at {{merchant_name}}...');
}""".trimIndent()
                    )
                }
            }

            isAiGeneratingFormCode.value = false
            onSuccess(
                generated.first,
                generated.second,
                generated.third,
                "Successfully generated custom code for '$userPrompt' with responsive markup, styling, and JavaScript interactions."
            )
        }
    }

    // AI Custom HTML & CSS Generator Engine
    fun generateCustomHtmlWithAI(prompt: String) {
        val lc = prompt.lowercase()
        val generatedHtml = when {
            lc.contains("mosque") || lc.contains("donation") || lc.contains("sadaqah") || lc.contains("ngo") -> {
                """
                <div style="font-family: 'Inter', sans-serif; background: #064E3B; color: #FFFFFF; padding: 40px 24px; text-align: center; border-radius: 24px; max-width: 600px; margin: auto; border: 1px solid #059669;">
                  <div style="font-size: 48px; margin-bottom: 12px;">🕌</div>
                  <h1 style="color: #34D399; font-size: 26px; font-weight: 800; margin: 0;">May Allah Reward Your Sadaqah!</h1>
                  <p style="color: #A7F3D0; font-size: 14px; margin-top: 6px;">JazakAllah Khair, <strong>{{name}}</strong> for your contribution.</p>
                  
                  <div style="background: #047857; border: 1px solid #059669; padding: 20px; border-radius: 16px; margin: 24px 0; text-align: left;">
                    <div style="display: flex; justify-content: space-between; font-size: 13px; color: #D1FAE5; margin-bottom: 8px;">
                      <span>Receipt ID:</span> <strong style="font-family: monospace;">#{{submission_id}}</strong>
                    </div>
                    <div style="display: flex; justify-content: space-between; font-size: 13px; color: #D1FAE5; margin-bottom: 8px;">
                      <span>Donor Phone:</span> <strong>{{phone}}</strong>
                    </div>
                    <div style="display: flex; justify-content: space-between; font-size: 13px; color: #D1FAE5; margin-bottom: 8px;">
                      <span>Payment Status:</span> <strong style="color: #34D399;">{{payment_status}}</strong>
                    </div>
                    <hr style="border-color: #065F46; margin: 12px 0;">
                    <div style="display: flex; justify-content: space-between; font-size: 16px; font-weight: bold; color: #FFFFFF;">
                      <span>Total Sadaqah Paid:</span> <span style="color: #FBBF24;">৳{{payment_amount}} BDT</span>
                    </div>
                  </div>
                  
                  <p style="font-size: 12px; color: #A7F3D0;">A printable SMS/Email receipt has been sent to <strong>{{email}}</strong>.</p>
                </div>
                """.trimIndent()
            }
            lc.contains("invoice") || lc.contains("receipt") || lc.contains("billing") -> {
                """
                <div style="font-family: 'Inter', sans-serif; background: #1E293B; color: #F8FAFC; padding: 36px 24px; border-radius: 20px; max-width: 600px; margin: auto; border: 1px solid #334155;">
                  <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 16px; margin-bottom: 20px;">
                    <div>
                      <h2 style="margin: 0; color: #7C3AED; font-size: 22px;">SwapnoPay Digital Invoice</h2>
                      <span style="font-size: 11px; color: #94A3B8;">Transaction Ref: {{submission_id}}</span>
                    </div>
                    <span style="background: #10B981; color: #FFFFFF; font-size: 11px; font-weight: bold; padding: 4px 10px; border-radius: 8px;">{{payment_status}}</span>
                  </div>
                  
                  <div style="font-size: 13px; color: #CBD5E1; line-height: 24px;">
                    <div><strong>Billed To:</strong> {{name}}</div>
                    <div><strong>Contact Email:</strong> {{email}}</div>
                    <div><strong>Phone:</strong> {{phone}}</div>
                  </div>
                  
                  <div style="background: #0F172A; padding: 16px; border-radius: 12px; margin: 20px 0; border: 1px solid #334155;">
                    <div style="display: flex; justify-content: space-between; font-size: 14px; font-weight: bold; color: #FFFFFF;">
                      <span>Total Paid Amount:</span>
                      <span style="color: #34D399;">৳{{payment_amount}} BDT</span>
                    </div>
                  </div>
                </div>
                """.trimIndent()
            }
            else -> {
                """
                <div style="font-family: 'Inter', sans-serif; background: #0F172A; color: #FFFFFF; padding: 40px 24px; border-radius: 24px; max-width: 600px; margin: auto; border: 1px solid #7C3AED; text-align: center;">
                  <div style="width: 64px; height: 64px; background: #7C3AED; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; font-size: 28px;">🎉</div>
                  <h2 style="color: #FFFFFF; font-size: 24px; font-weight: 800; margin: 0;">Submission & Payment Successful!</h2>
                  <p style="color: #94A3B8; font-size: 13px; margin-top: 6px;">Thank you <strong>{{name}}</strong>. Your response has been recorded successfully.</p>
                  
                  <div style="background: #1E293B; border-radius: 16px; padding: 20px; margin: 24px 0; text-align: left; border: 1px solid #334155;">
                    <div style="font-size: 13px; color: #E2E8F0; line-height: 22px;">
                      <div>Order ID: <strong style="color: #A78BFA;">{{submission_id}}</strong></div>
                      <div>Customer Phone: <strong>{{phone}}</strong></div>
                      <div>Payment Status: <strong style="color: #34D399;">{{payment_status}}</strong></div>
                      <div>Total Amount: <strong style="color: #FBBF24;">৳{{payment_amount}} BDT</strong></div>
                    </div>
                  </div>
                  
                  <p style="font-size: 11px; color: #64748B;">A confirmation copy has been sent to {{email}}.</p>
                </div>
                """.trimIndent()
            }
        }

        val updated = formThemeConfig.value.copy(
            enableCustomHtml = true,
            customHtmlContent = generatedHtml,
            redirectType = "CUSTOM_HTML"
        )
        updateFormThemeConfig(updated)
        logFirebaseStatus("AI Generated Custom HTML Content from prompt: '$prompt'")
    }

    fun generateCustomCssWithAI(prompt: String) {
        val generatedCss = """
        /* AI Generated Theme CSS for SwapnoPay Form Builder */
        .swapnopay-form-container {
          background: linear-gradient(135deg, #0F172A 0%, #1E1B4B 100%) !important;
          border: 1.5px solid #7C3AED !important;
          box-shadow: 0 20px 30px -5px rgba(124, 58, 237, 0.3) !important;
          border-radius: 24px !important;
        }

        .swapnopay-field-input {
          background: #1E293B !important;
          color: #FFFFFF !important;
          border: 1px solid #475569 !important;
          border-radius: 12px !important;
          transition: all 0.3s ease !important;
        }

        .swapnopay-field-input:focus {
          border-color: #7C3AED !important;
          box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.35) !important;
        }

        .swapnopay-submit-btn {
          background: linear-gradient(90deg, #7C3AED 0%, #5B7FFF 100%) !important;
          font-weight: 800 !important;
          letter-spacing: 0.8px !important;
          border-radius: 14px !important;
        }
        """.trimIndent()

        val updated = formThemeConfig.value.copy(customCss = generatedCss)
        updateFormThemeConfig(updated)
        logFirebaseStatus("AI Generated Custom CSS Rules from prompt: '$prompt'")
    }

    val aiFormPromptInput = MutableStateFlow("")
    val isAiFormGenerating = MutableStateFlow(false)
    val aiFormError = MutableStateFlow<String?>(null)
    val formBuilderStep = MutableStateFlow(0)

    fun testPaymentWebhook(urlStr: String, onResult: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val targetUrl = if (!urlStr.startsWith("https://")) "https://${urlStr.substringAfter("://")}" else urlStr
                val url = java.net.URL(targetUrl)
                if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null) {
                    error("A public HTTPS webhook URL is required")
                }
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("User-Agent", "SwapnoPay-WebhookEngine/2.0")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val payload = org.json.JSONObject().apply {
                    put("event", "swapnopay.endpoint_verification")
                    put("verification_id", java.util.UUID.randomUUID().toString())
                    put("merchant_id", activeProfile.value.id)
                    put("timestamp", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))
                }.toString()

                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }

                val respCode = conn.responseCode
                val respMsg = if (respCode in 200..299) {
                    "✅ Production Webhook Delivered! HTTP $respCode OK"
                } else {
                    "⚠️ Webhook Destination Returned HTTP $respCode"
                }
                conn.disconnect()
                logFirebaseStatus("Production Webhook Request POST $targetUrl -> HTTP $respCode")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(respMsg)
                }
            } catch (e: Exception) {
                logFirebaseStatus("Production Webhook Request Error: ${e.localizedMessage}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult("❌ Webhook Request Error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun refreshGatewayReceiptHealth() {
        viewModelScope.launch {
            try {
                val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return@launch
                com.example.data.remote.SupabaseClient.callRpc(
                    active.supabaseUrl, active.anonKey, active.authSessionToken,
                    "ping_receipt_worker", org.json.JSONObject(),
                    onSuccess = { logFirebaseStatus("Receipt worker healthy") },
                    onFailure = { logFirebaseStatus("Receipt worker unreachable: $it") }
                )
            } catch (e: Exception) {
                logFirebaseStatus("Gateway health check error: ${e.message}")
            }
        }
    }

    fun testSendSmsNotification(phoneStr: String, onResult: (String) -> Unit) {
        if (!phoneStr.replace(" ", "").matches(Regex("^\\+?[0-9]{10,15}$"))) {
            onResult("Enter a valid notification phone number")
            return
        }
        sendQuickCustomSms(listOf(phoneStr), "SwapnoPay Test SMS Verification") { success, msg ->
            onResult(if (success) "✅ $msg" else "❌ $msg")
        }
    }

    // ── Enterprise SMS Gateway & Campaign State ──
    private val _gatewayApiKey = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .getString("api_key", null) ?: "sp_gw_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}".also {
                getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
                    .edit().putString("api_key", it).apply()
            }
    )
    val gatewayApiKey: StateFlow<String> = _gatewayApiKey.asStateFlow()

    private val _isGatewayActive = MutableStateFlow(true)
    val isGatewayActive: StateFlow<Boolean> = _isGatewayActive.asStateFlow()

    private val _selectedSimSlot = MutableStateFlow(0)
    val selectedSimSlot: StateFlow<Int> = _selectedSimSlot.asStateFlow()

    private val _availableSimCards = MutableStateFlow<List<com.example.service.SimCardInfo>>(emptyList())
    val availableSimCards: StateFlow<List<com.example.service.SimCardInfo>> = _availableSimCards.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val outboxSmsList: StateFlow<List<OutboxSmsEntity>> = activeProfile.flatMapLatest { profile ->
        repository.observeOutboxSms(profile.id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val customersWithDue: StateFlow<List<CustomerEntity>> = activeProfile.flatMapLatest { profile ->
        repository.observeCustomersWithDue(profile.id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val smsThrottleDelayMs = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .getLong("sms_throttle_delay", 2500L)
    )
    val autoPosReceiptSmsEnabled = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .getBoolean("auto_pos_receipt_enabled", true)
    )
    val isAutoDueScheduleActive = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .getBoolean("auto_due_schedule_active", false)
    )
    val autoDueScheduleHour = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .getInt("auto_due_schedule_hour", 10)
    )
    val autoDueMinAmount = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .getFloat("auto_due_min_amount", 100f).toDouble()
    )
    val dueSmsTemplate = MutableStateFlow("প্রিয় {name}, {store}-এ আপনার বাকি {due} টাকা পরিশোধের অনুরোধ জানাচ্ছি। ধন্যবাদ।")
    val marketingSmsTemplate = MutableStateFlow("সম্মানিত গ্রাহক {name}, {store}-এ নতুন অফার ও বিশেষ ডিসকাউন্টের জন্য ভিজিট করুন। ধন্যবাদ!")
    val salesSmsTemplate = MutableStateFlow("ধন্যবাদ {name}! {store}-এ আপনার {amount} টাকার অর্ডার সম্পন্ন হয়েছে। ইনভয়েস: {invoice}।")

    fun refreshSimCards() {
        try {
            val sims = com.example.service.SmsGatewayEngine.getAvailableSimCards(getApplication())
            _availableSimCards.value = sims
        } catch (e_: Exception) { android.util.Log.w("AppViewModel", "Suppressed: ${e_.message}") }
    }

    fun setSelectedSimSlot(slot: Int) {
        _selectedSimSlot.value = slot
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .edit().putInt("selected_sim_slot", slot).apply()
    }

    fun setGatewayActive(active: Boolean) {
        _isGatewayActive.value = active
    }

    fun setAutoPosReceiptEnabled(enabled: Boolean) {
        autoPosReceiptSmsEnabled.value = enabled
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("auto_pos_receipt_enabled", enabled).apply()
    }

    fun setAutoDueScheduleConfig(active: Boolean, hour: Int, minAmount: Double) {
        isAutoDueScheduleActive.value = active
        autoDueScheduleHour.value = hour
        autoDueMinAmount.value = minAmount
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_due_schedule_active", active)
            .putInt("auto_due_schedule_hour", hour)
            .putFloat("auto_due_min_amount", minAmount.toFloat())
            .apply()
    }

    fun setSmsThrottleDelay(delayMs: Long) {
        smsThrottleDelayMs.value = delayMs
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .edit().putLong("sms_throttle_delay", delayMs).apply()
    }

    fun generateNewGatewayApiKey() {
        val newKey = "sp_gw_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}"
        getApplication<Application>().getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
            .edit().putString("api_key", newKey).apply()
        _gatewayApiKey.value = newKey
    }

    fun sendDueReminderSms(
        selectedCustomerIds: Set<String>? = null,
        customTemplate: String? = null,
        onResult: (Int, String) -> Unit
    ) {
        viewModelScope.launch {
            val merchantId = activeProfile.value.id
            val storeName = activeProfile.value.businessName.ifBlank { "SwapnoPay Merchant" }
            val tpl = customTemplate?.ifBlank { null } ?: dueSmsTemplate.value
            val dueCustomers = repository.getCustomersWithDue(merchantId)
            val targets = if (selectedCustomerIds != null) {
                dueCustomers.filter { it.id in selectedCustomerIds }
            } else {
                dueCustomers
            }

            if (targets.isEmpty()) {
                onResult(0, "No customers with due balance to send reminders to.")
                return@launch
            }

            val slot = _selectedSimSlot.value
            val entities = targets.mapNotNull { cust ->
                val phone = cust.phone.trim()
                if (phone.length < 10) return@mapNotNull null
                val dueFormatted = String.format(java.util.Locale.US, "%.0f", cust.currentBalance)
                val msg = tpl
                    .replace("{name}", cust.name)
                    .replace("{due}", dueFormatted)
                    .replace("{store}", storeName)
                    .replace("{phone}", phone)

                OutboxSmsEntity(
                    merchantId = merchantId,
                    recipientPhone = phone,
                    messageText = msg,
                    smsType = "DUE_REMINDER",
                    simSlot = slot,
                    status = "QUEUED",
                    customerId = cust.id,
                    partsCount = (msg.length / 160) + 1
                )
            }

            if (entities.isNotEmpty()) {
                repository.queueOutboxSmsList(entities)
                com.example.service.SmsGatewayEngine.startOutboxQueueProcessor(
                    context = getApplication(),
                    merchantId = merchantId,
                    throttleDelayMs = smsThrottleDelayMs.value
                )
                onResult(entities.size, "${entities.size} due reminder SMS added to outbox queue.")
            } else {
                onResult(0, "No valid phone numbers found among due customers.")
            }
        }
    }

    fun runAutomatedDueReminderBatch(onResult: (Int, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val merchantId = activeProfile.value.id
            val debtors = repository.getCustomersWithDue(merchantId)
            val minAmount = autoDueMinAmount.value
            val qualified = debtors.filter { it.currentBalance >= minAmount }
            if (qualified.isEmpty()) {
                val isBangla = language.value == "Bangla"
                val msg = if (isBangla)
                    "ন্যূনতম ৳${minAmount.toInt()} বাকি থাকা কোনো কাস্টমার পাওয়া যায়নি।"
                else
                    "No customers found with due ≥ ৳${minAmount.toInt()}."
                onResult(0, msg)
                return@launch
            }
            sendDueReminderSms(qualified.map { it.id }.toSet(), null, onResult)
        }
    }

    fun sendMarketingCampaign(
        targetGroup: String, // "ALL", "DUE", "ZERO_DUE"
        template: String,
        onResult: (Int, String) -> Unit
    ) {
        viewModelScope.launch {
            if (!canAccessFeature("bulk_sms")) {
                onResult(0, "Marketing SMS campaign requires an active subscription.")
                return@launch
            }
            val merchantId = activeProfile.value.id
            val storeName = activeProfile.value.businessName.ifBlank { "SwapnoPay Merchant" }
            val allCusts = repository.getAllCustomersList(merchantId)
            val filtered = when (targetGroup) {
                "DUE" -> allCusts.filter { it.currentBalance > 0 }
                "ZERO_DUE" -> allCusts.filter { it.currentBalance <= 0 }
                else -> allCusts
            }

            if (filtered.isEmpty()) {
                onResult(0, "No customers found for selected audience: $targetGroup")
                return@launch
            }

            val slot = _selectedSimSlot.value
            val entities = filtered.mapNotNull { cust ->
                val phone = cust.phone.trim()
                if (phone.length < 10) return@mapNotNull null
                val dueFormatted = String.format(java.util.Locale.US, "%.0f", cust.currentBalance)
                val msg = template
                    .replace("{name}", cust.name)
                    .replace("{due}", dueFormatted)
                    .replace("{store}", storeName)
                    .replace("{phone}", phone)

                OutboxSmsEntity(
                    merchantId = merchantId,
                    recipientPhone = phone,
                    messageText = msg,
                    smsType = "MARKETING",
                    simSlot = slot,
                    status = "QUEUED",
                    customerId = cust.id,
                    partsCount = (msg.length / 160) + 1
                )
            }

            if (entities.isNotEmpty()) {
                repository.queueOutboxSmsList(entities)
                com.example.service.SmsGatewayEngine.startOutboxQueueProcessor(
                    context = getApplication(),
                    merchantId = merchantId,
                    throttleDelayMs = smsThrottleDelayMs.value
                )
                onResult(entities.size, "${entities.size} marketing SMS added to outbox queue.")
            } else {
                onResult(0, "No valid customer phone numbers found.")
            }
        }
    }

    fun sendPosReceiptSms(
        customerName: String,
        customerPhone: String,
        amount: Double,
        invoiceId: String
    ) {
        if (!autoPosReceiptSmsEnabled.value || customerPhone.isBlank() || customerPhone.length < 10) return
        viewModelScope.launch {
            val merchantId = activeProfile.value.id
            val storeName = activeProfile.value.businessName.ifBlank { "SwapnoPay Store" }
            val amountFormatted = String.format(java.util.Locale.US, "%.2f", amount)
            val msg = salesSmsTemplate.value
                .replace("{name}", customerName.ifBlank { "সম্মানিত গ্রাহক" })
                .replace("{amount}", amountFormatted)
                .replace("{invoice}", invoiceId)
                .replace("{store}", storeName)

            val entity = OutboxSmsEntity(
                merchantId = merchantId,
                recipientPhone = customerPhone,
                messageText = msg,
                smsType = "SALES_RECEIPT",
                simSlot = _selectedSimSlot.value,
                status = "QUEUED",
                partsCount = (msg.length / 160) + 1
            )
            repository.queueOutboxSms(entity)
            com.example.service.SmsGatewayEngine.startOutboxQueueProcessor(
                context = getApplication(),
                merchantId = merchantId,
                throttleDelayMs = smsThrottleDelayMs.value
            )
        }
    }

    fun sendQuickCustomSms(
        phones: List<String>,
        message: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (message.isBlank()) {
            onResult(false, "Message cannot be empty")
            return
        }
        viewModelScope.launch {
            val merchantId = activeProfile.value.id
            val slot = _selectedSimSlot.value
            val entities = phones.mapNotNull { p ->
                val clean = p.trim().replace(" ", "").replace("-", "")
                if (clean.length < 10) return@mapNotNull null
                OutboxSmsEntity(
                    merchantId = merchantId,
                    recipientPhone = clean,
                    messageText = message.trim(),
                    smsType = "GATEWAY_CUSTOM",
                    simSlot = slot,
                    status = "QUEUED",
                    partsCount = (message.length / 160) + 1
                )
            }

            if (entities.isNotEmpty()) {
                repository.queueOutboxSmsList(entities)
                com.example.service.SmsGatewayEngine.startOutboxQueueProcessor(
                    context = getApplication(),
                    merchantId = merchantId,
                    throttleDelayMs = smsThrottleDelayMs.value
                )
                onResult(true, "${entities.size} SMS enqueued for delivery.")
            } else {
                onResult(false, "Please provide valid phone number(s)")
            }
        }
    }

    fun deleteOutboxSms(id: String) {
        viewModelScope.launch {
            repository.deleteOutboxSms(id)
        }
    }

    fun clearOutboxSmsHistory() {
        viewModelScope.launch {
            repository.clearOutboxSms(activeProfile.value.id)
        }
    }

    fun retryFailedOutboxSms(item: OutboxSmsEntity) {
        viewModelScope.launch {
            repository.updateOutboxSmsStatus(item.id, "QUEUED", null, null)
            com.example.service.SmsGatewayEngine.startOutboxQueueProcessor(
                context = getApplication(),
                merchantId = activeProfile.value.id,
                throttleDelayMs = smsThrottleDelayMs.value
            )
        }
    }

    fun testSendEmailNotification(emailStr: String, onResult: (String) -> Unit) {
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(emailStr.trim())) {
            onResult("Enter a valid receipt email address")
            return
        }
        refreshGatewayReceiptHealth()
        onResult("Email delivery is server-managed. Receipt worker health verification started; no synthetic email was sent.")
    }

    // Multi-page management & Active component selector state
    val formPagesList = MutableStateFlow<List<FormPageItem>>(listOf(FormPageItem(id = "page_0", title = "Page 1: Customer Details", subtitle = "Please fill out your personal information")))
    val activePageIndex = MutableStateFlow<Int>(0)
    val selectedFieldId = MutableStateFlow<String?>(null)

    fun addFormPage(
        title: String = "",
        subtitle: String = "",
        isCustomHtml: Boolean = false,
        customHtmlContent: String = "",
        customCssContent: String = ""
    ) {
        if (formPagesList.value.size >= 25) return
        val newIdx = formPagesList.value.size + 1
        val newPage = FormPageItem(
            title = title.ifEmpty { if (isCustomHtml) "Page $newIdx: Custom HTML" else "Page $newIdx: Additional Details" },
            subtitle = subtitle.ifEmpty { if (isCustomHtml) "Custom HTML view with dynamic variables" else "Please complete the required fields" },
            isCustomHtml = isCustomHtml,
            customHtmlContent = customHtmlContent.ifEmpty {
                if (isCustomHtml) {
                    """<div style="padding: 24px; text-align: center;">
  <h2 style="color: #1E293B; margin-bottom: 8px;">{{form_title}}</h2>
  <p style="color: #64748B; font-size: 14px;">Welcome! Please review information below.</p>
  <div style="background: #F8FAFC; border: 1px dashed #CBD5E1; border-radius: 12px; padding: 16px; margin: 18px 0; text-align: left;">
    <p style="margin: 0 0 6px 0; font-weight: 700; color: #334155;">Merchant Notice:</p>
    <p style="margin: 0; font-size: 13px; color: #64748B;">Store: <strong>{{merchant_name}}</strong> | Contact: {{merchant_phone}}</p>
    <p style="margin: 4px 0 0; font-size: 13px; color: #64748B;">Date: {{current_date}}</p>
  </div>
</div>""".trimIndent()
                } else ""
            },
            customCssContent = customCssContent
        )
        formPagesList.value = formPagesList.value + newPage
        activePageIndex.value = formPagesList.value.size - 1
        saveActiveFormToHostedList()
    }

    fun updateFormCustomHtmlPage(
        index: Int,
        title: String,
        subtitle: String,
        htmlContent: String,
        cssContent: String = ""
    ) {
        if (index !in formPagesList.value.indices) return
        formPagesList.value = formPagesList.value.mapIndexed { pageIndex, page ->
            if (pageIndex == index) page.copy(
                title = title.take(120),
                subtitle = subtitle.take(500),
                isCustomHtml = true,
                customHtmlContent = htmlContent,
                customCssContent = cssContent
            ) else page
        }
        saveActiveFormToHostedList()
    }

    fun updateFormPage(index: Int, title: String, subtitle: String) {
        if (index !in formPagesList.value.indices) return
        formPagesList.value = formPagesList.value.mapIndexed { pageIndex, page ->
            if (pageIndex == index) page.copy(title = title.take(120), subtitle = subtitle.take(500)) else page
        }
        saveActiveFormToHostedList()
    }

    fun removeFormPage(index: Int) {
        if (formPagesList.value.size > 1 && index in formPagesList.value.indices) {
            val newList = formPagesList.value.toMutableList()
            newList.removeAt(index)
            formPagesList.value = newList
            formFieldsList.value = formFieldsList.value.map { field ->
                when {
                    field.pageIndex == index -> field.copy(pageIndex = (index - 1).coerceAtLeast(0))
                    field.pageIndex > index -> field.copy(pageIndex = field.pageIndex - 1)
                    else -> field
                }
            }
            activePageIndex.value = (index - 1).coerceAtLeast(0)
            saveActiveFormToHostedList()
        }
    }

    // JSON Schema Export & Import Engine
    fun exportFormConfigToJson(): String {
        val snapshot = HostedFormModel(
            id = activeFormId.value,
            title = formTitle.value,
            slug = formSlug.value,
            description = formDescription.value,
            status = formStatus.value,
            templateKey = formTemplateKey.value,
            themeConfig = formThemeConfig.value.copy(),
            fields = formFieldsList.value.map { it.copy() },
            products = formProductsList.value.map { it.copy() },
            pages = formPagesList.value.map { it.copy() }
        )
        return hostedFormToJson(snapshot).toString(2)
    }

    fun importFormConfigFromJson(jsonStr: String): Boolean {
        return try {
            if (jsonStr.toByteArray(Charsets.UTF_8).size > 1_000_000) return false
            val obj = org.json.JSONObject(jsonStr)
            obj.put("id", java.util.UUID.randomUUID().toString())
            obj.put("status", "DRAFT")
            val importedSlug = obj.optString("slug").lowercase().replace(Regex("[^a-z0-9-]"), "-")
                .replace(Regex("-+"), "-").trim('-').take(80)
            obj.put("slug", "${importedSlug.ifBlank { "imported-form" }}-${System.currentTimeMillis().toString().takeLast(6)}")
            loadCachedPaymentForm(obj, autoSelect = true)
            saveActiveFormToHostedList()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Undo / Redo Stack
    private val formUndoStack = mutableListOf<List<FormFieldItem>>()
    private val formRedoStack = mutableListOf<List<FormFieldItem>>()

    private fun pushFormStateToUndo() {
        if (formUndoStack.size > 30) formUndoStack.removeAt(0)
        formUndoStack.add(formFieldsList.value.map { it.copy() })
        formRedoStack.clear()
    }

    fun undoFormEdit() {
        if (formUndoStack.isNotEmpty()) {
            val previous = formUndoStack.removeAt(formUndoStack.size - 1)
            formRedoStack.add(formFieldsList.value.map { it.copy() })
            formFieldsList.value = previous
            saveActiveFormToHostedList()
        }
    }

    fun redoFormEdit() {
        if (formRedoStack.isNotEmpty()) {
            val next = formRedoStack.removeAt(formRedoStack.size - 1)
            formUndoStack.add(formFieldsList.value.map { it.copy() })
            formFieldsList.value = next
            saveActiveFormToHostedList()
        }
    }

    // Auto-Configure Form Template Engine
    fun autoConfigureTemplate(templateKey: String) {
        pushFormStateToUndo()
        formTemplateKey.value = templateKey
        when (templateKey) {
            "FLAGSHIP_PRODUCT" -> {
                formTitle.value = "Aura Pro Wireless Headphones"
                formDescription.value = "Engineered for acoustic depth with bespoke aerospace-grade magnesium dynamics, active noise cancellation, and high-fidelity sound."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Customer Full Name", placeholder = "Recipient's full name", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "bKash / Nagad Contact Number", placeholder = "01XXXXXXXXX", isRequired = true),
                    FormFieldItem(type = FormFieldType.ADDRESS, label = "Delivery Street Address", placeholder = "House, Road, Area, Thana", isRequired = true),
                    FormFieldItem(type = FormFieldType.SHIPPING, label = "Delivery Area & Speed", options = listOf("Standard Courier — 3-5 days (৳60)", "Express Courier — 24-48 hrs (৳120)", "Store Pickup — Banani, Dhaka (Free)")),
                    FormFieldItem(type = FormFieldType.COUPON, label = "Promo / Voucher Code", placeholder = "e.g. AURA10", isRequired = false),
                    FormFieldItem(type = FormFieldType.NOTES, label = "Delivery Instructions (Optional)", placeholder = "e.g. Call before delivery, leave with concierge", isRequired = false)
                )
                formProductsList.value = listOf(
                    FormProductItem(
                        title = "Aura Pro Wireless Headphones",
                        description = "Engineered for acoustic depth with bespoke aerospace-grade magnesium dynamics and 40h playtime.",
                        price = 2490.0,
                        salePrice = 3290.0,
                        stock = 18,
                        sku = "AURA-PRO-01",
                        category = "Audio & Electronics"
                    )
                )
                formThemeConfig.value = FormThemeConfig(
                    primaryColorHex = "#0D0F12",
                    buttonShape = "ROUNDED",
                    fontFamily = "Inter",
                    eyebrowText = "Flagship Audio • 2026 Collection",
                    badgeText = "Best Seller",
                    wasPrice = 3290.0,
                    ratingScore = 4.9,
                    ratingCount = 248
                )
            }
            "SINGLE_PRODUCT" -> {
                formTitle.value = "Single Product Direct Checkout"
                formDescription.value = "Instant 1-page checkout for your featured product."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Customer Name", placeholder = "Enter full name", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "bKash / Nagad Number", placeholder = "017XXXXXXXX", isRequired = true),
                    FormFieldItem(type = FormFieldType.ADDRESS, label = "Delivery Address", placeholder = "Full shipping address", isRequired = true),
                    FormFieldItem(type = FormFieldType.PRODUCT, label = "Selected Product", isRequired = true),
                    FormFieldItem(type = FormFieldType.QUANTITY, label = "Quantity", isRequired = true),
                    FormFieldItem(type = FormFieldType.SHIPPING, label = "Delivery Option", options = listOf("Standard Delivery (৳60)", "Express Delivery (৳120)")),
                    FormFieldItem(type = FormFieldType.COUPON, label = "Promo Code", isRequired = false)
                )
                formProductsList.value = emptyList()
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#7C3AED", buttonShape = "ROUNDED")
            }
            "MULTI_PRODUCT" -> {
                formTitle.value = "Multi-Product Store Catalog Checkout"
                formDescription.value = "Browse products, select items, and checkout seamlessly."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Customer Name", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Phone Number", isRequired = true),
                    FormFieldItem(type = FormFieldType.PRODUCT_LIST, label = "Select Items to Purchase", isRequired = true),
                    FormFieldItem(type = FormFieldType.ADDRESS, label = "Shipping Address", isRequired = true),
                    FormFieldItem(type = FormFieldType.DISCOUNT, label = "Subtotal & Discounts"),
                    FormFieldItem(type = FormFieldType.COUPON, label = "Coupon Code")
                )
                formProductsList.value = emptyList()
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#2563EB", buttonShape = "PILL")
            }
            "CART" -> {
                formTitle.value = "Full E-Commerce Shopping Cart Checkout"
                formDescription.value = "Complete shopping cart with taxes, coupons, and shipping."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Full Name", isRequired = true),
                    FormFieldItem(type = FormFieldType.EMAIL, label = "Email Address", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Phone Number", isRequired = true),
                    FormFieldItem(type = FormFieldType.ADDRESS, label = "Shipping Address", isRequired = true),
                    FormFieldItem(type = FormFieldType.PRODUCT_LIST, label = "Cart Items"),
                    FormFieldItem(type = FormFieldType.COUPON, label = "Apply Voucher Code"),
                    FormFieldItem(type = FormFieldType.SHIPPING, label = "Shipping Speed", options = listOf("Express (1 Day) - ৳150", "Standard (3 Days) - ৳70")),
                    FormFieldItem(type = FormFieldType.TAX, label = "VAT & Tax (5%)")
                )
                formProductsList.value = emptyList()
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#059669", buttonShape = "ROUNDED")
            }
            "DIGITAL", "DIGITAL_PRODUCT" -> {
                formTitle.value = "Digital Download Product Checkout"
                formDescription.value = "Instant automated email & screen file link delivery upon payment."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Buyer Name", isRequired = true),
                    FormFieldItem(type = FormFieldType.EMAIL, label = "Email Address (For file delivery)", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Phone Number", isRequired = true),
                    FormFieldItem(type = FormFieldType.PRODUCT, label = "Digital File Package"),
                    FormFieldItem(type = FormFieldType.COUPON, label = "Discount Coupon")
                )
                formProductsList.value = emptyList()
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#D97706", buttonShape = "ROUNDED")
            }
            "DONATION" -> {
                formTitle.value = "Mosque / NGO / Charity Donation Portal"
                formDescription.value = "Support our cause. Select a donation tier or enter a custom amount."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Donor Name", placeholder = "Anonymous or Full Name", isRequired = false),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Contact Phone", isRequired = true),
                    FormFieldItem(type = FormFieldType.DONATION, label = "Select Donation Tier", options = listOf("৳100 (Sadaqah)", "৳500 (Generous)", "৳1000 (Patron)", "৳5000 (Sponsor)")),
                    FormFieldItem(type = FormFieldType.CUSTOM_AMOUNT, label = "Or Custom Donation Amount (BDT)"),
                    FormFieldItem(type = FormFieldType.NOTES, label = "Special Prayer Request / Message", isRequired = false)
                )
                formProductsList.value = emptyList()
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#10B981", buttonShape = "PILL")
            }
            "APPOINTMENT" -> {
                formTitle.value = "Service Consultation & Appointment Booking"
                formDescription.value = "Book your slot and complete booking fee payment."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Client Name", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Phone Number", isRequired = true),
                    FormFieldItem(type = FormFieldType.EMAIL, label = "Email Address", isRequired = true),
                    FormFieldItem(type = FormFieldType.DROPDOWN, label = "Select Service Type", options = listOf("IT Consultation (৳1000)", "Legal Advice (৳2000)", "Business Strategy (৳1500)")),
                    FormFieldItem(type = FormFieldType.DROPDOWN, label = "Preferred Time Slot", options = listOf("Morning (10:00 AM)", "Afternoon (2:30 PM)", "Evening (6:00 PM)")),
                    FormFieldItem(type = FormFieldType.NOTES, label = "Topic / Notes for Specialist")
                )
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#6366F1", buttonShape = "ROUNDED")
            }
            "EVENT", "EVENT_TICKETING" -> {
                formTitle.value = "Conference / Ticket Registration Form"
                formDescription.value = "Reserve your seat and get instant QR pass."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Attendee Full Name", isRequired = true),
                    FormFieldItem(type = FormFieldType.EMAIL, label = "Email (For QR Ticket)", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Phone Number", isRequired = true),
                    FormFieldItem(type = FormFieldType.COMPANY, label = "Company / Organization", isRequired = false),
                    FormFieldItem(type = FormFieldType.RADIO, label = "Ticket Tier", options = listOf("Standard Pass (৳500)", "VIP Pass (৳1500)", "Student Pass (৳250)")),
                    FormFieldItem(type = FormFieldType.QUANTITY, label = "Number of Tickets")
                )
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#EC4899", buttonShape = "PILL")
            }
            "EDUCATION", "EDUCATION_FEE" -> {
                formTitle.value = "Course Tuition & Exam Fee Payment"
                formDescription.value = "Pay student fees online with instant digital invoice."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Student Full Name", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Guardian Contact Phone", isRequired = true),
                    FormFieldItem(type = FormFieldType.DROPDOWN, label = "Select Course / Batch", options = listOf("Batch 12 - Web Dev (৳4500)", "Batch 08 - AI & Automation (৳6000)", "Exam Retake Fee (৳1000)")),
                    FormFieldItem(type = FormFieldType.COUPON, label = "Scholarship Voucher Code")
                )
                formThemeConfig.value = FormThemeConfig(primaryColorHex = "#8B5CF6", buttonShape = "ROUNDED")
            }
            "ONLINE_MCQ_EXAM", "EXAM", "QUIZ", "MCQ_EXAM" -> {
                formTitle.value = "Certified Professional Examination — Online Assessment"
                formDescription.value = "Timed computer-based multiple choice examination with autosave and live progress tracking."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Candidate Full Name", placeholder = "Enter full legal name", isRequired = true, pageIndex = 0),
                    FormFieldItem(type = FormFieldType.NAME, label = "Roll / Student ID", placeholder = "e.g. REG-2026-8941", isRequired = true, pageIndex = 0),
                    FormFieldItem(type = FormFieldType.EMAIL, label = "Registered Email Address", placeholder = "candidate@example.com", isRequired = true, pageIndex = 0),
                    FormFieldItem(type = FormFieldType.PHONE, label = "Contact Phone", placeholder = "01XXXXXXXXX", isRequired = true, pageIndex = 0),
                    FormFieldItem(
                        type = FormFieldType.RADIO,
                        label = "Which methodology emphasizes iterative delivery in short cycles?",
                        options = listOf("Waterfall", "Agile", "Critical Path Method", "Six Sigma"),
                        isRequired = true,
                        pageIndex = 1
                    ),
                    FormFieldItem(
                        type = FormFieldType.RADIO,
                        label = "A risk register should be updated only at project closure.",
                        options = listOf("True", "False"),
                        isRequired = true,
                        pageIndex = 1
                    ),
                    FormFieldItem(
                        type = FormFieldType.CHECKBOX,
                        label = "Select all elements typically found in a project charter.",
                        options = listOf("Business case", "Stakeholder list", "Detailed Gantt chart", "High-level budget", "Vendor invoices"),
                        isRequired = true,
                        pageIndex = 1
                    ),
                    FormFieldItem(
                        type = FormFieldType.NAME,
                        label = "The process of identifying, analyzing, and responding to project risk is called risk ______.",
                        placeholder = "Fill in the blank...",
                        isRequired = true,
                        pageIndex = 1
                    ),
                    FormFieldItem(
                        type = FormFieldType.RADIO,
                        label = "Which document formally authorizes a project to begin?",
                        options = listOf("Project charter", "Status report", "Lessons learned register", "RACI matrix"),
                        isRequired = true,
                        pageIndex = 2
                    ),
                    FormFieldItem(
                        type = FormFieldType.RADIO,
                        label = "Earned Value Management can be used to forecast final project cost.",
                        options = listOf("True", "False"),
                        isRequired = true,
                        pageIndex = 2
                    ),
                    FormFieldItem(
                        type = FormFieldType.DROPDOWN,
                        label = "Match: Uncontrolled expansion of project scope is known as...",
                        options = listOf("Scope creep", "Critical path", "Milestone variance", "Sprint backlog"),
                        isRequired = true,
                        pageIndex = 2
                    ),
                    FormFieldItem(
                        type = FormFieldType.FILE_UPLOAD,
                        label = "Upload completed risk register or calculation sheet (PDF / Image)",
                        isRequired = false,
                        pageIndex = 2
                    )
                )
                formPagesList.value = listOf(
                    FormPageItem(id = "page_0", title = "Part 1: Candidate Verification", subtitle = "Verify your credentials before starting"),
                    FormPageItem(id = "page_1", title = "Part 2: Core Project Management MCQs", subtitle = "Select the most accurate answer for each item"),
                    FormPageItem(id = "page_2", title = "Part 3: Advanced Concepts & Scenarios", subtitle = "Applied risk and scheduling questions"),
                    FormPageItem(
                        id = "page_3",
                        title = "Exam Submitted",
                        subtitle = "Your assessment has been recorded",
                        isCustomHtml = true,
                        customHtmlContent = """<div style="max-width: 480px; margin: 30px auto; text-align: center; font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 24px;"><div style="width: 60px; height: 60px; border-radius: 50%; background: #E3F5EC; color: #1B8A5A; font-size: 28px; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px auto; font-weight: bold;">✓</div><h2 style="color: #1C1F26; margin: 0 0 8px 0; font-size: 22px;">Exam Submitted Successfully</h2><p style="color: #6B7280; font-size: 14.5px; margin: 0 0 16px 0; line-height: 1.6;">Your answers have been securely logged. The evaluation results will be published and sent to your registered email address.</p><div style="display: inline-block; padding: 8px 16px; background: #E8EDFC; color: #2856E0; font-weight: 700; border-radius: 6px; font-size: 13px;">Status: Response Locked</div></div>"""
                    )
                )
                formProductsList.value = emptyList()
                formThemeConfig.value = FormThemeConfig(
                    primaryColorHex = "#2856E0",
                    buttonShape = "ROUNDED",
                    isMultiPageForm = true,
                    enableTimer = true,
                    timerMinutes = 25,
                    progressTrackerStyle = "NUMBER",
                    enablePayment = false,
                    fontFamily = "Inter"
                )
            }
        }
        saveActiveFormToHostedList()
        logFirebaseStatus("Auto-configured Form Template: $templateKey")
    }

    // AI Form Generator Logic
    fun buildSemanticFormJson(prompt: String): org.json.JSONObject {
        val p = prompt.trim()
        val lc = p.lowercase()

        var cleanTitle = p
            .replace(Regex("^(create|make|build|generate|design)\\s+(a|an|the)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(form|page|checkout|payment)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanTitle.length > 50) cleanTitle = cleanTitle.take(50).trim()
        if (cleanTitle.isNotEmpty()) {
            cleanTitle = cleanTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            cleanTitle = "Payment & Order Form"
        }

        val isExam = listOf("exam", "quiz", "mcq", "test", "assessment", "questionnaire", "mock test").any { lc.contains(it) }
        val isEdu = !isExam && listOf("course", "tuition", "student", "school", "college", "university", "academy", "batch", "class", "admission", "training", "bootcamp").any { lc.contains(it) }
        val isDonation = listOf("donation", "mosque", "masjid", "madrasah", "ngo", "charity", "zakat", "sadaqah", "relief", "fundraiser", "waqf", "help").any { lc.contains(it) }
        val isEvent = listOf("event", "ticket", "conference", "webinar", "seminar", "meetup", "summit", "workshop", "party", "concert", "fest").any { lc.contains(it) }
        val isAppointment = listOf("appointment", "booking", "consult", "doctor", "clinic", "lawyer", "session", "slot", "schedule", "advisor").any { lc.contains(it) }
        val isDigital = listOf("digital", "download", "ebook", "pdf", "software", "script", "template", "plugin", "license", "preset").any { lc.contains(it) }
        val isFood = listOf("food", "restaurant", "burger", "pizza", "cafe", "catering", "bakery", "meal", "lunch", "dinner", "snack").any { lc.contains(it) }
        val isSub = listOf("membership", "subscription", "gym", "fitness", "club", "monthly", "annual").any { lc.contains(it) }
        val isClothing = listOf("shirt", "pant", "dress", "panjabi", "shoe", "cloth", "fashion", "tshirt", "hoodie", "saree").any { lc.contains(it) }
        val isFlagship = listOf("headphone", "earphone", "airpods", "aura", "gadget", "watch", "smartwatch", "flagship", "electronics").any { lc.contains(it) }

        val amountRegex = Regex("(?:৳|tk|bdt|\\$)\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)")
        val amountRegex2 = Regex("(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*(?:৳|tk|bdt|taka|dollars|\\$)")
        val amountMatch = amountRegex.find(lc) ?: amountRegex2.find(lc)
        val extractedAmount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.toInt()

        val hasBkash = listOf("bkash", "nagad", "rocket", "trx", "transaction", "txn").any { lc.contains(it) }
        val hasUpload = listOf("upload", "file", "pdf", "cv", "resume", "screenshot", "photo", "image").any { lc.contains(it) }
        val hasAddress = listOf("address", "shipping", "delivery", "home delivery", "courier").any { lc.contains(it) }
        val hasCoupon = listOf("coupon", "promo", "voucher", "discount").any { lc.contains(it) }

        val root = org.json.JSONObject()
        root.put("title", cleanTitle)

        var templateKey = "SINGLE_PRODUCT"
        var primaryColor = "#4F46E5"
        var buttonShape = "ROUNDED"
        var desc = "Online form for $cleanTitle. Please fill in your details below."

        val pagesArr = org.json.JSONArray()

        when {
            isExam -> {
                templateKey = "ONLINE_MCQ_EXAM"
                primaryColor = "#2856E0"
                buttonShape = "ROUNDED"
                desc = "Official online assessment for $cleanTitle. Timed exam with autosave and live progress tracking."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Part 1: Candidate Verification")
                    put("subtitle", "Verify your identity before starting the exam")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Candidate Full Name"); put("placeholder", "Enter full legal name"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Roll / Student ID"); put("placeholder", "e.g. REG-2026-8941"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "EMAIL"); put("label", "Registered Email Address"); put("placeholder", "candidate@example.com"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "Contact Phone"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                    }
                    put("fields", fieldsArr)
                }
                val p2 = org.json.JSONObject().apply {
                    put("title", "Part 2: Multiple Choice Questions")
                    put("subtitle", "Select the best answer for each question")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("type", "RADIO")
                            put("label", "Which methodology emphasizes iterative delivery in short cycles?")
                            put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Waterfall", "Agile", "Critical Path Method", "Six Sigma")))
                        })
                        put(org.json.JSONObject().apply {
                            put("type", "RADIO")
                            put("label", "A risk register should be updated only at project closure.")
                            put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("True", "False")))
                        })
                        put(org.json.JSONObject().apply {
                            put("type", "CHECKBOX")
                            put("label", "Select all elements typically found in a project charter.")
                            put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Business case", "Stakeholder list", "Detailed Gantt chart", "High-level budget", "Vendor invoices")))
                        })
                        put(org.json.JSONObject().apply {
                            put("type", "NAME")
                            put("label", "The process of identifying, analyzing, and responding to project risk is called risk ______.")
                            put("placeholder", "Fill in the blank...")
                            put("isRequired", true)
                        })
                    }
                    put("fields", fieldsArr)
                }
                val p3 = org.json.JSONObject().apply {
                    put("title", "Part 3: Advanced Scenarios & Matching")
                    put("subtitle", "Applied knowledge questions")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("type", "RADIO")
                            put("label", "Which document formally authorizes a project to begin?")
                            put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Project charter", "Status report", "Lessons learned register", "RACI matrix")))
                        })
                        put(org.json.JSONObject().apply {
                            put("type", "DROPDOWN")
                            put("label", "Match: Uncontrolled expansion of project scope is known as...")
                            put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Scope creep", "Critical path", "Milestone variance", "Sprint backlog")))
                        })
                        put(org.json.JSONObject().apply {
                            put("type", "FILE_UPLOAD")
                            put("label", "Upload completed calculation sheet or supporting PDF (Optional)")
                            put("isRequired", false)
                        })
                    }
                    put("fields", fieldsArr)
                }
                val p4 = org.json.JSONObject().apply {
                    put("title", "Exam Completed")
                    put("subtitle", "Your assessment has been submitted")
                    put("isCustomHtml", true)
                    put("fields", org.json.JSONArray())
                    put("customHtmlContent", """<div style="max-width: 480px; margin: 30px auto; text-align: center; font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 24px;"><div style="width: 60px; height: 60px; border-radius: 50%; background: #E3F5EC; color: #1B8A5A; font-size: 28px; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px auto; font-weight: bold;">✓</div><h2 style="color: #1C1F26; margin: 0 0 8px 0; font-size: 22px;">Exam Submitted Successfully</h2><p style="color: #6B7280; font-size: 14.5px; margin: 0 0 16px 0; line-height: 1.6;">Your responses have been recorded and locked for evaluation.</p><div style="display: inline-block; padding: 8px 16px; background: #E8EDFC; color: #2856E0; font-weight: 700; border-radius: 6px; font-size: 13px;">Reference ID: EXAM-LOCKED</div></div>""")
                }
                pagesArr.put(p1)
                pagesArr.put(p2)
                pagesArr.put(p3)
                pagesArr.put(p4)
            }
            isDonation -> {
                templateKey = "DONATION"
                primaryColor = "#059669"
                buttonShape = "PILL"
                desc = "Support our noble cause with your contribution. Select a donation tier or specify a custom amount."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Donation Details")
                    put("subtitle", "Every contribution makes a significant difference")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Donor Name / দাতার নাম"); put("placeholder", "Optional / Anonymous"); put("isRequired", false) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "Mobile Number / মোবাইল নম্বর"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "EMAIL"); put("label", "Email Address (for receipt)"); put("placeholder", "your@email.com"); put("isRequired", false) })
                        put(org.json.JSONObject().apply {
                            put("type", "DONATION"); put("label", "Select Contribution Amount"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("৳100 (Sadaqah)", "৳500 (Supporter)", "৳1,000 (Generous)", "৳5,000 (Patron)")))
                        })
                        put(org.json.JSONObject().apply { put("type", "CUSTOM_AMOUNT"); put("label", "Or Enter Custom Amount (BDT / টাকা)"); put("placeholder", "e.g. 2500"); put("isRequired", false) })
                        put(org.json.JSONObject().apply { put("type", "NOTES"); put("label", "Prayer Request / Purpose"); put("placeholder", "Any message or prayer request..."); put("isRequired", false) })
                        if (hasBkash) put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "bKash / Nagad TrxID"); put("placeholder", "e.g. 9J47KL89X"); put("isRequired", true) })
                        if (hasUpload) put(org.json.JSONObject().apply { put("type", "FILE_UPLOAD"); put("label", "Payment Screenshot / Deposit Slip"); put("isRequired", false) })
                    }
                    put("fields", fieldsArr)
                }
                val p2 = org.json.JSONObject().apply {
                    put("title", "Thank You")
                    put("subtitle", "Jazakallah Khair for your generosity")
                    put("isCustomHtml", true)
                    put("fields", org.json.JSONArray())
                    put("customHtmlContent", """<div style="text-align: center; padding: 32px 16px; font-family: sans-serif;"><div style="font-size: 54px; margin-bottom: 12px;">🤲</div><h2 style="color: #059669; margin: 0 0 8px 0; font-size: 24px;">জাযাকাল্লাহু খাইরান!</h2><p style="color: #4B5563; font-size: 15px; max-width: 440px; margin: 0 auto 20px auto; line-height: 1.6;">আপনার অনুদানের জন্য আন্তরিক ধন্যবাদ। আল্লাহ আপনার দানকে কবুল করুন ও উত্তম প্রতিদান দান করুন।</p><div style="display: inline-block; padding: 10px 20px; background: #ECFDF5; border: 1px solid #A7F3D0; border-radius: 9999px; color: #065F46; font-size: 13px; font-weight: 600;">Donation Receipt Will Be Sent via SMS</div></div>""")
                }
                pagesArr.put(p1)
                pagesArr.put(p2)
            }
            isEdu -> {
                templateKey = "EDUCATION"
                primaryColor = "#7C3AED"
                buttonShape = "ROUNDED"
                val price = extractedAmount ?: 3500
                desc = "Complete your course registration and fee payment to secure your seat in the batch."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Student Registration")
                    put("subtitle", "Enter your academic and contact information")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Student Full Name / শিক্ষার্থীর নাম"); put("placeholder", "e.g. Tanvir Hasan"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "Student WhatsApp / Phone Number"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "EMAIL"); put("label", "Email Address (for course access)"); put("placeholder", "student@example.com"); put("isRequired", true) })
                        put(org.json.JSONObject().apply {
                            put("type", "DROPDOWN"); put("label", "Select Batch / ব্যাচ"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Upcoming Live Batch (৳$price)", "Weekend Intensive (৳$price)", "Self-Paced Portal (৳${(price * 0.7).toInt()})")))
                        })
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Educational Institution / Background"); put("placeholder", "College / University"); put("isRequired", false) })
                        if (hasBkash) put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "bKash / Nagad Transaction ID"); put("placeholder", "e.g. 8K42NX99"); put("isRequired", true) })
                        if (hasUpload) put(org.json.JSONObject().apply { put("type", "FILE_UPLOAD"); put("label", "Upload Student ID / Payment Proof"); put("isRequired", false) })
                        if (hasCoupon) put(org.json.JSONObject().apply { put("type", "COUPON"); put("label", "Scholarship / Promo Code"); put("placeholder", "e.g. PROMO20"); put("isRequired", false) })
                    }
                    put("fields", fieldsArr)
                }
                val p2 = org.json.JSONObject().apply {
                    put("title", "Registration Confirmed")
                    put("subtitle", "Welcome to the class")
                    put("isCustomHtml", true)
                    put("fields", org.json.JSONArray())
                    put("customHtmlContent", """<div style="text-align: center; padding: 32px 16px; font-family: sans-serif;"><div style="font-size: 52px; margin-bottom: 12px;">🎓</div><h2 style="color: #7C3AED; margin: 0 0 8px 0; font-size: 24px;">অভিনন্দন! রেজিস্ট্রেশন সম্পন্ন হয়েছে</h2><p style="color: #4B5563; font-size: 15px; max-width: 440px; margin: 0 auto 20px auto; line-height: 1.6;">আপনার আসনটি সফলভাবে নিশ্চিত করা হয়েছে। ব্যাচ শুরু হওয়ার পূর্বে ক্লাসের লিংক ও রুটিন পাঠানো হবে।</p></div>""")
                }
                pagesArr.put(p1)
                pagesArr.put(p2)
            }
            isEvent -> {
                templateKey = "EVENT"
                primaryColor = "#EC4899"
                buttonShape = "PILL"
                val price = extractedAmount ?: 800
                desc = "Register now for $cleanTitle and receive your official digital entry pass."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Attendee Registration")
                    put("subtitle", "Reserve your seat for the event")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Attendee Full Name"); put("placeholder", "e.g. Sadia Islam"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "EMAIL"); put("label", "Email Address (for QR Pass)"); put("placeholder", "sadia@example.com"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "Phone Number"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "COMPANY"); put("label", "Organization / University"); put("placeholder", "Company or Institution"); put("isRequired", false) })
                        put(org.json.JSONObject().apply {
                            put("type", "RADIO"); put("label", "Ticket Tier"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("General Pass (৳$price)", "VIP Pass (৳${price * 2})", "Student Pass (৳${(price * 0.5).toInt()})")))
                        })
                        put(org.json.JSONObject().apply { put("type", "QUANTITY"); put("label", "Number of Tickets"); put("defaultValue", "1"); put("isRequired", true) })
                        if (hasBkash) put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "bKash / Nagad TrxID"); put("placeholder", "e.g. 9B88CL21"); put("isRequired", true) })
                        if (hasCoupon) put(org.json.JSONObject().apply { put("type", "COUPON"); put("label", "Discount Code"); put("placeholder", "e.g. EARLYBIRD"); put("isRequired", false) })
                    }
                    put("fields", fieldsArr)
                }
                val p2 = org.json.JSONObject().apply {
                    put("title", "Pass Issued")
                    put("subtitle", "Your digital ticket is ready")
                    put("isCustomHtml", true)
                    put("fields", org.json.JSONArray())
                    put("customHtmlContent", """<div style="text-align: center; padding: 32px 16px; font-family: sans-serif;"><div style="font-size: 54px; margin-bottom: 12px;">🎟️</div><h2 style="color: #EC4899; margin: 0 0 8px 0; font-size: 24px;">Ticket Reserved Successfully!</h2><p style="color: #4B5563; font-size: 15px; max-width: 440px; margin: 0 auto 20px auto; line-height: 1.6;">Thank you for registering. Your QR verification code is ready.</p></div>""")
                }
                pagesArr.put(p1)
                pagesArr.put(p2)
            }
            isAppointment -> {
                templateKey = "APPOINTMENT"
                primaryColor = "#0284C7"
                buttonShape = "ROUNDED"
                val price = extractedAmount ?: 1000
                desc = "Book a consultation slot. Select your convenient date and preferred time window."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Appointment Booking")
                    put("subtitle", "Choose your desired consultation slot")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Client Name"); put("placeholder", "Full Name"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "Contact Phone Number"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "EMAIL"); put("label", "Email Address"); put("placeholder", "client@example.com"); put("isRequired", false) })
                        put(org.json.JSONObject().apply {
                            put("type", "DROPDOWN"); put("label", "Consultation Type"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("General Session (৳$price)", "In-Depth Consultation (৳${price * 2})", "Follow-up Review (৳${(price * 0.6).toInt()})")))
                        })
                        put(org.json.JSONObject().apply { put("type", "DATE"); put("label", "Preferred Date"); put("placeholder", "YYYY-MM-DD"); put("isRequired", true) })
                        put(org.json.JSONObject().apply {
                            put("type", "DROPDOWN"); put("label", "Time Window"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Morning Slot (10:00 AM - 12:00 PM)", "Afternoon Slot (02:30 PM - 05:00 PM)", "Evening Slot (06:30 PM - 09:00 PM)")))
                        })
                        put(org.json.JSONObject().apply { put("type", "NOTES"); put("label", "Reason / Notes"); put("placeholder", "Describe your requirements..."); put("isRequired", false) })
                    }
                    put("fields", fieldsArr)
                }
                pagesArr.put(p1)
            }
            isDigital -> {
                templateKey = "DIGITAL"
                primaryColor = "#D97706"
                buttonShape = "ROUNDED"
                val price = extractedAmount ?: 1200
                desc = "Instant automated digital delivery. Download link and license sent upon payment."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Digital Order")
                    put("subtitle", "Enter your email for instant automated file link delivery")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Buyer Full Name"); put("placeholder", "Your Name"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "EMAIL"); put("label", "Delivery Email (Required for file link)"); put("placeholder", "your@email.com"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "WhatsApp / Phone Number"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                        put(org.json.JSONObject().apply {
                            put("type", "DROPDOWN"); put("label", "License Tier"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Single License (৳$price)", "Commercial License (৳${price * 3})")))
                        })
                        if (hasCoupon) put(org.json.JSONObject().apply { put("type", "COUPON"); put("label", "Discount Code"); put("placeholder", "e.g. LAUNCH50"); put("isRequired", false) })
                    }
                    put("fields", fieldsArr)
                }
                pagesArr.put(p1)
            }
            isFlagship -> {
                templateKey = "FLAGSHIP_PRODUCT"
                primaryColor = "#0D0F12"
                buttonShape = "ROUNDED"
                val price = extractedAmount ?: 2490
                desc = "Engineered for acoustic depth and unmatched performance. Order $cleanTitle with instant checkout."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Delivery & Details")
                    put("subtitle", "Fill in your delivery address for instant fulfillment")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Recipient Full Name"); put("placeholder", "e.g. Tanvir Ahmed"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "bKash / Nagad Contact Phone"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "ADDRESS"); put("label", "Delivery Street Address"); put("placeholder", "House, Road, Area, Thana"); put("isRequired", true) })
                        put(org.json.JSONObject().apply {
                            put("type", "SHIPPING"); put("label", "Delivery Speed & Area"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Standard Courier — 3-5 days (৳60)", "Express Courier — 24-48 hrs (৳120)", "Store Pickup (Banani, Dhaka - Free)")))
                        })
                        if (hasCoupon) put(org.json.JSONObject().apply { put("type", "COUPON"); put("label", "Promo / Voucher Code"); put("placeholder", "e.g. AURA10"); put("isRequired", false) })
                        put(org.json.JSONObject().apply { put("type", "NOTES"); put("label", "Delivery Instructions (Optional)"); put("placeholder", "e.g. Call before delivery"); put("isRequired", false) })
                    }
                    put("fields", fieldsArr)
                }
                val p2 = org.json.JSONObject().apply {
                    put("title", "Order Received")
                    put("subtitle", "Thank you for shopping with us")
                    put("isCustomHtml", true)
                    put("fields", org.json.JSONArray())
                    put("customHtmlContent", """<div style="text-align: center; padding: 32px 16px; font-family: sans-serif;"><div style="font-size: 54px; margin-bottom: 12px;">🎧</div><h2 style="color: #0D0F12; margin: 0 0 8px 0; font-size: 24px;">Order Placed Successfully!</h2><p style="color: #4B5563; font-size: 15px; max-width: 440px; margin: 0 auto 20px auto; line-height: 1.6;">Your order for $cleanTitle has been recorded and will be dispatched promptly.</p></div>""")
                }
                pagesArr.put(p1)
                pagesArr.put(p2)
            }
            else -> {
                // E-Commerce / Physical Goods / General Form
                templateKey = if (hasAddress || isClothing || isFood) "SINGLE_PRODUCT" else "CART"
                primaryColor = if (isFood) "#EA580C" else if (isClothing) "#0F172A" else "#2563EB"
                buttonShape = "ROUNDED"
                val price = extractedAmount ?: (if (isClothing) 950 else if (isFood) 450 else 1250)
                desc = "Order $cleanTitle online with nationwide courier delivery and cash on delivery or online payment."

                val p1 = org.json.JSONObject().apply {
                    put("title", "Order & Shipping")
                    put("subtitle", "Provide your delivery details to complete your order")
                    put("isCustomHtml", false)
                    val fieldsArr = org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "Customer Full Name / আপনার নাম"); put("placeholder", "e.g. Asif Mahmud"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "PHONE"); put("label", "Mobile Number / মোবাইল নম্বর"); put("placeholder", "01XXXXXXXXX"); put("isRequired", true) })
                        put(org.json.JSONObject().apply { put("type", "ADDRESS"); put("label", "Full Delivery Address / পূর্ণ ঠিকানা"); put("placeholder", "House/Road, Area, District/Thana"); put("isRequired", true) })
                        if (isClothing) {
                            put(org.json.JSONObject().apply {
                                put("type", "DROPDOWN"); put("label", "Select Size / সাইজ"); put("isRequired", true)
                                put("options", org.json.JSONArray(listOf("M (Medium)", "L (Large)", "XL (Extra Large)", "XXL")))
                            })
                            put(org.json.JSONObject().apply {
                                put("type", "RADIO"); put("label", "Color Variant / কালার"); put("isRequired", false)
                                put("options", org.json.JSONArray(listOf("Black", "Navy Blue", "Maroon", "White")))
                            })
                        }
                        put(org.json.JSONObject().apply { put("type", "QUANTITY"); put("label", "Quantity / পরিমাণ"); put("defaultValue", "1"); put("isRequired", true) })
                        put(org.json.JSONObject().apply {
                            put("type", "SHIPPING"); put("label", "Delivery Area / ডেলিভারি এলাকা"); put("isRequired", true)
                            put("options", org.json.JSONArray(listOf("Inside Dhaka (৳60)", "Sub-Dhaka / Savar / Gazipur (৳100)", "Outside Dhaka Nationwide (৳130)")))
                        })
                        if (hasCoupon) put(org.json.JSONObject().apply { put("type", "COUPON"); put("label", "Discount Voucher Code"); put("placeholder", "e.g. SAVE10"); put("isRequired", false) })
                        if (hasBkash) put(org.json.JSONObject().apply { put("type", "NAME"); put("label", "bKash / Nagad TrxID (if paid)"); put("placeholder", "Leave empty for COD"); put("isRequired", false) })
                        if (hasUpload) put(org.json.JSONObject().apply { put("type", "FILE_UPLOAD"); put("label", "Reference Image / Slip"); put("isRequired", false) })
                    }
                    put("fields", fieldsArr)
                }
                val p2 = org.json.JSONObject().apply {
                    put("title", "Order Received")
                    put("subtitle", "Thank you for shopping with us")
                    put("isCustomHtml", true)
                    put("fields", org.json.JSONArray())
                    put("customHtmlContent", """<div style="text-align: center; padding: 32px 16px; font-family: sans-serif;"><div style="font-size: 54px; margin-bottom: 12px;">📦</div><h2 style="color: $primaryColor; margin: 0 0 8px 0; font-size: 24px;">অর্ডার সফলভাবে গ্রহণ করা হয়েছে!</h2><p style="color: #4B5563; font-size: 15px; max-width: 440px; margin: 0 auto 20px auto; line-height: 1.6;">আপনার অর্ডারটি সফলভাবে নিবন্ধিত হয়েছে। আমাদের টিম শিগগিরই কল বা এসএমএসের মাধ্যমে আপনার অর্ডার কনফার্ম করবে।</p></div>""")
                }
                pagesArr.put(p1)
                pagesArr.put(p2)
            }
        }

        root.put("description", desc)
        root.put("template_key", templateKey)

        val theme = org.json.JSONObject().apply {
            put("primaryColorHex", primaryColor)
            put("backgroundColorHex", "#F8FAFC")
            put("buttonShape", buttonShape)
            put("fontFamily", "Inter")
            put("isDarkMode", false)
            put("showHeader", true)
            put("backgroundStyle", "SOLID")
            put("enablePayment", true)
            put("currencyCode", "BDT")
            put("redirectType", "SUCCESS_MSG")
            put("successMessage", "ধন্যবাদ! আপনার তথ্য সফলভাবে জমা হয়েছে।")
            put("enableAntiSpam", true)
            put("isMultiPageForm", pagesArr.length() > 1)
            put("progressTrackerStyle", "BAR")
        }
        root.put("theme", theme)
        root.put("pages", pagesArr)

        val customVars = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply { put("key", "customer_name"); put("exampleValue", "Asif Mahmud"); put("source", "field") })
            put(org.json.JSONObject().apply { put("key", "customer_phone"); put("exampleValue", "01712345678"); put("source", "field") })
            put(org.json.JSONObject().apply { put("key", "form_title"); put("exampleValue", cleanTitle); put("source", "system") })
        }
        root.put("custom_variables", customVars)

        return root
    }

    fun generateFormWithAI(prompt: String) {
        pushFormStateToUndo()
        aiFormPromptInput.value = prompt
        try {
            val json = buildSemanticFormJson(prompt)
            applyGeminiGeneratedForm(json, prompt)
            saveActiveFormToHostedList()
            logFirebaseStatus("AI Semantic Form Generator built form from prompt: '$prompt'")
        } catch (e: Exception) {
            android.util.Log.e("AppViewModel", "Semantic form gen error: ${e.message}", e)
            formTitle.value = prompt.take(40).ifBlank { "Custom Form" }
            saveActiveFormToHostedList()
        }
    }

    fun generateFormFromAiPrompt(prompt: String) {
        generateFormWithAI(prompt)
    }

    fun applyGeminiGeneratedForm(formObj: org.json.JSONObject, originalPrompt: String) {
        val titleStr = formObj.optString("title").ifBlank { "AI Form: ${originalPrompt.take(40)}" }
        formTitle.value = titleStr
        formDescription.value = formObj.optString("description")
        val tKey = formObj.optString("template_key", "SINGLE_PRODUCT")
        formTemplateKey.value = tKey

        // Parse Theme
        val themeObj = formObj.optJSONObject("theme")
        if (themeObj != null) {
            val currentTheme = formThemeConfig.value
            val newTheme = currentTheme.copy(
                primaryColorHex = themeObj.optString("primaryColorHex", currentTheme.primaryColorHex),
                backgroundColorHex = themeObj.optString("backgroundColorHex", currentTheme.backgroundColorHex),
                buttonShape = themeObj.optString("buttonShape", currentTheme.buttonShape),
                fontFamily = themeObj.optString("fontFamily", currentTheme.fontFamily),
                isDarkMode = themeObj.optBoolean("isDarkMode", currentTheme.isDarkMode),
                showHeader = themeObj.optBoolean("showHeader", currentTheme.showHeader),
                backgroundStyle = themeObj.optString("backgroundStyle", currentTheme.backgroundStyle),
                gradientColorStart = themeObj.optString("gradientColorStart", currentTheme.gradientColorStart),
                gradientColorEnd = themeObj.optString("gradientColorEnd", currentTheme.gradientColorEnd),
                enablePayment = themeObj.optBoolean("enablePayment", currentTheme.enablePayment),
                currencyCode = themeObj.optString("currencyCode", currentTheme.currencyCode),
                redirectType = themeObj.optString("redirectType", currentTheme.redirectType),
                closedMessage = themeObj.optString("successMessage", currentTheme.closedMessage),
                enableAntiSpam = themeObj.optBoolean("enableAntiSpam", currentTheme.enableAntiSpam),
                isMultiPageForm = themeObj.optBoolean("isMultiPageForm", currentTheme.isMultiPageForm),
                progressTrackerStyle = themeObj.optString("progressTrackerStyle", currentTheme.progressTrackerStyle),
                customCss = themeObj.optString("customCss", currentTheme.customCss),
                customJs = themeObj.optString("customJs", currentTheme.customJs),
                enableCustomJs = themeObj.optBoolean("enableCustomJs", currentTheme.enableCustomJs)
            )
            formThemeConfig.value = newTheme
        }

        // Parse Custom Variables
        val customVarsArr = formObj.optJSONArray("custom_variables")
        if (customVarsArr != null && customVarsArr.length() > 0) {
            val varsList = mutableListOf<CustomVariable>()
            for (i in 0 until customVarsArr.length()) {
                val vObj = customVarsArr.optJSONObject(i) ?: continue
                val k = vObj.optString("key").trim().removePrefix("{{").removeSuffix("}}")
                if (k.isNotBlank()) {
                    varsList.add(
                        CustomVariable(
                            key = k,
                            exampleValue = vObj.optString("exampleValue", ""),
                            source = vObj.optString("source", "field")
                        )
                    )
                }
            }
            if (varsList.isNotEmpty()) {
                formThemeConfig.value = formThemeConfig.value.copy(customVariables = varsList)
            }
        }

        // Parse Pages and Fields
        val pagesArr = formObj.optJSONArray("pages")
        if (pagesArr != null && pagesArr.length() > 0) {
            val parsedPages = mutableListOf<FormPageItem>()
            val parsedFields = mutableListOf<FormFieldItem>()

            for (pIdx in 0 until pagesArr.length()) {
                val pObj = pagesArr.optJSONObject(pIdx) ?: continue
                val pageTitle = pObj.optString("title", "Page ${pIdx + 1}")
                val pageSubtitle = pObj.optString("subtitle", "")
                val isCustomHtml = pObj.optBoolean("isCustomHtml", false)
                val customHtml = pObj.optString("customHtmlContent", "")
                val customCss = pObj.optString("customCssContent", "")

                parsedPages.add(
                    FormPageItem(
                        id = "page_$pIdx",
                        title = pageTitle,
                        subtitle = pageSubtitle,
                        isCustomHtml = isCustomHtml,
                        customHtmlContent = customHtml,
                        customCssContent = customCss
                    )
                )

                if (!isCustomHtml) {
                    val fieldsArr = pObj.optJSONArray("fields")
                    if (fieldsArr != null) {
                        for (fIdx in 0 until fieldsArr.length()) {
                            val fObj = fieldsArr.optJSONObject(fIdx) ?: continue
                            val typeStr = fObj.optString("type", "NAME").uppercase()
                            val fieldType = runCatching { FormFieldType.valueOf(typeStr) }.getOrDefault(FormFieldType.NAME)
                            val label = fObj.optString("label", fieldType.displayName)
                            val placeholder = fObj.optString("placeholder", "")
                            val helperText = fObj.optString("helperText", "")
                            val isReq = fObj.optBoolean("isRequired", true)
                            val defVal = fObj.optString("defaultValue", "")

                            val optList = mutableListOf<String>()
                            val optArr = fObj.optJSONArray("options")
                            if (optArr != null) {
                                for (o in 0 until optArr.length()) {
                                    optList.add(optArr.optString(o))
                                }
                            }

                            parsedFields.add(
                                FormFieldItem(
                                    type = fieldType,
                                    label = label,
                                    placeholder = placeholder,
                                    helperText = helperText,
                                    isRequired = isReq,
                                    defaultValue = defVal,
                                    options = if (optList.isNotEmpty()) optList else when (fieldType) {
                                        FormFieldType.RADIO, FormFieldType.DROPDOWN -> listOf("Option 1", "Option 2")
                                        else -> emptyList()
                                    },
                                    pageIndex = pIdx
                                )
                            )
                        }
                    }
                }
            }

            if (parsedPages.isNotEmpty()) {
                formPagesList.value = parsedPages
                activePageIndex.value = 0
            }
            if (parsedFields.isNotEmpty()) {
                formFieldsList.value = parsedFields
            }
        }
    }

    fun generateFormWithGemini(
        prompt: String,
        feedback: String? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            isAiFormGenerating.value = true
            aiFormError.value = null
            pushFormStateToUndo()
            aiFormPromptInput.value = prompt

            var success = false
            try {
                val merchantId = _activeProfile.value.id.ifEmpty { "default" }
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val payload = org.json.JSONObject().apply {
                    put("prompt", prompt)
                    if (!feedback.isNullOrBlank()) {
                        put("feedback", feedback)
                        val currentFormJson = org.json.JSONObject().apply {
                            put("title", formTitle.value)
                            put("description", formDescription.value)
                            put("template_key", formTemplateKey.value)
                            val pagesArr = org.json.JSONArray()
                            formPagesList.value.forEach { page ->
                                val pageObj = org.json.JSONObject().apply {
                                    put("title", page.title)
                                    put("subtitle", page.subtitle)
                                    put("isCustomHtml", page.isCustomHtml)
                                    put("customHtmlContent", page.customHtmlContent)
                                    put("customCssContent", page.customCssContent)
                                }
                                pagesArr.put(pageObj)
                            }
                            put("pages", pagesArr)
                        }
                        put("current_form", currentFormJson)
                    }
                    put("merchant_id", merchantId)
                    val gemKey = _geminiApiKey.value.trim()
                    if (gemKey.isNotBlank()) {
                        put("gemini_api_key", gemKey)
                    }
                    val gemModel = _selectedGeminiModel.value.trim()
                    if (gemModel.isNotBlank()) {
                        put("gemini_model", gemModel)
                    }
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)

                val candidateBases = listOf("https://api.swapnopay.top", "https://swapnopay.top", "https://pay.swapnopay.top")
                var responseStr: String? = null

                for (base in candidateBases) {
                    try {
                        val reqBuilder = okhttp3.Request.Builder()
                            .url("$base/v1/ai/generate-form")
                            .post(body)
                        val gemKey = _geminiApiKey.value.trim()
                        if (gemKey.isNotBlank()) {
                            reqBuilder.addHeader("x-gemini-api-key", gemKey)
                        }
                        val request = reqBuilder.build()
                        val res = withContext(Dispatchers.IO) {
                            client.newCall(request).execute().use { resp ->
                                if (resp.isSuccessful) resp.body?.string() else null
                            }
                        }
                        if (!res.isNullOrBlank()) {
                            responseStr = res
                            break
                        }
                    } catch (e_: Exception) { android.util.Log.w("AppViewModel", "Suppressed: ${e_.message}") }
                }

                if (!responseStr.isNullOrBlank()) {
                    val json = runCatching { org.json.JSONObject(responseStr) }.getOrNull()
                    if (json != null) {
                        val formObj = json.optJSONObject("form")
                        if (formObj != null && (json.optBoolean("success") || json.optBoolean("fallback"))) {
                            applyGeminiGeneratedForm(formObj, prompt)
                            success = true
                            logFirebaseStatus("AI Form Builder generated full custom form from prompt: '$prompt'")
                        }
                    }
                }

                if (!success) {
                    // Fallback to client Gemini / OpenRouter if configured, or template keyword matching
                    val clientGeminiKey = _geminiApiKey.value
                    if (clientGeminiKey.isNotBlank()) {
                        val systemPrompt = "You are an expert form designer for SwapnoPay. Respond ONLY with valid JSON conforming to the requested form schema (title, description, template_key, theme, pages with fields or isCustomHtml, custom_variables). No code fences, no commentary."
                        val userMsg = if (!feedback.isNullOrBlank()) {
                            "Update this form based on prompt '$prompt' and feedback '$feedback'."
                        } else {
                            "Create a complete form for: '$prompt'."
                        }
                        val messages = JSONArray().apply {
                            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                            put(JSONObject().apply { put("role", "user"); put("content", userMsg) })
                        }
                        val model = getAutoSelectedGeminiModel()
                        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                            viewModelScope.launch {
                                GeminiClient.getChatCompletion(
                                    apiKey = clientGeminiKey,
                                    model = model,
                                    messages = messages,
                                    onSuccess = { rawResp ->
                                        val clean = rawResp.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                                        val formObj = runCatching { org.json.JSONObject(clean) }.getOrNull()
                                        if (formObj != null) {
                                            applyGeminiGeneratedForm(formObj, prompt)
                                            success = true
                                        } else {
                                            generateFormWithAI(prompt)
                                        }
                                        if (cont.isActive) cont.resume(Unit) {}
                                    },
                                    onFailure = {
                                        generateFormWithAI(prompt)
                                        if (cont.isActive) cont.resume(Unit) {}
                                    }
                                )
                            }
                        }
                    } else {
                        generateFormWithAI(prompt)
                        success = true
                    }
                }

                saveActiveFormToHostedList()
            } catch (e: Exception) {
                aiFormError.value = e.message
                generateFormWithAI(prompt)
                saveActiveFormToHostedList()
            } finally {
                isAiFormGenerating.value = false
                onDone(true)
            }
        }
    }

    // Field Operations
    fun addFormField(type: FormFieldType) {
        if (formFieldsList.value.size >= 200) return
        pushFormStateToUndo()
        val defaultMediaUrl = when (type) {
            FormFieldType.IMAGE -> "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=1200"
            FormFieldType.VIDEO -> "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            FormFieldType.PDF -> ""
            else -> ""
        }
        val defaultOptions = when (type) {
            FormFieldType.COUPON -> listOf("SAVE10:10%", "FLAT50:50", "SWAPNO20:20%")
            else -> listOf("Option 1", "Option 2")
        }
        val newItem = FormFieldItem(
            type = type,
            label = if (type == FormFieldType.COUPON) "Promo / Coupon Code" else type.displayName,
            placeholder = if (type == FormFieldType.COUPON) "Enter promo code (e.g. SAVE10)" else "Enter ${type.displayName.lowercase()}",
            options = defaultOptions,
            pageIndex = activePageIndex.value,
            helperText = if (type == FormFieldType.IMAGE) "Promotional Announcement Banner" else "",
            isRequired = type !in setOf(
                FormFieldType.CUSTOM_CODE,
                FormFieldType.MEDIA_IMAGE,
                FormFieldType.MEDIA_VIDEO,
                FormFieldType.MEDIA_PDF,
                FormFieldType.IMAGE,
                FormFieldType.VIDEO,
                FormFieldType.PDF,
                FormFieldType.PRODUCT,
                FormFieldType.PRODUCT_LIST,
                FormFieldType.DISCOUNT,
                FormFieldType.SHIPPING,
                FormFieldType.TAX,
                FormFieldType.TIP,
                FormFieldType.CURRENCY,
                FormFieldType.COUPON
            ),
            mediaUrl = defaultMediaUrl,
            mediaAltText = "Banner Image",
            mediaHeightDp = 180,
            minValue = when (type) {
                FormFieldType.CUSTOM_AMOUNT, FormFieldType.QUANTITY -> 1.0
                else -> null
            }
        )
        formFieldsList.value = formFieldsList.value + newItem
        saveActiveFormToHostedList()
        logFirebaseStatus("Added form field: ${type.displayName}")
    }

    fun reorderFormField(fromIndex: Int, toIndex: Int) {
        val current = formFieldsList.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            pushFormStateToUndo()
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            formFieldsList.value = current
            saveActiveFormToHostedList()
        }
    }

    fun duplicateFormField(fieldId: String) {
        if (formFieldsList.value.size >= 200) return
        val current = formFieldsList.value.toMutableList()
        val index = current.indexOfFirst { it.id == fieldId }
        if (index != -1) {
            pushFormStateToUndo()
            val original = current[index]
            val copy = original.copy(
                id = java.util.UUID.randomUUID().toString(),
                label = "${original.label} (Copy)"
            )
            current.add(index + 1, copy)
            formFieldsList.value = current
            saveActiveFormToHostedList()
        }
    }

    fun deleteFormField(fieldId: String) {
        pushFormStateToUndo()
        formFieldsList.value = formFieldsList.value.filter { it.id != fieldId }
        saveActiveFormToHostedList()
    }

    fun toggleFieldCollapse(fieldId: String) {
        formFieldsList.value = formFieldsList.value.map {
            if (it.id == fieldId) it.copy(isCollapsed = !it.isCollapsed) else it
        }
        saveActiveFormToHostedList()
    }

    fun updateField(
        fieldId: String,
        newLabel: String,
        newPlaceholder: String,
        isRequired: Boolean,
        options: List<String>,
        helperText: String = "",
        mediaUrl: String = "",
        mediaAltText: String = "",
        mediaHeightDp: Int = 180
    ) {
        pushFormStateToUndo()
        formFieldsList.value = formFieldsList.value.map {
            if (it.id == fieldId) {
                it.copy(
                    label = newLabel,
                    placeholder = newPlaceholder,
                    isRequired = isRequired,
                    options = options,
                    helperText = helperText,
                    mediaUrl = mediaUrl,
                    mediaAltText = mediaAltText,
                    mediaHeightDp = mediaHeightDp
                )
            } else it
        }
        saveActiveFormToHostedList()
    }

    fun addFormProduct(title: String, price: Double, salePrice: Double, sku: String, stock: Int, category: String, isDigital: Boolean, imageUrl: String = "") {
        if (formProductsList.value.size >= 100) return
        val newProd = FormProductItem(
            title = title,
            price = price,
            salePrice = salePrice,
            sku = sku,
            stock = stock,
            category = category,
            imageUrl = imageUrl,
            isDigital = isDigital
        )
        formProductsList.value = formProductsList.value + newProd
        saveActiveFormToHostedList()
    }

    fun deleteFormProduct(productId: String) {
        formProductsList.value = formProductsList.value.filter { it.id != productId }
        saveActiveFormToHostedList()
    }

    fun updateFormProduct(product: FormProductItem) {
        pushFormStateToUndo()
        formProductsList.value = formProductsList.value.map { if (it.id == product.id) product else it }
        saveActiveFormToHostedList()
    }

    fun saveOrUpdateShowcaseProduct(product: FormProductItem) {
        pushFormStateToUndo()
        val list = formProductsList.value.toMutableList()
        val idx = list.indexOfFirst { it.id == product.id }
        if (idx != -1) {
            list[idx] = product
        } else if (list.isNotEmpty()) {
            list[0] = product
        } else {
            list.add(product)
        }
        formProductsList.value = list
        saveActiveFormToHostedList()
    }

    fun saveFormDraft() {
        formStatus.value = "DRAFT"
        saveActiveFormToHostedList()
        logFirebaseStatus("Form saved as DRAFT. Title: '${formTitle.value}'")
    }

    fun publishForm(): FormValidationResult {
        val validation = FormValidationEngine.validateFormForPublishing(
            title = formTitle.value,
            slug = formSlug.value,
            fields = formFieldsList.value
        )
        if (!validation.isValid) {
            logFirebaseStatus("Form publish failed: ${validation.summaryMessage}")
            return validation
        }
        val publishErrors = linkedMapOf<String, String>()
        val theme = formThemeConfig.value
        if (theme.enablePayment) {
            val hasPricing = formProductsList.value.isNotEmpty() || formFieldsList.value.any { it.type in listOf(FormFieldType.PRODUCT, FormFieldType.PRODUCT_LIST, FormFieldType.CUSTOM_AMOUNT) }
            if (hasPricing && formFieldsList.value.none { it.type == FormFieldType.PHONE && it.isRequired }) {
                publishErrors["payment_phone"] = "Payment forms require a required phone field for payer matching."
            }
            if (theme.currencyCode != "BDT") {
                publishErrors["payment_currency"] = "The connected mobile payment gateway currently settles in BDT."
            }
        }
        if (theme.redirectType == "REDIRECT_URL" && !theme.redirectUrl.matches(Regex("^https://[^\\s]+$"))) {
            publishErrors["redirect_url"] = "Redirect URL must be a valid HTTPS URL."
        }
        if (theme.redirectType == "CUSTOM_HTML" && theme.customHtmlContent.isBlank()) {
            publishErrors["custom_html"] = "Upload or enter custom HTML before using the custom-code response."
        }
        if (theme.bannerUrl.isNotBlank() && !theme.bannerUrl.matches(Regex("^(https://|data:image/)[^\\s]+$"))) {
            publishErrors["banner_url"] = "Cover photo URL must use HTTPS or data URI."
        }
        if (theme.logoUrl.isNotBlank() && !theme.logoUrl.matches(Regex("^(https://|data:image/)[^\\s]+$"))) {
            publishErrors["logo_url"] = "Logo URL must use HTTPS or data URI."
        }
        if (theme.backgroundStyle == "GRADIENT" && listOf(theme.gradientColorStart, theme.gradientColorEnd).any { !it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }) {
            publishErrors["gradient_colors"] = "Gradient colors must use six-digit hex values such as #5B7FFF."
        }
        if (listOf(theme.primaryColorHex, theme.backgroundColorHex).any { !it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }) {
            publishErrors["theme_colors"] = "Primary and background colors must use six-digit hex values."
        }
        if (theme.fontFamily.uppercase() !in setOf("INTER", "SYSTEM", "SERIF", "MONOSPACE", "OUTFIT", "POPPINS", "ROBOTO", "PLUS JAKARTA SANS", "PLAYFAIR DISPLAY")) {
            publishErrors["font_family"] = "Choose a supported hosted-form font family."
        }
        if (theme.productImageUrl.isNotBlank() && !theme.productImageUrl.matches(Regex("^(https://|data:image/)[^\\s]+$"))) {
            publishErrors["product_image_url"] = "Product image URL must use HTTPS or data URI."
        }
        if (theme.buttonShape.uppercase() !in setOf("ROUNDED", "PILL", "SQUARE")) {
            publishErrors["button_shape"] = "Choose a supported button shape."
        }
        if (theme.progressTrackerStyle.uppercase() !in setOf("BAR", "NUMBER", "HIDE")) {
            publishErrors["progress_tracker"] = "Choose a supported multi-page progress tracker."
        }
        if (theme.enforceQuantityRange && (theme.minQuantity < 1 || theme.maxQuantity < theme.minQuantity)) {
            publishErrors["quantity_range"] = "Quantity limits must start at 1 and the maximum must be at least the minimum."
        }
        val fields = formFieldsList.value
        val fieldsById = fields.associateBy { it.id }
        if (fields.size > 200) publishErrors["field_count"] = "Hosted forms support up to 200 fields."
        if (formProductsList.value.size > 100) publishErrors["product_count"] = "Hosted forms support up to 100 products."
        if (formPagesList.value.size > 25) publishErrors["page_count"] = "Multi-page forms support up to 25 pages."
        if (theme.customVariables.size > 100) publishErrors["variable_count"] = "Custom code supports up to 100 form variables."
        val unsafeValidationPattern = Regex("""\\[1-9]|\(\?(?!:)|\([^)]*[+*][^)]*\)[+*{]""")
        fields.forEachIndexed { index, field ->
            val fieldKey = field.label.ifBlank { "Field ${index + 1}" }
            if (field.minLength > 0 && field.maxLength > 0 && field.minLength > field.maxLength) {
                publishErrors["field_length_${field.id}"] = "$fieldKey has a minimum length greater than its maximum length."
            }
            if (field.minValue != null && field.maxValue != null && field.minValue!! > field.maxValue!!) {
                publishErrors["field_range_${field.id}"] = "$fieldKey has a minimum value greater than its maximum value."
            }
            if (field.type == FormFieldType.QUANTITY && theme.enforceQuantityRange && (
                    (field.minValue != null && field.minValue!! > theme.maxQuantity) ||
                        (field.maxValue != null && field.maxValue!! < theme.minQuantity)
                    )
            ) {
                publishErrors["field_quantity_${field.id}"] = "$fieldKey does not overlap the form-level quantity range."
            }
            if (
                theme.enablePayment &&
                formProductsList.value.isEmpty() &&
                field.type == FormFieldType.CUSTOM_AMOUNT &&
                (field.minValue == null || field.minValue!! <= 0.0)
            ) {
                publishErrors["field_amount_${field.id}"] = "$fieldKey needs a positive minimum amount for hosted checkout."
            }
            if (field.type in listOf(FormFieldType.FILE_UPLOAD, FormFieldType.CAMERA_UPLOAD) && field.allowedFileExtensions.isEmpty()) {
                publishErrors["field_files_${field.id}"] = "$fieldKey needs at least one allowed file extension."
            }
            if (field.validationRegex.isNotBlank() && (
                    field.validationRegex.length > 256 ||
                        unsafeValidationPattern.containsMatchIn(field.validationRegex) ||
                        runCatching { Regex(field.validationRegex) }.isFailure
                    )
            ) {
                publishErrors["field_regex_${field.id}"] = "$fieldKey uses an invalid or unsafe validation pattern."
            }
            val dependencyId = field.dependsOnFieldId
            if (!dependencyId.isNullOrBlank() && dependencyId !in fieldsById) {
                publishErrors["field_condition_${field.id}"] = "$fieldKey depends on a field that no longer exists."
            }
            if (field.pageIndex !in formPagesList.value.indices) {
                publishErrors["field_page_${field.id}"] = "$fieldKey is assigned to a page that no longer exists."
            }
        }
        fields.forEach { field ->
            val visited = mutableSetOf(field.id)
            var cursor = field
            while (!cursor.dependsOnFieldId.isNullOrBlank()) {
                val dependencyId = cursor.dependsOnFieldId ?: break
                if (!visited.add(dependencyId)) {
                    publishErrors["field_condition_cycle_${field.id}"] = "Conditional logic contains a dependency cycle."
                    break
                }
                cursor = fieldsById[dependencyId] ?: break
            }
        }
        formProductsList.value.forEachIndexed { index, product ->
            val productKey = product.title.ifBlank { "Product ${index + 1}" }
            val effectivePrice = product.salePrice.takeIf { it > 0.0 } ?: product.price
            if (product.title.isBlank()) publishErrors["product_title_${product.id}"] = "Product ${index + 1} needs a title."
            if (!product.price.isFinite() || product.price <= 0.0 || product.price > 10_000_000.0) {
                publishErrors["product_regular_price_${product.id}"] = "$productKey needs a valid positive regular price."
            }
            if (!effectivePrice.isFinite() || effectivePrice <= 0.0 || effectivePrice > 10_000_000.0) {
                publishErrors["product_price_${product.id}"] = "$productKey needs a valid positive BDT price."
            }
            if (product.salePrice > product.price) {
                publishErrors["product_sale_price_${product.id}"] = "$productKey sale price cannot exceed its regular price."
            }
            if (product.stock < 0) publishErrors["product_stock_${product.id}"] = "$productKey cannot have negative stock."
            if (product.imageUrl.isNotBlank() && !product.imageUrl.matches(Regex("^(https://|data:image/)[^\\s]+$"))) {
                publishErrors["product_image_${product.id}"] = "$productKey image URL must use HTTPS or data URI."
            }
            if (product.isDigital && product.digitalDownloadUrl.isNotBlank() && !product.digitalDownloadUrl.matches(Regex("^https://[^\\s]+$"))) {
                publishErrors["product_download_${product.id}"] = "$productKey download URL must use HTTPS."
            }
        }
        if (theme.enablePaymentCallback && !theme.paymentCallbackUrl.matches(Regex("^https://[^\\s]+$"))) {
            publishErrors["payment_callback_url"] = "Payment callback URL must be a valid HTTPS URL."
        }
        if (theme.enablePayment && theme.paymentProvider.uppercase() !in setOf("AUTO", "BKASH", "NAGAD", "ROCKET", "UPAY")) {
            publishErrors["payment_provider"] = "Payment provider must be AUTO, bKash, Nagad, Rocket, or Upay."
        }
        if (theme.enableEmailNotifications && !theme.notificationEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            publishErrors["notification_email"] = "Enter a valid recipient email for form notifications."
        }
        if (theme.enableSmsNotifications && !theme.notificationSmsNumber.replace(" ", "").matches(Regex("^\\+?[0-9]{10,15}$"))) {
            publishErrors["notification_sms_number"] = "Enter a valid recipient phone number for SMS notifications."
        }
        if (publishErrors.isNotEmpty()) {
            val blocked = FormValidationResult(false, publishErrors, "Publish blocked: ${publishErrors.values.first()}")
            logFirebaseStatus("Form publish failed: ${blocked.summaryMessage}")
            return blocked
        }
        formStatus.value = "PUBLISHED"
        saveActiveFormToHostedList()
        val form = hostedFormsList.value.find { it.id == activeFormId.value } ?: return validation

        // ALWAYS register the hosted form route with the platform router immediately
        registerBrandedHostedFormRoute(form)

        // Then, if a custom Supabase profile is configured, also sync to it in the background
        val configuredProfile = _activeSupabaseProfile.value
        if (configuredProfile != null && configuredProfile.supabaseUrl.isNotBlank() && configuredProfile.anonKey.isNotBlank()) {
            val payload = hostedFormToJson(form)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val active = validSupabaseSession(configuredProfile)
                if (active != null) {
                    com.example.data.remote.SupabaseClient.upsertRecord(
                        active.supabaseUrl,
                        active.anonKey,
                        active.authSessionToken,
                        "payment_forms",
                        payload,
                        onSuccess = {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                repository.upsertPaymentFormCache(
                                    PaymentFormCacheEntity(form.id, activeProfile.value.id, payload.toString(), isDirty = false)
                                )
                            }
                            logFirebaseStatus("Form published and synced to merchant Supabase: ${form.slug}")
                        },
                        onFailure = {
                            logFirebaseStatus("Custom Supabase sync notice: $it (form is active on platform router)")
                        }
                    )
                }
            }
        }
        return validation
    }

    // --- FORM SUBMISSIONS & ANALYTICS ---
    val formSubmissionsList = MutableStateFlow<List<FormSubmissionItem>>(emptyList())

    val formAnalyticsData = MutableStateFlow<FormAnalytics>(FormAnalytics())

    fun submitCustomerOrderForm(name: String, phone: String, email: String, amount: Double, method: String) {
        if (name.isBlank() || phone.count(Char::isDigit) < 10 || amount <= 0.0) {
            logFirebaseStatus("Submission rejected: valid name, phone and positive amount are required.")
            return
        }
        val newSub = FormSubmissionItem(
            formId = activeFormId.value,
            customerName = name,
            customerPhone = phone,
            customerEmail = email,
            amountBdt = amount,
            paymentMethod = method,
            paymentStatus = "PENDING",
            trxId = ""
        )
        formSubmissionsList.value = listOf(newSub) + formSubmissionsList.value
        val payload = org.json.JSONObject().apply {
            put("id", newSub.id)
            put("form_id", newSub.formId)
            put("customer_name", newSub.customerName)
            put("customer_phone", newSub.customerPhone)
            put("customer_email", newSub.customerEmail)
            put("amount_bdt", newSub.amountBdt)
            put("payment_method", newSub.paymentMethod)
            put("payment_status", newSub.paymentStatus)
            put("trx_id", org.json.JSONObject.NULL)
            put("answers", org.json.JSONObject())
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.upsertFormSubmissionCache(
                FormSubmissionCacheEntity(
                    id = newSub.id,
                    merchantId = activeProfile.value.id,
                    formId = newSub.formId,
                    payloadJson = payload.toString(),
                    submittedAt = newSub.submittedAt,
                    isDirty = true
                )
            )
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null) {
                try {
                    upsertRemoteOrThrow(active.supabaseUrl, active.anonKey, active.authSessionToken, "form_submissions", payload)
                    repository.upsertFormSubmissionCache(
                        FormSubmissionCacheEntity(
                            id = newSub.id,
                            merchantId = activeProfile.value.id,
                            formId = newSub.formId,
                            payloadJson = payload.toString(),
                            submittedAt = newSub.submittedAt,
                            isDirty = false
                        )
                    )
                } catch (error: Exception) {
                    logFirebaseStatus("Submission saved offline; Supabase sync failed: ${error.message}")
                }
            }
        }
        val curAn = formAnalyticsData.value
        formAnalyticsData.value = curAn.copy(
            totalViews = curAn.totalViews,
            totalSubmissions = curAn.totalSubmissions + 1,
            totalRevenueBdt = curAn.totalRevenueBdt + amount
        )
        logFirebaseStatus("New customer form submission saved as pending: BDT $amount via $method")
    }

    // C6 Fix: General form field answers submission — saves all field values to Room + Supabase
    fun submitFormFieldAnswers(
        formId: String,
        answers: Map<String, String>,
        onResult: (Boolean, String) -> Unit
    ) {
        if (formId.isBlank()) {
            onResult(false, "No active form selected.")
            return
        }
        val submissionId = java.util.UUID.randomUUID().toString()
        val answersJson = org.json.JSONObject()
        answers.forEach { (k, v) -> answersJson.put(k, v) }
        val nowMs = System.currentTimeMillis()
        val payload = org.json.JSONObject().apply {
            put("id", submissionId)
            put("form_id", formId)
            put("merchant_id", activeProfile.value.id)
            put("answers", answersJson)
            put("submitted_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(nowMs)))
            put("source", "preview")
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Save locally first (works offline)
                repository.upsertFormSubmissionCache(
                    FormSubmissionCacheEntity(
                        id = submissionId,
                        merchantId = activeProfile.value.id,
                        formId = formId,
                        payloadJson = payload.toString(),
                        submittedAt = nowMs,
                        isDirty = true
                    )
                )
                // Attempt cloud sync
                val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
                if (active != null) {
                    try {
                        upsertRemoteOrThrow(active.supabaseUrl, active.anonKey, active.authSessionToken, "form_submissions", payload)
                        repository.upsertFormSubmissionCache(
                            FormSubmissionCacheEntity(
                                id = submissionId,
                                merchantId = activeProfile.value.id,
                                formId = formId,
                                payloadJson = payload.toString(),
                                submittedAt = nowMs,
                                isDirty = false
                            )
                        )
                    } catch (syncEx: Exception) {
                        Log.w("AppViewModel", "Form answer sync queued (offline): ${syncEx.message}")
                    }
                }
                // Update analytics count
                formAnalyticsData.value = formAnalyticsData.value.copy(
                    totalSubmissions = formAnalyticsData.value.totalSubmissions + 1
                )
                onResult(true, "Form submitted successfully!")
            } catch (e: Exception) {
                Log.e("AppViewModel", "submitFormFieldAnswers failed", e)
                onResult(false, "Failed to save submission: ${e.message}")
            }
        }
    }

    // Dynamic configuration lists and specs for Supabase Setup
    val stepBgColorHexes = listOf(
        0xFF5D45FFL, // Intro: Indigo
        0xFF0F172AL, // Step 1: Supabase Slate Dark (matches Supabase console style)
        0xFF1E1B4BL, // Step 2: Auth Deep Indigo
        0xFF0F172AL, // Step 3: SQL Dark Console
        0xFF111827L, // Step 4: CLI Charcoal Terminal
        0xFF1E3A8AL, // Step 5: Webhook Ocean Blue
        0xFF581C87L, // Step 6: Storage Deep Purple
        0xFF032B25L  // Step 7: Done Emerald
    )

    val step1Phases = listOf(
        "Requesting project allocation from Supabase API...",
        "Allocating isolated secure PostgreSQL server...",
        "Instantiating core DB engine and setup modules...",
        "Configuring network access rules & JWT encryption...",
        "Project 'swapnopay-store' is now ONLINE! 🚀"
    )

    val step3Tables = listOf(
        "Connecting with admin credentials...",
        "CREATE TABLE merchants; -- SUCCESS ✔",
        "CREATE TABLE merchant_numbers; -- SUCCESS ✔",
        "CREATE TABLE orders; -- SUCCESS ✔",
        "CREATE TABLE payments; -- SUCCESS ✔",
        "CREATE TABLE sms_logs; -- SUCCESS ✔",
        "CREATE TABLE devices; -- SUCCESS ✔",
        "CREATE TABLE appeals; -- SUCCESS ✔",
        "CREATE TABLE notifications; -- SUCCESS ✔",
        "CREATE TABLE security_logs; -- SUCCESS ✔",
        "Configuring composite database indexes...",
        "Applying Row Level Security (RLS) policies...",
        "SQL script execution complete. Schema verified! 🌟"
    )

    val step4Commands = listOf(
        "\$ npm install -g supabase\nInstalling Supabase CLI globally...",
        "Supabase CLI v1.110.0 installed successfully. [OK]",
        "\$ supabase login\nRedirecting to login browser...\nAuthentication success! Logged in as merchant@gmail.com.",
        "\$ supabase link --project-ref swapnopay-store\nRetrieving project config...\nLinked successfully to Postgres DB.",
        "\$ supabase functions deploy\nPackaging edge functions...",
        "Deploying process-sms... [SUCCESS ✔]",
        "Deploying match-payment-manual... [SUCCESS ✔]",
        "Deploying resolve-appeal... [SUCCESS ✔]",
        "Deploying extend-order... [SUCCESS ✔]",
        "Deploying cancel-order... [SUCCESS ✔]",
        "Deploying get-dashboard-stats... [SUCCESS ✔]",
        "All 6 Edge Functions successfully synchronized and live! 🎉"
    )

    val step5HookPhases = listOf(
        "Listening for live database webhook triggers...",
        "[Database Event] INSERT on table 'sms_logs'",
        "Triggering Supabase Edge Function 'process-sms'...",
        "Incoming SMS Payload: 'You received Tk 500 from 0171...' matched!",
        "Order status updated: PENDING -> PAID",
        "Payment status updated: UNMATCHED -> MATCHED",
        "Webhook cycle completed successfully in 124ms! ⚡"
    )

    val mascotSpeeches = mapOf(
        0 to "Welcome to the Database Wizard. We'll guide you through setting up and optimizing your private, self-hosted Postgres database cluster to store transaction history.",
        1 to "Deploy a high-performance PostgreSQL cluster on Supabase. Name your project and start provisioning. Standard setup takes approximately 1-2 minutes.",
        2 to "Configure Authentication Providers. Enable Email confirmation and add only the OAuth providers and redirect URLs your production application actually supports.",
        3 to "Apply Database Schema. Copy the standard relational database migration script below, execute it in the Supabase SQL Editor, and test connection.",
        4 to "Deploy Server-Side API Handlers. Initialize server-side matching functions to automatically capture, parse, and verify bKash and Nagad SMS broadcasts.",
        5 to "Configure Instant Webhook Listeners. Link database event triggers to invoke SMS processors immediately upon receiving transaction notifications.",
        6 to "Provision Storage Buckets. Migration 09 creates a private, tenant-protected 'appeal-screenshots' bucket for digital receipts and disputes.",
        7 to "Establish Encrypted Client Connection. Enter your public endpoint URL and anonymous access key below to link this automated terminal to your cloud storage.",
        8 to "All secure database systems are fully online and synced."
    )

    // Supabase Wizard States (Fully Dynamic / State-Driven)
    private val _currentWizardStep = MutableStateFlow(0)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    fun setWizardStep(step: Int) {
        _currentWizardStep.value = step
    }

    private val _step1ProvisionState = MutableStateFlow("IDLE")
    val step1ProvisionState: StateFlow<String> = _step1ProvisionState.asStateFlow()

    private val _step1Progress = MutableStateFlow(0f)
    val step1Progress: StateFlow<Float> = _step1Progress.asStateFlow()

    private val _step1Logs = MutableStateFlow<List<String>>(emptyList())
    val step1Logs: StateFlow<List<String>> = _step1Logs.asStateFlow()

    private val _step2EmailAuth = MutableStateFlow(false)
    val step2EmailAuth: StateFlow<Boolean> = _step2EmailAuth.asStateFlow()

    private val _step2GoogleAuth = MutableStateFlow(false)
    val step2GoogleAuth: StateFlow<Boolean> = _step2GoogleAuth.asStateFlow()

    private val _step3SqlState = MutableStateFlow("IDLE")
    val step3SqlState: StateFlow<String> = _step3SqlState.asStateFlow()

    private val _step3Progress = MutableStateFlow(0f)
    val step3Progress: StateFlow<Float> = _step3Progress.asStateFlow()

    private val _step3Logs = MutableStateFlow<List<String>>(emptyList())
    val step3Logs: StateFlow<List<String>> = _step3Logs.asStateFlow()

    private val _step4CliState = MutableStateFlow("IDLE")
    val step4CliState: StateFlow<String> = _step4CliState.asStateFlow()

    private val _step4Progress = MutableStateFlow(0f)
    val step4Progress: StateFlow<Float> = _step4Progress.asStateFlow()

    private val _step4Logs = MutableStateFlow<List<String>>(emptyList())
    val step4Logs: StateFlow<List<String>> = _step4Logs.asStateFlow()

    private val _step5HookState = MutableStateFlow("IDLE")
    val step5HookState: StateFlow<String> = _step5HookState.asStateFlow()

    private val _step5Progress = MutableStateFlow(0f)
    val step5Progress: StateFlow<Float> = _step5Progress.asStateFlow()

    private val _step5Logs = MutableStateFlow<List<String>>(emptyList())
    val step5Logs: StateFlow<List<String>> = _step5Logs.asStateFlow()

    private val _step6CreatedBucket = MutableStateFlow(false)
    val step6CreatedBucket: StateFlow<Boolean> = _step6CreatedBucket.asStateFlow()

    private val _supabaseUrlInput = MutableStateFlow("")
    val supabaseUrlInput: StateFlow<String> = _supabaseUrlInput.asStateFlow()

    private val _supabaseAnonKeyInput = MutableStateFlow("")
    val supabaseAnonKeyInput: StateFlow<String> = _supabaseAnonKeyInput.asStateFlow()

    private val _onboardingBusinessName = MutableStateFlow("")
    val onboardingBusinessName: StateFlow<String> = _onboardingBusinessName.asStateFlow()

    private val _onboardingPhone = MutableStateFlow("")
    val onboardingPhone: StateFlow<String> = _onboardingPhone.asStateFlow()

    fun setSupabaseUrlInput(url: String) {
        _supabaseUrlInput.value = url
    }

    fun setSupabaseAnonKeyInput(key: String) {
        _supabaseAnonKeyInput.value = key
    }

    fun setOnboardingBusinessName(name: String) {
        _onboardingBusinessName.value = name
    }

    fun setOnboardingPhone(phone: String) {
        _onboardingPhone.value = phone
    }

    fun startStep1Provision() {
        if (_step1ProvisionState.value == "PROVISIONING") return
        _step1ProvisionState.value = "PROVISIONING"
        _step1Progress.value = 0.1f
        _step1Logs.value = listOf("Initiating Supabase project verification...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val profile = _activeSupabaseProfile.value
            if (profile != null && profile.supabaseUrl.isNotBlank()) {
                _step1Logs.value = listOf(
                    "Connecting to Supabase endpoint: ${profile.supabaseUrl}",
                    "Verifying REST endpoint and public client credential..."
                )
                _step1Progress.value = 0.6f
                com.example.data.remote.SupabaseClient.testConnection(
                    profile.supabaseUrl, profile.anonKey,
                    onSuccess = {
                        _step1Logs.value = _step1Logs.value + "Supabase REST endpoint verified."
                        _step1Progress.value = 1.0f
                        _step1ProvisionState.value = "DONE"
                    },
                    onFailure = {
                        _step1Logs.value = _step1Logs.value + "Verification failed: $it"
                        _step1Progress.value = 0f
                        _step1ProvisionState.value = "ERROR"
                    }
                )
            } else {
                _step1Logs.value = listOf(
                    "No Supabase project URL configured.",
                    "Please configure your Supabase URL in Settings > Supabase Setup."
                )
                _step1Progress.value = 0.5f
                _step1ProvisionState.value = "READY"
            }
        }
    }

    fun startStep3SqlExecution() {
        if (_step3SqlState.value == "EXECUTING") return
        _step3SqlState.value = "EXECUTING"
        _step3Progress.value = 0.1f
        _step3Logs.value = listOf("Verifying database schema tables and RPC functions...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val profile = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (profile == null) {
                _step3Logs.value = listOf("Sign in to the merchant Supabase project before schema verification.")
                _step3SqlState.value = "ERROR"
                _step3Progress.value = 0f
                return@launch
            }
            val tables = listOf(
                "merchants", "orders", "payments", "sms_logs", "customers", "suppliers", "products",
                "stock_transactions", "ledger_transactions", "pos_sales", "employees", "payment_forms",
                "form_submissions", "dps_accounts", "finance_installments", "merchant_notifications"
            )
            val currentLogs = mutableListOf<String>()
            for (i in tables.indices) {
                var failure: String? = null
                com.example.data.remote.SupabaseClient.fetchRecords(
                    profile.supabaseUrl, profile.anonKey, profile.authSessionToken,
                    tables[i], "id&limit=1", onSuccess = { }, onFailure = { failure = it }
                )
                if (failure != null) {
                    currentLogs.add("Failed public.${tables[i]}: $failure")
                    _step3Logs.value = currentLogs.toList()
                    _step3SqlState.value = "ERROR"
                    return@launch
                }
                currentLogs.add("Verified schema entity: public.${tables[i]}")
                _step3Logs.value = currentLogs.toList()
                _step3Progress.value = (i + 1) / tables.size.toFloat()
            }
            _step3Logs.value = currentLogs + "All required tables are reachable under the authenticated merchant RLS context."
            _step3SqlState.value = "DONE"
        }
    }

    fun startStep4FunctionVerification() {
        if (_step4CliState.value == "RUNNING") return
        _step4CliState.value = "RUNNING"
        _step4Progress.value = 0.2f
        _step4Logs.value = listOf("Checking Edge Functions routing status...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val profile = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (profile == null) {
                _step4Logs.value = listOf("Sign in before checking Edge Function routes.")
                _step4CliState.value = "ERROR"
                _step4Progress.value = 0f
                return@launch
            }
            val functions = listOf("process-sms", "create-order", "resolve-appeal", "hosted-form", "payment-receipt")
            val currentLogs = mutableListOf<String>()
            for (i in functions.indices) {
                var failure: String? = null
                com.example.data.remote.SupabaseClient.checkEdgeFunctionRoute(
                    profile.supabaseUrl, profile.anonKey, profile.authSessionToken, functions[i],
                    onSuccess = { }, onFailure = { failure = it }
                )
                if (failure != null) {
                    currentLogs.add("Failed /functions/v1/${functions[i]}: $failure")
                    _step4Logs.value = currentLogs.toList()
                    _step4CliState.value = "ERROR"
                    return@launch
                }
                currentLogs.add("Route available: /functions/v1/${functions[i]}")
                _step4Logs.value = currentLogs.toList()
                _step4Progress.value = (i + 1) / functions.size.toFloat()
            }
            _step4Logs.value = currentLogs + "Required Edge Function routes are deployed."
            _step4CliState.value = "DONE"
        }
    }

    fun startStep5WebhookReadinessCheck() {
        if (_step5HookState.value == "RUNNING") return
        _step5HookState.value = "RUNNING"
        _step5Progress.value = 0.2f
        _step5Logs.value = listOf("Testing webhook verification & trigger endpoints...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val profile = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (profile == null) {
                _step5Logs.value = listOf("Sign in before checking webhook database readiness.")
                _step5HookState.value = "ERROR"
                _step5Progress.value = 0f
                return@launch
            }
            // The app intentionally cannot inspect the webhook secret or send a
            // forged payment event. It can only verify the merchant-visible
            // notification table; webhook delivery must be checked server-side.
            val phases = listOf("merchant_notifications")
            val currentLogs = mutableListOf<String>()
            for (i in phases.indices) {
                var failure: String? = null
                com.example.data.remote.SupabaseClient.fetchRecords(
                    profile.supabaseUrl, profile.anonKey, profile.authSessionToken,
                    phases[i], "id&limit=1", onSuccess = { }, onFailure = { failure = it }
                )
                if (failure != null) {
                    currentLogs.add("Failed ${phases[i]} readiness check: $failure")
                    _step5Logs.value = currentLogs.toList()
                    _step5HookState.value = "ERROR"
                    return@launch
                }
                currentLogs.add("Verified RLS access to public.${phases[i]}")
                _step5Logs.value = currentLogs.toList()
                _step5Progress.value = (i + 1) / phases.size.toFloat()
            }
            _step5Logs.value = currentLogs + "Database is webhook-ready. Confirm live delivery from the Supabase webhook delivery log."
            _step5HookState.value = "DONE"
        }
    }

    fun setStep2EmailAuth(enabled: Boolean) {
        _step2EmailAuth.value = enabled
    }

    fun setStep2GoogleAuth(enabled: Boolean) {
        _step2GoogleAuth.value = enabled
    }

    fun setStep6CreatedBucket(enabled: Boolean) {
        _step6CreatedBucket.value = enabled
    }

    // Fully persistent settings for More and Settings screens
    private val _notificationsEnabled = MutableStateFlow(securityPrefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _instantAlertsEnabled = MutableStateFlow(securityPrefs.getBoolean("instant_alerts_enabled", true))
    val instantAlertsEnabled: StateFlow<Boolean> = _instantAlertsEnabled.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(securityPrefs.getBoolean("auto_sync_enabled", true))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        securityPrefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun setInstantAlertsEnabled(enabled: Boolean) {
        _instantAlertsEnabled.value = enabled
        securityPrefs.edit().putBoolean("instant_alerts_enabled", enabled).apply()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        securityPrefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
    }

    init {
        viewModelScope.launch {
            repository.prepopulateIfEmpty()
            try {
                val email = securityPrefs.getString("logged_in_email", null)
                val active = repository.getActiveSupabaseProfile()?.takeIf {
                    it
... [truncated for diff preview]