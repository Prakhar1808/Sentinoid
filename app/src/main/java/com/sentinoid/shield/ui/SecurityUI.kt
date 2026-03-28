package com.sentinoid.shield.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SecurityDashboard(
    isProtected: Boolean,
    threatCount: Int,
    lastScanTime: String,
    onScanClick: () -> Unit,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Sentinoid Shield",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isProtected) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isProtected) "PROTECTED" else "AT RISK",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Last scan: $lastScanTime",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Threat Counter
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Threats",
                    fontSize = 18.sp
                )
                Badge(
                    containerColor = if (threatCount > 0) Color.Red else Color.Green
                ) {
                    Text(
                        text = threatCount.toString(),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                text = "Scan Now",
                onClick = onScanClick,
                icon = "SCAN"
            )
            ActionButton(
                text = "Vault",
                onClick = onVaultClick,
                icon = "LOCK"
            )
            ActionButton(
                text = "Settings",
                onClick = onSettingsClick,
                icon = "SETTINGS"
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    icon: String
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(100.dp, 80.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(text = text, fontSize = 12.sp)
        }
    }
}

@Composable
fun ThreatList(
    threats: List<ThreatItem>,
    onIgnore: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    LazyColumn {
        items(threats) { threat ->
            ThreatCard(threat = threat, onIgnore = onIgnore, onRemove = onRemove)
        }
    }
}

@Composable
private fun ThreatCard(
    threat: ThreatItem,
    onIgnore: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = threat.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = threat.packageName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Risk: ${(threat.riskScore * 100).toInt()}%",
                    color = if (threat.riskScore > 0.7) Color.Red else Color(0xFFFFA500),
                    fontSize = 14.sp
                )
            }
            Row {
                TextButton(onClick = { onIgnore(threat.packageName) }) {
                    Text("Ignore")
                }
                Button(onClick = { onRemove(threat.packageName) }) {
                    Text("Remove")
                }
            }
        }
    }
}

data class ThreatItem(
    val packageName: String,
    val appName: String,
    val riskScore: Float,
    val threatType: String
)

@Composable
fun VaultScreen(
    files: List<VaultFile>,
    onAddFile: () -> Unit,
    onDecryptFile: (VaultFile) -> Unit,
    onDeleteFile: (VaultFile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Security Vault",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onAddFile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("+ Add File to Vault")
        }

        LazyColumn {
            items(files) { file ->
                VaultFileCard(
                    file = file,
                    onDecrypt = { onDecryptFile(file) },
                    onDelete = { onDeleteFile(file) }
                )
            }
        }
    }
}

@Composable
private fun VaultFileCard(
    file: VaultFile,
    onDecrypt: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Encrypted with ${file.encryptionType}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                OutlinedButton(onClick = onDecrypt) {
                    Text("Decrypt")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

data class VaultFile(
    val name: String,
    val encryptionType: String,
    val size: Long
)
