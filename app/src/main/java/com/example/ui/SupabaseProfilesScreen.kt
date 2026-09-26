package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.SupabaseProfileEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseProfilesScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val profiles by viewModel.supabaseProfiles.collectAsState()
    val activeProfile by viewModel.activeSupabaseProfile.collectAsState()
    val isConnected by viewModel.supabaseConnected.collectAsState()

    val diagnosticSteps by viewModel.realtimeDiagnosticSteps.collectAsState()
    val isRunningDiagnostics by viewModel.isRunningRealtimeDiagnostics.collectAsState()
    val diagnosticsSuccess by viewModel.realtimeDiagnosticsOverallSuccess.collectAsState()
    val diagnosticsSummary by viewModel.realtimeDiagnosticsSummary.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<SupabaseProfileEntity?>(null) }
    var showDiagnosticsModal by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<SupabaseProfileEntity?>(null) }
    var revealAnonKey by remember { mutableStateOf(false) }

    val bgScreen = if (isDarkMode) Color(0xFF0C0C0E) else Color(0xFFF8FAFC)
    val cardBg = if (isDarkMode) Color(0xFF141418) else Color.White
    val cardBorder = if (isDarkMode) Color(0xFF27272E) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF64748B)

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Supabase Profiles",
                subtitle = "Multi-Backend Fleet & Realtime Diagnostics",
                onBack = { viewModel.goBack() },
                gradient = if (isDarkMode) listOf(Color(0xFF1E1E24), Color(0xFF141418)) else listOf(Color(0xFF4F46E5), Color(0xFF3730A3))
            ) {
                IconButton(
                    onClick = {
                        viewModel.forceSyncSupabase()
                        Toast.makeText(context, "Cloud sync triggered", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Force Sync", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        profileToEdit = null
                        showAddEditDialog = true
                    }
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Profile", tint = Color.White)
                }
            }
        },
        containerColor = bgScreen
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── ACTIVE BACKEND HERO CARD ──
            item {
                ActiveBackendHeroCard(
                    activeProfile = activeProfile,
                    isConnected = isConnected,
                    revealAnonKey = revealAnonKey,
                    onToggleRevealKey = { revealAnonKey = !revealAnonKey },
                    onCopyUrl = {
                        val url = activeProfile?.supabaseUrl.orEmpty()
                        if (url.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(url))
                            Toast.makeText(context, "Supabase URL copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopyKey = {
                        val key = activeProfile?.anonKey.orEmpty()
                        if (key.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(key))
                            Toast.makeText(context, "Anon Key copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRunDiagnostics = {
                        showDiagnosticsModal = true
                        viewModel.runRealtimeSupabaseDiagnostics()
                    },
                    onEditActive = {
                        profileToEdit = activeProfile
                        showAddEditDialog = true
                    },
                    isDarkMode = isDarkMode
                )
            }

            // ── PROFILES SECTION HEADER ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CONFIGURED BACKENDS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkMode) Color(0xFF27272E) else Color(0xFFE2E8F0)
                        ) {
                            Text(
                                text = "${profiles.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            profileToEdit = null
                            showAddEditDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── LIST OF ALL PROFILES ──
            if (profiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Backend Profiles Configured",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "Add a Supabase backend to enable multi-tenant data sync and real-time ledger operations.",
                                fontSize = 13.sp,
                                color = textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    profileToEdit = null
                                    showAddEditDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Supabase Profile")
                            }
                        }
                    }
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    val isActive = activeProfile?.id == profile.id || profile.isActive
                    ProfileItemCard(
                        profile = profile,
                        isActive = isActive,
                        onActivate = {
                            viewModel.switchProfile(profile.id)
                            Toast.makeText(context, "Switched to ${profile.businessName}", Toast.LENGTH_SHORT).show()
                        },
                        onTestDiagnostics = {
                            showDiagnosticsModal = true
                            viewModel.runRealtimeSupabaseDiagnostics(profile.supabaseUrl, profile.anonKey)
                        },
                        onEdit = {
                            profileToEdit = profile
                            showAddEditDialog = true
                        },
                        onDelete = {
                            profileToDelete = profile
                        },
                        canDelete = profiles.size > 1 && !isActive,
                        isDarkMode = isDarkMode
                    )
                }
            }

            // ── QUICK DIAGNOSTICS & SYNC BAR ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF181820) else Color(0xFFEEF2FF)
                    ),
                    border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF312E81) else Color(0xFFC7D2FE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Speed,
                                        contentDescription = null,
                                        tint = Color(0xFF6366F1),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Realtime CRUD Health",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "5-step verification: REST, insert, read, update, delete",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        Button(
                            onClick = {
                                showDiagnosticsModal = true
                                viewModel.runRealtimeSupabaseDiagnostics()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Diagnostics", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── ADD / EDIT PROFILE DIALOG ──
    if (showAddEditDialog) {
        AddEditProfileDialog(
            initialProfile = profileToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, url, key, makeActive ->
                viewModel.addOrUpdateSupabaseProfile(
                    profileId = profileToEdit?.id,
                    businessName = name,
                    url = url,
                    anonKey = key,
                    makeActive = makeActive,
                    onSuccess = {
                        showAddEditDialog = false
                        Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            isDarkMode = isDarkMode
        )
    }

    // ── DELETE CONFIRMATION DIALOG ──
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = {
                Text(
                    text = "Delete Backend Profile?",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${profile.businessName}'? This will remove saved credentials from this device. Remote database tables will remain intact.",
                    color = textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = profile.id
                        profileToDelete = null
                        viewModel.deleteSupabaseProfile(
                            profileId = id,
                            onSuccess = {
                                Toast.makeText(context, "Profile deleted", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = cardBg
        )
    }

    // ── LIVE DIAGNOSTICS MODAL DIALOG ──
    if (showDiagnosticsModal) {
        LiveDiagnosticsModal(
            steps = diagnosticSteps,
            isRunning = isRunningDiagnostics,
            overallSuccess = diagnosticsSuccess,
            summary = diagnosticsSummary,
            onRunAgain = { viewModel.runRealtimeSupabaseDiagnostics() },
            onDismiss = { showDiagnosticsModal = false },
            onCopyLogs = {
                val report = buildString {
                    appendLine("=== SWAPNOPAY REAL-TIME CRUD DIAGNOSTIC REPORT ===")
                    appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                    appendLine("Active Backend URL: ${activeProfile?.supabaseUrl.orEmpty()}")
                    appendLine("Overall Status: ${if (diagnosticsSuccess == true) "PASSED" else if (diagnosticsSuccess == false) "FAILED" else "IN PROGRESS"}")
                    appendLine("Summary: ${diagnosticsSummary.orEmpty()}")
                    appendLine("--------------------------------------------------")
                    diagnosticSteps.forEach { step ->
                        appendLine("[${step.status}] ${step.name}: ${step.detail}")
                    }
                    appendLine("==================================================")
                }
                clipboardManager.setText(AnnotatedString(report))
                Toast.makeText(context, "Diagnostic report copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            isDarkMode = isDarkMode
        )
    }
}

// ─────────────────────────────────────────────────────────────
// HERO CARD: ACTIVE BACKEND
// ─────────────────────────────────────────────────────────────
@Composable
private fun ActiveBackendHeroCard(
    activeProfile: SupabaseProfileEntity?,
    isConnected: Boolean,
    revealAnonKey: Boolean,
    onToggleRevealKey: () -> Unit,
    onCopyUrl: () -> Unit,
    onCopyKey: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onEditActive: () -> Unit,
    isDarkMode: Boolean
) {
    val cardGradient = if (isDarkMode) {
        listOf(Color(0xFF131B2E), Color(0xFF0F172A))
    } else {
        listOf(Color(0xFFF0FDF4), Color(0xFFFFFFFF))
    }

    val borderColor = if (isConnected) {
        if (isDarkMode) Color(0xFF059669) else Color(0xFF10B981)
    } else {
        if (isDarkMode) Color(0xFF374151) else Color(0xFFD1D5DB)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(cardGradient))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header Row: Status badge & project ref
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isConnected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF6B7280).copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF10B981) else Color(0xFF9CA3AF))
                            )
                            Text(
                                text = if (isConnected) "ACTIVE & CONNECTED" else "OFFLINE / UNVERIFIED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Project reference extracted from URL
                    val projectRef = remember(activeProfile?.supabaseUrl) {
                        val url = activeProfile?.supabaseUrl.orEmpty()
                        Regex("https://([a-zA-Z0-9_-]+)\\.supabase\\.co").find(url)?.groupValues?.getOrNull(1) ?: "custom-host"
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = projectRef,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Business / Profile Name
                Text(
                    text = activeProfile?.businessName?.ifBlank { "SwapnoPay Main Cloud" } ?: "No Active Backend Selected",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                )

                // URL Row with Copy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeProfile?.supabaseUrl?.ifBlank { "Not configured" } ?: "Not configured",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF334155),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onCopyUrl,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy URL",
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Anon Key Row with Reveal & Copy
                val key = activeProfile?.anonKey.orEmpty()
                val displayKey = if (revealAnonKey) {
                    key.ifEmpty { "Not configured" }
                } else {
                    if (key.length > 12) "••••••••••••••••••••${key.takeLast(6)}" else if (key.isNotEmpty()) "••••••••" else "Not configured"
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.VpnKey,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = displayKey,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF334155),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onToggleRevealKey,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (revealAnonKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Reveal Key",
                                    tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onCopyKey,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Key",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRunDiagnostics,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Outlined.Speed, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Diagnostics", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onEditActive,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
                        ),
                        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PROFILE ITEM CARD IN LIST
// ─────────────────────────────────────────────────────────────
@Composable
private fun ProfileItemCard(
    profile: SupabaseProfileEntity,
    isActive: Boolean,
    onActivate: () -> Unit,
    onTestDiagnostics: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    isDarkMode: Boolean
) {
    val cardBg = if (isDarkMode) Color(0xFF141418) else Color.White
    val cardBorder = if (isActive) {
        Color(0xFF10B981)
    } else {
        if (isDarkMode) Color(0xFF27272E) else Color(0xFFE2E8F0)
    }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF64748B)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title & Active Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isActive) Color(0xFF10B981).copy(alpha = 0.15f) else (if (isDarkMode) Color(0xFF27272E) else Color(0xFFF1F5F9)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Outlined.CloudQueue,
                                contentDescription = null,
                                tint = if (isActive) Color(0xFF10B981) else textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = profile.businessName.ifBlank { "Unnamed Profile" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.supabaseUrl.removePrefix("https://").removePrefix("http://"),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = if (isDarkMode) Color(0xFF1E1F26) else Color(0xFFF1F5F9),
                thickness = 1.dp
            )

            // Actions row: Activate / Test Diagnostics / Edit / Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    Button(
                        onClick = onActivate,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Set as Active", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onTestDiagnostics,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Speed, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verify CRUD", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onTestDiagnostics,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Analytics,
                            contentDescription = "Test",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
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

// ─────────────────────────────────────────────────────────────
// ADD / EDIT PROFILE DIALOG
// ─────────────────────────────────────────────────────────────
@Composable
private fun AddEditProfileDialog(
    initialProfile: SupabaseProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, anonKey: String, makeActive: Boolean) -> Unit,
    isDarkMode: Boolean
) {
    var businessName by remember { mutableStateOf(initialProfile?.businessName.orEmpty()) }
    var supabaseUrl by remember { mutableStateOf(initialProfile?.supabaseUrl.orEmpty()) }
    var anonKey by remember { mutableStateOf(initialProfile?.anonKey.orEmpty()) }
    var makeActive by remember { mutableStateOf(initialProfile?.isActive ?: true) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isServiceRoleDetected = remember(anonKey) {
        anonKey.contains("service_role", ignoreCase = true) || anonKey.contains("\"role\":\"service_role\"")
    }

    val cardBg = if (isDarkMode) Color(0xFF181820) else Color.White
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Text(
                    text = if (initialProfile == null) "Add Supabase Profile" else "Edit Supabase Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                // Business / Instance Name
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Profile / Business Name") },
                    placeholder = { Text("e.g. Dhaka Branch Cloud") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Supabase URL
                OutlinedTextField(
                    value = supabaseUrl,
                    onValueChange = {
                        supabaseUrl = it
                        testStatus = null
                    },
                    label = { Text("Supabase URL") },
                    placeholder = { Text("https://your-ref.supabase.co") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Supabase Anon Key
                OutlinedTextField(
                    value = anonKey,
                    onValueChange = {
                        anonKey = it
                        testStatus = null
                    },
                    label = { Text("Publishable Anon Key") },
                    placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = isServiceRoleDetected
                )

                // Service Role Key Warning Guardrail
                if (isServiceRoleDetected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            Text(
                                text = "Security Warning: Never store a service_role key on mobile devices. Paste your public anon key instead.",
                                fontSize = 11.sp,
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Checkbox: Make active
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { makeActive = !makeActive }
                ) {
                    Checkbox(
                        checked = makeActive,
                        onCheckedChange = { makeActive = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set as active database immediately",
                        fontSize = 13.sp,
                        color = textPrimary
                    )
                }

                // Test Connection Before Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testStatus = "Verifying credentials..."
                            val cleanUrl = if (!supabaseUrl.startsWith("http")) "https://${supabaseUrl.trim()}" else supabaseUrl.trim()
                            coroutineScope.launch {
                                com.example.data.remote.SupabaseClient.testConnection(
                                    url = cleanUrl,
                                    anonKey = anonKey.trim(),
                                    onSuccess = {
                                        isTesting = false
                                        testStatus = "✓ Endpoint verified successfully!"
                                    },
                                    onFailure = { err ->
                                        isTesting = false
                                        testStatus = "✗ $err"
                                    }
                                )
                            }
                        },
                        enabled = !isTesting && supabaseUrl.isNotBlank() && anonKey.isNotBlank() && !isServiceRoleDetected,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test Connection", fontSize = 12.sp)
                    }

                    testStatus?.let { status ->
                        Text(
                            text = status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status.startsWith("✓")) Color(0xFF10B981) else Color(0xFFEF4444),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                HorizontalDivider(color = if (isDarkMode) Color(0xFF27272E) else Color(0xFFE2E8F0))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(businessName, supabaseUrl, anonKey, makeActive)
                        },
                        enabled = supabaseUrl.isNotBlank() && anonKey.isNotBlank() && !isServiceRoleDetected,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Save Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// LIVE DIAGNOSTICS MODAL
// ─────────────────────────────────────────────────────────────
@Composable
private fun LiveDiagnosticsModal(
    steps: List<com.example.ui.RealtimeDiagnosticStep>,
    isRunning: Boolean,
    overallSuccess: Boolean?,
    summary: String?,
    onRunAgain: () -> Unit,
    onDismiss: () -> Unit,
    onCopyLogs: () -> Unit,
    isDarkMode: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val dialogBg = if (isDarkMode) Color(0xFF0F1117) else Color.White
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF6366F1).copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Realtime CRUD Diagnostics",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "5-Step Database Lifecycle Check",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Overall Status Banner
                val bannerBg = when (overallSuccess) {
                    true -> Color(0xFF10B981).copy(alpha = 0.15f)
                    false -> Color(0xFFEF4444).copy(alpha = 0.15f)
                    null -> Color(0xFF6366F1).copy(alpha = 0.12f)
                }
                val bannerBorder = when (overallSuccess) {
                    true -> Color(0xFF10B981)
                    false -> Color(0xFFEF4444)
                    null -> Color(0xFF6366F1)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bannerBg,
                    border = BorderStroke(1.dp, bannerBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF6366F1),
                                strokeWidth = 2.dp
                            )
                        } else if (overallSuccess == true) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                        } else if (overallSuccess == false) {
                            Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Outlined.HourglassEmpty, null, tint = Color(0xFF6366F1), modifier = Modifier.size(22.dp))
                        }

                        Text(
                            text = summary ?: "Diagnostic verification ready to run.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step by Step List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(steps) { step ->
                        StepItemCard(step = step, isDarkMode = isDarkMode)
                    }

                    // Remediation Hint Card if test failed
                    if (overallSuccess == false) {
                        item {
                            RemediationAdviceCard(
                                summary = summary.orEmpty(),
                                onCopySql = {
                                    val sql = """
                                        -- SwapnoPay security_logs table creation SQL:
                                        CREATE TABLE IF NOT EXISTS public.security_logs (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            event TEXT NOT NULL,
                                            details JSONB DEFAULT '{}'::jsonb,
                                            created_at TIMESTAMPTZ DEFAULT now()
                                        );
                                        ALTER TABLE public.security_logs ENABLE ROW LEVEL SECURITY;
                                        CREATE POLICY "Allow anon insert and select" ON public.security_logs 
                                            FOR ALL TO anon USING (true) WITH CHECK (true);
                                    """.trimIndent()
                                    clipboardManager.setText(AnnotatedString(sql))
                                    Toast.makeText(context, "SQL copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Actions: Copy Logs, Run Again, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCopyLogs,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Report", fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRunAgain,
                            enabled = !isRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkMode) Color(0xFF27272E) else Color(0xFFE2E8F0),
                                contentColor = textPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Close", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// DIAGNOSTIC STEP ITEM
// ─────────────────────────────────────────────────────────────
@Composable
private fun StepItemCard(
    step: com.example.ui.RealtimeDiagnosticStep,
    isDarkMode: Boolean
) {
    val cardBg = if (isDarkMode) Color(0xFF14161F) else Color(0xFFF8FAFC)
    val stepBorder = when (step.status) {
        com.example.ui.DiagnosticStepStatus.SUCCESS -> Color(0xFF10B981)
        com.example.ui.DiagnosticStepStatus.FAILED -> Color(0xFFEF4444)
        com.example.ui.DiagnosticStepStatus.RUNNING -> Color(0xFF6366F1)
        com.example.ui.DiagnosticStepStatus.PENDING -> if (isDarkMode) Color(0xFF27272E) else Color(0xFFE2E8F0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, stepBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (step.status) {
                com.example.ui.DiagnosticStepStatus.SUCCESS -> {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                }
                com.example.ui.DiagnosticStepStatus.FAILED -> {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
                com.example.ui.DiagnosticStepStatus.RUNNING -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF6366F1), strokeWidth = 2.dp)
                }
                com.example.ui.DiagnosticStepStatus.PENDING -> {
                    Icon(Icons.Outlined.HourglassEmpty, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = step.detail,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// REMEDIATION ADVICE CARD
// ─────────────────────────────────────────────────────────────
@Composable
private fun RemediationAdviceCard(
    summary: String,
    onCopySql: () -> Unit,
    isDarkMode: Boolean
) {
    val is404MissingTable = summary.contains("404") || summary.contains("security_logs") || summary.contains("does not exist")
    val is401Auth = summary.contains("401") || summary.contains("Authentication") || summary.contains("Invalid Supabase anon key")
    val is403Rls = summary.contains("403") || summary.contains("RLS") || summary.contains("permission")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1E1418) else Color(0xFFFFF1F2)
        ),
        border = BorderStroke(1.dp, Color(0xFFEF4444))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Build, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Text(
                    text = "Recommended Remediation Steps",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            }

            if (is404MissingTable) {
                Text(
                    text = "The 'security_logs' table is missing from your Supabase project. You can initialize it instantly by executing the setup SQL in the Supabase SQL Editor.",
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                )
                Button(
                    onClick = onCopySql,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Setup SQL for Supabase", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else if (is401Auth) {
                Text(
                    text = "Authentication failed (HTTP 401). Verify that your Supabase Anon Key is correctly copied from Project Settings -> API -> Project API keys -> anon (public). Do not use service_role key.",
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                )
            } else if (is403Rls) {
                Text(
                    text = "Row Level Security (RLS) blocked the operation (HTTP 403). Make sure the table has an RLS policy granting access to the 'anon' role, or run the complete database migration script.",
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                )
            } else {
                Text(
                    text = "Please check your network connection, ensure the Supabase project is active (not paused), and confirm your endpoint URL ends in '.supabase.co'.",
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                )
            }
        }
    }
}
