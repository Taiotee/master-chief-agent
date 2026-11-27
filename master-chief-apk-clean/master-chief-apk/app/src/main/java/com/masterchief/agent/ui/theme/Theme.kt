package com.masterchief.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Military / HUD type: monospace for data, tight letter-spacing for headers.
val ChiefTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, letterSpacing = 1.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, letterSpacing = 1.2.sp
    ),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, letterSpacing = 1.sp
    )
)

@Composable
fun MasterChiefTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MasterChiefColors,
        typography = ChiefTypography,
        content = content
    )
}
