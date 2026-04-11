package com.cooled.data.persistence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RememberedDeviceStore {
    private val remembered = MutableStateFlow<List<String>>(emptyList())
    fun remember(address: String) { remembered.value = (remembered.value + address).distinct() }
    fun all() = remembered.asStateFlow()
}
