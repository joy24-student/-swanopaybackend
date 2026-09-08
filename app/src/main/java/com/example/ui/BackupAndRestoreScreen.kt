package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.BackupManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BackupAndRestoreScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark by viewModel.isDarkMode.collectAsState()
    SideEffect { isDarkModeGlobal = isDark }

    // Real state from ViewModel
    val storageStats by viewModel.storageStats.collectAsState()
    val backupHistory by viewModel.backupHistory.collectAsState()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val backupScheduleFrequency by viewModel.backupScheduleFrequency.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val activeSupabaseProfile by viewModel.activeSupabaseProfile.collectAsState()
    val lastBackupTimestampMs by viewModel.lastBackupTimestamp.collectAsState()

    // Local UI states
    var isBackingUpNow by remember { mutableStateOf(false) }
    var isRestoringNow by remember { mutableStateOf(false) }
    var showHistoryModal by remember { mutableStateOf(false) }
    var showScheduleModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showRestoreConfirmModal by remember { mutableStateOf(false) }
    var restoreTargetSnapshot by remember { mutableStateOf<BackupManager.BackupSnapshotInfo?>(null) }
    var isCloudRestoreSelected by remember { mutableStateOf(true) }
    var showManageStorageModal by remember { mutableStateOf(false) }

    // Load real metrics on mount
    LaunchedEffect(Unit) {
        viewModel.loadBackupHistory(context)
        viewModel.loadStorageStatistics(context)
    }

    val lastBackupTime = remember(lastBackupTimestampMs) {
        if (lastBackupTimestampMs == 0L) "Never"
        else SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(lastBackupTimestampMs))
    }

    val nextBackupTime = remember(backupScheduleFrequency, autoBackupEnabled) {
        BackupManager.calculateNextBackupTime(backupScheduleFrequency, autoBackupEnabled)
    }

    val isBackupUpToDate = remember(lastBackupTimestampMs) {
        lastBackupTimestampMs > 0L && (System.currentTimeMillis() - lastBackupTimestampMs < 86_400_000L * 2)
    }

    // Color tokens matching rich enterprise design
    val screenBg = if (isDark) Color(0xFF0F1117) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF1C1F2B) else Color.White
    val cardBorder = if (isDark) Color(0xFF2D3243) else Color(0xFFF1F5F9)
    val primaryText = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val goldAccent = Color(0xFFF59E0B)
    val goldBgSoft = if (isDark) Color(0xFF382A0F) else Color(0xFFFEF3C7)
    val goldText = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // Subtle Background Wave Graphic
        if (!isDark) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val path = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.7f)
                    quadraticTo(
                        size.width * 0.5f, size.height * 1.1f,
                        0f, size.height * 0.4f
                    )
                    lineTo(0f, 0f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF1F5F9).copy(alpha = 0.6f), Color.Transparent)
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navBarBottom + 90.dp)
        ) {
            // 1. STICKY HEADER WITH SAFEAREA INSETS
            stickyHeader {
                Surface(
                    color = screenBg,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(cardBg)
                                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                        .clickable { viewModel.navigateTo("More") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = primaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "Backup & Restore",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "Secure your business data & snapshots",
                                        fontSize = 12.sp,
                                        color = secondaryText
                                    )
                                }
                            }

                            // History Icon Action
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                    .clickable { showHistoryModal = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = "Backup History",
                                    tint = primaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. BACKUP STATUS CARD (100% REAL LIVE METRICS)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Top Row: Icon + Title + Live Status Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(goldBgSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    tint = goldAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Backup Status",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    val badgeBg = when {
                                        isBackupUpToDate -> if (isDark) Color(0xFF14532D) else Color(0xFFDCFCE7)
                                        lastBackupTimestampMs > 0L -> if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
                                        else -> if (isDark) Color(0xFF4C0519) else Color(0xFFFFE4E6)
                                    }
                                    val badgeTextColor = when {
                                        isBackupUpToDate -> if (isDark) Color(0xFF86EFAC) else Color(0xFF166534)
                                        lastBackupTimestampMs > 0L -> if (isDark) Color(0xFFFDE047) else Color(0xFFB45309)
                                        else -> if (isDark) Color(0xFFFDA4AF) else Color(0xFFBE123C)
                                    }
                                    val badgeText = when {
                                        isBackupUpToDate -> "Up to date"
                                        lastBackupTimestampMs > 0L -> "Backup Due"
                                        else -> "Action Needed"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(badgeBg)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeTextColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (isBackupUpToDate) "Your data is backed up and synced safely." else "Perform a manual or cloud backup to protect your records.",
                                    fontSize = 12.sp,
                                    color = secondaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Middle Row: 3 Live Metrics with Dividers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Last Backup
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Last Backup", fontSize = 11.sp, color = secondaryText)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = secondaryText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = lastBackupTime,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .width(1.dp)
                                    .background(cardBorder)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Next Backup
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Next Backup", fontSize = 11.sp, color = secondaryText)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = secondaryText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = nextBackupTime,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .width(1.dp)
                                    .background(cardBorder)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // App / DB Size
                            Column(modifier = Modifier.weight(0.9f)) {
                                Text("Database Size", fontSize = 11.sp, color = secondaryText)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Storage,
                                        contentDescription = null,
                                        tint = secondaryText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = storageStats?.databaseSizeFormatted ?: "Calculating...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = cardBorder)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Bottom Row: Auto Backup Toggle Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = primaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Auto Backup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                    Text(backupScheduleFrequency, fontSize = 12.sp, color = secondaryText)
                                }
                            }

                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = {
                                    viewModel.setAutoBackup(it)
                                    Toast.makeText(
                                        context,
                                        if (it) "Auto Backup Enabled ($backupScheduleFrequency)" else "Auto Backup Disabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = goldAccent,
                                    checkedTrackColor = goldAccent.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }

            // 3. SECTION: BACKUP ACTIONS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Backup Actions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column {
                        // 1. Create Backup Now
                        BackupActionRow(
                            icon = Icons.Outlined.CloudUpload,
                            title = "Create Backup Now",
                            subtitle = if (isBackingUpNow) "Generating snapshot & syncing..." else "Create full local & cloud snapshot",
                            isLoading = isBackingUpNow,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            goldBgSoft = goldBgSoft,
                            goldAccent = goldAccent,
                            onClick = {
                                if (!isBackingUpNow) {
                                    isBackingUpNow = true
                                    viewModel.createFullBackup(context) { success, msg ->
                                        isBackingUpNow = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)

                        // 2. Backup Schedule
                        BackupActionRow(
                            icon = Icons.Outlined.AccessTime,
                            title = "Backup Schedule",
                            subtitle = "Frequency: $backupScheduleFrequency",
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            goldBgSoft = goldBgSoft,
                            goldAccent = goldAccent,
                            onClick = { showScheduleModal = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)

                        // 3. Backup History
                        BackupActionRow(
                            icon = Icons.Outlined.Storage,
                            title = "Backup History",
                            subtitle = "${backupHistory.size} snapshot(s) available on device",
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            goldBgSoft = goldBgSoft,
                            goldAccent = goldAccent,
                            onClick = { showHistoryModal = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)

                        // 4. Backup & Sync Settings
                        BackupActionRow(
                            icon = Icons.Outlined.Settings,
                            title = "Backup & Cloud Settings",
                            subtitle = if (activeSupabaseProfile?.supabaseUrl.isNullOrEmpty()) "Cloud: Offline / Local mode" else "Cloud: Connected (${activeSupabaseProfile?.businessName})",
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            goldBgSoft = goldBgSoft,
                            goldAccent = goldAccent,
                            onClick = { showSettingsModal = true }
                        )
                    }
                }
            }

            // 4. SECTION: STORAGE OVERVIEW (100% REAL STORAGE METRICS)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Storage Overview",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }
            }

            item {
                val stats = storageStats
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Donut Ring Chart with Real Data Ratio
                            val appSizeStr = stats?.totalAppFormatted ?: "0 KB"
                            val arcSweep = ((stats?.usedPercentage ?: 5f) * 3.6f).coerceIn(15f, 360f)

                            Box(
                                modifier = Modifier.size(116.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 20f
                                    // Background Track Ring
                                    drawArc(
                                        color = if (isDark) Color(0xFF2E3444) else Color(0xFFF1F5F9),
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    // Active Yellow Used Arc Segment
                                    drawArc(
                                        color = goldAccent,
                                        startAngle = -90f,
                                        sweepAngle = arcSweep,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = appSizeStr,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "App Storage",
                                        fontSize = 10.sp,
                                        color = secondaryText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Right Storage Legend List (Real file statistics)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StorageLegendRow(
                                    color = goldAccent,
                                    label = "Room Database",
                                    size = stats?.databaseSizeFormatted ?: "0 KB",
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )
                                StorageLegendRow(
                                    color = Color(0xFFFB923C),
                                    label = "Snapshots & Files",
                                    size = stats?.mediaSizeFormatted ?: "0 KB",
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )
                                StorageLegendRow(
                                    color = Color(0xFFFDE68A),
                                    label = "App Cache & Logs",
                                    size = stats?.cacheSizeFormatted ?: "0 KB",
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )
                                StorageLegendRow(
                                    color = Color(0xFF10B981),
                                    label = "Free Disk Space",
                                    size = stats?.freeDiskFormatted ?: "50 GB",
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = cardBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Manage Storage Clickable Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showManageStorageModal = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(goldBgSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderSpecial,
                                        contentDescription = null,
                                        tint = goldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text("Manage Storage", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                    Text("Clear temp cache & view disk breakdown", fontSize = 12.sp, color = secondaryText)
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Manage Storage",
                                tint = primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 5. SECTION: RESTORE DATA
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Restore Data",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Restore from Cloud Action Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isCloudRestoreSelected = true
                                    restoreTargetSnapshot = null
                                    showRestoreConfirmModal = true
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(goldBgSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = goldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text("Restore from Cloud (Supabase)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                    Text("Pull latest database records from cloud endpoint", fontSize = 12.sp, color = secondaryText)
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Restore Cloud",
                                tint = primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = cardBorder)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Restore from Local Snapshot Action Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showHistoryModal = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEEF2FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text("Restore from Local Snapshot File", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                    Text("Select a saved .json database backup file", fontSize = 12.sp, color = secondaryText)
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Restore Local",
                                tint = primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Soft Amber Warning Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color(0xFF382A0F) else Color(0xFFFEF3C7))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = goldText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Restoring data will merge & update your current database. Proceed with confirmation.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = goldText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── MODAL: BACKUP HISTORY (REAL FILES FROM STORAGE) ──────────────────────
    if (showHistoryModal) {
        Dialog(onDismissRequest = { showHistoryModal = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Backup History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
                            Text("${backupHistory.size} saved backups on device", fontSize = 11.sp, color = secondaryText)
                        }
                        IconButton(onClick = { showHistoryModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = secondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (backupHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOff,
                                    contentDescription = null,
                                    tint = secondaryText,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No backup snapshots created yet", fontSize = 13.sp, color = secondaryText)
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        showHistoryModal = false
                                        isBackingUpNow = true
                                        viewModel.createFullBackup(context) { success, msg ->
                                            isBackingUpNow = false
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = goldAccent, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Create First Backup Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(backupHistory) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(screenBg)
                                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.formattedDate, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                        Text("${item.type} • ${item.formattedSize} • ${item.totalRecords} records", fontSize = 11.sp, color = secondaryText)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Share Action
                                        IconButton(
                                            onClick = { viewModel.shareBackupFile(context, item) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = goldAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Restore Action
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(goldBgSoft)
                                                .clickable {
                                                    showHistoryModal = false
                                                    isCloudRestoreSelected = false
                                                    restoreTargetSnapshot = item
                                                    showRestoreConfirmModal = true
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = goldText)
                                        }

                                        // Delete Action
                                        IconButton(
                                            onClick = { viewModel.deleteBackupFile(context, item.fileName) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
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

    // ── MODAL: RESTORE CONFIRMATION (REAL RESTORE EXECUTION) ─────────────────
    if (showRestoreConfirmModal) {
        Dialog(onDismissRequest = { if (!isRestoringNow) showRestoreConfirmModal = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = goldAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isCloudRestoreSelected) "Confirm Cloud Restore" else "Confirm Snapshot Restore",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val restoreMessage = if (isCloudRestoreSelected) {
                        "Restoring from Supabase cloud will pull all customer accounts, inventory, sales, expenses, and ledger entries down into your local Room database."
                    } else {
                        val name = restoreTargetSnapshot?.formattedDate ?: "selected file"
                        "Restoring from snapshot '$name' will import ${restoreTargetSnapshot?.totalRecords ?: 0} records across all database tables."
                    }

                    Text(
                        text = restoreMessage,
                        fontSize = 13.sp,
                        color = secondaryText
                    )

                    if (isRestoringNow) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = goldAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Applying database transactions...",
                            fontSize = 11.sp,
                            color = goldAccent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showRestoreConfirmModal = false },
                            enabled = !isRestoringNow,
                            colors = ButtonDefaults.buttonColors(containerColor = cardBorder, contentColor = primaryText),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                isRestoringNow = true
                                if (isCloudRestoreSelected) {
                                    viewModel.restoreFromCloudSupabase(context) { success, msg ->
                                        isRestoringNow = false
                                        showRestoreConfirmModal = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    val snapshot = restoreTargetSnapshot
                                    if (snapshot != null) {
                                        val file = java.io.File(snapshot.filePath)
                                        if (file.exists()) {
                                            viewModel.restoreFromLocalJson(context, file.readText()) { success, msg ->
                                                isRestoringNow = false
                                                showRestoreConfirmModal = false
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            isRestoringNow = false
                                            Toast.makeText(context, "Snapshot file not found on disk", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isRestoringNow,
                            colors = ButtonDefaults.buttonColors(containerColor = goldAccent, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isRestoringNow) "Restoring..." else "Restore Now",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // ── MODAL: BACKUP SETTINGS & SUPABASE DETAILS ────────────────────────────
    if (showSettingsModal) {
        Dialog(onDismissRequest = { showSettingsModal = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Backup & Sync Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
                        IconButton(onClick = { showSettingsModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = secondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Active Merchant Profile:", fontSize = 11.sp, color = secondaryText)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(activeProfile.businessName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryText)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Configured Supabase Cloud Endpoint:", fontSize = 11.sp, color = secondaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    val endpointUrl = activeSupabaseProfile?.supabaseUrl?.ifEmpty { "Not Connected (Offline Mode)" } ?: "Not Connected (Offline Mode)"
                    Text(
                        text = endpointUrl,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (endpointUrl.startsWith("http")) goldAccent else secondaryText
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showSettingsModal = false
                            viewModel.navigateTo("SupabaseSetupGuide")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = goldAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Cloud Connection Wizard", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ── MODAL: BACKUP SCHEDULE (REAL PERSISTENCE) ────────────────────────────
    if (showScheduleModal) {
        Dialog(onDismissRequest = { showScheduleModal = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Backup Schedule", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        "Daily at 11:30 PM (Recommended)",
                        "Daily at 02:00 AM",
                        "Weekly on Sunday midnight",
                        "Monthly on 1st day",
                        "Manual Only"
                    ).forEach { freq ->
                        val isSelected = backupScheduleFrequency == freq
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) goldBgSoft.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable {
                                    viewModel.setBackupSchedule(freq)
                                    Toast.makeText(context, "Schedule updated: $freq", Toast.LENGTH_SHORT).show()
                                    showScheduleModal = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) goldAccent else secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(freq, fontSize = 13.sp, color = primaryText, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // ── MODAL: MANAGE STORAGE (REAL CACHE CLEARING) ──────────────────────────
    if (showManageStorageModal) {
        Dialog(onDismissRequest = { showManageStorageModal = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Manage Storage", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
                        IconButton(onClick = { showManageStorageModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = secondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val stats = storageStats
                    Text("SQLite Database: ${stats?.databaseSizeFormatted ?: "0 KB"}", fontSize = 13.sp, color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Backup Snapshots & Exports: ${stats?.mediaSizeFormatted ?: "0 KB"}", fontSize = 13.sp, color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Temporary Cache: ${stats?.cacheSizeFormatted ?: "0 KB"}", fontSize = 13.sp, color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Free Device Storage: ${stats?.freeDiskFormatted ?: "50 GB"}", fontSize = 13.sp, color = secondaryText)

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            viewModel.clearAppCache(context) { freed ->
                                Toast.makeText(context, "Cache cleared! Freed $freed storage space.", Toast.LENGTH_SHORT).show()
                                showManageStorageModal = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = goldAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Temporary Cache", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── COMPONENT: Backup Action Row Item ──────────────────────────────────────
@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    primaryText: Color,
    secondaryText: Color,
    goldBgSoft: Color,
    goldAccent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(goldBgSoft),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = goldAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open",
            tint = primaryText,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── COMPONENT: Storage Legend Row Item ─────────────────────────────────────
@Composable
private fun StorageLegendRow(
    color: Color,
    label: String,
    size: String,
    primaryText: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = secondaryText,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = size,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = primaryText,
            maxLines = 1,
            softWrap = false
        )
    }
}
