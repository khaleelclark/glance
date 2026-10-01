package com.khaleel.glance.notification

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NotificationPublicationTest {
    @Test fun `receiver paused after early validation cannot publish after privacy cancellation`() {
        for (boundary in listOf("Stop", "sign-out", "person change")) {
            val publication = NotificationPublication()
            val earlyCheckPassed = CountDownLatch(1)
            val resumeReceiver = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            var host: String? = "old person reading"
            try {
                val restore = executor.submit<Boolean> {
                    val generation = publication.generation()
                    // Simulate the receiver having already validated the running session.
                    earlyCheckPassed.countDown()
                    check(resumeReceiver.await(3, TimeUnit.SECONDS))
                    publication.publish(generation, { true }) { host = "old person reading" }
                }
                assertTrue(earlyCheckPassed.await(3, TimeUnit.SECONDS))
                publication.clear { host = null }
                // Even a return to identical state must not authorize the old callback.
                resumeReceiver.countDown()
                assertFalse(boundary, restore.get(3, TimeUnit.SECONDS))
                assertNull(boundary, host)
            } finally {
                resumeReceiver.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test fun `cancellation waits for an overlapping notify and clears the card last`() {
        val publication = NotificationPublication()
        val generation = publication.generation()
        val notifyEntered = CountDownLatch(1)
        val resumeNotify = CountDownLatch(1)
        val clearRequested = CountDownLatch(1)
        val clearFinished = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(2)
        var host: String? = null
        try {
            val restore = executor.submit<Boolean> {
                publication.publish(generation, { true }) {
                    notifyEntered.countDown()
                    check(resumeNotify.await(3, TimeUnit.SECONDS))
                    host = "old person reading"
                }
            }
            assertTrue(notifyEntered.await(3, TimeUnit.SECONDS))
            val clear = executor.submit {
                clearRequested.countDown()
                publication.clear { host = null }
                clearFinished.set(true)
            }
            assertTrue(clearRequested.await(3, TimeUnit.SECONDS))
            assertFalse(clearFinished.get())
            resumeNotify.countDown()
            assertTrue(restore.get(3, TimeUnit.SECONDS))
            clear.get(3, TimeUnit.SECONDS)
            assertTrue(clearFinished.get())
            assertNull(host)
        } finally {
            resumeNotify.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `final session validation rejects stopped and superseded callbacks`() {
        val publication = NotificationPublication()
        var host: String? = null
        for (action in listOf(
            NotificationDismissal.action(7, 7, false, false, true),
            NotificationDismissal.action(7, 8, true, true, true),
        )) {
            assertFalse(publication.publish(publication.generation(), {
                action == DismissalAction.RESTORE_REGULAR
            }) { host = "old person reading" })
        }
        assertNull(host)
    }

    @Test fun `regular dismissal restoration invalidates pending promoted cards`() {
        val publication = NotificationPublication()
        val pendingPromotion = publication.generation()
        var host = "promoted"
        assertTrue(publication.publish(pendingPromotion, { true }, invalidatePending = true) {
            host = "regular"
        })
        assertFalse(publication.publish(pendingPromotion, { true }) { host = "promoted" })
        assertEquals("regular", host)
    }

    @Test fun `privacy choice waits for a passed final check then clears and rejects old public renders`() {
        val publication = NotificationPublication()
        val generation = publication.generation()
        val visible = AtomicBoolean(true)
        val finalCheckPassed = CountDownLatch(1)
        val resumeNotify = CountDownLatch(1)
        val privacyRequested = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        var host: String? = null
        try {
            val render = executor.submit<Boolean> {
                publication.publish(generation, {
                    visible.get().also { finalCheckPassed.countDown() }
                }) {
                    check(resumeNotify.await(3, TimeUnit.SECONDS))
                    // A public card must never publish after the choice changed to hidden.
                    check(visible.get())
                    host = "public reading"
                }
            }
            assertTrue(finalCheckPassed.await(3, TimeUnit.SECONDS))
            val privacy = executor.submit {
                privacyRequested.countDown()
                publication.clear {
                    host = null
                    visible.set(false)
                }
            }
            assertTrue(privacyRequested.await(3, TimeUnit.SECONDS))
            assertTrue(visible.get())
            resumeNotify.countDown()
            assertTrue(render.get(3, TimeUnit.SECONDS))
            privacy.get(3, TimeUnit.SECONDS)
            assertFalse(visible.get())
            assertNull(host)
            assertFalse(publication.publish(generation, { true }) { host = "public reading" })
        } finally {
            resumeNotify.countDown()
            executor.shutdownNow()
        }
    }
}
