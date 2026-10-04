package com.khaleel.glance.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHealthDataCleanupTest {
    @Test fun failedLedgerWriteStillRemovesPublishedReadingsAndAlerts() {
        var cachedReading: String? = "old reading"
        var monitoringCard: String? = cachedReading
        var thresholdCard: String? = cachedReading
        var snapshotCleared = false

        val result = LocalHealthDataCleanup.run(
            clearReadings = { cachedReading = null },
            clearHistory = { throw IOException("synthetic ledger write failure") },
            clearAlertSnapshot = { snapshotCleared = true },
            clearAlertNotifications = { thresholdCard = null },
            updateMonitoring = { readingsCleared ->
                assertTrue(readingsCleared)
                monitoringCard = cachedReading
            },
        )

        assertFalse(result)
        assertTrue(snapshotCleared)
        assertNull(thresholdCard)
        assertNull(monitoringCard)
    }

    @Test fun failedReadingCleanupDoesNotRerenderTheOldReading() {
        var historyCleared = false
        var thresholdCard: String? = "old reading"
        var monitoringCard: String? = "old reading"

        val result = LocalHealthDataCleanup.run(
            clearReadings = { throw IOException("synthetic reading write failure") },
            clearHistory = { historyCleared = true },
            clearAlertSnapshot = {},
            clearAlertNotifications = { thresholdCard = null },
            updateMonitoring = { readingsCleared ->
                assertFalse(readingsCleared)
                monitoringCard = null
            },
        )

        assertFalse(result)
        assertTrue(historyCleared)
        assertNull(thresholdCard)
        assertNull(monitoringCard)
    }

    @Test fun MultipleFailuresStillAttemptEveryIndependentCleanup() {
        val attempted = mutableListOf<String>()
        val result = LocalHealthDataCleanup.run(
            clearReadings = { attempted += "readings" },
            clearHistory = { attempted += "history"; throw IOException() },
            clearAlertSnapshot = { attempted += "snapshot"; throw IOException() },
            clearAlertNotifications = { attempted += "alerts"; throw IOException() },
            updateMonitoring = { attempted += "monitoring" },
        )

        assertFalse(result)
        assertEquals(listOf("readings", "history", "snapshot", "alerts", "monitoring"), attempted)
    }

    @Test fun successfulCleanupKeepsTheNormalMonitoringRenderPath() {
        var renderClearedState = false
        val result = LocalHealthDataCleanup.run({}, {}, {}, {}, { renderClearedState = it })
        assertTrue(result)
        assertTrue(renderClearedState)
    }
}
