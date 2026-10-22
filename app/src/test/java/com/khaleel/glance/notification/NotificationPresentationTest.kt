package com.khaleel.glance.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NotificationPresentationTest {
    @Test fun `current dismissal is accepted and old dismissal after restore is ignored`() {
        val storage = Storage(NotificationPresentationState(false, 4))
        val presentation = NotificationPresentation(storage)

        assertTrue(presentation.dismiss(4))
        assertEquals(NotificationPresentationState(true, 4), presentation.current())
        assertEquals(5L, presentation.restore())
        assertFalse(presentation.dismiss(4))
        assertEquals(NotificationPresentationState(false, 5), presentation.current())
    }

    @Test fun `failed dismissal and restore retain only accepted state`() {
        for (operation in listOf<(NotificationPresentation) -> Unit>(
            { it.dismiss(4) },
            { it.restore() },
        )) {
            val initial = NotificationPresentationState(false, 4)
            val storage = Storage(initial).apply { fail = true }
            val presentation = NotificationPresentation(storage)

            assertThrows(IOException::class.java) { operation(presentation) }
            assertEquals(initial, presentation.current())
            assertEquals(initial, storage.disk)
            assertFalse(initial == storage.memory)
        }
    }

    @Test fun `dismissal already writing completes before restore and restore wins`() {
        val storage = Storage(NotificationPresentationState(false, 4)).apply { blockNextWrite = true }
        val presentation = NotificationPresentation(storage)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val dismiss = executor.submit<Boolean> { presentation.dismiss(4) }
            assertTrue(storage.writeEntered.await(3, TimeUnit.SECONDS))
            val restore = executor.submit<Long> { presentation.restore() }
            assertFalse(restore.isDone)
            storage.resumeWrite.countDown()

            assertTrue(dismiss.get(3, TimeUnit.SECONDS))
            assertEquals(5L, restore.get(3, TimeUnit.SECONDS))
            assertEquals(NotificationPresentationState(false, 5), presentation.current())
        } finally {
            storage.resumeWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `restore already writing completes before old dismissal and rejects it`() {
        val storage = Storage(NotificationPresentationState(false, 4)).apply { blockNextWrite = true }
        val presentation = NotificationPresentation(storage)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val restore = executor.submit<Long> { presentation.restore() }
            assertTrue(storage.writeEntered.await(3, TimeUnit.SECONDS))
            val dismiss = executor.submit<Boolean> { presentation.dismiss(4) }
            assertFalse(dismiss.isDone)
            storage.resumeWrite.countDown()

            assertEquals(5L, restore.get(3, TimeUnit.SECONDS))
            assertFalse(dismiss.get(3, TimeUnit.SECONDS))
            assertEquals(NotificationPresentationState(false, 5), presentation.current())
        } finally {
            storage.resumeWrite.countDown()
            executor.shutdownNow()
        }
    }

    private class Storage(initial: NotificationPresentationState) : NotificationVisibilityStorage {
        var disk = initial
        var memory = initial
        var fail = false
        var blockNextWrite = false
        val writeEntered = CountDownLatch(1)
        val resumeWrite = CountDownLatch(1)

        override fun read() = disk

        override fun write(state: NotificationPresentationState): Boolean {
            memory = state // SharedPreferences updates memory even when commit fails.
            if (blockNextWrite) {
                blockNextWrite = false
                writeEntered.countDown()
                check(resumeWrite.await(3, TimeUnit.SECONDS))
            }
            if (fail) return false
            disk = state
            return true
        }
    }
}
