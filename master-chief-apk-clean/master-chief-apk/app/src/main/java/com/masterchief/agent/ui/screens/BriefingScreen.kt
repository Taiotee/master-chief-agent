package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel

/** Briefing screen — morning weather for Lagos, today's plan, storage. */
@Composable
fun BriefingScreen(state: ChiefUiState, vm: ChiefViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("MORNING BRIEFING — LAGOS", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val s = state.storage
        Text(if (s != null)
            "Storage: ${s.freeBytes / 1_048_576} MB free of ${s.totalBytes / 1_048_576} MB " +
            if (s.isLow) "— LOW, sir. Cleanup assessment recommended." else "— nominal."
        else "Storage: checking…")
        Text("Open directives: ${state.missions.count { !it.done }}")
        Text("Mood logged: ${state.profile.mood.ifBlank { "—" }}")
        Text("Music: ${state.profile.musicTaste.ifBlank { "—" }}")
        Spacer(Modifier.height(16.dp))
        Text("Weather and Gmail are read live in the Muse hub today. " +
            "On-device v1 shows calendar + storage here; the Gmail API " +
            "connection is the next milestone (see README).",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.sendCommand("plan my day") }) { Text("PLAN MY DAY") }
        Button(onClick = { vm.refreshStorage() }) { Text("REFRESH") }
    }
}
