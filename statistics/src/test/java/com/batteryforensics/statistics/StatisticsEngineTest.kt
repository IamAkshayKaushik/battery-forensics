package com.batteryforensics.statistics

import com.batteryforensics.core.model.MonitoringSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatisticsEngineTest {
    @Test
    fun movingAverage_smoothsSeries() {
        val points = listOf(0L to 1.0, 1L to 3.0, 2L to 5.0)
        val ma = StatisticsEngine.movingAverage(points, 2)
        assertThat(ma).hasSize(3)
        assertThat(ma[1].value).isWithin(0.01).of(2.0)
    }

    @Test
    fun anomalies_flagOutliers() {
        val points = (0..9).map { it.toLong() to 50.0 } + listOf(10L to 90.0)
        val anomalies = StatisticsEngine.detectAnomalies(points, "battery_percent", zThreshold = 2.0)
        assertThat(anomalies).isNotEmpty()
        assertThat(anomalies.first().observed).isWithin(0.1).of(90.0)
    }

    @Test
    fun analyze_computesStandbyAndScreenDrain() {
        val start = 1_000_000L
        val samples = listOf(
            sample(start, 90, screen = false),
            sample(start + 3_600_000, 87, screen = false),
            sample(start + 4_600_000, 80, screen = true),
            sample(start + 5_600_000, 70, screen = true),
        )
        val report = StatisticsEngine.analyze(samples)
        assertThat(report.overallDrainPercentPerHour).isNotNull()
        assertThat(report.dischargeCurve).isNotEmpty()
    }

    private fun sample(t: Long, pct: Int, screen: Boolean) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = pct,
        voltageMv = 3900,
        currentMicroamps = -200000,
        chargeCounterMah = 2000,
        temperatureC = 33f,
        isCharging = false,
        chargePlug = null,
        screenOn = screen,
        brightnessPercent = if (screen) 80 else 0,
        refreshRateHz = 60f,
        thermalStatus = 0,
        wifiConnected = false,
        wifiRssiDbm = null,
        cellularRssiDbm = null,
        networkType = "lte",
    )
}
