package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.ui.components.HaloRing
import com.masterchief.agent.ui.theme.HaloGreen

/** Command screen — the home screen. Halo, status, directives, and the
 *  standby chat box pinned at the bottom (always available). */
@Composable
fun CommandScreen(state: ChiefUiState, vm: ChiefViewModel, onOpenChat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("MASTER CHIEF", style = MaterialTheme.typography.headlineMedium, color = HaloGreen)
                Text("UNSC // SPARTAN-117 // STANDING BY", style = MaterialTheme.typography.labelMedium)
                Text("Brain: ${state.brainMode}  •  Last used: ${state.lastBrainUsed}",
                    style = MaterialTheme.typography.labelMedium)
            }
            HaloRing(sizeDp = 72)
        }
        Spacer(Modifier.height(16.dp))
        Text("DIRECTIVES", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(state.missions) { m ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (m.done) "✓ ${m.title}" else "○ ${m.title}")
                        Button(onClick = { vm.toggleMission(m.id) }) {
                            Text(if (m.done) "Reopen" else "Done")
                        }
                    }
                }
            }
        }
        var newMission by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newMission, onValueChange = { newMission = it },
                label = { Text("New directive") }, modifier = Modifier.weight(1f)
            )
            Button(onClick = { if (newMission.isNotBlank()) { vm.addMission(newMission); newMission = "" } }) {
                Text("ADD")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
            Text("OPEN STANDBY CHAT")
        }
    }
}
