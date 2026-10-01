package com.khaleel.glance.widget

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WidgetVisibilityTest {
    private class Storage(var persisted: Boolean = false) : WidgetVisibilityStorage {
        var writesSucceed = true
        var removalSucceeds = true
        var readFails = false
        override fun read(): Boolean {
            if (readFails) throw IOException("synthetic storage failure")
            return persisted
        }
        override fun write(value: Boolean): Boolean {
            if (writesSucceed) persisted = value
            return writesSucceed
        }
        override fun remove(): Boolean {
            if (removalSucceeds) persisted = false
            return removalSucceeds
        }
    }

    @Test fun `failed opt-out write removes previous grant and recreation stays hidden`() {
        val storage = Storage(true).apply { writesSucceed = false }
        val visibility = WidgetVisibility(storage)
        visibility.visible = false
        assertFalse(visibility.visible)
        assertFalse(WidgetVisibility(storage).visible)
    }

    @Test fun `failed opt-in is reported and never grants current or recreated visibility`() {
        val storage = Storage().apply { writesSucceed = false }
        val visibility = WidgetVisibility(storage)
        assertThrows(IOException::class.java) { visibility.visible = true }
        assertFalse(visibility.visible)
        assertFalse(WidgetVisibility(storage).visible)
    }

    @Test fun `total revocation failure is reported and rendering remains blocked`() {
        val storage = Storage(true).apply { writesSucceed = false; removalSucceeds = false }
        val visibility = WidgetVisibility(storage)
        assertThrows(IOException::class.java) { visibility.visible = false }
        assertFalse(visibility.visible)
        // No claim of durable success: caller must tell the user to remove the placed widgets.
        assertTrue(storage.persisted)
    }

    @Test fun `a read failure defaults to hidden instead of restoring a grant`() {
        assertFalse(WidgetVisibility(Storage(true).apply { readFails = true }).visible)
    }

    @Test fun `successful explicit choices survive recreation`() {
        val storage = Storage()
        val visibility = WidgetVisibility(storage)
        assertFalse(visibility.visible)
        visibility.visible = true
        assertTrue(WidgetVisibility(storage).visible)
        visibility.visible = false
        assertFalse(WidgetVisibility(storage).visible)
    }
}
