package com.batteryforensics.ruleengine.rules

import com.batteryforensics.core.evidence.Confidence
import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.evidence.DiagnosticCategory
import com.batteryforensics.core.evidence.Evidence
import com.batteryforensics.core.evidence.SupportingMetric
import com.batteryforensics.core.time.TimeConstants
import com.batteryforensics.ruleengine.ForensicRule
import com.batteryforensics.ruleengine.RuleContext
import com.batteryforensics.ruleengine.RuleEvaluation
import kotlin.math.abs

/** GPS / location services left enabled with prolonged screen-off drain. */
class LocationEnabledDrainRule : ForensicRule {
    override val id: String = "location_enabled_drain"
    override val title: String = "GPS / location-enabled prolonged drain"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val samples = context.samples
        if (samples.size < 4) return null
        val locOn = samples.count { it.locationEnabled == true }.toDouble() / samples.size
        if (locOn < 0.6) return null
        val screenOff = samples.filter { it.screenOn != true }
        if (screenOff.size < 3) return null
        val highCurrent = screenOff.mapNotNull { it.currentMicroamps }.count { it < -200_000 }
        if (highCurrent < 2 && locOn < 0.9) return null
        val score = (62 + (locOn * 25)).toInt().coerceIn(62, 88)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.SENSORS,
                explanation =
                    "Location services stayed enabled for ${"%.0f".format(locOn * 100)}% of samples, " +
                        "including screen-off periods with elevated discharge. Location radios and fused providers raise idle cost — Measured flag, Derived impact.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = listOf(
                    Evidence(
                        id = "location_on_ratio",
                        description = "Location enabled ratio",
                        metricKey = "location_enabled_ratio",
                        observedValue = "${"%.0f".format(locOn * 100)}%",
                        threshold = "≥60%",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("high_current_off", "Screen-off high-current samples", highCurrent.toString()),
                ),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Turn off location overnight if unused",
                    "Revoke background location from non-essential apps",
                    "Prefer battery-saving location mode",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class BluetoothLeftOnDrainRule : ForensicRule {
    override val id: String = "bluetooth_left_on_drain"
    override val title: String = "Bluetooth left on / connected drain"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val samples = context.samples
        if (samples.size < 4) return null
        val btOn = samples.count { it.bluetoothOn == true }.toDouble() / samples.size
        val connected = samples.count { it.bluetoothConnected == true }.toDouble() / samples.size
        if (btOn < 0.7 && connected < 0.4) return null
        val score = when {
            connected >= 0.5 -> 78
            btOn >= 0.85 -> 70
            else -> 64
        }
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.NETWORK,
                explanation =
                    "Bluetooth was on ${"%.0f".format(btOn * 100)}% of the window" +
                        (if (connected > 0) " and connected ${"%.0f".format(connected * 100)}%" else "") +
                        ". Scanning/connected audio keeps the radio awake — Measured radio state, Derived drain attribution.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = buildList {
                    add(
                        Evidence(
                            id = "bt_on",
                            description = "Bluetooth enabled ratio",
                            metricKey = "bluetooth_on_ratio",
                            observedValue = "${"%.0f".format(btOn * 100)}%",
                            confidenceLevel = ConfidenceLevel.MEASURED,
                        ),
                    )
                    if (connected > 0) {
                        add(
                            Evidence(
                                id = "bt_connected",
                                description = "Bluetooth connected ratio",
                                metricKey = "bluetooth_connected_ratio",
                                observedValue = "${"%.0f".format(connected * 100)}%",
                                confidenceLevel = ConfidenceLevel.MEASURED,
                            ),
                        )
                    }
                },
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Disable Bluetooth overnight if unused",
                    "Disconnect idle accessories",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class HotspotOnDrainRule : ForensicRule {
    override val id: String = "hotspot_on_drain"
    override val title: String = "Hotspot / tethering drain"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val samples = context.samples
        if (samples.size < 3) return null
        val ratio = samples.count { it.hotspotOn == true }.toDouble() / samples.size
        if (ratio < 0.3) return null
        val score = (75 + ratio * 20).toInt().coerceIn(75, 95)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.NETWORK,
                explanation =
                    "Wi-Fi hotspot/tethering was active for ${"%.0f".format(ratio * 100)}% of samples. " +
                        "SoftAP keeps Wi-Fi and often the modem in high-power modes — Measured flag when available.",
                confidence = Confidence(score, ConfidenceLevel.MEASURED),
                evidence = listOf(
                    Evidence(
                        id = "hotspot_ratio",
                        description = "Hotspot active ratio",
                        metricKey = "hotspot_on_ratio",
                        observedValue = "${"%.0f".format(ratio * 100)}%",
                        threshold = "≥30%",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Turn off hotspot when not sharing",
                    "Prefer wired USB tethering if available and cooler",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

/** Dedicated 120 Hz while screen-on (refines display evidence). */
class HighRefreshWhileScreenOnRule : ForensicRule {
    override val id: String = "display_120hz_screen_on"
    override val title: String = "120 Hz while screen-on excessive"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val screenOn = context.samples.filter { it.screenOn == true }
        val rates = screenOn.mapNotNull { it.refreshRateHz }
        if (rates.size < 3) return null
        val high = rates.count { it >= TimeConstants.HIGH_REFRESH_DEDICATED_HZ }
        val ratio = high.toDouble() / rates.size
        if (ratio < 0.55) return null
        val score = (68 + ratio * 25).toInt().coerceIn(68, 92)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.DISPLAY,
                explanation =
                    "While screen-on, refresh rate was ≥120 Hz for ${"%.0f".format(ratio * 100)}% of samples. " +
                        "Panel power scales with refresh — Measured.",
                confidence = Confidence(score, ConfidenceLevel.MEASURED),
                evidence = listOf(
                    Evidence(
                        id = "refresh_120",
                        description = "120 Hz+ during screen-on",
                        metricKey = "refresh_rate_hz",
                        observedValue = "${"%.0f".format(ratio * 100)}% ≥${TimeConstants.HIGH_REFRESH_DEDICATED_HZ.toInt()} Hz",
                        threshold = "≥55% of screen-on samples",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("avg_hz", "Avg refresh", "${"%.0f".format(rates.average())} Hz"),
                ),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Force 60 Hz in display settings when battery matters",
                    "Disable peak refresh for static content apps",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

/** Extreme ΔT/Δt distinct from mild rapid heat. */
class ThermalRunawayIshRule : ForensicRule {
    override val id: String = "thermal_runaway_ish"
    override val title: String = "Thermal runaway-ish extreme heating"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val ordered = context.samples.sortedBy { it.timestampEpochMs }
        val temps = ordered.mapNotNull { s -> s.temperatureC?.let { s.timestampEpochMs to it } }
        if (temps.size < 3) return null
        val maxTemp = temps.maxOf { it.second }
        val minTemp = temps.minOf { it.second }
        val delta = maxTemp - minTemp
        val durationMin = (temps.last().first - temps.first().first).toDouble() /
            TimeConstants.MILLIS_PER_MINUTE
        val rate = if (durationMin > 0.5) delta.toDouble() / durationMin else delta.toDouble()
        val extreme = maxTemp >= TimeConstants.THERMAL_RUNAWAY_TEMP_C &&
            (delta >= TimeConstants.THERMAL_RUNAWAY_DELTA_C || rate >= TimeConstants.RAPID_HEAT_C_PER_MINUTE * 2)
        if (!extreme) return null
        val score = 92
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.THERMAL,
                explanation =
                    "Temperature peaked at ${"%.1f".format(maxTemp)}°C with ΔT=${"%.1f".format(delta)}°C " +
                        "(~${"%.2f".format(rate)}°C/min). This exceeds mild rapid-heat thresholds — treat as urgent Measured thermal evidence.",
                confidence = Confidence(score, ConfidenceLevel.MEASURED),
                evidence = listOf(
                    Evidence(
                        id = "extreme_temp",
                        description = "Extreme peak temperature",
                        metricKey = "temperature_c",
                        observedValue = "${"%.1f".format(maxTemp)}°C",
                        threshold = "≥${TimeConstants.THERMAL_RUNAWAY_TEMP_C}°C",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                    Evidence(
                        id = "extreme_delta",
                        description = "Extreme temperature rise",
                        metricKey = "temperature_delta_c",
                        observedValue = "${"%.1f".format(delta)}°C (~${"%.2f".format(rate)}°C/min)",
                        threshold = "≥${TimeConstants.THERMAL_RUNAWAY_DELTA_C}°C",
                        confidenceLevel = ConfidenceLevel.DERIVED,
                    ),
                ),
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Stop charging and heavy workloads immediately",
                    "Cool the device; remove case",
                    "If heat recurs at idle, seek hardware service",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class ChargingInefficiencyHeatRule : ForensicRule {
    override val id: String = "charging_inefficiency_heat"
    override val title: String = "Charging inefficiency + heat combo"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val charging = context.samples.filter { it.isCharging == true }
        if (charging.size < 3) return null
        val hot = charging.mapNotNull { it.temperatureC }.count { it >= TimeConstants.CHARGING_HEAT_TEMP_C }
        val hotRatio = hot.toDouble() / charging.count { it.temperatureC != null }.coerceAtLeast(1)
        val currents = charging.mapNotNull { it.chargingCurrentMicroamps ?: it.currentMicroamps?.let { ua -> abs(ua) } }
        val avgUa = currents.average().takeIf { currents.isNotEmpty() }
        val slowHot = avgUa != null && avgUa < TimeConstants.INEFFICIENT_CHARGE_CURRENT_UA && hotRatio >= 0.4
        val fastHot = avgUa != null && avgUa >= TimeConstants.INEFFICIENT_CHARGE_CURRENT_UA && hotRatio >= 0.5
        if (!slowHot && !fastHot && hotRatio < 0.6) return null
        val score = if (slowHot) 84 else 80
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.CHARGING,
                explanation =
                    if (slowHot) {
                        "Charging stayed hot (${"%.0f".format(hotRatio * 100)}% ≥${TimeConstants.CHARGING_HEAT_TEMP_C}°C) " +
                            "while average charge current was only ~${"%.0f".format((avgUa ?: 0.0) / 1000.0)} mA — inefficient heat without throughput."
                    } else {
                        "High charge current with concurrent heat (${"%.0f".format(hotRatio * 100)}% hot samples). " +
                            "Combo stresses chemistry — Measured temp/current, Derived inefficiency claim."
                    },
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = listOf(
                    Evidence(
                        id = "charge_heat_ratio",
                        description = "Hot samples while charging",
                        metricKey = "charging_hot_ratio",
                        observedValue = "${"%.0f".format(hotRatio * 100)}%",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                    Evidence(
                        id = "charge_current",
                        description = "Average charge current",
                        metricKey = "charging_current_ua",
                        observedValue = avgUa?.let { "${"%.0f".format(it / 1000.0)} mA" } ?: "n/a",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Use original / cooler charger and cable",
                    "Avoid wireless charging if this combo repeats",
                    "Charge in a cool place; pause if >45°C",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

/** Sudden drain vs prior baseline window — Inferred. */
class BaselineAnomalyRegressionRule : ForensicRule {
    override val id: String = "baseline_anomaly_regression"
    override val title: String = "System update / baseline drain anomaly"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        if (context.baselineSamples.size < 4 || context.samples.size < 4) return null
        val baselineRate = drainPerHour(context.baselineSamples) ?: return null
        val currentRate = drainPerHour(context.samples) ?: return null
        val delta = currentRate - baselineRate
        val deltaPct = if (abs(baselineRate) > 1e-6) delta / abs(baselineRate) * 100.0 else null
        if (delta <= 0.5 && (deltaPct ?: 0.0) < 25.0) return null
        val score = (58 + abs(delta) * 8).toInt().coerceIn(58, 82)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.HISTORICAL_REGRESSION,
                explanation =
                    "Current overall drain ${"%.2f".format(currentRate)}%/h vs baseline ${"%.2f".format(baselineRate)}%/h " +
                        "(Δ ${"%.2f".format(delta)}). Consistent with post-update or behavior regression — Inferred, not proof of a specific OS bug.",
                confidence = Confidence(score, ConfidenceLevel.INFERRED),
                evidence = listOf(
                    Evidence(
                        id = "baseline_delta",
                        description = "Baseline vs current overall drain %/h",
                        metricKey = "overall_drain_percent_per_hour",
                        observedValue = "${"%.2f".format(currentRate)} (baseline ${"%.2f".format(baselineRate)})",
                        confidenceLevel = ConfidenceLevel.INFERRED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("delta", "Drain delta %/h", "%.2f".format(delta)),
                    SupportingMetric("delta_pct", "Relative change", deltaPct?.let { "%.0f%%".format(it) } ?: "n/a"),
                ),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Note last system update date and compare another night",
                    "Boot into safe mode overnight to test third-party regression",
                    "Export both windows for differential analysis",
                ),
                probabilityPercent = score,
            ),
        )
    }

    private fun drainPerHour(samples: List<com.batteryforensics.core.model.MonitoringSample>): Double? {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val start = ordered.first().batteryPercent ?: return null
        val end = ordered.last().batteryPercent ?: return null
        val hours = (ordered.last().timestampEpochMs - ordered.first().timestampEpochMs)
            .toDouble() / TimeConstants.MILLIS_PER_HOUR
        if (hours < 1.0) return null
        return ((start - end) / hours).takeIf { it > 0 }
    }
}

class NfcLeftOnDrainRule : ForensicRule {
    override val id: String = "nfc_left_on_drain"
    override val title: String = "NFC left enabled"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val samples = context.samples
        if (samples.size < 6) return null
        val ratio = samples.count { it.nfcEnabled == true }.toDouble() / samples.size
        if (ratio < 0.85) return null
        // NFC alone is mild — only flag with concurrent standby drain
        val ordered = samples.filter { it.screenOn != true }.sortedBy { it.timestampEpochMs }
        if (ordered.size < 3) return null
        val start = ordered.first().batteryPercent ?: return null
        val end = ordered.last().batteryPercent ?: return null
        val hours = (ordered.last().timestampEpochMs - ordered.first().timestampEpochMs)
            .toDouble() / TimeConstants.MILLIS_PER_HOUR
        if (hours < 2) return null
        val drain = (start - end) / hours
        if (drain < 2.0) return null
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.SENSORS,
                explanation =
                    "NFC stayed enabled (${"%.0f".format(ratio * 100)}%) during elevated standby drain " +
                        "(${"%.2f".format(drain)}%/h). NFC is usually minor — Speculative contributor unless OEM polling is aggressive.",
                confidence = Confidence(55, ConfidenceLevel.SPECULATIVE),
                evidence = listOf(
                    Evidence(
                        id = "nfc_on",
                        description = "NFC enabled ratio",
                        metricKey = "nfc_enabled_ratio",
                        observedValue = "${"%.0f".format(ratio * 100)}%",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("standby_drain", "Standby drain", "${"%.2f".format(drain)} %/h"),
                ),
                counterEvidence = listOf(
                    Evidence(
                        id = "nfc_minor",
                        description = "NFC power is typically small vs modem/display — treat as secondary",
                        metricKey = "nfc_caveat",
                        observedValue = "Speculative",
                        confidenceLevel = ConfidenceLevel.SPECULATIVE,
                    ),
                ),
                recommendedActions = listOf("Disable NFC when unused if standby remains high after other fixes"),
                probabilityPercent = 55,
            ),
        )
    }
}

class LowStoragePressureRule : ForensicRule {
    override val id: String = "low_storage_pressure"
    override val title: String = "Low free storage pressure"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val pct = context.samples.mapNotNull { it.storageFreePercent }.minOrNull() ?: return null
        if (pct > TimeConstants.STORAGE_LOW_PERCENT) return null
        val score = (60 + (TimeConstants.STORAGE_LOW_PERCENT - pct) * 2).toInt().coerceIn(60, 85)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.STORAGE,
                explanation =
                    "Free storage fell to ${"%.1f".format(pct)}%. Low free space increases flash write amplification and GC, " +
                        "which can raise idle/background drain — Measured free space, Inferred drain link.",
                confidence = Confidence(score, ConfidenceLevel.INFERRED),
                evidence = listOf(
                    Evidence(
                        id = "storage_free",
                        description = "Minimum free storage percent",
                        metricKey = "storage_free_percent",
                        observedValue = "${"%.1f".format(pct)}%",
                        threshold = "≤${TimeConstants.STORAGE_LOW_PERCENT}%",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Free ≥15% storage",
                    "Clear large caches / offline media",
                ),
                probabilityPercent = score,
            ),
        )
    }
}
