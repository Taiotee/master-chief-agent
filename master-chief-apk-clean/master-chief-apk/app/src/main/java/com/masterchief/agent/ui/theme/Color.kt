package com.masterchief.agent.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Halo / UNSC palette. Dark armour black, glowing Halo green/cyan.
// "Glowing" in Compose = a bright colour on near-black, with a soft
// shadow/glow via .shadow() on the Halo ring composable — never a bitmap.
val UnscBlack = Color(0xFF060A08)
val ArmourDark = Color(0xFF0E1A16)
val ArmourPanel = Color(0xFF122019)
// AI Studio HUD look: dark navy/black with gold/amber accents.
val UnscNavy = Color(0xFF08131F)
val NavyPanel = Color(0xFF0E1E30)
val NavyLine = Color(0xFF1E3A52)
val CortanaBlue = Color(0xFF4FC3F7)
val HaloGreen = Color(0xFF3DFF88)   // the glowing Halo green
val HaloCyan = Color(0xFF35E0D4)
val SpartanGold = Color(0xFFFFC247)
val AlertRed = Color(0xFFFF5252)
val TextPrimary = Color(0xFFE8F5EC)
val TextMuted = Color(0xFF8FA89A)

val MasterChiefColors = darkColorScheme(
    primary = HaloGreen,
    secondary = HaloCyan,
    tertiary = SpartanGold,
    background = UnscBlack,
    surface = ArmourDark,
    surfaceVariant = ArmourPanel,
    onPrimary = UnscBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = AlertRed
)
