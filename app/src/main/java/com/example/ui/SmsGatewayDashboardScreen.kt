package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.OutboxSmsEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsGatewayDashboardScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val isBangla = language == "Bangla"

    val outboxList by viewModel.outboxSmsList.collectAsState()
    val dueCustomers by viewModel.customersWithDue.collectAsState()
    val availableSims by viewModel.availableSimCards.collectAsState()
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsState()
    val isGatewayActive by viewModel.isGatewayActive.collectAsState()
    val apiKey by viewModel.gatewayApiKey.collectAsState()
    val throttleDelay by viewModel.smsThrottleDelayMs.collectAsState()
    val autoPosReceipt by viewModel.autoPosReceiptSmsEnabled.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = if (isBangla) {
        listOf("বাকি ও ক্যাম্পেইন", "সিম ও সেটিংস", "ওয়েবসাইট এপিআই", "আউটবক্স হিস্টোরি")
    } else {
        listOf("Dues & Campaigns", "SIM & Settings", "Website API", "Outbox History")
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSimCards()
    }

    // Theme definitions
    val scaffoldBg = if (isDarkMode) Color(0xFF0B0F19) else Color(0xFFF8FAFC)
    val cardBg = if (isDarkMode) Color(0xFF131B2E) else Color.White
    val cardSurface = if (isDarkMode) Color(0xFF1B2438) else Color(0xFFF1F5F9)
    val primaryText = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val borderCol = if (isDarkMode) Color(0xFF26334D) else Color(0xFFE2E8F0)
    val accentBrand = Color(0xFF2563EB)
    val successCol = Color(0xFF10B981)
    val warningCol = Color(0xFFF59E0B)
    val errorCol = Color(0xFFEF4444)

    Scaffold(
        containerColor = scaffoldBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isBangla) "এসএমএস গেটওয়ে ও অটো ক্যাম্পেইন" else "SMS Gateway & Auto Campaign",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBangla) "নিজস্ব সিম কার্ড দিয়ে স্বয়ংক্রিয় এসএমএস প্রেরণ" else "Automated SMS Dispatch via Dedicated SIM",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryText)
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isGatewayActive) successCol.copy(alpha = 0.15f) else errorCol.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isGatewayActive) successCol else errorCol),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isGatewayActive) successCol else errorCol)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isGatewayActive) {
                                    if (isBangla) "সিম গেটওয়ে সক্রিয়" else "SIM Gateway Active"
                                } else {
                                    if (isBangla) "গেটওয়ে বন্ধ" else "Gateway Inactive"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGatewayActive) successCol else errorCol
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scrollable Tab Selector with no collapsed text
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = cardBg,
                contentColor = accentBrand,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = borderCol) }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index) accentBrand else secondaryText,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> DueAndCampaignTab(viewModel, cardBg, cardSurface, borderCol, primaryText, secondaryText, accentBrand, successCol, isBangla, isDarkMode)
                1 -> SimSettingsTab(viewModel, cardBg, cardSurface, borderCol, primaryText, secondaryText, accentBrand, successCol, isBangla, isDarkMode)
                2 -> ExternalApiTab(viewModel, cardBg, cardSurface, borderCol, primaryText, secondaryText, accentBrand, successCol, isBangla, isDarkMode)
                3 -> OutboxLogsTab(viewModel, outboxList, cardBg, cardSurface, borderCol, primaryText, secondaryText, successCol, warningCol, errorCol, isBangla, isDarkMode)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1: Due & Marketing Campaigns
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DueAndCampaignTab(
    viewModel: AppViewModel,
    cardBg: Color,
    cardSurface: Color,
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    accentBrand: Color,
    successCol: Color,
    isBangla: Boolean,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val dueCustomers by viewModel.customersWithDue.collectAsState()
    val initialDueTemplate = if (isBangla) {
        "প্রিয় {name}, {store}-এ আপনার বাকি {due} টাকা পরিশোধের অনুরোধ জানাচ্ছি। ধন্যবাদ।"
    } else {
        "Dear {name}, please clear your outstanding balance of BDT {due} at {store}. Thank you."
    }
    var dueTemplate by remember(isBangla) { mutableStateOf(initialDueTemplate) }
    var isSendingDue by remember { mutableStateOf(false) }

    var marketingAudience by remember { mutableStateOf("ALL") }
    val initialMarketingTemplate = if (isBangla) {
        "সম্মানিত গ্রাহক {name}, {store}-এ নতুন অফার ও বিশেষ ডিসকাউন্ট চলছে! আজই কেনাকাটা করুন।"
    } else {
        "Dear {name}, exclusive offers and discounts are waiting for you at {store}! Visit us today."
    }
    var marketingTemplate by remember(isBangla) { mutableStateOf(initialMarketingTemplate) }
    var isSendingMarketing by remember { mutableStateOf(false) }

    var customPhonesInput by remember { mutableStateOf("") }
    var customMessageInput by remember { mutableStateOf("") }
    var isSendingCustom by remember { mutableStateOf(false) }

    val totalDueAmount = remember(dueCustomers) { dueCustomers.sumOf { it.currentBalance } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── 1. Due Reminder Card ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.MonetizationOn, null, tint = Color(0xFFEAB308), modifier = Modifier.size(22.dp))
                            Text(
                                text = if (isBangla) "বাকি তাগাদা এসএমএস" else "Due Collection SMS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = primaryText
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDarkMode) Color(0xFF382A0B) else Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF785E15) else Color(0xFFFDE68A))
                        ) {
                            Text(
                                text = if (isBangla) "${dueCustomers.size} জন বাকিদার" else "${dueCustomers.size} With Due",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color(0xFFFACC15) else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Total due box with Dark Mode safe background
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkMode) Color(0xFF2B1717) else Color(0xFFFEF2F2))
                            .border(1.dp, if (isDarkMode) Color(0xFF522121) else Color(0xFFFECACA), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isBangla) "মোট বকেয়া টাকার পরিমাণ" else "Total Outstanding Due",
                                fontSize = 11.5.sp,
                                color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "৳ ${String.format(Locale.US, "%,.2f", totalDueAmount)}",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkMode) Color(0xFFF87171) else Color(0xFFDC2626)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isBangla) "উপলব্ধ ট্যাগসমূহ:" else "Available Tags:",
                                fontSize = 10.5.sp,
                                color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("{name}", "{due}", "{store}").forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isDarkMode) Color(0xFF3D1E1E) else Color(0xFFFEE2E2),
                                        modifier = Modifier.clickable {
                                            if (!dueTemplate.contains(tag)) {
                                                dueTemplate = "$dueTemplate $tag"
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val isScheduleActive by viewModel.isAutoDueScheduleActive.collectAsState()
                    val scheduleHour by viewModel.autoDueScheduleHour.collectAsState()
                    if (isScheduleActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDarkMode) Color(0xFF142B1F) else Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E4C33) else Color(0xFFA7F3D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Alarm, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isBangla)
                                        "দৈনিক অটো শিডিউল সক্রিয়: প্রতিদিন $scheduleHour:00 টায় তাগাদা যাবে"
                                    else
                                        "Daily Auto Schedule Active: Dispatch daily at $scheduleHour:00",
                                    fontSize = 11.5.sp,
                                    color = if (isDarkMode) Color(0xFF6EE7B7) else Color(0xFF047857),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = dueTemplate,
                        onValueChange = { dueTemplate = it },
                        label = { Text(if (isBangla) "এসএমএস বার্তা টেমপ্লেট" else "SMS Message Template") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBrand,
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    // Character count & Part helper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBangla) "অক্ষর: ${dueTemplate.length}" else "Characters: ${dueTemplate.length}",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                        val parts = if (dueTemplate.any { it.code > 127 }) (dueTemplate.length / 70) + 1 else (dueTemplate.length / 160) + 1
                        Text(
                            text = if (isBangla) "এসএমএস পার্ট: $parts টি" else "SMS Parts: $parts",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBrand
                        )
                    }

                    Button(
                        onClick = {
                            if (dueCustomers.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    if (isBangla) "কোনো বাকিদার গ্রাহক নেই" else "No customers with outstanding due found",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            isSendingDue = true
                            viewModel.sendDueReminderSms(customTemplate = dueTemplate) { count, msg ->
                                isSendingDue = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isSendingDue && dueCustomers.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentBrand),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        if (isSendingDue) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isBangla)
                                    "সকল বাকিদারকে একসাথে এসএমএস পাঠান (${dueCustomers.size} জন)"
                                else
                                    "Send Due SMS to All Customers (${dueCustomers.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Marketing Campaign Composer ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Campaign, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(22.dp))
                        Text(
                            text = if (isBangla) "মার্কেটিং ব্রডকাস্ট ক্যাম্পেইন" else "Marketing Broadcast Campaign",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = primaryText
                        )
                    }

                    // Target Audience Selection
                    val audienceOptions = if (isBangla) {
                        listOf("ALL" to "সকল গ্রাহক", "DUE" to "বাকিদার", "ZERO_DUE" to "বাকিহীন")
                    } else {
                        listOf("ALL" to "All Customers", "DUE" to "Due Only", "ZERO_DUE" to "Zero Due")
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        audienceOptions.forEach { (code, label) ->
                            val isSelected = marketingAudience == code
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF8B5CF6) else cardSurface,
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF8B5CF6) else borderCol),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { marketingAudience = code }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else primaryText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = marketingTemplate,
                        onValueChange = { marketingTemplate = it },
                        label = { Text(if (isBangla) "মার্কেটিং বার্তা" else "Marketing Message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    // Character count & Part helper
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (isBangla) "অক্ষর: ${marketingTemplate.length}" else "Characters: ${marketingTemplate.length}",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                        val parts = if (marketingTemplate.any { it.code > 127 }) (marketingTemplate.length / 70) + 1 else (marketingTemplate.length / 160) + 1
                        Text(
                            text = if (isBangla) "এসএমএস পার্ট: $parts টি" else "SMS Parts: $parts",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                    }

                    Button(
                        onClick = {
                            isSendingMarketing = true
                            viewModel.sendMarketingCampaign(marketingAudience, marketingTemplate) { count, msg ->
                                isSendingMarketing = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isSendingMarketing && marketingTemplate.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        if (isSendingMarketing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isBangla)
                                    "ক্যাম্পেইন শুরু করুন (Broadcast via SIM)"
                                else
                                    "Launch Campaign (Broadcast via SIM)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }

        // ── 3. Quick Custom SMS ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Chat, null, tint = successCol, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (isBangla) "যেকোনো নম্বরে সরাসরি এসএমএস পাঠান" else "Direct SMS Dispatch",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = primaryText
                        )
                    }

                    OutlinedTextField(
                        value = customPhonesInput,
                        onValueChange = { customPhonesInput = it },
                        label = { Text(if (isBangla) "মোবাইল নম্বর" else "Recipient Mobile Number(s)") },
                        placeholder = { Text("017XXXXXXXX, 018XXXXXXXX") },
                        supportingText = {
                            Text(
                                text = if (isBangla)
                                    "একাধিক নম্বর হলে কমা (,) বা নতুন লাইনে লিখুন"
                                else
                                    "Separate multiple numbers with commas or newlines",
                                fontSize = 11.sp,
                                color = secondaryText
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBrand,
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    OutlinedTextField(
                        value = customMessageInput,
                        onValueChange = { customMessageInput = it },
                        label = { Text(if (isBangla) "বার্তা (Message)" else "Message Body") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBrand,
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        )
                    )

                    Button(
                        onClick = {
                            val numbers = customPhonesInput.split(",", "\n").map { it.trim() }.filter { it.length >= 10 }
                            if (numbers.isEmpty() || customMessageInput.isBlank()) {
                                Toast.makeText(
                                    context,
                                    if (isBangla) "নম্বর এবং মেসেজ পূরণ করুন" else "Please enter valid phone number and message",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            isSendingCustom = true
                            viewModel.sendQuickCustomSms(numbers, customMessageInput) { success, msg ->
                                isSendingCustom = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    customPhonesInput = ""
                                    customMessageInput = ""
                                }
                            }
                        },
                        enabled = !isSendingCustom,
                        colors = ButtonDefaults.buttonColors(containerColor = successCol),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text(
                            text = if (isBangla) "এসএমএস কিউতে যোগ করুন" else "Queue SMS for Dispatch",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 2: SIM & Dispatcher Settings
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SimSettingsTab(
    viewModel: AppViewModel,
    cardBg: Color,
    cardSurface: Color,
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    accentBrand: Color,
    successCol: Color,
    isBangla: Boolean,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val availableSims by viewModel.availableSimCards.collectAsState()
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsState()
    val isGatewayActive by viewModel.isGatewayActive.collectAsState()
    val autoPosReceipt by viewModel.autoPosReceiptSmsEnabled.collectAsState()
    var throttleDelaySlider by remember { mutableFloatStateOf(2.5f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // SIM Card Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SimCard, null, tint = accentBrand)
                            Text(
                                text = if (isBangla) "এসএমএস প্রেরক সিম কার্ড নির্বাচন" else "Select SMS Sender SIM Card",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = primaryText
                            )
                        }
                        IconButton(onClick = { viewModel.refreshSimCards() }) {
                            Icon(Icons.Default.Refresh, "Refresh SIMs", tint = accentBrand)
                        }
                    }

                    if (availableSims.isEmpty()) {
                        Text(
                            text = if (isBangla)
                                "সিম কার্ড তথ্য লোড হচ্ছে বা কোনো সক্রিয় সিম সনাক্ত হয়নি।"
                            else
                                "Scanning SIM cards or no active carrier SIM detected.",
                            fontSize = 12.sp,
                            color = secondaryText
                        )
                    } else {
                        availableSims.forEach { sim ->
                            val isSelected = selectedSimSlot == sim.slotIndex
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) accentBrand.copy(alpha = 0.1f) else cardSurface,
                                border = BorderStroke(1.5.dp, if (isSelected) accentBrand else borderCol),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setSelectedSimSlot(sim.slotIndex) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.SimCard,
                                            null,
                                            tint = if (isSelected) accentBrand else secondaryText,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(sim.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryText)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (isBangla)
                                                    "অপারেটর: ${sim.carrierName} | স্লট: ${sim.slotIndex + 1}"
                                                else
                                                    "Carrier: ${sim.carrierName} | Slot: ${sim.slotIndex + 1}",
                                                fontSize = 11.5.sp,
                                                color = secondaryText
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setSelectedSimSlot(sim.slotIndex) },
                                        colors = RadioButtonDefaults.colors(selectedColor = accentBrand)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Anti-Spam Carrier Throttling Delay Slider
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isBangla) "অ্যান্টি-স্প্যাম সিম সুরক্ষা ডিলে (Throttle Delay)" else "Anti-Spam Carrier Throttle Delay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = primaryText
                    )
                    Text(
                        text = if (isBangla)
                            "মোবাইল অপারেটর (GP, Robi, BL) যেন বাল্ক এসএমএস-এর কারণে সিম ব্লক না করে, সেজন্য প্রতি এসএমএস-এর মাঝে সেফ বিরতি দেওয়া হয়।"
                        else
                            "Introduces safe intervals between consecutive messages to protect the SIM against carrier anti-spam filters.",
                        fontSize = 12.sp,
                        color = secondaryText,
                        lineHeight = 16.sp
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isBangla) "বর্তমান ডিলে:" else "Current Interval:", fontSize = 12.sp, color = primaryText)
                        Text(
                            text = if (isBangla)
                                "${String.format(Locale.US, "%.1f", throttleDelaySlider)} সেকেন্ড"
                            else
                                "${String.format(Locale.US, "%.1f", throttleDelaySlider)} Seconds",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accentBrand
                        )
                    }

                    Slider(
                        value = throttleDelaySlider,
                        onValueChange = {
                            throttleDelaySlider = it
                            viewModel.setSmsThrottleDelay((it * 1000).toLong())
                        },
                        valueRange = 1.0f..5.0f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = accentBrand, activeTrackColor = accentBrand)
                    )
                }
            }
        }

        // Auto POS Receipt SMS Switch
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBangla) "বিক্রয় রশিদ অটোমেটিক এসএমএস" else "Automated POS Sales Receipt SMS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = primaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBangla)
                                "POS সেল বা চেকআউট সম্পন্ন হলে কাস্টমারের নম্বরে ডিজিটাল রশিদের এসএমএস চলে যাবে।"
                            else
                                "Instantly dispatches a digital invoice SMS to the customer upon POS checkout.",
                            fontSize = 11.5.sp,
                            color = secondaryText,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoPosReceipt,
                        onCheckedChange = { viewModel.setAutoPosReceiptEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentBrand, checkedTrackColor = accentBrand.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // USSD Balance & Pack Quick Check with structured responsive cards
        item {
            val currentSim = availableSims.find { it.slotIndex == selectedSimSlot } ?: availableSims.firstOrNull()
            val carrierName = currentSim?.carrierName ?: "Grameenphone"
            val ussdList = remember(carrierName) { com.example.service.SmsGatewayEngine.getUssdCodesForCarrier(carrierName) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PhoneInTalk, null, tint = accentBrand)
                        Text(
                            text = if (isBangla)
                                "সিম ব্যালেন্স ও প্যাক অনুসন্ধান ($carrierName)"
                            else
                                "Check SIM Balance & Packs ($carrierName)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = primaryText
                        )
                    }
                    Text(
                        text = if (isBangla)
                            "সিম কার্ডে পর্যাপ্ত ব্যালেন্স বা এসএমএস প্যাক আছে কিনা তা ১-ট্যাপে সরাসরি ডায়াল করে চেক করুন:"
                        else
                            "Check available carrier airtime and SMS packs via one-tap USSD execution:",
                        fontSize = 11.5.sp,
                        color = secondaryText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ussdList.take(3).forEach { (label, code) ->
                            OutlinedButton(
                                onClick = { com.example.service.SmsGatewayEngine.dialUssd(context, code) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(code, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentBrand)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automated Daily Due Reminder Schedule Card
        item {
            val isScheduleActive by viewModel.isAutoDueScheduleActive.collectAsState()
            val scheduleHour by viewModel.autoDueScheduleHour.collectAsState()
            val minAmount by viewModel.autoDueMinAmount.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Alarm, null, tint = accentBrand)
                                Text(
                                    text = if (isBangla) "দৈনিক অটোমেটিক বাকি তাগাদা শিডিউল" else "Automated Daily Due Reminder Schedule",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = primaryText
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBangla)
                                    "প্রতিদিন নির্ধারিত সময়ে ব্যাকগ্রাউন্ডে স্বয়ংক্রিয়ভাবে বাকিদারদের সিম থেকে তাগাদা এসএমএস পাঠাবে।"
                                else
                                    "Automatically sends reminder SMS from your SIM to due customers daily in background.",
                                fontSize = 11.5.sp,
                                color = secondaryText,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isScheduleActive,
                            onCheckedChange = { viewModel.setAutoDueScheduleConfig(it, scheduleHour, minAmount) },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentBrand, checkedTrackColor = accentBrand.copy(alpha = 0.5f))
                        )
                    }

                    if (isScheduleActive) {
                        HorizontalDivider(color = borderCol)
                        Text(
                            text = if (isBangla) "এসএমএস পাঠানোর সময় নির্বাচন করুন:" else "Select Dispatch Time:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText
                        )
                        val hourOptions = if (isBangla) {
                            listOf(9 to "সকাল ৯:০০", 10 to "সকাল ১০:০০", 11 to "সকাল ১১:০০", 16 to "বিকাল ৪:০০", 20 to "রাত ৮:০০")
                        } else {
                            listOf(9 to "9:00 AM", 10 to "10:00 AM", 11 to "11:00 AM", 16 to "4:00 PM", 20 to "8:00 PM")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            hourOptions.forEach { (hour, label) ->
                                val selected = scheduleHour == hour
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setAutoDueScheduleConfig(true, hour, minAmount) },
                                    label = { Text(label, fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentBrand.copy(alpha = 0.15f),
                                        selectedLabelColor = accentBrand
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBangla) "সর্বনিম্ন বাকি সীমা:" else "Minimum Due Threshold:",
                                fontSize = 12.sp,
                                color = primaryText
                            )
                            Text("৳${minAmount.toInt()}+", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentBrand)
                        }
                        val amountOptions = listOf(50.0, 100.0, 200.0, 500.0, 1000.0)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            amountOptions.forEach { amt ->
                                val selected = minAmount == amt
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setAutoDueScheduleConfig(true, scheduleHour, amt) },
                                    label = { Text("৳${amt.toInt()}", fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentBrand.copy(alpha = 0.15f),
                                        selectedLabelColor = accentBrand
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.runAutomatedDueReminderBatch { count, msg ->
                                    Toast.makeText(context, if (isBangla) "অটো তাগাদা: $msg" else "Auto Due: $msg", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentBrand),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.ScheduleSend, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isBangla) "এখনই শিডিউল টেস্ট রান করুন" else "Run Schedule Test Now",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 3: External Website API & OTP Gateway
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ExternalApiTab(
    viewModel: AppViewModel,
    cardBg: Color,
    cardSurface: Color,
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    accentBrand: Color,
    successCol: Color,
    isBangla: Boolean,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val apiKey by viewModel.gatewayApiKey.collectAsState()
    var selectedSnippetTab by remember { mutableIntStateOf(0) }
    val snippetTabs = listOf("cURL", "PHP / WordPress", "Node.js", "Python")

    var testOtpPhone by remember { mutableStateOf("") }
    var testOtpCode by remember { mutableStateOf("") }
    var testOtpStatusMsg by remember { mutableStateOf("") }
    var isSendingTestOtp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // API Key Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isBangla) "গেটওয়ে এপিআই কি (Merchant Gateway API Key)" else "Merchant Gateway API Key",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = primaryText
                    )
                    Text(
                        text = if (isBangla)
                            "যেকোনো ই-কমার্স ওয়েবসাইট (WooCommerce, Shopify, Laravel) বা সফটওয়্যারে এই কি ব্যবহার করে ওটিপি ও ট্রানজেকশনাল এসএমএস পাঠাতে পারবেন।"
                        else
                            "Use this secure API key to trigger customer OTP and transactional notifications from WooCommerce, Shopify, Laravel, or custom apps.",
                        fontSize = 11.5.sp,
                        color = secondaryText,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = apiKey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBrand,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("API Key", apiKey))
                                Toast.makeText(
                                    context,
                                    if (isBangla) "API Key কপি করা হয়েছে!" else "API Key copied to clipboard!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = primaryText, modifier = Modifier.size(16.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.generateNewGatewayApiKey()
                            Toast.makeText(
                                context,
                                if (isBangla) "নতুন API Key তৈরি হয়েছে" else "New API Key generated successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isBangla) "নতুন কি তৈরি করুন" else "Regenerate API Key",
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        // Live Test Sandbox
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isBangla) "টেস্ট ওটিপি প্রেরণ ও যাচাই স্যান্ডবক্স" else "Live OTP Dispatch & Verification Sandbox",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = primaryText
                    )
                    Text(
                        text = if (isBangla)
                            "ওয়েবসাইট ছাড়া সরাসরি এই অ্যাপ থেকেই ওটিপি জেনারেশন ও সিম প্রেরণের পূর্ণ চক্র টেস্ট করুন:"
                        else
                            "Test the complete OTP generation and SIM dispatch pipeline directly from this device:",
                        fontSize = 11.5.sp,
                        color = secondaryText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = testOtpPhone,
                            onValueChange = { testOtpPhone = it },
                            label = { Text(if (isBangla) "মোবাইল নম্বর" else "Mobile Number") },
                            placeholder = { Text("017XXXXXXXX") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBrand)
                        )

                        Button(
                            onClick = {
                                if (testOtpPhone.length < 10) {
                                    Toast.makeText(
                                        context,
                                        if (isBangla) "সঠিক নম্বর দিন" else "Please enter valid number",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                isSendingTestOtp = true
                                testOtpStatusMsg = if (isBangla) "ওটিপি জেনারেট হচ্ছে..." else "Generating OTP..."
                                val code = (100000..999999).random().toString()
                                testOtpCode = code
                                viewModel.sendQuickCustomSms(
                                    listOf(testOtpPhone),
                                    if (isBangla)
                                        "আপনার SwapnoPay যাচাইকরণ কোড হলো: $code (মেয়াদ ৫ মিনিট)।"
                                    else
                                        "Your SwapnoPay verification OTP is: $code (valid for 5 mins)."
                                ) { success, msg ->
                                    isSendingTestOtp = false
                                    testOtpStatusMsg = if (success) {
                                        if (isBangla) "✅ ওটিপি ($code) সিম কিউতে পাঠানো হয়েছে!" else "✅ OTP ($code) queued to SIM successfully!"
                                    } else "❌ $msg"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSendingTestOtp,
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text(if (isBangla) "ওটিপি পাঠান" else "Send OTP")
                        }
                    }

                    if (testOtpStatusMsg.isNotEmpty()) {
                        Text(testOtpStatusMsg, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = successCol)
                    }
                }
            }
        }

        // Code Integration Snippets
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isBangla) "ওয়েবসাইটে ইন্টিগ্রেশনের কোড (API Snippets)" else "Website Integration Code Snippets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = primaryText
                    )

                    TabRow(
                        selectedTabIndex = selectedSnippetTab,
                        containerColor = cardSurface,
                        contentColor = accentBrand
                    ) {
                        snippetTabs.forEachIndexed { idx, title ->
                            Tab(
                                selected = selectedSnippetTab == idx,
                                onClick = { selectedSnippetTab = idx },
                                text = { Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    val snippetCode = when (selectedSnippetTab) {
                        0 -> """curl -X POST "https://api.swapnopay.top/v1/sms-gateway/send-otp" \
  -H "X-API-Key: $apiKey" \
  -H "Content-Type: application/json" \
  -d '{"phone": "01712345678", "purpose": "Login"}'"""
                        1 -> """<?php
${'$'}curl = curl_init();
curl_setopt_array(${'$'}curl, [
  CURLOPT_URL => 'https://api.swapnopay.top/v1/sms-gateway/send-otp',
  CURLOPT_RETURNTRANSFER => true,
  CURLOPT_POST => true,
  CURLOPT_HTTPHEADER => [
    'X-API-Key: $apiKey',
    'Content-Type: application/json'
  ],
  CURLOPT_POSTFIELDS => json_encode([
    'phone' => '01712345678',
    'purpose' => 'Registration'
  ])
]);
${'$'}response = curl_exec(${'$'}curl);
curl_close(${'$'}curl);
?>"""
                        2 -> """await fetch('https://api.swapnopay.top/v1/sms-gateway/send-otp', {
  method: 'POST',
  headers: {
    'X-API-Key': '$apiKey',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    phone: '01712345678',
    purpose: 'Checkout OTP'
  })
});"""
                        else -> """import requests

res = requests.post(
  'https://api.swapnopay.top/v1/sms-gateway/send-otp',
  headers={'X-API-Key': '$apiKey'},
  json={'phone': '01712345678', 'purpose': 'Login'}
)
print(res.json())"""
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Endpoint: /v1/sms-gateway/send-otp", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp).clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", snippetCode))
                                        Toast.makeText(
                                            context,
                                            if (isBangla) "কোড কপি করা হয়েছে!" else "Code copied to clipboard!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(snippetCode, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 4: Outbox History Logs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OutboxLogsTab(
    viewModel: AppViewModel,
    outboxList: List<OutboxSmsEntity>,
    cardBg: Color,
    cardSurface: Color,
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    successCol: Color,
    warningCol: Color,
    errorCol: Color,
    isBangla: Boolean,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val queuedCount = outboxList.count { it.status == "QUEUED" || it.status == "SENDING" }
    val sentCount = outboxList.count { it.status == "SENT" || it.status == "DELIVERED" }
    val failedCount = outboxList.count { it.status == "FAILED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Summary row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val stats = if (isBangla) {
                    listOf(
                        Triple("অপেক্ষমান (Queue)", queuedCount.toString(), warningCol),
                        Triple("সফল (Sent)", sentCount.toString(), successCol),
                        Triple("ব্যর্থ (Failed)", failedCount.toString(), errorCol)
                    )
                } else {
                    listOf(
                        Triple("Queued", queuedCount.toString(), warningCol),
                        Triple("Sent", sentCount.toString(), successCol),
                        Triple("Failed", failedCount.toString(), errorCol)
                    )
                }
                stats.forEach { (label, count, col) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderCol),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(count, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = col)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(label, fontSize = 11.sp, color = secondaryText, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Action header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBangla)
                        "এসএমএস আউটবক্স রেকর্ড (${outboxList.size} টি)"
                    else
                        "Outbox SMS Records (${outboxList.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = primaryText
                )
                if (outboxList.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearOutboxSmsHistory() }) {
                        Text(
                            text = if (isBangla) "লগ পরিষ্কার করুন" else "Clear History",
                            fontSize = 12.sp,
                            color = errorCol
                        )
                    }
                }
            }
        }

        if (outboxList.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.MarkEmailRead, null, tint = secondaryText, modifier = Modifier.size(44.dp))
                    Text(
                        text = if (isBangla) "কোনো আউটবক্স রেকর্ড পাওয়া যায়নি" else "No Outbox Records Found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = primaryText
                    )
                    Text(
                        text = if (isBangla)
                            "বাকি তাগাদা বা মার্কেটিং ক্যাম্পেইন শুরু করলে এখানে হিস্টোরি দেখা যাবে।"
                        else
                            "Sent reminders and marketing campaigns will show their live delivery status here.",
                        fontSize = 12.sp,
                        color = secondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(outboxList, key = { it.id }) { item ->
                OutboxItemCard(item, viewModel, cardBg, borderCol, primaryText, secondaryText, successCol, warningCol, errorCol, isBangla, isDarkMode)
            }
        }
    }
}

@Composable
private fun OutboxItemCard(
    item: OutboxSmsEntity,
    viewModel: AppViewModel,
    cardBg: Color,
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    successCol: Color,
    warningCol: Color,
    errorCol: Color,
    isBangla: Boolean,
    isDarkMode: Boolean
) {
    val statusColor = when (item.status) {
        "SENT", "DELIVERED" -> successCol
        "QUEUED", "SENDING" -> warningCol
        else -> errorCol
    }
    val dateStr = remember(item.createdAt) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(item.createdAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderCol),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.recipientPhone, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryText)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = item.smsType,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = item.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(item.messageText, fontSize = 12.5.sp, color = primaryText, maxLines = 3, lineHeight = 17.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, fontSize = 11.sp, color = secondaryText)

                if (item.status == "FAILED") {
                    TextButton(
                        onClick = { viewModel.retryFailedOutboxSms(item) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isBangla) "পুনরায় পাঠান" else "Retry",
                            fontSize = 11.5.sp,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}
