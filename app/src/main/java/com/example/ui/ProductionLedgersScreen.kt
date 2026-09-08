package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.local.LedgerTransactionEntity
import java.text.SimpleDateFormat
import java.util.*

private data class LedgerPartyRow(
    val id: String,
    val name: String,
    val phone: String,
    val openingBalance: Double,
    val currentBalance: Double,
    val createdAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLedgersScreen(viewModel: AppViewModel, initialTab: String = "CUSTOMER") {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val transactions by viewModel.ledgerTransactions.collectAsState()
    val profile by viewModel.activeProfile.collectAsState()
    val dark by viewModel.isDarkMode.collectAsState()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var range by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("BALANCE_DESC") }
    var sortExpanded by remember { mutableStateOf(false) }
    var expandedParty by rememberSaveable { mutableStateOf<String?>(null) }
    var entryParty by remember { mutableStateOf<LedgerPartyRow?>(null) }
    var entryType by remember { mutableStateOf("payment") }
    val cutoff = remember(range) { ledgerRangeCutoff(range) }
    val scopedTransactions = remember(transactions, cutoff) { transactions.filter { cutoff == null || it.date >= cutoff } }
    val baseParties = remember(customers, suppliers, tab) {
        if (tab == "CUSTOMER") customers.map { LedgerPartyRow(it.id, it.name, it.phone, it.openingBalance, it.currentBalance, it.createdAt) }
        else suppliers.map { LedgerPartyRow(it.id, it.name, it.phone, it.openingBalance, it.currentBalance, it.createdAt) }
    }
    val visibleParties = remember(baseParties, scopedTransactions, query, sort, cutoff, tab) {
        baseParties.filter { party ->
            val matchesSearch = query.isBlank() || party.name.contains(query, true) || party.phone.contains(query)
            val hasActivity = cutoff == null || party.createdAt >= cutoff || scopedTransactions.any {
                if (tab == "CUSTOMER") it.customerId == party.id else it.supplierId == party.id
            }
            matchesSearch && hasActivity
        }.let { rows ->
            when (sort) {
                "NAME" -> rows.sortedBy { it.name.lowercase() }
                "BALANCE_ASC" -> rows.sortedBy { kotlin.math.abs(it.currentBalance) }
                else -> rows.sortedByDescending { kotlin.math.abs(it.currentBalance) }
            }
        }
    }
    val visibleIds = remember(visibleParties) { visibleParties.mapTo(mutableSetOf()) { it.id } }
    val visibleTransactions = remember(scopedTransactions, visibleIds, tab) {
        scopedTransactions.filter { if (tab == "CUSTOMER") it.customerId in visibleIds else it.supplierId in visibleIds }
    }
    val outstanding = visibleParties.sumOf { party ->
        val partyTx = visibleTransactions.filter { if (tab == "CUSTOMER") it.customerId == party.id else it.supplierId == party.id }
        if (partyTx.isNotEmpty()) {
            partyTx.sumOf { if (it.type == "credit") it.amount else -it.amount }.coerceAtLeast(0.0)
        } else {
            kotlin.math.abs(party.currentBalance)
        }
    }
    val paidInRange = visibleTransactions.filter { it.type == "payment" }.sumOf { it.amount }
    val chargedInRange = visibleTransactions.filter { it.type == "credit" }.sumOf { it.amount }
    val bg = if (dark) Color(0xFF070707) else Color(0xFFF8FAFC)
    val card = if (dark) Color(0xFF13100A) else Color.White
    val border = if (dark) Color(0xFF382A0B) else Color(0xFFE2E8F0)
    val text = if (dark) Color.White else Color(0xFF0F172A)
    val muted = if (dark) Color(0xFF9CA3AF) else Color(0xFF64748B)
    val accent = Color(0xFFF5C518)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.appendLine("party_type,party_id,name,phone,opening_balance,current_balance,transaction_id,date,type,amount,payment_method,invoice_no,note")
                visibleParties.forEach { party ->
                    val partyTx = visibleTransactions.filter { if (tab == "CUSTOMER") it.customerId == party.id else it.supplierId == party.id }
                    if (partyTx.isEmpty()) {
                        writer.appendLine(listOf(tab, party.id, party.name, party.phone, party.openingBalance, party.currentBalance, "", "", "", "", "", "", "").joinToString(",") { ledgerCsv(it.toString()) })
                    } else partyTx.forEach { tx ->
                        writer.appendLine(listOf(tab, party.id, party.name, party.phone, party.openingBalance, party.currentBalance,
                            tx.id, ledgerIso(tx.date), tx.type, tx.amount, tx.paymentMethod, tx.invoiceNo.orEmpty(), tx.note.orEmpty())
                            .joinToString(",") { ledgerCsv(it.toString()) })
                    }
                }
            } ?: error("Unable to open the selected file")
        }.onSuccess { Toast.makeText(context, "Ledger CSV exported", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(context, it.localizedMessage ?: "Export failed", Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Column { Text("Ledger Book", fontWeight = FontWeight.Bold); Text("Tenant-isolated account statement", fontSize = 11.sp, color = muted) } },
                navigationIcon = { IconButton(onClick = viewModel::goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.triggerSync() }) { Icon(Icons.Default.Sync, "Sync") }
                    IconButton(
                        onClick = { exportLauncher.launch("${tab.lowercase()}-ledger-${ledgerIso(System.currentTimeMillis())}.csv") },
                        enabled = visibleParties.isNotEmpty()
                    ) { Icon(Icons.Default.FileDownload, "Export CSV") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card, titleContentColor = text)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("CUSTOMER", "SUPPLIER").forEachIndexed { index, value ->
                    SegmentedButton(selected = tab == value, onClick = { tab = value; expandedParty = null }, shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                        Text(if (value == "CUSTOMER") "Customer dues" else "Supplier payable")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "All", "TODAY" to "Today", "7_DAYS" to "7 days", "MONTH" to "This month").forEach { (value, label) ->
                    FilterChip(selected = range == value, onClick = { range = value }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerMetric("Outstanding", outstanding, card, border, text, muted, Modifier.weight(1f))
                LedgerMetric("Paid", paidInRange, card, border, text, muted, Modifier.weight(1f))
                LedgerMetric("Charged", chargedInRange, card, border, text, muted, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search name or phone") }
                )
                Box {
                    OutlinedButton(onClick = { sortExpanded = true }) { Icon(Icons.Default.Sort, null); Text("Sort") }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        listOf("BALANCE_DESC" to "Highest balance", "BALANCE_ASC" to "Lowest balance", "NAME" to "Name A-Z").forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { sort = value; sortExpanded = false })
                        }
                    }
                }
            }
            if (visibleParties.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, null, tint = muted, modifier = Modifier.size(54.dp))
                        Text("No ledger accounts match this filter", color = text, fontWeight = FontWeight.Bold)
                        Text("Add a customer or supplier from the relevant account screen.", color = muted, fontSize = 12.sp)
                    }
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visibleParties, key = { it.id }) { party ->
                    val partyTransactions = visibleTransactions.filter { if (tab == "CUSTOMER") it.customerId == party.id else it.supplierId == party.id }
                    val due = if (partyTransactions.isNotEmpty()) {
                        partyTransactions.sumOf { if (it.type == "credit") it.amount else -it.amount }.coerceAtLeast(0.0)
                    } else {
                        kotlin.math.abs(party.currentBalance)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { expandedParty = if (expandedParty == party.id) null else party.id },
                        colors = CardDefaults.cardColors(containerColor = card), border = BorderStroke(1.dp, border), shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text(party.name, color = text, fontWeight = FontWeight.Bold); Text(party.phone, color = muted, fontSize = 11.sp) }
                                Column(horizontalAlignment = Alignment.End) { Text("BDT ${ledgerMoney(due)}", color = if (due > 0) Color(0xFFEF4444) else Color(0xFF10B981), fontWeight = FontWeight.Bold); Text("Current due", color = muted, fontSize = 10.sp) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { entryParty = party; entryType = "credit" }, modifier = Modifier.weight(1f)) { Text("Add charge") }
                                Button(onClick = { entryParty = party; entryType = "payment" }, enabled = due > 0.0, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Record payment", color = Color.Black) }
                                IconButton(onClick = { shareLedgerReminder(context, profile.businessName, party, due) }, enabled = due > 0.0) { Icon(Icons.Default.Share, "Share reminder") }
                            }
                            if (expandedParty == party.id) {
                                HorizontalDivider(color = border)
                                if (partyTransactions.isEmpty()) Text("No transactions in this period.", color = muted, fontSize = 12.sp)
                                partyTransactions.sortedByDescending { it.date }.forEach { tx -> LedgerTransactionRow(tx, text, muted) }
                            }
                        }
                    }
                }
            }
        }
    }

    entryParty?.let { party ->
        LedgerEntryDialog(
            party = party, partyType = tab, initialType = entryType,
            onDismiss = { entryParty = null },
            onSave = { type, amount, method, note ->
                val due = if (tab == "CUSTOMER") party.currentBalance.coerceAtLeast(0.0) else (-party.currentBalance).coerceAtLeast(0.0)
                if (type == "payment" && amount > due) {
                    Toast.makeText(context, "Payment cannot exceed the current due", Toast.LENGTH_LONG).show()
                } else viewModel.addLedgerTransaction(
                    customerId = if (tab == "CUSTOMER") party.id else null,
                    supplierId = if (tab == "SUPPLIER") party.id else null,
                    type = type, amount = amount, note = note.ifBlank { "$method ${type.replaceFirstChar(Char::uppercase)}" },
                    paymentMethod = method,
                    onResult = { ok, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show(); if (ok) entryParty = null }
                )
            }
        )
    }
}

