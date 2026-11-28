package com.masterchief.agent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterchief.agent.data.DeviceStatus
import com.masterchief.agent.ui.theme.CortanaBlue
import com.masterchief.agent.ui.theme.HaloCyan
import com.masterchief.agent.ui.theme.HaloGreen
import com.masterchief.agent.ui.theme.NavyLine
import com.masterchief.agent.ui.theme.NavyPanel
import com.masterchief.agent.ui.theme.SpartanGold
import com.masterchief.agent.ui.theme.TextMuted

/**
 * The AI Studio HUD look, rebuilt natively in Compose:
 * dark navy/black, gold/amber accents, scanlines, Cortana hologram header,
 * MJOLNIR shield gauge. Every number shown comes from the real device.
 */

/** Faint horizontal scanlines over the whole screen, like the AI Studio HUD. */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.025f),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1f
            )
            y += 28f
        }
    }
}

/** Top bar: MASTER CHIEF / SIERRA-117, online status, Cortana waveform. */
@Composable
fun HudHeader(device: DeviceStatus?, brainMode: String, lastBrainUsed: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "MASTER CHIEF",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SpartanGold
                )
                Text(
                    "SIERRA-117  //  UNSC",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(
                        if (device?.online == true) HaloGreen else Color(0xFFFF5252),
                        RoundedCornerShape(5.dp)
                    )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (device?.online == true) "ONLINE" else "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (device?.online == true) HaloGreen else Color(0xFFFF5252)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HaloRing(sizeDp = 64)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CORTANA // COMM LINK", style = MaterialTheme.typography.labelMedium, color = CortanaBlue)
                Spacer(Modifier.height(4.dp))
                // Audio waveform bars (static shape, decorative only).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(8, 16, 24, 12, 28, 18, 10, 22, 14, 26, 12, 8, 18, 10).forEach { h ->
                        Box(
                            Modifier.width(4.dp).height(h.dp)
                                .background(CortanaBlue.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Brain: $brainMode  •  Last used: $lastBrainUsed",
                    style = MaterialTheme.typography.labelMedium, color = TextMuted
                )
            }
        }
    }
}

/**
 * MJOLNIR shield gauge. The "shield charge" is the phone's real battery
 * level — labelled as such, never an invented shield percentage.
 */
@Composable
fun ShieldGauge(batteryPct: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val pct = batteryPct.coerceIn(0, 100)
    val arcColor = when {
        pct > 50 -> HaloGreen
        pct > 20 -> SpartanGold
        else -> Color(0xFFFF5252)
    }
    Box(modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = NavyLine, startAngle = 135f, sweepAngle = 270f,
                useCenter = false, style = Stroke(width = 26f, cap = StrokeCap.Round)
            )
            drawArc(
                color = arcColor, startAngle = 135f, sweepAngle = 270f * pct / 100f,
                useCenter = false, style = Stroke(width = 26f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$pct%",
                style = MaterialTheme.typography.headlineMedium,
                color = arcColor
            )
            Text(
                "SHIELD / BATTERY",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            if (charging) Text("CHARGING", style = MaterialTheme.typography.labelMedium, color = HaloCyan)
        }
    }
}

/** A bordered HUD panel with a gold section title. */
@Composable
fun HudPanel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyPanel),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NavyLine)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SpartanGold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** One label/value readout row. */
@Composable
fun ReadoutRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold
        )
    }
}

/** Real device readouts — model, Android, CPU, battery, storage, RAM, network. */
@Composable
fun DeviceReadouts(device: DeviceStatus?) {
    HudPanel("DEVICE // ${device?.model ?: "READING…"}") {
        if (device == null) {
            Text("Reading device status…", style = MaterialTheme.typography.bodyMedium)
        } else {
            ReadoutRow("MANUFACTURER", device.manufacturer.uppercase())
            ReadoutRow("ANDROID", "${device.androidVersion}  (SDK ${device.sdk})")
            ReadoutRow("CPU CORES", device.cpuCores.toString())
            ReadoutRow(
                "BATTERY",
                "${device.batteryPct}%${if (device.charging) " — charging" else ""}"
            )
            ReadoutRow(
                "STORAGE FREE",
                "${device.storageFreeBytes / 1_048_576} MB of ${device.storageTotalBytes / 1_048_576} MB"
            )
            ReadoutRow(
                "RAM AVAILABLE",
                "${device.ramAvailBytes / 1_048_576} MB of ${device.ramTotalBytes / 1_048_576} MB"
            )
            ReadoutRow("NETWORK", if (device.online) "CONNECTED" else "OFFLINE")
        }
    }
}

/** Gold divider line, HUD style. */
@Composable
fun HudDivider() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(SpartanGold.copy(alpha = 0.35f))
    )
}
