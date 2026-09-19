@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import android.content.Intent
import android.net.Uri

// ── Models for Subscription, Dynamic Pricing & Billing ──

data class SubscriptionPlanUi(
    val planKey: String, // "MONTHLY" | "QUARTERLY" | "YEARLY"
    val titleBn: String,
    val titleEn: String,
    val priceBdt: Int,
    val originalPriceBdt: Int? = null,
    val durationTextBn: String,
    val durationDays: Int,
    val discountBadge: String? = null,
    val isPopular: Boolean = false,
    val isBestValue: Boolean = false,
    val gradientColors: List<Color>
)

data class SubscriptionStatusState(
    val status: String = "TRIAL", // "ACTIVE", "TRIAL", "EXPIRED", "REQUIRES_NID"
    val canAccessService: Boolean = true,
    val lockReason: String? = null,
    val hasNid: Boolean = true,
    val nidNumber: String? = null,
    val isKycVerified: Boolean = true,
    val isSubscriptionActive: Boolean = false,
    val subscriptionPlan: String? = "FREE_TRIAL",
    val subscriptionExpiresAt: String? = null,
    val isTrialActive: Boolean = true,
    val trialDaysTotal: Int = 90,
    val trialRemainingDays: Int = 90,
    val trialEndsAt: String? = null,
    val monthlyPrice: Double = 100.0,
    val quarterlyPrice: Double = 250.0,
    val yearlyPrice: Double = 650.0
)

data class SubscriptionCheckoutState(
    val orderId: String = "",
    val planType: String = "MONTHLY",
    val amount: Double = 100.0,
    val days: Int = 30,
    val currency: String = "BDT",
    val paymentMethod: String = "bKash",
    val receivingAccount: String = "01711223344",
    val nidAssociated: String? = null,
    val instructions: String = "",
    val checkoutUrl: String? = null
)

data class SubscriptionPaymentHistoryItem(
    val id: String = "",
    val merchantId: String = "",
    val nidNumber: String? = null,
    val planType: String = "MONTHLY",
    val amount: Double = 0.0,
    val trxId: String? = null,
    val paymentMethod: String = "bKash",
    val status: String = "COMPLETED",
    val createdAt: String = "",
    val verifiedAt: String? = null
)

data class AdminNoticePopup(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = "INFO", // "INFO", "WARNING", "ERROR", "SUCCESS"
    val type: String = "ANNOUNCEMENT", // "ANNOUNCEMENT", "ALERT", "SYSTEM", "PROMOTION"
    val timestamp: Long = System.currentTimeMillis(),
    val isDismissible: Boolean = true
)

/**
 * Main Subscription & Anti-Piracy Billing Paywall Screen
 */
