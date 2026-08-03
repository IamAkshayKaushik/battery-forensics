package com.batteryforensics.ruleengine

import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.ruleengine.rules.DefaultRules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleEngineTest {
    private val engine = RuleEngine(DefaultRules.all())

    @Test
    fun weakCellularSignal_triggers_whenMajorityBelowThreshold() {
        val samples = (1..10).map { i ->
            sample(cellularRssiDbm = if (i <= 8) -115 else -90)
        }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("weak_cellular_signal")
        val diagnosis = result.first { it.id == "weak_cellular_signal" }
        assertThat(diagnosis.evidence).isNotEmpty()
        assertThat(diagnosis.confidence.starsLabel).contains("Measured")
        assertThat(diagnosis.recommendedActions).isNotEmpty()
    }

    @Test
    fun excessiveBrightness_triggers_onHighScreenOnBrightness() {
        val samples = (1..10).map {
            sample(screenOn = true, brightnessPercent = 95, refreshRateHz = 120f)
        }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("excessive_screen_brightness")
    }

    @Test
    fun elevatedTemperature_triggers_onHotPeak() {
        val samples = listOf(
            sample(temperatureC = 32f),
            sample(temperatureC = 41f),
            sample(temperatureC = 43f),
        )
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("elevated_temperature")
    }

    @Test
    fun chargingHeat_triggers_whenHotWhileCharging() {
        val samples = listOf(
            sample(isCharging = true, temperatureC = 44f),
            sample(isCharging = true, temperatureC = 45f),
        )
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("charging_heat")
    }

    @Test
    fun overnightStandby_triggers_onHighDrainScreenOff() {
        val start = 1_000_000L
        val samples = listOf(
            sample(timestamp = start, batteryPercent = 90, screenOn = false),
            sample(timestamp = start + 2 * 3_600_000, batteryPercent = 80, screenOn = false),
            sample(timestamp = start + 4 * 3_600_000, batteryPercent = 70, screenOn = false),
            sample(timestamp = start + 6 * 3_600_000, batteryPercent = 60, screenOn = false),
        )
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("overnight_standby_drain")
        val d = result.first { it.id == "overnight_standby_drain" }
        assertThat(d.confidence.starsLabel).contains("Derived")
    }

    @Test
    fun ranking_ordersByProbability() {
        val samples = (1..10).map {
            sample(
                cellularRssiDbm = -120,
                screenOn = true,
                brightnessPercent = 100,
                refreshRateHz = 120f,
                temperatureC = 44f,
                isCharging = true,
            )
        }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.size).isAtLeast(2)
        assertThat(result.zipWithNext().all { (a, b) -> a.probabilityPercent >= b.probabilityPercent })
            .isTrue()
    }

    @Test
    fun modemInducedHeating_triggersOnHotWeakScreenOffPattern() {
        val samples = (1..10).map {
            sample(
                temperatureC = 40f,
                cellularRssiDbm = -115,
                screenOn = false,
                isCharging = false,
            )
        }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("modem_induced_heating")
        val d = result.first { it.id == "modem_induced_heating" }
        assertThat(d.confidence.starsLabel).contains("Inferred")
    }

    @Test
    fun batteryAging_triggersOnLargeVoltageSag() {
        val samples = listOf(
            sample(currentUa = -20_000, voltageMv = 4100),
            sample(currentUa = -15_000, voltageMv = 4090),
            sample(currentUa = -800_000, voltageMv = 3850),
            sample(currentUa = -900_000, voltageMv = 3840),
        )
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("battery_aging_voltage_sag")
    }

    private fun sample(
        timestamp: Long = System.currentTimeMillis(),
        batteryPercent: Int? = 80,
        temperatureC: Float? = 30f,
        isCharging: Boolean? = false,
        screenOn: Boolean? = false,
        brightnessPercent: Int? = 40,
        refreshRateHz: Float? = 60f,
        cellularRssiDbm: Int? = null,
        wifiConnected: Boolean? = false,
        currentUa: Int? = -300_000,
        voltageMv: Int? = 4000,
    ) = MonitoringSample(
        timestampEpochMs = timestamp,
        batteryPercent = batteryPercent,
        voltageMv = voltageMv,
        currentMicroamps = currentUa,
        chargeCounterMah = 3000,
        temperatureC = temperatureC,
        isCharging = isCharging,
        chargePlug = null,
        screenOn = screenOn,
        brightnessPercent = brightnessPercent,
        refreshRateHz = refreshRateHz,
        thermalStatus = 0,
        wifiConnected = wifiConnected,
        wifiRssiDbm = null,
        cellularRssiDbm = cellularRssiDbm,
        networkType = "lte",
    )
}
