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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cooled.core.assets.OriginalLedAssetCatalogs
import com.cooled.core.ble.ConnectionState
import com.cooled.ui.AppViewModel
import com.cooled.ui.screens.ClockScreen
import com.cooled.ui.screens.ClockScreenState
import com.cooled.ui.screens.ConnectScreen
import com.cooled.ui.screens.DisplayScreen
import com.cooled.ui.screens.HomeScreen
import com.cooled.ui.screens.MoreScreen
import com.cooled.ui.theme.CooledTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CooledTheme { AppScreen() } }
    }
}

private enum class Tab(val label: String) { HOME("Home"), DISPLAY("Display"), CLOCK("Clock"), MORE("More") }

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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    LaunchedEffect(missing.joinToString(separator = "|")) {
        if (missing.isNotEmpty() && !hasAutoRequested) {
            hasAutoRequested = true
            launcher.launch(missing.toTypedArray())
        }
    }
    refresh
    return missing
}

@Composable
private fun MissingPermissionsBanner(missing: List<String>) {
    if (missing.isEmpty()) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Bluetooth permissions are missing: ${missing.joinToString { permissionLabel(it) }}",
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            "Android may refuse to show the permission popup again after denial - use App Permission Settings if Retry does nothing.",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { launcher.launch(missing.toTypedArray()) }) { Text("Retry") }
            Button(onClick = { context.startActivity(appPermissionSettingsIntent(context.packageName)) }) { Text("App settings") }
        }
    }
}

