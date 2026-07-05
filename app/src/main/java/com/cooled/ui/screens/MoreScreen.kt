package com.cooled.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Scoreboard
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cooled.core.model.DeviceCapabilities
import com.cooled.ui.components.LabeledSlider
import com.cooled.ui.components.NumberField
import com.cooled.ui.components.SectionCard
import com.cooled.ui.components.StatChip

@Composable
fun MoreScreen(
    capabilities: DeviceCapabilities,
    scoreboardLeft: String,
    onScoreboardLeftChange: (String) -> Unit,
    scoreboardRight: String,
    onScoreboardRightChange: (String) -> Unit,
    onScoreboardSet: () -> Unit,
    onScoreboardStart: () -> Unit,
    onScoreboardStop: () -> Unit,
    onQueryScoreboard: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onSendVolume: (Int) -> Unit,
    onQueryTomato: () -> Unit,
    onQueryTempHumidity: () -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onCheckPassword: () -> Unit,
    onSetPassword: () -> Unit,
    transportMode: String,
    isFakeMode: Boolean,
    onStartFakeTransfer: () -> Unit,
    onScriptHappy: () -> Unit,
    onScriptNack: () -> Unit,
    onScriptExhaust: () -> Unit,
    onTimeoutTick: () -> Unit,
    events: List<String>,
    onCopyDebugLog: () -> Unit
) {
    var showDebug by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (capabilities.supportsScoreboard) {
            item {
                SectionCard("Scoreboard", icon = Icons.Filled.Scoreboard) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(scoreboardLeft, onScoreboardLeftChange, "Left")
                        NumberField(scoreboardRight, onScoreboardRightChange, "Right")
                        Button(onClick = onScoreboardSet) { Text("Set") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onScoreboardStart, modifier = Modifier.weight(1f)) { Text("Start") }
                        OutlinedButton(onClick = onScoreboardStop, modifier = Modifier.weight(1f)) { Text("Stop") }
                        OutlinedButton(onClick = onQueryScoreboard, modifier = Modifier.weight(1f)) { Text("Query") }
                    }
                }
            }
        }

        if (capabilities.supportsVolume) {
            item {
                SectionCard("Volume", icon = Icons.AutoMirrored.Filled.VolumeUp) {
                    LabeledSlider("Volume", volume, onVolumeChange, 0f..100f)
                    Button(onClick = { onSendVolume(volume.toInt()) }, modifier = Modifier.fillMaxWidth()) { Text("Apply") }
                }
            }
        }

        if (capabilities.supportsTomato || capabilities.supportsTempHumidity) {
            item {
                SectionCard("Sensors", icon = Icons.Filled.Thermostat) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        if (capabilities.supportsTomato) {
                            OutlinedButton(onClick = onQueryTomato, modifier = Modifier.weight(1f)) { Text("Tomato timer") }
                        }
                        if (capabilities.supportsTempHumidity) {
                            OutlinedButton(onClick = onQueryTempHumidity, modifier = Modifier.weight(1f)) { Text("Temp/humidity") }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Password", icon = Icons.Filled.Password) {
                OutlinedTextField(value = password, onValueChange = onPasswordChange, label = { Text("Device password") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onCheckPassword, modifier = Modifier.weight(1f)) { Text("Check") }
                    OutlinedButton(onClick = onSetPassword, modifier = Modifier.weight(1f)) { Text("Set") }
                }
            }
        }

        item {
            SectionCard("Debug", icon = Icons.Filled.BugReport, subtitle = "Transport: $transportMode") {
                OutlinedButton(onClick = { showDebug = !showDebug }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showDebug) "Hide raw event log" else "Show raw event log (${events.size})")
                }
                if (showDebug) {
                    if (isFakeMode) {
                        Text("Fake transport scripted scenarios", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onStartFakeTransfer, modifier = Modifier.weight(1f)) { Text("Fake upload") }
                            OutlinedButton(onClick = onTimeoutTick, modifier = Modifier.weight(1f)) { Text("Timeout tick") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onScriptHappy, modifier = Modifier.weight(1f)) { Text("Happy path") }
                            OutlinedButton(onClick = onScriptNack, modifier = Modifier.weight(1f)) { Text("NACK retry") }
                            OutlinedButton(onClick = onScriptExhaust, modifier = Modifier.weight(1f)) { Text("Retry exhaust") }
                        }
                        HorizontalDivider()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatChip("${events.size} events")
                        OutlinedButton(onClick = onCopyDebugLog) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.height(14.dp))
                            Text("  Copy log")
                        }
                    }
                    events.take(60).forEach { e ->
                        Text(e, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
