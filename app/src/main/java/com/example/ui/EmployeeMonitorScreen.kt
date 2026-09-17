@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Merchant's Dedicated Live Staff Monitor Screen
 * Allows real-time monitoring of all sales submitted by employees,
 * 1-tap Cash finalization, live MFS matching audit, and staff shift metrics.
 */
@Composable
fun EmployeeMonitorScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isDark by viewModel.isDarkMode.collectAsState()
    val staffState by viewModel.staffMonitorState.collectAsState()
    val isLoading by viewModel.isEmployeeLoading.collectAsState()

    var activeTab by remember { mutableStateOf("PENDING_CASH") } // "PENDING_CASH", "LIVE_FEED", "STAFF_STATS"
    var selectedOrderForDetail by remember { mutableStateOf<EmployeeSaleOrder?>(null) }
    var actionInProgressId by remember { mutableStateOf<String?>(null) }
    var staffToRevoke by remember { mutableStateOf<StaffLeaderboardItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchMerchantStaffMonitor()
    }

    // Colors
    val screenBg = if (isDark) Color(0xFF0C0F17) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF161B26) else Color.White
    val cardBorder = if (isDark) Color(0xFF273042) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val brandGreen = Color(0xFF10B981)
    val warningOrange = Color(0xFFF59E0B)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "স্টাফ লাইভ মনিটর",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = primaryText
                        )
                        Text(
                            text = "কর্মচারী বিক্রয়, ক্যাশ অনুমোদন ও রিয়েলটাইম কার্যকলাপ",
                            fontSize = 12.sp,
                            color = secondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryText)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.fetchMerchantStaffMonitor(
                            onSuccess = { Toast.makeText(context, "মনিটর আপডেট সম্পন্ন", Toast.LENGTH_SHORT).show() },
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                        )
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = primaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg)
            )
        },
        containerColor = screenBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // ── 1. KPI SUMMARY CARDS ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Staff Sales
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(brandGreen.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.PointOfSale, contentDescription = null, tint = brandGreen, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("আজকের স্টাফ সেল", fontSize = 11.sp, color = secondaryText, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "৳ ${String.format("%,.0f", staffState.totalStaffSalesToday)}",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                        }
                    }

                    // Pending Cash to Finalize
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (staffState.pendingCashSalesCount > 0) {
                                if (isDark) Color(0xFF38250A) else Color(0xFFFEF3C7)
                            } else cardBg
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (staffState.pendingCashSalesCount > 0) warningOrange.copy(alpha = 0.5f) else cardBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(warningOrange.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.Payments, contentDescription = null, tint = warningOrange, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ক্যাশ অনুমোদন বাকি", fontSize = 11.sp, color = if (staffState.pendingCashSalesCount > 0) warningOrange else secondaryText, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "৳ ${String.format("%,.0f", staffState.pendingCashTotalAmount)} (${staffState.pendingCashSalesCount})",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (staffState.pendingCashSalesCount > 0) warningOrange else primaryText
                            )
                        }
                    }
                }
            }

            // ── 2. TAB SELECTOR CHIPS ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeTab == "PENDING_CASH",
                        onClick = { activeTab = "PENDING_CASH" },
                        label = {
                            Text(
                                text = "ক্যাশ অনুমোদন (${staffState.pendingCashSalesCount})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = warningOrange,
                            selectedLabelColor = Color.Black
                        )
                    )

                    FilterChip(
                        selected = activeTab == "LIVE_FEED",
                        onClick = { activeTab = "LIVE_FEED" },
                        label = {
                            Text(
                                text = "লাইভ সেলস স্ট্রিম",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandGreen,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = activeTab == "STAFF_STATS",
                        onClick = { activeTab = "STAFF_STATS" },
                        label = {
                            Text(
                                text = "স্টাফ পারফরম্যান্স (${staffState.staffLeaderboard.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6366F1),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // ── 3. CONTENT PER ACTIVE TAB ──

            // TAB A: PENDING CASH APPROVALS
            if (activeTab == "PENDING_CASH") {
                if (staffState.pendingCashSales.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(brandGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = brandGreen, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "সব ক্যাশ পেমেন্ট অনুমোদিত!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "কর্মচারীদের গ্রহণ করা কোনো ক্যাশ পেমেন্ট পেন্ডিং নেই। নতুন বিক্রয় হলে এখানে স্বয়ংক্রিয়ভাবে ভেসে উঠবে।",
                                    fontSize = 12.sp,
                                    color = secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(staffState.pendingCashSales) { sale ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E1B13) else Color(0xFFFFFBEB)
                            ),
                            border = BorderStroke(1.dp, warningOrange.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Header: Invoice & Staff Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Receipt, contentDescription = null, tint = warningOrange, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(sale.invoiceNo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(warningOrange.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "বিক্রেতা: ${sale.employeeName}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = warningOrange
                                        )
                                    }
                                }

                                HorizontalDivider(color = cardBorder.copy(alpha = 0.5f))

                                // Customer & Items Info
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "গ্রাহক: ${sale.customerName}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryText
                                        )
                                        if (sale.customerPhone.isNotBlank()) {
                                            Text(sale.customerPhone, fontSize = 11.sp, color = secondaryText)
                                        }
                                        Text(
                                            text = "আইটেম: ${sale.itemCount} টি (${sale.items.joinToString { it.name }})",
                                            fontSize = 11.sp,
                                            color = secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("ক্যাশ গ্রহণ", fontSize = 11.sp, color = warningOrange, fontWeight = FontWeight.Bold)
                                        Text(
                                            "৳ ${String.format("%,.2f", sale.netTotal)}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = primaryText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Action Button: 1-Tap Cash Finalization
                                Button(
                                    onClick = {
                                        actionInProgressId = sale.id
                                        viewModel.finalizeMerchantStaffCashSale(
                                            saleId = sale.id,
                                            onSuccess = { finalized ->
                                                actionInProgressId = null
                                                Toast.makeText(context, "✓ ক্যাশ ৳${finalized.netTotal} সফলভাবে গৃহীত ও অনুমোদিত হয়েছে!", Toast.LENGTH_LONG).show()
                                            },
                                            onError = { err ->
                                                actionInProgressId = null
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = brandGreen),
                                    enabled = actionInProgressId != sale.id
                                ) {
                                    if (actionInProgressId == sale.id) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ক্যাশ গ্রহণ নিশ্চিত ও বিক্রয় অনুমোদন করুন",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB B: LIVE SALES STREAM
            if (activeTab == "LIVE_FEED") {
                if (staffState.liveSalesStream.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Text(
                                text = "এখনো কোনো বিক্রয় রেকর্ড নেই।",
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = secondaryText
                            )
                        }
                    }
                } else {
                    items(staffState.liveSalesStream) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOrderForDetail = item },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (item.paymentType) {
                                                    "bKash" -> Color(0xFFE11D48).copy(alpha = 0.15f)
                                                    "Nagad" -> Color(0xFFEA580C).copy(alpha = 0.15f)
                                                    "Rocket" -> Color(0xFF7C3AED).copy(alpha = 0.15f)
                                                    else -> brandGreen.copy(alpha = 0.15f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (item.paymentType) {
                                                "Cash" -> Icons.Outlined.Payments
                                                else -> Icons.Outlined.AccountBalanceWallet
                                            },
                                            contentDescription = null,
                                            tint = when (item.paymentType) {
                                                "bKash" -> Color(0xFFE11D48)
                                                "Nagad" -> Color(0xFFEA580C)
                                                "Rocket" -> Color(0xFF7C3AED)
                                                else -> brandGreen
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(item.invoiceNo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                        Text("${item.employeeName} • ${item.customerName}", fontSize = 11.sp, color = secondaryText)
                                        Text(item.createdAt.ifBlank { "এখনই" }, fontSize = 10.sp, color = secondaryText)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "৳ ${String.format("%,.2f", item.netTotal)}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (item.status) {
                                                    "PAID" -> brandGreen.copy(alpha = 0.18f)
                                                    "PENDING_CASH_CONFIRMATION" -> warningOrange.copy(alpha = 0.18f)
                                                    else -> Color(0xFF3B82F6).copy(alpha = 0.18f)
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when (item.status) {
                                                "PAID" -> "✓ পেইড"
                                                "PENDING_CASH_CONFIRMATION" -> "ক্যাশ অনুমোদন বাকি"
                                                else -> "MFS মেলানো হচ্ছে"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (item.status) {
                                                "PAID" -> brandGreen
                                                "PENDING_CASH_CONFIRMATION" -> warningOrange
                                                else -> Color(0xFF3B82F6)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB C: STAFF PERFORMANCE LEADERBOARD
            if (activeTab == "STAFF_STATS") {
                if (staffState.staffLeaderboard.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Text(
                                text = "কোনো কর্মচারীর ডেটা পাওয়া যায়নি।",
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = secondaryText
                            )
                        }
                    }
                } else {
                    items(staffState.staffLeaderboard) { staff ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF6366F1))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(staff.employeeName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryText)
                                            Text("মোট বিক্রয়: ${staff.totalSalesCount} টি ইনভয়েস", fontSize = 11.5.sp, color = secondaryText)
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("মোট সংগ্রহ", fontSize = 10.sp, color = secondaryText)
                                        Text(
                                            "৳ ${String.format("%,.0f", staff.totalSalesAmount)}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = brandGreen
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDark) Color(0xFF0F131D) else Color(0xFFF1F5F9))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ক্যাশ সংগ্রহ", fontSize = 10.sp, color = secondaryText)
                                        Text("৳ ${String.format("%,.0f", staff.cashCollected)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = primaryText)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("MFS সংগ্রহ", fontSize = 10.sp, color = secondaryText)
                                        Text("৳ ${String.format("%,.0f", staff.mfsCollected)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = primaryText)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("পেন্ডিং ক্যাশ", fontSize = 10.sp, color = secondaryText)
                                        Text("${staff.pendingCashCount} টি", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (staff.pendingCashCount > 0) warningOrange else primaryText)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { staffToRevoke = staff },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🚨 অ্যাক্সেস বাতিল করুন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Detail Breakdown of an Order
    selectedOrderForDetail?.let { ord ->
        AlertDialog(
            onDismissRequest = { selectedOrderForDetail = null },
            title = {
                Text(
                    text = "ইনভয়েস বিবরণ: ${ord.invoiceNo}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("বিক্রেতা: ${ord.employeeName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("গ্রাহক: ${ord.customerName} (${ord.customerPhone.ifBlank { "নম্বর নেই" }})", fontSize = 12.sp)
                    Text("পেমেন্ট পদ্ধতি: ${ord.paymentType}", fontSize = 12.sp)
                    if (ord.trxId != null) {
                        Text("TrxID: ${ord.trxId}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = brandGreen)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("পণ্য তালিকা:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    ord.items.forEach { it ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${it.name} x ${it.quantity.toInt()}", fontSize = 12.sp)
                            Text("৳ ${String.format("%,.2f", it.lineTotal)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট পরিশোধযোগ্য:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("৳ ${String.format("%,.2f", ord.netTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = brandGreen)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedOrderForDetail = null }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }

    // Modal: Emergency Revoke Access Confirmation Dialog
    staffToRevoke?.let { target ->
        AlertDialog(
            onDismissRequest = { staffToRevoke = null },
            title = {
                Text("🚨 অ্যাক্সেস বাতিল ও টার্মিনেট করবেন?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFEF4444))
            },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিত যে ${target.employeeName}-এর সমস্ত অ্যাক্সেস বন্ধ করতে চান? এর ফলে তার ফোনে থাকা অ্যাক্টিভ সেলস সেশন ও টোকেন তাৎক্ষণিকভাবে কিল (kill) করা হবে এবং সে আর কোনো বিক্রয় বা ইনভেন্টরি অ্যাক্সেস করতে পারবে না।",
                    fontSize = 13.sp,
                    color = primaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val empId = target.employeeId
                        val empName = target.employeeName
                        staffToRevoke = null
                        viewModel.revokeEmployeeAccess(empId, "লাইভ মনিটর থেকে মার্চেন্ট কর্তৃক বরখাস্ত")
                        Toast.makeText(context, "$empName-এর অ্যাক্সেস তাৎক্ষণিক বন্ধ করা হয়েছে!", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("হ্যাঁ, অবিলম্বে বন্ধ করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { staffToRevoke = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

