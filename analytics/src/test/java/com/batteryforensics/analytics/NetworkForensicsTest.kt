package com.batteryforensics.analytics

import com.batteryforensics.core.model.MonitoringSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkForensicsTest {
    @Test
    fun analyze_countsTransitionsAndLabelsRadioActiveAsInferred() {
        val samples = listOf(
            sample(0, rssi = -90, wifi = false, network = "lte"),
            sample(60_000, rssi = -115, wifi = false, network = "lte"),
            sample(120_000, rssi = -118, wifi = false, network = "5g"),
            sample(180_000, rssi = -70, wifi = true, network = "wifi"),
        )
        val report = NetworkForensics.analyze(samples)
        assertThat(report.networkTransitionCount).isAtLeast(1)
        assertThat(report.radioActiveMinutesEstimate).isNotNull()
        assertThat(report.notes.any { it.contains("Inferred") && it.contains("RRC") }).isTrue()
        assertThat(report.weakCellularRatio).isNotNull()
        assertThat(report.weakCellularRatio!!).isGreaterThan(0.0)
    }

    private fun sample(
        t: Long,
        rssi: Int?,
        wifi: Boolean,
        network: String,
    ) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = 80,
        voltageMv = 3900,
        currentMicroamps = -200000,
        chargeCounterMah = 2500,
        temperatureC = 33f,
        isCharging = false,
        chargePlug = null,
        screenOn = false,
        brightnessPercent = 0,
        refreshRateHz = 60f,
        thermalStatus = 0,
        wifiConnected = wifi,
        wifiRssiDbm = if (wifi) -55 else null,
        cellularRssiDbm = rssi,
        networkType = network,
    )
}
