package com.cooled.data.persistence

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RememberedDeviceStoreTest {
    @Test
    fun rememberPutsMostRecentFirstAndDeduplicates() = runBlocking {
        val store = InMemoryRememberedDeviceStore()
        store.remember("AA:AA:AA:AA:AA:AA")
        store.remember("BB:BB:BB:BB:BB:BB")
        store.remember("AA:AA:AA:AA:AA:AA") // re-connecting to A should move it back to the front, not duplicate it

        assertEquals(listOf("AA:AA:AA:AA:AA:AA", "BB:BB:BB:BB:BB:BB"), store.all().first())
    }

    @Test
    fun rememberCapsAtMaxRemembered() = runBlocking {
        val store = InMemoryRememberedDeviceStore()
        repeat(InMemoryRememberedDeviceStore.MAX_REMEMBERED + 5) { i -> store.remember("device-$i") }

        val all = store.all().first()
        assertEquals(InMemoryRememberedDeviceStore.MAX_REMEMBERED, all.size)
        // most recently remembered should survive the cap, oldest should be evicted
        assertEquals("device-${InMemoryRememberedDeviceStore.MAX_REMEMBERED + 4}", all.first())
    }
}