@Composable
private fun LedgerMetric(label: String, value: Double, card: Color, border: Color, text: Color, muted: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = card), border = BorderStroke(1.dp, border)) {
        Column(Modifier.padding(10.dp)) { Text(label, color = muted, fontSize = 10.sp); Text("BDT ${ledgerMoney(value)}", color = text, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

@Composable
private fun LedgerTransactionRow(tx: LedgerTransactionEntity, text: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(tx.note ?: tx.type, color = text, fontSize = 12.sp); Text("${ledgerDisplay(tx.date)} • ${tx.paymentMethod}${tx.invoiceNo?.let { " • $it" }.orEmpty()}", color = muted, fontSize = 10.sp) }
        Text("${if (tx.type == "payment") "-" else "+"} BDT ${ledgerMoney(tx.amount)}", color = if (tx.type == "payment") Color(0xFF10B981) else Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun LedgerEntryDialog(
    party: LedgerPartyRow, partyType: String, initialType: String, onDismiss: () -> Unit,
    onSave: (String, Double, String, String) -> Unit
) {
    var type by remember(initialType) { mutableStateOf(initialType) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("${party.name} ledger entry") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("credit" to "Charge", "payment" to "Payment").forEach { (value,label) -> FilterChip(selected=type==value,onClick={type=value},label={Text(label)}) } }
            OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch=='.' } }, label = { Text("Amount (BDT)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Cash","MFS","Bank","Card").forEach { value -> FilterChip(selected=method==value,onClick={method=value},label={Text(value)}) } }
            OutlinedTextField(note, { note=it }, label={Text("Note / reference")}, maxLines=2)
            Text(if (partyType=="CUSTOMER") "Customer current balance: BDT ${ledgerMoney(party.currentBalance)}" else "Supplier current balance: BDT ${ledgerMoney(party.currentBalance)}", fontSize=11.sp)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=11.sp) }
        } },
        confirmButton = { Button(onClick = { val parsed=amount.toDoubleOrNull(); if(parsed==null||parsed<=0||!parsed.isFinite()) error="Enter a valid positive amount" else onSave(type,parsed,method,note.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick=onDismiss){Text("Cancel")} }
    )
}

private fun ledgerRangeCutoff(range: String): Long? {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
    return when (range) {
        "TODAY" -> calendar.apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
        "7_DAYS" -> calendar.apply { add(Calendar.DAY_OF_YEAR,-7) }.timeInMillis
        "MONTH" -> calendar.apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
        else -> null
    }
}

private fun shareLedgerReminder(context: android.content.Context, business: String, party: LedgerPartyRow, due: Double) {
    val message = "Dear ${party.name}, your outstanding balance with $business is BDT ${ledgerMoney(due)}. Please contact us if this does not match your records."
    val intent = Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,message); putExtra(Intent.EXTRA_SUBJECT,"Account balance reminder") }
    context.startActivity(Intent.createChooser(intent,"Share balance reminder"))
}

private fun ledgerMoney(value: Double) = String.format(Locale.US,"%,.2f",value)
private fun ledgerIso(value: Long) = SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(value))
private fun ledgerDisplay(value: Long) = SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(Date(value))
private fun ledgerCsv(value: String) = "\"${value.replace("\"","\"\"")}\""
