package com.khaleel.glance.widget

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WidgetPublicationTest {
    @Test fun `composition completed after opt-out or stop cannot overwrite redaction`() {
        for (transition in listOf("opt-out", "stop")) {
            val publication = WidgetPublication()
            val generation = publication.generation()
            var host = "patient reading"
            publication.redact { host = "hidden after $transition" }
            // Even an ABA state change back to the old values cannot reuse the old composition.
            assertFalse(publication.publish(generation, { true }) { host = "patient reading" })
            assertEquals("hidden after $transition", host)
        }
    }

    @Test fun `redaction waits for in-flight host publication then clears it last`() {
        val publication = WidgetPublication()
        val generation = publication.generation()
        val updateEntered = CountDownLatch(1)
        val resumeUpdate = CountDownLatch(1)
        val redactionRequested = CountDownLatch(1)
        val redactionFinished = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(2)
        var host = "hidden"
        try {
            val update = executor.submit<Boolean> {
                publication.publish(generation, { true }) {
                    updateEntered.countDown()
                    check(resumeUpdate.await(3, TimeUnit.SECONDS))
                    host = "patient reading"
                }
            }
            assertTrue(updateEntered.await(3, TimeUnit.SECONDS))
            val redact = executor.submit {
                redactionRequested.countDown()
                publication.redact { host = "hidden" }
                redactionFinished.set(true)
            }
            assertTrue(redactionRequested.await(3, TimeUnit.SECONDS))
            assertFalse(redactionFinished.get())
            resumeUpdate.countDown()
            assertTrue(update.get(3, TimeUnit.SECONDS))
            redact.get(3, TimeUnit.SECONDS)
            assertTrue(redactionFinished.get())
            assertEquals("hidden", host)
        } finally {
            resumeUpdate.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `final state check rejects a changed snapshot before touching the host`() {
        val publication = WidgetPublication()
        var host = "hidden"
        assertFalse(publication.publish(publication.generation(), { false }) { host = "patient reading" })
        assertEquals("hidden", host)
    }
}
