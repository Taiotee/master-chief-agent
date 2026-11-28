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
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.agent.cleanup.CleanupEngine
import com.masterchief.agent.ui.theme.NavyPanel
import com.masterchief.agent.ui.theme.SpartanGold
import com.masterchief.agent.ui.theme.TextMuted

/**
 * Photo Organizer — real duplicate scan via MediaStore.
 * Exact duplicates only (same size + same first-64KB hash); the oldest
 * copy is always kept. NOTHING is deleted here: each duplicate waits for
 * the user's CONFIRM DELETE tap, and Android may still ask for its own
 * confirmation in Photos.
 */
@Composable
fun PhotoScreen(state: ChiefUiState, vm: ChiefViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("PHOTO ORGANIZER", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
        Text(
            if (state.permissions?.photosGranted == true) "Photos permission: GRANTED"
            else "Photos permission: NOT GRANTED — grant it in Settings for a real scan.",
            style = MaterialTheme.typography.labelMedium, color = TextMuted
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.runPhotoSweep() }, enabled = !state.busy) {
            Text(if (state.busy) "SCANNING…" else "RUN DUPLICATE SCAN")
        }
        Spacer(Modifier.height(8.dp))
        if (state.photoScanDone) {
            Text(
                if (state.photoDuplicates.isEmpty()) "No exact duplicates found, sir."
                else "${state.photoDuplicates.size} exact duplicate(s) — oldest copy is always kept. " +
                    "Confirm each one before it is deleted.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.photoDuplicates) { dup ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPanel)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(dup.fileName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${dup.reason} • ${CleanupEngine.formatBytes(dup.sizeBytes)}",
                            style = MaterialTheme.typography.labelMedium, color = TextMuted
                        )
                        Row {
                            Button(
                                onClick = { vm.deletePhotoCandidate(dup) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) { Text("CONFIRM DELETE") }
                            Button(onClick = { vm.keepPhotoCandidate(dup) }) { Text("KEEP") }
                        }
                    }
                }
            }
        }
    }
}
