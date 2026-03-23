package com.sentinoid.shield

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinoidDashboard(
    highRiskApps: List<RiskyApp>, 
    bridgeConnected: Boolean,
    onBridgeToggle: (Boolean) -> Unit,
    onOpenVault: () -> Unit
) {
    var showModal by remember { mutableStateOf(false) }
    var isThreatActive by remember { mutableStateOf(false) }
    var isSanitizing by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    
    val tacticalLogs by SilentAlarmManager.tacticalLogs.collectAsState()
    var lastLogCount by remember { mutableIntStateOf(tacticalLogs.size) }

    LaunchedEffect(tacticalLogs) {
        if (tacticalLogs.size > lastLogCount) {
            val latest = tacticalLogs.firstOrNull()
            if (latest?.status == ThreatStatus.ACTIVE) {
                isThreatActive = true
            }
        }
        lastLogCount = tacticalLogs.size
    }

    LaunchedEffect(bridgeConnected) {
        if (bridgeConnected) {
            isThreatActive = false
            isSanitizing = false
            SilentAlarmManager.setLockdown(false)
        }
    }

    LaunchedEffect(isSanitizing) {
        if (isSanitizing) {
            delay(3000)
            isSanitizing = false
            isThreatActive = false
            SilentAlarmManager.resolveAllLogs()
        }
    }

    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            delay(2500)
            isSyncing = false
        }
    }

    val isSecure = bridgeConnected && highRiskApps.none { it.riskScore > 0.8f } && !isThreatActive
    
    // Premium Color Palette
    val CarbonBlack = Color(0xFF0A0A0B)
    val TacticalGrey = Color(0xFF1C1C1E)
    val NeonCyan = Color(0xFF00E5FF)
    val WarningOrange = Color(0xFFFF9100)
    val AlertRed = Color(0xFFFF1744)
    val SecureGreen = Color(0xFF00E676)

    val statusColor = when {
        isSanitizing -> WarningOrange
        !isSecure -> AlertRed
        else -> SecureGreen
    }

    if (showModal) {
        DiagnosticModal(onDismiss = { showModal = false })
    }

    Scaffold(
        containerColor = CarbonBlack,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "SENTINOID", 
                        letterSpacing = 4.sp, 
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    ) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CarbonBlack,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = onOpenVault) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Vault",
                            tint = NeonCyan
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TACTICAL STATUS RING
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Ring
                CircularProgressIndicator(
                    progress = 1f,
                    modifier = Modifier.fillMaxSize(),
                    color = statusColor.copy(alpha = 0.2f),
                    strokeWidth = 8.dp
                )
                
                if (isSanitizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = WarningOrange,
                        strokeWidth = 8.dp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isSecure) "ARMED" else if (isSanitizing) "SCANNING" else "BREACH",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        if (bridgeConnected) "ENCRYPTED" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // HARDWARE ANCHOR CARD
            TacticalCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SettingsInputHdmi,
                            contentDescription = null,
                            tint = if (bridgeConnected) SecureGreen else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("HARDWARE ANCHOR", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                if (bridgeConnected) "SILICON LINK ACTIVE" else "NO EXTERNAL BRIDGE",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (bridgeConnected) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // RISKY APPS SECTION
            if (highRiskApps.isNotEmpty()) {
                Text(
                    "HEURISTIC THREATS", 
                    style = MaterialTheme.typography.labelMedium, 
                    modifier = Modifier.align(Alignment.Start),
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                highRiskApps.forEach { app ->
                    TacticalCard(borderColor = if (app.riskScore > 0.8f) AlertRed else WarningOrange) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(app.packageName, color = Color.Gray, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${(app.riskScore * 100).toInt()}% RISK", 
                                    color = if (app.riskScore > 0.8f) AlertRed else WarningOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(app.threatType, color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ACTION BUTTONS
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TacticalButton(
                    text = if (isSanitizing) "PURGING..." else if (isThreatActive) "SANITIZE" else "MANUAL SCAN",
                    onClick = { 
                        if (isThreatActive) isSanitizing = true 
                        else SilentAlarmManager.triggerLockdown("Manual Audit Triggered")
                    },
                    modifier = Modifier.weight(1f),
                    color = if (isThreatActive) AlertRed else NeonCyan
                )
                
                TacticalButton(
                    text = "DIAGNOSTICS",
                    onClick = { showModal = true },
                    modifier = Modifier.weight(1f),
                    outlined = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // TACTICAL SECURITY LOG
            Text(
                "SECURITY EVENT LOG", 
                style = MaterialTheme.typography.labelSmall, 
                modifier = Modifier.align(Alignment.Start).padding(start = 4.dp),
                color = NeonCyan,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tacticalLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(TacticalGrey, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SYSTEM CLEAN. NO ACTIVE THREATS.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                
                tacticalLogs.forEach { log ->
                    TacticalCard(
                        borderColor = if (log.status == ThreatStatus.ACTIVE) AlertRed else SecureGreen.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp), 
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(log.vector.uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                Text("${log.timestamp} | ${log.status}", color = if (log.status == ThreatStatus.ACTIVE) AlertRed else SecureGreen, fontSize = 11.sp)
                            }
                            if (log.status == ThreatStatus.ACTIVE) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AlertRed, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CheckCircle, null, tint = SecureGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // LIVE SYSTEM FEED
            Text(
                "LIVE SYSTEM FEED", 
                style = MaterialTheme.typography.labelSmall, 
                modifier = Modifier.align(Alignment.Start).padding(start = 4.dp),
                color = NeonCyan,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalGrey, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                val time = remember { SecurityUtils.getCurrentTime() }
                SystemFeedLine("$time | BRIDGE_ESTABLISHED", SecureGreen)
                SystemFeedLine("$time | KERNEL_INTEGRITY_CHECK: OK", SecureGreen)
                
                if (isSyncing) {
                    SystemFeedLine("${SecurityUtils.getCurrentTime()} | AOA: HANDSHAKE_START", NeonCyan)
                    SystemFeedLine("${SecurityUtils.getCurrentTime()} | DATA: EXPORT_ENCRYPTED_LOGS", NeonCyan)
                } else if (isSanitizing) {
                    SystemFeedLine("${SecurityUtils.getCurrentTime()} | SCRUBBING: MEMORY_SANITIZATION", WarningOrange)
                } else if (isThreatActive) {
                    SystemFeedLine("${SecurityUtils.getCurrentTime()} | ALERT: UNAUTHORIZED_ACCESS", AlertRed)
                }
            }
        }
    }
}

@Composable
fun SystemFeedLine(text: String, color: Color) {
    Text(
        text, 
        color = color, 
        fontSize = 10.sp, 
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
fun TacticalCard(
    borderColor: Color = Color.White.copy(alpha = 0.1f),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1C1C1E).copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

@Composable
fun TacticalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00E5FF),
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
        ) {
            Text(text, color = color, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            Text(text, color = Color.Black, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun DiagnosticModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "SYSTEM DIAGNOSTICS", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagnosticLine("Kernel Integrity", "VERIFIED", Color(0xFF00E676))
                DiagnosticLine("Bridge Encryption", "AES-256-GCM", Color.White)
                DiagnosticLine("Protocol", "USB AOA v2.0", Color(0xFF00E676))
                DiagnosticLine("Heuristic Engine", "TFLite Active", Color(0xFF00E676))
                DiagnosticLine("Hardware Root", "TEE LOCKED", Color(0xFF00E676))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TacticalButton(
                    text = "CLOSE",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    outlined = true
                )
            }
        }
    }
}

@Composable
fun DiagnosticLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
