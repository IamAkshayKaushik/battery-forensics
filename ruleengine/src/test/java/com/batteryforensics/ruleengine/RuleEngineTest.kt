package com.batteryforensics.ruleengine

import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.parser.AlarmSummary
import com.batteryforensics.parser.DeviceIdleSummary
import com.batteryforensics.parser.DozeTimelineSummary
import com.batteryforensics.parser.JobSchedulerSummary
import com.batteryforensics.parser.PackageCount
import com.batteryforensics.parser.UsageStatsSummary
import com.batteryforensics.parser.WakeLockSummary
import com.batteryforensics.ruleengine.rules.DefaultRules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleEngineTest {
    private val engine = RuleEngine(DefaultRules.all())

    @Test
    fun defaultRules_countIsExpanded() {
        assertThat(DefaultRules.all().size).isAtLeast(25)
    }

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

    @Test
    fun locationEnabled_triggers() {
        val samples = (1..8).map {
            sample(locationEnabled = true, screenOn = false, currentUa = -250_000)
        }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("location_enabled_drain")
    }

    @Test
    fun bluetoothLeftOn_triggers() {
        val samples = (1..8).map {
            sample(bluetoothOn = true, bluetoothConnected = true)
        }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("bluetooth_left_on_drain")
    }

    @Test
    fun hotspotOn_triggers() {
        val samples = (1..6).map { sample(hotspotOn = true) }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("hotspot_on_drain")
    }

    @Test
    fun display120Hz_triggers() {
        val samples = (1..6).map {
            sample(screenOn = true, refreshRateHz = 120f)
        }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("display_120hz_screen_on")
    }

    @Test
    fun thermalRunawayIsh_triggers() {
        val t0 = 1_000_000L
        val samples = listOf(
            sample(timestamp = t0, temperatureC = 35f),
            sample(timestamp = t0 + 60_000, temperatureC = 42f),
            sample(timestamp = t0 + 120_000, temperatureC = 50f),
        )
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("thermal_runaway_ish")
    }

    @Test
    fun dozeFailure_triggersFromPrivileged() {
        val priv = PrivilegedEvidence(
            deviceIdle = DeviceIdleSummary(deepEnabled = false, lightEnabled = true, state = "ACTIVE"),
            doze = DozeTimelineSummary(
                state = "ACTIVE",
                deepEnabled = false,
                lightEnabled = true,
                historyHints = listOf("ACTIVE", "INACTIVE"),
            ),
        )
        val result = engine.evaluate(RuleContext(samples = listOf(sample()), privileged = priv))
        assertThat(result.map { it.id }).contains("doze_failure_to_enter")
        assertThat(result.first { it.id == "doze_failure_to_enter" }.confidence.starsLabel)
            .contains("Derived")
    }

    @Test
    fun alarmStorm_triggersFromPrivileged() {
        val priv = PrivilegedEvidence(
            alarms = AlarmSummary(
                wakeupAlarmCount = 80,
                topPackages = listOf(PackageCount("com.example.chatty", 40)),
            ),
        )
        val result = engine.evaluate(RuleContext(samples = listOf(sample()), privileged = priv))
        assertThat(result.map { it.id }).contains("alarm_storm")
    }

    @Test
    fun wakeLockAbuse_triggersFromPrivileged() {
        val priv = PrivilegedEvidence(
            wakeLocks = WakeLockSummary(
                totalLocks = 12,
                appLocks = 9,
                kernelLocks = 3,
                topTags = listOf(PackageCount("*alarm*", 4)),
            ),
        )
        val result = engine.evaluate(RuleContext(samples = listOf(sample()), privileged = priv))
        assertThat(result.map { it.id }).contains("wake_lock_abuse")
    }

    @Test
    fun gmsWakeup_isInferred() {
        val priv = PrivilegedEvidence(
            alarms = AlarmSummary(
                wakeupAlarmCount = 20,
                topPackages = listOf(PackageCount("com.google.android.gms", 15)),
            ),
        )
        val result = engine.evaluate(RuleContext(samples = listOf(sample()), privileged = priv))
        assertThat(result.map { it.id }).contains("gms_wakeup_pattern")
        assertThat(result.first { it.id == "gms_wakeup_pattern" }.confidence.starsLabel)
            .contains("Inferred")
    }

    @Test
    fun jobThrash_triggers() {
        val priv = PrivilegedEvidence(
            jobs = JobSchedulerSummary(pendingJobCount = 40, runningJobCount = 5),
        )
        val result = engine.evaluate(RuleContext(samples = listOf(sample()), privileged = priv))
        assertThat(result.map { it.id }).contains("jobscheduler_thrash")
    }

    @Test
    fun appStandbyBypass_triggers() {
        val priv = PrivilegedEvidence(
            usageStats = UsageStatsSummary(
                standbyBucketHints = listOf("ACTIVE", "RARE"),
                elevatedBucketPackages = listOf("com.example.keeplive"),
            ),
        )
        val result = engine.evaluate(RuleContext(samples = listOf(sample()), privileged = priv))
        assertThat(result.map { it.id }).contains("app_standby_bypass")
    }

    @Test
    fun baselineAnomaly_triggers() {
        val t0 = 1_000_000L
        val baseline = listOf(
            sample(timestamp = t0, batteryPercent = 90, screenOn = false),
            sample(timestamp = t0 + 3_600_000, batteryPercent = 88, screenOn = false),
            sample(timestamp = t0 + 2 * 3_600_000, batteryPercent = 86, screenOn = false),
            sample(timestamp = t0 + 3 * 3_600_000, batteryPercent = 84, screenOn = false),
        )
        val current = listOf(
            sample(timestamp = t0 + 10 * 3_600_000, batteryPercent = 90, screenOn = false),
            sample(timestamp = t0 + 11 * 3_600_000, batteryPercent = 80, screenOn = false),
            sample(timestamp = t0 + 12 * 3_600_000, batteryPercent = 70, screenOn = false),
            sample(timestamp = t0 + 13 * 3_600_000, batteryPercent = 60, screenOn = false),
        )
        val result = engine.evaluate(RuleContext(samples = current, baselineSamples = baseline))
        assertThat(result.map { it.id }).contains("baseline_anomaly_regression")
        assertThat(result.first { it.id == "baseline_anomaly_regression" }.confidence.starsLabel)
            .contains("Inferred")
    }

    @Test
    fun lowStorage_triggers() {
        val samples = (1..4).map { sample(storageFreePercent = 5f) }
        val result = engine.evaluate(RuleContext(samples))
        assertThat(result.map { it.id }).contains("low_storage_pressure")
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
        locationEnabled: Boolean? = null,
        bluetoothOn: Boolean? = null,
        bluetoothConnected: Boolean? = null,
        hotspotOn: Boolean? = null,
        storageFreePercent: Float? = null,
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
        locationEnabled = locationEnabled,
        bluetoothOn = bluetoothOn,
        bluetoothConnected = bluetoothConnected,
        hotspotOn = hotspotOn,
        storageFreePercent = storageFreePercent,
    )
}
