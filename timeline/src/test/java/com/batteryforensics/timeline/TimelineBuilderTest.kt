package com.batteryforensics.timeline

import com.batteryforensics.core.model.MonitoringSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimelineBuilderTest {
    @Test
    fun build_emitsMeaningfulTransitionsOnly() {
        val samples = listOf(
            sample(0, pct = 90, screen = false, charging = false, network = "lte"),
            sample(60_000, pct = 90, screen = true, charging = false, network = "lte"),
            sample(120_000, pct = 88, screen = true, charging = false, network = "5g"),
            sample(180_000, pct = 87, screen = true, charging = true, network = "5g"),
        )
        val events = TimelineBuilder.build(samples)
        assertThat(events.any { it.eventType == "SCREEN_ON" }).isTrue()
        assertThat(events.any { it.eventType == "NETWORK_TRANSITION" }).isTrue()
        assertThat(events.any { it.eventType == "CHARGE_START" }).isTrue()
        assertThat(events.size).isLessThan(samples.size * 3)
    }

    private fun sample(
        t: Long,
        pct: Int,
        screen: Boolean,
        charging: Boolean,
        network: String,
    ) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = pct,
        voltageMv = 4000,
        currentMicroamps = -200000,
        chargeCounterMah = 3000,
        temperatureC = 33f,
        isCharging = charging,
        chargePlug = if (charging) "usb" else null,
        screenOn = screen,
        brightnessPercent = if (screen) 70 else 0,
        refreshRateHz = 60f,
        thermalStatus = 0,
        wifiConnected = false,
        wifiRssiDbm = null,
        cellularRssiDbm = -90,
        networkType = network,
    )
}
