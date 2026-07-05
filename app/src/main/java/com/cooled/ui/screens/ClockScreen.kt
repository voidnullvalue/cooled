package com.cooled.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cooled.core.model.DeviceCapabilities
import com.cooled.ui.components.NumberField
import com.cooled.ui.components.SectionCard

data class ClockScreenState(
    var timerHour: String,
    var timerMinute: String,
    var timerWeekdayMask: String,
    var countdownHour: String,
    var countdownMinute: String,
    var countdownSecond: String,
    var alarmHour: String,
    var alarmMinute: String,
    var alarmRepeatMask: String,
    var alarmDurationSeconds: String,
    var alarmReminderMinutes: String,
    var nightStartHour: String,
    var nightStartMinute: String,
    var nightEndHour: String,
    var nightEndMinute: String,
    var reminderId: String
)

@Composable
fun ClockScreen(
    capabilities: DeviceCapabilities,
    state: ClockScreenState,
    onSyncTime: () -> Unit,
    onSetTimerOn: () -> Unit,
    onSetTimerOff: () -> Unit,
    onDisableTimer: () -> Unit,
    onQueryTimerSwitches: () -> Unit,
    onCountdownStart: () -> Unit,
    onCountdownStop: () -> Unit,
    onCountdownResetTo: () -> Unit,
    onQueryCountdown: () -> Unit,
    onStopwatchStart: () -> Unit,
    onStopwatchStop: () -> Unit,
    onStopwatchReset: () -> Unit,
    onQueryStopwatch: () -> Unit,
    onSetAlarm: () -> Unit,
    onDisableAlarm: () -> Unit,
    onQueryAlarms: () -> Unit,
    onSetNightMode: () -> Unit,
    onDisableNightMode: () -> Unit,
    onQueryNightMode: () -> Unit,
    onQueryReminderList: () -> Unit,
    onQueryReminderDetail: () -> Unit,
    onDeleteReminder: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Time", icon = Icons.Filled.Schedule) {
                Button(onClick = onSyncTime, modifier = Modifier.fillMaxWidth()) { Text("Sync device clock to phone time") }
            }
        }

        if (capabilities.supportsClock) {
            item {
                SectionCard("Daily on/off timer", icon = Icons.Filled.Timer) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(state.timerHour, { state.timerHour = it }, "Hour")
                        NumberField(state.timerMinute, { state.timerMinute = it }, "Minute")
                        NumberField(state.timerWeekdayMask, { state.timerWeekdayMask = it }, "Days mask")
                    }
                    TwoButtonRow("Set on-timer", onSetTimerOn, "Set off-timer", onSetTimerOff)
                    TwoButtonRow("Disable", onDisableTimer, "Query", onQueryTimerSwitches)
                }
            }
        }

        if (capabilities.supportsCountdown) {
            item {
                SectionCard("Countdown", icon = Icons.Filled.HourglassBottom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(state.countdownHour, { state.countdownHour = it }, "Hour")
                        NumberField(state.countdownMinute, { state.countdownMinute = it }, "Minute")
                        NumberField(state.countdownSecond, { state.countdownSecond = it }, "Second")
                    }
                    TwoButtonRow("Set & start", onCountdownResetTo, "Stop", onCountdownStop)
                    TwoButtonRow("Start", onCountdownStart, "Query", onQueryCountdown)
                }
            }
        }

        if (capabilities.supportsStopwatch) {
            item {
                SectionCard("Stopwatch", icon = Icons.Filled.HourglassBottom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onStopwatchStart, modifier = Modifier.weight(1f)) { Text("Start") }
                        OutlinedButton(onClick = onStopwatchStop, modifier = Modifier.weight(1f)) { Text("Stop") }
                        OutlinedButton(onClick = onStopwatchReset, modifier = Modifier.weight(1f)) { Text("Reset") }
                    }
                    Button(onClick = onQueryStopwatch, modifier = Modifier.fillMaxWidth()) { Text("Query status") }
                }
            }
        }

        if (capabilities.supportsAlarms) {
            item {
                SectionCard("Alarms", icon = Icons.Filled.Alarm) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(state.alarmHour, { state.alarmHour = it }, "Hour")
                        NumberField(state.alarmMinute, { state.alarmMinute = it }, "Minute")
                        NumberField(state.alarmRepeatMask, { state.alarmRepeatMask = it }, "Repeat")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(state.alarmDurationSeconds, { state.alarmDurationSeconds = it }, "Duration s")
                        NumberField(state.alarmReminderMinutes, { state.alarmReminderMinutes = it }, "Remind m")
                        OutlinedButton(onClick = onQueryAlarms) { Text("Query") }
                    }
                    TwoButtonRow("Set alarm", onSetAlarm, "Disable", onDisableAlarm)
                }
            }
        }

        if (capabilities.supportsReminders) {
            item {
                SectionCard("Reminders", icon = Icons.Filled.Notifications) {
                    NumberField(state.reminderId, { state.reminderId = it }, "ID")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onQueryReminderList, modifier = Modifier.weight(1f)) { Text("List") }
                        OutlinedButton(onClick = onQueryReminderDetail, modifier = Modifier.weight(1f)) { Text("Detail") }
                        OutlinedButton(onClick = onDeleteReminder, modifier = Modifier.weight(1f)) { Text("Delete") }
                    }
                }
            }
        }

        if (capabilities.supportsNightMode) {
            item {
                SectionCard("Night mode", icon = Icons.Filled.NightsStay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(state.nightStartHour, { state.nightStartHour = it }, "Start h")
                        NumberField(state.nightStartMinute, { state.nightStartMinute = it }, "Start m")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField(state.nightEndHour, { state.nightEndHour = it }, "End h")
                        NumberField(state.nightEndMinute, { state.nightEndMinute = it }, "End m")
                    }
                    TwoButtonRow("Enable", onSetNightMode, "Disable", onDisableNightMode)
                    OutlinedButton(onClick = onQueryNightMode, modifier = Modifier.fillMaxWidth()) { Text("Query") }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun TwoButtonRow(labelA: String, onA: () -> Unit, labelB: String, onB: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onA, modifier = Modifier.weight(1f)) { Text(labelA) }
        OutlinedButton(onClick = onB, modifier = Modifier.weight(1f)) { Text(labelB) }
    }
}
