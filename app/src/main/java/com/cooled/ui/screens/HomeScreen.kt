package com.cooled.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cooled.core.ble.ConnectionState
import com.cooled.core.model.DeviceCapabilities
import com.cooled.core.model.DeviceFamily
import com.cooled.ui.components.LabeledSlider
import com.cooled.ui.components.MatrixPreview
import com.cooled.ui.components.NumberField
import com.cooled.ui.components.SectionCard
import com.cooled.ui.components.StatChip

@Composable
fun HomeScreen(
    connection: ConnectionState,
    mtu: Int,
    family: DeviceFamily,
    capabilities: DeviceCapabilities,
    columns: Int?,
    rows: Int?,
    isOn: Boolean,
    brightness: Float,
    colorMode: String,
    driveState: String,
    onDisconnect: () -> Unit,
    onPower: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSendBrightness: (Int) -> Unit,
    onSpeed: (Int) -> Unit,
    onMirror: (Int) -> Unit,
    onColorModeChange: (String) -> Unit,
    onSendColorMode: () -> Unit,
    onQueryInfo: () -> Unit,
    onQueryOta: () -> Unit,
    onDriveStateChange: (String) -> Unit,
    onQueryDriveState: () -> Unit,
    onSetDriveState: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(family.name.lowercase().replaceFirstChar { it.uppercase() }, emphasized = true)
                    StatChip(connectionLabel(connection))
                    StatChip("MTU $mtu")
                }
                MatrixPreview(columns = columns, rows = rows, on = isOn, brightness = brightness.toInt())
            }
        }

        item {
            SectionCard("Power & motion", icon = Icons.Filled.Power) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onPower(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Icon(Icons.Filled.Power, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  On") }
                    OutlinedButton(onClick = { onPower(false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PowerOff, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  Off")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onSpeed(1) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  Music")
                    }
                    OutlinedButton(onClick = { onSpeed(2) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.MicNone, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  Mic")
                    }
                    OutlinedButton(onClick = { onMirror(1) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.FlipCameraAndroid, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  Mirror")
                    }
                }
                LabeledSlider("Brightness", brightness, onBrightnessChange, 1f..100f)
                Button(onClick = { onSendBrightness(brightness.toInt()) }, modifier = Modifier.fillMaxWidth()) { Text("Apply brightness") }
            }
        }

        if (capabilities.supportsColorModes) {
            item {
                SectionCard(
                    "Color mode",
                    icon = Icons.Filled.Palette,
                    subtitle = "Style 1-31. Sends the device a full color pattern, not just a number - see below for what each style looks like."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(colorMode, onColorModeChange, "Style")
                        Button(onClick = onSendColorMode) { Text("Apply") }
                    }
                    Text(
                        colorMode.toIntOrNull()?.let { com.cooled.core.protocol.ColorModeTables.describe(it) } ?: "Enter a style number 1-31",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard("Device", icon = Icons.Filled.Info) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onQueryInfo, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  Info")
                    }
                    if (capabilities.supportsOta) {
                        OutlinedButton(onClick = onQueryOta, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  OTA")
                        }
                    }
                }
                if (capabilities.supportsDriveState) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onQueryDriveState) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.height(18.dp)); Text("  Query drive")
                        }
                        NumberField(driveState, onDriveStateChange, "State")
                        Button(onClick = onSetDriveState) { Text("Set") }
                    }
                }
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.DISCONNECTED -> "Disconnected"
    ConnectionState.CONNECTING -> "Connecting..."
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.READY -> "Ready"
}