@Composable
private fun AppScreenContent(vm: AppViewModel, missingPermissions: List<String>) {
    val scans by vm.scanResults.collectAsState()
    val connection by vm.connection.collectAsState()
    val mtu by vm.mtu.collectAsState()
    val family by vm.family.collectAsState()
    val caps by vm.capabilities.collectAsState()
    val transfer by vm.transferState.collectAsState()
    val events by vm.events.collectAsState()
    val transportMode by vm.transportMode.collectAsState()
    val connectedMetadata by vm.connectedDeviceMetadata.collectAsState()
    val hasBlePermissions = missingPermissions.isEmpty()
    val isFakeMode = transportMode == "Fake demo"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var isScanning by rememberSaveable { mutableStateOf(false) }
    var isOn by rememberSaveable { mutableStateOf(true) }
    var brightness by rememberSaveable { mutableStateOf(80f) }
    var volume by rememberSaveable { mutableStateOf(60f) }
    var colorMode by rememberSaveable { mutableStateOf("5") }
    var driveState by rememberSaveable { mutableStateOf("0") }
    var password by rememberSaveable { mutableStateOf("1234") }
    var uploadText by rememberSaveable { mutableStateOf("HELLO") }
    var uploadSpeed by rememberSaveable { mutableStateOf("255") }
    var uploadEffect by rememberSaveable { mutableStateOf("2") }
    var scoreboardLeft by rememberSaveable { mutableStateOf("0") }
    var scoreboardRight by rememberSaveable { mutableStateOf("0") }
    var assetUploadPath by rememberSaveable { mutableStateOf("") }
    var assetUploadKind by rememberSaveable { mutableStateOf("payload-asset") }
    var assetRefresh by rememberSaveable { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(Tab.HOME) }

    val clockState = remember {
        ClockScreenState(
            timerHour = "8", timerMinute = "0", timerWeekdayMask = "127",
            countdownHour = "0", countdownMinute = "10", countdownSecond = "0",
            alarmHour = "7", alarmMinute = "30", alarmRepeatMask = "62", alarmDurationSeconds = "600", alarmReminderMinutes = "5",
            nightStartHour = "22", nightStartMinute = "0", nightEndHour = "6", nightEndMinute = "0",
            reminderId = "0"
        )
    }

    val ledAssetSummary = remember(assetRefresh, transportMode) { OriginalLedAssetCatalogs.active.summary() }
    val isConnected = connection == ConnectionState.CONNECTED || connection == ConnectionState.READY

    Scaffold(
        bottomBar = {
            if (isConnected) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == Tab.HOME,
                        onClick = { selectedTab = Tab.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(Tab.HOME.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.DISPLAY,
                        onClick = { selectedTab = Tab.DISPLAY },
                        icon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                        label = { Text(Tab.DISPLAY.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.CLOCK,
                        onClick = { selectedTab = Tab.CLOCK },
                        icon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                        label = { Text(Tab.CLOCK.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.MORE,
                        onClick = { selectedTab = Tab.MORE },
                        icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                        label = { Text(Tab.MORE.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            MissingPermissionsBanner(missingPermissions)

            if (!isConnected) {
                ConnectScreen(
                    scanning = isScanning,
                    scanResults = scans,
                    transportMode = transportMode,
                    hasBlePermissions = hasBlePermissions,
                    onScan = { isScanning = true; vm.scan() },
                    onStopScan = { isScanning = false; vm.stopScan() },
                    onConnect = { device -> isScanning = false; vm.stopScan(); vm.connect(device.address, device.name) },
                    onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    onOpenAppPermissionSettings = { context.startActivity(appPermissionSettingsIntent(context.packageName)) }
                )
            } else {
                when (selectedTab) {
                    Tab.HOME -> HomeScreen(
                        connection = connection,
                        mtu = mtu,
                        family = family,
                        capabilities = caps,
                        columns = connectedMetadata.columns,
                        rows = connectedMetadata.rows,
                        isOn = isOn,
                        brightness = brightness,
                        colorMode = colorMode,
                        driveState = driveState,
                        onDisconnect = { vm.disconnect(); selectedTab = Tab.HOME },
                        onPower = { on -> isOn = on; vm.power(on) },
                        onBrightnessChange = { brightness = it },
                        onSendBrightness = { vm.brightness(it) },
                        onSpeed = { vm.speed(it) },
                        onMirror = { vm.mirror(it) },
                        onColorModeChange = { colorMode = it },
                        onSendColorMode = { vm.colorMode(colorMode.toIntOrNull() ?: 0) },
                        onQueryInfo = { vm.queryInfo() },
                        onQueryOta = { vm.queryOtaVersion() },
                        onDriveStateChange = { driveState = it },
                        onQueryDriveState = { vm.queryDriveState() },
                        onSetDriveState = { vm.setDriveState(driveState.toIntOrNull() ?: 0) }
                    )

                    Tab.DISPLAY -> DisplayScreen(
                        uploadText = uploadText,
                        onUploadTextChange = { uploadText = it },
                        uploadSpeed = uploadSpeed,
                        onUploadSpeedChange = { uploadSpeed = it },
                        uploadEffect = uploadEffect,
                        onUploadEffectChange = { uploadEffect = it },
                        onSendTextProgram = { vm.sendTextProgram(uploadText, uploadSpeed.toIntOrNull() ?: 255, uploadEffect.toIntOrNull() ?: 2, null, null) },
                        assetSummary = ledAssetSummary,
                        assetUploadPath = assetUploadPath,
                        onAssetSelected = { asset -> assetUploadPath = asset.path; assetUploadKind = asset.kind },
                        onClearAsset = { assetUploadPath = ""; assetUploadKind = "payload-asset" },
                        onSendAssetProgram = {
                            vm.sendOriginalAssetProgram(assetUploadPath, assetUploadKind, uploadSpeed.toIntOrNull() ?: 255, uploadEffect.toIntOrNull() ?: 2, null, null)
                        },
                        transferState = transfer,
                        onCancelTransfer = { vm.cancelTransfer() }
                    )

                    Tab.CLOCK -> ClockScreen(
                        capabilities = caps,
                        state = clockState,
                        onSyncTime = { vm.syncTimeNow() },
                        onSetTimerOn = { vm.setTimerSwitch(true, clockState.timerHour.toIntOrNull() ?: 8, clockState.timerMinute.toIntOrNull() ?: 0, clockState.timerWeekdayMask.toIntOrNull() ?: 127, true) },
                        onSetTimerOff = { vm.setTimerSwitch(true, clockState.timerHour.toIntOrNull() ?: 8, clockState.timerMinute.toIntOrNull() ?: 0, clockState.timerWeekdayMask.toIntOrNull() ?: 127, false) },
                        onDisableTimer = { vm.setTimerSwitch(false, clockState.timerHour.toIntOrNull() ?: 8, clockState.timerMinute.toIntOrNull() ?: 0, clockState.timerWeekdayMask.toIntOrNull() ?: 127, false) },
                        onQueryTimerSwitches = { vm.queryTimerSwitches() },
                        onCountdownStart = { vm.countdown(true) },
                        onCountdownStop = { vm.countdown(false) },
                        onCountdownResetTo = { vm.resetCountdownTo(clockState.countdownHour.toIntOrNull() ?: 0, clockState.countdownMinute.toIntOrNull() ?: 10, clockState.countdownSecond.toIntOrNull() ?: 0) },
                        onQueryCountdown = { vm.queryCountdownStatus() },
                        onStopwatchStart = { vm.stopwatch(true) },
                        onStopwatchStop = { vm.stopwatch(false) },
                        onStopwatchReset = { vm.resetStopwatch() },
                        onQueryStopwatch = { vm.queryStopwatchStatus() },
                        onSetAlarm = {
                            vm.setAlarm(true, clockState.alarmHour.toIntOrNull() ?: 7, clockState.alarmMinute.toIntOrNull() ?: 30, clockState.alarmRepeatMask.toIntOrNull() ?: 62, clockState.alarmDurationSeconds.toIntOrNull() ?: 600, clockState.alarmReminderMinutes.toIntOrNull() ?: 5)
                        },
                        onDisableAlarm = {
                            vm.setAlarm(false, clockState.alarmHour.toIntOrNull() ?: 7, clockState.alarmMinute.toIntOrNull() ?: 30, clockState.alarmRepeatMask.toIntOrNull() ?: 62, clockState.alarmDurationSeconds.toIntOrNull() ?: 600, clockState.alarmReminderMinutes.toIntOrNull() ?: 5)
                        },
                        onQueryAlarms = { vm.queryAlarms() },
                        onSetNightMode = { vm.setNightMode(true, clockState.nightStartHour.toIntOrNull() ?: 22, clockState.nightStartMinute.toIntOrNull() ?: 0, clockState.nightEndHour.toIntOrNull() ?: 6, clockState.nightEndMinute.toIntOrNull() ?: 0) },
                        onDisableNightMode = { vm.setNightMode(false, clockState.nightStartHour.toIntOrNull() ?: 22, clockState.nightStartMinute.toIntOrNull() ?: 0, clockState.nightEndHour.toIntOrNull() ?: 6, clockState.nightEndMinute.toIntOrNull() ?: 0) },
                        onQueryReminderList = { vm.queryReminderList() },
                        onQueryReminderDetail = { vm.queryReminderDetail(clockState.reminderId.toIntOrNull() ?: 0) },
                        onDeleteReminder = { vm.deleteReminder(clockState.reminderId.toIntOrNull() ?: 0) }
                    )

                    Tab.MORE -> MoreScreen(
                        capabilities = caps,
                        scoreboardLeft = scoreboardLeft,
                        onScoreboardLeftChange = { scoreboardLeft = it },
                        scoreboardRight = scoreboardRight,
                        onScoreboardRightChange = { scoreboardRight = it },
                        onScoreboardSet = { vm.resetScoreboard(scoreboardLeft.toIntOrNull() ?: 0, scoreboardRight.toIntOrNull() ?: 0) },
                        onScoreboardStart = { vm.scoreboard(true) },
                        onScoreboardStop = { vm.scoreboard(false) },
                        onQueryScoreboard = { vm.queryScoreboardStatus() },
                        volume = volume,
                        onVolumeChange = { volume = it },
                        onSendVolume = { vm.volume(it) },
                        onQueryTomato = { vm.queryTomato() },
                        onQueryTempHumidity = { vm.queryTempHumidity() },
                        password = password,
                        onPasswordChange = { password = it },
                        onCheckPassword = { vm.checkPassword(password) },
                        onSetPassword = { vm.setPassword(password) },
                        transportMode = transportMode,
                        isFakeMode = isFakeMode,
                        onStartFakeTransfer = { vm.startFakeTransfer() },
                        onScriptHappy = { vm.scriptTransferScenario("happy") },
                        onScriptNack = { vm.scriptTransferScenario("nack_then_success") },
                        onScriptExhaust = { vm.scriptTransferScenario("retry_exhaust") },
                        onTimeoutTick = { vm.timeoutTransfer() },
                        events = events,
                        onCopyDebugLog = { clipboard.setText(AnnotatedString(vm.copyDebugLog())) }
                    )
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

private fun appPermissionSettingsIntent(packageName: String): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", packageName, null) }
