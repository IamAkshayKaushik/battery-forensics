package com.batteryforensics.diagnostics

import com.batteryforensics.core.model.MonitoringSample
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
