package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FinanceInstallmentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceManagerScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isDark by viewModel.isDarkMode.collectAsState()
    val dpsAccounts by viewModel.dpsAccounts.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val installments by viewModel.financeInstallments.collectAsState()
    var accountType by rememberSaveable { mutableStateOf("DPS") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var showAdd by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<FinanceInstallmentEntity?>(null) }
    var expandedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val bg = if (isDark) Color(0xFF070707) else Color(0xFFF8FAFC)
    val card = if (isDark) Color(0xFF13100A) else Color.White
    val border = if (isDark) Color(0xFF382A0B) else Color(0xFFE2E8F0)
    val text = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val muted = if (isDark) Color(0xFF9CA3AF) else Color(0xFF64748B)
    val accent = Color(0xFFF5C518)

    val visibleInstallments = remember(installments, accountType, statusFilter, now) {
        installments.filter { row ->
            val effectiveStatus = if (row.status == "PENDING" && row.dueDate < now) "OVERDUE" else row.status
            row.accountType == accountType && (statusFilter == "ALL" || effectiveStatus == statusFilter)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.appendLine("account_type,account_reference,provider,installment,due_date,principal,interest,total,status,paid_at,payment_method,payment_reference")
                    visibleInstallments.forEach { row ->
                        val dps = dpsAccounts.firstOrNull { it.id == row.accountId }
                        val loan = loans.firstOrNull { it.id == row.accountId }
                        val values = listOf(
                            row.accountType,
                            dps?.accountReference ?: loan?.accountReference.orEmpty(),
                            dps?.providerName ?: loan?.providerName.orEmpty(),
                            row.installmentNumber.toString(),
                            isoDate(row.dueDate),
                            row.principalAmount.toString(),
                            row.interestAmount.toString(),
                            row.totalAmount.toString(),
                            if (row.status == "PENDING" && row.dueDate < now) "OVERDUE" else row.status,
                            row.paidAt?.let(::isoDate).orEmpty(),
                            row.paymentMethod.orEmpty(),
                            row.paymentReference.orEmpty()
                        )
                        writer.appendLine(values.joinToString(",") { csvCell(it) })
                    }
                } ?: error("Unable to open selected file")
            }.onSuccess {
                Toast.makeText(context, "Finance CSV exported", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DPS & EMI Manager", fontWeight = FontWeight.Bold)
                        Text("Merchant database schedules", fontSize = 11.sp, color = muted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCalculator = true }) {
                        Icon(Icons.Default.Calculate, "Calculator", tint = accent)
                    }
                    IconButton(
                        onClick = { exportLauncher.launch("finance_${accountType.lowercase()}_${isoDate(now)}.csv") },
                        enabled = visibleInstallments.isNotEmpty()
                    ) { Icon(Icons.Default.Download, "Export finance CSV") }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add account") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card, titleContentColor = text)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("DPS", "LOAN").forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = accountType == type,
                        onClick = { accountType = type; expandedAccountId = null },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(if (type == "LOAN") "Loan / EMI" else "DPS") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "PENDING", "PAID", "OVERDUE").forEach { value ->
                    FilterChip(
                        selected = statusFilter == value,
                        onClick = { statusFilter = value },
                        label = { Text(if (value == "ALL") "All" else value.lowercase().replaceFirstChar(Char::uppercase)) }
                    )
                }
            }

            val accountIds = if (accountType == "DPS") dpsAccounts.map { it.id } else loans.map { it.id }
            if (accountIds.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBalance, null, tint = muted, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No $accountType account", color = text, fontWeight = FontWeight.Bold)
                        Text("Add the real provider and account reference.", color = muted, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                            Text("Add account", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    items(accountIds, key = { it }) { accountId ->
                        val dps = dpsAccounts.firstOrNull { it.id == accountId }
                        val loan = loans.firstOrNull { it.id == accountId }
                        val accountRows = visibleInstallments.filter { it.accountId == accountId }
                        val allAccountRows = installments.filter { it.accountType == accountType && it.accountId == accountId }
                        val paid = allAccountRows.filter { it.status == "PAID" }.sumOf { it.totalAmount }
                        val outstanding = allAccountRows.filter { it.status != "PAID" && it.status != "WAIVED" }.sumOf { it.totalAmount }
                        val expanded = expandedAccountId == accountId
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedAccountId = if (expanded) null else accountId
                            },
                            colors = CardDefaults.cardColors(containerColor = card),
                            border = BorderStroke(1.dp, border),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(dps?.providerName ?: loan?.providerName.orEmpty(), color = text, fontWeight = FontWeight.Bold)
                                        Text(dps?.accountReference ?: loan?.accountReference.orEmpty(), color = muted, fontSize = 11.sp)
                                    }
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = { Text(if ((dps?.isSynced ?: loan?.isSynced) == true) "Synced" else "Offline pending") }
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { if (paid + outstanding == 0.0) 0f else (paid / (paid + outstanding)).toFloat() },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = accent
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Paid: BDT ${money(paid)}", color = muted, fontSize = 12.sp)
                                    Text("Remaining: BDT ${money(outstanding)}", color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (expanded) {
                                    HorizontalDivider(color = border)
                                    if (accountRows.isEmpty()) Text("No installments match this filter.", color = muted, fontSize = 12.sp)
                                    accountRows.forEach { row ->
                                        val effectiveStatus = if (row.status == "PENDING" && row.dueDate < now) "OVERDUE" else row.status
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("#${row.installmentNumber} • ${displayDate(row.dueDate)}", color = text, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "$effectiveStatus • BDT ${money(row.totalAmount)}${if (!row.isSynced && row.status == "PAID") " • sync pending" else ""}",
                                                    color = when (effectiveStatus) {
                                                        "PAID" -> Color(0xFF10B981)
                                                        "OVERDUE" -> Color(0xFFEF4444)
                                                        else -> muted
                                                    },
                                                    fontSize = 11.sp
                                                )
                                            }
                                            if (effectiveStatus in setOf("PENDING", "OVERDUE")) {
                                                TextButton(onClick = { paymentTarget = row }) { Text("Record payment") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        FinanceAccountDialog(
            type = accountType,
            onDismiss = { showAdd = false },
            onSave = { provider, reference, amount, rate, months, interestType ->
                val callback: (Boolean, String) -> Unit = { ok, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (ok) showAdd = false
                }
                if (accountType == "DPS") {
                    viewModel.addDpsAccount(provider, reference, amount, rate, months, System.currentTimeMillis(), callback)
                } else {
                    viewModel.addLoanAccount(provider, reference, amount, rate, interestType, months, System.currentTimeMillis(), callback)
                }
            }
        )
    }
    paymentTarget?.let { row ->
        RecordInstallmentPaymentDialog(
            installment = row,
            onDismiss = { paymentTarget = null },
            onSave = { method, reference ->
                viewModel.payFinanceInstallment(row.id, method, reference) { ok, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (ok) paymentTarget = null
                }
            }
        )
    }

    if (showCalculator) {
        if (accountType == "LOAN") {
            LoanCalculatorDialog(
                isDarkMode = isDark,
                onDismiss = { showCalculator = false },
                onApplyToNewLoan = { principal, rate, months, type ->
                    showCalculator = false
                    showAdd = true
                }
            )
        } else {
            DepositCalculatorDialog(
                isDarkMode = isDark,
                onDismiss = { showCalculator = false },
                onApplyToNewDps = { monthly, rate, months ->
                    showCalculator = false
                    showAdd = true
                }
            )
        }
    }
}

@Composable
private fun FinanceAccountDialog(
    type: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double, Int, String) -> Unit
) {
    var provider by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    var interestType by remember { mutableStateOf("Reducing") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $type account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(provider, { provider = it }, label = { Text("Bank / provider") }, singleLine = true)
                OutlinedTextField(reference, { reference = it }, label = { Text("Account reference") }, singleLine = true)
                OutlinedTextField(
                    amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(if (type == "DPS") "Monthly deposit (BDT)" else "Principal (BDT)") },
                    singleLine = true
                )
                OutlinedTextField(
                    rate, { rate = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Annual interest rate (%)") }, singleLine = true
                )
                OutlinedTextField(
                    months, { months = it.filter(Char::isDigit) },
                    label = { Text("Tenure (months)") }, singleLine = true
                )
                if (type == "LOAN") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Reducing", "Flat").forEach {
                            FilterChip(selected = interestType == it, onClick = { interestType = it }, label = { Text(it) })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedAmount = amount.toDoubleOrNull()
                val parsedRate = rate.toDoubleOrNull()
                val parsedMonths = months.toIntOrNull()
                if (provider.isBlank() || reference.isBlank() || parsedAmount == null || parsedAmount <= 0.0 ||
                    parsedRate == null || parsedRate < 0.0 || parsedMonths == null || parsedMonths !in 1..600
                ) {
                    error = "Complete every field with valid positive values."
                } else {
                    onSave(provider.trim(), reference.trim(), parsedAmount, parsedRate, parsedMonths, interestType)
                }
            }) { Text("Save schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecordInstallmentPaymentDialog(
    installment: FinanceInstallmentEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var method by remember { mutableStateOf("Bank") }
    var reference by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record installment #${installment.installmentNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BDT ${money(installment.totalAmount)} due ${displayDate(installment.dueDate)}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Bank", "Cash", "MFS", "Card").forEach {
                        FilterChip(selected = method == it, onClick = { method = it }, label = { Text(it) })
                    }
                }
                OutlinedTextField(
                    reference,
                    { reference = it },
                    label = { Text("Bank / receipt reference") },
                    supportingText = { Text("Use a unique reference; this action is idempotent.") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (reference.trim().length < 4) error = "Enter at least 4 reference characters"
                else onSave(method, reference.trim())
            }) { Text("Record paid") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun displayDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))

private fun isoDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

private fun money(value: Double): String = String.format(Locale.US, "%,.2f", value)

private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
