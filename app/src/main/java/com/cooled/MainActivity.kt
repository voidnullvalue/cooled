package com.cooled

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cooled.ui.AppViewModel

private val CooledColors = darkColorScheme(
    background = Color(0xFF111014),
    surface = Color(0xFF111014),
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFF7E57C2),
    onPrimary = Color.White,
    secondary = Color(0xFFB39DDB),
    onSecondary = Color.White,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = CooledColors) { AppScreen() } }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel(factory = AppViewModel.androidBleFactory(context))
    val missingPermissions = rememberMissingBlePermissions()
    AppScreenContent(vm = vm, missingPermissions = missingPermissions)
}

@Composable
private fun rememberMissingBlePermissions(): List<String> {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var hasAutoRequested by remember { mutableStateOf(false) }
    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }
    val missing = permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(missing.joinToString(separator = "|")) {
        if (missing.isNotEmpty() && !hasAutoRequested) {
            hasAutoRequested = true
            launcher.launch(missing.toTypedArray())
        }
    }

    if (missing.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("BLE permissions are missing: ${missing.joinToString { permissionLabel(it) }}", color = Color.White)
            Text("Android may refuse to show the permission popup again after denial. Use App Permission Settings if Retry does not show a dialog.", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(missing.toTypedArray()) }) { Text("Retry Permission Prompt") }
                Button(onClick = { context.startActivity(appPermissionSettingsIntent(context.packageName)) }) { Text("App Permission Settings") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh++ }) { Text("Refresh Permission State") }
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Location Settings") }
            }
        }
    }
    refresh
    return missing
}

