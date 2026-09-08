package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.local.*
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object BackupManager {

    data class BackupSnapshotInfo(
        val fileName: String,
        val filePath: String,
        val timestamp: Long,
        val formattedDate: String,
        val formattedSize: String,
        val sizeBytes: Long,
        val type: String, // "Local Snapshot", "Cloud Sync", "Auto Scheduled"
        val totalRecords: Int
    )

    data class StorageStats(
        val databaseSizeBytes: Long,
        val databaseSizeFormatted: String,
        val cacheSizeBytes: Long,
        val cacheSizeFormatted: String,
        val mediaSizeBytes: Long,
        val mediaSizeFormatted: String,
        val totalAppBytes: Long,
        val totalAppFormatted: String,
        val freeDiskBytes: Long,
        val freeDiskFormatted: String,
        val usedPercentage: Float // 0f to 100f
    )

    data class RestoreSummary(
        val success: Boolean,
        val message: String,
        val restoredTables: Map<String, Int> = emptyMap()
    )

    private fun getBackupsDir(context: Context): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    // ── 1. CREATE LOCAL DATABASE SNAPSHOT (JSON) ─────────────────────────────
    suspend fun createLocalSnapshot(
        context: Context,
        repository: AppRepository,
        merchantId: String,
        backupType: String = "Local Snapshot"
    ): Result<BackupSnapshotInfo> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileName = "backup_${dateFormat.format(Date(now))}.json"
            val backupsDir = getBackupsDir(context)
            val targetFile = File(backupsDir, fileName)

            // Gather all Room records
            val customers = repository.observeCustomers(merchantId).firstOrNull().orEmpty()
            val suppliers = repository.observeSuppliers(merchantId).firstOrNull().orEmpty()
            val products = repository.observeProducts(merchantId).firstOrNull().orEmpty()
            val productVariants = repository.observeProductVariants(merchantId).firstOrNull().orEmpty()
            val stockTransactions = repository.observeStockTransactions(merchantId).firstOrNull().orEmpty()
            val ledgerTransactions = repository.observeLedgerTransactions(merchantId).firstOrNull().orEmpty()
            val posSales = repository.observePosSales(merchantId).firstOrNull().orEmpty()
            val expenses = repository.observeExpenses(merchantId).firstOrNull().orEmpty()
            val loans = repository.observeLoans(merchantId).firstOrNull().orEmpty()
            val dpsAccounts = repository.observeDpsAccounts(merchantId).firstOrNull().orEmpty()
            val financeInstallments = repository.observeFinanceInstallments(merchantId).firstOrNull().orEmpty()
            val notifications = repository.observeMerchantNotifications(merchantId).firstOrNull().orEmpty()
            val employees = repository.observeEmployees(merchantId).firstOrNull().orEmpty()
            val businessAnalytics = repository.observeBusinessAnalytics(merchantId).firstOrNull()
            val merchantNumbers = repository.observeMerchantNumbers(merchantId).firstOrNull().orEmpty()
            val paymentForms = repository.observePaymentFormCache(merchantId).firstOrNull().orEmpty()
            val formSubmissions = repository.observeFormSubmissionCache(merchantId).firstOrNull().orEmpty()
            val orders = repository.observeOrders(merchantId).firstOrNull().orEmpty()
            val payments = repository.observePayments(merchantId).firstOrNull().orEmpty()
            val appeals = repository.observeAppeals(merchantId).firstOrNull().orEmpty()
            val profile: com.example.data.local.MerchantProfileEntity? = repository.observeMerchantProfile().firstOrNull()

            val totalRecords = customers.size + suppliers.size + products.size +
                    productVariants.size + stockTransactions.size + ledgerTransactions.size +
                    posSales.size + expenses.size + loans.size + dpsAccounts.size +
                    financeInstallments.size + notifications.size + employees.size +
                    merchantNumbers.size + paymentForms.size + formSubmissions.size +
                    orders.size + payments.size + appeals.size +
                    (if (businessAnalytics == null) 0 else 1) + (if (profile == null) 0 else 1)

            val rootJson = JSONObject().apply {
                put("app", "SwapnoPay Merchant")
                put("version", 11)
                put("timestamp", now)
                put("formatted_date", SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(now)))
                put("merchant_id", merchantId)
                put("backup_type", backupType)
                put("total_records", totalRecords)

                // Customers
                val customersArr = JSONArray()
                customers.forEach { c ->
                    customersArr.put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("phone", c.phone)
                        put("email", c.email ?: JSONObject.NULL)
                        put("address", c.address ?: JSONObject.NULL)
                        put("openingBalance", c.openingBalance)
                        put("currentBalance", c.currentBalance)
                        put("status", c.status)
                        put("createdAt", c.createdAt)
                    })
                }
                put("customers", customersArr)

                // Suppliers
                val suppliersArr = JSONArray()
                suppliers.forEach { s ->
                    suppliersArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("phone", s.phone)
                        put("email", s.email ?: JSONObject.NULL)
                        put("address", s.address ?: JSONObject.NULL)
                        put("openingBalance", s.openingBalance)
                        put("currentBalance", s.currentBalance)
                        put("createdAt", s.createdAt)
                    })
                }
                put("suppliers", suppliersArr)

                // Products
                val productsArr = JSONArray()
                products.forEach { p ->
                    productsArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("code", p.code ?: JSONObject.NULL)
                        put("category", p.category ?: "General")
                        put("purchasePrice", p.purchasePrice)
                        put("salePrice", p.salePrice)
                        put("costPrice", p.costPrice)
                        put("askingPrice", p.askingPrice)
                        put("stockQuantity", p.stockQuantity)
                        put("minStockThreshold", p.minStockThreshold)
                        put("unit", p.unit)
                        put("qrCode", p.qrCode ?: JSONObject.NULL)
                        put("imageUrl", p.imageUrl ?: JSONObject.NULL)
                        put("createdAt", p.createdAt)
                    })
                }
                put("products", productsArr)

                // Product Variants
                val variantsArr = JSONArray()
                productVariants.forEach { v ->
                    variantsArr.put(JSONObject().apply {
                        put("id", v.id)
                        put("productId", v.productId)
                        put("variantName", v.variantName)
                        put("supplierId", v.supplierId ?: JSONObject.NULL)
                        put("qrCode", v.qrCode)
                        put("costPrice", v.costPrice)
                        put("askingPrice", v.askingPrice)
                        put("salePrice", v.salePrice)
                        put("stockQuantity", v.stockQuantity)
                        put("createdAt", v.createdAt)
                    })
                }
                put("product_variants", variantsArr)

                // Stock Transactions
                val stockArr = JSONArray()
                stockTransactions.forEach { st ->
                    stockArr.put(JSONObject().apply {
                        put("id", st.id)
                        put("productId", st.productId)
                        put("variantId", st.variantId ?: JSONObject.NULL)
                        put("type", st.type)
                        put("quantity", st.quantity)
                        put("price", st.price)
                        put("customerId", st.customerId ?: JSONObject.NULL)
                        put("supplierId", st.supplierId ?: JSONObject.NULL)
                        put("referenceNote", st.referenceNote ?: JSONObject.NULL)
                        put("createdAt", st.createdAt)
                    })
                }
                put("stock_transactions", stockArr)

                // Ledger Transactions
                val ledgerArr = JSONArray()
                ledgerTransactions.forEach { lt ->
                    ledgerArr.put(JSONObject().apply {
                        put("id", lt.id)
                        put("customerId", lt.customerId ?: JSONObject.NULL)
                        put("supplierId", lt.supplierId ?: JSONObject.NULL)
                        put("type", lt.type)
                        put("amount", lt.amount)
                        put("date", lt.date)
                        put("note", lt.note ?: JSONObject.NULL)
                        put("productDetailsJson", lt.productDetailsJson)
                        put("isVoiceEntry", lt.isVoiceEntry)
                        put("attachmentUri", lt.attachmentUri ?: JSONObject.NULL)
                        put("paymentMethod", lt.paymentMethod)
                        put("invoiceNo", lt.invoiceNo ?: JSONObject.NULL)
                        put("isSynced", lt.isSynced)
                    })
                }
                put("ledger_transactions", ledgerArr)

                // POS Sales
                val posArr = JSONArray()
                posSales.forEach { ps ->
                    posArr.put(JSONObject().apply {
                        put("id", ps.id)
                        put("invoiceNo", ps.invoiceNo)
                        put("customerId", ps.customerId ?: JSONObject.NULL)
                        put("customerName", ps.customerName)
                        put("customerPhone", ps.customerPhone)
                        put("subtotal", ps.subtotal)
                        put("discount", ps.discount)
                        put("netTotal", ps.netTotal)
                        put("cashReceived", ps.cashReceived)
                        put("changeDue", ps.changeDue)
                        put("paymentMethod", ps.paymentMethod)
                        put("paymentStatus", ps.paymentStatus)
                        put("itemCount", ps.itemCount)
                        put("cartItemsJson", ps.cartItemsJson)
                        put("timestamp", ps.timestamp)
                        put("isSynced", ps.isSynced)
                    })
                }
                put("pos_sales", posArr)

                // Expenses
                val expArr = JSONArray()
                expenses.forEach { e ->
                    expArr.put(JSONObject().apply {
                        put("id", e.id)
                        put("category", e.category)
                        put("amount", e.amount)
                        put("date", e.date)
                        put("description", e.description ?: JSONObject.NULL)
                        put("paymentMethod", e.paymentMethod)
                        put("isRecurring", e.isRecurring)
                        put("createdAt", e.createdAt)
                    })
                }
                put("expenses", expArr)

                // Loans
                val loansArr = JSONArray()
                loans.forEach { l ->
                    loansArr.put(JSONObject().apply {
                        put("id", l.id)
                        put("principalAmount", l.principalAmount)
                        put("interestRate", l.interestRate)
                        put("interestType", l.interestType)
                        put("durationMonths", l.durationMonths)
                        put("monthlyInstallment", l.monthlyInstallment)
                        put("providerName", l.providerName)
                        put("accountReference", l.accountReference)
                        put("startDate", l.startDate)
                        put("isSynced", l.isSynced)
                        put("status", l.status)
                        put("appliedAt", l.appliedAt)
                        put("disbursedAt", l.disbursedAt ?: JSONObject.NULL)
                    })
                }
                put("loans", loansArr)

                val dpsArr = JSONArray()
                dpsAccounts.forEach { account ->
                    dpsArr.put(JSONObject().apply {
                        put("id", account.id)
                        put("providerName", account.providerName)
                        put("accountReference", account.accountReference)
                        put("monthlyDeposit", account.monthlyDeposit)
                        put("interestRate", account.interestRate)
                        put("durationMonths", account.durationMonths)
                        put("startDate", account.startDate)
                        put("maturityDate", account.maturityDate)
                        put("status", account.status)
                        put("isSynced", account.isSynced)
                        put("createdAt", account.createdAt)
                        put("updatedAt", account.updatedAt)
                    })
                }
                put("dps_accounts", dpsArr)

                val installmentArr = JSONArray()
                financeInstallments.forEach { installment ->
                    installmentArr.put(JSONObject().apply {
                        put("id", installment.id)
                        put("accountType", installment.accountType)
                        put("accountId", installment.accountId)
                        put("installmentNumber", installment.installmentNumber)
                        put("dueDate", installment.dueDate)
                        put("principalAmount", installment.principalAmount)
                        put("interestAmount", installment.interestAmount)
                        put("totalAmount", installment.totalAmount)
                        put("status", installment.status)
                        put("paidAt", installment.paidAt ?: JSONObject.NULL)
                        put("paymentMethod", installment.paymentMethod ?: JSONObject.NULL)
                        put("paymentReference", installment.paymentReference ?: JSONObject.NULL)
                        put("isSynced", installment.isSynced)
                        put("createdAt", installment.createdAt)
                    })
                }
                put("finance_installments", installmentArr)

                val notificationArr = JSONArray()
                notifications.forEach { notification ->
                    notificationArr.put(JSONObject().apply {
                        put("id", notification.id)
                        put("type", notification.type)
                        put("title", notification.title)
                        put("message", notification.message)
                        put("severity", notification.severity)
                        put("entityType", notification.entityType ?: JSONObject.NULL)
                        put("entityId", notification.entityId ?: JSONObject.NULL)
                        put("createdAt", notification.createdAt)
                        put("readAt", notification.readAt ?: JSONObject.NULL)
                    })
                }
                put("merchant_notifications", notificationArr)

                // Employees
                val empArr = JSONArray()
                employees.forEach { emp ->
                    empArr.put(JSONObject().apply {
                        put("id", emp.id)
                        put("name", emp.name)
                        put("designation", emp.designation)
                        put("role", emp.role)
                        put("email", emp.email)
                        put("phone", emp.phone)
                        put("department", emp.department)
                        put("status", emp.status)
                        put("avatarUrl", emp.avatarUrl ?: JSONObject.NULL)
                        put("permissionsJson", emp.permissionsJson)
                        put("joinedDate", emp.joinedDate)
                        put("updatedAt", emp.updatedAt)
                    })
                }
                put("employees", empArr)

                // Business Analytics
                businessAnalytics?.let { ba ->
                    put("business_analytics", JSONObject().apply {
                        put("id", ba.id)
                        put("totalRevenue", ba.totalRevenue)
                        put("cashReceived", ba.cashReceived)
                        put("totalDues", ba.totalDues)
                        put("totalPayables", ba.totalPayables)
                        put("totalExpenses", ba.totalExpenses)
                        put("netProfit", ba.netProfit)
                        put("lastUpdated", ba.lastUpdated)
                    })
                }

                // Merchant Numbers
                val numbersArr = JSONArray()
                merchantNumbers.forEach { mn ->
                    numbersArr.put(JSONObject().apply {
                        put("number", mn.number)
                        put("method", mn.method)
                        put("accountType", mn.accountType)
                        put("isActive", mn.isActive)
                        put("isDefault", mn.isDefault)
                        put("updatedAt", mn.updatedAt)
                    })
                }
                put("merchant_numbers", numbersArr)

                val formsArr = JSONArray()
                paymentForms.forEach { form ->
                    formsArr.put(JSONObject().apply {
                        put("id", form.id)
                        put("payloadJson", form.payloadJson)
                        put("updatedAt", form.updatedAt)
                        put("isDirty", form.isDirty)
                    })
                }
                put("payment_forms", formsArr)

                val submissionsArr = JSONArray()
                formSubmissions.forEach { submission ->
                    submissionsArr.put(JSONObject().apply {
                        put("id", submission.id)
                        put("formId", submission.formId)
                        put("payloadJson", submission.payloadJson)
                        put("submittedAt", submission.submittedAt)
                        put("isDirty", submission.isDirty)
                    })
                }
                put("form_submissions", submissionsArr)

                val ordersArr = JSONArray()
                orders.forEach { order ->
                    ordersArr.put(JSONObject().apply {
                        put("id", order.id)
                        put("customerName", order.customerName)
                        put("customerPhone", order.customerPhone)
                        put("amount", order.amount)
                        put("status", order.status)
                        put("method", order.method)
                        put("createdAt", order.createdAt)
                        put("expiresAt", order.expiresAt)
                        put("notes", order.notes)
                    })
                }
                put("orders", ordersArr)

                val paymentsArr = JSONArray()
                payments.forEach { payment ->
                    paymentsArr.put(JSONObject().apply {
                        put("id", payment.id)
                        put("amount", payment.amount)
                        put("sender", payment.sender)
                        put("timestamp", payment.timestamp)
                        put("status", payment.status)
                        put("method", payment.method)
                        put("orderId", payment.orderId ?: JSONObject.NULL)
                    })
                }
                put("payments", paymentsArr)

                val appealsArr = JSONArray()
                appeals.forEach { appeal ->
                    appealsArr.put(JSONObject().apply {
                        put("id", appeal.id)
                        put("orderId", appeal.orderId)
                        put("amount", appeal.amount)
                        put("customerName", appeal.customerName)
                        put("customerPhone", appeal.customerPhone)
                        put("trxId", appeal.trxId)
                        put("timestamp", appeal.timestamp)
                        put("status", appeal.status)
                    })
                }
                put("appeals", appealsArr)

                // Profile
                profile?.let { p ->
                    put("merchant_profile", JSONObject().apply {
                        put("id", p.id)
                        put("businessName", p.businessName)
                        put("email", p.email)
                        put("phone", p.phone)
                        put("businessType", p.businessType)
                        put("website", p.website)
                        put("primaryBank", p.primaryBank)
                        put("accountHolder", p.accountHolder)
                        put("accountNumber", p.accountNumber)
                        put("kycStatus", p.kycStatus)
                        put("photoUrl", p.photoUrl)
                    })
                }
            }

            // Write JSON to disk
            targetFile.writeText(rootJson.toString(2))

            val fileSizeBytes = targetFile.length()
            val info = BackupSnapshotInfo(
                fileName = fileName,
                filePath = targetFile.absolutePath,
                timestamp = now,
                formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(now)),
                formattedSize = formatBytes(fileSizeBytes),
                sizeBytes = fileSizeBytes,
                type = backupType,
                totalRecords = totalRecords
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 2. GET BACKUP HISTORY ────────────────────────────────────────────────
    fun getBackupHistory(context: Context): List<BackupSnapshotInfo> {
        val backupsDir = getBackupsDir(context)
        val files = backupsDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?: return emptyList()

        return files.sortedByDescending { it.lastModified() }.map { file ->
            val size = file.length()
            var dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(file.lastModified()))
            var type = "Local Snapshot"
            var records = 0
            var timestamp = file.lastModified()

            try {
                val headerSnippet = file.readText().take(500)
                if (headerSnippet.contains("backup_type")) {
                    val json = JSONObject(file.readText())
                    type = json.optString("backup_type", "Local Snapshot")
                    records = json.optInt("total_records", 0)
                    timestamp = json.optLong("timestamp", file.lastModified())
                    dateStr = json.optString("formatted_date", dateStr)
                }
            } catch (_: Exception) {}

            BackupSnapshotInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                timestamp = timestamp,
                formattedDate = dateStr,
                formattedSize = formatBytes(size),
                sizeBytes = size,
                type = type,
                totalRecords = records
            )
        }
    }

    // ── 3. DELETE BACKUP FILE ────────────────────────────────────────────────
    fun deleteBackupFile(context: Context, fileName: String): Boolean {
        val backupsDir = getBackupsDir(context)
        val file = File(backupsDir, fileName)
        if (!file.canonicalPath.startsWith(backupsDir.canonicalPath)) {
            return false
        }
        return if (file.exists()) file.delete() else false
    }

    // ── 4. RESTORE DATABASE FROM LOCAL JSON SNAPSHOT ─────────────────────────
    suspend fun restoreFromSnapshotJson(
        repository: AppRepository,
        jsonContent: String,
        merchantId: String
    ): RestoreSummary = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonContent)
            val restoredCounts = mutableMapOf<String, Int>()

            // 1. Customers
            if (root.has("customers")) {
                val arr = root.getJSONArray("customers")
                val list = mutableListOf<CustomerEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        CustomerEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            name = obj.getString("name"),
                            phone = obj.optString("phone", ""),
                            email = if (obj.isNull("email")) null else obj.optString("email"),
                            address = if (obj.isNull("address")) null else obj.optString("address"),
                            openingBalance = obj.optDouble("openingBalance", 0.0),
                            currentBalance = obj.optDouble("currentBalance", 0.0),
                            status = obj.optString("status", "VIP"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertCustomers(list)
                    restoredCounts["Customers"] = list.size
                }
            }

            // 2. Suppliers
            if (root.has("suppliers")) {
                val arr = root.getJSONArray("suppliers")
                val list = mutableListOf<SupplierEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SupplierEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            name = obj.getString("name"),
                            phone = obj.optString("phone", ""),
                            email = if (obj.isNull("email")) null else obj.optString("email"),
                            address = if (obj.isNull("address")) null else obj.optString("address"),
                            openingBalance = obj.optDouble("openingBalance", 0.0),
                            currentBalance = obj.optDouble("currentBalance", 0.0),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertSuppliers(list)
                    restoredCounts["Suppliers"] = list.size
                }
            }

            // 3. Products
            if (root.has("products")) {
                val arr = root.getJSONArray("products")
                val list = mutableListOf<ProductItemEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ProductItemEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            name = obj.getString("name"),
                            code = if (obj.isNull("code")) null else obj.optString("code"),
                            category = obj.optString("category", "General"),
                            purchasePrice = obj.optDouble("purchasePrice", 0.0),
                            salePrice = obj.optDouble("salePrice", 0.0),
                            costPrice = obj.optDouble("costPrice", obj.optDouble("purchasePrice", 0.0)),
                            askingPrice = obj.optDouble("askingPrice", obj.optDouble("salePrice", 0.0)),
                            stockQuantity = obj.optDouble("stockQuantity", 0.0),
                            minStockThreshold = obj.optDouble("minStockThreshold", 5.0),
                            unit = obj.optString("unit", "pcs"),
                            qrCode = if (obj.isNull("qrCode")) null else obj.optString("qrCode"),
                            imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertProducts(list)
                    restoredCounts["Products"] = list.size
                }
            }

            // 4. Product Variants
            if (root.has("product_variants")) {
                val arr = root.getJSONArray("product_variants")
                val list = mutableListOf<ProductVariantEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ProductVariantEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            productId = obj.getString("productId"),
                            variantName = obj.getString("variantName"),
                            supplierId = if (obj.isNull("supplierId")) null else obj.optString("supplierId"),
                            qrCode = obj.getString("qrCode"),
                            costPrice = obj.optDouble("costPrice", 0.0),
                            askingPrice = obj.optDouble("askingPrice", 0.0),
                            salePrice = obj.optDouble("salePrice", 0.0),
                            stockQuantity = obj.optDouble("stockQuantity", 0.0),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertProductVariants(list)
                    restoredCounts["Variants"] = list.size
                }
            }

            // 5. Stock Transactions
            if (root.has("stock_transactions")) {
                val arr = root.getJSONArray("stock_transactions")
                val list = mutableListOf<StockTransactionEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        StockTransactionEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            productId = obj.getString("productId"),
                            variantId = if (obj.isNull("variantId")) null else obj.optString("variantId"),
                            type = obj.getString("type"),
                            quantity = obj.optDouble("quantity", 0.0),
                            price = obj.optDouble("price", 0.0),
                            customerId = if (obj.isNull("customerId")) null else obj.optString("customerId"),
                            supplierId = if (obj.isNull("supplierId")) null else obj.optString("supplierId"),
                            referenceNote = if (obj.isNull("referenceNote")) null else obj.optString("referenceNote"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertStockTransactions(list)
                    restoredCounts["Stock Movements"] = list.size
                }
            }

            // 6. Ledger Transactions
            if (root.has("ledger_transactions")) {
                val arr = root.getJSONArray("ledger_transactions")
                val list = mutableListOf<LedgerTransactionEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        LedgerTransactionEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            customerId = if (obj.isNull("customerId")) null else obj.optString("customerId"),
                            supplierId = if (obj.isNull("supplierId")) null else obj.optString("supplierId"),
                            type = obj.getString("type"),
                            amount = obj.optDouble("amount", 0.0),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            note = if (obj.isNull("note")) null else obj.optString("note"),
                            productDetailsJson = obj.optString("productDetailsJson", "[]"),
                            isVoiceEntry = obj.optBoolean("isVoiceEntry", false),
                            attachmentUri = if (obj.isNull("attachmentUri")) null else obj.optString("attachmentUri"),
                            paymentMethod = obj.optString("paymentMethod", "Cash"),
                            invoiceNo = if (obj.isNull("invoiceNo")) null else obj.optString("invoiceNo"),
                            isSynced = obj.optBoolean("isSynced", true)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertLedgerTransactions(list)
                    restoredCounts["Ledger Entries"] = list.size
                }
            }

            // 7. POS Sales
            if (root.has("pos_sales")) {
                val arr = root.getJSONArray("pos_sales")
                val list = mutableListOf<PosSaleEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        PosSaleEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            invoiceNo = obj.getString("invoiceNo"),
                            customerId = if (obj.isNull("customerId")) null else obj.optString("customerId"),
                            customerName = obj.optString("customerName", "Walk-in Customer"),
                            customerPhone = obj.optString("customerPhone", ""),
                            subtotal = obj.optDouble("subtotal", 0.0),
                            discount = obj.optDouble("discount", 0.0),
                            netTotal = obj.optDouble("netTotal", 0.0),
                            cashReceived = obj.optDouble("cashReceived", 0.0),
                            changeDue = obj.optDouble("changeDue", 0.0),
                            paymentMethod = obj.optString("paymentMethod", "Cash"),
                            paymentStatus = obj.optString("paymentStatus", "PAID"),
                            itemCount = obj.optInt("itemCount", 1),
                            cartItemsJson = obj.optString("cartItemsJson", "[]"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            isSynced = obj.optBoolean("isSynced", true)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertPosSales(list)
                    restoredCounts["POS Invoices"] = list.size
                }
            }

            // 8. Expenses
            if (root.has("expenses")) {
                val arr = root.getJSONArray("expenses")
                val list = mutableListOf<ExpenseEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ExpenseEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            category = obj.getString("category"),
                            amount = obj.optDouble("amount", 0.0),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            description = if (obj.isNull("description")) null else obj.optString("description"),
                            paymentMethod = obj.optString("paymentMethod", "Cash"),
                            isRecurring = obj.optBoolean("isRecurring", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertExpenses(list)
                    restoredCounts["Expenses"] = list.size
                }
            }

            // 9. Loans
            if (root.has("loans")) {
                val arr = root.getJSONArray("loans")
                val list = mutableListOf<BusinessLoanEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        BusinessLoanEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            principalAmount = obj.optDouble("principalAmount", 0.0),
                            interestRate = obj.optDouble("interestRate", 0.0),
                            interestType = obj.optString("interestType", "Flat"),
                            durationMonths = obj.optInt("durationMonths", 12),
                            monthlyInstallment = obj.optDouble("monthlyInstallment", 0.0),
                            providerName = obj.optString("providerName", ""),
                            accountReference = obj.optString("accountReference", ""),
                            startDate = obj.optLong("startDate", obj.optLong("appliedAt", 0L)),
                            isSynced = obj.optBoolean("isSynced", false),
                            status = obj.optString("status", "applied"),
                            appliedAt = obj.optLong("appliedAt", System.currentTimeMillis()),
                            disbursedAt = if (obj.isNull("disbursedAt")) null else obj.optLong("disbursedAt")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.insertLoans(list)
                    restoredCounts["Loans"] = list.size
                }
            }

            if (root.has("dps_accounts")) {
                val arr = root.getJSONArray("dps_accounts")
                val list = mutableListOf<DpsAccountEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        DpsAccountEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            providerName = obj.getString("providerName"),
                            accountReference = obj.getString("accountReference"),
                            monthlyDeposit = obj.optDouble("monthlyDeposit", 0.0),
                            interestRate = obj.optDouble("interestRate", 0.0),
                            durationMonths = obj.optInt("durationMonths", 1),
                            startDate = obj.optLong("startDate", System.currentTimeMillis()),
                            maturityDate = obj.optLong("maturityDate", System.currentTimeMillis()),
                            status = obj.optString("status", "ACTIVE"),
                            isSynced = obj.optBoolean("isSynced", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.restoreDpsAccounts(list)
                    restoredCounts["DPS Accounts"] = list.size
                }
            }

            if (root.has("finance_installments")) {
                val arr = root.getJSONArray("finance_installments")
                val list = mutableListOf<FinanceInstallmentEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        FinanceInstallmentEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            accountType = obj.getString("accountType"),
                            accountId = obj.getString("accountId"),
                            installmentNumber = obj.optInt("installmentNumber", i + 1),
                            dueDate = obj.optLong("dueDate", System.currentTimeMillis()),
                            principalAmount = obj.optDouble("principalAmount", 0.0),
                            interestAmount = obj.optDouble("interestAmount", 0.0),
                            totalAmount = obj.optDouble("totalAmount", 0.0),
                            status = obj.optString("status", "PENDING"),
                            paidAt = if (obj.isNull("paidAt")) null else obj.optLong("paidAt"),
                            paymentMethod = if (obj.isNull("paymentMethod")) null else obj.optString("paymentMethod"),
                            paymentReference = if (obj.isNull("paymentReference")) null else obj.optString("paymentReference"),
                            isSynced = obj.optBoolean("isSynced", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.restoreFinanceInstallments(list)
                    restoredCounts["Finance Installments"] = list.size
                }
            }

            // 10. Employees
            if (root.has("employees")) {
                val arr = root.getJSONArray("employees")
                val list = mutableListOf<EmployeeEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        EmployeeEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            name = obj.getString("name"),
                            designation = obj.optString("designation", "Staff"),
                            role = obj.optString("role", "Staff"),
                            email = obj.optString("email", ""),
                            phone = obj.optString("phone", ""),
                            department = obj.optString("department", "General"),
                            status = obj.optString("status", "Active"),
                            avatarUrl = if (obj.isNull("avatarUrl")) null else obj.optString("avatarUrl"),
                            permissionsJson = obj.optString("permissionsJson", "[]"),
                            joinedDate = obj.optString("joinedDate", "Today"),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.upsertEmployees(list)
                    restoredCounts["Employees"] = list.size
                }
            }

            if (root.has("merchant_notifications")) {
                val arr = root.getJSONArray("merchant_notifications")
                val list = mutableListOf<MerchantNotificationEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        MerchantNotificationEntity(
                            id = obj.getString("id"),
                            merchantId = merchantId,
                            type = obj.optString("type", "SYSTEM"),
                            title = obj.getString("title"),
                            message = obj.getString("message"),
                            severity = obj.optString("severity", "INFO"),
                            entityType = if (obj.isNull("entityType")) null else obj.optString("entityType"),
                            entityId = if (obj.isNull("entityId")) null else obj.optString("entityId"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            readAt = if (obj.isNull("readAt")) null else obj.optLong("readAt")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    repository.upsertMerchantNotifications(list)
                    restoredCounts["Notifications"] = list.size
                }
            }

            if (root.has("merchant_numbers")) {
                val arr = root.getJSONArray("merchant_numbers")
                var count = 0
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    repository.upsertMerchantNumber(
                        MerchantNumberEntity(
                            number = obj.getString("number"),
                            merchantId = merchantId,
                            method = obj.getString("method"),
                            accountType = obj.optString("accountType", "PERSONAL"),
                            isActive = obj.optBoolean("isActive", true),
                            isDefault = obj.optBoolean("isDefault", false),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                    count++
                }
                if (count > 0) restoredCounts["Merchant Numbers"] = count
            }

            if (root.has("payment_forms")) {
                val arr = root.getJSONArray("payment_forms")
                val list = mutableListOf<PaymentFormCacheEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(PaymentFormCacheEntity(
                        id = obj.getString("id"), merchantId = merchantId,
                        payloadJson = obj.optString("payloadJson", "{}"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        isDirty = obj.optBoolean("isDirty", false)
                    ))
                }
                if (list.isNotEmpty()) {
                    repository.upsertPaymentFormCaches(list)
                    restoredCounts["Payment Forms"] = list.size
                }
            }

            if (root.has("form_submissions")) {
                val arr = root.getJSONArray("form_submissions")
                val list = mutableListOf<FormSubmissionCacheEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(FormSubmissionCacheEntity(
                        id = obj.getString("id"), merchantId = merchantId,
                        formId = obj.getString("formId"), payloadJson = obj.optString("payloadJson", "{}"),
                        submittedAt = obj.optLong("submittedAt", System.currentTimeMillis()),
                        isDirty = obj.optBoolean("isDirty", false)
                    ))
                }
                if (list.isNotEmpty()) {
                    repository.upsertFormSubmissionCaches(list)
                    restoredCounts["Form Submissions"] = list.size
                }
            }

            if (root.has("orders")) {
                val arr = root.getJSONArray("orders")
                val list = mutableListOf<CachedOrderEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(CachedOrderEntity(
                        id = obj.getString("id"), merchantId = merchantId,
                        customerName = obj.getString("customerName"), customerPhone = obj.optString("customerPhone", ""),
                        amount = obj.optDouble("amount", 0.0), status = obj.optString("status", "PENDING"),
                        method = obj.optString("method", "UNKNOWN"), createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        expiresAt = obj.optLong("expiresAt", System.currentTimeMillis()), notes = obj.optString("notes", "")
                    ))
                }
                if (list.isNotEmpty()) {
                    repository.insertOrders(list)
                    restoredCounts["Orders"] = list.size
                }
            }

            if (root.has("payments")) {
                val arr = root.getJSONArray("payments")
                val list = mutableListOf<CachedPaymentEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(CachedPaymentEntity(
                        id = obj.getString("id"), merchantId = merchantId,
                        amount = obj.optDouble("amount", 0.0), sender = obj.optString("sender", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = obj.optString("status", "UNMATCHED"), method = obj.optString("method", "UNKNOWN"),
                        orderId = if (obj.isNull("orderId")) null else obj.optString("orderId")
                    ))
                }
                if (list.isNotEmpty()) {
                    repository.insertPayments(list)
                    restoredCounts["Payments"] = list.size
                }
            }

            if (root.has("appeals")) {
                val arr = root.getJSONArray("appeals")
                val list = mutableListOf<AppealEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(AppealEntity(
                        id = obj.getString("id"), merchantId = merchantId, orderId = obj.getString("orderId"),
                        amount = obj.optDouble("amount", 0.0), customerName = obj.getString("customerName"),
                        customerPhone = obj.optString("customerPhone", ""), trxId = obj.getString("trxId"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = obj.optString("status", "PENDING_REVIEW")
                    ))
                }
                if (list.isNotEmpty()) {
                    repository.insertAppeals(list)
                    restoredCounts["Appeals"] = list.size
                }
            }

            if (root.has("business_analytics")) {
                val obj = root.getJSONObject("business_analytics")
                repository.insertBusinessAnalytics(BusinessAnalyticsEntity(
                    id = obj.optString("id", merchantId), merchantId = merchantId,
                    totalRevenue = obj.optDouble("totalRevenue", 0.0), cashReceived = obj.optDouble("cashReceived", 0.0),
                    totalDues = obj.optDouble("totalDues", 0.0), totalPayables = obj.optDouble("totalPayables", 0.0),
                    totalExpenses = obj.optDouble("totalExpenses", 0.0), netProfit = obj.optDouble("netProfit", 0.0),
                    lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                ))
                restoredCounts["Business Analytics"] = 1
            }

            if (root.has("merchant_profile")) {
                val obj = root.getJSONObject("merchant_profile")
                repository.insertMerchantProfile(MerchantProfileEntity(
                    id = obj.optString("id", merchantId), businessName = obj.optString("businessName", ""),
                    email = obj.optString("email", ""), phone = obj.optString("phone", ""),
                    businessType = obj.optString("businessType", ""), website = obj.optString("website", ""),
                    primaryBank = obj.optString("primaryBank", ""), accountHolder = obj.optString("accountHolder", ""),
                    accountNumber = obj.optString("accountNumber", ""), kycStatus = obj.optString("kycStatus", "UNVERIFIED"),
                    photoUrl = obj.optString("photoUrl", "")
                ))
                restoredCounts["Merchant Profile"] = 1
            }

            val totalRestored = restoredCounts.values.sum()
            RestoreSummary(
                success = true,
                message = "Successfully restored $totalRestored records across ${restoredCounts.size} tables.",
                restoredTables = restoredCounts
            )
        } catch (e: Exception) {
            RestoreSummary(
                success = false,
                message = "Restore failed: ${e.message}"
            )
        }
    }

    // ── 5. REAL STORAGE USAGE STATISTICS (NO MOCK DATA) ──────────────────────
    fun getStorageStatistics(context: Context): StorageStats {
        // Real SQLite database file + WAL + SHM size
        val dbFile = context.getDatabasePath("swapnopay_database")
        var dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        val walFile = File(dbFile.parentFile, "swapnopay_database-wal")
        if (walFile.exists()) dbBytes += walFile.length()
        val shmFile = File(dbFile.parentFile, "swapnopay_database-shm")
        if (shmFile.exists()) dbBytes += shmFile.length()

        // Real Cache Directory recursive size
        val cacheBytes = getFolderSize(context.cacheDir)

        // Real Media / Files Directory recursive size (excluding database)
        val filesBytes = getFolderSize(context.filesDir)

        val totalAppBytes = dbBytes + cacheBytes + filesBytes

        // Real Device Free Space via StatFs
        var freeDiskBytes = 50L * 1024L * 1024L * 1024L // fallback 50GB
        try {
            val stat = StatFs(context.filesDir.path)
            freeDiskBytes = stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {}

        // Calculate realistic usage percent against typical app quota / device storage
        val usedPercentage = ((totalAppBytes.toFloat() / (totalAppBytes + freeDiskBytes).toFloat()) * 100f)
            .coerceIn(0.5f, 100f)

        return StorageStats(
            databaseSizeBytes = dbBytes,
            databaseSizeFormatted = formatBytes(dbBytes),
            cacheSizeBytes = cacheBytes,
            cacheSizeFormatted = formatBytes(cacheBytes),
            mediaSizeBytes = filesBytes,
            mediaSizeFormatted = formatBytes(filesBytes),
            totalAppBytes = totalAppBytes,
            totalAppFormatted = formatBytes(totalAppBytes),
            freeDiskBytes = freeDiskBytes,
            freeDiskFormatted = formatBytes(freeDiskBytes),
            usedPercentage = usedPercentage
        )
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { child ->
            size += if (child.isDirectory) getFolderSize(child) else child.length()
        }
        return size
    }

    // ── 6. CLEAR REAL APP CACHE ──────────────────────────────────────────────
    fun clearCache(context: Context): Long {
        val initialSize = getFolderSize(context.cacheDir)
        try {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
        } catch (_: Exception) {}
        return initialSize
    }

    // ── 7. CALCULATE NEXT BACKUP TIME DYNAMICALLY ────────────────────────────
    fun calculateNextBackupTime(frequency: String, isAutoEnabled: Boolean): String {
        if (!isAutoEnabled) return "Disabled (Manual Only)"

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        return when {
            frequency.contains("Daily at 11:30 PM", ignoreCase = true) -> {
                if (currentHour < 23 || (currentHour == 23 && currentMinute < 30)) {
                    "Today, 11:30 PM"
                } else {
                    "Tomorrow, 11:30 PM"
                }
            }
            frequency.contains("Daily at 02:00 AM", ignoreCase = true) -> {
                if (currentHour < 2) {
                    "Today, 02:00 AM"
                } else {
                    "Tomorrow, 02:00 AM"
                }
            }
            frequency.contains("Weekly", ignoreCase = true) -> {
                val daysUntilSunday = (Calendar.SUNDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (daysUntilSunday == 0 && currentHour < 23) {
                    "Today, midnight"
                } else {
                    "Next Sunday, 12:00 AM"
                }
            }
            frequency.contains("Monthly", ignoreCase = true) -> {
                "1st of Next Month"
            }
            else -> "Scheduled Automatically"
        }
    }
}
