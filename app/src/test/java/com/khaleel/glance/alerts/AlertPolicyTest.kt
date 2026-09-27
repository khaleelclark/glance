package com.khaleel.glance.alerts

import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AlertPolicyTest {
    private val start = Instant.parse("2026-09-27T12:00:00Z")
    private val patrick = Connection("patrick", "Patrick")
    private val config = AlertConfig(
        AlertConfig.defaults().rules.mapValues { (_, rule) -> rule.copy(enabled = true) },
    )

    @Test fun urgentThresholdWinsAndRepeatsOnlyOnNewSampleAfterCooldown() {
        val first = AlertPolicy.evaluate(config, AlertSnapshot(), display(305.0, start), start)
        assertEquals(AlertKind.URGENT_HIGH, first.kind)
        assertNull(AlertPolicy.evaluate(config, first.snapshot, display(305.0, start), start.plusSeconds(300)).kind)
        val withinCooldown = AlertPolicy.evaluate(
            config, first.snapshot, display(310.0, start.plusSeconds(60)), start.plusSeconds(60),
        )
        assertNull(withinCooldown.kind)
        val afterCooldown = AlertPolicy.evaluate(
            config, withinCooldown.snapshot, display(315.0, start.plusSeconds(300)), start.plusSeconds(300),
        )
        assertEquals(AlertKind.URGENT_HIGH, afterCooldown.kind)
    }

    @Test fun eachSideAndLevelHasItsOwnCooldown() {
        val high = AlertPolicy.evaluate(config, AlertSnapshot(), display(240.0, start), start)
        assertEquals(AlertKind.HIGH, high.kind)
        val urgentHigh = AlertPolicy.evaluate(
            config, high.snapshot, display(300.0, start.plusSeconds(60)), start.plusSeconds(60),
        )
        assertEquals(AlertKind.URGENT_HIGH, urgentHigh.kind)
        val low = AlertPolicy.evaluate(
            config, urgentHigh.snapshot, display(70.0, start.plusSeconds(120)), start.plusSeconds(120),
        )
        assertEquals(AlertKind.LOW, low.kind)
        val urgentLow = AlertPolicy.evaluate(
            config, low.snapshot, display(55.0, start.plusSeconds(180)), start.plusSeconds(180),
        )
        assertEquals(AlertKind.URGENT_LOW, urgentLow.kind)
    }

    @Test fun staleSpecialAndWrongPersonNeverAlert() {
        val stale = display(305.0, start).copy(freshness = ReadingFreshness.STALE)
        assertNull(AlertPolicy.evaluate(config, AlertSnapshot(), stale, start.plusSeconds(1200)).kind)
        val special = display(305.0, start).copy(
            reading = display(305.0, start).reading!!.copy(specialValue = SpecialValue.HIGH),
        )
        assertNull(AlertPolicy.evaluate(config, AlertSnapshot(), special, start).kind)
        val wrongPerson = display(305.0, start).copy(person = Connection("other", "Other"))
        assertNull(AlertPolicy.evaluate(config, AlertSnapshot(), wrongPerson, start).kind)
        assertNull(AlertPolicy.evaluate(config, AlertSnapshot(), display(305.0, start).copy(monitoringEnabled = false), start).kind)
    }

    @Test fun personChangeResetsCooldownAndDisabledRulesStaySilent() {
        val first = AlertPolicy.evaluate(config, AlertSnapshot(), display(305.0, start), start)
        val other = Connection("other", "Other")
        val changed = AlertPolicy.evaluate(
            config, first.snapshot,
            display(305.0, start.plusSeconds(60), other), start.plusSeconds(60),
        )
        assertEquals(AlertKind.URGENT_HIGH, changed.kind)
        assertNull(AlertPolicy.evaluate(AlertConfig.defaults(), AlertSnapshot(), display(305.0, start), start).kind)
    }

    @Test fun configuredCooldownControlsRepeatIntervalAndNormalReadingClearsAlert() {
        val tenMinutes = AlertConfig(config.rules + (
            AlertKind.HIGH to config[AlertKind.HIGH].copy(cooldownMinutes = 10)
        ))
        val first = AlertPolicy.evaluate(tenMinutes, AlertSnapshot(), display(240.0, start), start)
        assertEquals(AlertKind.HIGH, first.kind)
        val atFive = AlertPolicy.evaluate(
            tenMinutes, first.snapshot, display(245.0, start.plusSeconds(300)), start.plusSeconds(300),
        )
        assertNull(atFive.kind)
        val atTen = AlertPolicy.evaluate(
            tenMinutes, atFive.snapshot, display(250.0, start.plusSeconds(600)), start.plusSeconds(600),
        )
        assertEquals(AlertKind.HIGH, atTen.kind)
        val normal = AlertPolicy.evaluate(
            tenMinutes, atTen.snapshot, display(110.0, start.plusSeconds(660)), start.plusSeconds(660),
        )
        assertNull(normal.kind)
        assertEquals(true, normal.clearExisting)
    }

    private fun display(value: Double, at: Instant, person: Connection = patrick) = DisplayState(
        monitoringEnabled = true,
        runtime = RuntimeState.RUNNING,
        connection = ConnectionState.READY,
        person = person,
        reading = Reading(
            person.id, at, value, value, GlucoseUnit.MG_DL, null,
            Trend.STEADY, null, at,
        ),
        freshness = ReadingFreshness.FRESH,
        lastSuccessfulFetch = at,
        nextAllowedFetch = null,
    )
}
