package com.batteryforensics.diagnostics

import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.parser.AlarmSummary
import com.batteryforensics.parser.DozeTimelineSummary
import com.batteryforensics.parser.WakeLockSummary
import com.batteryforensics.ruleengine.PrivilegedEvidence
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DifferentialAnalyzerTest {
    @Test
    fun compare_highlightsLargestDeviations() {
        val healthy = listOf(
            sample(0, 90, temp = 30f, rssi = -80, wifi = true),
            sample(3_600_000, 88, temp = 31f, rssi = -82, wifi = true),
            sample(7_200_000, 86, temp = 30f, rssi = -81, wifi = true),
        )
        val problem = listOf(
            sample(0, 90, temp = 38f, rssi = -115, wifi = false, network = "lte"),
            sample(3_600_000, 80, temp = 40f, rssi = -118, wifi = false, network = "5g"),
            sample(7_200_000, 70, temp = 41f, rssi = -120, wifi = false, network = "lte"),
        )
        val report = DifferentialAnalyzer.compare(healthy, problem)
        assertThat(report.deltas).isNotEmpty()
        assertThat(report.deltas.first().magnitude).isAtLeast(report.deltas.last().magnitude)
        assertThat(report.summary).contains("Largest deviations")
    }

    @Test
    fun compare_includesPrivilegedDozeAlarmWakeLockDeltas() {
        val healthy = listOf(
            sample(0, 90, temp = 30f, rssi = -80, wifi = true),
            sample(3_600_000, 88, temp = 31f, rssi = -82, wifi = true),
            sample(7_200_000, 86, temp = 30f, rssi = -81, wifi = true),
        )
        val problem = listOf(
            sample(0, 90, temp = 38f, rssi = -115, wifi = false, network = "lte"),
            sample(3_600_000, 80, temp = 40f, rssi = -118, wifi = false, network = "5g"),
            sample(7_200_000, 70, temp = 41f, rssi = -120, wifi = false, network = "lte"),
        )
        val healthyPriv = PrivilegedEvidence(
            doze = DozeTimelineSummary(
                state = "IDLE",
                deepEnabled = true,
                lightEnabled = true,
                historyHints = listOf("IDLE"),
                motionTriggeredInterruptions = 0,
                locationTriggeredInterruptions = 0,
            ),
            alarms = AlarmSummary(wakeupAlarmCount = 5, topPackages = emptyList(), wakeupsPerHour = 2.0),
            wakeLocks = WakeLockSummary(totalLocks = 2, appLocks = 1, kernelLocks = 1, topTags = emptyList()),
        )
        val problemPriv = PrivilegedEvidence(
            doze = DozeTimelineSummary(
                state = "ACTIVE",
                deepEnabled = true,
                lightEnabled = true,
                historyHints = listOf("ACTIVE", "IDLE_MAINTENANCE", "ACTIVE"),
                motionTriggeredInterruptions = 4,
                locationTriggeredInterruptions = 2,
            ),
            alarms = AlarmSummary(wakeupAlarmCount = 55, topPackages = emptyList(), wakeupsPerHour = 22.0),
            wakeLocks = WakeLockSummary(totalLocks = 14, appLocks = 10, kernelLocks = 4, topTags = emptyList()),
        )
        val report = DifferentialAnalyzer.compare(
            healthy = healthy,
            problem = problem,
            healthyPrivileged = healthyPriv,
            problemPrivileged = problemPriv,
        )
        assertThat(report.deltas.any { it.key == "alarm_wakeups" }).isTrue()
        assertThat(report.deltas.any { it.key == "wake_lock_count" }).isTrue()
        assertThat(report.deltas.any { it.key == "doze_motion_interrupts" }).isTrue()
        assertThat(report.deltas.first { it.key == "alarm_wakeups" }.magnitude).isAtLeast(40.0)
    }

    private fun sample(
        t: Long,
        pct: Int,
        temp: Float,
        rssi: Int?,
        wifi: Boolean,
        network: String = "lte",
    ) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = pct,
        voltageMv = 3900,
        currentMicroamps = -150000,
        chargeCounterMah = 2500,
        temperatureC = temp,
        isCharging = false,
        chargePlug = null,
        screenOn = false,
        brightnessPercent = 0,
        refreshRateHz = 60f,
        thermalStatus = 0,
        wifiConnected = wifi,
        wifiRssiDbm = if (wifi) -50 else null,
        cellularRssiDbm = rssi,
        networkType = network,
    )
}
