package com.masterchief.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ui.theme.HaloCyan
import com.masterchief.agent.ui.theme.HaloGreen

/**
 * The glowing Halo ring — the visual signature of the app.
 * A pulsing green/cyan ring, drawn in Compose (no image asset needed).
 * Used on the Command screen and as the standby bubble icon.
 */
@Composable
fun HaloRing(modifier: Modifier = Modifier, sizeDp: Int = 120) {
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(HaloGreen.copy(alpha = 0.9f * pulse), HaloCyan.copy(alpha = 0.25f)),
                center = center, radius = r
            ),
            radius = r * 0.92f,
            style = Stroke(width = 10f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = r * 0.18f,
            center = Offset(center.x, center.y - r * 0.55f)
        )
    }
}
