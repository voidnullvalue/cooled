package com.cooled.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cooled.core.ble.ScanDevice
import com.cooled.data.persistence.RememberedDevice
import com.cooled.ui.components.StatChip

@Composable
fun ConnectScreen(
    scanning: Boolean,
    scanResults: List<ScanDevice>,
    rememberedDevices: List<RememberedDevice>,
    transportMode: String,
    hasBlePermissions: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (ScanDevice) -> Unit,
    onReconnect: (String) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAppPermissionSettings: () -> Unit
) {
    val scannedAddresses = remember(scanResults) { scanResults.map { it.address }.toSet() }
    val reconnectOnly = remember(rememberedDevices, scannedAddresses) { rememberedDevices.filterNot { it.address in scannedAddresses } }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("cooled", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "A free, from-scratch reimplementation of the CoolLED BLE controller. Find a nearby light to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            StatChip("Transport: $transportMode")
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = onScan, enabled = hasBlePermissions && !scanning, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(0.dp))
                Text(if (scanning) "  Scanning..." else "  Scan for devices")
            }
            OutlinedButton(onClick = onStopScan, enabled = hasBlePermissions && scanning) { Text("Stop") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistIconButton(Icons.Filled.LocationOn, "Location", onOpenLocationSettings)
            AssistIconButton(Icons.Filled.Bluetooth, "Bluetooth", onOpenBluetoothSettings)
            AssistIconButton(Icons.Filled.Settings, "App permissions", onOpenAppPermissionSettings)
        }

        if (reconnectOnly.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recently connected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                reconnectOnly.forEach { device ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(device.name ?: device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedButton(onClick = { onReconnect(device.address) }, enabled = hasBlePermissions) { Text("Connect") }
                    }
                }
            }
        }

        if (scanResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (scanning) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Looking for lights nearby...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("No devices found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Make sure the light is powered on and in range, then tap Scan.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scanResults, key = { it.address }) { device ->
                    DeviceCard(device, onClick = { onConnect(device) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: ScanDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Unnamed device", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val meta = device.metadata
                if (meta.hasMatrix || meta.colorType != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (meta.hasMatrix) StatChip("${meta.columns}x${meta.rows}")
                        if (meta.colorType != null) StatChip("color ${meta.colorType}")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Filled.SignalCellularAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text("${device.rssi}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AssistIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text("  $label", style = MaterialTheme.typography.labelMedium)
    }
}
