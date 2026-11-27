package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Column
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

/** Standby chat box — always available, at the bottom of every screen in
 *  the real app via MainActivity; this is the full chat view. */
@Composable
fun ChatScreen(state: ChiefUiState, vm: ChiefViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("STANDBY CHAT — TALK TO CHIEF", style = MaterialTheme.typography.titleMedium)
        Text("Try: \"clean my photos\", \"open WhatsApp\", \"set an alarm for 07:30\", " +
            "\"switch to local\", \"plan my day\"",
            style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(state.chat.reversed()) { m ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (m.fromChief) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        (if (m.fromChief) "CHIEF: " else "YOU: ") + m.text,
                        Modifier.padding(12.dp)
                    )
                }
            }
        }
        var input by remember { mutableStateOf("") }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("Give an order, sir…") }, modifier = Modifier.weight(1f)
            )
            Button(onClick = { vm.sendCommand(input); input = "" }, enabled = !state.busy) {
                Text("SEND")
            }
        }
        if (state.busy) Text("Chief is working…", style = MaterialTheme.typography.labelMedium)
    }
}
