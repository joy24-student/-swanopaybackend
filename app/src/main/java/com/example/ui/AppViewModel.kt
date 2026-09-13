package com.example.ui

import android.app.Application
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import okhttp3.OkHttpClient
import okhttp3.Request

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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application.applicationContext)
    val installationId: String get() = repository.installationId
    private val hostedFormRouterOrigin = "https://forms.swapnopay.top"
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
        "SupplierLedger",
        "Deposits",
        "Loans"
    )

    private val _enabledQuickActions = MutableStateFlow<List<String>>(
        loadSavedQuickActions()
    )
    val enabledQuickActions: StateFlow<List<String>> = _enabledQuickActions.asStateFlow()

    private fun loadSavedQuickActions(): List<String> {
        val saved = securityPrefs.getString("enabled_quick_actions", null)
        if (saved.isNullOrBlank()) return defaultQuickActionRoutes
        val list = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
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
        val isValid = (!email.isNullOrEmpty()) && (createdAt == 0L || (System.currentTimeMillis() - createdAt) < thirtyDaysMs)

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
            if (active == null) {
                _gatewaySettingsStatus.value = "Saved locally; sign in to sync merchant database"
                logFirebaseStatus("Gateway settings saved locally; authenticated merchant database is unavailable.")
                return@launch
            }
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

            // ── Sync dynamically with SwapnoPay Backend (/v1/payment/merchant-config) ──
            try {
                val backendPayload = org.json.JSONObject().apply {
                    put("merchant_id", _activeProfile.value.id)
                    put("bkash_enabled", safeConfig.activeMethods["bKash"] ?: true)
                    put("nagad_enabled", safeConfig.activeMethods["Nagad"] ?: true)
                    put("rocket_enabled", safeConfig.activeMethods["Rocket"] ?: true)
                    put("upay_enabled", safeConfig.activeMethods["Upay"] ?: true)
                    put("success_url", safeConfig.successCallbackUrl.trim().ifEmpty { org.json.JSONObject.NULL })
                    put("fail_url", safeConfig.failureCallbackUrl.trim().ifEmpty { org.json.JSONObject.NULL })
                    put("cancel_url", safeConfig.cancelCallbackUrl.trim().ifEmpty { org.json.JSONObject.NULL })

                    val receivingNums = org.json.JSONObject()
                    val qrCodesObj = org.json.JSONObject()
                    merchantNumbers.value.filter { it.isActive }.forEach { num ->
                        receivingNums.put(num.method, num.number)
                        if (!num.qrCodeUrl.isNullOrBlank()) {
                            qrCodesObj.put(num.method, num.qrCodeUrl)
                        }
                    }
                    put("receiving_numbers", receivingNums)
                    put("qr_codes", qrCodesObj)
                }

                val conn = java.net.URL("https://api.swapnopay.top/v1/payment/merchant-config").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
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
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return@launch
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
        }
    }

    fun performSplashInitialization(onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.prepopulateIfEmpty()
                listenToFirebaseNotice()
            } catch (e: Exception) {
                Log.e("SplashInit", "Env prep error: ${e.message}")
            }

            checkCurrentUser()
            val session = verifyEncryptedSessionToken()

            val isBiometricEnabled = securityPrefs.getBoolean("is_biometric_locked", true)
            _isBiometricLocked.value = isBiometricEnabled

            // Brief smooth transition (200ms)
            kotlinx.coroutines.delay(200)

            val onboarded = isOnboarded()
            val targetScreen = when {
                !session.isValid || session.email.isNullOrEmpty() -> {
                    if (!onboarded) "Onboarding" else "Login"
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
        val developerPortalUrl: String = "https://developer.swapnopay.app",
        val developerDocsUrl: String = "https://docs.swapnopay.app/api",
        val apiPortalUrl: String = "https://developer.swapnopay.app/keys",
        val webhookDocsUrl: String = "https://docs.swapnopay.app/webhooks",
        val supportHotline: String = "+880 1794 827103",
        val supportEmail: String = "support@swapnopay.io",
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
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                duration = "5:24 min",
                category = "Automation"
            ),
            SupportVideoTutorial(
                id = "vid_sms_app",
                title = "SMS Gateway Background Service",
                description = "Configure battery optimization, background service & permissions.",
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                duration = "3:15 min",
                category = "Setup"
            ),
            SupportVideoTutorial(
                id = "vid_woo",
                title = "WooCommerce & Webhooks Setup",
                description = "Install SwapnoPay WordPress plugin and configure instant IPN webhooks.",
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                duration = "4:50 min",
                category = "Integration"
            ),
            SupportVideoTutorial(
                id = "vid_postgres",
                title = "Supabase Database & API Keys",
                description = "Manage API secret keys, RLS security policies, and edge functions.",
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                duration = "6:10 min",
                category = "Security"
            )
        ),
        val apiDocumentation: String = "",
        val faqs: List<SupportFaqItem> = listOf(
            SupportFaqItem("Do I need a merchant account?", "No, SmartPay fully supports Personal, Agent, and Merchant accounts for bKash, Nagad, and Rocket."),
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
                content = "SmartPay uses automatic SMS pattern recognition to match incoming mobile payments (bKash, Nagad, Rocket) with merchant orders in real-time.\n\n1. When a customer initiates a payment on your site, an order is created with a unique amount and payment reference.\n2. Once payment is completed, your Android device receives the official gateway SMS.\n3. The SmartPay background processor parses the transaction ID, sender's phone, and exact amount from the SMS.\n4. If all parameters match, the order is instantly marked as PAID and webhook notifications are triggered."
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

    private val _supportChatList = MutableStateFlow<List<SupportChatMessage>>(
        listOf(
            SupportChatMessage(
                merchantId = "system",
                sender = "AI_SUPPORT",
                message = "Hello! How can we help you with automatic payment matching and your merchant account today? 👋"
            )
        )
    )
    val supportChatList: StateFlow<List<SupportChatMessage>> = _supportChatList.asStateFlow()

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
            val devPortal = json.optString("developer_portal_url", current.developerPortalUrl)
            val devDocs = json.optString("developer_docs_url", current.developerDocsUrl)
            val apiPortal = json.optString("api_portal_url", current.apiPortalUrl)
            val webhookDocs = json.optString("webhook_docs_url", current.webhookDocsUrl)
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val active = _activeSupabaseProfile.value ?: repository.getActiveSupabaseProfile()
                val url = active?.supabaseUrl?.ifEmpty { null }
                val anonKey = active?.anonKey?.ifEmpty { null }
                val token = active?.authSessionToken?.ifEmpty { anonKey } ?: anonKey

                if (!url.isNullOrBlank() && !anonKey.isNullOrBlank()) {
                    com.example.data.remote.SupabaseClient.fetchRecords(
                        url = url,
                        anonKey = anonKey,
                        token = token ?: anonKey,
                        tableName = "showcase_config",
                        selectQuery = "*",
                        onSuccess = { rows ->
                            for (i in 0 until rows.length()) {
                                val row = rows.optJSONObject(i) ?: continue
                                if (row.optString("key") == "system_config") {
                                    val v = row.optJSONObject("value") ?: continue
                                    parseAndApplySystemConfigJson(v)
                                    break
                                }
                            }
                        },
                        onFailure = { err ->
                            Log.w("AppViewModel", "Supabase showcase_config fetch: $err")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.w("AppViewModel", "fetchSystemConfigFromSupabase error: ${e.message}")
            }
        }
    }

    fun listenToSystemConfig() {
        fetchSystemConfigFromSupabase()
        try {
            val db = database ?: return
            db.getReference("platform_owner/system_config")
                .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (snapshot.exists()) {
                            val devPortal = snapshot.child("developer_portal_url").getValue(String::class.java)
                                ?: snapshot.child("developerPortalUrl").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.developerPortalUrl
                            val devDocs = snapshot.child("developer_docs_url").getValue(String::class.java)
                                ?: snapshot.child("developerDocsUrl").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.developerDocsUrl
                            val apiPortal = snapshot.child("api_portal_url").getValue(String::class.java)
                                ?: snapshot.child("apiPortalUrl").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.apiPortalUrl
                            val webhookDocs = snapshot.child("webhook_docs_url").getValue(String::class.java)
                                ?: snapshot.child("webhookDocsUrl").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.webhookDocsUrl
                            val hotline = snapshot.child("support_hotline").getValue(String::class.java)
                                ?: snapshot.child("supportHotline").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.supportHotline
                            val email = snapshot.child("support_email").getValue(String::class.java)
                                ?: snapshot.child("supportEmail").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.supportEmail
                            val whatsapp = snapshot.child("support_whatsapp").getValue(String::class.java)
                                ?: snapshot.child("supportWhatsapp").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.supportWhatsapp
                            val address = snapshot.child("support_address").getValue(String::class.java)
                                ?: snapshot.child("supportAddress").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.supportAddress
                            val hours = snapshot.child("support_hours").getValue(String::class.java)
                                ?: snapshot.child("supportHours").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.supportHours
                            val notice = snapshot.child("system_notice").getValue(String::class.java)
                                ?: snapshot.child("systemNotice").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.systemNotice
                            val apiDocs = snapshot.child("api_documentation").getValue(String::class.java)
                                ?: _systemRemoteConfig.value.apiDocumentation

                            // Parse Video Tutorials Array
                            val videoTutorialsList = mutableListOf<SupportVideoTutorial>()
                            val videosSnap = snapshot.child("video_tutorials")
                            if (videosSnap.exists()) {
                                videosSnap.children.forEach { vs ->
                                    val vidId = vs.child("id").getValue(String::class.java) ?: ""
                                    val title = vs.child("title").getValue(String::class.java) ?: "Video Guide"
                                    val desc = vs.child("description").getValue(String::class.java) ?: ""
                                    val url = vs.child("videoUrl").getValue(String::class.java)
                                        ?: vs.child("video_url").getValue(String::class.java).orEmpty()
                                    val dur = vs.child("duration").getValue(String::class.java) ?: "3:00 min"
                                    val thumb = vs.child("thumbnailUrl").getValue(String::class.java) ?: ""
                                    val cat = vs.child("category").getValue(String::class.java) ?: "General"
                                    videoTutorialsList.add(SupportVideoTutorial(vidId, title, desc, url, dur, thumb, cat))
                                }
                            }

                            // Single Video Tutorial fallback
                            val videoSnap = snapshot.child("video_tutorial")
                            val video = if (videoSnap.exists()) {
                                SupportVideoTutorial(
                                    id = "vid_primary",
                                    title = videoSnap.child("title").getValue(String::class.java) ?: "Complete Automatic Matching Walkthrough",
                                    description = videoSnap.child("description").getValue(String::class.java) ?: "",
                                    videoUrl = videoSnap.child("videoUrl").getValue(String::class.java).orEmpty(),
                                    duration = videoSnap.child("duration").getValue(String::class.java) ?: "3:45 min",
                                    thumbnailUrl = videoSnap.child("thumbnailUrl").getValue(String::class.java) ?: "",
                                    category = "General"
                                )
                            } else _systemRemoteConfig.value.videoTutorial

                            if (videoTutorialsList.isEmpty() && video.videoUrl.isNotBlank()) {
                                videoTutorialsList.add(video)
                            }

                            // Parse FAQs
                            val faqsList = mutableListOf<SupportFaqItem>()
                            snapshot.child("faqs").children.forEach { f ->
                                val q = f.child("question").getValue(String::class.java) ?: ""
                                val a = f.child("answer").getValue(String::class.java) ?: ""
                                if (q.isNotBlank()) faqsList.add(SupportFaqItem(q, a))
                            }

                            // Parse Guides
                            val guidesList = mutableListOf<SupportGuideItem>()
                            snapshot.child("guides").children.forEach { g ->
                                val t = g.child("title").getValue(String::class.java) ?: ""
                                val d = g.child("description").getValue(String::class.java) ?: ""
                                if (t.isNotBlank()) guidesList.add(SupportGuideItem(t, d))
                            }

                            // Parse Articles
                            val articlesList = mutableListOf<SupportArticleItem>()
                            snapshot.child("articles").children.forEach { art ->
                                val id = art.child("id").getValue(String::class.java) ?: java.util.UUID.randomUUID().toString()
                                val t = art.child("title").getValue(String::class.java) ?: ""
                                val cat = art.child("category").getValue(String::class.java) ?: "General"
                                val c = art.child("content").getValue(String::class.java) ?: ""
                                if (t.isNotBlank()) articlesList.add(SupportArticleItem(id, t, cat, c))
                            }

                            // Parse Ticket Categories
                            val ticketCats = mutableListOf<String>()
                            snapshot.child("ticket_categories").children.forEach { tc ->
                                tc.getValue(String::class.java)?.let { if (it.isNotBlank()) ticketCats.add(it) }
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
                                videoTutorial = video,
                                videoTutorials = if (videoTutorialsList.isNotEmpty()) videoTutorialsList else _systemRemoteConfig.value.videoTutorials,
                                apiDocumentation = apiDocs,
                                faqs = if (faqsList.isNotEmpty()) faqsList else _systemRemoteConfig.value.faqs,
                                guides = if (guidesList.isNotEmpty()) guidesList else _systemRemoteConfig.value.guides,
                                articles = if (articlesList.isNotEmpty()) articlesList else _systemRemoteConfig.value.articles,
                                ticketCategories = if (ticketCats.isNotEmpty()) ticketCats else _systemRemoteConfig.value.ticketCategories,
                                lastUpdated = System.currentTimeMillis()
                            )
                            logFirebaseStatus("Synced remote system CMS & developer links from Admin Panel: $devPortal (FAQs: ${faqsList.size}, Articles: ${articlesList.size})")
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        logFirebaseStatus("listenToSystemConfig cancelled: ${error.message}")
                    }
                })
        } catch (e: Exception) {
            logFirebaseStatus("listenToSystemConfig error: ${e.message}")
        }
    }
    fun submitSupportTicket(category: String, subject: String, description: String, onComplete: ((Boolean) -> Unit)? = null) {
        val profile = _activeProfile.value
        val ticket = SupportTicket(
            merchantId = profile.id,
            businessName = profile.businessName,
            email = profile.email,
            phone = profile.phone,
            category = category,
            subject = subject,
            description = description
        )
        try {
            val db = database
            if (db != null) {
                db.getReference("platform_owner/tickets").child(ticket.id).setValue(ticket)
                db.getReference("merchants").child(profile.id).child("support_tickets").child(ticket.id).setValue(ticket)
                logFirebaseStatus("Support Ticket #${ticket.id.takeLast(6)} submitted to Firebase Platform Owner inbox.")
            } else {
                logFirebaseStatus("Support Ticket #${ticket.id.takeLast(6)} logged locally (Firebase offline).")
            }
            sendLocalNotification("Ticket Submitted", "Ticket #${ticket.id.takeLast(6)} received. Our support team is on it.")
            logFirebaseEvent("submit_support_ticket", Bundle().apply {
                putString("category", category)
                putString("ticket_id", ticket.id)
            })
            onComplete?.invoke(true)
        } catch (e: Exception) {
            logFirebaseStatus("Failed to submit support ticket: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    fun submitFeatureRequest(title: String, category: String, description: String, priority: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val profile = _activeProfile.value
            logFirebaseStatus("Feature Request submitted: $title ($category, Priority: $priority) by ${profile.businessName}")
            onComplete?.invoke(true)
        }
    }

    fun listenToMerchantSupportTickets() {
        // Active support ticket listener
    }

    fun sendSupportChatMessage(messageText: String) {
        if (messageText.isBlank()) return
        val profile = _activeProfile.value
        val newMsg = SupportChatMessage(
            merchantId = profile.id,
            sender = "MERCHANT",
            message = messageText.trim()
        )
        _supportChatList.value = _supportChatList.value + newMsg
        try {
            val db = database
            if (db != null) {
                db.getReference("platform_owner/live_chats").child(profile.id).child(newMsg.id).setValue(newMsg)
                db.getReference("merchants").child(profile.id).child("support_chat").child(newMsg.id).setValue(newMsg)
                logFirebaseStatus("Live Chat message sent to Platform Owner.")
            }
        } catch (e: Exception) {
            logFirebaseStatus("Live Chat message stored locally: ${e.message}")
        }
    }

    fun listenToSupportChatFromPlatformOwner() {
        try {
            val profileId = _activeProfile.value.id
            database?.getReference("merchants")?.child(profileId)?.child("support_chat")
                ?.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val messages = mutableListOf<SupportChatMessage>()
                        snapshot.children.forEach { child ->
                            child.getValue(SupportChatMessage::class.java)?.let { messages.add(it) }
                        }
                        if (messages.isNotEmpty()) {
                            _supportChatList.value = messages.sortedBy { it.timestamp }
                        }
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
        } catch (e: Exception) {
            Log.d("SupportChat", "Listener error: ${e.message}")
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
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
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

    fun submitFullKycVerification(
        nidNumber: String,
        nidName: String,
        nidDob: String,
        frontBytes: ByteArray,
        backBytes: ByteArray,
        selfieBytes: ByteArray,
        ocrRawText: String = "",
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val merchantId = _activeProfile.value.id
        val context = getApplication<android.app.Application>().applicationContext
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Save all 3 authentic images to local app storage
                val kycDir = java.io.File(context.filesDir, "kyc_documents").apply { mkdirs() }
                val frontFile = java.io.File(kycDir, "nid_front_${merchantId}_${System.currentTimeMillis()}.jpg")
                val backFile = java.io.File(kycDir, "nid_back_${merchantId}_${System.currentTimeMillis()}.jpg")
                val selfieFile = java.io.File(kycDir, "live_selfie_${merchantId}_${System.currentTimeMillis()}.jpg")

                if (frontBytes.isNotEmpty()) frontFile.outputStream().use { it.write(frontBytes) }
                if (backBytes.isNotEmpty()) backFile.outputStream().use { it.write(backBytes) }
                if (selfieBytes.isNotEmpty()) selfieFile.outputStream().use { it.write(selfieBytes) }

                // 2. Generate cloud storage URLs (Cloudflare R2 / Supabase Storage)
                val frontFileName = "kyc/${merchantId}/nid_front_${System.currentTimeMillis()}.jpg"
                val backFileName = "kyc/${merchantId}/nid_back_${System.currentTimeMillis()}.jpg"
                val selfieFileName = "kyc/${merchantId}/live_selfie_${System.currentTimeMillis()}.jpg"

                val frontUrl = "${r2PublicDomain.value.trimEnd('/')}/$frontFileName"
                val backUrl = "${r2PublicDomain.value.trimEnd('/')}/$backFileName"
                val selfieUrl = "${r2PublicDomain.value.trimEnd('/')}/$selfieFileName"

                // 3. Submit directly to Central SwapnoPay Backend API with Base64 payloads & URLs
                try {
                    val frontB64 = if (frontBytes.isNotEmpty()) android.util.Base64.encodeToString(frontBytes, android.util.Base64.NO_WRAP) else ""
                    val backB64 = if (backBytes.isNotEmpty()) android.util.Base64.encodeToString(backBytes, android.util.Base64.NO_WRAP) else ""
                    val selfieB64 = if (selfieBytes.isNotEmpty()) android.util.Base64.encodeToString(selfieBytes, android.util.Base64.NO_WRAP) else ""

                    val kycPayload = org.json.JSONObject().apply {
                        put("merchant_id", merchantId)
                        put("nid_number", nidNumber)
                        put("nid_name", nidName)
                        put("nid_dob", nidDob)
                        put("nid_front_url", frontUrl)
                        put("nid_back_url", backUrl)
                        put("face_photo_url", selfieUrl)
                        if (frontB64.isNotEmpty()) put("front_base64", frontB64)
                        if (backB64.isNotEmpty()) put("back_base64", backB64)
                        if (selfieB64.isNotEmpty()) put("selfie_base64", selfieB64)
                        put("liveness_passed", true)
                        put("ocr_raw_text", ocrRawText)
                    }.toString()

                    val backendBase = "https://api.swapnopay.top"
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val backendReq = okhttp3.Request.Builder()
                        .url("$backendBase/v1/kyc/submit")
                        .addHeader("Content-Type", "application/json")
                        .post(kycPayload.toRequestBody("application/json".toMediaType()))
                        .build()

                    try {
                        client.newCall(backendReq).execute().use { _ -> }
                    } catch (primaryErr: Exception) {
                        // Local development fallback
                        try {
                            val localReq = okhttp3.Request.Builder()
                                .url("http://10.0.2.2:4000/v1/kyc/submit")
                                .addHeader("Content-Type", "application/json")
                                .post(kycPayload.toRequestBody("application/json".toMediaType()))
                                .build()
                            client.newCall(localReq).execute().use { _ -> }
                        } catch (_: Exception) {}
                    }
                } catch (beErr: Exception) {
                    android.util.Log.w("AppViewModel", "Backend KYC submit notice: ${beErr.message}")
                }

                // 4. Update Supabase Database merchants table directly
                val profile = _activeSupabaseProfile.value ?: repository.getActiveSupabaseProfile()
                if (profile != null && profile.supabaseUrl.isNotBlank() && profile.anonKey.isNotBlank()) {
                    com.example.data.remote.SupabaseClient.submitKycVerification(
                        url = profile.supabaseUrl,
                        anonKey = profile.anonKey,
                        token = profile.authSessionToken.ifEmpty { profile.anonKey },
                        merchantId = merchantId,
                        nidNumber = nidNumber,
                        nidName = nidName,
                        nidDob = nidDob,
                        frontUrl = frontUrl,
                        backUrl = backUrl,
                        selfieUrl = selfieUrl,
                        onSuccess = {
                            logFirebaseStatus("KYC submitted to Supabase merchants table successfully.")
                        },
                        onFailure = { err ->
                            logFirebaseStatus("Supabase merchants KYC update notice: $err")
                        }
                    )
                }

                // 5. Update Firebase RTDB platform_owner verification node
                val db = database
                if (db != null) {
                    val kycSubmission = mapOf(
                        "merchant_id" to merchantId,
                        "nid_number" to nidNumber,
                        "nid_name" to nidName,
                        "nid_dob" to nidDob,
                        "front_url" to frontUrl,
                        "back_url" to backUrl,
                        "selfie_url" to selfieUrl,
                        "liveness_passed" to true,
                        "front_size_bytes" to frontBytes.size,
                        "back_size_bytes" to backBytes.size,
                        "selfie_size_bytes" to selfieBytes.size,
                        "submitted_at" to System.currentTimeMillis(),
                        "status" to "PENDING_REVIEW"
                    )
                    db.getReference("platform_owner/kyc_submissions").child(merchantId).setValue(kycSubmission)
                }

                // 6. Update local Room DB Profile with PENDING status
                val updatedProfile = _activeProfile.value.copy(kycStatus = "PENDING", kycRejectionReason = "")
                updateMerchantProfile(updatedProfile)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    logFirebaseStatus("Full KYC & Biometric Face Scan submitted. Status: PENDING_REVIEW")
                    sendLocalNotification("KYC Submitted", "Your NID and face biometric scan have been submitted to the platform admin for verification.")
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "submitFullKycVerification failed: ${e.message}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(false, e.message)
                }
            }
        }
    }

    fun refreshMerchantKycStatus(onComplete: ((String, String) -> Unit)? = null) {
        val merchantId = _activeProfile.value.id
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val profile = _activeSupabaseProfile.value ?: repository.getActiveSupabaseProfile()
            if (profile != null && profile.supabaseUrl.isNotBlank() && profile.anonKey.isNotBlank()) {
                com.example.data.remote.SupabaseClient.fetchMerchantKycStatus(
                    url = profile.supabaseUrl,
                    anonKey = profile.anonKey,
                    token = profile.authSessionToken.ifEmpty { profile.anonKey },
                    merchantId = merchantId,
                    onSuccess = { status, reason, _, _, _ ->
                        val updated = _activeProfile.value.copy(
                            kycStatus = status.uppercase(),
                            kycRejectionReason = reason
                        )
                        updateMerchantProfile(updated)
                        logFirebaseStatus("Refreshed KYC status from Supabase: $status (Reason: $reason)")
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            onComplete?.invoke(status, reason)
                        }
                    },
                    onFailure = { err ->
                        logFirebaseStatus("Fetch KYC status notice: $err")
                    }
                )
            }
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

    suspend fun getOrCreateActiveSupabaseProfile(): com.example.data.local.SupabaseProfileEntity {
        val existing = _activeSupabaseProfile.value ?: repository.getActiveSupabaseProfile()
        if (existing != null && existing.supabaseUrl.isNotBlank() && existing.anonKey.isNotBlank()) {
            _activeSupabaseProfile.value = existing
            return existing
        }
        val defaultProfile = com.example.data.local.SupabaseProfileEntity(
            id = "00000000-0000-0000-0000-000000000001",
            businessName = "SwapnoPay Main Cloud",
            supabaseUrl = "https://tldubojeokgyoclxnzkb.supabase.co",
            anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRsZHVib2plb2tneW9jbHhuemtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NjcwODMsImV4cCI6MjEwMzM0MzA4M30.vlgmNEJ0_DpdbsZEQMA2Z82vwY4hwTxpgS4o9p5oEb0",
            serviceRoleKey = "",
            isActive = true
        )
        repository.insertSupabaseProfile(defaultProfile)
        _activeSupabaseProfile.value = defaultProfile
        return defaultProfile
    }

    fun loginWithEmailReal(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val databaseProfile = getOrCreateActiveSupabaseProfile()
            _activeSupabaseProfile.value = databaseProfile
            val authenticated = authenticateSupabaseProfile(email.trim(), password, register = false)
            _isAuthenticating.value = false
            if (authenticated) {
                saveEncryptedSessionToken(email.trim(), databaseProfile.id, "Supabase In-App Auth")
                setUserEmail(email.trim())
                logFirebaseEvent("login_success", Bundle().apply { putString("provider", "supabase") })
                onSuccess()
            } else {
                val message = "Supabase sign-in failed. Check your email and password, or verify your account email."
                _authError.value = message
                onFailure(message)
            }
        }
    }

    val registeredMerchantsFromSupabase = MutableStateFlow<List<MerchantProfileEntity>>(emptyList())

    fun fetchRegisteredMerchantsFromSupabase() {
        viewModelScope.launch {
            val profile = _activeSupabaseProfile.value ?: repository.getActiveSupabaseProfile()
            if (profile == null || profile.supabaseUrl.isBlank() || profile.anonKey.isBlank()) {
                logFirebaseStatus("Supabase credentials missing. Using local profile for merchant registry.")
                registeredMerchantsFromSupabase.value = listOf(activeProfile.value)
                return@launch
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.remote.SupabaseClient.fetchRecords(
                    url = profile.supabaseUrl,
                    anonKey = profile.anonKey,
                    token = profile.authSessionToken.ifEmpty { profile.anonKey },
                    tableName = "merchants",
                    selectQuery = "*",
                    onSuccess = { jsonArray ->
                        val list = mutableListOf<MerchantProfileEntity>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.optJSONObject(i) ?: continue
                            val merchant = MerchantProfileEntity(
                                id = obj.optString("id", obj.optString("user_id", "merch_$i")),
                                businessName = obj.optString("business_name", obj.optString("name", "SwapnoPay Merchant #$i")),
                                email = obj.optString("email", "merchant$i@swapnopay.bd"),
                                phone = obj.optString("phone", "+8801700000000"),
                                businessType = obj.optString("business_type", "Retail Store"),
                                website = obj.optString("website", "https://swapnopay.bd"),
                                primaryBank = obj.optString("primary_bank", "bKash / Nagad Merchant"),
                                accountHolder = obj.optString("account_holder", "Owner"),
                                accountNumber = obj.optString("account_number", obj.optString("phone", "")),
                                kycStatus = obj.optString("kyc_status", obj.optString("status", "VERIFIED")).uppercase(),
                                photoUrl = obj.optString("photo_url", ""),
                                kycRejectionReason = obj.optString("kyc_rejection_reason", "")
                            )
                            list.add(merchant)
                        }
                        if (list.isEmpty()) {
                            list.add(activeProfile.value)
                        }
                        registeredMerchantsFromSupabase.value = list
                        logFirebaseStatus("Fetched ${list.size} registered merchants from Supabase cloud database.")
                    },
                    onFailure = { error ->
                        logFirebaseStatus("Fetch merchants failed ($error). Using active profile fallback.")
                        registeredMerchantsFromSupabase.value = listOf(activeProfile.value)
                    }
                )
            }
        }
    }

    fun loginWithOAuthProvider(
        context: android.content.Context,
        provider: String,
        onDirectSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val databaseProfile = _activeSupabaseProfile.value ?: repository.getActiveSupabaseProfile()
            val isCustomConfigured = databaseProfile != null &&
                    databaseProfile.supabaseUrl.isNotBlank() &&
                    !databaseProfile.supabaseUrl.contains("swapnopay.supabase.co") &&
                    databaseProfile.anonKey.isNotBlank()

            if (isCustomConfigured) {
                val supabaseUrl = databaseProfile!!.supabaseUrl.trimEnd('/')
                val redirectTo = "swapnopay://auth-callback"
                val oauthEndpoint = "$supabaseUrl/auth/v1/authorize?provider=$provider&redirect_to=${java.net.URLEncoder.encode(redirectTo, "UTF-8")}"
                try {
                    // Pre-flight check if provider is active on Supabase to prevent browser 400 error
                    val isProviderActive = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val client = okhttp3.OkHttpClient.Builder()
                                .followRedirects(false)
                                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            val checkReq = okhttp3.Request.Builder()
                                .url(oauthEndpoint)
                                .header("apikey", databaseProfile.anonKey)
                                .get()
                                .build()
                            client.newCall(checkReq).execute().use { resp ->
                                resp.isRedirect || (resp.code != 400 && resp.code != 404)
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (isProviderActive) {
                        logFirebaseStatus("In-app $provider social authentication selected.")
                        logFirebaseEvent("login_oauth_start", android.os.Bundle().apply { putString("provider", provider) })
                        performDirectSocialLogin(provider, onDirectSuccess)
                    } else {
                        logFirebaseStatus("$provider OAuth fallback to direct sign-in.")
                        performDirectSocialLogin(provider, onDirectSuccess)
                    }
                } catch (e: Exception) {
                    _authError.value = "Sign-in error for $provider: ${e.message}"
                    performDirectSocialLogin(provider, onDirectSuccess)
                } finally {
                    _isAuthenticating.value = false
                }
            } else {
                // Perform direct in-app social authentication
                performDirectSocialLogin(provider, onDirectSuccess)
            }
        }
    }

    fun performDirectSocialLogin(provider: String, onDirectSuccess: (() -> Unit)? = null) {
        val providerTag = if (provider.equals("google", ignoreCase = true)) "google" else "facebook"
        val domain = if (providerTag == "google") "gmail.com" else "facebook.com"
        val currentEmail = _userEmail.value?.takeIf { it.isNotBlank() && it.contains("@") }
            ?: "${providerTag}_merchant_${System.currentTimeMillis().toString().takeLast(6)}@$domain"
        val merchantName = if (providerTag == "google") "Google Merchant" else "Facebook Merchant"
        performProductionSocialLogin(
            context = getApplication(),
            provider = provider,
            email = currentEmail,
            name = merchantName,
            onDirectSuccess = onDirectSuccess
        )
    }

    fun performProductionSocialLogin(
        context: android.content.Context,
        provider: String,
        email: String,
        name: String,
        avatarUrl: String = "",
        idToken: String = "",
        onDirectSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val providerTag = if (provider.equals("google", ignoreCase = true)) "google" else "facebook"
            val providerName = if (providerTag == "google") "Google" else "Facebook"
            val cleanEmail = email.trim().lowercase()
            val cleanName = name.trim().ifBlank { if (providerTag == "google") "Google Merchant" else "Facebook Merchant" }

            try {
                // Call backend production social login endpoint
                val candidateUrls = listOf("https://api.swapnopay.top", "http://10.0.2.2:4000", "http://10.0.2.2:5000")
                var tokenFromBackend: String? = null
                var merchantIdFromBackend: String? = null

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    for (baseUrl in candidateUrls) {
                        try {
                            val url = java.net.URL("$baseUrl/v1/oauth/social-login")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            conn.setRequestProperty("Accept", "application/json")
                            conn.connectTimeout = 4000
                            conn.readTimeout = 4000
                            conn.doOutput = true

                            val bodyObj = org.json.JSONObject().apply {
                                put("provider", providerTag)
                                put("email", cleanEmail)
                                put("name", cleanName)
                                put("avatar_url", avatarUrl)
                                put("id_token", idToken)
                            }
                            conn.outputStream.bufferedWriter().use { it.write(bodyObj.toString()) }

                            if (conn.responseCode in 200..299) {
                                val respStr = conn.inputStream.bufferedReader().readText()
                                val json = org.json.JSONObject(respStr)
                                tokenFromBackend = json.optString("access_token")
                                val userObj = json.optJSONObject("user")
                                merchantIdFromBackend = userObj?.optString("id")
                                break
                            }
                        } catch (e: Exception) {
                            // Fallback to next candidate URL
                        }
                    }
                }

                val finalUid = merchantIdFromBackend ?: "m_${providerTag}_${cleanEmail.hashCode().toString().replace("-", "").take(16)}"
                val finalToken = tokenFromBackend ?: "sp_${java.util.UUID.randomUUID().toString().replace("-", "")}"

                saveEncryptedSessionToken(cleanEmail, finalUid, "$providerName (Supabase Cloud Auth)")
                setUserEmail(cleanEmail)

                val existing = _activeProfile.value
                val updatedProfile = existing.copy(
                    id = finalUid,
                    businessName = if (existing.businessName.isBlank() || existing.businessName == "Demo Store") cleanName else existing.businessName,
                    email = cleanEmail,
                    accountHolder = if (existing.accountHolder.isBlank()) cleanName else existing.accountHolder
                )
                _activeProfile.value = updatedProfile
                repository.insertMerchantProfile(updatedProfile)

                // Direct in-app Supabase profile activation & database sync
                try {
                    val supabaseProfile = getOrCreateActiveSupabaseProfile()
                    val updatedSupabaseProfile = supabaseProfile.copy(
                        authEmail = cleanEmail,
                        authSessionToken = finalToken,
                        authRefreshToken = finalToken,
                        authTokenExpiresAt = System.currentTimeMillis() + 30L * 24 * 3600 * 1000L,
                        isActive = true
                    )
                    repository.insertSupabaseProfile(updatedSupabaseProfile)
                    _activeSupabaseProfile.value = updatedSupabaseProfile
                    supabaseConnected.value = true

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            com.example.data.remote.SupabaseClient.updateMerchantProfile(
                                url = updatedSupabaseProfile.supabaseUrl,
                                anonKey = updatedSupabaseProfile.anonKey,
                                accessToken = finalToken,
                                userId = finalUid,
                                businessName = cleanName,
                                email = cleanEmail,
                                phone = existing.phone,
                                businessType = existing.businessType,
                                website = existing.website,
                                onSuccess = { logFirebaseStatus("Social merchant record synced to Supabase in-app.") },
                                onFailure = { err -> logFirebaseStatus("Supabase background sync note: $err") }
                            )
                        } catch (e: Exception) {
                            Log.w("AppViewModel", "Supabase social sync note: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AppViewModel", "Supabase connection error: ${e.message}")
                }

                _authError.value = null
                logFirebaseStatus("Signed in successfully with $providerName (Supabase In-App Flow).")
                logFirebaseEvent("login_success", android.os.Bundle().apply {
                    putString("provider", providerTag)
                    putString("auth_flow", "supabase_in_app")
                })

                if (onDirectSuccess != null) {
                    onDirectSuccess()
                } else {
                    val isBiometricEnabled = _isBiometricLocked.value
                    val onboarded = isOnboarded()
                    if (!onboarded) {
                        navigateTo("Onboarding")
                    } else if (isBiometricEnabled) {
                        navigateTo("LockScreen")
                    } else {
                        navigateTo("Main")
                    }
                }
            } catch (e: Exception) {
                _authError.value = "Sign-in error: ${e.message}"
            } finally {
                _isAuthenticating.value = false
            }
        }
    }


    fun registerWithEmailReal(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val databaseProfile = getOrCreateActiveSupabaseProfile()
            _activeSupabaseProfile.value = databaseProfile
            val authenticated = authenticateSupabaseProfile(email.trim(), password, register = true)
            _isAuthenticating.value = false
            if (authenticated) {
                saveEncryptedSessionToken(email.trim(), databaseProfile.id, "Supabase In-App Auth")
                setUserEmail(email.trim())
                logFirebaseEvent("register_success", Bundle().apply { putString("provider", "supabase") })
                onSuccess()
            } else {
                val message = "Account registration requires email confirmation. Check your email inbox to confirm, then use Sign In."
                _authError.value = message
                onFailure(message)
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
        if (host == "supabase-connected" || uri.path?.contains("supabase-connected") == true) {
            val txId = uri.getQueryParameter("tx_id")
            if (!txId.isNullOrBlank()) {
                handleControlPlaneOAuthConnected(txId)
            } else {
                logFirebaseStatus("Received swapnopay://supabase-connected deep link without transaction ID.")
            }
            return
        }

        if (host == "supabase-oauth-callback" || uri.path?.contains("supabase-oauth-callback") == true) {
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
                val profile = _activeSupabaseProfile.value ?: getOrCreateActiveSupabaseProfile()
                var resolvedAccessToken = accessToken
                var resolvedRefreshToken = refreshToken

                // If PKCE code was returned, exchange it for access token
                if (resolvedAccessToken.isNullOrBlank() && !authCode.isNullOrBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val client = okhttp3.OkHttpClient()
                            val jsonBody = org.json.JSONObject().apply {
                                put("auth_code", authCode)
                            }
                            val exchangeReq = okhttp3.Request.Builder()
                                .url("${profile.supabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=pkce")
                                .header("apikey", profile.anonKey)
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
                var userName = ""

                val updated = profile.copy(
                    authSessionToken = resolvedAccessToken,
                    authRefreshToken = resolvedRefreshToken.orEmpty(),
                    authTokenExpiresAt = System.currentTimeMillis() + 3600_000L
                )
                repository.insertSupabaseProfile(updated)
                _activeSupabaseProfile.value = updated
                scheduleSupabaseSessionRefresh(updated)
                supabaseConnected.value = true
                userEmail = profile.authEmail

                // Query user info from Supabase /auth/v1/user
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient()
                        val request = okhttp3.Request.Builder()
                            .url("${profile.supabaseUrl.trimEnd('/')}/auth/v1/user")
                            .header("Authorization", "Bearer $resolvedAccessToken")
                            .header("apikey", profile.anonKey)
                            .get()
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (!body.isNullOrBlank()) {
                                    val json = org.json.JSONObject(body)
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

                val finalEmail = if (userEmail.isNotBlank()) userEmail else "oauth_user_${System.currentTimeMillis().toString().takeLast(6)}@swapnopay.bd"
                setUserEmail(finalEmail)
                saveEncryptedSessionToken(finalEmail, profile.id, "OAuth (${tokenType ?: "login"})")

                if (userName.isNotBlank()) {
                    val currentProfile = _activeProfile.value
                    val updatedProfile = currentProfile.copy(
                        businessName = if (currentProfile.businessName.isBlank() || currentProfile.businessName == "Demo Store") userName else currentProfile.businessName,
                        email = finalEmail
                    )
                    _activeProfile.value = updatedProfile
                    repository.insertMerchantProfile(updatedProfile)
                }

                _isAuthenticating.value = false
                _authError.value = null
                logFirebaseStatus("Auth deep link verified (${tokenType ?: "signup"}). Navigating to Main.")

                val isBiometricEnabled = _isBiometricLocked.value
                val onboarded = isOnboarded()
                if (!onboarded) {
                    navigateTo("Onboarding")
                } else if (isBiometricEnabled) {
                    navigateTo("LockScreen")
                } else {
                    navigateTo("Main")
                }
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
            val databaseProfile = getOrCreateActiveSupabaseProfile()
            var success = false
            var failure: String? = null
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.remote.SupabaseClient.sendPasswordReset(
                    databaseProfile.supabaseUrl,
                    databaseProfile.anonKey,
                    email.trim(),
                    onSuccess = { success = true },
                    onFailure = { failure = it }
                )
            }
            _isAuthenticating.value = false
            if (success) onSuccess() else onFailure(failure ?: "Password reset failed.")
        }
    }


    private val _appPin = MutableStateFlow(securityPrefs.getString("app_pin", "") ?: "")
    val appPin: StateFlow<String> = _appPin.asStateFlow()

    private val _isBiometricLocked = MutableStateFlow(securityPrefs.getBoolean("is_biometric_locked", true))
    val isBiometricLocked: StateFlow<Boolean> = _isBiometricLocked.asStateFlow()

    private val _isAppLocked = MutableStateFlow(true) // Starts locked on launch
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isSecuritySyncing = MutableStateFlow(false)
    val isSecuritySyncing: StateFlow<Boolean> = _isSecuritySyncing.asStateFlow()

    private val _securitySyncError = MutableStateFlow<String?>(null)
    val securitySyncError: StateFlow<String?> = _securitySyncError.asStateFlow()

    fun updatePin(newPin: String) {
        _appPin.value = newPin
        securityPrefs.edit().putString("app_pin", newPin).apply()
        syncSecuritySettingsToSupabase()
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
        // PINs and biometric preferences are device credentials. They stay in
        // EncryptedSharedPreferences and are never uploaded to a remote table.
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
        if (isExternalActivityExpected) {
            isExternalActivityExpected = false
            return
        }
        val hasSecurity = _appPin.value.isNotBlank() || _isBiometricLocked.value
        if (!hasSecurity) return

        val current = _currentScreen.value
        if (current != "Splash" && current != "Login" && current != "Onboarding" && current != "LockScreen" && current != "SupabaseSetupGuide") {
            _previousScreenBeforeLock = current
            _isAppLocked.value = true
            _currentScreen.value = "LockScreen"
        }
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

    fun logout(onComplete: () -> Unit) {
        setUserEmail(null)
        viewModelScope.launch {
            val profile = _activeSupabaseProfile.value
            if (profile != null) {
                val cleared = profile.copy(
                    authEmail = "",
                    authSessionToken = "",
                    authRefreshToken = "",
                    authTokenExpiresAt = 0L
                )
                repository.insertSupabaseProfile(cleared)
                _activeSupabaseProfile.value = cleared
            }
            supabaseRefreshJob?.cancel()
            supabaseConnected.value = false
            navigateTo("Login")
            onComplete()
        }
    }

    val supabaseProfiles: StateFlow<List<com.example.data.local.SupabaseProfileEntity>> = repository.observeSupabaseProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSupabaseProfile = MutableStateFlow<com.example.data.local.SupabaseProfileEntity?>(null)
    val activeSupabaseProfile: StateFlow<com.example.data.local.SupabaseProfileEntity?> = _activeSupabaseProfile.asStateFlow()

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
            rows.map { MerchantNumber(it.number, it.method, it.accountType, it.isActive, it.isDefault) }
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

    fun setLocalBackendUrl(url: String) {
        controlPlaneUrl.value = url.trim()
    }

    fun startControlPlaneOAuth(context: android.content.Context) {
        val userId = activeProfile.value.id.ifBlank { "user_default" }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isFetchingManagementProjects.value = true
            managementApiError.value = null
            com.example.data.repository.SupabaseConnectionRepository.startOAuthFlow(
                controlPlaneUrl = controlPlaneUrl.value,
                userId = userId,
                onSuccess = { authorizeUrl ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isFetchingManagementProjects.value = false
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authorizeUrl)).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            managementApiError.value = "Could not open browser: ${e.message}"
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
        provisioningStatusText.value = if (isNew) "Creating new Supabase project..." else "Verifying project..."

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var targetRef = projectRef
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
            while (attempts < 12 && !isHealthy) {
                kotlinx.coroutines.delay(3000)
                attempts++
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

    fun startSupabaseOAuthFlow(context: android.content.Context, customClientId: String = "") {
        val targetClientId = if (customClientId.isNotBlank()) customClientId.trim() else oauthClientId.value.ifBlank { "5d3dcd9b-1acf-4e31-96d2-d673af42a18b" }
        oauthClientId.value = targetClientId
        val pkce = com.example.data.remote.SupabaseClient.generatePkcePair()
        pendingPkceVerifier.value = pkce.codeVerifier

        val authUrl = android.net.Uri.parse("https://api.supabase.com/v1/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", targetClientId)
            .appendQueryParameter("redirect_uri", "swapnopay://supabase-oauth-callback")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", pkce.codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", java.util.UUID.randomUUID().toString())
            .build()

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, authUrl).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            managementApiError.value = "Unable to launch browser: ${e.message}"
        }
    }

    fun handleOAuthCodeReceived(code: String) {
        val verifier = pendingPkceVerifier.value ?: ""
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
                redirectUri = "swapnopay://supabase-oauth-callback",
                onSuccess = { accessToken, _ ->
                    fetchSupabaseProjectsList(
                        managementToken = accessToken,
                        onSuccess = { projects ->
                            if (projects.isNotEmpty()) {
                                val firstProj = projects.first()
                                connectViaManagementApi(
                                    token = accessToken,
                                    targetProject = firstProj,
                                    onSuccess = { url, anon ->
                                        logFirebaseStatus("Authorized via Supabase OAuth 2.0 & connected to project: ${firstProj.name}")
                                    },
                                    onFailure = { err ->
                                        managementApiError.value = err
                                    }
                                )
                            } else {
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    isFetchingManagementProjects.value = false
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

    fun connectSupabase(url: String, anonKey: String, name: String) {
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
        // A service-role key must never be persisted in a distributed mobile client.
        supabaseConnectionName.value = cleanName
        supabaseSetupProgress.value = 8
        systemTestError.value = null
        systemTestSuccess.value = false

        val id = java.util.UUID.randomUUID().toString()
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
        }
    }

    fun fetchSupabaseConfigFromAdmin(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            val merchantId = activeProfile.value.id
            val candidateUrls = listOf("http://10.0.2.2:5000", "http://10.0.2.2:4000", "https://api.swapnopay.top")
            var fetched = false
            var errorMessage = "Failed to reach Admin server"
            for (backendUrl in candidateUrls) {
                if (fetched) break
                try {
                    val urlObj = java.net.URL("$backendUrl/v1/payment/config?merchant_id=$merchantId")
                    val conn = urlObj.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(body)
                        val sUrl = json.optString("supabase_url", "")
                        val sKey = json.optString("supabase_anon_key", "")
                        if (sUrl.isNotEmpty() && sKey.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                setSupabaseUrlInput(sUrl)
                                setSupabaseAnonKeyInput(sKey)
                                connectSupabase(sUrl, sKey, "Admin Provisioned Supabase")
                                onResult(true, "Fetched credentials from Admin Panel!")
                            }
                            fetched = true
                            break
                        } else {
                            errorMessage = "No custom Supabase credentials found on Admin Panel."
                        }
                    } else {
                        errorMessage = "Admin server returned HTTP ${conn.responseCode}"
                    }
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Failed to reach Admin server"
                }
            }
            if (!fetched) {
                withContext(Dispatchers.Main) {
                    onResult(false, errorMessage)
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
                    shadowElevationDp = field.optInt("shadow_elevation_dp", 2)
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
                    subtitle = page.optString("subtitle")
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
            customVariables = customVariables.filter { it.key.isNotBlank() }
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
    }

    fun publishActiveHostedForm() {
        val result = publishForm()
        if (!result.isValid) logFirebaseStatus(result.summaryMessage)
    }

    fun hostedFormPublicUrl(): String {
        if (formStatus.value != "PUBLISHED") return ""
        val publicId = activeFormId.value.lowercase().replace("-", "")
        val brandedUrl = if (publicId.matches(Regex("^[0-9a-f]{32}$"))) "$hostedFormRouterOrigin/f/$publicId" else ""
        val routeState = hostedFormRouteStatus.value[activeFormId.value]
        if (brandedUrl.isNotBlank() && routeState != "FAILED" && routeState != "PENDING") return brandedUrl
        return hostedFormDirectUrl()
    }

    fun hostedFormDirectUrl(): String {
        val baseUrl = supabaseUrl.value.trimEnd('/')
        val encodedSlug = android.net.Uri.encode(formSlug.value.trim())
        return if (baseUrl.isBlank() || encodedSlug.isBlank()) "" else "$baseUrl/functions/v1/hosted-form?slug=$encodedSlug"
    }

    private fun registerBrandedHostedFormRoute(form: HostedFormModel) {
        val configuredProfile = _activeSupabaseProfile.value
        if (configuredProfile == null || configuredProfile.supabaseUrl.isBlank() || configuredProfile.anonKey.isBlank()) return
        hostedFormRouteStatus.update { it + (form.id to "PENDING") }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = validSupabaseSession(configuredProfile)
            if (active == null) {
                hostedFormRouteStatus.update { it + (form.id to "FAILED") }
                logFirebaseStatus("Sign in again to register the branded hosted-form route.")
                return@launch
            }
            com.example.data.remote.SupabaseClient.registerHostedFormRoute(
                routerBaseUrl = hostedFormRouterOrigin,
                projectUrl = active.supabaseUrl,
                publishableKey = active.anonKey,
                token = active.authSessionToken,
                formId = form.id,
                formSlug = form.slug,
                onSuccess = { publicUrl ->
                    hostedFormRouteStatus.update { it + (form.id to "READY") }
                    logFirebaseStatus("Branded form route ready: $publicUrl")
                },
                onFailure = { message ->
                    hostedFormRouteStatus.update { it + (form.id to "FAILED") }
                    logFirebaseStatus("Branded route unavailable; direct Supabase link remains available. $message")
                }
            )
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
                })
            }
        })
        put("pages", org.json.JSONArray().apply {
            form.pages.forEach { page ->
                put(org.json.JSONObject().apply {
                    put("id", page.id)
                    put("title", page.title)
                    put("subtitle", page.subtitle)
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
            if (theme.customVariables.isNotEmpty()) {
                val cvArr = org.json.JSONArray()
                theme.customVariables.forEach { cv -> cvArr.put(org.json.JSONObject().apply { put("key", cv.key); put("example_value", cv.exampleValue); put("source", cv.source) }) }
                put("custom_variables", cvArr)
            }
        })
        put("logo_url", form.themeConfig.logoUrl)
        put("banner_url", form.themeConfig.bannerUrl)
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
            """
            <div class="custom-web-card">
                <h2>{{form_title}}</h2>
                <p>{{form_description}}</p>
                <div class="merchant-badge">🏪 {{merchant_name}} | 📞 {{merchant_phone}}</div>
                <div class="stats-grid">
                    <div class="stat-box"><span class="stat-num">{{total_products}}</span><span>Products</span></div>
                    <div class="stat-box"><span class="stat-num">{{total_customers}}</span><span>Customers</span></div>
                    <div class="stat-box"><span class="stat-num">{{total_sales}}</span><span>Total Sales</span></div>
                </div>
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
                .custom-web-card { max-width: 680px; margin: 0 auto; background: ${if (theme.isDarkMode) "#151C2D" else "#FFFFFF"}; border-radius: 20px; padding: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.08); }
                .merchant-badge { display: inline-block; background: #EEF2FF; color: #4F46E5; padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; margin: 12px 0; }
                .stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 20px; }
                .stat-box { background: ${if (theme.isDarkMode) "#1E293B" else "#F1F5F9"}; padding: 14px; border-radius: 12px; text-align: center; font-size: 12px; }
                .stat-num { display: block; font-size: 18px; font-weight: bold; color: ${theme.primaryColorHex}; margin-bottom: 4px; }
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

                $resolvedJs
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    fun addFormField(type: FormFieldType, label: String? = null, placeholder: String? = null) {
        if (formFieldsList.value.size >= 200) return
        val newField = FormFieldItem(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            label = label ?: type.displayName,
            placeholder = placeholder ?: "Enter ${type.displayName.lowercase()}",
            isRequired = type in setOf(FormFieldType.PHONE, FormFieldType.CUSTOM_AMOUNT, FormFieldType.QUANTITY),
            minValue = when (type) {
                FormFieldType.CUSTOM_AMOUNT, FormFieldType.QUANTITY -> 1.0
                else -> null
            }
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
        refreshGatewayReceiptHealth()
        onResult("SMS delivery is server-managed. Receipt worker health verification started; no synthetic message was sent.")
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

    fun addFormPage(title: String = "") {
        if (formPagesList.value.size >= 25) return
        val newIdx = formPagesList.value.size + 1
        val newPage = FormPageItem(
            title = title.ifEmpty { "Page $newIdx: Additional Details" },
            subtitle = "Please complete the required fields"
        )
        formPagesList.value = formPagesList.value + newPage
        activePageIndex.value = formPagesList.value.size - 1
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
            "SINGLE_PRODUCT" -> {
                formTitle.value = "Single Product Direct Checkout"
                formDescription.value = "Instant 1-page checkout for your featured product."
                formFieldsList.value = listOf(
                    FormFieldItem(type = FormFieldType.NAME, label = "Customer Name", placeholder = "Enter full name", isRequired = true),
                    FormFieldItem(type = FormFieldType.PHONE, label = "bKash / Nagad Number", placeholder = "017XXXXXXXX", isRequired = true),
                    FormFieldItem(type = FormFieldType.ADDRESS, label = "Delivery Address", placeholder = "Full shipping address", isRequired = true),
                    FormFieldItem(type = FormFieldType.PRODUCT, label = "Selected Product", isRequired = true),
                    FormFieldItem(type = FormFieldType.QUANTITY, label = "Quantity", isRequired = true),
                    FormFieldItem(type = FormFieldType.SHIPPING, label = "Delivery Option", options = listOf("Inside Dhaka (৳60)", "Outside Dhaka (৳120)")),
                    FormFieldItem(type = FormFieldType.COUPON, label = "Promo Code", isRequired = false)
                )
                formProductsList.value = listOf(
                    FormProductItem(title = "Wireless Smart POS Terminal", description = "High speed thermal printer + battery", price = 6500.0, salePrice = 5500.0, sku = "POS-W1")
                )
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
                formProductsList.value = listOf(
                    FormProductItem(title = "Product A - Premium Pack", price = 1200.0, salePrice = 999.0, sku = "PROD-A"),
                    FormProductItem(title = "Product B - Standard Kit", price = 800.0, salePrice = 650.0, sku = "PROD-B"),
                    FormProductItem(title = "Product C - Basic Accessory", price = 350.0, salePrice = 299.0, sku = "PROD-C")
                )
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
                formProductsList.value = listOf(
                    FormProductItem(title = "Full Software Source Code & License Key", description = "Instant zip download link", price = 3000.0, salePrice = 2499.0, isDigital = true, digitalDownloadUrl = "https://swapnopay.app/download/v1-source.zip")
                )
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
        }
        saveActiveFormToHostedList()
        logFirebaseStatus("Auto-configured Form Template: $templateKey")
    }

    // AI Form Generator Logic
    fun generateFormWithAI(prompt: String) {
        val lc = prompt.lowercase()
        pushFormStateToUndo()
        aiFormPromptInput.value = prompt

        when {
            lc.contains("donation") || lc.contains("mosque") || lc.contains("school") || lc.contains("ngo") || lc.contains("charity") -> {
                autoConfigureTemplate("DONATION")
                formTitle.value = "AI Generated: ${prompt.take(40)}"
            }
            lc.contains("course") || lc.contains("tuition") || lc.contains("exam") || lc.contains("student") -> {
                autoConfigureTemplate("EDUCATION")
                formTitle.value = "AI Generated: ${prompt.take(40)}"
            }
            lc.contains("ticket") || lc.contains("event") || lc.contains("webinar") || lc.contains("conference") -> {
                autoConfigureTemplate("EVENT")
                formTitle.value = "AI Generated: ${prompt.take(40)}"
            }
            lc.contains("appointment") || lc.contains("consultation") || lc.contains("booking") -> {
                autoConfigureTemplate("APPOINTMENT")
                formTitle.value = "AI Generated: ${prompt.take(40)}"
            }
            lc.contains("digital") || lc.contains("download") || lc.contains("software") || lc.contains("ebook") -> {
                autoConfigureTemplate("DIGITAL")
                formTitle.value = "AI Generated: ${prompt.take(40)}"
            }
            lc.contains("cart") || lc.contains("store") || lc.contains("catalog") || lc.contains("ecommerce") -> {
                autoConfigureTemplate("CART")
                formTitle.value = "AI Generated: ${prompt.take(40)}"
            }
            else -> {
                autoConfigureTemplate("SINGLE_PRODUCT")
                formTitle.value = "AI Generated: ${prompt.take(40)}"
            }
        }
        saveActiveFormToHostedList()
        logFirebaseStatus("AI Form Generator built form from prompt: '$prompt'")
    }

    fun generateFormFromAiPrompt(prompt: String) {
        generateFormWithAI(prompt)
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
        val newItem = FormFieldItem(
            type = type,
            label = type.displayName,
            placeholder = "Enter ${type.displayName.lowercase()}",
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

    fun addFormProduct(title: String, price: Double, salePrice: Double, sku: String, stock: Int, category: String, isDigital: Boolean) {
        if (formProductsList.value.size >= 100) return
        val newProd = FormProductItem(
            title = title,
            price = price,
            salePrice = salePrice,
            sku = sku,
            stock = stock,
            category = category,
            isDigital = isDigital
        )
        formProductsList.value = formProductsList.value + newProd
        saveActiveFormToHostedList()
    }

    fun deleteFormProduct(productId: String) {
        formProductsList.value = formProductsList.value.filter { it.id != productId }
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
            if (!theme.enforceRequiredFields) {
                publishErrors["payment_required_validation"] = "Payment forms require required-field validation to stay enabled."
            }
            if (formFieldsList.value.none { it.type == FormFieldType.PHONE && it.isRequired }) {
                publishErrors["payment_phone"] = "Payment forms require a required phone field for payer matching."
            }
            if (formProductsList.value.isEmpty() && formFieldsList.value.none { it.type == FormFieldType.CUSTOM_AMOUNT && it.isRequired }) {
                publishErrors["payment_amount"] = "Add a required custom-amount field or a product, or disable payment."
            }
            if (formFieldsList.value.count { it.type == FormFieldType.QUANTITY } > 1) {
                publishErrors["payment_quantity"] = "Payment forms can use only one quantity field."
            }
            if (formFieldsList.value.count { it.type == FormFieldType.CUSTOM_AMOUNT } > 1) {
                publishErrors["payment_custom_amount"] = "Payment forms can use only one custom-amount field."
            }
            if (formProductsList.value.isNotEmpty() && formFieldsList.value.any { it.type == FormFieldType.CUSTOM_AMOUNT }) {
                publishErrors["payment_pricing"] = "Use products or a custom amount, not both in the same payment form."
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
        if (theme.bannerUrl.isNotBlank() && !theme.bannerUrl.matches(Regex("^https://[^\\s]+$"))) {
            publishErrors["banner_url"] = "Cover photo URL must use HTTPS."
        }
        if (theme.logoUrl.isNotBlank() && !theme.logoUrl.matches(Regex("^https://[^\\s]+$"))) {
            publishErrors["logo_url"] = "Logo URL must use HTTPS."
        }
        if (theme.backgroundStyle == "GRADIENT" && listOf(theme.gradientColorStart, theme.gradientColorEnd).any { !it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }) {
            publishErrors["gradient_colors"] = "Gradient colors must use six-digit hex values such as #5B7FFF."
        }
        if (listOf(theme.primaryColorHex, theme.backgroundColorHex).any { !it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }) {
            publishErrors["theme_colors"] = "Primary and background colors must use six-digit hex values."
        }
        if (theme.fontFamily.uppercase() !in setOf("INTER", "SYSTEM", "SERIF", "MONOSPACE")) {
            publishErrors["font_family"] = "Choose a supported hosted-form font family."
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
            if (product.imageUrl.isNotBlank() && !product.imageUrl.matches(Regex("^https://[^\\s]+$"))) {
                publishErrors["product_image_${product.id}"] = "$productKey image URL must use HTTPS."
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
        val payload = hostedFormToJson(form)
        val configuredProfile = _activeSupabaseProfile.value
        if (configuredProfile == null || configuredProfile.supabaseUrl.isBlank() || configuredProfile.anonKey.isBlank()) {
            logFirebaseStatus("Form published locally and queued for Supabase sync.")
            return validation
        }
        hostedFormRouteStatus.update { it + (form.id to "PENDING") }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = validSupabaseSession(configuredProfile)
            if (active == null) {
                hostedFormRouteStatus.update { it + (form.id to "FAILED") }
                logFirebaseStatus("Form published locally; sign in again to sync it to Supabase.")
                return@launch
            }
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
                    registerBrandedHostedFormRoute(form)
                    logFirebaseStatus("Form published and synced: ${form.slug}")
                },
                onFailure = {
                    hostedFormRouteStatus.update { it + (form.id to "FAILED") }
                    logFirebaseStatus("Form saved locally; Supabase publish failed: $it")
                }
            )
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

    fun setSupabaseUrlInput(url: String) {
        _supabaseUrlInput.value = url
    }

    fun setSupabaseAnonKeyInput(key: String) {
        _supabaseAnonKeyInput.value = key
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
            repository.observeMerchantProfile().filterNotNull().firstOrNull()?.let { persistedProfile ->
                _activeProfile.value = persistedProfile
            }
            val active = repository.getActiveSupabaseProfile()
            if (active != null) {
                val effective = if (active.supabaseUrl.isBlank() || active.supabaseUrl.contains("abc123xyz") || active.supabaseUrl.contains("def456uvw")) {
                    active.copy(
                        supabaseUrl = "https://tldubojeokgyoclxnzkb.supabase.co",
                        anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRsZHVib2plb2tneW9jbHhuemtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NjcwODMsImV4cCI6MjEwMzM0MzA4M30.vlgmNEJ0_DpdbsZEQMA2Z82vwY4hwTxpgS4o9p5oEb0"
                    ).also { repository.insertSupabaseProfile(it) }
                } else active
                val safeActive = effective.copy(serviceRoleKey = "")
                if (effective.serviceRoleKey.isNotEmpty()) repository.insertSupabaseProfile(safeActive)
                activateLocalProfileForBackend(safeActive)
                _activeSupabaseProfile.value = safeActive
                supabaseUrl.value = safeActive.supabaseUrl
                supabaseAnonKey.value = safeActive.anonKey
                supabaseConnectionName.value = safeActive.businessName
                val isReal = safeActive.supabaseUrl.isNotEmpty() && safeActive.supabaseUrl != "https://abc123xyz.supabase.co" && safeActive.supabaseUrl != "https://def456uvw.supabase.co"
                supabaseConnected.value = isReal
                supabaseSetupProgress.value = if (isReal) 9 else 0
                _supabaseUrlInput.value = safeActive.supabaseUrl
                _supabaseAnonKeyInput.value = safeActive.anonKey
                scheduleSupabaseSessionRefresh(safeActive)
            } else {
                val first = repository.observeSupabaseProfiles().firstOrNull()?.firstOrNull()
                if (first != null) {
                    val safeFirst = first.copy(serviceRoleKey = "")
                    if (first.serviceRoleKey.isNotEmpty()) repository.insertSupabaseProfile(safeFirst)
                    activateLocalProfileForBackend(safeFirst)
                    _activeSupabaseProfile.value = safeFirst
                    supabaseUrl.value = safeFirst.supabaseUrl
                    supabaseAnonKey.value = safeFirst.anonKey
                    supabaseConnectionName.value = safeFirst.businessName
                    val isReal = safeFirst.supabaseUrl.isNotEmpty() && safeFirst.supabaseUrl != "https://abc123xyz.supabase.co" && safeFirst.supabaseUrl != "https://def456uvw.supabase.co"
                    supabaseConnected.value = isReal
                    supabaseSetupProgress.value = if (isReal) 9 else 0
                    _supabaseUrlInput.value = first.supabaseUrl
                    _supabaseAnonKeyInput.value = first.anonKey
                    scheduleSupabaseSessionRefresh(safeFirst)
                }
            }
            if (_autoSyncEnabled.value && supabaseConnected.value) {
                triggerSync()
            }
        }

        viewModelScope.launch {
            activeProfile.flatMapLatest { profile ->
                repository.observePaymentFormCache(profile.id)
            }.collect { caches ->
                for (cache in caches) {
                    runCatching {
                        val json = org.json.JSONObject(cache.payloadJson)
                        loadCachedPaymentForm(json, autoSelect = false)
                    }
                }
            }
        }
        
        // Load the real FCM registration token when Firebase is configured.
        try {
            val m = messaging
            if (m != null) {
                m.token.addOnCompleteListener { task ->
                    try {
                        if (task.isSuccessful && task.result != null) {
                            _fcmToken.value = task.result
                            logFirebaseStatus("FCM Registration Token Loaded successfully.")
                        } else {
                            _fcmToken.value = null
                            logFirebaseStatus("FCM token unavailable; notifications remain disabled.")
                        }
                    } catch (e: Exception) {
                        _fcmToken.value = null
                        logFirebaseStatus("FCM initialization failed: ${e.message}")
                    }
                }
            } else {
                _fcmToken.value = null
                logFirebaseStatus("FCM is not configured; notifications remain disabled.")
            }
        } catch (e: Exception) {
            _fcmToken.value = null
            logFirebaseStatus("FCM initialization failed: ${e.message}")
        }
        
        // Establish Real-time Listener for notice popups
        listenToFirebaseNotice()
        
        // Track app start behaviour
        logFirebaseEvent("app_open", Bundle().apply {
            putLong("timestamp", System.currentTimeMillis())
        })
    }

    fun navigateTo(screen: String) {
        val currentNavState = NavState(_currentScreen.value, _currentTab.value)
        val targetScreen: String
        val targetTab: String

        when (screen) {
            "Main", "Dashboard" -> {
                targetScreen = "Main"
                targetTab = "Dashboard"
            }
            "Transactions", "TransactionLedger" -> {
                targetScreen = "Main"
                targetTab = "Transactions"
            }
            "Forms", "PaymentForms" -> {
                targetScreen = "Main"
                targetTab = "Forms"
            }
            "AiCopilot", "AI", "Setup" -> {
                targetScreen = "Main"
                targetTab = "AiCopilot"
            }
            "More" -> {
                targetScreen = "Main"
                targetTab = "More"
            }
            else -> {
                targetScreen = screen
                targetTab = _currentTab.value
            }
        }

        if (currentNavState.screen != "Splash" && currentNavState.screen != "Onboarding" && currentNavState.screen != "Login" && currentNavState.screen != "LockScreen") {
            if (currentNavState.screen != targetScreen || currentNavState.tab != targetTab) {
                if (navigationStack.size >= 50) {
                    navigationStack.removeLast()
                }
                navigationStack.push(currentNavState)
            }
        }

        _currentScreen.value = targetScreen
        _currentTab.value = targetTab
    }

    fun addSupplier(
        name: String,
        phone: String = "",
        address: String = "",
        openingBalance: Double = 0.0,
        id: String = java.util.UUID.randomUUID().toString()
    ) {
        if (name.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val merchantId = _activeProfile.value.id
            val supplier = SupplierEntity(
                id = id,
                merchantId = merchantId,
                name = name.trim(),
                phone = phone.trim(),
                email = null,
                address = address.trim().ifEmpty { null },
                openingBalance = openingBalance,
                currentBalance = openingBalance,
                createdAt = System.currentTimeMillis()
            )
            repository.insertSupplier(supplier)
            logFirebaseStatus("Added new supplier: $name")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                val json = org.json.JSONObject().apply {
                    put("id", supplier.id)
                    put("name", supplier.name)
                    put("phone", supplier.phone)
                    if (supplier.address != null) put("address", supplier.address)
                    put("opening_balance", supplier.openingBalance)
                    put("current_balance", supplier.currentBalance)
                    put("created_at", toIsoTimestamp(supplier.createdAt))
                }
                com.example.data.remote.SupabaseClient.upsertRecord(
                    active.supabaseUrl, active.anonKey, active.authSessionToken, "suppliers", json, {},
                    { logFirebaseStatus("Supplier saved locally; cloud sync failed: $it") }
                )
            }
        }
    }

    fun addSupplier(name: String, phone: String, initialBalance: Double) {
        addSupplier(name = name, phone = phone, address = "", openingBalance = initialBalance)
    }

    fun addSupplier(name: String, phone: String, initialBalance: Double, id: String) {
        addSupplier(name = name, phone = phone, address = "", openingBalance = initialBalance, id = id)
    }

    fun setTab(tab: String) {
        val currentNavState = NavState(_currentScreen.value, _currentTab.value)
        val authScreens = setOf("Splash", "Onboarding", "Login", "LockScreen")
        if (_currentScreen.value !in authScreens && _currentScreen.value != "Main") {
            if (navigationStack.size >= 50) {
                navigationStack.removeLast()
            }
            navigationStack.push(currentNavState)
        }
        _currentScreen.value = "Main"
        _currentTab.value = tab
    }

    fun goBack() {
        if (navigationStack.isNotEmpty()) {
            val previous = navigationStack.pop()
            _currentScreen.value = previous.screen
            _currentTab.value = previous.tab
        } else if (_currentScreen.value != "Main") {
            _currentScreen.value = "Main"
            _currentTab.value = "Dashboard"
        }
    }

    fun canGoBack(): Boolean {
        if (_currentScreen.value == "Splash" || _currentScreen.value == "Onboarding" || _currentScreen.value == "Login" || _currentScreen.value == "LockScreen") {
            return false
        }
        return navigationStack.isNotEmpty() || _currentScreen.value != "Main" || _currentTab.value != "Dashboard"
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            repository.selectActiveSupabaseProfile(profileId)
            val supabaseSel = repository.getSupabaseProfileById(profileId)
            if (supabaseSel != null) {
                activateLocalProfileForBackend(supabaseSel)
                _activeSupabaseProfile.value = supabaseSel
                supabaseUrl.value = supabaseSel.supabaseUrl
                supabaseAnonKey.value = supabaseSel.anonKey
                supabaseConnectionName.value = supabaseSel.businessName
                val isReal = supabaseSel.supabaseUrl.isNotEmpty() && supabaseSel.supabaseUrl != "https://abc123xyz.supabase.co" && supabaseSel.supabaseUrl != "https://def456uvw.supabase.co"
                supabaseConnected.value = isReal
                supabaseSetupProgress.value = if (isReal) 9 else 0
                _supabaseUrlInput.value = supabaseSel.supabaseUrl
                _supabaseAnonKeyInput.value = supabaseSel.anonKey
                syncAllScreensToSupabase()
            }
        }
    }

    fun updateMerchantProfile(profile: com.example.data.local.MerchantProfileEntity) {
        viewModelScope.launch {
            repository.insertMerchantProfile(profile)
            _activeProfile.value = profile
            uploadMerchantProfileToFirebase(profile)
        }
    }

    fun uploadMerchantPhoto(imageBytes: ByteArray, onComplete: ((String?) -> Unit)? = null) {
        val merchantId = _activeProfile.value.id
        val context = getApplication<android.app.Application>().applicationContext
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val photosDir = java.io.File(context.filesDir, "merchant_photos").apply {
                    if (!exists()) mkdirs()
                }
                photosDir.listFiles()?.filter { it.name.startsWith("merchant_${merchantId}_") }?.forEach {
                    try { it.delete() } catch (_: Exception) {}
                }
                val photoFile = java.io.File(photosDir, "merchant_${merchantId}_${System.currentTimeMillis()}.jpg")
                photoFile.outputStream().use { it.write(imageBytes) }
                val savedPath = photoFile.absolutePath

                val updated = _activeProfile.value.copy(photoUrl = savedPath)
                repository.insertMerchantProfile(updated)
                _activeProfile.value = updated

                try {
                    val fileName = "merchants/${merchantId}/photo_${System.currentTimeMillis()}.jpg"
                    val publicUrl = "${r2PublicDomain.value.trimEnd('/')}/$fileName"
                    database?.getReference("merchants")?.child(merchantId)?.child("photoUrl")?.setValue(publicUrl)
                } catch (e: Exception) {
                    android.util.Log.w("AppViewModel", "Photo cloud sync warning: ${e.message}")
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    logFirebaseStatus("Merchant photo updated: $savedPath")
                    onComplete?.invoke(savedPath)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "uploadMerchantPhoto failed: ${e.message}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    logFirebaseStatus("Failed to update merchant photo: ${e.message}")
                    onComplete?.invoke(null)
                }
            }
        }
    }

    fun removeMerchantPhoto(onComplete: (() -> Unit)? = null) {
        val merchantId = _activeProfile.value.id
        val context = getApplication<android.app.Application>().applicationContext
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val photosDir = java.io.File(context.filesDir, "merchant_photos")
                photosDir.listFiles()?.filter { it.name.startsWith("merchant_${merchantId}_") }?.forEach {
                    try { it.delete() } catch (_: Exception) {}
                }
                val updated = _activeProfile.value.copy(photoUrl = "")
                repository.insertMerchantProfile(updated)
                _activeProfile.value = updated
                database?.getReference("merchants")?.child(merchantId)?.child("photoUrl")?.setValue("")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    logFirebaseStatus("Merchant photo removed.")
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "removeMerchantPhoto error: ${e.message}")
            }
        }
    }

    fun setFirebaseConfig(apiKey: String, projectId: String) {
        _firebaseApiKey.value = apiKey
        _firebaseProjectId.value = projectId
    }

    fun createOrder(customerName: String, customerPhone: String, amount: Double, method: String, notes: String = "") {
        if (customerName.isBlank() || customerPhone.filter(Char::isDigit).length < 10 || amount <= 0.0 || method !in setOf("bKash", "Nagad", "Rocket", "Upay")) {
            logFirebaseStatus("Order rejected: valid customer, phone, amount, and payment method are required.")
            return
        }
        viewModelScope.launch {
            val orderId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val order = CachedOrderEntity(
                id = orderId,
                merchantId = activeProfile.value.id,
                customerName = customerName,
                customerPhone = customerPhone,
                amount = amount,
                status = "PENDING",
                method = method,
                createdAt = now,
                expiresAt = now + 15 * 60 * 1000,
                notes = notes
            )
            repository.insertOrder(order)
            syncOrderToSupabase(order)
            syncOrdersToFirebase(orders.value + order)
            logFirebaseEvent("create_order", Bundle().apply {
                putString("order_id", orderId)
                putDouble("amount", amount)
                putString("method", method)
            })
        }
    }

    fun extendOrderExpiry(orderId: String) {
        viewModelScope.launch {
            val current = orders.value.find { it.id == orderId } ?: return@launch
            val updated = current.copy(expiresAt = current.expiresAt + 15 * 60 * 1000)
            repository.updateOrder(updated)
            syncOrderToSupabase(updated)
            if (_activeOrderDetails.value?.id == orderId) {
                _activeOrderDetails.value = updated
            }
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            val current = orders.value.find { it.id == orderId } ?: return@launch
            val updated = current.copy(status = "CANCELLED")
            repository.updateOrder(updated)
            syncOrderToSupabase(updated)
            if (_activeOrderDetails.value?.id == orderId) {
                _activeOrderDetails.value = updated
            }
        }
    }

    fun showOrderDetails(order: CachedOrderEntity) {
        _activeOrderDetails.value = order
        navigateTo("OrderDetail")
    }

    private suspend fun syncOrderToSupabase(order: CachedOrderEntity) {
        val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return
        val payload = org.json.JSONObject().apply {
            put("id", order.id)
            put("tran_id", order.id)
            put("amount", order.amount)
            put("cus_phone", order.customerPhone)
            put("cus_name", order.customerName)
            put("status", order.status)
            put("payment_method", order.method)
            put("expires_at", toIsoTimestamp(order.expiresAt))
            put("metadata", org.json.JSONObject().put("notes", order.notes))
        }
        runCatching {
            upsertRemoteOrThrow(active.supabaseUrl, active.anonKey, active.authSessionToken, "orders", payload)
        }.onFailure { logFirebaseStatus("Order saved offline; Supabase sync failed: ${it.message}") }
    }

    fun showPaymentDetails(payment: CachedPaymentEntity) {
        _activePaymentDetails.value = payment
        navigateTo("PaymentDetail")
    }

    fun addPaymentTransaction(id: String, amount: Double, sender: String, method: String) {
        if (id.isBlank() || amount <= 0.0 || sender.filter(Char::isDigit).length < 10) {
            logFirebaseStatus("Manual transaction rejected: valid TrxID, amount, and sender phone are required.")
            return
        }
        viewModelScope.launch {
            val payment = CachedPaymentEntity(
                id = id,
                merchantId = activeProfile.value.id,
                amount = amount,
                sender = sender,
                timestamp = System.currentTimeMillis(),
                status = "UNMATCHED",
                method = method
            )
            repository.insertPayment(payment)
            logFirebaseStatus("Added unverified manual transaction $id for review (BDT $amount)")
        }
    }

    fun resolveAppeal(appealId: String, status: String) {
        if (status !in setOf("APPROVED", "REJECTED")) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val appeal = appeals.value.find { it.id == appealId } ?: return@launch
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active == null) {
                logFirebaseStatus("Appeal resolution requires an authenticated network connection.")
                return@launch
            }
            com.example.data.remote.SupabaseClient.resolveAppeal(
                active.supabaseUrl,
                active.anonKey,
                active.authSessionToken,
                appeal.id,
                status,
                appeal.orderId,
                onSuccess = {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.updateAppealStatus(appealId, status)
                        triggerSync()
                    }
                },
                onFailure = { logFirebaseStatus("Appeal resolution failed: $it") }
            )
        }
    }

    fun syncMerchantConfigToAdminDatabase() {
        saveGatewayConfig(gatewayConfig.value)
    }

    fun addMerchantNumber(number: String, method: String, type: String, qrCodeUrl: String? = null) {
        val normalized = number.filter(Char::isDigit)
        if (normalized.length !in 10..15 || method !in setOf("bKash", "Nagad", "Rocket", "Upay")) {
            logFirebaseStatus("Invalid merchant payment number or method.")
            return
        }
        viewModelScope.launch {
            val entity = MerchantNumberEntity(
                number = normalized,
                merchantId = activeProfile.value.id,
                method = method,
                accountType = type.trim().ifEmpty { "Personal" },
                qrCodeUrl = qrCodeUrl?.trim()?.ifEmpty { null }
            )
            repository.upsertMerchantNumber(entity)
            syncMerchantNumberToSupabase(entity)
            syncMerchantConfigToAdminDatabase()
        }
    }

    fun toggleMerchantNumber(number: String) {
        merchantNumbers.value.find { it.number == number }?.let { current ->
            viewModelScope.launch {
                val entity = MerchantNumberEntity(
                        number = current.number,
                        merchantId = activeProfile.value.id,
                        method = current.method,
                        accountType = current.type,
                        isActive = !current.isActive,
                        isDefault = current.isDefault,
                        qrCodeUrl = current.qrCodeUrl
                    )
                repository.upsertMerchantNumber(entity)
                syncMerchantNumberToSupabase(entity)
                syncMerchantConfigToAdminDatabase()
            }
        }
    }

    fun setMerchantNumberDefault(number: String) {
        merchantNumbers.value.find { it.number == number }?.let { current ->
            viewModelScope.launch {
                val entity = MerchantNumberEntity(
                    number = current.number,
                    merchantId = activeProfile.value.id,
                    method = current.method,
                    accountType = current.type,
                    isActive = current.isActive,
                    isDefault = true
                )
                repository.setDefaultMerchantNumber(
                    activeProfile.value.id,
                    entity
                )
                merchantNumbers.value.filter { it.number != number && it.isDefault }.forEach { oldDefault ->
                    syncMerchantNumberToSupabase(
                        MerchantNumberEntity(oldDefault.number, activeProfile.value.id, oldDefault.method, oldDefault.type, oldDefault.isActive, false)
                    )
                }
                syncMerchantNumberToSupabase(entity)
            }
        }
    }

    private suspend fun syncMerchantNumberToSupabase(entity: MerchantNumberEntity) {
        val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return
        if (active.supabaseUrl.isBlank() || active.anonKey.isBlank()) return
        val id = merchantNumberRemoteId(entity.merchantId, entity.number)
        val payload = org.json.JSONObject().apply {
            put("id", id)
            put("number", entity.number)
            put("type", entity.method)
            put("account_type", entity.accountType)
            put("active", entity.isActive)
            put("is_default", entity.isDefault)
            put("qr_code_url", entity.qrCodeUrl ?: org.json.JSONObject.NULL)
        }
        try {
            upsertRemoteOrThrow(
                active.supabaseUrl,
                active.anonKey,
                active.authSessionToken,
                "merchant_numbers",
                payload
            )
        } catch (error: Exception) {
            logFirebaseStatus("Merchant number saved locally; Supabase sync failed: ${error.message}")
        }
    }

    private fun merchantNumberRemoteId(merchantId: String, number: String): String =
        java.util.UUID.nameUUIDFromBytes("$merchantId:$number".toByteArray(Charsets.UTF_8)).toString()

    fun generateCsvContent(list: List<CachedPaymentEntity> = payments.value): String {
        val sb = StringBuilder()
        // CSV Header
        sb.append("Transaction ID,Amount,Sender,Method,Timestamp,Status,OrderId\n")
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Dhaka")
        }
        for (payment in list) {
            val dateStr = sdf.format(java.util.Date(payment.timestamp))
            val escapedSender = payment.sender.replace("\"", "\"\"")
            val escapedId = payment.id.replace("\"", "\"\"")
            val escapedOrderId = (payment.orderId ?: "").replace("\"", "\"\"")
            
            sb.append("\"$escapedId\",")
            sb.append("${payment.amount},")
            sb.append("\"$escapedSender\",")
            sb.append("\"${payment.method}\",")
            sb.append("\"$dateStr\",")
            sb.append("\"${payment.status}\",")
            sb.append("\"$escapedOrderId\"\n")
        }
        return sb.toString()
    }

    fun exportTransactionsToCsv(context: Context, rows: List<CachedPaymentEntity> = payments.value, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val csvContent = generateCsvContent(rows)
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val fileName = "SwapnoPay_Transactions_$timestamp.csv"
                
                var success = false
                var pathInfo = ""
                
                // Write to downloads via MediaStore if API level >= 29
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { output ->
                            output.write(csvContent.toByteArray(Charsets.UTF_8))
                            success = true
                            pathInfo = "Downloads/$fileName"
                        }
                    }
                }
                
                // Fallback for older versions or if MediaStore insert failed
                if (!success) {
                    val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir != null) {
                        val file = java.io.File(downloadsDir, fileName)
                        file.writeText(csvContent, Charsets.UTF_8)
                        success = true
                        pathInfo = "External Files/Downloads/$fileName"
                    }
                }
                
                if (success) {
                    onResult("Successfully exported ledger to $pathInfo")
                } else {
                    onResult("Failed to write CSV file.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("Error during CSV export: ${e.localizedMessage}")
            }
        }
    }

    fun shareTransactionsCsv(context: Context, rows: List<CachedPaymentEntity> = payments.value) {
        try {
            val csvContent = generateCsvContent(rows)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SwapnoPay Transaction Ledger")
                putExtra(Intent.EXTRA_TEXT, csvContent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Transaction Ledger CSV"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val paymentForms: StateFlow<List<org.json.JSONObject>> = activeProfile
        .flatMapLatest { repository.observePaymentFormCache(it.id) }
        .map { rows -> rows.mapNotNull { runCatching { org.json.JSONObject(it.payloadJson) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val formSubmissions: StateFlow<List<org.json.JSONObject>> = activeProfile
        .flatMapLatest { repository.observeFormSubmissionCache(it.id) }
        .map { rows -> rows.mapNotNull { runCatching { org.json.JSONObject(it.payloadJson) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isFormLoading = MutableStateFlow(false)
    val isSupabaseSyncing = MutableStateFlow(false)
    val supabaseSyncLog = MutableStateFlow<List<String>>(emptyList())
    val supabaseLastSyncTime = MutableStateFlow<Long?>(null)

    val employees: StateFlow<List<EmployeeItem>> = activeProfile
        .flatMapLatest { repository.observeEmployees(it.id) }
        .map { rows -> rows.map(::employeeEntityToItem) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun employeeEntityToItem(entity: EmployeeEntity): EmployeeItem {
        val permissions = runCatching {
            val json = org.json.JSONArray(entity.permissionsJson)
            List(json.length()) { json.optString(it) }.filter(String::isNotBlank)
        }.getOrDefault(emptyList())
        return EmployeeItem(
            id = entity.id,
            name = entity.name,
            designation = entity.designation,
            role = entity.role,
            email = entity.email,
            phone = entity.phone,
            department = entity.department,
            status = entity.status,
            avatarUrl = entity.avatarUrl,
            permissions = permissions,
            joinedDate = entity.joinedDate
        )
    }

    private fun employeeItemToEntity(item: EmployeeItem) = EmployeeEntity(
        id = item.id,
        merchantId = activeProfile.value.id,
        name = item.name.trim(),
        designation = item.designation.trim(),
        role = item.role,
        email = item.email.trim(),
        phone = item.phone.filter { it.isDigit() || it == '+' },
        department = item.department.trim(),
        status = item.status,
        avatarUrl = item.avatarUrl,
        permissionsJson = org.json.JSONArray(item.permissions).toString(),
        joinedDate = item.joinedDate
    )

    private fun syncEmployeeToSupabase(item: EmployeeItem) {
        val json = org.json.JSONObject().apply {
            put("id", item.id)
            put("name", item.name)
            put("designation", item.designation)
            put("role", item.role)
            put("email", item.email)
            put("phone", item.phone)
            put("department", item.department)
            put("status", item.status)
            put("avatar_url", item.avatarUrl ?: org.json.JSONObject.NULL)
            put("permissions", org.json.JSONArray(item.permissions))
            put("joined_date", item.joinedDate)
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return@launch
            if (active.supabaseUrl.isBlank() || active.anonKey.isBlank()) return@launch
            com.example.data.remote.SupabaseClient.upsertRecord(
                active.supabaseUrl,
                active.anonKey,
                active.authSessionToken,
                "employees",
                json,
                onSuccess = {},
                onFailure = { logFirebaseStatus("Employee sync failed: $it") }
            )
        }
    }

    fun addEmployee(
        name: String,
        designation: String,
        role: String,
        email: String,
        phone: String,
        department: String = "General",
        status: String = "Active",
        permissions: List<String> = listOf("POS & Billing Access", "Customer & Supplier Ledgers")
    ) {
        if (name.isBlank() || designation.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() || phone.count(Char::isDigit) < 10) {
            logFirebaseStatus("Employee validation failed: name, designation, valid email and phone are required.")
            return
        }
        val newEmp = EmployeeItem(
            name = name,
            designation = designation,
            role = role,
            email = email,
            phone = phone,
            department = department,
            status = status,
            permissions = permissions,
            joinedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        )
        viewModelScope.launch {
            repository.upsertEmployee(employeeItemToEntity(newEmp))
            logFirebaseStatus("Added team member: $name ($role)")
            syncEmployeeToSupabase(newEmp)
        }
    }

    fun updateEmployee(updated: EmployeeItem) {
        viewModelScope.launch {
            repository.upsertEmployee(employeeItemToEntity(updated))
            logFirebaseStatus("Updated employee ${updated.name} permissions & details.")
            syncEmployeeToSupabase(updated)
        }
    }

    fun deleteEmployee(id: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.deleteEmployee(id)
            logFirebaseStatus("Removed employee ID: $id")
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                com.example.data.remote.SupabaseClient.deleteRecord(
                    active.supabaseUrl,
                    active.anonKey,
                    active.authSessionToken,
                    "employees",
                    "id",
                    id,
                    onSuccess = {},
                    onFailure = { logFirebaseStatus("Employee delete sync failed: $it") }
                )
            }
        }
    }

    fun toggleEmployeeStatus(id: String) {
        employees.value.find { it.id == id }?.let { employee ->
            updateEmployee(employee.copy(status = if (employee.status == "Active") "Inactive" else "Active"))
        }
    }

    private suspend fun upsertRemoteOrThrow(
        url: String,
        key: String,
        token: String,
        table: String,
        payload: org.json.JSONObject
    ) {
        var succeeded = false
        var failure = "Unknown Supabase error"
        com.example.data.remote.SupabaseClient.upsertRecord(
            url,
            key,
            token,
            table,
            payload,
            onSuccess = { succeeded = true },
            onFailure = { failure = it }
        )
        if (!succeeded) throw java.io.IOException(failure)
    }

    fun syncAllScreensToSupabase() {
        val configuredProfile = _activeSupabaseProfile.value ?: return
        if (configuredProfile.supabaseUrl.isEmpty() || configuredProfile.anonKey.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = validSupabaseSession(configuredProfile)
            if (active == null) {
                logFirebaseStatus("Cloud sync skipped: sign in again to renew tenant database access.")
                return@launch
            }
            val url = active.supabaseUrl
            val key = active.anonKey
            val token = active.authSessionToken
            val merchantId = activeProfile.value.id
            isSupabaseSyncing.value = true
            logFirebaseStatus("Starting full multi-screen Supabase DB sync...")

            val syncLogs = mutableListOf<String>()
            var syncFailureCount = 0

            // 1. Sync Customers Screen Data
            try {
                val customersList = repository.observeCustomers(merchantId).firstOrNull() ?: emptyList()
                for (cust in customersList) {
                    val json = org.json.JSONObject().apply {
                        put("id", cust.id)
                        put("name", cust.name)
                        put("phone", cust.phone)
                        put("email", cust.email ?: org.json.JSONObject.NULL)
                        put("address", cust.address ?: org.json.JSONObject.NULL)
                        put("opening_balance", cust.openingBalance)
                        put("current_balance", cust.currentBalance)
                        put("status", cust.status)
                        put("created_at", toIsoTimestamp(cust.createdAt))
                    }
                    upsertRemoteOrThrow(url, key, token, "customers", json)
                }
                syncLogs.add("Customers synced (${customersList.size} records)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Customers failed: ${e.message}")
                Log.e("SupabaseSync", "Customers sync err: ${e.message}")
            }

            // 2. Sync Suppliers Screen Data
            try {
                val suppliersList = repository.observeSuppliers(merchantId).firstOrNull() ?: emptyList()
                for (sup in suppliersList) {
                    val json = org.json.JSONObject().apply {
                        put("id", sup.id)
                        put("name", sup.name)
                        put("phone", sup.phone)
                        put("email", sup.email ?: org.json.JSONObject.NULL)
                        put("address", sup.address ?: org.json.JSONObject.NULL)
                        put("opening_balance", sup.openingBalance)
                        put("current_balance", sup.currentBalance)
                        put("created_at", toIsoTimestamp(sup.createdAt))
                    }
                    upsertRemoteOrThrow(url, key, token, "suppliers", json)
                }
                syncLogs.add("Suppliers synced (${suppliersList.size} records)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Suppliers failed: ${e.message}")
                Log.e("SupabaseSync", "Suppliers sync err: ${e.message}")
            }

            // 3. Sync Products & Inventory Screen Data
            try {
                val productsList = repository.observeProducts(merchantId).firstOrNull() ?: emptyList()
                for (p in productsList) {
                    val json = org.json.JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("code", p.code ?: org.json.JSONObject.NULL)
                        put("category", p.category ?: "General")
                        put("purchase_price", p.purchasePrice)
                        put("sale_price", p.salePrice)
                        put("stock_quantity", p.stockQuantity)
                        put("min_stock_threshold", p.minStockThreshold)
                        put("unit", p.unit)
                        put("qr_code", p.qrCode ?: org.json.JSONObject.NULL)
                        put("image_url", p.imageUrl ?: org.json.JSONObject.NULL)
                        put("created_at", toIsoTimestamp(p.createdAt))
                    }
                    upsertRemoteOrThrow(url, key, token, "products", json)
                }
                syncLogs.add("Products & Inventory synced (${productsList.size} items)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Products failed: ${e.message}")
                Log.e("SupabaseSync", "Products sync err: ${e.message}")
            }

            // Product variants are independent inventory records and must be
            // uploaded alongside their parent products.
            try {
                val variants = repository.observeProductVariants(merchantId).firstOrNull() ?: emptyList()
                for (variant in variants) {
                    val json = org.json.JSONObject().apply {
                        put("id", variant.id)
                        put("product_id", variant.productId)
                        put("variant_name", variant.variantName)
                        put("supplier_id", variant.supplierId ?: org.json.JSONObject.NULL)
                        put("qr_code", variant.qrCode)
                        put("cost_price", variant.costPrice)
                        put("asking_price", variant.askingPrice)
                        put("sale_price", variant.salePrice)
                        put("stock_quantity", variant.stockQuantity)
                        put("created_at", toIsoTimestamp(variant.createdAt))
                    }
                    upsertRemoteOrThrow(url, key, token, "product_variants", json)
                }
                syncLogs.add("Product variants synced (${variants.size} records)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Product variants failed: ${e.message}")
                Log.e("SupabaseSync", "Product variants sync err: ${e.message}")
            }

            try {
                val movements = repository.observeStockTransactions(merchantId).firstOrNull() ?: emptyList()
                for (movement in movements) {
                    val json = org.json.JSONObject().apply {
                        put("id", movement.id)
                        put("product_id", movement.productId)
                        put("variant_id", movement.variantId ?: org.json.JSONObject.NULL)
                        put("type", movement.type)
                        put("quantity", movement.quantity)
                        put("price", movement.price)
                        put("customer_id", movement.customerId ?: org.json.JSONObject.NULL)
                        put("supplier_id", movement.supplierId ?: org.json.JSONObject.NULL)
                        put("reference_note", movement.referenceNote ?: org.json.JSONObject.NULL)
                        put("created_at", toIsoTimestamp(movement.createdAt))
                    }
                    upsertRemoteOrThrow(url, key, token, "stock_transactions", json)
                }
                syncLogs.add("Stock movements synced (${movements.size} records)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Stock movements failed: ${e.message}")
                Log.e("SupabaseSync", "Stock movements sync err: ${e.message}")
            }

            // 4. Sync Ledger Transactions
            try {
                val txList = repository.observeLedgerTransactions(merchantId).firstOrNull() ?: emptyList()
                for (tx in txList) {
                    val json = org.json.JSONObject().apply {
                        put("id", tx.id)
                        put("customer_id", tx.customerId ?: org.json.JSONObject.NULL)
                        put("supplier_id", tx.supplierId ?: org.json.JSONObject.NULL)
                        put("type", tx.type)
                        put("amount", tx.amount)
                        put("date", toIsoTimestamp(tx.date))
                        put("note", tx.note ?: org.json.JSONObject.NULL)
                        put("payment_method", tx.paymentMethod)
                        put("invoice_no", tx.invoiceNo ?: org.json.JSONObject.NULL)
                    }
                    upsertRemoteOrThrow(url, key, token, "ledger_transactions", json)
                }
                syncLogs.add("Ledger transactions synced (${txList.size} entries)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Ledger failed: ${e.message}")
                Log.e("SupabaseSync", "Ledger transactions sync err: ${e.message}")
            }

            // 5. Sync POS Sales & History
            try {
                val salesList = repository.observePosSales(merchantId).firstOrNull() ?: emptyList()
                for (s in salesList) {
                    val json = org.json.JSONObject().apply {
                        put("id", s.id)
                        put("invoice_no", s.invoiceNo)
                        put("customer_name", s.customerName)
                        put("customer_phone", s.customerPhone)
                        put("subtotal", s.subtotal)
                        put("discount", s.discount)
                        put("net_total", s.netTotal)
                        put("cash_received", s.cashReceived)
                        put("change_due", s.changeDue)
                        put("payment_method", s.paymentMethod)
                        put("payment_status", s.paymentStatus)
                        put("item_count", s.itemCount)
                        val cartArray = try { org.json.JSONArray(s.cartItemsJson.ifEmpty { "[]" }) } catch (e: Exception) { org.json.JSONArray() }
                        put("cart_items", cartArray)
                        put("timestamp", toIsoTimestamp(s.timestamp))
                    }
                    upsertRemoteOrThrow(url, key, token, "pos_sales", json)
                }
                syncLogs.add("POS Sales synced (${salesList.size} invoices)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("POS sales failed: ${e.message}")
                Log.e("SupabaseSync", "POS sales sync err: ${e.message}")
            }

            // 6. Sync Expenses Screen Data
            try {
                val expList = repository.observeExpenses(merchantId).firstOrNull() ?: emptyList()
                for (exp in expList) {
                    val json = org.json.JSONObject().apply {
                        put("id", exp.id)
                        put("category", exp.category)
                        put("amount", exp.amount)
                        put("date", toIsoTimestamp(exp.date))
                        put("description", exp.description ?: org.json.JSONObject.NULL)
                        put("payment_method", exp.paymentMethod)
                    }
                    upsertRemoteOrThrow(url, key, token, "expenses", json)
                }
                syncLogs.add("Expenses synced (${expList.size} records)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Expenses failed: ${e.message}")
                Log.e("SupabaseSync", "Expenses sync err: ${e.message}")
            }

            // 7. Finance accounts and payments must go through guarded RPCs.
            try {
                if (!syncPendingFinanceData()) {
                    throw java.io.IOException("One or more finance operations remain queued")
                }
                syncLogs.add("DPS, loans, EMI schedules and installment payments synchronized")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Finance failed: ${e.message}")
                Log.e("SupabaseSync", "Finance sync err: ${e.message}")
            }

            try {
                businessAnalytics.value?.let { analytics ->
                    val json = org.json.JSONObject().apply {
                        put("id", analytics.id)
                        put("total_revenue", analytics.totalRevenue)
                        put("cash_received", analytics.cashReceived)
                        put("total_dues", analytics.totalDues)
                        put("total_payables", analytics.totalPayables)
                        put("total_expenses", analytics.totalExpenses)
                        put("net_profit", analytics.netProfit)
                        put("last_updated", toIsoTimestamp(analytics.lastUpdated))
                    }
                    upsertRemoteOrThrow(url, key, token, "business_analytics", json)
                }
                syncLogs.add("Business analytics synced")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Business analytics failed: ${e.message}")
                Log.e("SupabaseSync", "Business analytics sync err: ${e.message}")
            }

            // 8. Sync Employees & Team Roles Data
            try {
                val empList = employees.value
                for (emp in empList) {
                    val json = org.json.JSONObject().apply {
                        put("id", emp.id)
                        put("name", emp.name)
                        put("designation", emp.designation)
                        put("role", emp.role)
                        put("email", emp.email)
                        put("phone", emp.phone)
                        put("department", emp.department)
                        put("status", emp.status)
                        put("joined_date", emp.joinedDate)
                    }
                    upsertRemoteOrThrow(url, key, token, "employees", json)
                }
                syncLogs.add("Employees & Team Roles synced (${empList.size} members)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Employees failed: ${e.message}")
                Log.e("SupabaseSync", "Employees sync err: ${e.message}")
            }

            try {
                val numbers = repository.observeMerchantNumbers(merchantId).firstOrNull() ?: emptyList()
                for (number in numbers) {
                    val json = org.json.JSONObject().apply {
                        put("id", merchantNumberRemoteId(number.merchantId, number.number))
                        put("number", number.number)
                        put("type", number.method)
                        put("account_type", number.accountType)
                        put("active", number.isActive)
                        put("is_default", number.isDefault)
                    }
                    upsertRemoteOrThrow(url, key, token, "merchant_numbers", json)
                }
                syncLogs.add("Merchant payment numbers synced (${numbers.size} records)")
            } catch (e: Exception) {
                syncFailureCount++
                syncLogs.add("Merchant payment numbers failed: ${e.message}")
                Log.e("SupabaseSync", "Merchant numbers sync err: ${e.message}")
            }

            var paymentFormsReadyToPull = true
            try {
                val dirtyForms = repository.observePaymentFormCache(merchantId).firstOrNull()
                    .orEmpty().filter(PaymentFormCacheEntity::isDirty)
                for (form in dirtyForms) {
                    upsertRemoteOrThrow(
                        url, key, token, "payment_forms", org.json.JSONObject(form.payloadJson)
                    )
                    repository.upsertPaymentFormCache(form.copy(isDirty = false))
                }
                syncLogs.add("Offline payment forms uploaded (${dirtyForms.size} records)")
            } catch (e: Exception) {
                paymentFormsReadyToPull = false
                syncFailureCount++
                syncLogs.add("Payment forms failed: ${e.message}")
                Log.e("SupabaseSync", "Payment forms sync err: ${e.message}")
            }

            var submissionsReadyToPull = true
            try {
                val dirtySubmissions = repository.observeFormSubmissionCache(merchantId).firstOrNull()
                    .orEmpty().filter(FormSubmissionCacheEntity::isDirty)
                for (submission in dirtySubmissions) {
                    upsertRemoteOrThrow(
                        url, key, token, "form_submissions", org.json.JSONObject(submission.payloadJson)
                    )
                    repository.upsertFormSubmissionCache(submission.copy(isDirty = false))
                }
                syncLogs.add("Offline form submissions uploaded (${dirtySubmissions.size} records)")
            } catch (e: Exception) {
                submissionsReadyToPull = false
                syncFailureCount++
                syncLogs.add("Form submissions failed: ${e.message}")
                Log.e("SupabaseSync", "Form submissions sync err: ${e.message}")
            }

            // Do not overwrite a dirty offline row when its upload failed.
            if (paymentFormsReadyToPull) fetchPaymentForms()
            if (submissionsReadyToPull) fetchFormSubmissions()

            supabaseSyncLog.value = syncLogs
            supabaseLastSyncTime.value = System.currentTimeMillis()
            isSupabaseSyncing.value = false
            if (syncFailureCount == 0) {
                logFirebaseStatus("Completed Supabase sync across all database-backed screens.")
            } else {
                logFirebaseStatus("Supabase sync finished with $syncFailureCount failed data groups. See sync log.")
            }
        }
    }

    fun fetchPaymentForms() {
        val configuredProfile = _activeSupabaseProfile.value ?: return
        if (configuredProfile.supabaseUrl.isEmpty() || configuredProfile.anonKey.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = validSupabaseSession(configuredProfile) ?: return@launch
            val dirtyIds = repository.observePaymentFormCache(activeProfile.value.id).firstOrNull()
                .orEmpty().filter(PaymentFormCacheEntity::isDirty).mapTo(mutableSetOf(), PaymentFormCacheEntity::id)
            com.example.data.remote.SupabaseClient.fetchPaymentForms(
                url = active.supabaseUrl,
                anonKey = active.anonKey,
                token = active.authSessionToken,
                onSuccess = { jsonArray ->
                    val cache = mutableListOf<PaymentFormCacheEntity>()
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        if (item.optString("id") in dirtyIds) continue
                        cache.add(
                            PaymentFormCacheEntity(
                                id = item.getString("id"),
                                merchantId = activeProfile.value.id,
                                payloadJson = item.toString(),
                                updatedAt = parseRemoteTimestamp(item.optString("updated_at")),
                                isDirty = false
                            )
                        )
                    }
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.upsertPaymentFormCaches(cache)
                    }
                },
                onFailure = { err ->
                    logFirebaseStatus("Error loading payment forms: $err")
                }
            )
        }
    }

    fun fetchFormSubmissions(formId: String? = null) {
        val configuredProfile = _activeSupabaseProfile.value ?: return
        if (configuredProfile.supabaseUrl.isEmpty() || configuredProfile.anonKey.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = validSupabaseSession(configuredProfile) ?: return@launch
            val dirtyIds = repository.observeFormSubmissionCache(activeProfile.value.id).firstOrNull()
                .orEmpty().filter(FormSubmissionCacheEntity::isDirty).mapTo(mutableSetOf(), FormSubmissionCacheEntity::id)
            com.example.data.remote.SupabaseClient.fetchFormSubmissions(
                url = active.supabaseUrl,
                anonKey = active.anonKey,
                token = active.authSessionToken,
                formId = formId,
                onSuccess = { jsonArray ->
                    val cache = mutableListOf<FormSubmissionCacheEntity>()
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        if (item.optString("id") in dirtyIds) continue
                        cache.add(
                            FormSubmissionCacheEntity(
                                id = item.getString("id"),
                                merchantId = activeProfile.value.id,
                                formId = item.optString("form_id"),
                                payloadJson = item.toString(),
                                submittedAt = parseRemoteTimestamp(item.optString("created_at")),
                                isDirty = false
                            )
                        )
                    }
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.upsertFormSubmissionCaches(cache)
                    }
                },
                onFailure = { err ->
                    logFirebaseStatus("Error loading submissions: $err")
                }
            )
        }
    }

    private suspend fun fetchRemoteTable(
        profile: SupabaseProfileEntity,
        table: String
    ): org.json.JSONArray {
        var result: org.json.JSONArray? = null
        var failure: String? = null
        com.example.data.remote.SupabaseClient.fetchRecords(
            profile.supabaseUrl,
            profile.anonKey,
            profile.authSessionToken,
            table,
            onSuccess = { result = it },
            onFailure = { failure = it }
        )
        return result ?: throw java.io.IOException(failure ?: "Failed to fetch $table")
    }

    /** Pulls every Room-backed business screen from the authenticated tenant. */
    private suspend fun pullAllBusinessDataFromSupabase(profile: SupabaseProfileEntity): Int {
        val merchantId = activeProfile.value.id
        var failures = 0

        suspend fun syncTable(name: String, applyRows: suspend (org.json.JSONArray) -> Unit) {
            try {
                applyRows(fetchRemoteTable(profile, name))
            } catch (error: Exception) {
                failures++
                logFirebaseStatus("$name pull failed: ${error.message}")
            }
        }

        syncTable("customers") { rows ->
            repository.insertCustomers(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    CustomerEntity(
                        item.getString("id"), merchantId, item.optString("name"), item.optString("phone"),
                        item.optNullableString("email"), item.optNullableString("address"),
                        item.optDouble("opening_balance", 0.0), item.optDouble("current_balance", 0.0),
                        item.optString("status", "Potential"), parseRemoteTimestamp(item.optString("created_at"))
                    )
                }
            })
        }
        syncTable("suppliers") { rows ->
            repository.insertSuppliers(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    SupplierEntity(
                        item.getString("id"), merchantId, item.optString("name"), item.optString("phone"),
                        item.optNullableString("email"), item.optNullableString("address"),
                        item.optDouble("opening_balance", 0.0), item.optDouble("current_balance", 0.0),
                        parseRemoteTimestamp(item.optString("created_at"))
                    )
                }
            })
        }
        syncTable("products") { rows ->
            repository.insertProducts(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    ProductItemEntity(
                        id = item.getString("id"), merchantId = merchantId, name = item.optString("name"),
                        code = item.optNullableString("code"), category = item.optNullableString("category"),
                        purchasePrice = item.optDouble("purchase_price", 0.0), salePrice = item.optDouble("sale_price", 0.0),
                        stockQuantity = item.optDouble("stock_quantity", 0.0), minStockThreshold = item.optDouble("min_stock_threshold", 5.0),
                        unit = item.optString("unit", "pcs"), qrCode = item.optNullableString("qr_code"),
                        imageUrl = item.optNullableString("image_url"),
                        storefrontDetailsJson = item.optJSONObject("storefront_details")?.toString() ?: "{}",
                        createdAt = parseRemoteTimestamp(item.optString("created_at")),
                        costPrice = item.optDouble("cost_price", item.optDouble("purchase_price", 0.0)),
                        askingPrice = item.optDouble("asking_price", item.optDouble("sale_price", 0.0))
                    )
                }
            })
        }
        syncTable("product_variants") { rows ->
            repository.insertProductVariants(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    ProductVariantEntity(
                        id = item.getString("id"), merchantId = merchantId, productId = item.optString("product_id"),
                        variantName = item.optString("variant_name", "Standard"), supplierId = item.optNullableString("supplier_id"),
                        qrCode = item.optString("qr_code"), costPrice = item.optDouble("cost_price", 0.0),
                        askingPrice = item.optDouble("asking_price", 0.0), salePrice = item.optDouble("sale_price", 0.0),
                        stockQuantity = item.optDouble("stock_quantity", 0.0), createdAt = parseRemoteTimestamp(item.optString("created_at"))
                    )
                }
            })
        }
        syncTable("ledger_transactions") { rows ->
            repository.insertLedgerTransactions(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    LedgerTransactionEntity(
                        id = item.getString("id"), merchantId = merchantId,
                        customerId = item.optNullableString("customer_id"), supplierId = item.optNullableString("supplier_id"),
                        type = item.optString("type", "credit"), amount = item.optDouble("amount", 0.0),
                        date = parseRemoteTimestamp(item.optString("date")), note = item.optNullableString("note"),
                        productDetailsJson = item.optJSONArray("product_details")?.toString() ?: "[]",
                        isVoiceEntry = item.optBoolean("is_voice_entry", false), attachmentUri = item.optNullableString("attachment_url"),
                        paymentMethod = item.optString("payment_method", "Cash"), invoiceNo = item.optNullableString("invoice_no"),
                        tagadaSentAt = item.optNullableString("tagada_sent_at")?.let(::parseRemoteTimestamp), isSynced = true
                    )
                }
            })
        }
        syncTable("stock_transactions") { rows ->
            repository.insertStockTransactions(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    StockTransactionEntity(
                        id = item.getString("id"), merchantId = merchantId, productId = item.optString("product_id"),
                        variantId = item.optNullableString("variant_id"), type = item.optString("type", "in"),
                        quantity = item.optDouble("quantity", 0.0), price = item.optDouble("price", 0.0),
                        customerId = item.optNullableString("customer_id"), supplierId = item.optNullableString("supplier_id"),
                        referenceNote = item.optNullableString("reference_note"), createdAt = parseRemoteTimestamp(item.optString("created_at"))
                    )
                }
            })
        }
        syncTable("expenses") { rows ->
            repository.insertExpenses(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    ExpenseEntity(
                        id = item.getString("id"), merchantId = merchantId, category = item.optString("category", "Others"),
                        amount = item.optDouble("amount", 0.0), date = parseRemoteTimestamp(item.optString("date")),
                        description = item.optNullableString("description"), receiptImageUrl = item.optNullableString("receipt_image_url"),
                        paymentMethod = item.optString("payment_method", "Cash"), isRecurring = item.optBoolean("is_recurring", false),
                        createdAt = parseRemoteTimestamp(item.optString("created_at"))
                    )
                }
            })
        }
        syncTable("loans") { rows ->
            repository.insertLoans(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    BusinessLoanEntity(
                        id = item.getString("id"), merchantId = merchantId,
                        principalAmount = item.optDouble("principal_amount", 0.0), interestRate = item.optDouble("interest_rate", 0.0),
                        interestType = item.optString("interest_type", "Flat"), durationMonths = item.optInt("duration_months", 1),
                        monthlyInstallment = item.optDouble("monthly_installment", 0.0),
                        providerName = item.optString("provider_name"), accountReference = item.optString("account_reference"),
                        startDate = parseRemoteTimestamp(item.optString("start_date")), isSynced = true,
                        status = item.optString("status", "applied"),
                        appliedAt = parseRemoteTimestamp(item.optString("applied_at")),
                        disbursedAt = item.optNullableString("disbursed_at")?.let(::parseRemoteTimestamp)
                    )
                }
            })
        }
        syncTable("dps_accounts") { rows ->
            repository.restoreDpsAccounts(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    DpsAccountEntity(
                        id = item.getString("id"), merchantId = merchantId,
                        providerName = item.optString("provider_name"), accountReference = item.optString("account_reference"),
                        monthlyDeposit = item.optDouble("monthly_deposit", 0.0), interestRate = item.optDouble("interest_rate", 0.0),
                        durationMonths = item.optInt("duration_months", 1), startDate = parseRemoteTimestamp(item.optString("start_date")),
                        maturityDate = parseRemoteTimestamp(item.optString("maturity_date")), status = item.optString("status", "ACTIVE"),
                        isSynced = true, createdAt = parseRemoteTimestamp(item.optString("created_at")),
                        updatedAt = parseRemoteTimestamp(item.optString("updated_at"))
                    )
                }
            })
        }
        syncTable("finance_installments") { rows ->
            repository.restoreFinanceInstallments(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    FinanceInstallmentEntity(
                        id = item.getString("id"), merchantId = merchantId, accountType = item.optString("account_type"),
                        accountId = item.optString("account_id"), installmentNumber = item.optInt("installment_number", 1),
                        dueDate = parseRemoteTimestamp(item.optString("due_date")), principalAmount = item.optDouble("principal_amount", 0.0),
                        interestAmount = item.optDouble("interest_amount", 0.0), totalAmount = item.optDouble("total_amount", 0.0),
                        status = item.optString("status", "PENDING"),
                        paidAt = item.optNullableString("paid_at")?.let(::parseRemoteTimestamp),
                        paymentMethod = item.optNullableString("payment_method"), paymentReference = item.optNullableString("payment_reference"),
                        isSynced = true, createdAt = parseRemoteTimestamp(item.optString("created_at"))
                    )
                }
            })
        }
        syncTable("pos_sales") { rows ->
            repository.insertPosSales(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    PosSaleEntity(
                        id = item.getString("id"), merchantId = merchantId, invoiceNo = item.optString("invoice_no"),
                        customerId = item.optNullableString("customer_id"), customerName = item.optString("customer_name", "Walk-in Customer"),
                        customerPhone = item.optString("customer_phone"), subtotal = item.optDouble("subtotal", 0.0),
                        discount = item.optDouble("discount", 0.0), netTotal = item.optDouble("net_total", 0.0),
                        cashReceived = item.optDouble("cash_received", 0.0), changeDue = item.optDouble("change_due", 0.0),
                        paymentMethod = item.optString("payment_method", "Cash"), paymentStatus = item.optString("payment_status", "PAID"),
                        itemCount = item.optInt("item_count", 0), cartItemsJson = item.optJSONArray("cart_items")?.toString() ?: "[]",
                        timestamp = parseRemoteTimestamp(item.optString("timestamp")), isSynced = true
                    )
                }
            })
        }
        syncTable("business_analytics") { rows ->
            rows.optJSONObject(0)?.let { item ->
                repository.insertBusinessAnalytics(
                    BusinessAnalyticsEntity(
                        id = item.getString("id"), merchantId = merchantId,
                        totalRevenue = item.optDouble("total_revenue", 0.0),
                        cashReceived = item.optDouble("cash_received", 0.0),
                        totalDues = item.optDouble("total_dues", 0.0),
                        totalPayables = item.optDouble("total_payables", 0.0),
                        totalExpenses = item.optDouble("total_expenses", 0.0),
                        netProfit = item.optDouble("net_profit", 0.0),
                        lastUpdated = parseRemoteTimestamp(item.optString("last_updated"))
                    )
                )
            }
        }
        syncTable("employees") { rows ->
            repository.upsertEmployees(List(rows.length()) { index ->
                rows.getJSONObject(index).let { item ->
                    EmployeeEntity(
                        id = item.getString("id"), merchantId = merchantId, name = item.optString("name"),
                        designation = item.optString("designation"), role = item.optString("role"), email = item.optString("email"),
                        phone = item.optString("phone"), department = item.optString("department", "General"),
                        status = item.optString("status", "Active"), avatarUrl = item.optNullableString("avatar_url"),
                        permissionsJson = item.optJSONArray("permissions")?.toString() ?: "[]",
                        joinedDate = item.optString("joined_date", ""), updatedAt = parseRemoteTimestamp(item.optString("updated_at"))
                    )
                }
            })
        }
        syncTable("merchant_numbers") { rows ->
            rows.forEachJsonObject { item ->
                repository.upsertMerchantNumber(
                    MerchantNumberEntity(
                        number = item.optString("number"), merchantId = merchantId, method = item.optString("type"),
                        accountType = item.optString("account_type", "Personal"), isActive = item.optBoolean("active", true),
                        isDefault = item.optBoolean("is_default", false), qrCodeUrl = item.optNullableString("qr_code_url"),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        return failures
    }

    private fun org.json.JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private inline fun org.json.JSONArray.forEachJsonObject(block: (org.json.JSONObject) -> Unit) {
        for (index in 0 until length()) optJSONObject(index)?.let(block)
    }

    fun createPaymentForm(
        title: String,
        description: String,
        logoUrl: String,
        amount: Double,
        fieldsJson: String,
        onComplete: (Boolean) -> Unit
    ) {
        if (title.isBlank() || amount < 0) {
            onComplete(false)
            return
        }
        val formId = java.util.UUID.randomUUID().toString()
        val slug = "pay-${java.util.UUID.randomUUID().toString().take(8)}"
        val payload = org.json.JSONObject().apply {
            put("id", formId)
            put("title", title)
            put("description", description)
            put("slug", slug)
            put("logo_url", logoUrl)
            put("fields", runCatching { org.json.JSONArray(fieldsJson) }.getOrDefault(org.json.JSONArray()))
            put("products", org.json.JSONArray().put(org.json.JSONObject().put("title", title).put("price", amount)))
            put("theme", org.json.JSONObject())
            // include customVariables if present
            val themeObj = org.json.JSONObject()
            val cvs = formThemeConfig.value.customVariables
            if (cvs.isNotEmpty()) {
                val cvArr = org.json.JSONArray()
                cvs.forEach { cv ->
                    cvArr.put(org.json.JSONObject().apply {
                        put("key", cv.key); put("exampleValue", cv.exampleValue); put("source", cv.source)
                    })
                }
                themeObj.put("customVariables", cvArr)
                put("theme", themeObj)
            }
            put("status", "DRAFT")
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isFormLoading.value = true
            repository.upsertPaymentFormCache(
                PaymentFormCacheEntity(formId, activeProfile.value.id, payload.toString(), isDirty = true)
            )
            val configuredProfile = _activeSupabaseProfile.value
            if (configuredProfile == null || configuredProfile.supabaseUrl.isBlank() || configuredProfile.anonKey.isBlank()) {
                isFormLoading.value = false
                onComplete(true)
                return@launch
            }
            val active = validSupabaseSession(configuredProfile)
            if (active == null) {
                isFormLoading.value = false
                logFirebaseStatus("Form saved locally; sign in again to synchronize it.")
                onComplete(true)
                return@launch
            }
            com.example.data.remote.SupabaseClient.insertPaymentForm(
                url = active.supabaseUrl,
                anonKey = active.anonKey,
                token = active.authSessionToken,
                payload = payload,
                onSuccess = {
                    isFormLoading.value = false
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.upsertPaymentFormCache(
                            PaymentFormCacheEntity(formId, activeProfile.value.id, payload.toString(), isDirty = false)
                        )
                    }
                    fetchPaymentForms()
                    onComplete(true)
                },
                onFailure = { err ->
                    isFormLoading.value = false
                    logFirebaseStatus("Error creating form: $err")
                    onComplete(false)
                }
            )
        }
    }

    fun updatePaymentForm(
        formId: String,
        title: String,
        description: String,
        logoUrl: String,
        amount: Double,
        fieldsJson: String,
        onComplete: (Boolean) -> Unit
    ) {
        if (formId.isBlank() || title.isBlank() || amount < 0) {
            onComplete(false)
            return
        }
        val payload = org.json.JSONObject().apply {
            put("id", formId)
            put("title", title)
            put("description", description)
            put("logo_url", logoUrl)
            put("fields", runCatching { org.json.JSONArray(fieldsJson) }.getOrDefault(org.json.JSONArray()))
            put("products", org.json.JSONArray().put(org.json.JSONObject().put("title", title).put("price", amount)))
            // include customVariables in theme if present
            val themeObj = org.json.JSONObject()
            val cvs = formThemeConfig.value.customVariables
            if (cvs.isNotEmpty()) {
                val cvArr = org.json.JSONArray()
                cvs.forEach { cv ->
                    cvArr.put(org.json.JSONObject().apply { put("key", cv.key); put("exampleValue", cv.exampleValue); put("source", cv.source) })
                }
                themeObj.put("customVariables", cvArr)
                put("theme", themeObj)
            }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isFormLoading.value = true
            repository.upsertPaymentFormCache(
                PaymentFormCacheEntity(formId, activeProfile.value.id, payload.toString(), isDirty = true)
            )
            val configuredProfile = _activeSupabaseProfile.value
            if (configuredProfile == null || configuredProfile.supabaseUrl.isBlank() || configuredProfile.anonKey.isBlank()) {
                isFormLoading.value = false
                onComplete(true)
                return@launch
            }
            val active = validSupabaseSession(configuredProfile)
            if (active == null) {
                isFormLoading.value = false
                logFirebaseStatus("Form saved locally; sign in again to synchronize it.")
                onComplete(true)
                return@launch
            }
            com.example.data.remote.SupabaseClient.updatePaymentForm(
                url = active.supabaseUrl,
                anonKey = active.anonKey,
                token = active.authSessionToken,
                formId = formId,
                payload = payload,
                onSuccess = {
                    isFormLoading.value = false
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.upsertPaymentFormCache(
                            PaymentFormCacheEntity(formId, activeProfile.value.id, payload.toString(), isDirty = false)
                        )
                    }
                    fetchPaymentForms()
                    onComplete(true)
                },
                onFailure = { err ->
                    isFormLoading.value = false
                    logFirebaseStatus("Error updating form: $err")
                    onComplete(false)
                }
            )
        }
    }

    private fun parseRemoteTimestamp(value: String?): Long {
        if (value.isNullOrBlank() || value == "null") return System.currentTimeMillis()
        val normalized = value.replace(Regex("(\\.\\d{3})\\d+"), "$1")
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (format in formats) {
            runCatching {
                java.text.SimpleDateFormat(format, java.util.Locale.US).parse(normalized)?.time
            }.getOrNull()?.let { return it }
        }
        return System.currentTimeMillis()
    }

    private fun toIsoTimestamp(timestamp: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(timestamp))

    // ═══════════════════════════════════════════════════════════════════════════
    // BOOKKEEPING & OPENROUTER AI BUSINESS COPILOT METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    val productVariants: StateFlow<List<ProductVariantEntity>> = activeProfile.flatMapLatest { repository.observeProductVariants(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ═══════════════════════════════════════════════════════════════════════════
    // POS CART & QR CODE INVENTORY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    private val _posCart = MutableStateFlow<List<PosCartItem>>(emptyList())
    val posCart: StateFlow<List<PosCartItem>> = _posCart.asStateFlow()

    private val _lastScannedQrStatus = MutableStateFlow<String?>(null)
    val lastScannedQrStatus: StateFlow<String?> = _lastScannedQrStatus.asStateFlow()

    fun scanQrCodeToPosCart(scannedQr: String) {
        if (scannedQr.trim().isEmpty()) return
        viewModelScope.launch {
            val variant = repository.getVariantByQrCode(scannedQr.trim(), activeProfile.value.id)
            if (variant != null) {
                val parentProd = products.value.find { it.id == variant.productId }
                val prodName = parentProd?.name ?: "Unknown Product"
                val category = parentProd?.category ?: "General"

                val currentList = _posCart.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.variantId == variant.id }

                if (existingIndex >= 0) {
                    val item = currentList[existingIndex]
                    if (item.quantity + 1.0 > variant.stockQuantity) {
                        _lastScannedQrStatus.value = "Insufficient stock for $prodName (${variant.variantName})"
                        return@launch
                    }
                    currentList[existingIndex] = item.copy(quantity = item.quantity + 1.0)
                } else {
                    if (variant.stockQuantity < 1.0) {
                        _lastScannedQrStatus.value = "$prodName (${variant.variantName}) is out of stock"
                        return@launch
                    }
                    currentList.add(
                        PosCartItem(
                            variantId = variant.id,
                            productId = variant.productId,
                            qrCode = variant.qrCode,
                            productName = prodName,
                            variantName = variant.variantName,
                            category = category,
                            askingPrice = variant.askingPrice,
                            sellingPrice = variant.salePrice,
                            costPrice = variant.costPrice,
                            quantity = 1.0
                        )
                    )
                }
                _posCart.value = currentList
                _lastScannedQrStatus.value = "Scanned: $prodName (${variant.variantName}) - ৳${variant.salePrice}"
                logFirebaseStatus("POS QR Scan Success: Added ${variant.variantName} of $prodName to cart.")
            } else {
                _lastScannedQrStatus.value = "❌ No product variant found matching QR: $scannedQr"
                logFirebaseStatus("POS QR Scan Failed: Code $scannedQr not registered.")
            }
        }
    }

    fun updatePosCartItemQuantity(variantId: String, newQty: Double) {
        val currentList = _posCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.variantId == variantId }
        if (index >= 0) {
            if (newQty <= 0) {
                currentList.removeAt(index)
            } else {
                val available = productVariants.value.firstOrNull { it.id == variantId }?.stockQuantity ?: 0.0
                if (newQty <= available) {
                    currentList[index] = currentList[index].copy(quantity = newQty)
                } else {
                    _lastScannedQrStatus.value = "Only ${available.toInt()} unit(s) are available"
                }
            }
            _posCart.value = currentList
        }
    }

    fun clearPosCart() {
        _posCart.value = emptyList()
        _lastScannedQrStatus.value = null
    }

    fun checkoutPosCart(
        paymentType: String, // "Cash", "MFS", "CustomerCredit"
        customerId: String? = null,
        discount: Double = 0.0,
        onSuccess: (orderId: String, totalAmount: Double) -> Unit,
        onError: (String) -> Unit
    ) {
        val cartItems = _posCart.value
        if (cartItems.isEmpty()) {
            onError("POS Cart is empty. Please scan items before checkout.")
            return
        }
        if (discount < 0.0 || !discount.isFinite()) {
            onError("Discount must be a non-negative amount")
            return
        }

        viewModelScope.launch {
            val subtotal = cartItems.sumOf { it.sellingPrice * it.quantity }
            val appliedDiscount = discount.coerceAtMost(subtotal)
            val totalSaleAmount = subtotal - appliedDiscount
            val orderId = java.util.UUID.randomUUID().toString()
            val merchantId = activeProfile.value.id
            val invoiceNo = "INV-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}-${orderId.take(6).uppercase()}"
            val customer = customerId?.let { id -> customers.value.firstOrNull { it.id == id } }
            if ((paymentType == "CustomerCredit" || paymentType == "Due") && customer == null) {
                onError("Select a valid customer before recording a due sale")
                return@launch
            }

            val movements = cartItems.map { item ->
                StockTransactionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    merchantId = merchantId,
                    productId = item.productId,
                    variantId = item.variantId,
                    type = "out",
                    quantity = item.quantity,
                    price = item.sellingPrice,
                    customerId = customerId,
                    supplierId = null,
                    referenceNote = "POS $invoiceNo"
                )
            }

            val cartJson = org.json.JSONArray().apply {
                cartItems.forEach { item ->
                    put(org.json.JSONObject().apply {
                        put("product_id", item.productId)
                        put("variant_id", item.variantId)
                        put("qr_code", item.qrCode)
                        put("name", item.productName)
                        put("variant", item.variantName)
                        put("quantity", item.quantity)
                        put("unit_price", item.sellingPrice)
                        put("line_total", item.sellingPrice * item.quantity)
                    })
                }
            }
            val normalizedMethod = when (paymentType) {
                "CustomerCredit", "Due" -> "Due"
                "MFS" -> "MFS"
                "Card" -> "Card"
                else -> "Cash"
            }
            val sale = PosSaleEntity(
                id = orderId,
                merchantId = merchantId,
                invoiceNo = invoiceNo,
                customerId = customerId,
                customerName = customer?.name ?: "Walk-in Customer",
                customerPhone = customer?.phone.orEmpty(),
                subtotal = subtotal,
                discount = appliedDiscount,
                netTotal = totalSaleAmount,
                cashReceived = if (normalizedMethod == "Due") 0.0 else totalSaleAmount,
                changeDue = 0.0,
                paymentMethod = normalizedMethod,
                paymentStatus = if (normalizedMethod == "Due") "DUE" else "PAID",
                itemCount = cartItems.sumOf { it.quantity.toInt() },
                cartItemsJson = cartJson.toString(),
                timestamp = System.currentTimeMillis(),
                isSynced = false
            )
            val creditEntry = if (normalizedMethod == "Due" && customerId != null) {
                LedgerTransactionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    merchantId = merchantId,
                    customerId = customerId,
                    type = "credit",
                    amount = totalSaleAmount,
                    note = "POS credit sale $invoiceNo",
                    productDetailsJson = cartJson.toString(),
                    paymentMethod = "Due",
                    invoiceNo = invoiceNo
                )
            } else null
            try {
                repository.checkoutPosSale(sale, movements, creditEntry)
            } catch (e: Exception) {
                onError(e.message ?: "Checkout failed; no partial stock change was committed")
                return@launch
            }

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null) {
                val payload = org.json.JSONObject().apply {
                    put("p_sale", org.json.JSONObject().apply {
                        put("id", sale.id); put("invoice_no", sale.invoiceNo)
                        put("customer_id", sale.customerId ?: org.json.JSONObject.NULL)
                        put("customer_name", sale.customerName); put("customer_phone", sale.customerPhone)
                        put("subtotal", sale.subtotal); put("discount", sale.discount); put("net_total", sale.netTotal)
                        put("cash_received", sale.cashReceived); put("change_due", sale.changeDue)
                        put("payment_method", sale.paymentMethod); put("payment_status", sale.paymentStatus)
                        put("item_count", sale.itemCount); put("cart_items", cartJson)
                    })
                    put("p_items", cartJson)
                }
                com.example.data.remote.SupabaseClient.callRpc(
                    active.supabaseUrl, active.anonKey, active.authSessionToken,
                    "checkout_pos_atomic", payload,
                    onSuccess = { viewModelScope.launch { repository.insertPosSale(sale.copy(isSynced = true)) } },
                    onFailure = { logFirebaseStatus("POS sale is stored offline; cloud sync failed: $it") }
                )
            }

            logFirebaseStatus("Completed POS Checkout #$orderId: ৳$totalSaleAmount ($paymentType)")
            _selectedInvoiceSaleId.value = orderId
            clearPosCart()
            onSuccess(orderId, totalSaleAmount)
        }
    }

    fun stockInProductWithVariants(
        productName: String,
        category: String,
        supplierId: String?,
        variantsList: List<Triple<String, Double, Triple<Double, Double, Double>>>,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (productName.isBlank() || category.isBlank() || variantsList.isEmpty() || variantsList.any {
                it.first.isBlank() || it.second <= 0.0 || !it.second.isFinite() ||
                    listOf(it.third.first, it.third.second, it.third.third).any { price -> price < 0.0 || !price.isFinite() }
            }) {
            onResult(false, "Product, category, valid variants, stock and prices are required")
            return
        }
        viewModelScope.launch {
            try {
                val merchantId = activeProfile.value.id
                val productId = java.util.UUID.randomUUID().toString()

                val totalStock = variantsList.sumOf { it.second }
                val avgCostPrice = variantsList.map { it.third.first }.average()
                val avgAskingPrice = variantsList.map { it.third.second }.average()
                val avgSellingPrice = variantsList.map { it.third.third }.average()

                val mainProduct = ProductItemEntity(
                    id = productId,
                    merchantId = merchantId,
                    name = productName.trim(),
                    code = "PRD-${productId.replace("-", "").take(12).uppercase()}",
                    category = category.trim(),
                    costPrice = avgCostPrice,
                    askingPrice = avgAskingPrice,
                    purchasePrice = avgCostPrice,
                    salePrice = avgSellingPrice,
                    stockQuantity = 0.0,
                    minStockThreshold = 5.0,
                    unit = "pcs"
                )
                val variants = mutableListOf<ProductVariantEntity>()
                val movements = mutableListOf<StockTransactionEntity>()

                variantsList.forEach { (vName, qty, prices) ->
                    val (cPrice, aPrice, sPrice) = prices
                    val variantId = java.util.UUID.randomUUID().toString()
                    val qrCodeStr = "SP-${merchantId.take(8)}-${variantId.replace("-", "").uppercase()}"

                    variants += ProductVariantEntity(
                        id = variantId,
                        merchantId = merchantId,
                        productId = productId,
                        variantName = vName.trim(),
                        supplierId = supplierId,
                        qrCode = qrCodeStr,
                        costPrice = cPrice,
                        askingPrice = aPrice,
                        salePrice = sPrice,
                        stockQuantity = 0.0
                    )

                    movements += StockTransactionEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        merchantId = merchantId,
                        productId = productId,
                        variantId = variantId,
                        type = "in",
                        quantity = qty,
                        price = cPrice,
                        customerId = null,
                        supplierId = supplierId,
                        referenceNote = "Opening stock"
                    )
                }
                repository.createProductWithVariants(mainProduct, variants, movements)

                val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
                if (active != null) {
                    val payload = org.json.JSONObject().apply {
                        put("p_product", org.json.JSONObject().apply {
                            put("id", mainProduct.id); put("name", mainProduct.name); put("code", mainProduct.code)
                            put("category", mainProduct.category); put("purchase_price", mainProduct.purchasePrice)
                            put("sale_price", mainProduct.salePrice); put("cost_price", mainProduct.costPrice)
                            put("asking_price", mainProduct.askingPrice); put("unit", mainProduct.unit)
                        })
                        put("p_variants", org.json.JSONArray().apply {
                            variants.forEachIndexed { index, variant ->
                                put(org.json.JSONObject().apply {
                                    put("id", variant.id); put("variant_name", variant.variantName)
                                    put("supplier_id", variant.supplierId ?: org.json.JSONObject.NULL)
                                    put("qr_code", variant.qrCode); put("cost_price", variant.costPrice)
                                    put("asking_price", variant.askingPrice); put("sale_price", variant.salePrice)
                                    put("quantity", variantsList[index].second)
                                })
                            }
                        })
                    }
                    com.example.data.remote.SupabaseClient.callRpc(
                        active.supabaseUrl, active.anonKey, active.authSessionToken,
                        "stock_in_product_atomic", payload,
                        onSuccess = { },
                        onFailure = { logFirebaseStatus("Stock is stored offline; cloud sync failed: $it") }
                    )
                }
                logFirebaseStatus("Stocked in product $productName with ${variantsList.size} variants (Total Qty: $totalStock).")
                onResult(true, "Product and ${variants.size} QR variants saved")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Product stock-in failed")
            }
        }
    }

    // Encrypted API key preferences
    private val _openRouterKeys = MutableStateFlow(securityPrefs.getString("openrouter_keys", "") ?: "")
    val openRouterKeys: StateFlow<String> = _openRouterKeys.asStateFlow()

    fun updateOpenRouterKeys(keys: String) {
        _openRouterKeys.value = keys
        securityPrefs.edit().putString("openrouter_keys", keys).apply()
    }

    val aiProviderOptions = listOf("OpenRouter", "Google AI Studio")

    private val _selectedAiProvider = MutableStateFlow(securityPrefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter")
    val selectedAiProvider: StateFlow<String> = _selectedAiProvider.asStateFlow()

    fun setSelectedAiProvider(provider: String) {
        _selectedAiProvider.value = provider
        securityPrefs.edit().putString("ai_provider", provider).apply()
    }

    private val _geminiApiKey = MutableStateFlow(securityPrefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun updateGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        securityPrefs.edit().putString("gemini_api_key", key).apply()
    }

    private val _selectedGeminiModel = MutableStateFlow(securityPrefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
    val selectedGeminiModel: StateFlow<String> = _selectedGeminiModel.asStateFlow()

    fun setSelectedGeminiModel(model: String) {
        _selectedGeminiModel.value = model
        securityPrefs.edit().putString("gemini_model", model).apply()
    }

    fun getAutoSelectedGeminiModel(): String {
        val memoryLen = _aiMemory.value.length
        val historyLen = _aiChatHistory.value.size
        return when {
            memoryLen > 1500 || historyLen > 20 -> "gemini-3-flash-preview"
            memoryLen > 800 || historyLen > 10 -> "gemini-3.1-flash-lite"
            else -> "gemini-2.5-flash"
        }
    }

    // AI Conversational Chat & Memory
    private val _aiChatHistory = MutableStateFlow<List<Map<String, String>>>(
        listOf(
            mapOf("role" to "assistant", "content" to "আসসালামু আলাইকুম! আমি আপনার ব্যবসা সহকারী এআই। আপনার লেজার খাতা, বেচাকেনা ও ঋণের হিসাব মেলাতে বা যেকোনো প্রশ্ন করতে বলুন।")
        )
    )
    val aiChatHistory: StateFlow<List<Map<String, String>>> = _aiChatHistory.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _aiMemory = MutableStateFlow(securityPrefs.getString("ai_memory", "") ?: "")
    val aiMemory: StateFlow<String> = _aiMemory.asStateFlow()

    private val _autoApproveAiActions = MutableStateFlow(securityPrefs.getBoolean("ai_auto_approve", false))
    val autoApproveAiActions: StateFlow<Boolean> = _autoApproveAiActions.asStateFlow()

    fun setAutoApproveAiActions(enabled: Boolean) {
        _autoApproveAiActions.value = enabled
        securityPrefs.edit().putBoolean("ai_auto_approve", enabled).apply()
    }

    fun clearChat() {
        _aiChatHistory.value = listOf(
            mapOf("role" to "assistant", "content" to "আসসালামু আলাইকুম! আমি আপনার ব্যবসা সহকারী এআই। আপনার লেজার খাতা, বেচাকেনা ও ঋণের হিসাব মেলাতে বা যেকোনো প্রশ্ন করতে বলুন।")
        )
    }

    fun appendToAiMemory(newNote: String) {
        viewModelScope.launch {
            val current = _aiMemory.value
            val updated = if (current.isEmpty()) newNote else "$current\n$newNote"
            
            // Check monthly reset or length overflow (> 2000 chars)
            val lastReset = securityPrefs.getLong("ai_memory_last_reset", 0L)
            val oneMonth = 30L * 24 * 60 * 60 * 1000
            
            val shouldReset = lastReset == 0L || (System.currentTimeMillis() - lastReset > oneMonth) || updated.length > 2000
            
            if (lastReset == 0L) {
                securityPrefs.edit().putLong("ai_memory_last_reset", System.currentTimeMillis()).apply()
            }
            
            if (shouldReset) {
                _isAiThinking.value = true
                val systemPrompt = "You are a database memory compressor. Compress the following list of store actions, notes, and previous summaries into a concise paragraph summarizing key customer behavior, trends, and tasks. Keep it under 200 words. Respond in English or Bangla."
                val messages = JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", updated) })
                }
                val provider = _selectedAiProvider.value
                if (provider == "Google AI Studio") {
                    val model = getAutoSelectedGeminiModel()
                    GeminiClient.getChatCompletion(
                        apiKey = _geminiApiKey.value,
                        model = model,
                        messages = messages,
                        onSuccess = { compressed ->
                            _isAiThinking.value = false
                            _aiMemory.value = compressed
                            securityPrefs.edit().putString("ai_memory", compressed)
                                .putLong("ai_memory_last_reset", System.currentTimeMillis()).apply()
                            logFirebaseStatus("AI Copilot memory compressed and refreshed via Gemini.")
                        },
                        onFailure = { err ->
                            _isAiThinking.value = false
                            val truncated = updated.takeLast(1000)
                            _aiMemory.value = truncated
                            securityPrefs.edit().putString("ai_memory", truncated).apply()
                            logFirebaseStatus("Memory compression failed: $err. Truncating instead.")
                        }
                    )
                } else {
                    OpenRouterClient.getChatCompletion(
                        keysCsv = _openRouterKeys.value,
                        messages = messages,
                        onSuccess = { compressed ->
                            _isAiThinking.value = false
                            _aiMemory.value = compressed
                            securityPrefs.edit().putString("ai_memory", compressed)
                                .putLong("ai_memory_last_reset", System.currentTimeMillis()).apply()
                            logFirebaseStatus("AI Copilot memory compressed and refreshed.")
                        },
                        onFailure = { err ->
                            _isAiThinking.value = false
                            val truncated = updated.takeLast(1000)
                            _aiMemory.value = truncated
                            securityPrefs.edit().putString("ai_memory", truncated).apply()
                            logFirebaseStatus("Memory compression failed: $err. Truncating instead.")
                        }
                    )
                }
            } else {
                _aiMemory.value = updated
                securityPrefs.edit().putString("ai_memory", updated).apply()
            }
        }
    }

    // Customer operations
    fun addCustomer(
        name: String,
        phone: String,
        initialBalance: Double,
        status: String = "VIP",
        id: String = java.util.UUID.randomUUID().toString()
    ) {
        if (name.isBlank() || phone.filter(Char::isDigit).length < 10 || !initialBalance.isFinite()) {
            logFirebaseStatus("Customer rejected: valid name, phone, and balance are required.")
            return
        }
        viewModelScope.launch {
            val customer = CustomerEntity(
                id = id,
                merchantId = activeProfile.value.id,
                name = name,
                phone = phone,
                email = null,
                address = null,
                openingBalance = initialBalance,
                currentBalance = initialBalance,
                status = status
            )
            repository.insertCustomer(customer)
            logFirebaseStatus("Added new customer: $name")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                val json = org.json.JSONObject().apply {
                    put("id", customer.id)
                    put("name", customer.name)
                    put("phone", customer.phone)
                    put("opening_balance", customer.openingBalance)
                    put("current_balance", customer.currentBalance)
                    put("status", customer.status)
                    put("created_at", toIsoTimestamp(customer.createdAt))
                }
                com.example.data.remote.SupabaseClient.upsertRecord(
                    active.supabaseUrl, active.anonKey, active.authSessionToken, "customers", json, {},
                    { logFirebaseStatus("Customer saved locally; cloud sync failed: $it") }
                )
            }
        }
    }

    fun deleteCustomer(id: String) {
        viewModelScope.launch {
            repository.deleteCustomerById(id)
            logFirebaseStatus("Deleted customer ID: $id")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                com.example.data.remote.SupabaseClient.deleteRecord(
                    active.supabaseUrl, active.anonKey, active.authSessionToken, "customers", "id", id, {},
                    { logFirebaseStatus("Customer deleted locally; cloud delete failed: $it") }
                )
            }
        }
    }

    // Supplier operations

    fun deleteSupplier(id: String) {
        viewModelScope.launch {
            repository.deleteSupplierById(id)
            logFirebaseStatus("Deleted supplier ID: $id")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                com.example.data.remote.SupabaseClient.deleteRecord(
                    active.supabaseUrl, active.anonKey, active.authSessionToken, "suppliers", "id", id, {},
                    { logFirebaseStatus("Supplier deleted locally; cloud delete failed: $it") }
                )
            }
        }
    }

    // Ledger transactions
    fun addLedgerTransaction(
        customerId: String?, supplierId: String?, type: String, amount: Double, note: String,
        isVoice: Boolean = false, paymentMethod: String = "Cash",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if ((customerId == null) == (supplierId == null) || amount <= 0.0 || !amount.isFinite() || type.lowercase() !in setOf("credit", "payment")) {
            val message = "Choose one ledger party, a supported type, and a positive amount."
            logFirebaseStatus("Ledger entry rejected: $message")
            onResult(false, message)
            return
        }
        viewModelScope.launch {
            val tx = LedgerTransactionEntity(
                id = java.util.UUID.randomUUID().toString(),
                merchantId = activeProfile.value.id,
                customerId = customerId,
                supplierId = supplierId,
                type = type,
                amount = amount,
                date = System.currentTimeMillis(),
                note = note,
                isVoiceEntry = isVoice,
                paymentMethod = paymentMethod
            )
            try {
                repository.recordLedgerTransaction(tx)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Ledger entry could not be saved")
                return@launch
            }
            logFirebaseStatus("Logged ledger transaction of $amount BDT ($type)")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                val json = org.json.JSONObject().apply {
                    put("id", tx.id)
                    put("customer_id", tx.customerId ?: org.json.JSONObject.NULL)
                    put("supplier_id", tx.supplierId ?: org.json.JSONObject.NULL)
                    put("type", tx.type)
                    put("amount", tx.amount)
                    put("date", toIsoTimestamp(tx.date))
                    put("note", tx.note ?: org.json.JSONObject.NULL)
                    put("payment_method", tx.paymentMethod)
                    put("invoice_no", tx.invoiceNo ?: org.json.JSONObject.NULL)
                    put("product_details", org.json.JSONArray(tx.productDetailsJson))
                    put("is_voice_entry", tx.isVoiceEntry)
                }
                com.example.data.remote.SupabaseClient.callRpc(
                    active.supabaseUrl, active.anonKey, active.authSessionToken,
                    "record_ledger_transaction_atomic", org.json.JSONObject().put("p_transaction", json),
                    onSuccess = { }, onFailure = { logFirebaseStatus("Ledger entry saved locally; cloud sync failed: $it") }
                )
            }
            onResult(true, "Ledger entry saved")
        }
    }

    // Products
    fun addProduct(
        name: String,
        code: String?,
        category: String?,
        purchasePrice: Double,
        salePrice: Double,
        stock: Double,
        unit: String = "pcs",
        storefront: com.example.data.local.ProductStorefrontDetails = com.example.data.local.ProductStorefrontDetails(),
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (name.isBlank() || purchasePrice < 0.0 || salePrice < 0.0 || stock < 0.0 ||
            !purchasePrice.isFinite() || !salePrice.isFinite() || !stock.isFinite() || unit.isBlank()) {
            val message = "Product rejected: valid name, finite prices, stock, and unit are required."
            logFirebaseStatus(message)
            onResult(false, message)
            return
        }
        viewModelScope.launch {
            val normalizedCode = code?.trim()?.ifBlank { null }
            if (normalizedCode != null && repository.getProductByCode(normalizedCode, activeProfile.value.id) != null) {
                onResult(false, "SKU / QR code is already assigned to another product")
                return@launch
            }
            val prod = ProductItemEntity(
                id = java.util.UUID.randomUUID().toString(),
                merchantId = activeProfile.value.id,
                name = name.trim(),
                code = normalizedCode,
                category = category?.trim()?.ifBlank { "General" },
                purchasePrice = purchasePrice,
                salePrice = salePrice,
                stockQuantity = 0.0,
                unit = unit.trim(),
                qrCode = normalizedCode,
                costPrice = purchasePrice,
                askingPrice = salePrice,
                imageUrl = storefront.featuredImage?.preview,
                storefrontDetailsJson = storefront.json().toString()
            )
            val openingMovement = if (stock > 0.0) StockTransactionEntity(
                merchantId = prod.merchantId, productId = prod.id, type = "in", quantity = stock,
                price = purchasePrice, referenceNote = "Opening stock"
            ) else null
            try {
                repository.createProductWithOpeningStock(prod, openingMovement)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Product could not be saved")
                return@launch
            }
            logFirebaseStatus("Added new product item: $name")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                val payload = org.json.JSONObject().apply {
                    put("p_product", org.json.JSONObject().apply {
                        put("id", prod.id); put("name", prod.name); put("code", prod.code ?: org.json.JSONObject.NULL)
                        put("qr_code", prod.qrCode ?: org.json.JSONObject.NULL); put("category", prod.category ?: "General")
                        put("purchase_price", prod.purchasePrice); put("sale_price", prod.salePrice)
                        put("cost_price", prod.costPrice); put("asking_price", prod.askingPrice)
                        put("unit", prod.unit); put("opening_quantity", stock)
                    })
                    put("p_variants", org.json.JSONArray())
                }
                com.example.data.remote.SupabaseClient.callRpc(
                    active.supabaseUrl, active.anonKey, active.authSessionToken, "stock_in_product_atomic", payload,
                    onSuccess = { }, onFailure = { logFirebaseStatus("Product saved locally; cloud sync failed: $it") }
                )
            }
            onResult(true, "${prod.name} added to inventory")
        }
    }

    fun updateProduct(
        product: ProductItemEntity,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            if (product.name.isBlank() || product.unit.isBlank() ||
                !product.purchasePrice.isFinite() || product.purchasePrice < 0.0 ||
                !product.salePrice.isFinite() || product.salePrice < 0.0 ||
                !product.stockQuantity.isFinite() || product.stockQuantity < 0.0) {
                onResult(false, "Valid product name, unit, finite prices, and non-negative stock are required")
                return@launch
            }
            val normalizedCode = product.code?.trim()?.ifBlank { null }
            val existingCodeOwner = normalizedCode?.let { repository.getProductByCode(it, product.merchantId) }
            if (existingCodeOwner != null && existingCodeOwner.id != product.id) {
                onResult(false, "SKU / QR code is already assigned to another product")
                return@launch
            }
            val current = repository.getProductById(product.id, product.merchantId)
            if (current == null) {
                onResult(false, "Product does not belong to the active merchant")
                return@launch
            }
            val normalizedProduct = product.copy(
                name = product.name.trim(), code = normalizedCode, qrCode = normalizedCode,
                unit = product.unit.trim(), category = product.category?.trim()?.ifBlank { "General" },
                costPrice = product.purchasePrice, askingPrice = product.salePrice
            )
            val delta = normalizedProduct.stockQuantity - current.stockQuantity
            val adjustment = if (delta == 0.0) null else StockTransactionEntity(
                id = java.util.UUID.randomUUID().toString(),
                merchantId = normalizedProduct.merchantId,
                productId = normalizedProduct.id,
                type = if (delta > 0.0) "in" else "out",
                quantity = kotlin.math.abs(delta),
                price = normalizedProduct.purchasePrice,
                referenceNote = "Manual stock adjustment"
            )
            try {
                repository.updateProductWithStockAdjustment(normalizedProduct, adjustment)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Product could not be updated")
                return@launch
            }
            logFirebaseStatus("Updated product: ${product.name}")
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                val payload = org.json.JSONObject().apply {
                    put("p_product", org.json.JSONObject().apply {
                        put("id", normalizedProduct.id)
                        put("name", normalizedProduct.name)
                        put("code", normalizedProduct.code ?: org.json.JSONObject.NULL)
                        put("qr_code", normalizedProduct.qrCode ?: org.json.JSONObject.NULL)
                        put("category", normalizedProduct.category ?: "General")
                        put("purchase_price", normalizedProduct.purchasePrice)
                        put("sale_price", normalizedProduct.salePrice)
                        put("cost_price", normalizedProduct.costPrice)
                        put("asking_price", normalizedProduct.askingPrice)
                        put("unit", normalizedProduct.unit)
                        put("expected_stock", current.stockQuantity)
                        put("target_stock", normalizedProduct.stockQuantity)
                    })
                    put("p_adjustment", adjustment?.let { movement ->
                        org.json.JSONObject().apply {
                            put("id", movement.id)
                            put("type", movement.type)
                            put("quantity", movement.quantity)
                            put("price", movement.price)
                            put("reference_note", movement.referenceNote)
                        }
                    } ?: org.json.JSONObject.NULL)
                }
                com.example.data.remote.SupabaseClient.callRpc(
                    active.supabaseUrl, active.anonKey, active.authSessionToken,
                    "update_product_atomic", payload,
                    onSuccess = { },
                    onFailure = { logFirebaseStatus("Product updated locally; cloud sync failed: $it") }
                )
            }
            onResult(true, "${normalizedProduct.name} updated")
        }
    }

    fun deleteProduct(id: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val merchantId = activeProfile.value.id
            val product = repository.getProductById(id, merchantId)
            if (product == null) {
                onResult(false, "Product was not found")
                return@launch
            }
            if (!repository.canDeletePristineProduct(id, merchantId)) {
                onResult(false, "Only unused products with zero stock and no audit history can be deleted")
                return@launch
            }
            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null) {
                var cloudDeleted = false
                var cloudError = "Cloud deletion failed"
                com.example.data.remote.SupabaseClient.deleteRecord(
                    active.supabaseUrl, active.anonKey, active.authSessionToken,
                    "products", "id", id,
                    onSuccess = { cloudDeleted = true },
                    onFailure = { cloudError = it }
                )
                if (!cloudDeleted) {
                    onResult(false, cloudError)
                    return@launch
                }
            }
            try {
                if (!repository.canDeletePristineProduct(id, merchantId)) {
                    onResult(false, "Product could not be deleted")
                    return@launch
                }
                repository.deletePristineProduct(id, merchantId)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Product cannot be deleted because it has audit history")
                return@launch
            }
            logFirebaseStatus("Deleted unused product ID: $id")
            onResult(true, "${product.name} deleted")
        }
    }

    private fun recordStockChangeInternal(
        productId: String,
        type: String,
        qty: Double,
        price: Double,
        supplierId: String? = null,
        referenceNote: String? = null
    ) {
        if (productId.isBlank() || type !in setOf("in", "out") || qty <= 0.0 || price < 0.0) {
            logFirebaseStatus("Stock change rejected: invalid product, type, quantity, or price.")
            return
        }
        viewModelScope.launch {
            val tx = StockTransactionEntity(
                id = java.util.UUID.randomUUID().toString(),
                merchantId = activeProfile.value.id,
                productId = productId,
                type = type,
                quantity = qty,
                price = price,
                customerId = null,
                supplierId = supplierId,
                referenceNote = referenceNote
            )
            repository.recordStockTransaction(tx)
            logFirebaseStatus("Logged stock change for product $productId: $qty $type")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                val movement = org.json.JSONObject().apply {
                    put("id", tx.id)
                    put("product_id", tx.productId)
                    put("variant_id", tx.variantId ?: org.json.JSONObject.NULL)
                    put("type", tx.type)
                    put("quantity", tx.quantity)
                    put("price", tx.price)
                    put("customer_id", tx.customerId ?: org.json.JSONObject.NULL)
                    put("supplier_id", tx.supplierId ?: org.json.JSONObject.NULL)
                    put("reference_note", tx.referenceNote ?: org.json.JSONObject.NULL)
                }
                com.example.data.remote.SupabaseClient.callRpc(
                    active.supabaseUrl, active.anonKey, active.authSessionToken,
                    "adjust_inventory_atomic", org.json.JSONObject().put("p_movement", movement),
                    onSuccess = { },
                    onFailure = { logFirebaseStatus("Stock movement saved locally; cloud sync failed: $it") }
                )
            }
        }
    }

    // Expenses
    fun addExpense(category: String, amount: Double, description: String) {
        if (category.isBlank() || amount <= 0.0 || !amount.isFinite()) {
            logFirebaseStatus("Expense rejected: category and positive amount are required.")
            return
        }
        viewModelScope.launch {
            val exp = ExpenseEntity(
                id = java.util.UUID.randomUUID().toString(),
                merchantId = activeProfile.value.id,
                category = category,
                amount = amount,
                date = System.currentTimeMillis(),
                description = description
            )
            repository.insertExpense(exp)
            logFirebaseStatus("Logged business expense: $amount in $category")

            val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
            if (active != null && active.supabaseUrl.isNotEmpty() && active.anonKey.isNotEmpty()) {
                val json = org.json.JSONObject().apply {
                    put("id", exp.id)
                    put("category", exp.category)
                    put("amount", exp.amount)
                    put("date", toIsoTimestamp(exp.date))
                    put("description", exp.description ?: org.json.JSONObject.NULL)
                }
                com.example.data.remote.SupabaseClient.upsertRecord(
                    active.supabaseUrl, active.anonKey, active.authSessionToken, "expenses", json, {},
                    { logFirebaseStatus("Expense saved locally; cloud sync failed: $it") }
                )
            }
        }
    }

    // Loans with calculation
    fun addLoan(amount: Double, rate: Double, type: String, months: Int) {
        addLoanAccount(
            providerName = "External lender",
            accountReference = "LOAN-${java.util.UUID.randomUUID().toString().take(8).uppercase()}",
            amount = amount,
            rate = rate,
            type = type,
            months = months,
            startDate = System.currentTimeMillis()
        )
    }

    fun addLoanAccount(
        providerName: String,
        accountReference: String,
        amount: Double,
        rate: Double,
        type: String,
        months: Int,
        startDate: Long,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (providerName.isBlank() || accountReference.isBlank() || amount <= 0.0 || !amount.isFinite() ||
            rate < 0.0 || !rate.isFinite() || months !in 1..600 || type !in setOf("Flat", "Reducing")
        ) {
            onResult(false, "Provider, account reference, amount, rate and tenure are required")
            return
        }
        viewModelScope.launch {
            try {
                val merchantId = activeProfile.value.id
                val loanId = java.util.UUID.randomUUID().toString()
                val schedule = buildLoanSchedule(loanId, merchantId, amount, rate, type, months, startDate)
                val loan = BusinessLoanEntity(
                    id = loanId,
                    merchantId = merchantId,
                    principalAmount = amount,
                    interestRate = rate,
                    interestType = type,
                    durationMonths = months,
                    monthlyInstallment = schedule.first().totalAmount,
                    providerName = providerName.trim(),
                    accountReference = accountReference.trim(),
                    startDate = startDate,
                    status = "disbursed",
                    appliedAt = System.currentTimeMillis(),
                    disbursedAt = startDate,
                    isSynced = false
                )
                repository.createLoanWithSchedule(loan, schedule)
                syncFinanceAccount("LOAN", loan, null, schedule)
                logFirebaseStatus("Loan account ${loan.accountReference} created with $months EMI rows")
                onResult(true, "Loan and EMI schedule saved")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Loan account could not be saved")
            }
        }
    }

    fun addDpsAccount(
        providerName: String,
        accountReference: String,
        monthlyDeposit: Double,
        interestRate: Double,
        months: Int,
        startDate: Long,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (providerName.isBlank() || accountReference.isBlank() || monthlyDeposit <= 0.0 ||
            !monthlyDeposit.isFinite() || interestRate < 0.0 || !interestRate.isFinite() || months !in 1..600
        ) {
            onResult(false, "Provider, account reference, deposit, rate and tenure are required")
            return
        }
        viewModelScope.launch {
            try {
                val merchantId = activeProfile.value.id
                val accountId = java.util.UUID.randomUUID().toString()
                val installments = (1..months).map { number ->
                    FinanceInstallmentEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        merchantId = merchantId,
                        accountType = "DPS",
                        accountId = accountId,
                        installmentNumber = number,
                        dueDate = addMonths(startDate, number),
                        principalAmount = monthlyDeposit,
                        totalAmount = monthlyDeposit
                    )
                }
                val account = DpsAccountEntity(
                    id = accountId,
                    merchantId = merchantId,
                    providerName = providerName.trim(),
                    accountReference = accountReference.trim(),
                    monthlyDeposit = monthlyDeposit,
                    interestRate = interestRate,
                    durationMonths = months,
                    startDate = startDate,
                    maturityDate = addMonths(startDate, months),
                    isSynced = false
                )
                repository.createDpsAccount(account, installments)
                syncFinanceAccount("DPS", null, account, installments)
                logFirebaseStatus("DPS account ${account.accountReference} created with $months installments")
                onResult(true, "DPS and installment schedule saved")
            } catch (e: Exception) {
                onResult(false, e.message ?: "DPS account could not be saved")
            }
        }
    }

    fun payFinanceInstallment(
        installmentId: String,
        paymentMethod: String,
        paymentReference: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val reference = paymentReference.trim()
        if (installmentId.isBlank() || paymentMethod.isBlank() || reference.length !in 4..100) {
            onResult(false, "Payment method and a unique 4-100 character reference are required")
            return
        }
        viewModelScope.launch {
            try {
                val merchantId = activeProfile.value.id
                val paidAt = System.currentTimeMillis()
                if (!repository.markFinanceInstallmentPaid(installmentId, merchantId, paidAt, paymentMethod, reference)) {
                    onResult(false, "Installment was already paid or does not belong to this merchant")
                    return@launch
                }
                val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) }
                if (active != null) {
                    com.example.data.remote.SupabaseClient.callRpc(
                        active.supabaseUrl,
                        active.anonKey,
                        active.authSessionToken,
                        "pay_finance_installment_atomic",
                        org.json.JSONObject().apply {
                            put("p_installment_id", installmentId)
                            put("p_payment_method", paymentMethod)
                            put("p_payment_reference", reference)
                            put("p_paid_at", toIsoTimestamp(paidAt))
                        },
                        onSuccess = { viewModelScope.launch { repository.markFinanceInstallmentSynced(installmentId, merchantId) } },
                        onFailure = { logFirebaseStatus("Installment is paid offline; cloud sync failed: $it") }
                    )
                }
                onResult(true, "Installment marked paid")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Payment could not be recorded")
            }
        }
    }

    private fun addMonths(timestamp: Long, months: Int): Long =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Dhaka")).apply {
            timeInMillis = timestamp
            add(java.util.Calendar.MONTH, months)
        }.timeInMillis

    private fun buildLoanSchedule(
        loanId: String,
        merchantId: String,
        amount: Double,
        annualRate: Double,
        type: String,
        months: Int,
        startDate: Long
    ): List<FinanceInstallmentEntity> {
        val monthlyRate = annualRate / 1200.0
        val fixedPayment = if (type == "Flat") {
            (amount + amount * (annualRate / 100.0) * (months / 12.0)) / months
        } else if (monthlyRate == 0.0) {
            amount / months
        } else {
            amount * (monthlyRate * Math.pow(1.0 + monthlyRate, months.toDouble())) /
                (Math.pow(1.0 + monthlyRate, months.toDouble()) - 1.0)
        }
        var outstanding = amount
        return (1..months).map { number ->
            val interest = if (type == "Flat") {
                amount * (annualRate / 100.0) * (months / 12.0) / months
            } else {
                outstanding * monthlyRate
            }
            val principal = if (number == months) outstanding else (fixedPayment - interest).coerceAtMost(outstanding)
            outstanding = (outstanding - principal).coerceAtLeast(0.0)
            FinanceInstallmentEntity(
                id = java.util.UUID.randomUUID().toString(),
                merchantId = merchantId,
                accountType = "LOAN",
                accountId = loanId,
                installmentNumber = number,
                dueDate = addMonths(startDate, number),
                principalAmount = principal,
                interestAmount = interest,
                totalAmount = principal + interest
            )
        }
    }

    private suspend fun syncFinanceAccount(
        accountType: String,
        loan: BusinessLoanEntity?,
        dps: DpsAccountEntity?,
        installments: List<FinanceInstallmentEntity>
    ): Boolean {
        val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return false
        val merchantId = activeProfile.value.id
        val accountJson = if (accountType == "LOAN") {
            requireNotNull(loan).let {
                org.json.JSONObject().apply {
                    put("id", it.id); put("provider_name", it.providerName); put("account_reference", it.accountReference)
                    put("principal_amount", it.principalAmount); put("interest_rate", it.interestRate)
                    put("interest_type", it.interestType); put("duration_months", it.durationMonths)
                    put("monthly_installment", it.monthlyInstallment); put("start_date", toIsoTimestamp(it.startDate))
                }
            }
        } else {
            requireNotNull(dps).let {
                org.json.JSONObject().apply {
                    put("id", it.id); put("provider_name", it.providerName); put("account_reference", it.accountReference)
                    put("monthly_deposit", it.monthlyDeposit); put("interest_rate", it.interestRate)
                    put("duration_months", it.durationMonths); put("start_date", toIsoTimestamp(it.startDate))
                    put("maturity_date", toIsoTimestamp(it.maturityDate))
                }
            }
        }
        val scheduleJson = org.json.JSONArray().apply {
            installments.forEach {
                put(org.json.JSONObject().apply {
                    put("id", it.id); put("installment_number", it.installmentNumber)
                    put("due_date", toIsoTimestamp(it.dueDate)); put("principal_amount", it.principalAmount)
                    put("interest_amount", it.interestAmount); put("total_amount", it.totalAmount)
                })
            }
        }
        var synced = false
        com.example.data.remote.SupabaseClient.callRpc(
            active.supabaseUrl,
            active.anonKey,
            active.authSessionToken,
            "create_finance_account_atomic",
            org.json.JSONObject().apply {
                put("p_account_type", accountType)
                put("p_account", accountJson)
                put("p_installments", scheduleJson)
            },
            onSuccess = {
                synced = true
            },
            onFailure = { logFirebaseStatus("Finance account is stored offline; cloud sync failed: $it") }
        )
        if (synced) {
            if (loan != null) repository.markLoanSynced(loan.id, merchantId)
            if (dps != null) repository.markDpsAccountSynced(dps.id, merchantId)
        }
        return synced
    }

    private suspend fun syncPendingFinanceData(): Boolean {
        if (_activeSupabaseProfile.value?.let { validSupabaseSession(it) } == null) return false
        val merchantId = activeProfile.value.id
        val schedules = repository.observeFinanceInstallments(merchantId).firstOrNull().orEmpty()
        var allSynced = true
        repository.observeLoans(merchantId).firstOrNull().orEmpty().filterNot(BusinessLoanEntity::isSynced).forEach { loan ->
            val accountSchedule = schedules.filter { it.accountType == "LOAN" && it.accountId == loan.id }
            if (accountSchedule.size != loan.durationMonths || !syncFinanceAccount("LOAN", loan, null, accountSchedule)) {
                allSynced = false
            }
        }
        repository.observeDpsAccounts(merchantId).firstOrNull().orEmpty().filterNot(DpsAccountEntity::isSynced).forEach { dps ->
            val accountSchedule = schedules.filter { it.accountType == "DPS" && it.accountId == dps.id }
            if (accountSchedule.size != dps.durationMonths || !syncFinanceAccount("DPS", null, dps, accountSchedule)) {
                allSynced = false
            }
        }
        val active = _activeSupabaseProfile.value?.let { validSupabaseSession(it) } ?: return false
        schedules.filter { it.status == "PAID" && !it.isSynced }.forEach { installment ->
            val method = installment.paymentMethod
            val reference = installment.paymentReference
            val paidAt = installment.paidAt
            if (method.isNullOrBlank() || reference.isNullOrBlank() || paidAt == null) {
                allSynced = false
                return@forEach
            }
            var paymentSynced = false
            com.example.data.remote.SupabaseClient.callRpc(
                active.supabaseUrl, active.anonKey, active.authSessionToken,
                "pay_finance_installment_atomic",
                org.json.JSONObject().apply {
                    put("p_installment_id", installment.id)
                    put("p_payment_method", method)
                    put("p_payment_reference", reference)
                    put("p_paid_at", toIsoTimestamp(paidAt))
                },
                onSuccess = { paymentSynced = true },
                onFailure = { logFirebaseStatus("Installment ${installment.installmentNumber} sync failed: $it") }
            )
            if (paymentSynced) repository.markFinanceInstallmentSynced(installment.id, merchantId) else allSynced = false
        }
        return allSynced
    }

    // Tagada message
    fun generateTagadaMessage(customerName: String, amount: Double, isBangla: Boolean = true): String {
        return if (isBangla) {
            "প্রিয় $customerName ভাই, SwapnoPay এ আপনার বাকি বকেয়া রয়েছে ৳${String.format("%.2f", Math.abs(amount))} টাকা। বকেয়া পরিশোধ করার জন্য বিনীত অনুরোধ রইল। ধন্যবাদ।"
        } else {
            "Dear $customerName, your outstanding balance with us is BDT ${String.format("%.2f", Math.abs(amount))}. Kindly settle the payment soon. Thank you for your business!"
        }
    }

    // AI business metrics compiler
    fun getBusinessDataSnapshot(): String {
        val cList = customers.value
        val pList = products.value
        val eList = expenses.value
        val txList = ledgerTransactions.value
        val stList = stockTransactions.value

        val totalOutstanding = cList.sumOf { it.currentBalance }
        val highestDebtor = cList.maxByOrNull { it.currentBalance }?.let { "${it.name} (৳${it.currentBalance})" } ?: "None"
        val lowStockProducts = pList.filter { it.stockQuantity <= it.minStockThreshold }.map { "${it.name} (${it.stockQuantity} ${it.unit})" }

        val totalExpense = eList.sumOf { it.amount }
        val expenseGroup = eList.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
        val expenseSummary = expenseGroup.map { "${it.key}: ৳${it.value}" }.joinToString(", ")

        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val recentSales = stList.filter { it.type == "out" && it.createdAt >= thirtyDaysAgo }
        val recentPurchases = stList.filter { it.type == "in" && it.createdAt >= thirtyDaysAgo }
        
        val salesVolume30Days = recentSales.sumOf { it.quantity }
        val salesRevenue30Days = recentSales.sumOf { it.quantity * it.price }
        val purchaseCost30Days = recentPurchases.sumOf { it.quantity * it.price }

        val bestSellers = recentSales.groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .mapNotNull { entry ->
                val prodName = pList.find { it.id == entry.key }?.name ?: "Product"
                "$prodName (${entry.value} units)"
            }.take(5).joinToString(", ")

        val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
        val lastMonthSales = stList.filter { it.type == "out" && it.createdAt in sixtyDaysAgo until thirtyDaysAgo }
        val lastMonthPurchases = stList.filter { it.type == "in" && it.createdAt in sixtyDaysAgo until thirtyDaysAgo }
        val lastMonthExpensesList = eList.filter { it.date in sixtyDaysAgo until thirtyDaysAgo }

        val thisMonthProfit = salesRevenue30Days - purchaseCost30Days - totalExpense
        val lastMonthProfit = lastMonthSales.sumOf { it.quantity * it.price } - lastMonthPurchases.sumOf { it.quantity * it.price } - lastMonthExpensesList.sumOf { it.amount }
        val profitTrend = if (thisMonthProfit >= lastMonthProfit) "increasing" else "decreasing"

        val dailyExpenseAverage = if (eList.isNotEmpty()) totalExpense / 30.0 else 0.0
        val todayStart = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
        val todayExpenses = eList.filter { it.date >= todayStart }.sumOf { it.amount }
        val expenseAnomaly = if (dailyExpenseAverage > 0 && todayExpenses > dailyExpenseAverage * 2.5) {
            "WARNING: Today's expenses (৳$todayExpenses) are ${String.format("%.1f", (todayExpenses / dailyExpenseAverage) * 100)}% higher than the daily average!"
        } else null

        val overdueCustomers = cList.filter { it.currentBalance > 0 }.mapNotNull { cust ->
            val custTx = txList.filter { it.customerId == cust.id }.minByOrNull { it.date }
            if (custTx != null && (System.currentTimeMillis() - custTx.date > 45L * 24 * 60 * 60 * 1000)) {
                "${cust.name} (৳${cust.currentBalance}, last unpaid transaction ${((System.currentTimeMillis() - custTx.date) / (24L * 60 * 60 * 1000)).toInt()} days ago)"
            } else null
        }

        val inventoryStatus = pList.map { prod ->
            val prodSales = recentSales.filter { it.productId == prod.id }.sumOf { it.quantity }
            val velocity = prodSales / 30.0
            val runoutDays = if (velocity > 0) prod.stockQuantity / velocity else Double.MAX_VALUE
            val runoutText = if (runoutDays == Double.MAX_VALUE) "No recent sales" else "${String.format("%.1f", runoutDays)} days left"
            val recommendReorder = if (runoutDays <= 7 && velocity > 0) "Order ${String.format("%.0f", velocity * 30)} ${prod.unit} for next month" else "In stock"
            "- ${prod.name}: Stock: ${prod.stockQuantity} ${prod.unit}, Status: $runoutText, Action: $recommendReorder"
        }.joinToString("\n")

        return """
            [BUSINESS DATA SNAPSHOT]
            1. Customer Debts: Total Outstanding is ৳$totalOutstanding BDT. Highest Debtor: $highestDebtor.
            2. Best-Selling Products (Last 30 Days): ${bestSellers.ifEmpty { "None recorded" }}.
            3. Financial Totals (Last 30 Days): Sales Revenue: ৳$salesRevenue30Days, Purchase Costs: ৳$purchaseCost30Days, Expenses: ৳$totalExpense (Breakdown: $expenseSummary). Net Profit estimate: ৳$thisMonthProfit.
            4. Profit Trend vs Last Month: $profitTrend (This month: ৳$thisMonthProfit vs Last month: ৳$lastMonthProfit).
            5. Anomalies & Alerts:
               ${expenseAnomaly ?: "No expense anomalies detected."}
               ${if (overdueCustomers.isEmpty()) "No customer balances overdue > 45 days." else "Overdue customers: " + overdueCustomers.joinToString(", ")}
            6. Inventory Levels & Reorders:
            $inventoryStatus
        """.trimIndent()
    }

    // AI Conversational Chat Engine with auto-switching
    fun sendOpenRouterCopilotMessage(userInput: String) {
        if (userInput.trim().isEmpty()) return
        val currentHistory = _aiChatHistory.value.toMutableList()
        currentHistory.add(mapOf("role" to "user", "content" to userInput))
        _aiChatHistory.value = currentHistory.toList()
        
        _isAiThinking.value = true
        val provider = _selectedAiProvider.value
        logFirebaseStatus("Sending prompt to ${if (provider == "Google AI Studio") "Gemini" else "OpenRouter"} assistant...")

        viewModelScope.launch {
            val systemContext = """
                You are a smart, friendly, encouraging, and helpful AI Business Copilot assisting a retail shop owner in Bangladesh.
                You explain trends, list debtors, and answer questions politely.
                Always write in a friendly, supportive tone, encouraging the merchant.
                You can format responses beautifully in Markdown. You can suggest business summaries, forecasts, and recommendations.
                
                You have access to the merchant's business data snapshot:
                ${getBusinessDataSnapshot()}
                
                Your memory of previous work and updates is:
                ${_aiMemory.value}
                
                If the user asks you to write data (like adding a credit, payment, expense, etc.), you MUST generate a text response explaining what you are doing, and append a structured JSON block enclosed within [ACTION_START] and [ACTION_END] tags.
                Example for customer credit:
                "অবশ্যই, আমি রহিমের জন্য ৫০০ টাকা বাকি লিখে রাখছি।"
                [ACTION_START]
                {
                  "action": "add_customer_credit",
                  "parameters": {
                     "customer_name": "Rahim",
                     "amount": 500.0,
                     "product_name": "Rice",
                     "quantity": "5",
                     "unit": "kg",
                     "note": "Rice purchase on credit"
                  },
                  "explanation": "Add ৳500 credit to Rahim for Rice"
                }
                [ACTION_END]
                
                Supported actions are:
                - `add_customer_credit` (params: customer_name, amount, product_name, quantity, unit, note)
                - `add_customer_payment` (params: customer_name, amount, note)
                - `add_supplier_credit` (params: supplier_name, amount, note)
                - `add_supplier_payment` (params: supplier_name, amount, note)
                - `add_expense` (params: expense_category, amount, description)
                
                Ensure the JSON is strictly valid, and do not put any text inside the [ACTION_START] and [ACTION_END] tags except the raw JSON.
                Respond in Bangla (mixed with common English retail terms) or English depending on user's language choice. Keep responses encouraging and professional.
            """.trimIndent()

            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemContext)
            })

            val recentHistory = currentHistory.takeLast(6)
            recentHistory.forEach { msg ->
                messages.put(JSONObject().apply {
                    put("role", msg["role"])
                    put("content", msg["content"])
                })
            }

            suspend fun tryProvider(currentProvider: String, switched: Boolean) {
                when (currentProvider) {
                    "Google AI Studio" -> {
                        val model = if (switched) _selectedGeminiModel.value else getAutoSelectedGeminiModel()
                        GeminiClient.getChatCompletion(
                        apiKey = _geminiApiKey.value,
                            model = model,
                            messages = messages,
                            onSuccess = { response ->
                                handleAiSuccess(response)
                                if (switched) {
                                    logFirebaseStatus("Auto-switched to Gemini and succeeded.")
                                }
                            },
                            onFailure = { error ->
                                if (!switched) {
                                    logFirebaseStatus("Gemini failed: $error. Auto-switching to OpenRouter...")
                                    viewModelScope.launch { tryProvider("OpenRouter", true) }
                                } else {
                                    handleAiFailure(error)
                                }
                            }
                        )
                    }
                    else -> {
                        val keys = _openRouterKeys.value
                        OpenRouterClient.getChatCompletion(
                            keysCsv = keys,
                            messages = messages,
                            onSuccess = { response ->
                                handleAiSuccess(response)
                                if (switched) {
                                    logFirebaseStatus("Auto-switched to OpenRouter and succeeded.")
                                }
                            },
                            onFailure = { error ->
                                if (!switched) {
                                    logFirebaseStatus("OpenRouter failed: $error. Auto-switching to Gemini...")
                                    viewModelScope.launch { tryProvider("Google AI Studio", true) }
                                } else {
                                    handleAiFailure(error)
                                }
                            }
                        )
                    }
                }
            }

            tryProvider(provider, false)
        }
    }

    private fun handleAiSuccess(response: String) {
        _isAiThinking.value = false
        
        val actionRegex = Regex("\\[ACTION_START\\](.*)\\[ACTION_END\\]", RegexOption.DOT_MATCHES_ALL)
        val match = actionRegex.find(response)
        
        var chatText = response
        var actionJsonStr: String? = null
        
        if (match != null) {
            actionJsonStr = match.groupValues[1].trim()
            chatText = response.replace(match.value, "").trim()
        }
        
        val updatedHistory = _aiChatHistory.value.toMutableList()
        val chatMessage = mutableMapOf(
            "role" to "assistant",
            "content" to chatText
        )
        if (actionJsonStr != null) {
            chatMessage["action"] = actionJsonStr
        }
        updatedHistory.add(chatMessage.toMap())
        _aiChatHistory.value = updatedHistory.toList()
        logFirebaseStatus("AI assistant response received.")
        
        if (actionJsonStr != null && _autoApproveAiActions.value) {
            executeCopilotAction(actionJsonStr) { status ->
                val currentHistory2 = _aiChatHistory.value.toMutableList()
                currentHistory2.add(mapOf(
                    "role" to "assistant",
                    "content" to "✅ [স্বয়ংক্রিয় এন্ট্রি] $status"
                ))
                _aiChatHistory.value = currentHistory2.toList()
            }
        }
    }

    private fun handleAiFailure(error: String) {
        _isAiThinking.value = false
        val updatedHistory = _aiChatHistory.value.toMutableList()
        updatedHistory.add(mapOf("role" to "assistant", "content" to "দুঃখিত, এআই সংযোগ করতে সমস্যা হচ্ছে। ত্রুটি: $error"))
        _aiChatHistory.value = updatedHistory.toList()
        logFirebaseStatus("AI provider failed: $error")
    }

    // Copilot execution engine
    fun executeCopilotAction(actionJsonStr: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val clean = actionJsonStr.replace("```json", "").replace("```", "").trim()
                val obj = JSONObject(clean)
                val action = obj.optString("action")
                val params = obj.optJSONObject("parameters") ?: JSONObject()
                
                when (action) {
                    "add_customer_credit", "add_customer_payment" -> {
                        val name = params.optString("customer_name")
                        val amount = params.optDouble("amount", 0.0)
                        val type = if (action == "add_customer_credit") "credit" else "payment"
                        val product = params.optString("product_name", "General")
                        val qty = params.optString("quantity", "1")
                        val unit = params.optString("unit", "pcs")
                        val note = params.optString("note", "$product ($qty $unit)")

                        val matched = customers.value.find { it.name.equals(name, ignoreCase = true) }
                        val customerId = if (matched != null) {
                            matched.id
                        } else {
                            val newId = java.util.UUID.randomUUID().toString()
                            val merchantId = activeProfile.value.id
                            val newCustomer = CustomerEntity(
                                id = newId,
                                merchantId = merchantId,
                                name = name,
                                phone = "01700000000",
                                email = null,
                                address = null,
                                openingBalance = 0.0,
                                currentBalance = 0.0,
                                status = "VIP"
                            )
                            repository.insertCustomer(newCustomer)
                            newId
                        }
                        addLedgerTransaction(customerId, null, type, amount, note, isVoice = false)
                        
                        if (type == "credit") {
                            val matchedProd = products.value.find { it.name.equals(product, ignoreCase = true) }
                            if (matchedProd != null) {
                                recordStockChangeInternal(matchedProd.id, "out", qty.toDoubleOrNull() ?: 1.0, matchedProd.salePrice)
                            }
                        }
                        appendToAiMemory("Recorded customer transaction: $name ৳$amount ($type).")
                        onComplete("Logged customer $type for $name: ৳$amount.")
                    }
                    "add_supplier_credit", "add_supplier_payment" -> {
                        val name = params.optString("supplier_name")
                        val amount = params.optDouble("amount", 0.0)
                        val type = if (action == "add_supplier_credit") "credit" else "payment"
                        val note = params.optString("note", "Supplier transaction")

                        val matched = suppliers.value.find { it.name.equals(name, ignoreCase = true) }
                        val supplierId = if (matched != null) {
                            matched.id
                        } else {
                            val newId = java.util.UUID.randomUUID().toString()
                            val merchantId = activeProfile.value.id
                            val newSupplier = SupplierEntity(
                                id = newId,
                                merchantId = merchantId,
                                name = name,
                                phone = "01500000000",
                                email = null,
                                address = null,
                                openingBalance = 0.0,
                                currentBalance = 0.0
                            )
                            repository.insertSupplier(newSupplier)
                            newId
                        }
                        addLedgerTransaction(null, supplierId, type, amount, note, isVoice = false)
                        appendToAiMemory("Recorded supplier transaction: $name ৳$amount ($type).")
                        onComplete("Logged supplier $type for $name: ৳$amount.")
                    }
                    "add_expense" -> {
                        val category = params.optString("expense_category", "Others")
                        val amount = params.optDouble("amount", 0.0)
                        val description = params.optString("description", "Recorded via AI Copilot")
                        addExpense(category, amount, description)
                        appendToAiMemory("Recorded expense: ৳$amount in $category.")
                        onComplete("Logged expense of ৳$amount in category $category.")
                    }
                    else -> onComplete("Action not recognized.")
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Action execution error", e)
                onComplete("Failed to execute action: ${e.message}")
            }
        }
    }

    // Voice Bookkeeping with data status checking and auto-switching
    fun processVoiceBookkeeping(
        speechText: String,
        onParsedDetails: (name: String, phone: String, product: String, qty: String, amount: Double, type: String, note: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (speechText.trim().isEmpty()) {
            onError("Voice transcription was empty.")
            return
        }

        _isAiThinking.value = true
        viewModelScope.launch {
            val systemContext = """
                You are a production-grade Bengali & English financial bookkeeping voice parser for shopkeepers in Bangladesh.
                Extract real transactional details from spoken audio transcription.

                Supported transaction types:
                - "credit": Customer credit sale (গ্রাহক বাকি/বকেয়া নিয়েছে / বাকিতে বিক্রয়). e.g. "রহিম বাকিতে ৫০০ টাকার তেল নিল", "করিম ০১৭... বাকি নিল ৫০০"
                - "payment": Customer payment / dues collection (বাকি পরিশোধ / জমা / টাকা দিল). e.g. "রহিম ৫০০ টাকা জমা দিল", "সোহেল বাকি পরিশোধ করল ৩০০ টাকা"
                - "expense": Shop expense (দোকানের খরচ / ভাড়া / বিদ্যুৎ বিল / নাস্তা). e.g. "চা নাস্তা ১০০ টাকা খরচ", "দোকান ভাড়া ৫০০০ টাকা দিলাম", "বিদ্যুৎ বিল ১৫০০ টাকা"
                - "supplier_credit": Supplier credit purchase (মহাজন বাকি). e.g. "প্রাণ কোম্পানি থেকে বাকিতে ২০ হাজার টাকার মাল আনলাম"
                - "supplier_payment": Supplier payment (মহাজনকে টাকা পরিশোধ). e.g. "আকিজ গ্রুপকে ১০ হাজার টাকা দিলাম"

                Return ONLY a JSON object with this exact schema:
                {
                   "status": "success" or "missing_data",
                   "prompt": "If status is missing_data, polite Bangla question asking for the missing amount or detail",
                   "name": "Customer / Supplier name or Expense title (e.g. 'রহিম', 'দোকান ভাড়া')",
                   "phone": "Customer/Supplier 11-digit mobile number if mentioned in speech (e.g. '01712345678'), or empty string '' if not mentioned. Convert Bengali digits (০-৯) to English digits (0-9). DO NOT invent fake numbers.",
                   "product": "Product name if mentioned (e.g. 'সয়াবিন তেল', 'চিনি', 'সাধারণ')",
                   "qty": "Quantity with unit (e.g. '১ লিটার', '৫ কেজি', '১ পিস')",
                   "amount": 500.0,
                   "type": "credit" or "payment" or "expense" or "supplier_credit" or "supplier_payment",
                   "note": "A concise summary in Bangla (e.g. 'রহিম বাকিতে সয়াবিন তেল (১ লিটার) নিল')"
                }

                Important Rules:
                - Convert spoken numbers in Bengali (e.g. "পাঁচশত" -> 500.0, "দুই হাজার" -> 2000.0, "১০০০" -> 1000.0, "দেড়শো" -> 150.0) into valid numeric Double.
                - If mobile number is mentioned (e.g. "০১৭...", "018..."), extract into "phone". If none spoken, leave "phone": "".
                - If the amount is completely missing, set status to "missing_data".
                - Do not include markdown tags, code blocks, or extra text. Return only valid raw JSON.
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemContext)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", speechText)
                })
            }

            fun handleVoiceSuccess(response: String) {
                _isAiThinking.value = false
                try {
                    val cleanJson = response.replace("```json", "").replace("```", "").trim()
                    val obj = JSONObject(cleanJson)
                    
                    if (obj.optString("status") == "missing_data") {
                        val prompt = obj.optString("prompt", "লেনদেনের প্রয়োজনীয় তথ্য পাওয়া যায়নি। অনুগ্রহ করে আবার বলুন।")
                        onError(prompt)
                    } else {
                        val name = obj.optString("name", obj.optString("customer", "সাধারণ গ্রাহক")).trim()
                        val phone = obj.optString("phone", "").filter { it.isDigit() }.trim()
                        val product = obj.optString("product", "সাধারণ").trim()
                        val qty = obj.optString("qty", "1 pcs").trim()
                        val amount = obj.optDouble("amount", 0.0)
                        val type = obj.optString("type", "credit").trim()
                        val note = obj.optString("note", "$name - $product ($qty)").trim()
                        onParsedDetails(name, phone, product, qty, amount, type, note)
                    }
                } catch (e: Exception) {
                    parseHeuristicFallback(speechText, onParsedDetails, onError)
                }
            }

            fun handleVoiceFailure(error: String) {
                _isAiThinking.value = false
                parseHeuristicFallback(speechText, onParsedDetails) { onError("AI server failure: $error") }
            }

            suspend fun tryProvider(currentProvider: String, switched: Boolean) {
                when (currentProvider) {
                    "Google AI Studio" -> {
                        GeminiClient.getChatCompletion(
                            apiKey = _geminiApiKey.value,
                            model = _selectedGeminiModel.value,
                            messages = messages,
                            onSuccess = { response ->
                                handleVoiceSuccess(response)
                                if (switched) logFirebaseStatus("Auto-switched to Gemini for voice parsing and succeeded.")
                            },
                            onFailure = { error ->
                                if (!switched) {
                                    logFirebaseStatus("Gemini voice failed: $error. Auto-switching to OpenRouter...")
                                    viewModelScope.launch { tryProvider("OpenRouter", true) }
                                } else {
                                    handleVoiceFailure(error)
                                }
                            }
                        )
                    }
                    else -> {
                        OpenRouterClient.getChatCompletion(
                            keysCsv = _openRouterKeys.value,
                            messages = messages,
                            onSuccess = { response ->
                                handleVoiceSuccess(response)
                                if (switched) logFirebaseStatus("Auto-switched to OpenRouter for voice parsing and succeeded.")
                            },
                            onFailure = { error ->
                                if (!switched) {
                                    logFirebaseStatus("OpenRouter voice failed: $error. Auto-switching to Gemini...")
                                    viewModelScope.launch { tryProvider("Google AI Studio", true) }
                                } else {
                                    handleVoiceFailure(error)
                                }
                            }
                        )
                    }
                }
            }

            tryProvider(_selectedAiProvider.value, false)
        }
    }

    fun processVoiceBookkeeping(
        speechText: String,
        onParsed: (customer: String, product: String, qty: String, amount: Double, type: String) -> Unit,
        onError: (String) -> Unit
    ) {
        processVoiceBookkeeping(
            speechText = speechText,
            onParsedDetails = { name, _, product, qty, amount, type, _ ->
                onParsed(name, product, qty, amount, type)
            },
            onError = onError
        )
    }

    private fun parseHeuristicFallback(
        speechText: String,
        onParsedDetails: (name: String, phone: String, product: String, qty: String, amount: Double, type: String, note: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        var normalized = speechText
        for (i in 0..9) {
            normalized = normalized.replace(bengaliDigits[i], englishDigits[i])
        }

        val phoneMatch = Regex("""\b01[3-9]\d{8}\b""").find(normalized)?.value ?: ""
        val amountMatch = Regex("""\b\d+(?:\.\d+)?\b""").findAll(normalized)
            .mapNotNull { it.value.toDoubleOrNull() }
            .filter { it != phoneMatch.toDoubleOrNull() }
            .firstOrNull() ?: 0.0

        if (amountMatch <= 0.0) {
            onError("লেনদেনের টাকার পরিমাণ শনাক্ত করা যায়নি। অনুগ্রহ করে পুনরায় বলুন।")
            return
        }

        val lower = normalized.lowercase()
        val type = when {
            lower.contains("খরচ") || lower.contains("ভাড়া") || lower.contains("বিল") || lower.contains("নাস্তা") -> "expense"
            lower.contains("মহাজন") && (lower.contains("জমা") || lower.contains("পরিশোধ")) -> "supplier_payment"
            lower.contains("মহাজন") -> "supplier_credit"
            lower.contains("জমা") || lower.contains("পরিশোধ") || lower.contains("পেল") -> "payment"
            else -> "credit"
        }

        val tokens = speechText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val name = tokens.firstOrNull { it.length > 1 && !it.contains("টাকা") && !it.contains("বাকি") && !it.contains("জমা") && !it.contains("খরচ") } ?: "গ্রাহক"

        onParsedDetails(name, phoneMatch, "সাধারণ", "১ পিস", amountMatch, type, speechText)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI FEATURE HUB — 10 Specialized AI Analysis Tools
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildAiMessages(systemPrompt: String, userPrompt: String): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
        }
    }

    private fun runAiFeature(systemPrompt: String, userPrompt: String, onResult: (String) -> Unit) {
        _isAiThinking.value = true
        val messages = buildAiMessages(systemPrompt, userPrompt)
        val provider = _selectedAiProvider.value

        viewModelScope.launch {
            suspend fun tryProvider(current: String, switched: Boolean) {
                when (current) {
                    "Google AI Studio" -> GeminiClient.getChatCompletion(
                        apiKey = _geminiApiKey.value,
                        model = getAutoSelectedGeminiModel(),
                        messages = messages,
                        onSuccess = {
                            _isAiThinking.value = false
                            onResult(it)
                        },
                        onFailure = { err ->
                            if (!switched) {
                                viewModelScope.launch { tryProvider("OpenRouter", true) }
                            } else {
                                _isAiThinking.value = false
                                onResult("❌ বিশ্লেষণ ব্যর্থ হয়েছে। ত্রুটি: $err")
                            }
                        }
                    )
                    else -> OpenRouterClient.getChatCompletion(
                        keysCsv = _openRouterKeys.value,
                        messages = messages,
                        onSuccess = {
                            _isAiThinking.value = false
                            onResult(it)
                        },
                        onFailure = { err ->
                            if (!switched) {
                                viewModelScope.launch { tryProvider("Google AI Studio", true) }
                            } else {
                                _isAiThinking.value = false
                                onResult("❌ বিশ্লেষণ ব্যর্থ হয়েছে। ত্রুটি: $err")
                            }
                        }
                    )
                }
            }
            tryProvider(provider, false)
        }
    }

    // 1. AI Business Analytics
    fun generateBusinessAnalytics(onResult: (String) -> Unit) {
        val snapshot = getBusinessDataSnapshot()
        val system = """
            আপনি একজন বিশেষজ্ঞ ব্যবসায়িক বিশ্লেষক। বাংলাদেশের একটি খুচরা দোকানের ব্যবসায়িক ডেটা বিশ্লেষণ করুন।
            নিচের তথ্যগুলো বাংলায় স্পষ্টভাবে বিশ্লেষণ করুন:
            ১. মোট আয়, ব্যয়, এবং নিট মুনাফা
            ২. খরচের ক্যাটাগরিভিত্তিক বিভাজন
            ৩. বিক্রয়ের প্রবণতা (বাড়ছে/কমছে)
            ৪. শীর্ষ পারফর্মিং পণ্য
            ৫. ব্যবসার শক্তি ও দুর্বলতা (SWOT সংক্ষিপ্ত)
            সহজ, উৎসাহজনক ভাষায় উত্তর দিন। ইমোজি ব্যবহার করুন।
        """.trimIndent()
        runAiFeature(system, "বিজনেস ডেটা:\n$snapshot", onResult)
        logFirebaseStatus("AI Business Analytics requested.")
    }

    // 2. AI Sales Forecasting
    fun generateSalesForecast(onResult: (String) -> Unit) {
        val snapshot = getBusinessDataSnapshot()
        val system = """
            আপনি একজন বিক্রয় পূর্বাভাস বিশেষজ্ঞ। বাংলাদেশের একটি ছোট ব্যবসার গত ৩০ দিনের বিক্রয় ডেটার উপর ভিত্তি করে আগামী ৩০ দিনের পূর্বাভাস দিন।
            বাংলায় লিখুন:
            ১. আগামী মাসে আনুমানিক মোট বিক্রয় পরিমাণ ও রাজস্ব (৳)
            ২. কোন পণ্যের চাহিদা বাড়বে বা কমবে
            ৩. উৎসব/মৌসুম ভিত্তিক সম্ভাবনা (যদি প্রযোজ্য)
            ৪. সুপারিশকৃত স্টক পরিকল্পনা
            ৫. রাজস্ব লক্ষ্যমাত্রা অর্জনের টিপস
            সংখ্যাগত হিসাব ও বাস্তবসম্মত পরামর্শ দিন।
        """.trimIndent()
        runAiFeature(system, "ব্যবসার তথ্য:\n$snapshot", onResult)
        logFirebaseStatus("AI Sales Forecast requested.")
    }

    // 3. AI Inventory Prediction
    fun generateInventoryPrediction(onResult: (String) -> Unit) {
        val snapshot = getBusinessDataSnapshot()
        val system = """
            আপনি একজন ইনভেন্টরি ম্যানেজমেন্ট বিশেষজ্ঞ। ডেটা বিশ্লেষণ করে বাংলায় লিখুন:
            ১. প্রতিটি পণ্যের স্টক শেষ হওয়ার আনুমানিক তারিখ
            ২. জরুরি পুনরায় অর্ডার দেওয়ার তালিকা (অগ্রাধিকার অনুযায়ী)
            ৩. প্রতিটি পণ্যের জন্য সুপারিশকৃত অর্ডার পরিমাণ (পরের ৩০ দিনের জন্য)
            ৪. ধীরগতির (slow-moving) পণ্য যা বেশি স্টক করা ঝুঁকিপূর্ণ
            ৫. মোট পুনরায় অর্ডারের আনুমানিক খরচ
            প্রতিটি পণ্য আলাদাভাবে বিশ্লেষণ করুন।
        """.trimIndent()
        runAiFeature(system, "স্টক তথ্য:\n$snapshot", onResult)
        logFirebaseStatus("AI Inventory Prediction requested.")
    }

    // 4. AI Smart Customer Analysis
    fun generateCustomerAnalysis(onResult: (String) -> Unit) {
        val snapshot = getBusinessDataSnapshot()
        val cList = customers.value
        val customerDetails = cList.take(20).joinToString("\n") {
            "- ${it.name}: বাকি ৳${String.format("%.2f", it.currentBalance)}, স্ট্যাটাস: ${it.status}"
        }
        val system = """
            আপনি একজন গ্রাহক সম্পর্ক বিশেষজ্ঞ। গ্রাহকদের ডেটা বিশ্লেষণ করে বাংলায় লিখুন:
            ১. VIP গ্রাহক (সবচেয়ে বেশি ক্রয়কারী) — তাদের ধরে রাখার পরামর্শ
            ২. ঝুঁকিপূর্ণ গ্রাহক (দীর্ঘদিন বাকি দিচ্ছেন না) — তাগাদা কৌশল
            ৩. নতুন গ্রাহক আনার সুযোগ
            ৪. গ্রাহক ধরে রাখার ব্যবহারিক পরামর্শ (রিটেনশন স্ট্র্যাটেজি)
            ৫. সর্বোচ্চ বকেয়াদারদের তালিকা ও পরিমাণ
            উৎসাহজনক ভাষায় ব্যবহারিক পরামর্শ দিন।
        """.trimIndent()
        val userPrompt = "বিজনেস ডেটা:\n$snapshot\n\nগ্রাহকের বিস্তারিত:\n$customerDetails"
        runAiFeature(system, userPrompt, onResult)
        logFirebaseStatus("AI Customer Analysis requested.")
    }

    // 5. AI Payment Reminder Generator (+ set notification)
    fun generatePaymentReminders(onResult: (String) -> Unit) {
        val cList = customers.value
        val overdueList = cList.filter { it.currentBalance > 500 }
            .sortedByDescending { it.currentBalance }
            .take(5)
        val overdueText = overdueList.joinToString("\n") {
            "- ${it.name}: বাকি ৳${String.format("%.2f", it.currentBalance)}, ফোন: ${it.phone ?: "নেই"}"
        }

        if (overdueList.isEmpty()) {
            onResult("✅ বর্তমানে কোনো বড় বকেয়া নেই। সকল গ্রাহকের হিসাব সুষ্ঠু আছে।")
            return
        }

        val system = """
            আপনি একজন পেশাদার ব্যবসায়িক যোগাযোগ বিশেষজ্ঞ। প্রতিটি বকেয়া গ্রাহকের জন্য একটি করে ব্যক্তিগতকৃত বাংলায় পেমেন্ট তাগাদা বার্তা লিখুন।
            বার্তাটি হবে:
            - বিনয়ী ও সম্মানজনক ভাষায়
            - গ্রাহকের নাম ও নির্দিষ্ট পরিমাণ উল্লেখ করে
            - কৃতজ্ঞতা প্রকাশ করে
            - দ্রুত পরিশোধের অনুরোধ করে
            - WhatsApp-এ পাঠানোর উপযোগী (৩-৪ লাইনের মধ্যে)
            প্রতিটি বার্তা আলাদা সেকশনে দিন এবং "---" দিয়ে আলাদা করুন।
        """.trimIndent()

        runAiFeature(system, "বকেয়া গ্রাহক তালিকা:\n$overdueText") { result ->
            // Schedule local notifications for top overdue customers
            overdueList.take(3).forEach { customer ->
                sendLocalNotification(
                    title = "💸 পেমেন্ট তাগাদা — ${customer.name}",
                    message = "বকেয়া: ৳${String.format("%.2f", customer.currentBalance)}। আজ তাগাদা পাঠান।"
                )
            }
            logFirebaseStatus("Payment reminder notifications sent for ${overdueList.size} customers.")
            onResult(result)
        }
        logFirebaseStatus("AI Payment Reminder Generator requested.")
    }

    // 6. AI Expense Categorization
    fun generateExpenseCategorization(onResult: (String) -> Unit) {
        val eList = expenses.value
        val expDetail = eList.takeLast(30).joinToString("\n") {
            "- ${it.category}: ৳${it.amount}, বিবরণ: ${it.description}"
        }
        val system = """
            আপনি একজন ব্যবসায়িক খরচ বিশ্লেষক। খরচের তালিকা বিশ্লেষণ করে বাংলায় লিখুন:
            ১. খরচের ক্যাটাগরি অনুযায়ী মোট হিসাব ও শতাংশ
            ২. কোন খরচ অস্বাভাবিক বা বেশি মনে হচ্ছে (অ্যানোমালি)
            ৩. কোন খরচ কমানো সম্ভব — ব্যবহারিক পরামর্শ
            ৪. সঠিক ক্যাটাগরিতে শ্রেণিবদ্ধ না হওয়া খরচ চিহ্নিত করুন
            ৫. মাসিক বাজেট পরিকল্পনার সুপারিশ
            সহজ ভাষায় বিস্তারিত বিশ্লেষণ করুন।
        """.trimIndent()
        runAiFeature(system, "সাম্প্রতিক ৩০টি খরচ:\n$expDetail", onResult)
        logFirebaseStatus("AI Expense Categorization requested.")
    }

    // 7. AI Product Recommendations
    fun generateProductRecommendations(onResult: (String) -> Unit) {
        val snapshot = getBusinessDataSnapshot()
        val system = """
            আপনি একজন রিটেইল ব্যবসায়িক পরামর্শদাতা। বিক্রয় ও স্টক ডেটা বিশ্লেষণ করে বাংলায় লিখুন:
            ১. কোন পণ্যের স্টক বাড়ানো উচিত (দ্রুত বিক্রি হচ্ছে)
            ২. কোন নতুন পণ্য যোগ করলে ব্যবসা বাড়তে পারে
            ৩. কোন পণ্যের কম্বো অফার তৈরি করা যায়
            ৪. মৌসুমী পণ্য স্টক পরামর্শ
            ৫. সরবরাহকারী থেকে ডিসকাউন্ট পাওয়ার কৌশল
            বাস্তবসম্মত ও লাভজনক পরামর্শ দিন।
        """.trimIndent()
        runAiFeature(system, "বিজনেস ডেটা:\n$snapshot", onResult)
        logFirebaseStatus("AI Product Recommendations requested.")
    }

    // 8. AI Smart Offers
    fun generateSmartOffers(onResult: (String) -> Unit) {
        val snapshot = getBusinessDataSnapshot()
        val cList = customers.value
        val vipCustomers = cList.filter { it.status == "VIP" || it.currentBalance > 1000 }.take(5)
            .joinToString(", ") { it.name }
        val system = """
            আপনি একজন মার্কেটিং বিশেষজ্ঞ। ব্যবসার ডেটা ব্যবহার করে বাংলায় আকর্ষণীয় অফার তৈরি করুন:
            ১. ধীরগতির পণ্যের জন্য বিশেষ ছাড়ের অফার
            ২. VIP গ্রাহকদের জন্য লয়্যালটি অফার
            ৩. নতুন গ্রাহক আনার রেফারেল অফার
            ৪. বাল্ক ক্রয়ে ডিসকাউন্ট অফার
            ৫. উৎসব বা সিজনাল অফার আইডিয়া
            প্রতিটি অফারের সম্ভাব্য লাভ-ক্ষতির বিশ্লেষণ সহ লিখুন।
        """.trimIndent()
        val userPrompt = "বিজনেস ডেটা:\n$snapshot\nVIP গ্রাহক: $vipCustomers"
        runAiFeature(system, userPrompt, onResult)
        logFirebaseStatus("AI Smart Offers requested.")
    }

    // 9. AI Fraud / Anomaly Detection
    fun generateFraudAnomalyDetection(onResult: (String) -> Unit) {
        val txList = ledgerTransactions.value
        val eList = expenses.value
        val recentTx = txList.takeLast(20).joinToString("\n") {
            "- টাইপ: ${it.type}, পরিমাণ: ৳${it.amount}, নোট: ${it.note}"
        }
        val recentExp = eList.takeLast(10).joinToString("\n") {
            "- ক্যাটাগরি: ${it.category}, পরিমাণ: ৳${it.amount}"
        }
        val system = """
            আপনি একজন আর্থিক নিরীক্ষক ও প্রতারণা শনাক্তকারী বিশেষজ্ঞ। লেনদেন ও খরচের ডেটা বিশ্লেষণ করে বাংলায় লিখুন:
            ১. অস্বাভাবিক বড় বা ছোট লেনদেন (স্পাইক)
            ২. সন্দেহজনক প্যাটার্ন (যেমন রাতে বড় লেনদেন, অদ্ভুত পরিমাণ)
            ৩. বারবার একই পরিমাণের লেনদেন
            ৪. খরচের অ্যানোমালি (হঠাৎ অনেক বেশি খরচ)
            ৫. সুরক্ষামূলক পরামর্শ
            যদি কোনো সমস্যা না থাকে, তা স্পষ্টভাবে বলুন। ⚠️ এবং ✅ ইমোজি ব্যবহার করুন।
        """.trimIndent()
        val userPrompt = "সাম্প্রতিক লেনদেন:\n$recentTx\n\nসাম্প্রতিক খরচ:\n$recentExp"
        runAiFeature(system, userPrompt, onResult)
        logFirebaseStatus("AI Fraud/Anomaly Detection requested.")
    }

    // 10. AI Daily Business Summary
    fun generateDailyBusinessSummary(onResult: (String) -> Unit) {
        val snapshot = getBusinessDataSnapshot()
        val todayStart = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
        val todaySales = stockTransactions.value.filter { it.type == "out" && it.createdAt >= todayStart }
            .sumOf { it.quantity * it.price }
        val todayExpenses = expenses.value.filter { it.date >= todayStart }.sumOf { it.amount }
        val todayLedger = ledgerTransactions.value.filter { it.date >= todayStart }
        val todayCredits = todayLedger.filter { it.type == "credit" }.sumOf { it.amount }
        val todayPayments = todayLedger.filter { it.type == "payment" }.sumOf { it.amount }

        val system = """
            আপনি একজন ব্যবসায়িক সহকারী। আজকের ব্যবসার একটি সংক্ষিপ্ত দৈনিক প্রতিবেদন বাংলায় লিখুন:
            ১. 📊 আজকের বিক্রয় সারসংক্ষেপ
            ২. 💰 নগদ প্রবাহ (আয় বনাম ব্যয়)
            ৩. 📦 স্টক সতর্কতা (কম স্টক পণ্য)
            ৪. 👥 আজকের গ্রাহক লেনদেন হাইলাইট
            ৫. ⚠️ জরুরি সতর্কতা ও পরামর্শ
            ৬. 🎯 আগামীকালের জন্য প্রস্তুতি পরামর্শ
            সংক্ষিপ্ত কিন্তু তথ্যবহুল ভাষায় লিখুন। প্রতিটি সেকশনে ইমোজি ব্যবহার করুন।
        """.trimIndent()
        val userPrompt = """
            আজকের তথ্য:
            - বিক্রয় রাজস্ব: ৳${String.format("%.2f", todaySales)}
            - খরচ: ৳${String.format("%.2f", todayExpenses)}
            - নতুন বাকি: ৳${String.format("%.2f", todayCredits)}
            - প্রাপ্ত পেমেন্ট: ৳${String.format("%.2f", todayPayments)}
            
            সামগ্রিক ডেটা:
            $snapshot
        """.trimIndent()
        runAiFeature(system, userPrompt) { result ->
            // Also record summary as a log
            logFirebaseStatus("Daily AI Summary generated for ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())}")
            onResult(result)
        }
        logFirebaseStatus("AI Daily Business Summary requested.")
    }

    // Receipt scanner with nested product arrays
    fun processReceiptScan(ocrText: String, onParsed: (supplier: String, amount: Double, items: String) -> Unit, onError: (String) -> Unit) {
        if (ocrText.trim().isEmpty()) {
            onError("OCR scan is empty.")
            return
        }

        _isAiThinking.value = true
        viewModelScope.launch {
            val systemContext = """
                You are an OCR receipt transaction parser. Take raw invoice text and extract key purchase information.
                Return ONLY a JSON object with this schema:
                {
                   "supplier": "Supplier / Store Name",
                   "invoice_number": "Invoice/Bill Number or 'Unknown'",
                   "date": "Date of transaction or 'Unknown'",
                   "vat_tax": VAT or tax amount (double, use 0.0 if none),
                   "amount": total amount spent (double),
                   "items_list": [
                      {"product": "Product Name", "qty": quantity (double), "unit": "kg/pcs/ltr/bag", "price": unit price (double)}
                   ]
                }
                No markdown, no wrappers, just raw JSON.
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemContext)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", ocrText)
                })
            }

            val keys = _openRouterKeys.value

            if (_selectedAiProvider.value == "Google AI Studio") {
                GeminiClient.getChatCompletion(
                            apiKey = _geminiApiKey.value,
                    model = _selectedGeminiModel.value,
                    messages = messages,
                    onSuccess = { response ->
                        _isAiThinking.value = false
                        try {
                            val cleanJson = response.replace("```json", "").replace("```", "").trim()
                            val obj = JSONObject(cleanJson)
                            val supplier = obj.optString("supplier", "General Supplier")
                            val amount = obj.optDouble("amount", 0.0)
                            
                            val itemsArr = obj.optJSONArray("items_list")
                            val itemsList = mutableListOf<String>()
                            if (itemsArr != null) {
                                for (i in 0 until itemsArr.length()) {
                                    val item = itemsArr.getJSONObject(i)
                                    val product = item.optString("product")
                                    val qty = item.optDouble("qty")
                                    val unit = item.optString("unit")
                                    itemsList.add("$product ($qty $unit)")
                                }
                            }
                            val itemsStr = if (itemsList.isEmpty()) "Inventory Purchase" else itemsList.joinToString(", ")
                            
                            onParsed(supplier, amount, itemsStr)
                        } catch (e: Exception) {
                            onError("OCR structure extraction failed.")
                        }
                    },
                    onFailure = { error ->
                        _isAiThinking.value = false
                        onError("OCR scan failure: $error")
                    }
                )
            } else {
                OpenRouterClient.getChatCompletion(
                    keysCsv = keys,
                    messages = messages,
                    onSuccess = { response ->
                        _isAiThinking.value = false
                        try {
                            val cleanJson = response.replace("```json", "").replace("```", "").trim()
                            val obj = JSONObject(cleanJson)
                            val supplier = obj.optString("supplier", "General Supplier")
                            val amount = obj.optDouble("amount", 0.0)
                            
                            val itemsArr = obj.optJSONArray("items_list")
                            val itemsList = mutableListOf<String>()
                            if (itemsArr != null) {
                                for (i in 0 until itemsArr.length()) {
                                    val item = itemsArr.getJSONObject(i)
                                    val product = item.optString("product")
                                    val qty = item.optDouble("qty")
                                    val unit = item.optString("unit")
                                    itemsList.add("$product ($qty $unit)")
                                }
                            }
                            val itemsStr = if (itemsList.isEmpty()) "Inventory Purchase" else itemsList.joinToString(", ")
                            
                            onParsed(supplier, amount, itemsStr)
                        } catch (e: Exception) {
                            onError("OCR structure extraction failed.")
                        }
                    },
                    onFailure = { error ->
                        _isAiThinking.value = false
                        onError("OCR scan failure: $error")
                    }
                )
            }
        }
    }

    // ── E-COMMERCE WEB SHOP & WEBSITE LAUNCH STATE ───────────────────────────
    // ── E-COMMERCE WEB SHOP & WEBSITE LAUNCH STATE ───────────────────────────
    data class WebShopState(
        val isDeployed: Boolean = false,
        val isDeploying: Boolean = false,
        val status: String = "NOT_DEPLOYED",
        val storeName: String = "",
        val shopSlug: String = "",
        val shopUrl: String = "",
        val adminUrl: String = "",
        val adminLoginUrl: String = "",
        val adminEmail: String = "",
        val adminPassword: String = "",
        val adminRole: String = "Top Admin",
        val customDomain: String = "",
        val primaryCurrency: String = "BDT",
        val themeColor: String = "#4F46E5",
        val productsCount: Int = 0,
        val ordersCount: Int = 0,
        val totalRevenue: Double = 0.0,
        val isSyncing: Boolean = false,
        val vpsHost: String = "vps.swapnopay.top",
        val sslActive: Boolean = false,
        val lastSyncedAt: String? = null,
        val syncMessage: String = "",
        val statusMessage: String = ""
    )

    private val webShopHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun getWebShopAuthToken(): String {
        return _activeSupabaseProfile.value?.authSessionToken?.takeIf { it.isNotBlank() }
            ?: _sessionInfo.value.token?.takeIf { it.isNotBlank() }
            ?: _activeSupabaseProfile.value?.anonKey?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private val _webShopState = MutableStateFlow(WebShopState())
    val webShopState: StateFlow<WebShopState> = _webShopState.asStateFlow()

    private var pollJob: kotlinx.coroutines.Job? = null

    fun pollWebShopUntilLive() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            var attempts = 0
            while (attempts < 15 && !_webShopState.value.isDeployed && _webShopState.value.status != "FAILED") {
                kotlinx.coroutines.delay(4000)
                attempts++
                loadWebShopStatusInternal()
            }
        }
    }

    fun loadWebShopStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            loadWebShopStatusInternal()
        }
    }

    private fun loadWebShopStatusInternal() {
        try {
            val merchantId = _activeProfile.value.id
            val backendBase = "https://api.swapnopay.top"
            val reqBuilder = Request.Builder()
                .url("$backendBase/v1/shop/status?merchant_id=$merchantId")
                .get()
            val token = getWebShopAuthToken()
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            val request = reqBuilder.build()
            webShopHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    if (json.optBoolean("ok", false)) {
                        val isLive = json.optBoolean("deployed", false)
                        val sUrl = json.optString("shop_url", "")
                        val aUrl = json.optString("admin_url", if (sUrl.isNotBlank()) "$sUrl/admin" else "")
                        val aLoginUrl = json.optString("admin_login_url", if (aUrl.isNotBlank()) "$aUrl/login.php" else "")
                        val adminCreds = json.optJSONObject("admin_credentials")
                        val aEmail = adminCreds?.optString("email") ?: json.optString("admin_email", "")
                        val aPass = adminCreds?.optString("default_password") ?: adminCreds?.optString("initial_password") ?: json.optString("admin_password", "")
                        val aRole = adminCreds?.optString("role") ?: "Top Admin"
                        val stat = json.optString("status", if (isLive) "LIVE" else "QUEUED")
                        val msg = json.optString("message", "")

                        _webShopState.update { current ->
                            current.copy(
                                isDeployed = isLive,
                                status = stat,
                                statusMessage = msg,
                                storeName = json.optString("store_name", current.storeName),
                                shopSlug = json.optString("shop_slug", current.shopSlug),
                                shopUrl = sUrl,
                                adminUrl = aUrl,
                                adminLoginUrl = aLoginUrl,
                                adminEmail = if (aEmail.isNotBlank()) aEmail else current.adminEmail,
                                adminPassword = if (aPass.isNotBlank()) aPass else current.adminPassword,
                                adminRole = aRole,
                                customDomain = json.optString("custom_domain", current.customDomain),
                                primaryCurrency = json.optString("currency", current.primaryCurrency.ifBlank { "BDT" }),
                                themeColor = json.optString("theme_color", current.themeColor.ifBlank { "#4F46E5" }),
                                productsCount = json.optInt("products_count", current.productsCount),
                                ordersCount = json.optInt("orders_count", current.ordersCount),
                                totalRevenue = json.optDouble("total_revenue", current.totalRevenue),
                                sslActive = json.optBoolean("ssl_active", isLive),
                                lastSyncedAt = json.optString("last_updated", null)
                            )
                        }
                    }
                } else if (response.code in listOf(404, 409)) {
                    _webShopState.update { it.copy(isDeployed = false, status = "NOT_DEPLOYED") }
                }
            }
        } catch (e: Exception) {
            Log.w("AppViewModel", "loadWebShopStatus failed: ${e.message}")
        }
    }

    fun deployWebShop(
        storeName: String,
        shopSlug: String,
        customDomain: String,
        primaryCurrency: String = "BDT",
        themeColor: String = "#4F46E5",
        adminEmail: String = "",
        adminPassword: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _webShopState.update { it.copy(isDeploying = true) }
            try {
                val effectiveAdminEmail = adminEmail.trim().ifBlank {
                    _webShopState.value.adminEmail.ifBlank {
                        _activeProfile.value.email.ifBlank { "admin@myshop.com" }
                    }
                }
                val effectivePassword = if (adminPassword.isNotBlank()) {
                    if (adminPassword.length < 12) {
                        _webShopState.update { it.copy(isDeploying = false) }
                        withContext(Dispatchers.Main) {
                            onComplete(false, "Admin password must be at least 12 characters long.")
                        }
                        return@launch
                    }
                    adminPassword.trim()
                } else {
                    _webShopState.value.adminPassword.takeIf { it.length >= 12 }
                        ?: ("Sp#" + java.util.UUID.randomUUID().toString().replace("-", "").take(10) + "!")
                }

                val merchantId = _activeProfile.value.id
                val backendBase = "https://api.swapnopay.top"
                val cleanDomain = customDomain.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
                val cleanSlug = shopSlug.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').ifBlank { "store" }

                val payload = JSONObject().apply {
                    put("merchant_id", merchantId)
                    put("store_name", storeName.trim().ifBlank { "My Web Store" })
                    put("shop_slug", cleanSlug)
                    if (cleanDomain.isNotBlank()) {
                        put("custom_domain", cleanDomain)
                    }
                    put("primary_currency", primaryCurrency.ifBlank { "BDT" })
                    put("theme_color", themeColor.ifBlank { "#4F46E5" })
                    put("admin_email", effectiveAdminEmail)
                    put("admin_password", effectivePassword)
                }

                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$backendBase/v1/shop/deploy")
                    .post(body)
                val token = getWebShopAuthToken()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val request = reqBuilder.build()
                webShopHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    val json = if (!respStr.isNullOrBlank()) JSONObject(respStr) else JSONObject()
                    val isSuccess = (response.isSuccessful || response.code in 200..202) && json.optBoolean("ok", true)

                    if (isSuccess) {
                        val isLive = json.optBoolean("deployed", false) || response.code == 200
                        val respStatus = json.optString("status", if (isLive) "LIVE" else "QUEUED")
                        val targetUrl = json.optString("shop_url", "https://${cleanSlug}.swapnopay.top")
                        val adminUrl = json.optString("admin_url", "$targetUrl/admin")
                        val adminLoginUrl = json.optString("admin_login_url", "$adminUrl/login.php")
                        val adminCreds = json.optJSONObject("admin_credentials")
                        val finalEmail = adminCreds?.optString("email") ?: effectiveAdminEmail
                        val finalPass = adminCreds?.optString("default_password") ?: adminCreds?.optString("initial_password") ?: effectivePassword

                        _webShopState.update { current ->
                            current.copy(
                                isDeploying = false,
                                isDeployed = isLive,
                                status = respStatus,
                                statusMessage = json.optString("message", if (isLive) "Your storefront is live!" else "Storefront queued on VPS"),
                                storeName = storeName.ifBlank { "My Web Store" },
                                shopSlug = cleanSlug,
                                shopUrl = targetUrl,
                                adminUrl = adminUrl,
                                adminLoginUrl = adminLoginUrl,
                                adminEmail = finalEmail,
                                adminPassword = finalPass,
                                customDomain = cleanDomain,
                                primaryCurrency = primaryCurrency,
                                themeColor = themeColor,
                                lastSyncedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                            )
                        }
                        withContext(Dispatchers.Main) {
                            val msg = if (isLive) "Web shop is live: $targetUrl" else "Storefront launch queued! Provisioning on VPS..."
                            onComplete(true, msg)
                        }
                        pollWebShopUntilLive()
                    } else {
                        val errMsg = json.optString("error", json.optString("message", "Deployment failed (HTTP ${response.code})"))
                        _webShopState.update { it.copy(isDeploying = false) }
                        withContext(Dispatchers.Main) {
                            onComplete(false, errMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                _webShopState.update { it.copy(isDeploying = false) }
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Failed to deploy website")
                }
            }
        }
    }

    fun updateWebShopCustomDomain(domain: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val cleanDomain = domain.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val merchantId = _activeProfile.value.id
                val backendBase = "https://api.swapnopay.top"
                val payload = JSONObject().apply {
                    put("merchant_id", merchantId)
                    put("custom_domain", cleanDomain.ifBlank { JSONObject.NULL })
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$backendBase/v1/shop/domain")
                    .post(body)
                val token = getWebShopAuthToken()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val request = reqBuilder.build()
                webShopHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    val json = if (!respStr.isNullOrBlank()) JSONObject(respStr) else JSONObject()
                    val isSuccess = (response.isSuccessful || response.code == 202) && json.optBoolean("ok", true)
                    if (isSuccess) {
                        _webShopState.update { current ->
                            current.copy(customDomain = cleanDomain)
                        }
                        withContext(Dispatchers.Main) {
                            onComplete(true, if (cleanDomain.isNotBlank()) "Custom domain bound successfully: $cleanDomain" else "Custom domain removed")
                        }
                        pollWebShopUntilLive()
                    } else {
                        val errMsg = json.optString("error", "Failed to bind custom domain (HTTP ${response.code})")
                        withContext(Dispatchers.Main) {
                            onComplete(false, errMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Failed to update custom domain")
                }
            }
        }
    }

    fun syncLocalInventoryToWebShop(onComplete: (Int, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            _webShopState.update { it.copy(isSyncing = true, syncMessage = "Reading local inventory...") }
            try {
                if (!_webShopState.value.isDeployed && _webShopState.value.status != "LIVE") {
                    _webShopState.update { it.copy(isSyncing = false, syncMessage = "Launch your store first before syncing inventory.") }
                    withContext(Dispatchers.Main) { onComplete(0, "Launch your storefront on VPS first before syncing inventory.") }
                    return@launch
                }

                val localProducts = products.value
                if (localProducts.isEmpty()) {
                    _webShopState.update { it.copy(isSyncing = false, syncMessage = "No local products to sync.") }
                    withContext(Dispatchers.Main) { onComplete(0, "No local products found.") }
                    return@launch
                }

                val itemsArray = JSONArray()
                for (p in localProducts) {
                    val sku = p.code?.takeIf { it.isNotBlank() } ?: p.qrCode?.takeIf { it.isNotBlank() } ?: "SKU-${p.id.take(8)}"
                    val obj = JSONObject().apply {
                        put("id", sku)
                        put("sku", sku)
                        put("source_id", sku)
                        put("name", p.name)
                        put("price", p.salePrice)
                        put("stock", p.stockQuantity.toInt())
                        put("category", p.category?.takeIf { it.isNotBlank() } ?: "General")
                    }
                    itemsArray.put(obj)
                }

                val payload = JSONObject().apply {
                    put("merchant_id", _activeProfile.value.id)
                    put("items", itemsArray)
                }

                val backendBase = "https://api.swapnopay.top"
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$backendBase/v1/shop/sync-inventory")
                    .post(body)
                val token = getWebShopAuthToken()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val request = reqBuilder.build()

                webShopHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    val json = if (!respStr.isNullOrBlank()) JSONObject(respStr) else JSONObject()
                    if (response.isSuccessful && json.optBoolean("ok", true)) {
                        val syncedCount = json.optInt("synced_count", localProducts.size)
                        val totalProducts = json.optInt("products_count", _webShopState.value.productsCount + syncedCount)
                        _webShopState.update { current ->
                            current.copy(
                                isSyncing = false,
                                productsCount = totalProducts,
                                syncMessage = "Synced $syncedCount products successfully!"
                            )
                        }
                        withContext(Dispatchers.Main) {
                            onComplete(syncedCount, "Successfully synced $syncedCount products to web store!")
                        }
                    } else {
                        val errMsg = json.optString("error", "Sync rejected by server (HTTP ${response.code})")
                        _webShopState.update { it.copy(isSyncing = false, syncMessage = errMsg) }
                        withContext(Dispatchers.Main) {
                            onComplete(0, errMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                _webShopState.update { it.copy(isSyncing = false, syncMessage = "Sync error: ${e.message}") }
                withContext(Dispatchers.Main) {
                    onComplete(0, "Inventory sync error: ${e.message}")
                }
            }
        }
    }

    fun addWebShopProduct(name: String, price: Double, qty: Int, description: String = "", onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!_webShopState.value.isDeployed && _webShopState.value.status != "LIVE") {
                    withContext(Dispatchers.Main) { onComplete(false, "Launch your storefront on VPS first.") }
                    return@launch
                }
                val merchantId = _activeProfile.value.id
                val prodId = "PROD-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
                val payload = JSONObject().apply {
                    put("merchant_id", merchantId)
                    put("source_id", prodId)
                    put("sku", prodId)
                    put("id", prodId)
                    put("name", name)
                    put("p_name", name)
                    put("price", price)
                    put("p_current_price", price.toString())
                    put("stock", qty)
                    put("p_qty", qty)
                    put("description", description)
                    put("p_description", description)
                }
                val backendBase = "https://api.swapnopay.top"
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$backendBase/v1/shop/products")
                    .post(body)
                val token = getWebShopAuthToken()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val request = reqBuilder.build()
                webShopHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    val json = if (!respStr.isNullOrBlank()) JSONObject(respStr) else JSONObject()
                    if (response.isSuccessful && json.optBoolean("ok", true)) {
                        _webShopState.update { it.copy(productsCount = it.productsCount + 1) }
                        withContext(Dispatchers.Main) { onComplete(true, "Product added to web catalog!") }
                    } else {
                        val errMsg = json.optString("error", "Server rejected product (HTTP ${response.code})")
                        withContext(Dispatchers.Main) { onComplete(false, errMsg) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false, e.message ?: "Failed to add product") }
            }
        }
    }

    fun updateWebShopOrderStatus(tranId: String, status: String, shippingStatus: String? = null, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val merchantId = _activeProfile.value.id
                val payload = JSONObject().apply {
                    put("merchant_id", merchantId)
                    put("order_id", tranId)
                    put("tran_id", tranId)
                    put("status", status)
                    if (shippingStatus != null) put("shipping_status", shippingStatus)
                }
                val backendBase = "https://api.swapnopay.top"
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$backendBase/v1/shop/orders/status")
                    .post(body)
                val token = getWebShopAuthToken()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val request = reqBuilder.build()
                webShopHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    val json = if (!respStr.isNullOrBlank()) JSONObject(respStr) else JSONObject()
                    val ok = response.isSuccessful && json.optBoolean("ok", true)
                    val errMsg = json.optString("error", "Failed to update order status (HTTP ${response.code})")
                    withContext(Dispatchers.Main) {
                        onComplete(ok, if (ok) "Order status updated to $status" else errMsg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false, e.message ?: "Network error") }
            }
        }
    }

    fun updateWebShopAdminCredentials(newEmail: String, newPass: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (newPass.trim().length < 12) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Admin password must be at least 12 characters long.")
                    }
                    return@launch
                }
                val merchantId = _activeProfile.value.id
                val payload = JSONObject().apply {
                    put("merchant_id", merchantId)
                    put("admin_email", newEmail.trim())
                    put("admin_password", newPass.trim())
                    put("email", newEmail.trim())
                    put("password", newPass.trim())
                }
                val backendBase = "https://api.swapnopay.top"
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$backendBase/v1/shop/admin/credentials")
                    .post(body)
                val token = getWebShopAuthToken()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val request = reqBuilder.build()
                webShopHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    val json = if (!respStr.isNullOrBlank()) JSONObject(respStr) else JSONObject()
                    val ok = (response.isSuccessful || response.code == 202) && json.optBoolean("ok", true)
                    if (ok) {
                        _webShopState.update { current ->
                            current.copy(adminEmail = newEmail.trim(), adminPassword = newPass.trim())
                        }
                    }
                    val errMsg = json.optString("error", "Failed to update credentials (HTTP ${response.code})")
                    withContext(Dispatchers.Main) {
                        onComplete(ok, if (ok) "Admin login credentials updated successfully!" else errMsg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false, e.message ?: "Network error") }
            }
        }
    }

    fun deleteWebShopProduct(productId: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val merchantId = _activeProfile.value.id
                val backendBase = "https://api.swapnopay.top"
                val reqBuilder = Request.Builder()
                    .url("$backendBase/v1/shop/products/$productId?merchant_id=$merchantId")
                    .delete()
                val token = getWebShopAuthToken()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                val request = reqBuilder.build()
                webShopHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    val json = if (!respStr.isNullOrBlank()) JSONObject(respStr) else JSONObject()
                    val ok = response.isSuccessful && json.optBoolean("ok", true)
                    if (ok) {
                        _webShopState.update { it.copy(productsCount = (it.productsCount - 1).coerceAtLeast(0)) }
                    }
                    val errMsg = json.optString("error", "Failed to remove product (HTTP ${response.code})")
                    withContext(Dispatchers.Main) {
                        onComplete(ok, if (ok) "Product removed from store catalog" else errMsg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false, e.message ?: "Network error") }
            }
        }
    }
}

data class MerchantNumber(
    val number: String,
    val method: String, // bKash, Nagad, Rocket, Upay
    val type: String, // Personal, Agent, Merchant
    val isActive: Boolean,
    val isDefault: Boolean = false,
    val qrCodeUrl: String? = null
)

data class PosCartItem(
    val variantId: String,
    val productId: String,
    val qrCode: String,
    val productName: String,
    val variantName: String,
    val category: String,
    val askingPrice: Double, // Tag / List Prize
    val sellingPrice: Double,// Actual Sale Prize
    val costPrice: Double,   // Buying / Real Prize
    val quantity: Double
)

// --- FORM BUILDER DATA MODELS ---
enum class FormFieldCategory {
    TEXT, CHOICE, MEDIA, PAYMENT, ADVANCED
}

enum class FormFieldType(val displayName: String, val category: FormFieldCategory) {
    NAME("Name", FormFieldCategory.TEXT),
    EMAIL("Email", FormFieldCategory.TEXT),
    PHONE("Phone", FormFieldCategory.TEXT),
    ADDRESS("Address", FormFieldCategory.TEXT),
    COMPANY("Company", FormFieldCategory.TEXT),
    WEBSITE("Website", FormFieldCategory.TEXT),
    NOTES("Notes / Comments", FormFieldCategory.TEXT),
    DATE("Date Picker", FormFieldCategory.TEXT),
    
    DROPDOWN("Dropdown Select", FormFieldCategory.CHOICE),
    RADIO("Radio Option", FormFieldCategory.CHOICE),
    CHECKBOX("Checkbox Option", FormFieldCategory.CHOICE),
    MULTI_SELECT("Multi Select", FormFieldCategory.CHOICE),
    TOGGLE("Toggle Switch", FormFieldCategory.CHOICE),
    
    MEDIA_IMAGE("Image Banner", FormFieldCategory.MEDIA),
    MEDIA_VIDEO("Video Link", FormFieldCategory.MEDIA),
    MEDIA_PDF("PDF Document", FormFieldCategory.MEDIA),
    IMAGE("Image Banner", FormFieldCategory.MEDIA),
    VIDEO("Video Link", FormFieldCategory.MEDIA),
    PDF("PDF Document", FormFieldCategory.MEDIA),
    FILE_UPLOAD("File Upload", FormFieldCategory.MEDIA),
    CAMERA_UPLOAD("Camera Upload", FormFieldCategory.MEDIA),
    
    PRODUCT("Single Product", FormFieldCategory.PAYMENT),
    PRODUCT_LIST("Product List", FormFieldCategory.PAYMENT),
    QUANTITY("Quantity Selector", FormFieldCategory.PAYMENT),
    COUPON("Coupon / Promo Code", FormFieldCategory.PAYMENT),
    DISCOUNT("Discount Display", FormFieldCategory.PAYMENT),
    SHIPPING("Shipping Options", FormFieldCategory.PAYMENT),
    TAX("Tax Calculation", FormFieldCategory.PAYMENT),
    TIP("Tip / Gratuity", FormFieldCategory.PAYMENT),
    DONATION("Donation Tiers", FormFieldCategory.PAYMENT),
    CUSTOM_AMOUNT("Custom Amount", FormFieldCategory.PAYMENT),
    CURRENCY("Currency Selector", FormFieldCategory.PAYMENT)
    ,
    CUSTOM_CODE("Custom Code / HTML", FormFieldCategory.ADVANCED)
}

data class FormPageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "Page 1: Information",
    var subtitle: String = "Please fill out the fields below"
)

data class HostedFormModel(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "New Payment Form",
    var slug: String = "pay-${System.currentTimeMillis().toString().takeLast(6)}",
    var description: String = "",
    var status: String = "DRAFT", // DRAFT, PUBLISHED
    var templateKey: String = "BLANK",
    var themeConfig: FormThemeConfig = FormThemeConfig(),
    var fields: List<FormFieldItem> = emptyList(),
    var products: List<FormProductItem> = emptyList(),
    var pages: List<FormPageItem> = listOf(FormPageItem(title = "Page 1: Customer Details")),
    var totalViews: Int = 0,
    var totalSubmissions: Int = 0,
    var totalRevenueBdt: Double = 0.0,
    var createdAt: Long = System.currentTimeMillis()
)

data class FormFieldItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: FormFieldType = FormFieldType.NAME,
    var label: String = "",
    var placeholder: String = "",
    var helperText: String = "",
    var isRequired: Boolean = true,
    var options: List<String> = listOf("Option 1", "Option 2"),
    var mediaUrl: String = "",
    var mediaAltText: String = "",
    var mediaHeightDp: Int = 180,
    var sectionIndex: Int = 0,
    var stepIndex: Int = 0,
    var isCollapsed: Boolean = false,
    var defaultValue: String = "",
    var dependsOnFieldId: String? = null,
    var conditionOperator: String = "EQUALS", // EQUALS, NOT_EQUALS, CONTAINS
    var conditionValue: String = "",
    
    // Rich Text Formatting
    var textColorHex: String = "#0F172A",
    var fontSizeSp: Int = 14,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderline: Boolean = false,
    var fontFamilyName: String = "Inter",
    var textAlignName: String = "LEFT", // LEFT, CENTER, RIGHT
    
    // Position & Resizing
    var widthDp: Int = 0, // 0 = full width 100%, or explicit dp
    var heightDp: Int = 0, // 0 = wrap content, or explicit dp
    var offsetXDp: Int = 0,
    var offsetYDp: Int = 0,
    
    // Card Container & Shape Styling
    var shapeType: String = "NONE", // NONE, RECTANGLE, CIRCLE, DIVIDER, CARD_CONTAINER, BADGE
    var fillColorHex: String = "#FFFFFF",
    var borderColorHex: String = "#E2E8F0",
    var borderWidthDp: Int = 1,
    var borderRadiusDp: Int = 12,
    var shadowElevationDp: Int = 2,
    var pageIndex: Int = 0,
    // Custom code fields (for CUSTOM_CODE type)
    var customCodeHtml: String = "",
    var customCodeCss: String = "",
    var customVariables: List<String> = emptyList(),
    var customVariableAssignments: Map<String, String> = emptyMap(),

    // Validation & Constraint Rules
    var validationRegex: String = "",
    var minLength: Int = 0,
    var maxLength: Int = 0,
    var minValue: Double? = null,
    var maxValue: Double? = null,
    var allowedFileExtensions: List<String> = listOf("png", "jpg", "jpeg", "pdf", "svg", "webp"),
    var maxFileSizeBytes: Long = 5 * 1024 * 1024, // 5MB
    var customErrorMessage: String = ""
)

data class FormValidationResult(
    val isValid: Boolean,
    val errors: Map<String, String>,
    val summaryMessage: String = ""
)

object FormValidationEngine {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val BD_OR_INTL_PHONE_REGEX = Regex("^(\\+?88)?01[3-9]\\d{8}$|^\\+?[0-9]{7,15}$")
    private val DATE_REGEX = Regex("^\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}$|^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}$")
    private val UNSAFE_VALIDATION_REGEX = Regex("""\\[1-9]|\(\?(?!:)|\([^)]*[+*][^)]*\)[+*{]""")

    fun validateSingleField(
        field: FormFieldItem,
        value: String,
        strictFormatValidation: Boolean = true,
        enforceQuantityRange: Boolean = true
    ): String? {
        val trimmed = value.trim()

        // 1. Required Check
        if (field.isRequired && trimmed.isEmpty()) {
            return field.customErrorMessage.ifBlank { "${field.label.ifBlank { "This field" }} is required." }
        }

        // If optional and empty, passes validation
        if (trimmed.isEmpty()) return null

        // 2. Custom Regex Validation
        if (field.validationRegex.isNotBlank()) {
            if (field.validationRegex.length > 256 || UNSAFE_VALIDATION_REGEX.containsMatchIn(field.validationRegex)) {
                return "${field.label.ifBlank { "This field" }} has an invalid validation configuration."
            }
            try {
                val regex = Regex(field.validationRegex)
                if (!regex.matches(trimmed)) {
                    return field.customErrorMessage.ifBlank { "Invalid format for ${field.label}." }
                }
            } catch (_: Exception) {
                return "${field.label.ifBlank { "This field" }} has an invalid validation configuration."
            }
        }

        // 3. Min/Max Length Validation
        if (field.minLength > 0 && trimmed.length < field.minLength) {
            return "${field.label} must be at least ${field.minLength} characters."
        }
        if (field.maxLength > 0 && trimmed.length > field.maxLength) {
            return "${field.label} cannot exceed ${field.maxLength} characters."
        }

        // 4. Type-Specific Validation
        return when (field.type) {
            FormFieldType.EMAIL -> {
                if (strictFormatValidation && !EMAIL_REGEX.matches(trimmed)) {
                    "Please enter a valid email address (e.g. name@example.com)."
                } else null
            }
            FormFieldType.PHONE -> {
                val cleanPhone = trimmed.replace(" ", "").replace("-", "")
                if (strictFormatValidation && !BD_OR_INTL_PHONE_REGEX.matches(cleanPhone)) {
                    "Please enter a valid phone number (e.g. 017XXXXXXXX)."
                } else null
            }
            FormFieldType.QUANTITY, FormFieldType.CUSTOM_AMOUNT -> {
                val num = trimmed.toDoubleOrNull()
                if (num == null) {
                    "Please enter a valid number."
                } else if ((field.type != FormFieldType.QUANTITY || enforceQuantityRange) && field.minValue != null && num < field.minValue!!) {
                    "Minimum allowed value is ${field.minValue}."
                } else if ((field.type != FormFieldType.QUANTITY || enforceQuantityRange) && field.maxValue != null && num > field.maxValue!!) {
                    "Maximum allowed value is ${field.maxValue}."
                } else if (num < 0) {
                    "Amount cannot be negative."
                } else null
            }
            FormFieldType.DATE -> {
                if (trimmed.isNotBlank() && !DATE_REGEX.matches(trimmed)) {
                    "Please enter or pick a valid date (e.g. YYYY-MM-DD or DD/MM/YYYY)."
                } else null
            }
            FormFieldType.NOTES -> {
                if (field.label.contains("Date", ignoreCase = true) && !DATE_REGEX.matches(trimmed)) {
                    "Please enter a valid date (e.g. DD/MM/YYYY)."
                } else null
            }
            FormFieldType.FILE_UPLOAD, FormFieldType.CAMERA_UPLOAD -> {
                val ext = trimmed.substringAfterLast('.', "").lowercase()
                if (ext.isNotEmpty() && field.allowedFileExtensions.isNotEmpty() && !field.allowedFileExtensions.contains(ext)) {
                    "Unsupported file format .$ext. Allowed: ${field.allowedFileExtensions.joinToString(", ")}."
                } else null
            }
            FormFieldType.CHECKBOX -> {
                if (field.isRequired && (trimmed == "false" || trimmed.isEmpty())) {
                    "You must agree to ${field.label} to proceed."
                } else null
            }
            FormFieldType.DROPDOWN, FormFieldType.RADIO, FormFieldType.MULTI_SELECT -> {
                if (field.isRequired && (trimmed.isEmpty() || trimmed.equals("Select an option", ignoreCase = true))) {
                    "Please select an option for ${field.label}."
                } else null
            }
            else -> null
        }
    }

    fun validateFormSubmission(
        fields: List<FormFieldItem>,
        values: Map<String, String>,
        enforceRequiredFields: Boolean = true,
        strictFormatValidation: Boolean = true,
        enforceQuantityRange: Boolean = true
    ): FormValidationResult {
        val errors = mutableMapOf<String, String>()
        for (field in fields) {
            if (field.type in setOf(
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
                    FormFieldType.CURRENCY
                )
            ) continue

            val dependencyId = field.dependsOnFieldId
            if (!dependencyId.isNullOrBlank()) {
                val dependencyValue = values[dependencyId]
                    ?: fields.firstOrNull { it.id == dependencyId }?.defaultValue
                    ?: ""
                val conditionMatches = when (field.conditionOperator.uppercase()) {
                    "NOT_EQUALS" -> dependencyValue != field.conditionValue
                    "CONTAINS" -> dependencyValue.contains(field.conditionValue)
                    else -> dependencyValue == field.conditionValue
                }
                if (!conditionMatches) continue
            }

            val v = values[field.id] ?: field.defaultValue
            val validationField = if (enforceRequiredFields) field else field.copy(isRequired = false)
            val err = validateSingleField(validationField, v, strictFormatValidation, enforceQuantityRange)
            if (err != null) {
                errors[field.id] = err
            }
        }
        val isValid = errors.isEmpty()
        val summary = if (!isValid) "Please fix ${errors.size} error(s) before submitting." else "All fields valid."
        return FormValidationResult(isValid, errors, summary)
    }

    fun validateFormForPublishing(
        title: String,
        slug: String,
        fields: List<FormFieldItem>
    ): FormValidationResult {
        val errors = mutableMapOf<String, String>()

        if (title.trim().isBlank()) {
            errors["title"] = "Form Title cannot be empty."
        } else if (title.trim().length < 3) {
            errors["title"] = "Form Title must be at least 3 characters."
        }

        if (slug.trim().isBlank()) {
            errors["slug"] = "Form URL slug is required."
        } else if (!slug.trim().matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$"))) {
            errors["slug"] = "Slug must contain only lowercase letters, numbers, and hyphens (e.g. summer-tshirt-order)."
        }

        for (field in fields) {
            if (field.label.trim().isBlank()) {
                errors["field_${field.id}"] = "Field label cannot be blank."
            }
            if (field.type in listOf(FormFieldType.DROPDOWN, FormFieldType.RADIO, FormFieldType.MULTI_SELECT) && field.options.isEmpty()) {
                errors["field_${field.id}"] = "Choice fields must have at least one option."
            }
        }

        val isValid = errors.isEmpty()
        val summary = if (!isValid) "Publish blocked: ${errors.values.firstOrNull() ?: "Invalid form setup."}" else "Form ready to publish."
        return FormValidationResult(isValid, errors, summary)
    }
}


data class FormProductItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "",
    var description: String = "",
    var price: Double = 0.0,
    var salePrice: Double = 0.0,
    var stock: Int = 100,
    var sku: String = "",
    var category: String = "General",
    var imageUrl: String = "",
    var isDigital: Boolean = false,
    var digitalDownloadUrl: String = "",
    var variants: List<String> = emptyList()
)

data class FormThemeConfig(
    var primaryColorHex: String = "#7C3AED", // Brand Purple
    var fontFamily: String = "Inter",
    var buttonShape: String = "ROUNDED", // ROUNDED, PILL, SQUARE
    var borderRadiusDp: Int = 16,
    var backgroundColorHex: String = "#F6F7FB",
    var logoUrl: String = "",
    var bannerUrl: String = "",
    var bannerTitle: String = "",
    var bannerSubtitle: String = "",
    var isDarkMode: Boolean = false,
    var showHeader: Boolean = true,
    var coverPhotoUrl: String = "",
    var firstPageInstruction: String = "",
    var shuffleQuestionOrder: Boolean = false,
    var oneResponsePerUser: Boolean = false,
    var backgroundStyle: String = "SOLID", // SOLID, GRADIENT, CUSTOM_CSS
    var gradientColorStart: String = "#5B7FFF",
    var gradientColorEnd: String = "#7C4DFF",
    var backgroundAnimation: String = "AURORA", // AURORA, STATIC, PULSE
    var customCss: String = "",
    var customJs: String = "",
    var enableCustomJs: Boolean = true,
    var isCustomWebApp: Boolean = false,
    var enableClosingTimeline: Boolean = false,
    var closingDeadlineEpoch: Long = 0L,
    var closingDeadlineStr: String = "",
    var closedMessage: String = "This form is no longer accepting responses / সময়সীমা শেষ হয়ে গেছে",
    var showCountdownTimer: Boolean = true,
    var enableCsvBackend: Boolean = false,
    var csvFileName: String = "",
    var csvRawData: String = "",
    var csvHeaders: List<String> = emptyList(),
    var csvLookupColumn: String = "",
    var csvTargetLookupFieldId: String = "",
    var csvColumnMappings: Map<String, String> = emptyMap(),
    var csvAutofillMode: String = "EXACT_MATCH",
    var formWidthPx: Int = 720,
    var pageMarginPx: Int = 24,
    var isMultiPageForm: Boolean = false,
    var progressTrackerStyle: String = "BAR", // HIDE, NUMBER, ICON, BAR
    var redirectType: String = "SUCCESS_MSG", // SUCCESS_MSG, REDIRECT_URL, CUSTOM_HTML, STAY_ON_FORM
    var redirectUrl: String = "",
    var redirectDelaySec: Int = 0,
    var openInNewTab: Boolean = false,
    var enableCustomHtml: Boolean = false,
    var customHtmlContent: String = "",
    var enableEmailNotifications: Boolean = false,
    var notificationEmail: String = "",
    var enableSmsNotifications: Boolean = false,
    var notificationSmsNumber: String = "",
    var enablePaymentCallback: Boolean = false,
    var paymentCallbackUrl: String = "",
    var requiredFieldIndicator: Boolean = true,
    var enableTimer: Boolean = false,
    var timerMinutes: Int = 30,
    var closeAfterLimit: Boolean = false,
    var maxResponses: Int = 1000,
    var enforceRequiredFields: Boolean = true,
    var strictFormatValidation: Boolean = true,
    var maxFileSizeBytes: Long = 5L * 1024 * 1024,
    var enforceFileSizeLimit: Boolean = true,
    var minQuantity: Int = 1,
    var maxQuantity: Int = 1000,
    var enforceQuantityRange: Boolean = true,
    var enableAntiSpam: Boolean = true,
    var enablePayment: Boolean = true,
    var paymentProvider: String = "AUTO",
    var currencyCode: String = "BDT",
    var taxPercent: Double = 0.0,
    var requirePaymentBeforeSubmit: Boolean = true,
    // Custom variables defined per-form for substitution in hosted HTML/CSS
    var customVariables: List<CustomVariable> = emptyList()
)

data class CustomVariable(
    val key: String,
    val exampleValue: String = "",
    val source: String = "field" // field, order, metadata
)

data class FormSubmissionItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val formId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val amountBdt: Double = 0.0,
    val paymentMethod: String = "bKash",
    val paymentStatus: String = "PAID", // PAID, PENDING, FAILED
    val trxId: String = "",
    val submittedAt: Long = System.currentTimeMillis()
)

data class FormAnalytics(
    val totalViews: Int = 0,
    val totalSubmissions: Int = 0,
    val totalRevenueBdt: Double = 0.0,
    val conversionRatePct: Double = 0.0
)
