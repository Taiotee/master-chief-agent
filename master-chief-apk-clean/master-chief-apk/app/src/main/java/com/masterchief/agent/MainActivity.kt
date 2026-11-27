package com.masterchief.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.masterchief.agent.ui.screens.BriefingScreen
import com.masterchief.agent.ui.screens.ChatScreen
import com.masterchief.agent.ui.screens.CleanupScreen
import com.masterchief.agent.ui.screens.CommandScreen
import com.masterchief.agent.ui.screens.ProfileScreen
import com.masterchief.agent.ui.screens.SettingsScreen
import com.masterchief.agent.ui.theme.MasterChiefTheme

/**
 * MainActivity — the command centre.
 * Bottom navigation: Command (home) / Chat (standby chat box) /
 * Briefing / Cleanup / Profile / Settings.
 * The chat is reachable from every screen via the Chat tab, and from
 * anywhere on the phone via the floating Halo bubble (StandbyBubbleService)
 * once the user enables it. That is the "standby chat box always available"
 * requirement, in the Android-legal form.
 */
class MainActivity : ComponentActivity() {
    private val vm: ChiefViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MasterChiefTheme {
                val state by vm.state.collectAsState()
                val nav = rememberNavController()
                var tab by remember { mutableStateOf("command") }
                val tabs = listOf(
                    "command" to ("Command" to Icons.Filled.Home),
                    "chat" to ("Chat" to Icons.Filled.Chat),
                    "briefing" to ("Briefing" to Icons.Filled.WbSunny),
                    "cleanup" to ("Cleanup" to Icons.Filled.CleaningServices),
                    "profile" to ("Profile" to Icons.Filled.Person),
                    "settings" to ("Settings" to Icons.Filled.Settings)
                )
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            tabs.forEach { (route, labelIcon) ->
                                NavigationBarItem(
                                    selected = tab == route,
                                    onClick = { tab = route; nav.navigate(route) },
                                    icon = { Icon(labelIcon.second, contentDescription = labelIcon.first) },
                                    label = { Text(labelIcon.first) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(nav, startDestination = "command", Modifier.padding(padding)) {
                        composable("command") {
                            CommandScreen(state, vm) { tab = "chat"; nav.navigate("chat") }
                        }
                        composable("chat") { ChatScreen(state, vm) }
                        composable("briefing") { BriefingScreen(state, vm) }
                        composable("cleanup") { CleanupScreen(state, vm) }
                        composable("profile") { ProfileScreen(state, vm) }
                        composable("settings") { SettingsScreen(state, vm) }
                    }
                }
            }
        }
    }
}
