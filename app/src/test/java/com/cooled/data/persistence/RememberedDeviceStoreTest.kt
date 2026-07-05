package com.cooled.data.persistence

import com.cooled.core.ble.LedScanMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RememberedDeviceStoreTest {
    @Test
    fun rememberPutsMostRecentFirstAndDeduplicates() = runBlocking {
        val store = InMemoryRememberedDeviceStore()
        store.remember(RememberedDevice("AA:AA:AA:AA:AA:AA", "CoolLEDUX-A"))
        store.remember(RememberedDevice("BB:BB:BB:BB:BB:BB", "CoolLEDU-B"))
        store.remember(RememberedDevice("AA:AA:AA:AA:AA:AA", "CoolLEDUX-A")) // re-connecting to A should move it back to the front, not duplicate it

        assertEquals(
            listOf("AA:AA:AA:AA:AA:AA", "BB:BB:BB:BB:BB:BB"),
            store.all().first().map { it.address }
        )
    }

    @Test
    fun rememberCapsAtMaxRemembered() = runBlocking {
        val store = InMemoryRememberedDeviceStore()
        repeat(InMemoryRememberedDeviceStore.MAX_REMEMBERED + 5) { i -> store.remember(RememberedDevice("device-$i")) }

        val all = store.all().first()
        assertEquals(InMemoryRememberedDeviceStore.MAX_REMEMBERED, all.size)
        // most recently remembered should survive the cap, oldest should be evicted
        assertEquals("device-${InMemoryRememberedDeviceStore.MAX_REMEMBERED + 4}", all.first().address)
    }

    @Test
    fun rememberPersistsNameAndMatrixMetadataNotJustTheAddress() = runBlocking {
        // Regression test: reconnecting via the "recently connected" shortcut
        // used to always pass name=null and empty metadata because
        // AppViewModel looked them up from the (necessarily empty, for this
        // exact case) *current* scan results list instead of storing them at
        // connect time. That made FamilyDetector.detect(null) return
        // DeviceFamily.UNKNOWN, silently routing every send through the
        // unverified placeholder text encoder.
        val store = InMemoryRememberedDeviceStore()
        val metadata = LedScanMetadata(rows = 16, columns = 64, colorType = 2)
        store.remember(RememberedDevice("AA:AA:AA:AA:AA:AA", "CoolLEDUX-Panel", metadata))

        val remembered = store.all().first().single()
        assertEquals("CoolLEDUX-Panel", remembered.name)
        assertEquals(metadata, remembered.metadata)
    }

    @Test
    fun rememberedDeviceDefaultsToNullNameAndEmptyMetadata() = runBlocking {
        val store = InMemoryRememberedDeviceStore()
        store.remember(RememberedDevice("AA:AA:AA:AA:AA:AA"))

        val remembered = store.all().first().single()
        assertNull(remembered.name)
        assertEquals(LedScanMetadata(), remembered.metadata)
    }
}
