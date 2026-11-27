package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.agent.cleanup.CleanupEngine
import com.masterchief.agent.agent.cleanup.CleanupPermission
import com.masterchief.agent.agent.cleanup.SafetyTier

/**
 * Cleanup screen — the review list.
 *
 * Layout: SAFE items at the top (green, auto-cleaned only if the permission
 * allows), REVIEW items in the middle (amber, each with Confirm / Keep),
 * and the last report at the bottom ("freed X, Y items await you").
 * Nothing here deletes without a tap, except SAFE items when the user
 * chose AUTO_SAFE_ONLY — and even then the report says exactly what went.
 */
@Composable
fun CleanupScreen(state: ChiefUiState, vm: ChiefViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("CLEANUP — PHONE ASSESSMENT", style = MaterialTheme.typography.titleMedium)
        Text("Permission: ${state.cleanupPermission}", style = MaterialTheme.typography.labelMedium)
        Row {
            CleanupPermission.entries.forEach { level ->
                Button(
                    onClick = { vm.setCleanupPermission(level) },
                    modifier = Modifier.padding(end = 4.dp)
                ) { Text(level.name.replace("_", " ")) }
            }
        }
        Text("Auto-clean safe junk only  •  Ask before anything else  •  Never delete important files silently",
            style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.runCleanup("manual") }, enabled = !state.busy) {
            Text(if (state.busy) "ASSESSING…" else "RUN ASSESSMENT NOW")
        }
        if (state.cleanupReport.isNotBlank()) {
            Text(state.cleanupReport, Modifier.padding(vertical = 8.dp))
        }
        val safe = state.cleanupItems.filter { it.safety == SafetyTier.SAFE }
        val review = state.cleanupItems.filter { it.safety == SafetyTier.REVIEW }
        Text("SAFE — auto-cleanable (${safe.size})", style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF3DFF88))
        Text("REVIEW — needs your decision (${review.size})", style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFFFC247))
        LazyColumn(Modifier.weight(1f)) {
            items(review) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${item.kind} — ${item.fileName}")
                        Text("${item.reason} • ${CleanupEngine.formatBytes(item.sizeBytes)} • score ${item.safetyScore}")
                        Row {
                            Button(onClick = { vm.confirmCleanup(item) }) { Text("CONFIRM DELETE") }
                            Button(onClick = { vm.keepCleanup(item) }) { Text("KEEP") }
                        }
                    }
                }
            }
            items(safe) { item ->
                Text("✓ ${item.fileName} — ${item.reason} (${CleanupEngine.formatBytes(item.sizeBytes)})",
                    Modifier.padding(4.dp))
            }
        }
    }
}
