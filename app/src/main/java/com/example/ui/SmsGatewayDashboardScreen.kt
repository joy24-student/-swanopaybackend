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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val outboxList by viewModel.outboxSmsList.collectAsState()
    val dueCustomers by viewModel.customersWithDue.collectAsState()
    val availableSims by viewModel.availableSimCards.collectAsState()
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsState()
    val isGatewayActive by viewModel.isGatewayActive.collectAsState()
    val apiKey by viewModel.gatewayApiKey.collectAsState()
    val throttleDelay by viewModel.smsThrottleDelayMs.collectAsState()
    val autoPosReceipt by viewModel.autoPosReceiptSmsEnabled.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("বাকি ও ক্যাম্পেইন", "সিম ও সেটিংস", "ওয়েবসাইট এপিআই", "আউটবক্স হিস্টোরি")

    LaunchedEffect(Unit) {
        viewModel.refreshSimCards()
    }

    // Theme definitions
    val scaffoldBg = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val cardBg = if (isDarkMode) Color(0xFF1E293B) else Color.White
    val primaryText = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val secondaryText = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val borderCol = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
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
                            text = "এসএমএস গেটওয়ে ও অটো ক্যাম্পেইন",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Text(
                            text = "নিজস্ব সিম কার্ড দিয়ে স্বয়ংক্রিয় এসএমএস প্রেরণ",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = primaryText)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isGatewayActive) successCol.copy(alpha = 0.15f) else errorCol.copy(alpha = 0.15f))
                            .border(1.dp, if (isGatewayActive) successCol else errorCol, RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isGatewayActive) successCol else errorCol)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isGatewayActive) "সিম গেটওয়ে সক্রিয়" else "গেটওয়ে বন্ধ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isGatewayActive) successCol else errorCol
                        )
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
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = cardBg,
                contentColor = accentBrand,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = accentBrand
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.5.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index) accentBrand else secondaryText
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> DueAndCampaignTab(viewModel, cardBg, borderCol, primaryText, secondaryText, accentBrand, successCol)
                1 -> SimSettingsTab(viewModel, cardBg, borderCol, primaryText, secondaryText, accentBrand, successCol)
                2 -> ExternalApiTab(viewModel, cardBg, borderCol, primaryText, secondaryText, accentBrand, successCol)
                3 -> OutboxLogsTab(viewModel, outboxList, cardBg, borderCol, primaryText, secondaryText, successCol, warningCol, errorCol)
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
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    accentBrand: Color,
    successCol: Color
) {
    val context = LocalContext.current
    val dueCustomers by viewModel.customersWithDue.collectAsState()
    var dueTemplate by remember { mutableStateOf("প্রিয় {name}, {store}-এ আপনার বাকি {due} টাকা পরিশোধের অনুরোধ জানাচ্ছি। ধন্যবাদ।") }
    var isSendingDue by remember { mutableStateOf(false) }

    var marketingAudience by remember { mutableStateOf("ALL") }
    var marketingTemplate by remember { mutableStateOf("সম্মানিত গ্রাহক {name}, {store}-এ নতুন অফার ও বিশেষ ডিসকাউন্ট চলছে! আজই কেনাকাটা করুন।") }
    var isSendingMarketing by remember { mutableStateOf(false) }

    var customPhonesInput by remember { mutableStateOf("") }
    var customMessageInput by remember { mutableStateOf("") }
    var isSendingCustom by remember { mutableStateOf(false) }

    val totalDueAmount = remember(dueCustomers) { dueCustomers.sumOf { it.currentBalance } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Due Reminder Card ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.MonetizationOn, null, tint = Color(0xFFEAB308), modifier = Modifier.size(22.dp))
                            Text("বাকি তাগাদা এসএমএস (Due Reminder)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primaryText)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFEF3C7)) {
                            Text(
                                text = "${dueCustomers.size} জন বাকিদার",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFEF2F2))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("মোট বকেয়া টাকার পরিমাণ", fontSize = 11.sp, color = Color(0xFF991B1B))
                            Text("৳ ${String.format(Locale.US, "%,.0f", totalDueAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                        Text("ট্যাগ: {name}, {due}, {store}", fontSize = 10.5.sp, color = Color(0xFF991B1B))
                    }

                    OutlinedTextField(
                        value = dueTemplate,
                        onValueChange = { dueTemplate = it },
                        label = { Text("এসএমএস টেমপ্লেট") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBrand,
                            unfocusedBorderColor = borderCol
                        )
                    )

                    Button(
                        onClick = {
                            if (dueCustomers.isEmpty()) {
                                Toast.makeText(context, "কোনো বাকিদার গ্রাহক নেই", Toast.LENGTH_SHORT).show()
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSendingDue) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("সকল বাকিদারকে একসাথে এসএমএস পাঠান (${dueCustomers.size} জন)", fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Campaign, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(22.dp))
                        Text("মার্কেটিং ব্রডকাস্ট ক্যাম্পেইন (Marketing)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primaryText)
                    }

                    // Target Audience Selection
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL" to "সকল গ্রাহক", "DUE" to "বাকিদার", "ZERO_DUE" to "বাকিহীন").forEach { (code, label) ->
                            val isSelected = marketingAudience == code
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accentBrand else cardBg,
                                border = BorderStroke(1.dp, if (isSelected) accentBrand else borderCol),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { marketingAudience = code }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else primaryText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = marketingTemplate,
                        onValueChange = { marketingTemplate = it },
                        label = { Text("মার্কেটিং বার্তা") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBrand,
                            unfocusedBorderColor = borderCol
                        )
                    )

                    // Character count & Part helper
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("অক্ষর: ${marketingTemplate.length}", fontSize = 11.sp, color = secondaryText)
                        val parts = if (marketingTemplate.any { it.code > 127 }) (marketingTemplate.length / 70) + 1 else (marketingTemplate.length / 160) + 1
                        Text("এসএমএস পার্ট: $parts টি", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentBrand)
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSendingMarketing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ক্যাম্পেইন শুরু করুন (Broadcast via SIM)", fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Chat, null, tint = successCol, modifier = Modifier.size(20.dp))
                        Text("যেকোনো নম্বরে সরাসরি এসএমএস পাঠান", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = primaryText)
                    }

                    OutlinedTextField(
                        value = customPhonesInput,
                        onValueChange = { customPhonesInput = it },
                        label = { Text("মোবাইল নম্বর (একাধিক হলে কমা বা নতুন লাইনে লিখুন)") },
                        placeholder = { Text("017XXXXXXXX, 018XXXXXXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBrand, unfocusedBorderColor = borderCol)
                    )

                    OutlinedTextField(
                        value = customMessageInput,
                        onValueChange = { customMessageInput = it },
                        label = { Text("বার্তা (Message)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBrand, unfocusedBorderColor = borderCol)
                    )

                    Button(
                        onClick = {
                            val numbers = customPhonesInput.split(",", "\n").map { it.trim() }.filter { it.length >= 10 }
                            if (numbers.isEmpty() || customMessageInput.isBlank()) {
                                Toast.makeText(context, "নম্বর এবং মেসেজ পূরণ করুন", Toast.LENGTH_SHORT).show()
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("এসএমএস কিউতে যোগ করুন (Send SMS)", fontWeight = FontWeight.Bold)
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
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    accentBrand: Color,
    successCol: Color
) {
    val availableSims by viewModel.availableSimCards.collectAsState()
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsState()
    val isGatewayActive by viewModel.isGatewayActive.collectAsState()
    val autoPosReceipt by viewModel.autoPosReceiptSmsEnabled.collectAsState()
    var throttleDelaySlider by remember { mutableFloatStateOf(2.5f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SIM Card Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SimCard, null, tint = accentBrand)
                            Text("এসএমএস প্রেরক সিম কার্ড নির্বাচন", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primaryText)
                        }
                        IconButton(onClick = { viewModel.refreshSimCards() }) {
                            Icon(Icons.Default.Refresh, "Refresh SIMs", tint = accentBrand)
                        }
                    }

                    if (availableSims.isEmpty()) {
                        Text("সিম কার্ড তথ্য লোড হচ্ছে বা কোনো সক্রিয় সিম সনাক্ত হয়নি।", fontSize = 12.sp, color = secondaryText)
                    } else {
                        availableSims.forEach { sim ->
                            val isSelected = selectedSimSlot == sim.slotIndex
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) accentBrand.copy(alpha = 0.08f) else cardBg,
                                border = BorderStroke(1.5.dp, if (isSelected) accentBrand else borderCol),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setSelectedSimSlot(sim.slotIndex) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(
                                            Icons.Default.SimCard,
                                            null,
                                            tint = if (isSelected) accentBrand else secondaryText,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(sim.displayName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = primaryText)
                                            Text("অপারেটর: ${sim.carrierName} | স্লট: ${sim.slotIndex + 1}", fontSize = 11.sp, color = secondaryText)
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("অ্যান্টি-স্প্যাম সিম সুরক্ষা ডিলে (Throttle Delay)", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = primaryText)
                    Text(
                        text = "মোবাইল অপারেটর (GP, Robi, BL) যেন বাল্ক এসএমএস-এর কারণে সিম ব্লক না করে, সেজন্য প্রতি এসএমএস-এর মাঝে সেফ বিরতি দেওয়া হয়।",
                        fontSize = 12.sp,
                        color = secondaryText
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("বর্তমান ডিলে:", fontSize = 12.sp, color = primaryText)
                        Text("${String.format(Locale.US, "%.1f", throttleDelaySlider)} সেকেন্ড", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentBrand)
                    }

                    Slider(
                        value = throttleDelaySlider,
                        onValueChange = {
                            throttleDelaySlider = it
                            viewModel.smsThrottleDelayMs.value = (it * 1000).toLong()
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("বিক্রয় রশিদ অটোমেটিক এসএমএস", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryText)
                        Text("POS সেল বা চেকআউট সম্পন্ন হলে কাস্টমারের নম্বরে ডিজিটাল রশিদের এসএমএস চলে যাবে।", fontSize = 11.5.sp, color = secondaryText)
                    }
                    Switch(
                        checked = autoPosReceipt,
                        onCheckedChange = { viewModel.autoPosReceiptSmsEnabled.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentBrand, checkedTrackColor = accentBrand.copy(alpha = 0.5f))
                    )
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
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    accentBrand: Color,
    successCol: Color
) {
    val context = LocalContext.current
    val apiKey by viewModel.gatewayApiKey.collectAsState()
    var selectedSnippetTab by remember { mutableIntStateOf(0) }
    val snippetTabs = listOf("cURL", "PHP / WordPress", "JavaScript / Node", "Python")

    var testOtpPhone by remember { mutableStateOf("") }
    var testOtpCode by remember { mutableStateOf("") }
    var testOtpStatusMsg by remember { mutableStateOf("") }
    var isSendingTestOtp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // API Key Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("গেটওয়ে এপিআই কি (Merchant Gateway API Key)", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = primaryText)
                    Text("যেকোনো ই-কমার্স ওয়েবসাইট (WooCommerce, Shopify, Laravel) বা সফটওয়্যারে এই কি ব্যবহার করে ওটিপি ও ট্রানজেকশনাল এসএমএস পাঠাতে পারবেন।", fontSize = 11.5.sp, color = secondaryText)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (cardBg == Color.White) Color(0xFFF1F5F9) else Color(0xFF0F172A))
                            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                            .padding(10.dp),
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
                                Toast.makeText(context, "API Key কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = primaryText, modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.generateNewGatewayApiKey()
                                Toast.makeText(context, "নতুন API Key তৈরি হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("নতুন কি তৈরি করুন", fontSize = 12.sp)
                        }
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("টেস্ট ওটিপি প্রেরণ ও যাচাই স্যান্ডবক্স (Live Test)", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = primaryText)
                    Text("ওয়েবসাইট ছাড়া সরাসরি এই অ্যাপ থেকেই ওটিপি জেনারেশন ও সিম প্রেরণের পূর্ণ চক্র টেস্ট করুন:", fontSize = 11.5.sp, color = secondaryText)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = testOtpPhone,
                            onValueChange = { testOtpPhone = it },
                            label = { Text("মোবাইল নম্বর") },
                            placeholder = { Text("017XXXXXXXX") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentBrand)
                        )

                        Button(
                            onClick = {
                                if (testOtpPhone.length < 10) {
                                    Toast.makeText(context, "সঠিক নম্বর দিন", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSendingTestOtp = true
                                testOtpStatusMsg = "ওটিপি জেনারেট হচ্ছে..."
                                val code = (100000..999999).random().toString()
                                testOtpCode = code
                                viewModel.sendQuickCustomSms(
                                    listOf(testOtpPhone),
                                    "আপনার SwapnoPay যাচাইকরণ কোড হলো: $code (মেয়াদ ৫ মিনিট)।"
                                ) { success, msg ->
                                    isSendingTestOtp = false
                                    testOtpStatusMsg = if (success) "✅ ওটিপি ($code) সিম কিউতে পাঠানো হয়েছে!" else "❌ $msg"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSendingTestOtp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text("ওটিপি পাঠান")
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ওয়েবসাইটে ইন্টিগ্রেশনের কোড (API Integration Snippets)", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = primaryText)

                    TabRow(
                        selectedTabIndex = selectedSnippetTab,
                        containerColor = cardBg,
                        contentColor = accentBrand
                    ) {
                        snippetTabs.forEachIndexed { idx, title ->
                            Tab(
                                selected = selectedSnippetTab == idx,
                                onClick = { selectedSnippetTab = idx },
                                text = { Text(title, fontSize = 11.sp) }
                            )
                        }
                    }

                    val snippetCode = when (selectedSnippetTab) {
                        0 -> """
curl -X POST "https://api.swapnopay.top/v1/sms-gateway/send-otp" \
  -H "X-API-Key: $apiKey" \
  -H "Content-Type: application/json" \
  -d '{"phone": "01712345678", "purpose": "Login"}'
                        """.trimIndent()
                        1 -> """
<?php
// WordPress / PHP WooCommerce OTP dispatch
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
?>
                        """.trimIndent()
                        2 -> """
// Node.js / JavaScript Fetch
await fetch('https://api.swapnopay.top/v1/sms-gateway/send-otp', {
  method: 'POST',
  headers: {
    'X-API-Key': '$apiKey',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    phone: '01712345678',
    purpose: 'Checkout OTP'
  })
});
                        """.trimIndent()
                        else -> """
# Python Requests
import requests

res = requests.post(
  'https://api.swapnopay.top/v1/sms-gateway/send-otp',
  headers={'X-API-Key': '$apiKey'},
  json={'phone': '01712345678', 'purpose': 'Login'}
)
print(res.json())
                        """.trimIndent()
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Endpoint: /v1/sms-gateway/send-otp", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp).clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", snippetCode))
                                        Toast.makeText(context, "কোড কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
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
    borderCol: Color,
    primaryText: Color,
    secondaryText: Color,
    successCol: Color,
    warningCol: Color,
    errorCol: Color
) {
    val context = LocalContext.current
    val queuedCount = outboxList.count { it.status == "QUEUED" || it.status == "SENDING" }
    val sentCount = outboxList.count { it.status == "SENT" || it.status == "DELIVERED" }
    val failedCount = outboxList.count { it.status == "FAILED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("অপেক্ষমান (Queue)", queuedCount.toString(), warningCol),
                    Triple("সফল (Sent)", sentCount.toString(), successCol),
                    Triple("ব্যর্থ (Failed)", failedCount.toString(), errorCol)
                ).forEach { (label, count, col) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderCol),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = col)
                            Text(label, fontSize = 10.5.sp, color = secondaryText, textAlign = TextAlign.Center)
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
                Text("এসএমএস আউটবক্স রেকর্ড (${outboxList.size} টি)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = primaryText)
                if (outboxList.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearOutboxSmsHistory() }) {
                        Text("লগ পরিষ্কার করুন", fontSize = 11.5.sp, color = errorCol)
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
                    Icon(Icons.Outlined.MarkEmailRead, null, tint = secondaryText, modifier = Modifier.size(40.dp))
                    Text("কোনো আউটবক্স রেকর্ড পাওয়া যায়নি", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryText)
                    Text("বাকি তাগাদা বা মার্কেটিং ক্যাম্পেইন শুরু করলে এখানে হিস্টোরি দেখা যাবে।", fontSize = 12.sp, color = secondaryText, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(outboxList, key = { it.id }) { item ->
                OutboxItemCard(item, viewModel, cardBg, borderCol, primaryText, secondaryText, successCol, warningCol, errorCol)
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
    errorCol: Color
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
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.recipientPhone, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = primaryText)
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE2E8F0)) {
                        Text(item.smsType, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        text = item.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(item.messageText, fontSize = 12.sp, color = primaryText, maxLines = 3)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, fontSize = 10.5.sp, color = secondaryText)

                if (item.status == "FAILED") {
                    TextButton(
                        onClick = { viewModel.retryFailedOutboxSms(item) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("পুনরায় পাঠান (Retry)", fontSize = 11.sp, color = Color(0xFF2563EB))
                    }
                }
            }
        }
    }
}
