package com.cooled.core.protocol

import java.util.UUID

object BleProtocolConstants {
    val serviceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val commandCharacteristicUuid: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val frameStart: Byte = 0x01
    const val frameEscape: Byte = 0x02
    const val frameEnd: Byte = 0x03

    val scanNames = listOf(
        "CoolLED536", "CoolLED", "CoolLEDX", "CoolLEDA", "CoolLEDS", "CoolLEDM", "CoolLEDU",
        "CoolLEDUX", "iDevilEyes", "iLedHat", "iLedHatC", "iLedOpen", "iLedCar", "iLedBike", "iLedClock"
    )
}
