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

    @Test
    fun analyze_detectsCpuRunawayWhenHotWhileScreenOffNotCharging() {
        // Rapid heat + high thermal status while screen-off / not charging /
        // weak radio → Inferred CPU / SoC runaway (not modem-only).
        val samples = listOf(
            sample(0, 34f, 0, screenOn = false, charging = false, rssi = -70),
            sample(60_000, 41f, 2, screenOn = false, charging = false, rssi = -72),
            sample(120_000, 47f, 3, screenOn = false, charging = false, rssi = -71),
        )
        val report = ThermalAnalyzer.analyze(samples)
        assertThat(report.events.any { it.type == "CPU_RUNAWAY" }).isTrue()
        assertThat(report.cpuRunawaySuspected).isTrue()
        val event = report.events.first { it.type == "CPU_RUNAWAY" }
        assertThat(event.confidence.name).isEqualTo("INFERRED")
    }

    private fun sample(
        t: Long,
        temp: Float,
        status: Int,
        screenOn: Boolean = false,
        charging: Boolean = false,
        rssi: Int? = null,
    ) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = 50,
        voltageMv = 3900,
        currentMicroamps = -200000,
        chargeCounterMah = 2000,
        temperatureC = temp,
        isCharging = charging,
        chargePlug = null,
        screenOn = screenOn,
        brightnessPercent = 0,
        refreshRateHz = 60f,
        thermalStatus = status,
        wifiConnected = false,
        wifiRssiDbm = null,
        cellularRssiDbm = rssi,
        networkType = "lte",
    )
}
