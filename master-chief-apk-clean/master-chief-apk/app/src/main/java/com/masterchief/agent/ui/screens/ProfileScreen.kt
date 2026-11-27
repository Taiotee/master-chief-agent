package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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

/** Spartan profile — what Chief has learned about Tayo. */
@Composable
fun ProfileScreen(state: ChiefUiState, vm: ChiefViewModel) {
    var likes by remember { mutableStateOf(state.profile.likes.joinToString(", ")) }
    var style by remember { mutableStateOf(state.profile.style) }
    var music by remember { mutableStateOf(state.profile.musicTaste) }
    var mood by remember { mutableStateOf(state.profile.mood) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("SPARTAN PROFILE — ${state.profile.callsign}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(likes, { likes = it }, { Text("Likes (comma separated)") }, Modifier.fillMaxWidth())
        OutlinedTextField(style, { style = it }, { Text("Style") }, Modifier.fillMaxWidth())
        OutlinedTextField(music, { music = it }, { Text("Music taste") }, Modifier.fillMaxWidth())
        OutlinedTextField(mood, { mood = it }, { Text("Current mood") }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            vm.saveProfile(state.profile.copy(
                likes = likes.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                style = style, musicTaste = music, mood = mood))
        }) { Text("SAVE PROFILE") }
        Text("Stored on this phone only. Chief uses it to personalise briefings " +
            "and to pre-empt what you need.", style = MaterialTheme.typography.labelMedium)
    }
}
