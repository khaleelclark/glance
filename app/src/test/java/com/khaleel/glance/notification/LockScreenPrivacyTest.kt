package com.khaleel.glance.notification

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LockScreenPrivacyTest {
    private class Storage(var persisted: Boolean = false) : LockScreenPrivacyStorage {
        var memory = persisted
        var writeSucceeds = true
        var deleteSucceeds = true
        var readFails = false
        var writeThrows = false
        override fun read(): Boolean {
            if (readFails) throw IOException("synthetic read failure")
            return persisted
        }
        override fun write(value: Boolean): Boolean {
            memory = value // A failed commit still changes SharedPreferences memory.
            if (writeThrows) throw IOException("synthetic write failure")
            if (writeSucceeds) persisted = value
            return writeSucceeds
        }
        override fun remove(): Boolean {
            if (deleteSucceeds) { persisted = false; memory = false }
            return deleteSucceeds
        }
    }

    @Test fun `failed opt-out removes previous grant and recreation stays hidden`() {
        val storage = Storage(true).apply { writeSucceeds = false }
        val privacy = LockScreenVisibility(storage)
        privacy.visible = false
        assertFalse(privacy.visible)
        assertFalse(LockScreenVisibility(storage).visible)
    }

    @Test fun `failed opt-in is reported and never grants visibility after recreation`() {
        val storage = Storage().apply { writeSucceeds = false }
        val privacy = LockScreenVisibility(storage)
        assertThrows(IOException::class.java) { privacy.visible = true }
        assertFalse(privacy.visible)
        assertFalse(LockScreenVisibility(storage).visible)
    }

    @Test fun `write exception can revoke by deleting only the privacy grant`() {
        val storage = Storage(true).apply { writeThrows = true }
        val privacy = LockScreenVisibility(storage)
        privacy.visible = false
        assertFalse(LockScreenVisibility(storage).visible)
    }

    @Test fun `total revocation failure is reported and current rendering is hidden`() {
        val storage = Storage(true).apply { writeSucceeds = false; deleteSucceeds = false }
        val privacy = LockScreenVisibility(storage)
        assertThrows(IOException::class.java) { privacy.visible = false }
        assertFalse(privacy.visible)
        assertTrue(storage.persisted) // UI must direct the owner to Android lock-screen controls.
    }

    @Test fun `read failures are hidden`() {
        assertFalse(LockScreenVisibility(Storage(true).apply { readFails = true }).visible)
    }

    @Test fun `successful explicit opt-in and revocation survive recreation`() {
        val storage = Storage()
        val privacy = LockScreenVisibility(storage)
        assertFalse(privacy.visible)
        privacy.visible = true
        assertTrue(LockScreenVisibility(storage).visible)
        privacy.visible = false
        assertFalse(LockScreenVisibility(storage).visible)
    }
}