@Composable
private fun AppScreenContent(vm: AppViewModel, missingPermissions: List<String>) {
    val scans by vm.scanResults.collectAsState()
    val state by vm.connection.collectAsState()
    val mtu by vm.mtu.collectAsState()
    val family by vm.family.collectAsState()
    val caps by vm.capabilities.collectAsState()
    val parsed by vm.parsed.collectAsState()
    val transfer by vm.transferState.collectAsState()
    val events by vm.events.collectAsState()
    val transportMode by vm.transportMode.collectAsState()

    var brightness by remember { mutableStateOf(80f) }
    var password by remember { mutableStateOf("1234") }
    val hasBlePermissions = missingPermissions.isEmpty()
    val isFakeMode = transportMode == "Fake demo"
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WhiteText("Mode=$transportMode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::scan, enabled = hasBlePermissions) { Text("Scan") }
            Button(onClick = vm::stopScan, enabled = hasBlePermissions) { Text("Stop Scan") }
            Button(onClick = vm::disconnect, enabled = hasBlePermissions) { Text("Disconnect") }
            Button(onClick = vm::queryInfo, enabled = hasBlePermissions) { Text("Info") }
            Button(onClick = { vm.power(true) }, enabled = hasBlePermissions) { Text("On") }
            Button(onClick = { vm.power(false) }, enabled = hasBlePermissions) { Text("Off") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Location Settings") }
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth Settings") }
            Button(onClick = { context.startActivity(appPermissionSettingsIntent(context.packageName)) }) { Text("App Permissions") }
        }
        WhiteText("State=$state MTU=$mtu Family=$family")
        WhiteText("Transfer=$transfer")
        WhiteText("Caps: clock=${caps.supportsClock} alarms=${caps.supportsAlarms} reminders=${caps.supportsReminders} night=${caps.supportsNightMode} scoreboard=${caps.supportsScoreboard}")

        WhiteText("Brightness")
        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 1f..100f, enabled = hasBlePermissions)
        Button(onClick = { vm.brightness(brightness.toInt()) }, enabled = hasBlePermissions) { Text("Send Brightness") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.speed(1) }, enabled = hasBlePermissions) { Text("Music") }
            Button(onClick = { vm.speed(2) }, enabled = hasBlePermissions) { Text("Mic") }
            Button(onClick = { vm.mirror(1) }, enabled = hasBlePermissions) { Text("Mirror") }
            Button(onClick = { vm.mirror(0) }, enabled = hasBlePermissions) { Text("Rotate0") }
            Button(onClick = { vm.colorMode(5) }, enabled = hasBlePermissions && caps.supportsColorModes) { Text("ColorMode 5") }
        }

        TextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password (hex-ish digits)") },
            enabled = hasBlePermissions,
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.45f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedContainerColor = Color(0xFF2A2830),
                unfocusedContainerColor = Color(0xFF2A2830),
                disabledContainerColor = Color(0xFF2A2830)
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.checkPassword(password) }, enabled = hasBlePermissions) { Text("Check Password") }
            Button(onClick = { vm.setPassword(password) }, enabled = hasBlePermissions) { Text("Set Password") }
        }

        if (caps.supportsClock) {
            WhiteText("Clock Controls")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::syncTimeNow, enabled = hasBlePermissions) { Text("Sync Time") }
                Button(onClick = { vm.timer(10, true) }, enabled = hasBlePermissions) { Text("Timer 10m") }
                Button(onClick = { vm.volume(60) }, enabled = hasBlePermissions && caps.supportsVolume) { Text("Volume 60") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.countdown(true) }, enabled = hasBlePermissions && caps.supportsCountdown) { Text("Countdown Start") }
                Button(onClick = { vm.resetCountdown() }, enabled = hasBlePermissions && caps.supportsCountdown) { Text("Countdown Reset") }
                Button(onClick = { vm.stopwatch(true) }, enabled = hasBlePermissions && caps.supportsStopwatch) { Text("Stopwatch Start") }
                Button(onClick = { vm.resetStopwatch() }, enabled = hasBlePermissions && caps.supportsStopwatch) { Text("Stopwatch Reset") }
                Button(onClick = { vm.scoreboard(true) }, enabled = hasBlePermissions && caps.supportsScoreboard) { Text("Scoreboard Start") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::queryTomato, enabled = hasBlePermissions && caps.supportsTomato) { Text("Tomato") }
                Button(onClick = vm::queryTempHumidity, enabled = hasBlePermissions && caps.supportsTempHumidity) { Text("Temp/Humidity") }
                Button(onClick = vm::queryAlarms, enabled = hasBlePermissions && caps.supportsAlarms) { Text("Query Alarms") }
                Button(onClick = vm::setSampleAlarm, enabled = hasBlePermissions && caps.supportsAlarms) { Text("Set Alarm") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::setNightMode, enabled = hasBlePermissions && caps.supportsNightMode) { Text("Night Mode") }
                Button(onClick = vm::queryReminderList, enabled = hasBlePermissions && caps.supportsReminders) { Text("Reminders") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::startFakeTransfer, enabled = isFakeMode) { Text("Start Fake Transfer") }
            Button(onClick = vm::sendTextProgram, enabled = hasBlePermissions) { Text("Upload Text Program") }
            Button(onClick = vm::timeoutTransfer) { Text("Timeout Tick") }
            Button(onClick = vm::cancelTransfer) { Text("Cancel Transfer") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.scriptTransferScenario("happy") }, enabled = isFakeMode) { Text("Script Happy") }
            Button(onClick = { vm.scriptTransferScenario("nack_then_success") }, enabled = isFakeMode) { Text("Script NACK") }
            Button(onClick = { vm.scriptTransferScenario("retry_exhaust") }, enabled = isFakeMode) { Text("Script Exhaust") }
        }

        WhiteText("Parsed packet: $parsed")
        WhiteText("Debug events (latest first)")
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(events) { e -> WhiteText(e) }
        }

        WhiteText("Scan results")
        LazyColumn {
            items(scans) { d ->
                Button(onClick = { vm.connect(d.address, d.name) }, enabled = hasBlePermissions, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${d.name ?: "Unnamed"} (${d.address}) RSSI=${d.rssi}")
                }
            }
        }
    }
}

private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
    Manifest.permission.BLUETOOTH_SCAN -> "Nearby devices scan"
    Manifest.permission.BLUETOOTH_CONNECT -> "Nearby devices connect"
    else -> permission.substringAfterLast('.')
}

private fun appPermissionSettingsIntent(packageName: String): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.fromParts("package", packageName, null)
}

@Composable
private fun WhiteText(value: String) {
    Text(value, color = Color.White)
}