@Composable
fun SubscriptionScreen(
    viewModel: AppViewModel,
    isDismissible: Boolean = true
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark by viewModel.isDarkMode.collectAsState()

    val subStatus by viewModel.subscriptionStatus.collectAsState()
    val isLoading by viewModel.isSubscriptionLoading.collectAsState()
    val subHistory by viewModel.subscriptionHistory.collectAsState()
    val isHistoryLoading by viewModel.isSubscriptionHistoryLoading.collectAsState()

    var selectedPlanForCheckout by remember { mutableStateOf<SubscriptionPlanUi?>(null) }
    var activeCheckoutOrder by remember { mutableStateOf<SubscriptionCheckoutState?>(null) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var isVerifyingTrx by remember { mutableStateOf(false) }
    var userEnteredTrxId by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("bKash") }
    var successCelebrationMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchSubscriptionStatus()
        viewModel.fetchSubscriptionHistory()
    }

    val plans = remember(subStatus) {
        listOf(
            SubscriptionPlanUi(
                planKey = "MONTHLY",
                titleBn = "মাসিক প্ল্যান",
                titleEn = "Monthly Plan",
                priceBdt = subStatus.monthlyPrice.toInt(),
                durationTextBn = "১ মাস (৩০ দিন)",
                durationDays = 30,
                gradientColors = listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
            ),
            SubscriptionPlanUi(
                planKey = "QUARTERLY",
                titleBn = "ত্রৈমাসিক প্ল্যান",
                titleEn = "Quarterly Plan",
                priceBdt = subStatus.quarterlyPrice.toInt(),
                originalPriceBdt = (subStatus.monthlyPrice * 3).toInt(),
                durationTextBn = "৩ মাস (৯০ দিন)",
                durationDays = 90,
                discountBadge = "জনপ্রিয় • ৫০ ৳ সাশ্রয়",
                isPopular = true,
                gradientColors = listOf(Color(0xFF059669), Color(0xFF10B981))
            ),
            SubscriptionPlanUi(
                planKey = "YEARLY",
                titleBn = "বার্ষিক মেম্বারশিপ",
                titleEn = "Yearly VIP Plan",
                priceBdt = subStatus.yearlyPrice.toInt(),
                originalPriceBdt = (subStatus.monthlyPrice * 12).toInt(),
                durationTextBn = "১ বছর (৩৬৫ দিন)",
                durationDays = 365,
                discountBadge = "সেরা অফার • ৪৫% ছাড়!",
                isBestValue = true,
                gradientColors = listOf(Color(0xFFD97706), Color(0xFFF59E0B))
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SwapnoPay সাবস্ক্রিপশন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "প্রফেশনাল বিজনেস ও পেমেন্ট অটোমেশন",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    if (isDismissible) {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchSubscriptionStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF0F172A) else Color.White
                )
            )
        },
        containerColor = if (isDark) Color(0xFF090D16) else Color(0xFFF8FAFC)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── Paywall Notice if Service Locked ──
            if (!subStatus.canAccessService) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF3B1219) else Color(0xFFFEF2F2)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFF87171)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (subStatus.status == "REQUIRES_NID") "NID ভেরিফিকেশন সম্পন্ন করুন" else "সাবস্ক্রিপশনের মেয়াদ শেষ হয়েছে",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subStatus.lockReason ?: "সেবা অব্যাহত রাখতে সাবস্ক্রিপশন প্ল্যান নির্বাচন করুন।",
                                    fontSize = 13.sp,
                                    color = if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C)
                                )
                                if (subStatus.status == "REQUIRES_NID") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.navigateTo("KycVerification") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text("এখনই NID ভেরিফাই করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Current Membership Status Hero Card ──
            item {
                val heroGradient = when (subStatus.status) {
                    "ACTIVE" -> listOf(Color(0xFF065F46), Color(0xFF047857))
                    "TRIAL" -> listOf(Color(0xFF4338CA), Color(0xFF6366F1))
                    "REQUIRES_NID" -> listOf(Color(0xFF9F1239), Color(0xFFBE123C))
                    else -> listOf(Color(0xFF7F1D1D), Color(0xFF991B1B))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(heroGradient))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (subStatus.status == "ACTIVE") Icons.Default.WorkspacePremium else Icons.Default.CardGiftcard,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "SWAPNOPAY PRO",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = when (subStatus.status) {
                                                "ACTIVE" -> "প্রিমিয়াম অ্যাকাউন্ট সক্রিয়"
                                                "TRIAL" -> "৩ মাস ফ্রি ট্রায়াল মোড"
                                                "REQUIRES_NID" -> "NID ভেরিফিকেশন মুলতুবি"
                                                else -> "সাবস্ক্রিপশন ফি অপরিশোধিত"
                                            },
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = subStatus.status,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Details row inside hero card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = if (subStatus.status == "TRIAL") "ট্রায়ালের বাকি সময়" else "বর্তমান প্ল্যান",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = if (subStatus.status == "TRIAL") "${subStatus.trialRemainingDays} দিন বাকি" else (subStatus.subscriptionPlan ?: "TRIAL"),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "যুক্তকৃত NID স্ট্যাটাস",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = if (subStatus.hasNid || subStatus.isKycVerified) (subStatus.nidNumber ?: "ভেরিফাইড") else "যুক্ত নেই (আবশ্যক)",
                                        color = if (subStatus.hasNid || subStatus.isKycVerified) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "🛡️ অ্যান্টি-অ্যাবিউজ পলিসি: ১টি জাতীয় পরিচয়পত্র (NID) দিয়ে কেবল ১টি অ্যাকাউন্ট ভেরিফাই ও ব্যবহার করা যাবে।",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // ── Section Title: Choose Your Plan (3D Gallery Carousel) ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ViewCarousel,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "সাবস্ক্রিপশন প্ল্যান সমূহ",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = "ডানে ও বাঁয়ে সোয়াইপ করে পছন্দের প্ল্যান বেছে নিন",
                            fontSize = 12.5.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }

                    Surface(
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "৩টি প্ল্যান ➔",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── Modern 3D Gallery Style Horizontal Carousel ──
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    items(plans) { plan ->
                        Card(
                            modifier = Modifier
                                .width(295.dp)
                                .shadow(
                                    elevation = if (plan.isPopular || plan.isBestValue) 8.dp else 4.dp,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clickable {
                                    selectedPlanForCheckout = plan
                                    viewModel.checkoutSubscription(
                                        planType = plan.planKey,
                                        paymentMethod = selectedMethod
                                    ) { success, checkoutState, error ->
                                        if (success && checkoutState != null) {
                                            activeCheckoutOrder = checkoutState
                                            showCheckoutDialog = true
                                        } else {
                                            Toast.makeText(context, error ?: "চেকআউট শুরু করতে ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                            ),
                            border = BorderStroke(
                                width = if (plan.isPopular || plan.isBestValue) 2.dp else 1.dp,
                                color = if (plan.isPopular) Color(0xFF10B981) else if (plan.isBestValue) Color(0xFFF59E0B) else if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Column {
                                // 3D Card Header Top Banner
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Brush.horizontalGradient(plan.gradientColors))
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = plan.titleBn,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            if (plan.discountBadge != null) {
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = plan.discountBadge,
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = plan.titleEn,
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Card Content Body
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Price Row
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "৳${plan.priceBdt}",
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (plan.originalPriceBdt != null) {
                                            Text(
                                                text = "৳${plan.originalPriceBdt}",
                                                fontSize = 14.sp,
                                                color = Color(0xFF94A3B8),
                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "/ ${plan.durationTextBn}",
                                            fontSize = 12.sp,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Features List
                                    val features = when (plan.planKey) {
                                        "MONTHLY" -> listOf(
                                            "স্বয়ংক্রিয় SMS ম্যাচিং (bKash/Nagad/Rocket)",
                                            "আনলিমিটেড কাস্টমার ও বাকি খাতা লেজার",
                                            "AI ভয়েস কল ও ইনস্ট্যান্ট ইনকোয়ারি",
                                            "ক্লাউড অটো ব্যাকআপ ও মাল্টি-ডিভাইস"
                                        )
                                        "QUARTERLY" -> listOf(
                                            "মাসিক প্ল্যানের সকল ফিচার অন্তর্ভুক্ত",
                                            "৫০ টাকা নিশ্চিত ক্যাশ সেভিংস",
                                            "উন্নত AI ভয়েস ক্যাম্পেইন ও স্প্রেডশীট এক্সপোর্ট",
                                            "অগ্রাধিকারপ্রাপ্ত মার্চেন্ট টেক সাপোর্ট"
                                        )
                                        else -> listOf(
                                            "সকল প্রিমিয়াম ফিচার পুরো ১ বছরের জন্য",
                                            "সর্বোচ্চ ৪৫% সাশ্রয় (বেস্ট ভ্যালু)",
                                            "ভিআইপি অ্যাকাউন্ট ম্যানেজার ও হেল্পলাইন",
                                            "ফ্রি কাস্টম ব্র্যান্ডিং ও ডোমেন কানেকশন"
                                        )
                                    }

                                    features.forEach { feat ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = if (plan.isPopular) Color(0xFF10B981) else Color(0xFF3B82F6),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(7.dp))
                                            Text(
                                                text = feat,
                                                fontSize = 11.5.sp,
                                                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Button(
                                        onClick = {
                                            selectedPlanForCheckout = plan
                                            viewModel.checkoutSubscription(
                                                planType = plan.planKey,
                                                paymentMethod = selectedMethod
                                            ) { success, checkoutState, error ->
                                                if (success && checkoutState != null) {
                                                    activeCheckoutOrder = checkoutState
                                                    showCheckoutDialog = true
                                                } else {
                                                    Toast.makeText(context, error ?: "চেকআউট শুরু করতে ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (plan.isPopular) Color(0xFF059669) else if (plan.isBestValue) Color(0xFFD97706) else Color(0xFF4F46E5)
                                        )
                                    ) {
                                        Text(
                                            text = "আপগ্রেড করুন (৳${plan.priceBdt})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Guarantee & Anti-Piracy Security Card ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "স্বপ্নপে নিরাপদ পেমেন্ট ও এনআইডি সুরক্ষা",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "সকল পেমেন্ট সরাসরি SwapnoPay সেন্ট্রাল মার্চেন্ট গেটওয়েতে যাচাই ও সংরক্ষিত হয়। ১টি জাতীয় পরিচয়পত্র ১টি মাত্র অ্যাকাউন্টের সাথে সুরক্ষিত থাকে।",
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }
            }

            // ── Section Title: Subscription Payment History ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "সাবস্ক্রিপশন পেমেন্ট হিস্ট্রি",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            Text(
                                text = "পূর্বে পরিশোধিত সকল সাবস্ক্রিপশন লেনদেন ও ট্রানজেকশন",
                                fontSize = 11.5.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.fetchSubscriptionHistory() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh History",
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Subscription Payment History Content ──
            if (isHistoryLoading && subHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4F46E5)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "পেমেন্ট হিস্ট্রি লোড হচ্ছে...",
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else if (subHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "এখনও কোনো সাবস্ক্রিপশন পেমেন্ট রেকর্ড নেই",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "আপনার সম্পন্নকৃত সাবস্ক্রিপশন পেমেন্ট ও ট্রানজেকশন আইডি এখানে প্রদর্শিত হবে।",
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(subHistory) { hist ->
                    val methodBrandColor = when (hist.paymentMethod.lowercase()) {
                        "bkash" -> Color(0xFFE2136E)
                        "nagad" -> Color(0xFFF7941D)
                        "rocket" -> Color(0xFF8C3494)
                        else -> Color(0xFF4F46E5)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Payment Method Badge
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(methodBrandColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = hist.paymentMethod.take(1).uppercase(),
                                        color = methodBrandColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "৳${hist.amount.toInt()}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isDark) Color.White else Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = hist.planType,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    // TrxID with copy
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "TrxID: ${hist.trxId ?: "N/A"}",
                                            fontSize = 11.5.sp,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                        )
                                        if (!hist.trxId.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy TrxID",
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clickable {
                                                        clipboardManager.setText(AnnotatedString(hist.trxId))
                                                        Toast.makeText(context, "TrxID কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    },
                                                tint = Color(0xFF38BDF8)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = hist.createdAt.replace("T", " ").take(16),
                                        fontSize = 10.5.sp,
                                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Status Pill
                            Surface(
                                color = if (hist.status.uppercase() == "COMPLETED") Color(0xFF059669).copy(alpha = 0.15f) else Color(0xFFD97706).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (hist.status.uppercase() == "COMPLETED") Color(0xFF10B981) else Color(0xFFF59E0B))
                            ) {
                                Text(
                                    text = if (hist.status.uppercase() == "COMPLETED") "✓ সফল" else "⏳ অপেক্ষমান",
                                    color = if (hist.status.uppercase() == "COMPLETED") Color(0xFF10B981) else Color(0xFFF59E0B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // ── SwapnoPay Own Gateway Payment Dialog ──
    if (showCheckoutDialog && activeCheckoutOrder != null) {
        val order = activeCheckoutOrder!!
        AlertDialog(
            onDismissRequest = {
                if (!isVerifyingTrx) showCheckoutDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Payment,
                        contentDescription = null,
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "স্বপ্নপে গেটওয়ে পেমেন্ট",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "নির্বাচিত প্ল্যান: ${order.planType} (৳${order.amount.toInt()})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Payment Method Tabs
                    Text(text = "পেমেন্ট মেথড নির্বাচন করুন:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("bKash", "বিকাশ", Color(0xFFE2136E)),
                            Triple("Nagad", "নগদ", Color(0xFFF7941D)),
                            Triple("Rocket", "রকেট", Color(0xFF8C3494))
                        ).forEach { (key, label, brandColor) ->
                            val isSelected = selectedMethod == key
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedMethod = key
                                        // Refresh receiving account for selected method
                                        viewModel.checkoutSubscription(
                                            planType = order.planType,
                                            paymentMethod = key
                                        ) { _, updatedState, _ ->
                                            if (updatedState != null) activeCheckoutOrder = updatedState
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) brandColor else (if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)),
                                border = if (isSelected) BorderStroke(1.5.dp, brandColor) else null
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else (if (isDark) Color.White else Color(0xFF334155)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Receiving number container with Copy button
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFEEF2FF)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "স্বপ্নপে রিসিভিং $selectedMethod নম্বর:",
                                fontSize = 11.sp,
                                color = Color(0xFF4F46E5),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = order.receivingAccount,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDark) Color.White else Color(0xFF1E293B)
                                )
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(order.receivingAccount))
                                        Toast.makeText(context, "নম্বরটি কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("কপি", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "১. আপনার $selectedMethod অ্যাপে গিয়ে Send Money বা Payment করুন।\n" +
                               "২. টাকার পরিমাণ দিন: ৳${order.amount.toInt()}\n" +
                               "৩. পেমেন্ট শেষে প্রাপ্ত Transaction ID (TrxID) টি নিচের বক্সে দিন।",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                    )

                    if (!order.checkoutUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(order.checkoutUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ওয়েব গেটওয়ে খোলা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF4F46E5))
                        ) {
                            Icon(
                                Icons.Outlined.OpenInBrowser,
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ওয়েব গেটওয়েতে পে করুন (Web Checkout)",
                                color = Color(0xFF4F46E5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = userEnteredTrxId,
                        onValueChange = { userEnteredTrxId = it.uppercase().trim() },
                        label = { Text("Transaction ID (TrxID) লিখুন *") },
                        placeholder = { Text("যেমন: 9J76KLMNPQ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (userEnteredTrxId.length < 6) {
                            Toast.makeText(context, "সঠিক Transaction ID দিন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isVerifyingTrx = true
                        viewModel.verifySubscriptionTrx(
                            orderId = order.orderId,
                            trxId = userEnteredTrxId,
                            paymentMethod = selectedMethod,
                            planType = order.planType
                        ) { success, msg ->
                            isVerifyingTrx = false
                            if (success) {
                                showCheckoutDialog = false
                                userEnteredTrxId = ""
                                successCelebrationMsg = msg ?: "আপনার সাবস্ক্রিপশন সফলভাবে সক্রিয় হয়েছে!"
                                viewModel.fetchSubscriptionStatus()
                            } else {
                                Toast.makeText(context, msg ?: "পেমেন্ট ভেরিফিকেশন ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isVerifyingTrx && userEnteredTrxId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isVerifyingTrx) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("যাচাই হচ্ছে...")
                    } else {
                        Text("পেমেন্ট কনফার্ম করুন", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCheckoutDialog = false },
                    enabled = !isVerifyingTrx
                ) {
                    Text("বাতিল")
                }
            }
        )
    }

    // ── Success Modal ──
    successCelebrationMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { successCelebrationMsg = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("অভিনন্দন!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        successCelebrationMsg = null
                        if (!isDismissible) {
                            viewModel.navigateTo("Dashboard")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ধন্যবাদ", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

