package com.khaleel.glance.notification

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NotificationRetentionTest {
    private class Storage(var disk: Boolean) : NotificationRetentionStorage {
        var memory = disk
        var fail = false
        override fun read() = disk
        override fun write(value: Boolean): Boolean {
            memory = value // SharedPreferences updates memory even when commit fails.
            if (fail) return false
            disk = value
            return true
        }
    }

    @Test fun `successful retention choice survives recreation`() {
        val storage = Storage(true)
        val retention = NotificationRetention(storage)
        retention.keepVisible = false
        assertFalse(retention.keepVisible)
        assertFalse(NotificationRetention(storage).keepVisible)
    }

    @Test fun `failed save keeps accepted setting despite mutated preference memory`() {
        for (previous in listOf(true, false)) {
            val storage = Storage(previous).apply { fail = true }
            val retention = NotificationRetention(storage)
            try {
                retention.keepVisible = !previous
                fail("Failed save must be reported to the UI")
            } catch (_: IOException) { }
            assertEquals(!previous, storage.memory)
            assertEquals(previous, retention.keepVisible)
            assertEquals(previous, NotificationRetention(storage).keepVisible)
        }
    }

    @Test fun `a successful retry updates the accepted setting`() {
        val storage = Storage(true).apply { fail = true }
        val retention = NotificationRetention(storage)
        runCatching { retention.keepVisible = false }
        storage.fail = false
        retention.keepVisible = false
        assertFalse(retention.keepVisible)
        assertFalse(NotificationRetention(storage).keepVisible)
    }
}
