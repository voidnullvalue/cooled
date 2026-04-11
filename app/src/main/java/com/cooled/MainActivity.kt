package com.cooled

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cooled.ui.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppScreen() } }
    }
}

@Composable
fun AppScreen(vm: AppViewModel = viewModel()) {
    val scans by vm.scanResults.collectAsState()
    val state by vm.connection.collectAsState()
    val mtu by vm.mtu.collectAsState()
    val family by vm.family.collectAsState()
    val caps by vm.capabilities.collectAsState()
    val parsed by vm.parsed.collectAsState()

    var brightness by remember { mutableStateOf(80f) }
    var password by remember { mutableStateOf("1234") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::scan) { Text("Scan") }
            Button(onClick = vm::queryInfo) { Text("Info") }
            Button(onClick = { vm.power(true) }) { Text("On") }
            Button(onClick = { vm.power(false) }) { Text("Off") }
        }
        Text("State=$state MTU=$mtu Family=$family")
        Text("Caps: clock=${caps.supportsClock} scoreboard=${caps.supportsScoreboard} colorModes=${caps.supportsColorModes}")

        Text("Brightness")
        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 1f..100f)
        Button(onClick = { vm.brightness(brightness.toInt()) }) { Text("Send Brightness") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.speed(1) }) { Text("Music") }
            Button(onClick = { vm.speed(2) }) { Text("Mic") }
            Button(onClick = { vm.mirror(1) }) { Text("Mirror") }
            Button(onClick = { vm.mirror(0) }) { Text("Rotate0") }
        }

        TextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password (hex-ish digits)") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.checkPassword(password) }) { Text("Check Password") }
            Button(onClick = { vm.setPassword(password) }) { Text("Set Password") }
        }

        Text("Debug decode: $parsed")
        Text("Scan results")
        LazyColumn {
            items(scans) { d ->
                Button(onClick = { vm.connect(d.address, d.name) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${d.name ?: "Unnamed"} (${d.address}) RSSI=${d.rssi}")
                }
            }
        }
    }
}
