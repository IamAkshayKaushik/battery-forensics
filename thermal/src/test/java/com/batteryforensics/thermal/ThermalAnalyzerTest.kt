package com.batteryforensics.thermal

import com.batteryforensics.core.model.MonitoringSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThermalAnalyzerTest {
    @Test
    fun heatingRate_detectsRapidRise() {
        val temps = listOf(
            0L to 30f,
            60_000L to 36f, // 6°C/min
        )
        val rate = ThermalAnalyzer.maxRisingRate(temps)
        assertThat(rate).isWithin(0.01).of(6.0)
    }

    @Test
    fun analyze_flagsThrottlingOnHighStatus() {
        val samples = listOf(
            sample(0, 35f, 0),
            sample(60_000, 42f, 3),
            sample(120_000, 44f, 3),
        )
        val report = ThermalAnalyzer.analyze(samples)
        assertThat(report.throttlingDetected).isTrue()
        assertThat(report.events.any { it.type == "THROTTLING" }).isTrue()
        assertThat(report.maxTempC).isWithin(0.1f).of(44f)
    }

    private fun sample(t: Long, temp: Float, status: Int) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = 50,
        voltageMv = 3900,
        currentMicroamps = -200000,
        chargeCounterMah = 2000,
        temperatureC = temp,
        isCharging = false,
        chargePlug = null,
        screenOn = false,
        brightnessPercent = 0,
        refreshRateHz = 60f,
        thermalStatus = status,
        wifiConnected = false,
        wifiRssiDbm = null,
        cellularRssiDbm = null,
        networkType = "lte",
    )
}
