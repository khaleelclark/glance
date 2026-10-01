package com.khaleel.glance.debug

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.AlertRule
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import com.khaleel.glance.ui.ReadingHistoryChart
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject
import kotlin.math.sin

/** Synthetic-only fixture using the production chart inside a scrolling parent. */
class DebugChartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode")
        val unit = if (mode == "mmol") GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL
        val alerts = AlertConfig(AlertConfig.defaults().rules + mapOf(
            AlertKind.LOW to AlertRule(intent.getBooleanExtra("lowEnabled", false), intent.getIntExtra("low", 85)),
            AlertKind.HIGH to AlertRule(intent.getBooleanExtra("highEnabled", false), intent.getIntExtra("high", 210)),
        ))
        setContent {
            MaterialTheme(colorScheme = if (mode == "dark") darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val now = remember { Instant.parse("2026-10-01T12:00:00Z") }
                    val points = remember {
                        (0..288).map { step ->
                            val value = if (mode == null) (90 + step % 60).toDouble() else when (step) {
                                252 -> 25.0
                                288 -> if (mode == "below") 25.0 else 380.0
                                else -> 175 + 125 * sin(step / 5.0)
                            }
                            Reading("synthetic", now.minusSeconds((288 - step) * 300L),
                                value, value,
                                GlucoseUnit.MG_DL, null, Trend.STEADY, "synthetic-$step", now)
                        }
                    }
                    var selected by remember { mutableStateOf<Long?>(null) }
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
                        Text("DEMO · chart gesture test")
                        ReadingHistoryChart(points, "synthetic", unit, now,
                            ZoneId.of("UTC"), Locale.US, selected, { selected = it }, alerts = alerts)
                        Text("DEMO selected: $selected")
                        Column(Modifier.height(600.dp)) { Text("DEMO · page scroll space") }
                    }
                }
            }
        }
    }
}

