package com.batteryforensics.battery

import com.batteryforensics.core.model.MonitoringSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChemistryEngineTest {

    @Test
    fun internalResistance_computesFromDeltaVOverDeltaI() {
        val samples = listOf(
            sample(t = 0, voltageMv = 4000, currentUa = -200_000),
            sample(t = 15_000, voltageMv = 3900, currentUa = -700_000),
        )
        val ri = ChemistryEngine.computeInternalResistance(samples)
        assertThat(ri).isNotEmpty()
        // ΔV=100mV, ΔI=500mA → Ri = 100/500*1000 = 200 mΩ
        assertThat(ri.first().resistanceMilliohms).isWithin(1.0).of(200.0)
    }

    @Test
    fun voltageSag_derivedFromRestAndLoad() {
        val samples = listOf(
            sample(t = 0, voltageMv = 4100, currentUa = -20_000),
            sample(t = 1, voltageMv = 4090, currentUa = -10_000),
            sample(t = 2, voltageMv = 3800, currentUa = -800_000),
            sample(t = 3, voltageMv = 3790, currentUa = -900_000),
        )
        val sag = ChemistryEngine.computeVoltageSag(samples)
        assertThat(sag.sagMv).isNotNull()
        assertThat(sag.sagMv!!).isAtLeast(150)
    }

    @Test
    fun wearTrend_estimatesCyclesFromCounterReversals() {
        val samples = listOf(
            sample(t = 0, counter = 2000),
            sample(t = 1, counter = 2500),
            sample(t = 2, counter = 3000),
            sample(t = 3, counter = 2500),
            sample(t = 4, counter = 2000),
            sample(t = 5, counter = 2600),
        )
        val cycles = ChemistryEngine.estimateCycles(samples)
        assertThat(cycles).isNotNull()
        assertThat(cycles!!).isAtLeast(0.5)
    }

    @Test
    fun analyze_returnsLabeledReport() {
        val samples = (0..12).map { i ->
            sample(
                t = i * 60_000L,
                voltageMv = 4000 - i * 5,
                currentUa = -300_000 - i * 20_000,
                percent = 80 - i,
                counter = 3000 - i * 10,
            )
        }
        val report = ChemistryEngine.analyze(samples)
        assertThat(report.notes).isNotEmpty()
        assertThat(report.voltageSag.confidence.name).isNotEmpty()
    }

    private fun sample(
        t: Long,
        voltageMv: Int? = 4000,
        currentUa: Int? = -300_000,
        percent: Int? = 80,
        counter: Int? = 3000,
    ) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = percent,
        voltageMv = voltageMv,
        currentMicroamps = currentUa,
        chargeCounterMah = counter,
        temperatureC = 32f,
        isCharging = false,
        chargePlug = null,
        screenOn = false,
        brightnessPercent = 0,
        refreshRateHz = 60f,
        thermalStatus = 0,
        wifiConnected = false,
        wifiRssiDbm = null,
        cellularRssiDbm = null,
        networkType = "lte",
    )
}
