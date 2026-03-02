package com.monday8am.edgelab.copilot.ui.screens.liveride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.edgelab.copilot.ui.theme.CyclingCopilotTheme
import com.monday8am.edgelab.presentation.liveride.HudMetrics

@Composable
fun HudStrip(
    metrics: HudMetrics,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudCell(value = "%.1f".format(metrics.speed), label = "km/h")
            HudCell(value = "%.1f".format(metrics.distance), label = "km")
            HudCell(value = metrics.power?.let { "${it}W" } ?: "\u2013", label = "power")
            HudCell(value = "${metrics.batteryPercent}%", label = "battery")
        }
    }
}

@Composable
private fun HudCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HudStripPreview() {
    CyclingCopilotTheme(dynamicColor = false) {
        HudStrip(
            metrics =
                HudMetrics(
                    speed = 28.3f,
                    distance = 14.7f,
                    power = 187,
                    batteryPercent = 72,
                )
        )
    }
}

@Preview(showBackground = true, name = "No power")
@Composable
private fun HudStripNoPowerPreview() {
    CyclingCopilotTheme(dynamicColor = false) {
        HudStrip(
            metrics =
                HudMetrics(
                    speed = 0f,
                    distance = 0f,
                    power = null,
                    batteryPercent = 100,
                )
        )
    }
}
