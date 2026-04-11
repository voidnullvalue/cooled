package com.cooled.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooled.core.ble.ConnectionState
import com.cooled.core.ble.FakeBleTransport
import com.cooled.core.model.CapabilityMap
import com.cooled.core.model.DeviceFamily
import com.cooled.core.protocol.ParsedPayload
import com.cooled.data.persistence.RememberedDeviceStore
import com.cooled.data.repositories.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val repo = DeviceRepository(FakeBleTransport(), RememberedDeviceStore())

    val scanResults = repo.scanResults.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val connection = repo.connectionState.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)
    val mtu = repo.mtu.stateIn(viewModelScope, SharingStarted.Eagerly, 23)
    val parsed = repo.parsedRx.stateIn(viewModelScope, SharingStarted.Eagerly, ParsedPayload.Unknown(byteArrayOf()))
    val family = MutableStateFlow(DeviceFamily.UNKNOWN)
    val capabilities = MutableStateFlow(CapabilityMap.forFamily(DeviceFamily.UNKNOWN))

    fun scan() = repo.startScan()
    fun connect(address: String, name: String?) = viewModelScope.launch {
        repo.connect(address)
        val f = repo.detectFamily(name)
        family.value = f
        capabilities.value = CapabilityMap.forFamily(f)
    }

    fun queryInfo() = viewModelScope.launch { repo.sendQueryInfo() }
    fun power(on: Boolean) = viewModelScope.launch { repo.sendPower(on) }
    fun brightness(v: Int) = viewModelScope.launch { repo.sendBrightness(v) }
    fun speed(v: Int) = viewModelScope.launch { repo.sendRhythm(v) }
    fun mirror(v: Int) = viewModelScope.launch { repo.sendMirror(v) }
    fun checkPassword(p: String) = viewModelScope.launch { repo.sendCheckPassword(p) }
    fun setPassword(p: String) = viewModelScope.launch { repo.sendSetPassword(p) }
}
