package com.khaleel.glance.wear

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WatchGrantTest {
    private class Store(var disk: Boolean = false) : WatchGrantStorage {
        var writeWorks = true
        var deleteWorks = true
        var readThrows = false
        override fun read(): Boolean { if (readThrows) throw IOException(); return disk }
        override fun write(visible: Boolean): Boolean { if (writeWorks) disk = visible; return writeWorks }
        override fun remove(): Boolean { if (deleteWorks) disk = false; return deleteWorks }
    }
    @Test fun successfulChoicePersistsAcrossRecreation() {
        val store = Store(); val grant = WatchGrant(store)
        assertFalse(grant.visible); grant.visible = true
        assertTrue(WatchGrant(store).visible); grant.visible = false
        assertFalse(WatchGrant(store).visible)
    }
    @Test fun failedOptInCannotPublish() {
        val store = Store().apply { writeWorks = false }
        val grant = WatchGrant(store)
        try { grant.visible = true; fail() } catch (_: IOException) { }
        assertFalse(grant.visible); assertFalse(WatchGrant(store).visible)
    }
    @Test fun failedOptOutCanDeleteOnlyGrantAndStayHiddenAfterRecreation() {
        val store = Store(true).apply { writeWorks = false }
        val grant = WatchGrant(store); grant.visible = false
        assertFalse(grant.visible); assertFalse(WatchGrant(store).visible)
    }
    @Test fun allStorageFailureStaysHiddenInProcessAndReportsFailure() {
        val store = Store(true).apply { writeWorks = false; deleteWorks = false }
        val grant = WatchGrant(store)
        try { grant.visible = false; fail() } catch (_: IOException) { }
        assertFalse(grant.visible)
        // Durable revocation is impossible with an unchanged disk; UI must disclose it.
        assertTrue(WatchGrant(store).visible)
    }
    @Test fun failedReadHidesValues() {
        assertFalse(WatchGrant(Store(true).apply { readThrows = true }).visible)
    }
}
