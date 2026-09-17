@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
import android.widget.Toast

// ── Models for AI Call Service ──
data class AiCallRecord(
    val id: String,
    val merchantId: String = "default",
    val direction: String = "inbound", // "inbound" | "outbound"
    val from: String = "",
    val to: String = "",
    val customerName: String = "",
    val purpose: String = "",
    val status: String = "completed",
    val duration: String = "0m 45s",
    val createdAt: String = "",
    val summary: String = "",
    val transcript: List<AiCallTurn> = emptyList()
)

data class AiCallTurn(
    val role: String, // "assistant" | "customer"
    val text: String,
    val time: String = ""
)

data class AiCampaignFeedbackItem(
    val id: String,
    val customerName: String = "",
    val customerPhone: String = "",
    val campaignTitle: String = "",
    val campaignType: String = "GENERAL", // "MEETING_INVITE" | "DISCOUNT_OFFER" | "GENERAL"
    val decision: String = "PENDING",     // "ATTENDING" | "INTERESTED" | "DECLINED" | "PENDING"
    val feedbackText: String = "",
    val sentiment: String = "NEUTRAL",    // "POSITIVE" | "NEUTRAL" | "NEGATIVE"
    val callStatus: String = "COMPLETED", // "COMPLETED" | "PENDING" | "FAILED"
    val callDuration: String = "0m 45s",
    val createdAt: String = ""
)

data class AiVoiceSettingsState(
    val agentName: String = "তানিয়া (Tania)",
    val language: String = "bn-BD",
    val voiceGender: String = "female",
    val autoAnswer: Boolean = true,
    val businessName: String = "স্বপ্নপে স্টোর",
    val greetingBn: String = "আসসালামু আলাইকুম! স্বপ্নপে কাস্টমার কেয়ারে আপনাকে স্বাগতম। আমি আপনার এআই প্রতিনিধি। আজ আপনাকে কীভাবে সাহায্য করতে পারি?",
    val dueReminderScript: String = "আসসালামু আলাইকুম {customer_name}, {business_name} থেকে বলছি। আপনার {due_amount} টাকা বকেয়া রয়েছে। আপনি কি আগামীকালের মধ্যে পরিশোধ করতে পারবেন?",
    val callerNumber: String = "+8809612345678"
)

