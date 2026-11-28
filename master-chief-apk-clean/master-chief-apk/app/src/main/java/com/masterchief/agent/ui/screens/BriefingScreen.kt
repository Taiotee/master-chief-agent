package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.masterchief.agent.ui.components.HudPanel
import com.masterchief.agent.ui.components.ReadoutRow
import com.masterchief.agent.ui.theme.NavyPanel
import com.masterchief.agent.ui.theme.SpartanGold
import com.masterchief.agent.ui.theme.TextMuted

/**
 * SITREP — morning briefing, real Lagos weather (Open-Meteo, no key),
 * live notifications, alarms, and today's directives.
 * Weather says "unavailable" when the service fails — never an invented
 * temperature. Android does not let apps list existing alarms, and the
 * screen says so; setting one opens the Clock app to confirm.
 */
@Composable
fun BriefingScreen(state: ChiefUiState, vm: ChiefViewModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        Text("SITREP — MORNING BRIEFING", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
        val date = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
        Text(date, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(12.dp))

        HudPanel("WEATHER // LAGOS") {
            val w = state.weather
            when {
                w != null -> {
                    ReadoutRow("CONDITIONS", w.description)
                    ReadoutRow("TEMPERATURE", "${w.tempC}°C")
                    w.humidity?.let { ReadoutRow("HUMIDITY", "$it%") }
                    w.windKmh?.let { ReadoutRow("WIND", "$it km/h") }
                    Text(
                        "Live from Open-Meteo, no key needed.",
                        style = MaterialTheme.typography.labelMedium, color = TextMuted
                    )
                }
                state.weatherError != null -> {
                    Text(
                        "Weather unavailable: ${state.weatherError}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Check your connection and tap Refresh.",
                        style = MaterialTheme.typography.labelMedium, color = TextMuted
                    )
                }
                else -> Text("Checking weather…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("TODAY") {
            val s = state.storage
            Text(
                if (s != null) "Storage: ${s.freeBytes / 1_048_576} MB free of " +
                    "${s.totalBytes / 1_048_576} MB" +
                    if (s.isLow) " — LOW, sir. Cleanup recommended." else " — nominal."
                else "Storage: checking…"
            )
            Text("Open directives: ${state.missions.count { !it.done }}")
            Text("Mood logged: ${state.profile.mood.ifBlank { "—" }}")
            Text("Music: ${state.profile.musicTaste.ifBlank { "—" }}")
            Text(
                "Gmail is read in the Muse hub today — on-device Gmail needs " +
                    "the Gmail API and is a later milestone.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("ALARMS") {
            var hour by remember { mutableStateOf("07") }
            var minute by remember { mutableStateOf("30") }
            var label by remember { mutableStateOf("Master Chief alarm") }
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = hour, onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hour") }, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minute, onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                    label = { Text("Minute") }, modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = label, onValueChange = { label = it },
                label = { Text("Alarm label") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val h = hour.toIntOrNull() ?: 7
                val m = minute.toIntOrNull() ?: 30
                vm.setAlarm(h.coerceIn(0, 23), m.coerceIn(0, 59), label.ifBlank { "Master Chief alarm" })
            }) { Text("SET ALARM") }
            Text(
                "Android opens the Clock app to confirm — Chief cannot set an " +
                    "alarm silently. Android also does not let apps list your " +
                    "existing alarms, so they are not shown here.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("LIVE NOTIFICATIONS") {
            if (state.permissions?.notificationListenerEnabled != true) {
                Text("Notification access is NOT GRANTED, sir.")
                Text(
                    "Enable it in Settings > Permissions so Chief can show " +
                        "live notifications here.",
                    style = MaterialTheme.typography.labelMedium, color = TextMuted
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.openNotificationAccessSettings() }) {
                    Text("ENABLE NOTIFICATION ACCESS")
                }
            } else if (state.notifications.isEmpty()) {
                Text("No notifications seen yet this session, sir.")
            } else {
                state.notifications.take(10).forEach { n ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyPanel)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${n.app} — ${n.title}", style = MaterialTheme.typography.bodyMedium)
                            if (n.text.isNotBlank()) {
                                Text(n.text, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = { vm.sendCommand("plan my day") },
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) { Text("PLAN MY DAY") }
            Button(
                onClick = { vm.refreshDevice(); vm.refreshWeather(); vm.refreshStorage() },
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) { Text("REFRESH") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