/** Real touchscreen injection with no account/network; registered only in debug. */
class ChartGestureInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val checks = JSONObject()
        try {
            check(uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use {
                java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
            } == "1") { "Dedicated emulator required" }
            startActivitySync(Intent(targetContext, DebugChartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            settle()
            val initial = chart()
            val bounds = Rect().also { initial.getBoundsInScreen(it) }
            val left = bounds.left + bounds.width() * 0.2f
            val right = bounds.left + bounds.width() * 0.8f
            val y = bounds.centerY().toFloat()
            check(initial.stateDescription.toString() == "Showing 3.0 hours")
            gesture(listOf(left to y), listOf(right to y))
            val selected = selectedTime()
            check(selected > 0)
            check(chart().stateDescription.toString() == "Showing 3.0 hours")
            check(Rect().also { chart().getBoundsInScreen(it) } == bounds)
            gesture(listOf(right to y), listOf(left to y))
            check(selectedTime() < selected) { "Reverse scrub: first=$selected, reverse=${selectedTime()}, bounds=$bounds" }
            gesture(listOf(left to (y - bounds.height() / 3)), listOf(right to (y + bounds.height() / 3)))
            check(Rect().also { chart().getBoundsInScreen(it) } == bounds)
            checks.put("oneFingerScrubsWithoutPanningOrParentScrolling", true)

            // Start near the middle and spread fingers to zoom around that time.
            val middle = bounds.centerX().toFloat()
            gesture(listOf((middle - 35) to y, (middle + 35) to y),
                listOf((middle - 100) to y, (middle + 100) to y))
            check(hours() in 1.0..2.0)
            checks.put("twoFingerPinchZoomsIn", true)
            val zoomHours = hours()
            gesture(listOf(left to y, (left + 80) to y), listOf((left + 80) to y, (left + 160) to y))
            check(hours() == zoomHours)
            gesture(listOf(middle to y), listOf(middle to y))
            val earlier = selectedTime()
            click("Latest 3 hours")
            gesture(listOf(middle to y), listOf(middle to y))
            check(earlier < selectedTime())
            checks.put("twoFingerDragPansToEarlierReadings", true)

            // After lifting the second finger, the remaining finger must not scrub.
            val stamp = SystemClock.uptimeMillis()
            event(stamp, MotionEvent.ACTION_DOWN, listOf(left to y))
            event(stamp, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(left to y, right to y))
            event(stamp, MotionEvent.ACTION_MOVE, listOf((left + 10) to y, (right + 10) to y))
            settle()
            val beforeLift = selectedTime()
            event(stamp, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf((left + 10) to y, (right + 10) to y))
            event(stamp, MotionEvent.ACTION_MOVE, listOf(right to y))
            event(stamp, MotionEvent.ACTION_UP, listOf(right to y))
            settle()
            check(selectedTime() == beforeLift)
            checks.put("liftingOneFingerDoesNotSwitchFromPanToScrub", true)

            click("Show 24 hours")
            check(hours() == 24.0)
            gesture(listOf((middle - 100) to y, (middle + 100) to y), listOf((middle - 15) to y, (middle + 15) to y))
            check(hours() == 24.0)
            checks.put("zoomOutStopsAtTwentyFourHoursAndResetWorks", true)
            click("Latest 3 hours")
            repeat(3) {
                gesture(listOf((middle - 15) to y, (middle + 15) to y), listOf((middle - 110) to y, (middle + 110) to y))
            }
            check(hours() == 1.0)
            checks.put("zoomInStopsAtOneHour", true)
            val fresh = chart()
            val zoomOut = fresh.actionList.first { it.label?.toString() == "Zoom out" }
            check(fresh.performAction(zoomOut.id))
            settle()
            check(hours() == 2.0)
            checks.put("accessibilityZoomActionWorks", true)
            click("Latest 3 hours")
            limitChecks(checks)
            targetContext.filesDir.resolve("chart-gesture-probe.json").writeText(
                JSONObject().put("synthetic", true).put("device", "API 36 emulator")
                    .put("checks", checks).put("allPassed", true).toString(2))
            finish(Activity.RESULT_OK, Bundle().apply { putString("checks", checks.toString()) })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("failure", error.stackTraceToString()); putString("checks", checks.toString())
            })
        }
    }

    private fun launchLimits(mode: String = "limits", lowEnabled: Boolean = false, highEnabled: Boolean = false) {
        startActivitySync(Intent(targetContext, DebugChartActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("mode", mode).putExtra("lowEnabled", lowEnabled).putExtra("highEnabled", highEnabled))
        settle()
    }

    private fun texts(): List<String> = nodes(uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }

    private fun limitChecks(checks: JSONObject) {
        launchLimits()
        check("Low: 70 mg/dL · High: 240 mg/dL" in texts())
        check("350" in texts() && "40" in texts())
        check(chart().contentDescription.toString().contains("Scale: 40–350 mg/dL"))
        checks.put("fixedScaleAndDisabledSavedLimitsFallBackToSeventyTwoForty", true)
        checkColorBands(0xC62828, 0xB85C00)
        checks.put("defaultHorizontalLinesAndLowHighTraceColorsRender", true)
        check("380 mg/dL" in texts())
        check(texts().any { it.startsWith("Outside graph scale") })
        launchLimits("below")
        check("25 mg/dL" in texts())
        check(texts().any { it.startsWith("Outside graph scale") })
        checks.put("outOfScaleSelectionPreservesActualValues", true)
        launchLimits(lowEnabled = true, highEnabled = true)
        check("Low: 85 mg/dL · High: 210 mg/dL" in texts())
        checkColorBands(0xC62828, 0xB85C00, 85.0, 210.0)
        launchLimits(lowEnabled = true)
        check("Low: 85 mg/dL · High: 240 mg/dL" in texts())
        launchLimits(highEnabled = true)
        check("Low: 70 mg/dL · High: 210 mg/dL" in texts())
        checks.put("enabledOrdinaryLimitsSyncIndependently", true)
        launchLimits("mmol")
        check("Low: 3.9 mmol/L · High: 13.3 mmol/L" in texts())
        check("19.4" in texts() && "2.2" in texts())
        checkColorBands(0xC62828, 0xB85C00)
        checks.put("mmolScaleAndLimitsConvertWithIdenticalColorGeometry", true)
        launchLimits("dark")
        checkColorBands(0xFF6B6B, 0xFFB74D)
        checks.put("darkThemeHorizontalLinesAndLowHighColorsRender", true)
    }

    private fun checkColorBands(red: Int, orange: Int, low: Double = 70.0, high: Double = 240.0) {
        val bounds = Rect().also { chart().getBoundsInScreen(it) }
        val inset = bounds.height() * 16f / 220
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        try {
            fun lineHasColor(value: Double, expected: Int): Boolean {
                val y = (bounds.top + inset + (bounds.height() - 2 * inset) * (350 - value) / 310).toInt()
                var hits = 0
                for (x in bounds.left + 12 until bounds.right - 12 step 5) {
                    if ((y - 3..y + 3).any { row -> bitmap.getPixel(x, row) and 0xFFFFFF == expected }) hits++
                }
                return hits >= bounds.width() / 15
            }
            check(lineHasColor(low, red)) { "Low horizontal line pixels missing" }
            check(lineHasColor(high, orange)) { "High horizontal line pixels missing" }
            fun traceHasColor(expected: Int, lowBand: Boolean): Boolean {
                val edge = (bounds.top + inset + (bounds.height() - 2 * inset) * (350 - if (lowBand) low else high) / 310).toInt()
                val rows = if (lowBand) edge + 8 until (bounds.bottom - inset).toInt() - 8
                    else (bounds.top + inset).toInt() + 8 until edge - 8
                var count = 0
                for (y in rows) for (x in bounds.left + 8 until bounds.right - 8) {
                    if (bitmap.getPixel(x, y) and 0xFFFFFF == expected) count++
                }
                return count > 20
            }
            check(traceHasColor(red, true)) { "Low trace pixels missing" }
            check(traceHasColor(orange, false)) { "High trace pixels missing" }
        } finally { bitmap.recycle() }
    }

    private fun settle() { waitForIdleSync(); SystemClock.sleep(120) }

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        root.refresh()
        return listOf(root) + (0 until root.childCount).flatMap { index -> root.getChild(index)?.let { nodes(it) } ?: emptyList() }
    }

    private fun chart(): AccessibilityNodeInfo = nodes(uiAutomation.rootInActiveWindow)
        .first { it.contentDescription?.toString()?.contains("source readings over 24 hours") == true }

    private fun selectedTime(): Long = nodes(uiAutomation.rootInActiveWindow)
        .first { it.text?.startsWith("DEMO selected:") == true }.text.toString().substringAfter(": ").toLong()

    private fun hours(): Double = chart().stateDescription.toString().substringAfter("Showing ").substringBefore(" hours").toDouble()

    private fun click(label: String) {
        val node = nodes(uiAutomation.rootInActiveWindow).first { it.text?.toString() == label }
        check((if (node.isClickable) node else node.parent).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        settle()
    }

    private fun gesture(from: List<Pair<Float, Float>>, to: List<Pair<Float, Float>>) {
        val stamp = SystemClock.uptimeMillis()
        event(stamp, MotionEvent.ACTION_DOWN, from.take(1))
        if (from.size == 2) event(stamp, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), from)
        for (step in 1..20) {
            val fraction = step / 20f
            event(stamp, MotionEvent.ACTION_MOVE, from.indices.map { index ->
                (from[index].first + (to[index].first - from[index].first) * fraction) to
                    (from[index].second + (to[index].second - from[index].second) * fraction)
            })
            SystemClock.sleep(16)
        }
        if (from.size == 2) event(stamp, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), to)
        event(stamp, MotionEvent.ACTION_UP, to.take(1))
        settle()
    }

    private fun event(stamp: Long, action: Int, positions: List<Pair<Float, Float>>) {
        val properties = positions.indices.map { index -> MotionEvent.PointerProperties().apply {
            id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coordinates = positions.map { (x, y) -> MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = 1f; size = 1f
        } }.toTypedArray()
        val motion = MotionEvent.obtain(stamp, SystemClock.uptimeMillis(), action, positions.size,
            properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { check(uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
    }
}