// ══════════════════════════════════════════════════════════════════════════════
// Main AI Call Center Hub Screen
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AiCallCenterScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val isBangla = language == "Bangla"

    val callLogs by viewModel.aiCallLogs.collectAsState()
    val campaignFeedbacks by viewModel.aiCampaignFeedbacks.collectAsState()
    val voiceSettings by viewModel.aiVoiceSettings.collectAsState()
    val isActionLoading by viewModel.isTriggeringAiCall.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Call Logs, 1: AI Persona, 2: Campaigns & Spreadsheet
    var showDueCallDialog by remember { mutableStateOf(false) }
    var showOrderCallDialog by remember { mutableStateOf(false) }
    var showTestCallDialog by remember { mutableStateOf(false) }
    var showInstantVoiceRecordDialog by remember { mutableStateOf(false) }
    var showCampaignDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchAiCallLogs()
        viewModel.fetchCampaignFeedbacks()
    }

    val bgColor = if (isDarkMode) Color(0xFF0F0E17) else Color(0xFFF8FAFC)
    val cardBg = if (isDarkMode) Color(0xFF181524) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0xFF2C2640) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isBangla) "এআই ভয়েস কল সেবা" else "AI Voice Call Service",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = if (isBangla) "স্বয়ংক্রিয় ইনবাউন্ড, আউটবাউন্ড ও গণ ক্যাম্পেইন" else "Automated Inbound, Outbound & Mass Campaigns",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.fetchAiCallLogs()
                        viewModel.fetchCampaignFeedbacks()
                        Toast.makeText(context, if (isBangla) "কল ও ফিডব্যাক তথ্য রিফ্রেশ করা হয়েছে" else "Call logs & feedbacks refreshed", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = BrandPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Top Header Hero Banner ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF4F46E5),
                                    Color(0xFF7C3AED)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HeadsetMic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = voiceSettings.agentName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = if (isBangla) "দোকানের সক্রিয় এআই রিসেপশনিস্ট" else "Active Store AI Receptionist",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            // Auto-Answer Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (voiceSettings.autoAnswer) Color(0xFF10B981) else Color.White.copy(alpha = 0.25f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                    Text(
                                        text = if (voiceSettings.autoAnswer)
                                            (if (isBangla) "কল রিসিভ চালু" else "Auto-Answer Active")
                                        else
                                            (if (isBangla) "বন্ধ" else "Off"),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Statistics row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${callLogs.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(if (isBangla) "মোট কল" else "Total Calls", color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${callLogs.count { it.direction == "inbound" }}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(if (isBangla) "ইনবাউন্ড" else "Inbound", color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${callLogs.count { it.direction == "outbound" }}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(if (isBangla) "আউটবাউন্ড তাগাদা" else "Outbound", color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }

            // ── Instant Voice Record Card (100% Twilio-Free, Direct to AI) ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { showInstantVoiceRecordDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1E1B2E) else Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(
                                text = if (isBangla) "ইনস্ট্যান্ট ভয়েস রেকর্ড → এআই" else "Instant Voice Record → AI",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = if (isBangla) "কথা বলুন, এআই স্বয়ংক্রিয় উত্তর দিবে (টুইলিও ছাড়া ফ্রি)" else "Speak directly, AI responds with voice (Zero Twilio)",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF3B82F6))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Quick Action Buttons Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Outbound Due Reminder
                Button(
                    onClick = { showDueCallDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PhoneCallback, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBangla) "বকেয়া তাগাদা কল" else "Due Reminder Call",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Outbound Order Confirm
                Button(
                    onClick = { showOrderCallDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.CheckCircleOutline, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBangla) "অর্ডার কনফার্ম কল" else "Order Confirm",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Test Talk
                OutlinedButton(
                    onClick = { showTestCallDialog = true },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandPurple)
                ) {
                    Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(16.dp), tint = BrandPurple)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBangla) "টেস্ট" else "Test",
                        fontSize = 12.sp,
                        color = BrandPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Mass Campaign Broadcast Banner Button ──
            Button(
                onClick = { showCampaignDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBangla) "📢 নতুন গণ ভয়েস ক্যাম্পেইন ও স্প্রেডশিট কল" else "📢 Start Mass Voice Campaign Call",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Tabs: Call Logs vs Settings ──
            // ── Tabs: Call Logs vs Settings vs Campaign ──
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = cardBg,
                contentColor = BrandPurple,
                divider = { Divider(color = cardBorder, thickness = 1.dp) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = if (isBangla) "কল হিস্ট্রি" else "Call Logs",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (isBangla) "ভয়েস সেটিংস" else "AI Persona",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = if (isBangla) "ক্যাম্পেইন ও স্প্রেডশিট" else "Campaign & Sheet",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                )
            }

            // ── Tab Content ──
            if (selectedTab == 0) {
                // Call Logs List
                if (callLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.PhoneMissed, contentDescription = null, tint = textSecondary, modifier = Modifier.size(48.dp))
                            Text(
                                text = if (isBangla) "কোনো কল হিস্ট্রি নেই" else "No call logs yet",
                                color = textSecondary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isBangla) "ইনবাউন্ড কল এলে বা আউটবাউন্ড তাগাদা দিলে এখানে সম্পূর্ণ ট্রানস্ক্রিপ্ট দেখতে পাবেন।" else "Inbound and outbound calls will appear here with full AI transcripts.",
                                color = textSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(callLogs) { record ->
                            AiCallLogCard(
                                record = record,
                                isDarkMode = isDarkMode,
                                isBangla = isBangla
                            )
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // AI Settings Tab
                AiVoiceSettingsTab(
                    settings = voiceSettings,
                    isDarkMode = isDarkMode,
                    isBangla = isBangla,
                    onSave = { updated ->
                        viewModel.updateAiVoiceSettings(updated) { success ->
                            Toast.makeText(
                                context,
                                if (success) (if (isBangla) "সেটিংস সংরক্ষিত হয়েছে" else "Settings saved")
                                else (if (isBangla) "সংরক্ষণ ব্যর্থ হয়েছে" else "Failed to save settings"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } else {
                // AI Mass Campaign & Feedback Spreadsheet Tab
                CampaignFeedbackSpreadsheetView(
                    feedbacks = campaignFeedbacks,
                    isDarkMode = isDarkMode,
                    isBangla = isBangla,
                    viewModel = viewModel,
                    onOpenNewCampaign = { showCampaignDialog = true }
                )
            }
        }
    }

    // ── Dialogs ──
    if (showDueCallDialog) {
        OutboundDueCallDialog(
            isDarkMode = isDarkMode,
            isBangla = isBangla,
            isLoading = isActionLoading,
            onDismiss = { showDueCallDialog = false },
            onConfirm = { phone, name, amount ->
                viewModel.triggerDueReminderCall(phone, name, amount) { success, msg ->
                    showDueCallDialog = false
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showOrderCallDialog) {
        OutboundOrderCallDialog(
            isDarkMode = isDarkMode,
            isBangla = isBangla,
            isLoading = isActionLoading,
            onDismiss = { showOrderCallDialog = false },
            onConfirm = { phone, name, orderId, amount ->
                viewModel.triggerOrderConfirmCall(phone, name, orderId, amount) { success, msg ->
                    showOrderCallDialog = false
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showTestCallDialog) {
        InteractiveVoiceTestDialog(
            isDarkMode = isDarkMode,
            isBangla = isBangla,
            voiceSettings = voiceSettings,
            onDismiss = { showTestCallDialog = false }
        )
    }

    if (showInstantVoiceRecordDialog) {
        InstantVoiceRecordToAiDialog(
            isDarkMode = isDarkMode,
            isBangla = isBangla,
            voiceSettings = voiceSettings,
            viewModel = viewModel,
            onDismiss = { showInstantVoiceRecordDialog = false }
        )
    }

    if (showCampaignDialog) {
        CampaignBroadcastDialog(
            isDarkMode = isDarkMode,
            isBangla = isBangla,
            isLoading = isActionLoading,
            viewModel = viewModel,
            onDismiss = { showCampaignDialog = false }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Component: AI Call Log Card with Expandable Transcript
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AiCallLogCard(
    record: AiCallRecord,
    isDarkMode: Boolean,
    isBangla: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    val cardBg = if (isDarkMode) Color(0xFF181524) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0xFF2C2640) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)

    val isInbound = record.direction == "inbound"
    val badgeBg = if (isInbound) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF4F46E5).copy(alpha = 0.15f)
    val badgeTextColor = if (isInbound) Color(0xFF10B981) else Color(0xFF6366F1)
    val badgeIcon = if (isInbound) Icons.Default.CallReceived else Icons.Default.CallMade

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Type chip, Customer name/phone, Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(badgeIcon, contentDescription = null, tint = badgeTextColor, modifier = Modifier.size(12.dp))
                            Text(
                                text = if (isInbound) (if (isBangla) "ইনবাউন্ড কল" else "Inbound") else (if (isBangla) "তাগাদা কল" else "Outbound Due"),
                                color = badgeTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = record.customerName.ifBlank { record.from },
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Schedule, null, tint = textSecondary, modifier = Modifier.size(12.dp))
                    Text(record.duration, color = textSecondary, fontSize = 11.sp)
                }
            }

            // Purpose & Summary
            Text(
                text = record.purpose,
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (record.summary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkMode) Color(0xFF221D33) else Color(0xFFF1F5F9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = BrandPurple, modifier = Modifier.size(14.dp))
                        Text(
                            text = record.summary,
                            color = textPrimary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Expandable Conversation Transcript
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(color = cardBorder, thickness = 0.8.dp)
                    Text(
                        text = if (isBangla) "💬 কথোপকথনের পুরো ট্রানস্ক্রিপ্ট:" else "💬 Full Call Transcript:",
                        color = textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (record.transcript.isEmpty()) {
                        Text(
                            text = if (isBangla) "কোনো কথাবলার রেকর্ড পাওয়া যায়নি।" else "No transcript available.",
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    } else {
                        record.transcript.forEach { turn ->
                            val isAi = turn.role == "assistant"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isAi)
                                        (if (isDarkMode) Color(0xFF261D45) else Color(0xFFEEF2FF))
                                    else
                                        (if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (isAi) (if (isBangla) "🤖 এআই প্রতিনিধি" else "🤖 AI Assistant") else (if (isBangla) "👤 কাস্টমার" else "👤 Customer"),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAi) BrandPurple else textPrimary
                                            )
                                            Text(turn.time, fontSize = 9.5.sp, color = textSecondary)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(turn.text, fontSize = 11.5.sp, color = textPrimary, lineHeight = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Expand Prompt Hint
            Text(
                text = if (expanded)
                    (if (isBangla) "▲ ট্রানস্ক্রিপ্ট লুকান" else "▲ Hide Transcript")
                else
                    (if (isBangla) "▼ ট্রানস্ক্রিপ্ট ও অডিও বিবরণ দেখুন" else "▼ View Full Conversation Transcript"),
                color = BrandPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Component: AI Persona & Voice Settings Tab
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AiVoiceSettingsTab(
    settings: AiVoiceSettingsState,
    isDarkMode: Boolean,
    isBangla: Boolean,
    onSave: (AiVoiceSettingsState) -> Unit
) {
    var agentName by remember(settings) { mutableStateOf(settings.agentName) }
    var language by remember(settings) { mutableStateOf(settings.language) }
    var voiceGender by remember(settings) { mutableStateOf(settings.voiceGender) }
    var autoAnswer by remember(settings) { mutableStateOf(settings.autoAnswer) }
    var businessName by remember(settings) { mutableStateOf(settings.businessName) }
    var greetingBn by remember(settings) { mutableStateOf(settings.greetingBn) }
    var dueScript by remember(settings) { mutableStateOf(settings.dueReminderScript) }

    val cardBg = if (isDarkMode) Color(0xFF181524) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0xFF2C2640) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle 24/7 Receptionist
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBangla) "স্বয়ংক্রিয় কল রিসিভ (২৪/৭ AI Receptionist)" else "Auto Call Reception (24/7 AI Receptionist)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = textPrimary
                    )
                    Text(
                        text = if (isBangla)
                            "চালু থাকলে কাস্টমার কল করলে এআই প্রতিনিধি ফোন রিসিভ করে তথ্য প্রদান করবে।"
                        else
                            "Automatically answers inbound calls and answers customer queries using Gemini.",
                        fontSize = 11.sp,
                        color = textSecondary,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = autoAnswer,
                    onCheckedChange = { autoAnswer = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF10B981))
                )
            }
        }

        // Persona Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isBangla) "এআই প্রতিনিধির পরিচয়" else "AI Agent Persona",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textPrimary
                )

                // Agent Name
                OutlinedTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    label = { Text(if (isBangla) "প্রতিনিধির নাম" else "Agent Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Business Name
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text(if (isBangla) "প্রতিষ্ঠানের নাম" else "Business Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Voice Gender Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("female" to if (isBangla) "মহিলা কণ্ঠ (Female)" else "Female Voice",
                           "male" to if (isBangla) "পুরুষ কণ্ঠ (Male)" else "Male Voice").forEach { (valKey, label) ->
                        val isSelected = voiceGender == valKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrandPurple else cardBg)
                                .border(1.dp, if (isSelected) BrandPurple else cardBorder, RoundedCornerShape(10.dp))
                                .clickable { voiceGender = valKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else textPrimary
                            )
                        }
                    }
                }
            }
        }

        // Voice Scripts
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isBangla) "কথাবার্তার স্ক্রিপ্ট ও শুভেচ্ছা বার্তা" else "Voice Scripts & Greetings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textPrimary
                )

                // Greeting Script
                OutlinedTextField(
                    value = greetingBn,
                    onValueChange = { greetingBn = it },
                    label = { Text(if (isBangla) "ইনকামিং কল সম্ভাষণ (Greeting)" else "Inbound Greeting Script") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                // Due Reminder Script
                OutlinedTextField(
                    value = dueScript,
                    onValueChange = { dueScript = it },
                    label = { Text(if (isBangla) "বকেয়া তাগাদা কল স্ক্রিপ্ট" else "Due Reminder Script") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )
            }
        }

        // Save Button
        Button(
            onClick = {
                onSave(
                    settings.copy(
                        agentName = agentName,
                        businessName = businessName,
                        voiceGender = voiceGender,
                        autoAnswer = autoAnswer,
                        greetingBn = greetingBn,
                        dueReminderScript = dueScript
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
        ) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isBangla) "সেটিংস সংরক্ষণ করুন" else "Save AI Settings",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Dialog 1: Outbound Due Collection Call
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun OutboundDueCallDialog(
    isDarkMode: Boolean,
    isBangla: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (phone: String, name: String, amount: Double) -> Unit
) {
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var dueAmountStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PhoneCallback, null, tint = Color(0xFFEF4444))
                Text(
                    text = if (isBangla) "স্বয়ংক্রিয় বকেয়া তাগাদা কল" else "Outbound Due Reminder Call",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isBangla)
                        "এআই কাস্টমারকে সরাসরি কল করে বকেয়ার পরিমাণ জানাবে এবং টাকা দেওয়ার তারিখ রেকর্ড করবে।"
                    else
                        "AI dials customer to inform them of due balance and captures their repayment commitment.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text(if (isBangla) "কাস্টমারের ফোন নম্বর" else "Customer Phone") },
                    placeholder = { Text("017XXXXXXXX") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text(if (isBangla) "কাস্টমারের নাম" else "Customer Name") },
                    placeholder = { Text("আব্দুল রহিম") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = dueAmountStr,
                    onValueChange = { dueAmountStr = it },
                    label = { Text(if (isBangla) "বকেয়া টাকা (৳)" else "Due Amount (৳)") },
                    placeholder = { Text("1500") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = dueAmountStr.toDoubleOrNull() ?: 0.0
                    onConfirm(customerPhone, customerName.ifBlank { "সম্মানিত গ্রাহক" }, amt)
                },
                enabled = customerPhone.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (isBangla) "এখনই কল দিন" else "Call Now", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(if (isBangla) "বাতিল" else "Cancel")
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Dialog 2: Outbound Order Confirmation Call
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun OutboundOrderCallDialog(
    isDarkMode: Boolean,
    isBangla: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (phone: String, name: String, orderId: String, amount: Double) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var orderId by remember { mutableStateOf("#ORD-") }
    var amountStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFF10B981))
                Text(
                    text = if (isBangla) "অর্ডার নিশ্চিতকরণ কল" else "Order Confirmation Call",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (isBangla) "কাস্টমার ফোন নম্বর" else "Customer Phone") },
                    placeholder = { Text("018XXXXXXXX") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isBangla) "কাস্টমার নাম" else "Customer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = orderId,
                    onValueChange = { orderId = it },
                    label = { Text(if (isBangla) "অর্ডার আইডি" else "Order ID") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(if (isBangla) "মোট বিল (৳)" else "Total Bill (৳)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    onConfirm(phone, name.ifBlank { "সম্মানিত গ্রাহক" }, orderId, amt)
                },
                enabled = phone.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (isBangla) "কনফার্ম কল দিন" else "Confirm Call", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(if (isBangla) "বাতিল" else "Cancel")
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Dialog 3: Live In-App Voice AI Tester
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun InteractiveVoiceTestDialog(
    isDarkMode: Boolean,
    isBangla: Boolean,
    voiceSettings: AiVoiceSettingsState,
    onDismiss: () -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }
    var conversation by remember {
        mutableStateOf(
            listOf(
                AiCallTurn("assistant", voiceSettings.greetingBn, "00:01")
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.RecordVoiceOver, null, tint = BrandPurple)
                Text(
                    text = if (isBangla) "এআই ভয়েস সহকারী পরীক্ষা (Live Test)" else "Test AI Voice Agent",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isBangla)
                        "দোকানের সময়, পণ্য বা পেমেন্ট নিয়ে প্রশ্ন লিখে এআই এর উত্তর পরীক্ষা করুন:"
                    else
                        "Type a question to simulate customer speech over the call:",
                    fontSize = 11.5.sp,
                    color = Color.Gray
                )

                // Conversation Box
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkMode) Color(0xFF1A162B) else Color(0xFFF1F5F9)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        conversation.forEach { turn ->
                            val isAi = turn.role == "assistant"
                            Text(
                                text = "${if (isAi) "🤖 এআই: " else "👤 আপনি: "}${turn.text}",
                                fontSize = 12.sp,
                                color = if (isAi) BrandPurple else if (isDarkMode) Color.White else Color(0xFF0F172A),
                                fontWeight = if (isAi) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Input Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = { Text(if (isBangla) "প্রশ্ন লিখুন (যেমন: দোকান কখন খোলা?)" else "Ask question...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (queryText.isNotBlank()) {
                                val userQ = queryText
                                queryText = ""
                                val updated = conversation + AiCallTurn("customer", userQ, "00:05")
                                conversation = updated

                                // Mock rule reply for instant test feedback
                                val reply = if (userQ.contains("খোলা") || userQ.contains("সময়")) {
                                    "আমাদের শোরুম সকাল ৯টা থেকে রাত ১০টা পর্যন্ত খোলা থাকে। আপনি যেকোনো সময় আসতে পারেন।"
                                } else if (userQ.contains("বাকি") || userQ.contains("টাকা")) {
                                    "আপনার বকেয়া দেখতে অনুগ্রহ করে মোবাইল নম্বরটি বলুন, আমি চেক করে দিচ্ছি।"
                                } else {
                                    "জি আমি বুঝতে পেরেছি। আপনার প্রশ্নের সন্তোষজনক সমাধান দিতে আমি প্রস্তুত। আর কী জানতে চান?"
                                }
                                conversation = updated + AiCallTurn("assistant", reply, "00:10")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                    ) {
                        Text(if (isBangla) "বলুন" else "Send")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBangla) "বন্ধ করুন" else "Close")
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Dialog 4: Instant Voice Record to AI (100% Twilio-Free)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun InstantVoiceRecordToAiDialog(
    isDarkMode: Boolean,
    isBangla: Boolean,
    voiceSettings: AiVoiceSettingsState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf(if (isBangla) "মাইক্রোফোনে ট্যাপ করে কথা বলুন..." else "Tap microphone to speak...") }
    var capturedSpeech by remember { mutableStateOf("") }
    var aiReplyText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // TTS Engine initialization for speaking AI response aloud
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engineRef: TextToSpeech? = null
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = if (voiceSettings.language.startsWith("bn")) Locale("bn", "BD") else Locale.US
                engineRef?.language = locale
            }
        }
        engineRef = engine
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    // Android Native Speech Recognizer Launcher (Zero Twilio Dependency!)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                capturedSpeech = spoken
                statusText = if (isBangla) "এআই প্রসেস করছে..." else "Processing with AI..."
                isProcessing = true
                viewModel.sendInstantRecordToAi(spoken) { success, _, reply ->
                    isProcessing = false
                    aiReplyText = reply
                    statusText = if (success) (if (isBangla) "এআই উত্তর দিয়েছে" else "AI Responded") else (if (isBangla) "ত্রুটি হয়েছে" else "Error")
                    // Speak aloud via TTS
                    tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "ai_voice_reply")
                }
            } else {
                statusText = if (isBangla) "কোনো কথা শোনা যায়নি, আবার চেষ্টা করুন।" else "No speech detected, try again."
            }
        }
    }

    val launchSpeech = {
        try {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (voiceSettings.language.startsWith("bn")) "bn-BD" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (isBangla) "দোকানের সময়, অর্ডার বা হিসাব নিয়ে কথা বলুন..." else "Speak your question...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF10B981))
                Text(
                    text = if (isBangla) "ইনস্ট্যান্ট ভয়েস রেকর্ড → এআই" else "Instant Voice Record → AI",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF475569),
                    textAlign = TextAlign.Center
                )

                // Large Glowing Record Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening || isProcessing)
                                Brush.radialGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                            else
                                Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))
                        )
                        .clickable { if (!isProcessing) launchSpeech() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Text(
                    text = if (isBangla) "মাইক্রোফোনে ট্যাপ করে কথা বলুন (টুইলিও ছাড়া ১০০% ফ্রি)" else "Tap to Speak (100% Free, Zero Twilio)",
                    fontSize = 10.5.sp,
                    color = Color.Gray
                )

                // Captured User Speech Box
                if (capturedSpeech.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isBangla) "👤 আপনি বলেছেন:" else "👤 You said:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPurple
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(capturedSpeech, fontSize = 12.sp)
                        }
                    }
                }

                // AI Spoken Reply Box
                if (aiReplyText.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDarkMode) Color(0xFF261D45) else Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, BrandPurple.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🤖 ${voiceSettings.agentName} (এআই ভয়েস উত্তর):",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                IconButton(
                                    onClick = { tts?.speak(aiReplyText, TextToSpeech.QUEUE_FLUSH, null, null) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(aiReplyText, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBangla) "সমাপ্ত" else "Done")
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Component: AI Mass Campaign Feedback Spreadsheet View
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun CampaignFeedbackSpreadsheetView(
    feedbacks: List<AiCampaignFeedbackItem>,
    isDarkMode: Boolean,
    isBangla: Boolean,
    viewModel: AppViewModel,
    onOpenNewCampaign: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "ATTENDING", "INTERESTED", "DECLINED", "PENDING"
    var selectedFeedbackForDetail by remember { mutableStateOf<AiCampaignFeedbackItem?>(null) }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportCampaignFeedbackCsv(context, uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF181524) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0xFF2C2640) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)

    val attendingCount = feedbacks.count { it.decision == "ATTENDING" }
    val interestedCount = feedbacks.count { it.decision == "INTERESTED" }
    val declinedCount = feedbacks.count { it.decision == "DECLINED" }
    val pendingCount = feedbacks.count { it.decision == "PENDING" }

    val filteredList = feedbacks.filter { item ->
        val matchesSearch = if (searchQuery.isBlank()) true else {
            item.customerName.contains(searchQuery, ignoreCase = true) ||
            item.customerPhone.contains(searchQuery) ||
            item.campaignTitle.contains(searchQuery, ignoreCase = true) ||
            item.feedbackText.contains(searchQuery, ignoreCase = true)
        }
        val matchesFilter = when (selectedFilter) {
            "ATTENDING" -> item.decision == "ATTENDING"
            "INTERESTED" -> item.decision == "INTERESTED"
            "DECLINED" -> item.decision == "DECLINED"
            "PENDING" -> item.decision == "PENDING"
            else -> true
        }
        matchesSearch && matchesFilter
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. KPI Summary Banner Card ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.TableChart, null, tint = BrandPurple, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (isBangla) "ফিডব্যাক স্প্রেডশিট সামারি" else "Feedback Spreadsheet Summary",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandPurple.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isBangla) "মোট কল: ${feedbacks.size}" else "Total: ${feedbacks.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Attending
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$attendingCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF10B981))
                                Text(if (isBangla) "উপস্থিত" else "Attending", fontSize = 10.sp, color = textSecondary)
                            }
                        }
                        // Interested
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$interestedCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3B82F6))
                                Text(if (isBangla) "আগ্রহী" else "Interested", fontSize = 10.sp, color = textSecondary)
                            }
                        }
                        // Declined
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$declinedCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFEF4444))
                                Text(if (isBangla) "অনুপস্থিত" else "Declined", fontSize = 10.sp, color = textSecondary)
                            }
                        }
                        // Pending
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$pendingCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFF59E0B))
                                Text(if (isBangla) "অপেক্ষমান" else "Pending", fontSize = 10.sp, color = textSecondary)
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Action Bar: New Campaign, Export CSV/Excel, Share ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Launch New Campaign
                Button(
                    onClick = onOpenNewCampaign,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Campaign, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBangla) "+ নতুন ক্যাম্পেইন কল" else "+ New Campaign",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Export CSV / Excel Button
                Button(
                    onClick = {
                        exportCsvLauncher.launch("SwapnoPay_AI_Feedback_Spreadsheet_${System.currentTimeMillis()}.csv")
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBangla) "এক্সপোর্ট CSV" else "Export CSV",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Share Summary Report
                OutlinedButton(
                    onClick = {
                        val report = StringBuilder()
                        report.append("📊 স্বপ্নপে এআই কল ক্যাম্পেইন ও ফিডব্যাক রিপোর্ট\n")
                        report.append("═══════════════════════════════\n")
                        report.append("মোট কল সংখ্যা: ${feedbacks.size}\n")
                        report.append("✅ অংশগ্রহণ করবেন: $attendingCount\n")
                        report.append("👍 আগ্রহী: $interestedCount\n")
                        report.append("❌ অনিচ্ছুক: $declinedCount\n")
                        report.append("⏳ অপেক্ষমান: $pendingCount\n\n")
                        report.append("গ্রাহকদের সরাসরি মতামত তালিকা:\n")
                        feedbacks.take(10).forEachIndexed { i, fb ->
                            report.append("${i + 1}. ${fb.customerName} (${fb.customerPhone}): [${fb.decision}] \"${fb.feedbackText}\"\n")
                        }
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SwapnoPay AI Feedback Report")
                                putExtra(Intent.EXTRA_TEXT, report.toString())
                            }
                            context.startActivity(Intent.createChooser(intent, "রিপোর্ট শেয়ার করুন"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "শেয়ার করা যায়নি", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BrandPurple),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp), tint = BrandPurple)
                }
            }
        }

        // ── 3. Search & Filter Bar ──
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(if (isBangla) "কাস্টমার নাম, ফোন বা ক্যাম্পেইন খুঁজুন..." else "Search customer, phone or feedback...", fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = textSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg,
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = cardBorder
                    ),
                    singleLine = true
                )

                // Horizontal Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf(
                        "ALL" to (if (isBangla) "সকল (${feedbacks.size})" else "All (${feedbacks.size})"),
                        "ATTENDING" to (if (isBangla) "উপস্থিত ($attendingCount)" else "Attending ($attendingCount)"),
                        "INTERESTED" to (if (isBangla) "আগ্রহী ($interestedCount)" else "Interested ($interestedCount)"),
                        "DECLINED" to (if (isBangla) "অনিচ্ছুক ($declinedCount)" else "Declined ($declinedCount)"),
                        "PENDING" to (if (isBangla) "অপেক্ষমান ($pendingCount)" else "Pending ($pendingCount)")
                    )

                    items(filters) { (code, label) ->
                        val isSelected = selectedFilter == code
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) BrandPurple else cardBg,
                            border = BorderStroke(1.dp, if (isSelected) BrandPurple else cardBorder),
                            modifier = Modifier.clickable { selectedFilter = code }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else textPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 4. Spreadsheet Table Rows ──
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.FilterListOff, null, modifier = Modifier.size(44.dp), tint = textSecondary)
                        Text(
                            text = if (isBangla) "কোনো ফলাফল পাওয়া যায়নি" else "No matching feedback found",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = onOpenNewCampaign,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isBangla) "নতুন ক্যাম্পেইন চালু করুন" else "Start New Campaign", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(filteredList) { item ->
                CampaignFeedbackCard(
                    item = item,
                    isDarkMode = isDarkMode,
                    isBangla = isBangla,
                    onClick = { selectedFeedbackForDetail = item }
                )
            }
        }
    }

    // Detail Dialog
    if (selectedFeedbackForDetail != null) {
        CampaignFeedbackDetailDialog(
            item = selectedFeedbackForDetail!!,
            isDarkMode = isDarkMode,
            isBangla = isBangla,
            onDismiss = { selectedFeedbackForDetail = null }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Component: AI Campaign Feedback Card with Color-Coded Decisions & Quotes
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun CampaignFeedbackCard(
    item: AiCampaignFeedbackItem,
    isDarkMode: Boolean,
    isBangla: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardBg = if (isDarkMode) Color(0xFF181524) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0xFF2C2640) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)

    val decisionColor = when (item.decision) {
        "ATTENDING" -> Color(0xFF10B981)
        "INTERESTED" -> Color(0xFF3B82F6)
        "DECLINED" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }

    val decisionText = when (item.decision) {
        "ATTENDING" -> if (isBangla) "উপস্থিত হবেন" else "Attending"
        "INTERESTED" -> if (isBangla) "আগ্রহী" else "Interested"
        "DECLINED" -> if (isBangla) "অনিচ্ছুক" else "Declined"
        else -> if (isBangla) "অপেক্ষমান" else "Pending"
    }

    val decisionIcon = when (item.decision) {
        "ATTENDING" -> Icons.Default.CheckCircle
        "INTERESTED" -> Icons.Default.ThumbUp
        "DECLINED" -> Icons.Default.Cancel
        else -> Icons.Default.HourglassEmpty
    }

    val sentimentEmoji = when (item.sentiment) {
        "POSITIVE" -> "😊 ইতিবাচক"
        "NEGATIVE" -> "🙁 নেতিবাচক"
        else -> "😐 নিরপেক্ষ"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Customer Name, Phone & Decision Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BrandPurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = BrandPurple, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = item.customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textPrimary
                        )
                        Text(
                            text = item.customerPhone,
                            fontSize = 11.5.sp,
                            color = textSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = decisionColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, decisionColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(decisionIcon, null, tint = decisionColor, modifier = Modifier.size(13.dp))
                        Text(
                            text = decisionText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = decisionColor
                        )
                    }
                }
            }

            // Row 2: Campaign Title & Sentiment
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isDarkMode) Color(0xFF231E33) else Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Campaign, null, tint = BrandPurple, modifier = Modifier.size(14.dp))
                        Text(
                            text = item.campaignTitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = sentimentEmoji,
                        fontSize = 10.5.sp,
                        color = textSecondary
                    )
                }
            }

            // Row 3: Verbatim Customer Spoken Feedback
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDarkMode) Color(0xFF1E1A2C) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, cardBorder.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.FormatQuote,
                        null,
                        tint = BrandPurple.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (item.feedbackText.isNotBlank()) item.feedbackText else (if (isBangla) "কোনো মতামত পাওয়া যায়নি" else "No feedback recorded"),
                        fontSize = 12.sp,
                        color = textPrimary,
                        lineHeight = 17.sp
                    )
                }
            }

            // Row 4: Duration, Time & Quick Call
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, null, tint = textSecondary, modifier = Modifier.size(12.dp))
                        Text(item.callDuration, fontSize = 11.sp, color = textSecondary)
                    }
                    Text("•", fontSize = 10.sp, color = textSecondary)
                    Text(item.createdAt, fontSize = 11.sp, color = textSecondary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.customerPhone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.customerPhone}"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "কল করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Phone, null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Component: AI Feedback Detail Dialog
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun CampaignFeedbackDetailDialog(
    item: AiCampaignFeedbackItem,
    isDarkMode: Boolean,
    isBangla: Boolean,
    onDismiss: () -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)

    val decisionColor = when (item.decision) {
        "ATTENDING" -> Color(0xFF10B981)
        "INTERESTED" -> Color(0xFF3B82F6)
        "DECLINED" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }

    val decisionText = when (item.decision) {
        "ATTENDING" -> if (isBangla) "উপস্থিত হবেন (Attending)" else "Attending"
        "INTERESTED" -> if (isBangla) "আগ্রহী (Interested)" else "Interested"
        "DECLINED" -> if (isBangla) "অনিচ্ছুক (Declined)" else "Declined"
        else -> if (isBangla) "অপেক্ষমান (Pending)" else "Pending"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.RecordVoiceOver, null, tint = BrandPurple)
                Text(
                    text = if (isBangla) "গ্রাহক ফিডব্যাক বিবরণ" else "Customer Feedback Details",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = decisionColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, decisionColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isBangla) "এআই সনাক্তকৃত সিদ্ধান্ত:" else "AI Detected Decision:",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                        Text(
                            text = decisionText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = decisionColor
                        )
                    }
                }

                Text(
                    text = "${if (isBangla) "গ্রাহক:" else "Customer:"} ${item.customerName} (${item.customerPhone})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )

                Text(
                    text = "${if (isBangla) "ক্যাম্পেইন:" else "Campaign:"} ${item.campaignTitle}",
                    fontSize = 12.sp,
                    color = textSecondary
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkMode) Color(0xFF231E33) else Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isBangla) "🗣️ গ্রাহকের মূল মন্তব্য:" else "🗣️ Spoken Feedback:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPurple
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.feedbackText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = textPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${if (isBangla) "কল সময়কাল:" else "Duration:"} ${item.callDuration}",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                    Text(
                        text = "${if (isBangla) "তারিখ:" else "Date:"} ${item.createdAt}",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBangla) "বন্ধ করুন" else "Close")
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Component: AI Mass Campaign Broadcast Dialog
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun CampaignBroadcastDialog(
    isDarkMode: Boolean,
    isBangla: Boolean,
    isLoading: Boolean,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()

    var campaignType by remember { mutableStateOf("MEETING_INVITE") } // "MEETING_INVITE", "DISCOUNT_OFFER", "GENERAL"
    var title by remember { mutableStateOf("বার্ষিক মার্চেন্ট ও গ্রাহক সম্মেলন ২০২৬") }
    var script by remember {
        mutableStateOf("আসসালামু আলাইকুম {customer_name}, {business_name} থেকে বলছি। আমাদের আগামী ব্যবসায়িক সম্মেলনে আপনাকে সাদর আমন্ত্রণ জানাচ্ছি। আপনি কি উক্ত অনুষ্ঠানে সশরীরে উপস্থিত থাকতে পারবেন?")
    }
    var audienceMode by remember { mutableStateOf("ALL_DEBTORS") } // "ALL_DEBTORS", "ALL_CUSTOMERS", "SAMPLE_TEST"
    var isBroadcasting by remember { mutableStateOf(false) }

    val activeRecipients = remember(audienceMode, customers) {
        when (audienceMode) {
            "ALL_DEBTORS" -> {
                val list = customers.filter { it.currentBalance > 0 }.map { it.name to it.phone }
                if (list.isNotEmpty()) list else listOf("কামাল হোসেন" to "+8801711223344", "রহিম শেখ" to "+8801822334455")
            }
            "ALL_CUSTOMERS" -> {
                val list = customers.map { it.name to it.phone }
                if (list.isNotEmpty()) list else listOf("কামাল হোসেন" to "+8801711223344", "সালমা বেগম" to "+8801644556677", "ফারুক আহমেদ" to "+8801933445566")
            }
            else -> {
                listOf("নমুনা টেস্ট কাস্টমার" to "+8801711998877")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBroadcasting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Campaign, null, tint = BrandPurple)
                Text(
                    text = if (isBangla) "গণ এআই ভয়েস ক্যাম্পেইন ব্রডকাস্ট" else "Mass Voice Campaign Broadcast",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isBangla)
                        "এআই স্বয়ংক্রিয়ভাবে প্রতিটি কাস্টমারকে ফোন করবে, মিটিংয়ে আমন্ত্রণ জানাবে বা ডিসকাউন্ট অফার শেয়ার করবে এবং গ্রাহকের মতামত রেকর্ড করে স্প্রেডশিটে জমা করবে।"
                    else
                        "AI calls each customer, announces your meeting or discount offer, and records their verbal feedback into a spreadsheet.",
                    fontSize = 11.5.sp,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)
                )

                // 1. Campaign Type Selection
                Text(
                    text = if (isBangla) "ক্যাম্পেইনের ধরণ নির্বাচন করুন:" else "Select Campaign Type:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Meeting Invite
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                campaignType = "MEETING_INVITE"
                                title = "বার্ষিক মার্চেন্ট ও গ্রাহক সম্মেলন ২০২৬"
                                script = "আসসালামু আলাইকুম {customer_name}, {business_name} থেকে বলছি। আমাদের আগামী ব্যবসায়িক সম্মেলনে আপনাকে সাদর আমন্ত্রণ জানাচ্ছি। আপনি কি উক্ত অনুষ্ঠানে সশরীরে উপস্থিত থাকতে পারবেন?"
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (campaignType == "MEETING_INVITE") BrandPurple.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (campaignType == "MEETING_INVITE") BrandPurple else Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Groups, null, tint = if (campaignType == "MEETING_INVITE") BrandPurple else Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (isBangla) "মিটিং আমন্ত্রণ" else "Meeting", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Discount Offer
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                campaignType = "DISCOUNT_OFFER"
                                title = "বৈশাখী মেগা ২৫% ডিসকাউন্ট ক্যাম্পেইন"
                                script = "আসসালামু আলাইকুম {customer_name}, {business_name} থেকে একটি বিশেষ উপহার! আমাদের প্রতিষ্ঠানে চলছে মেগা ২৫% ছাড়। আপনি কি অফারের নতুন ক্যাটালগ দেখতে চান?"
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (campaignType == "DISCOUNT_OFFER") BrandPurple.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (campaignType == "DISCOUNT_OFFER") BrandPurple else Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LocalOffer, null, tint = if (campaignType == "DISCOUNT_OFFER") BrandPurple else Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (isBangla) "ডিসকাউন্ট অফার" else "Discount", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // General
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                campaignType = "GENERAL"
                                title = "দোকানের জরুরি নোটিশ"
                                script = "আসসালামু আলাইকুম {customer_name}, {business_name} থেকে একটি জরুরি নোটিশ ও শুভেচ্ছা বার্তা জানাতে ফোন করেছি..."
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (campaignType == "GENERAL") BrandPurple.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (campaignType == "GENERAL") BrandPurple else Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Campaign, null, tint = if (campaignType == "GENERAL") BrandPurple else Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (isBangla) "ঘোষণা বার্তা" else "Notice", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. Campaign Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isBangla) "ক্যাম্পেইন শিরোনাম" else "Campaign Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // 3. AI Voice Script Field
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it },
                    label = { Text(if (isBangla) "এআই কথা বলার স্ক্রিপ্ট (বাংলা)" else "AI Voice Script") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 5
                )

                // 4. Audience Target Selection
                Text(
                    text = if (isBangla) "গ্রাহক প্রাপক তালিকা (${activeRecipients.size} জন):" else "Audience Target (${activeRecipients.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (audienceMode == "ALL_DEBTORS") BrandPurple else Color.Transparent,
                        border = BorderStroke(1.dp, if (audienceMode == "ALL_DEBTORS") BrandPurple else Color.Gray.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { audienceMode = "ALL_DEBTORS" }
                    ) {
                        Text(
                            text = if (isBangla) "বকেয়া গ্রাহক" else "All Debtors",
                            fontSize = 11.sp,
                            color = if (audienceMode == "ALL_DEBTORS") Color.White else Color.Gray,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (audienceMode == "ALL_CUSTOMERS") BrandPurple else Color.Transparent,
                        border = BorderStroke(1.dp, if (audienceMode == "ALL_CUSTOMERS") BrandPurple else Color.Gray.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { audienceMode = "ALL_CUSTOMERS" }
                    ) {
                        Text(
                            text = if (isBangla) "সকল গ্রাহক" else "All Customers",
                            fontSize = 11.sp,
                            color = if (audienceMode == "ALL_CUSTOMERS") Color.White else Color.Gray,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (audienceMode == "SAMPLE_TEST") BrandPurple else Color.Transparent,
                        border = BorderStroke(1.dp, if (audienceMode == "SAMPLE_TEST") BrandPurple else Color.Gray.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { audienceMode = "SAMPLE_TEST" }
                    ) {
                        Text(
                            text = if (isBangla) "টেস্ট রান (১ জন)" else "Test Run (1)",
                            fontSize = 11.sp,
                            color = if (audienceMode == "SAMPLE_TEST") Color.White else Color.Gray,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() || script.isBlank()) {
                        Toast.makeText(context, if (isBangla) "শিরোনাম ও স্ক্রিপ্ট পূরণ করুন" else "Please fill title & script", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isBroadcasting = true
                    viewModel.startMassVoiceCampaign(
                        title = title,
                        type = campaignType,
                        script = script,
                        recipients = activeRecipients
                    ) { success, msg ->
                        isBroadcasting = false
                        onDismiss()
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isBroadcasting && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isBroadcasting || isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) "কলিং চলছে..." else "Calling...", fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.PhoneCallback, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) "এআই কল ব্রডকাস্ট শুরু" else "Start AI Broadcast", fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            if (!isBroadcasting) {
                TextButton(onClick = onDismiss) {
                    Text(if (isBangla) "বাতিল" else "Cancel")
                }
            }
        }
    )
}

