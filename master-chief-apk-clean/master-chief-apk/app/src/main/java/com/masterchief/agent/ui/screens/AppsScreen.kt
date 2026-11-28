package com.masterchief.agent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.ui.theme.NavyPanel
import com.masterchief.agent.ui.theme.SpartanGold
import com.masterchief.agent.ui.theme.TextMuted

/**
 * App Armory — every launchable app actually installed on this phone.
 * Tap one to open it. No app is listed that is not installed; there is no
 * "control WhatsApp" here — Chief opens apps, the user acts inside them.
 */
@Composable
fun AppsScreen(state: ChiefUiState, vm: ChiefViewModel) {
    LaunchedEffect(Unit) { vm.loadApps() }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("APP ARMORY", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
        Text(
            if (state.appsLoaded) "${state.apps.size} launchable apps on this phone — tap to open."
            else "Reading installed apps…",
            style = MaterialTheme.typography.labelMedium, color = TextMuted
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val shown = state.apps.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(shown) { app ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { vm.openAppByPackage(app.packageName) },
                    colors = CardDefaults.cardColors(containerColor = NavyPanel)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium)
                        Text(app.packageName, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    }
                }
            }
        }
    }
}
