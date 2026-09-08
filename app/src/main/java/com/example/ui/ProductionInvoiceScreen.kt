package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PosSaleEntity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionInvoiceScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isDark by viewModel.isDarkMode.collectAsState()
    val profile by viewModel.activeProfile.collectAsState()
    val sales by viewModel.posSales.collectAsState()
    val requestedId by viewModel.selectedInvoiceSaleId.collectAsState()
    var selectedId by remember(requestedId, sales) {
        mutableStateOf(requestedId?.takeIf { id -> sales.any { it.id == id } } ?: sales.firstOrNull()?.id)
    }
    val sale = sales.firstOrNull { it.id == selectedId }
    val invoice = sale?.let {
        invoiceFromSale(it, profile.businessName, profile.email, profile.phone)
    }
    val bg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val paper = if (isDark) Color(0xFF1E293B) else Color.White
    val text = if (isDark) Color.White else Color(0xFF0F172A)
    val muted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && invoice != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { InvoiceDocumentManager.writePdf(it, invoice) }
                    ?: error("Unable to open selected file")
            }.onSuccess {
                Toast.makeText(context, "Invoice PDF exported", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "PDF export failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Official Invoice", fontWeight = FontWeight.Bold)
                        Text("Persisted POS sale", fontSize = 11.sp, color = muted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { invoice?.let { InvoiceDocumentManager.print(context, it) } }, enabled = invoice != null) {
                        Icon(Icons.Default.Print, "Print invoice")
                    }
                    IconButton(onClick = {
                        invoice?.let {
                            runCatching { InvoiceDocumentManager.share(context, it) }
                                .onFailure { error -> Toast.makeText(context, error.message ?: "Share failed", Toast.LENGTH_LONG).show() }
                        }
                    }, enabled = invoice != null) { Icon(Icons.Default.Share, "Share invoice PDF") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = paper, titleContentColor = text)
            )
        }
    ) { padding ->
        if (sale == null || invoice == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No persisted POS invoice found", color = text, fontWeight = FontWeight.Bold)
                    Text("Complete a POS checkout first.", color = muted, fontSize = 12.sp)
                }
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (sales.size > 1) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = sale.invoiceNo,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Invoice") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sales.take(100).forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.invoiceNo} • BDT ${fmtMoney(option.netTotal)}") },
                                onClick = { selectedId = option.id; expanded = false }
                            )
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = paper),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(invoice.businessName, color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(invoice.businessEmail, color = muted, fontSize = 11.sp)
                            Text(invoice.businessPhone, color = muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(invoice.invoiceNumber, color = text, fontWeight = FontWeight.Bold)
                            Text(displayInvoiceDate(invoice.issuedAt), color = muted, fontSize = 10.sp)
                            Text(invoice.paymentStatus, color = if (invoice.paymentStatus == "PAID") Color(0xFF10B981) else Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider()
                    Text("BILLED TO", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(invoice.customerName, color = text, fontWeight = FontWeight.Bold)
                    if (invoice.customerPhone.isNotBlank()) Text(invoice.customerPhone, color = muted, fontSize = 12.sp)
                    Text("Payment method: ${invoice.paymentMethod}", color = muted, fontSize = 12.sp)
                    HorizontalDivider()
                    invoice.lines.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(line.description, color = text, fontWeight = FontWeight.Medium)
                                Text("${formatQty(line.quantity)} × BDT ${fmtMoney(line.unitPrice)}", color = muted, fontSize = 11.sp)
                            }
                            Text("BDT ${fmtMoney(line.lineTotal)}", color = text, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider()
                    InvoiceTotalRow("Subtotal", invoice.subtotal, text, muted)
                    InvoiceTotalRow("Discount", -invoice.discount, text, muted)
                    InvoiceTotalRow("Grand total", invoice.grandTotal, Color(0xFFF5C518), text, bold = true)
                    if (!sale.isSynced) {
                        Text("Cloud sync pending; this invoice is safely stored on this device.", color = Color(0xFFF59E0B), fontSize = 11.sp)
                    }
                }
            }
            Button(
                onClick = { exportLauncher.launch("Invoice-${safeInvoiceFileName(invoice.invoiceNumber)}.pdf") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5C518), contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Export PDF invoice", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InvoiceTotalRow(
    label: String,
    amount: Double,
    amountColor: Color,
    labelColor: Color,
    bold: Boolean = false
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = labelColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            "BDT ${fmtMoney(amount)}",
            color = amountColor,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}

private fun invoiceFromSale(
    sale: PosSaleEntity,
    businessName: String,
    email: String,
    phone: String
): InvoiceDocument {
    val parsedLines = runCatching {
        val array = JSONArray(sale.cartItemsJson)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val name = item.optString("name", "Product")
            val variant = item.optString("variant")
            InvoiceLine(
                description = if (variant.isBlank()) name else "$name — $variant",
                quantity = item.optDouble("quantity", 1.0),
                unitPrice = item.optDouble("unit_price", 0.0),
                lineTotal = item.optDouble("line_total", 0.0)
            )
        }
    }.getOrDefault(emptyList())
    return InvoiceDocument(
        invoiceNumber = sale.invoiceNo,
        issuedAt = sale.timestamp,
        businessName = businessName,
        businessEmail = email,
        businessPhone = phone,
        customerName = sale.customerName,
        customerPhone = sale.customerPhone,
        paymentMethod = sale.paymentMethod,
        paymentStatus = sale.paymentStatus,
        lines = parsedLines,
        subtotal = sale.subtotal,
        discount = sale.discount,
        grandTotal = sale.netTotal
    )
}

private fun displayInvoiceDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))

private fun fmtMoney(value: Double): String = String.format(Locale.US, "%,.2f", value)

private fun formatQty(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

private fun safeInvoiceFileName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "invoice" }
