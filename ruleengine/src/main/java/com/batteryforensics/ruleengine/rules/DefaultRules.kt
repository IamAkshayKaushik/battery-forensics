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

class WeakCellularSignalRule : ForensicRule {
    override val id: String = "weak_cellular_signal"
    override val title: String = "Weak cellular signal"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val withSignal = context.samples.mapNotNull { it.cellularRssiDbm }
        if (withSignal.isEmpty()) return null

        val weakCount = withSignal.count { it <= TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD }
        val weakRatio = weakCount.toDouble() / withSignal.size
        if (weakRatio < 0.5) return null

        val avg = withSignal.average()
        val score = (70 + (weakRatio * 25)).toInt().coerceIn(0, 98)
        val diagnosis = Diagnosis(
            id = id,
            title = title,
            category = DiagnosticCategory.MODEM,
            explanation =
                "Cellular RSSI spent most of the window at or below ${TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD} dBm. " +
                    "Weak signal forces the modem to raise transmit power and stay awake longer, which drains battery.",
            confidence = Confidence(scorePercent = score, level = ConfidenceLevel.MEASURED),
            evidence = listOf(
                Evidence(
                    id = "rssi_threshold",
                    description = "Signal at or below weak threshold",
                    metricKey = "cellular_rssi_dbm",
                    observedValue = "${"%.1f".format(avg)} dBm avg",
                    threshold = "${TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD} dBm",
                    confidenceLevel = ConfidenceLevel.MEASURED,
                ),
                Evidence(
                    id = "weak_ratio",
                    description = "Share of samples in weak range",
                    metricKey = "weak_signal_ratio",
                    observedValue = "${"%.0f".format(weakRatio * 100)}%",
                    threshold = "≥50%",
                    confidenceLevel = ConfidenceLevel.DERIVED,
                ),
            ),
            supportingMetrics = listOf(
                SupportingMetric("sample_count", "Samples with RSSI", withSignal.size.toString()),
                SupportingMetric("weak_count", "Weak samples", weakCount.toString()),
            ),
            counterEvidence = buildList {
                val wifiConnectedRatio = context.samples.count { it.wifiConnected == true }
                    .toDouble() / context.samples.size.coerceAtLeast(1)
                if (wifiConnectedRatio > 0.7) {
                    add(
                        Evidence(
                            id = "wifi_dominant",
                            description = "Wi-Fi was connected for most of the window; cellular may not be primary path",
                            metricKey = "wifi_connected_ratio",
                            observedValue = "${"%.0f".format(wifiConnectedRatio * 100)}%",
                            confidenceLevel = ConfidenceLevel.MEASURED,
                        ),
                    )
                }
            },
            recommendedActions = listOf(
                "Prefer Wi-Fi indoors when available",
                "Switch to LTE if 5G is unstable in this location",
                "Enable airplane mode overnight if calls/SMS are not needed",
            ),
            probabilityPercent = score,
        )
        return RuleEvaluation(triggered = true, diagnosis = diagnosis)
    }
}

class ExcessiveScreenBrightnessRule : ForensicRule {
    override val id: String = "excessive_screen_brightness"
    override val title: String = "Excessive screen brightness / high refresh"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val screenOn = context.samples.filter { it.screenOn == true }
        if (screenOn.size < 3) return null

        val bright = screenOn.mapNotNull { it.brightnessPercent }
            .count { it >= TimeConstants.HIGH_BRIGHTNESS_PERCENT }
        val highRefresh = screenOn.mapNotNull { it.refreshRateHz }
            .count { it >= TimeConstants.HIGH_REFRESH_RATE_HZ }

        val brightRatio = if (bright > 0 && screenOn.any { it.brightnessPercent != null }) {
            bright.toDouble() / screenOn.count { it.brightnessPercent != null }.coerceAtLeast(1)
        } else {
            0.0
        }
        val refreshRatio = if (highRefresh > 0 && screenOn.any { it.refreshRateHz != null }) {
            highRefresh.toDouble() / screenOn.count { it.refreshRateHz != null }.coerceAtLeast(1)
        } else {
            0.0
        }

        if (brightRatio < 0.5 && refreshRatio < 0.5) return null

        val score = (60 + ((brightRatio + refreshRatio) / 2.0 * 30)).toInt().coerceIn(0, 95)
        val diagnosis = Diagnosis(
            id = id,
            title = title,
            category = DiagnosticCategory.DISPLAY,
            explanation =
                "While the screen was on, brightness and/or refresh rate were frequently elevated. " +
                    "Display power scales strongly with both factors.",
            confidence = Confidence(scorePercent = score, level = ConfidenceLevel.MEASURED),
            evidence = buildList {
                if (brightRatio >= 0.5) {
                    add(
                        Evidence(
                            id = "brightness",
                            description = "High brightness during screen-on",
                            metricKey = "brightness_percent",
                            observedValue = "${"%.0f".format(brightRatio * 100)}% of samples ≥${TimeConstants.HIGH_BRIGHTNESS_PERCENT}%",
                            threshold = "≥${TimeConstants.HIGH_BRIGHTNESS_PERCENT}%",
                            confidenceLevel = ConfidenceLevel.MEASURED,
                        ),
                    )
                }
                if (refreshRatio >= 0.5) {
                    add(
                        Evidence(
                            id = "refresh",
                            description = "High refresh rate during screen-on",
                            metricKey = "refresh_rate_hz",
                            observedValue = "${"%.0f".format(refreshRatio * 100)}% of samples ≥${TimeConstants.HIGH_REFRESH_RATE_HZ.toInt()} Hz",
                            threshold = "≥${TimeConstants.HIGH_REFRESH_RATE_HZ.toInt()} Hz",
                            confidenceLevel = ConfidenceLevel.MEASURED,
                        ),
                    )
                }
            },
            supportingMetrics = listOf(
                SupportingMetric("screen_on_samples", "Screen-on samples", screenOn.size.toString()),
            ),
            counterEvidence = emptyList(),
            recommendedActions = listOf(
                "Lower brightness or use adaptive brightness",
                "Prefer 60 Hz when battery life matters more than smoothness",
                "Reduce outdoor-level brightness indoors",
            ),
            probabilityPercent = score,
        )
        return RuleEvaluation(triggered = true, diagnosis = diagnosis)
    }
}

class ElevatedTemperatureRule : ForensicRule {
    override val id: String = "elevated_temperature"
    override val title: String = "Elevated temperature / rapid heating"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val temps = context.samples.mapNotNull { it.temperatureC }
        if (temps.size < 2) return null

        val maxTemp = temps.max()
        val minTemp = temps.min()
        val delta = maxTemp - minTemp
        val elevated = maxTemp >= TimeConstants.ELEVATED_TEMP_C
        val rapid = delta >= TimeConstants.RAPID_HEAT_DELTA_C

        if (!elevated && !rapid) return null

        val level = when {
            elevated && rapid -> ConfidenceLevel.MEASURED
            elevated -> ConfidenceLevel.MEASURED
            else -> ConfidenceLevel.DERIVED
        }
        val score = when {
            elevated && rapid -> 90
            elevated -> 78
            else -> 65
        }
        val diagnosis = Diagnosis(
            id = id,
            title = title,
            category = DiagnosticCategory.THERMAL,
            explanation =
                "Battery/system temperature reached ${"%.1f".format(maxTemp)}°C" +
                    if (rapid) " with a rise of ${"%.1f".format(delta)}°C in the window." else "." +
                    " Heat increases internal resistance and accelerates drain; sustained heat also throttles the SoC.",
            confidence = Confidence(scorePercent = score, level = level),
            evidence = buildList {
                if (elevated) {
                    add(
                        Evidence(
                            id = "max_temp",
                            description = "Peak temperature above threshold",
                            metricKey = "temperature_c",
                            observedValue = "${"%.1f".format(maxTemp)}°C",
                            threshold = "≥${TimeConstants.ELEVATED_TEMP_C}°C",
                            confidenceLevel = ConfidenceLevel.MEASURED,
                        ),
                    )
                }
                if (rapid) {
                    add(
                        Evidence(
                            id = "temp_delta",
                            description = "Rapid temperature rise",
                            metricKey = "temperature_delta_c",
                            observedValue = "${"%.1f".format(delta)}°C",
                            threshold = "≥${TimeConstants.RAPID_HEAT_DELTA_C}°C",
                            confidenceLevel = ConfidenceLevel.DERIVED,
                        ),
                    )
                }
            },
            supportingMetrics = listOf(
                SupportingMetric("min_temp", "Min temp", "${"%.1f".format(minTemp)}°C", "°C"),
                SupportingMetric("max_temp", "Max temp", "${"%.1f".format(maxTemp)}°C", "°C"),
            ),
            counterEvidence = emptyList(),
            recommendedActions = listOf(
                "Remove case while charging or gaming if heat persists",
                "Avoid charging under pillows / direct sun",
                "Check for background CPU/GPU-heavy apps",
            ),
            probabilityPercent = score,
        )
        return RuleEvaluation(triggered = true, diagnosis = diagnosis)
    }
}

class ChargingHeatRule : ForensicRule {
    override val id: String = "charging_heat"
    override val title: String = "Charging heat"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val chargingHot = context.samples.filter {
            it.isCharging == true && (it.temperatureC ?: 0f) >= TimeConstants.CHARGING_HEAT_TEMP_C
        }
        if (chargingHot.isEmpty()) return null

        val maxTemp = chargingHot.mapNotNull { it.temperatureC }.maxOrNull() ?: return null
        val score = (75 + ((maxTemp - TimeConstants.CHARGING_HEAT_TEMP_C) * 3)).toInt().coerceIn(70, 95)
        val diagnosis = Diagnosis(
            id = id,
            title = title,
            category = DiagnosticCategory.CHARGING,
            explanation =
                "Temperature reached ${"%.1f".format(maxTemp)}°C while charging. " +
                    "Charging heat stresses chemistry and can increase subsequent idle drain.",
            confidence = Confidence(scorePercent = score, level = ConfidenceLevel.MEASURED),
            evidence = listOf(
                Evidence(
                    id = "charging_temp",
                    description = "High temperature during charge",
                    metricKey = "temperature_c_while_charging",
                    observedValue = "${"%.1f".format(maxTemp)}°C",
                    threshold = "≥${TimeConstants.CHARGING_HEAT_TEMP_C}°C",
                    confidenceLevel = ConfidenceLevel.MEASURED,
                ),
            ),
            supportingMetrics = listOf(
                SupportingMetric("hot_samples", "Hot charging samples", chargingHot.size.toString()),
            ),
            counterEvidence = emptyList(),
            recommendedActions = listOf(
                "Use a cooler surface while charging",
                "Prefer slower charging overnight",
                "Avoid wireless charging if heat is chronic",
            ),
            probabilityPercent = score,
        )
        return RuleEvaluation(triggered = true, diagnosis = diagnosis)
    }
}

class OvernightStandbyDrainRule : ForensicRule {
    override val id: String = "overnight_standby_drain"
    override val title: String = "High overnight standby drain"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val ordered = context.samples.sortedBy { it.timestampEpochMs }
        if (ordered.size < 4) return null

        val start = ordered.first()
        val end = ordered.last()
        val startPct = start.batteryPercent ?: return null
        val endPct = end.batteryPercent ?: return null
        val durationMs = (end.timestampEpochMs - start.timestampEpochMs).coerceAtLeast(1)
        val hours = durationMs.toDouble() / TimeConstants.MILLIS_PER_HOUR
        if (hours < 4.0) return null

        val screenOnRatio = ordered.count { it.screenOn == true }.toDouble() / ordered.size
        if (screenOnRatio > 0.15) return null

        val drop = (startPct - endPct).coerceAtLeast(0)
        val drainPerHour = drop / hours
        if (drainPerHour < TimeConstants.OVERNIGHT_STANDBY_DRAIN_PERCENT_PER_HOUR) return null

        val score = (70 + ((drainPerHour - TimeConstants.OVERNIGHT_STANDBY_DRAIN_PERCENT_PER_HOUR) * 8))
            .toInt()
            .coerceIn(70, 96)

        val diagnosis = Diagnosis(
            id = id,
            title = title,
            category = DiagnosticCategory.STANDBY,
            explanation =
                "Over ${"%.1f".format(hours)} hours with mostly screen-off, battery fell from $startPct% to $endPct% " +
                    "(${"%.2f".format(drainPerHour)}%/h). That exceeds a healthy standby budget and warrants wake-path investigation.",
            confidence = Confidence(scorePercent = score, level = ConfidenceLevel.DERIVED),
            evidence = listOf(
                Evidence(
                    id = "standby_rate",
                    description = "Standby drain rate",
                    metricKey = "standby_drain_percent_per_hour",
                    observedValue = "${"%.2f".format(drainPerHour)}%/h",
                    threshold = "≥${TimeConstants.OVERNIGHT_STANDBY_DRAIN_PERCENT_PER_HOUR}%/h",
                    confidenceLevel = ConfidenceLevel.DERIVED,
                ),
                Evidence(
                    id = "screen_off",
                    description = "Screen mostly off during window",
                    metricKey = "screen_on_ratio",
                    observedValue = "${"%.0f".format(screenOnRatio * 100)}%",
                    threshold = "≤15%",
                    confidenceLevel = ConfidenceLevel.MEASURED,
                ),
            ),
            supportingMetrics = listOf(
                SupportingMetric("start_pct", "Start battery", "$startPct%"),
                SupportingMetric("end_pct", "End battery", "$endPct%"),
                SupportingMetric("hours", "Duration hours", "%.1f".format(hours)),
            ),
            counterEvidence = buildList {
                if (ordered.any { it.isCharging == true }) {
                    add(
                        Evidence(
                            id = "charging_present",
                            description = "Some samples were charging; drain estimate may be diluted",
                            metricKey = "is_charging",
                            observedValue = "true in window",
                            confidenceLevel = ConfidenceLevel.MEASURED,
                        ),
                    )
                }
            },
            recommendedActions = listOf(
                "Review apps with unrestricted battery access",
                "Check for sync / push-heavy apps overnight",
                "Capture a Flight Recorder session overnight for wake evidence",
            ),
            probabilityPercent = score,
        )
        return RuleEvaluation(triggered = true, diagnosis = diagnosis)
    }
}

/** Inferred: heat + weak cellular without charging / screen load. */
class ModemInducedHeatingRule : ForensicRule {
    override val id: String = "modem_induced_heating"
    override val title: String = "Modem-induced heating (inferred)"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val samples = context.samples
        if (samples.size < 4) return null
        val hotWeak = samples.filter {
            (it.temperatureC ?: 0f) >= TimeConstants.MODEM_HEAT_TEMP_C &&
                (it.cellularRssiDbm ?: 0) <= TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD &&
                it.isCharging != true &&
                it.screenOn != true
        }
        if (hotWeak.size < 3) return null
        val ratio = hotWeak.size.toDouble() / samples.size
        if (ratio < 0.25) return null

        val avgTemp = hotWeak.mapNotNull { it.temperatureC }.average()
        val avgRssi = hotWeak.mapNotNull { it.cellularRssiDbm }.average()
        val score = (55 + ratio * 30).toInt().coerceIn(55, 82)

        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.MODEM,
                explanation =
                    "While screen-off and not charging, temperature stayed elevated alongside weak cellular signal. " +
                        "This pattern is consistent with modem TX heating — labeled Inferred (not a direct modem thermocouple).",
                confidence = Confidence(score, ConfidenceLevel.INFERRED),
                evidence = listOf(
                    Evidence(
                        id = "hot_weak_ratio",
                        description = "Screen-off samples that are hot + weak signal",
                        metricKey = "modem_heat_pattern_ratio",
                        observedValue = "${"%.0f".format(ratio * 100)}%",
                        threshold = "≥25%",
                        confidenceLevel = ConfidenceLevel.INFERRED,
                    ),
                    Evidence(
                        id = "avg_temp_rssi",
                        description = "Avg temp / RSSI in pattern window",
                        metricKey = "temp_and_rssi",
                        observedValue = "${"%.1f".format(avgTemp)}°C @ ${"%.0f".format(avgRssi)} dBm",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("pattern_samples", "Matching samples", hotWeak.size.toString()),
                ),
                counterEvidence = buildList {
                    if (samples.any { (it.thermalStatus ?: 0) >= 3 }) {
                        add(
                            Evidence(
                                id = "system_throttle",
                                description = "System thermal status also elevated — heat may be SoC-wide, not modem-only",
                                metricKey = "thermal_status",
                                observedValue = "≥SEVERE",
                                confidenceLevel = ConfidenceLevel.MEASURED,
                            ),
                        )
                    }
                },
                recommendedActions = listOf(
                    "Prefer Wi-Fi or airplane mode in poor coverage overnight",
                    "Force LTE if 5G hunting is suspected",
                    "Collect Shizuku batterystats for radio-active confirmation",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class WeakWifiDrainRule : ForensicRule {
    override val id: String = "weak_wifi_drain"
    override val title: String = "Weak Wi-Fi signal drain"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val wifi = context.samples.filter { it.wifiConnected == true }
        val rssi = wifi.mapNotNull { it.wifiRssiDbm }
        if (rssi.size < 4) return null
        val weakRatio = rssi.count { it <= TimeConstants.WEAK_WIFI_DBM_THRESHOLD }.toDouble() / rssi.size
        if (weakRatio < 0.5) return null
        val avg = rssi.average()
        val score = (60 + weakRatio * 25).toInt().coerceIn(60, 88)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.NETWORK,
                explanation =
                    "Wi-Fi stayed connected at weak RSSI (avg ${"%.0f".format(avg)} dBm). " +
                        "Clients raise TX power and retransmit more often, increasing radio drain.",
                confidence = Confidence(score, ConfidenceLevel.MEASURED),
                evidence = listOf(
                    Evidence(
                        id = "wifi_rssi",
                        description = "Weak Wi-Fi RSSI majority",
                        metricKey = "wifi_rssi_dbm",
                        observedValue = "${"%.0f".format(avg)} dBm avg · ${"%.0f".format(weakRatio * 100)}% weak",
                        threshold = "≤${TimeConstants.WEAK_WIFI_DBM_THRESHOLD} dBm",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("wifi_samples", "Wi-Fi samples", rssi.size.toString()),
                ),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Move closer to the AP or use a 5 GHz BSS with better SNR",
                    "Disable Wi-Fi overnight if cellular coverage is strong",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class BatteryAgingRule : ForensicRule {
    override val id: String = "battery_aging_voltage_sag"
    override val title: String = "Battery aging / high voltage sag"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val rest = context.samples.filter {
            val ua = it.currentMicroamps ?: return@filter false
            abs(ua) < 80_000 && it.voltageMv != null
        }.mapNotNull { it.voltageMv }
        val load = context.samples.filter {
            val ua = it.currentMicroamps ?: return@filter false
            ua < -400_000 && it.voltageMv != null
        }.mapNotNull { it.voltageMv }
        if (rest.size < 2 || load.size < 2) return null
        val oc = rest.average()
        val ul = load.average()
        val sag = oc - ul
        if (sag < 150) return null
        val score = (55 + ((sag - 150) / 10)).toInt().coerceIn(55, 85)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.BATTERY_CHEMISTRY,
                explanation =
                    "Voltage sag under load averaged ${"%.0f".format(sag)} mV versus near-rest. " +
                        "Elevated sag is consistent with rising internal resistance (aging) — Derived, not HEALTH_* alone.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = listOf(
                    Evidence(
                        id = "voltage_sag",
                        description = "Rest vs load voltage gap",
                        metricKey = "voltage_sag_mv",
                        observedValue = "${"%.0f".format(sag)} mV",
                        threshold = "≥150 mV",
                        confidenceLevel = ConfidenceLevel.DERIVED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("ocv", "Near-rest voltage", "${"%.0f".format(oc)} mV"),
                    SupportingMetric("load_v", "Under-load voltage", "${"%.0f".format(ul)} mV"),
                ),
                counterEvidence = buildList {
                    if (context.samples.any { (it.temperatureC ?: 0f) < 15f }) {
                        add(
                            Evidence(
                                id = "cold",
                                description = "Cold temperature can inflate sag temporarily",
                                metricKey = "temperature_c",
                                observedValue = "<15°C observed",
                                confidenceLevel = ConfidenceLevel.MEASURED,
                            ),
                        )
                    }
                },
                recommendedActions = listOf(
                    "Compare Chemistry screen Ri trend over days",
                    "Avoid deep discharges if capacity is declining",
                    "If sag worsens rapidly, consider battery service",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class HighDischargeCurrentRule : ForensicRule {
    override val id: String = "high_discharge_current"
    override val title: String = "Sustained high discharge current"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val discharging = context.samples.filter { it.isCharging != true }
        val currents = discharging.mapNotNull { it.currentMicroamps }.filter { it < 0 }
        if (currents.size < 4) return null
        val avgMa = currents.average() / 1_000.0
        // avgMa is negative for discharge
        if (avgMa > -500) return null
        val score = (65 + ((-avgMa - 500) / 50)).toInt().coerceIn(65, 92)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.STANDBY,
                explanation =
                    "Average discharge current was ${"%.0f".format(-avgMa)} mA while not charging. " +
                        "Sustained high draw implies active radios, CPU, or sensors — investigate top wake paths.",
                confidence = Confidence(score, ConfidenceLevel.MEASURED),
                evidence = listOf(
                    Evidence(
                        id = "avg_current",
                        description = "Average discharge current",
                        metricKey = "current_ma",
                        observedValue = "${"%.0f".format(-avgMa)} mA",
                        threshold = "≥500 mA avg",
                        confidenceLevel = ConfidenceLevel.MEASURED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("samples", "Discharge samples", currents.size.toString()),
                ),
                counterEvidence = buildList {
                    val screenOn = discharging.count { it.screenOn == true }.toDouble() / discharging.size
                    if (screenOn > 0.5) {
                        add(
                            Evidence(
                                id = "screen_on",
                                description = "Screen was on for much of the window — high current may be display-driven",
                                metricKey = "screen_on_ratio",
                                observedValue = "${"%.0f".format(screenOn * 100)}%",
                                confidenceLevel = ConfidenceLevel.MEASURED,
                            ),
                        )
                    }
                },
                recommendedActions = listOf(
                    "Check Live Monitor for concurrent screen / thermal / radio state",
                    "Run Causes investigation on the same window",
                    "Use Shizuku dumpsys power for wake lock attribution when available",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class FrequentNetworkTransitionsRule : ForensicRule {
    override val id: String = "frequent_network_transitions"
    override val title: String = "Frequent network transitions"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val types = context.samples.map { it.networkType }
        var changes = 0
        for (i in 1 until types.size) {
            if (types[i] != null && types[i - 1] != null && types[i] != types[i - 1]) changes++
        }
        if (changes < 4) return null
        val hours = ((context.samples.maxOf { it.timestampEpochMs } - context.samples.minOf { it.timestampEpochMs })
            .toDouble() / TimeConstants.MILLIS_PER_HOUR).coerceAtLeast(0.25)
        val perHour = changes / hours
        if (perHour < 2.0) return null
        val score = (58 + perHour * 8).toInt().coerceIn(58, 86)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.NETWORK,
                explanation =
                    "Network type changed $changes times (~${"%.1f".format(perHour)}/h). " +
                        "Reselection keeps the radio active and increases drain — especially on cell edges.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = listOf(
                    Evidence(
                        id = "transitions",
                        description = "Network type transitions",
                        metricKey = "network_transitions_per_hour",
                        observedValue = "${"%.1f".format(perHour)}/h ($changes total)",
                        threshold = "≥2/h",
                        confidenceLevel = ConfidenceLevel.DERIVED,
                    ),
                ),
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Prefer a stable RAT (LTE) if 5G/LTE flapping",
                    "Use Wi-Fi calling / Wi-Fi data indoors",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class ThermalThrottlingRule : ForensicRule {
    override val id: String = "thermal_throttling"
    override val title: String = "Thermal throttling"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val peak = context.samples.mapNotNull { it.thermalStatus }.maxOrNull()
        val hot = context.samples.mapNotNull { it.temperatureC }.maxOrNull()
        val statusThrottle = peak != null && peak >= 2
        val tempThrottle = hot != null && hot >= TimeConstants.ELEVATED_TEMP_C + 5f
        if (!statusThrottle && !tempThrottle) return null
        val level = if (statusThrottle) ConfidenceLevel.MEASURED else ConfidenceLevel.INFERRED
        val score = if (statusThrottle) 88 else 70
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.THERMAL,
                explanation =
                    "Thermal status peaked at ${peak ?: "n/a"}" +
                        (hot?.let { " with battery temp ${"%.1f".format(it)}°C" } ?: "") +
                        ". Throttling wastes energy as heat and can prolong high-power workloads.",
                confidence = Confidence(score, level),
                evidence = buildList {
                    peak?.let {
                        add(
                            Evidence(
                                id = "thermal_status",
                                description = "Peak PowerManager thermal status",
                                metricKey = "thermal_status",
                                observedValue = it.toString(),
                                threshold = "≥2 (MODERATE)",
                                confidenceLevel = ConfidenceLevel.MEASURED,
                            ),
                        )
                    }
                    if (tempThrottle) {
                        add(
                            Evidence(
                                id = "hot_temp",
                                description = "Very high battery temperature",
                                metricKey = "temperature_c",
                                observedValue = "${"%.1f".format(hot!!)}°C",
                                confidenceLevel = ConfidenceLevel.MEASURED,
                            ),
                        )
                    }
                },
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Cool the device before heavy workloads",
                    "Investigate concurrent charging + gaming",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

/** Convenience for tests and manual construction. */
object DefaultRules {
    fun all(): Set<ForensicRule> = setOf(
        WeakCellularSignalRule(),
        ExcessiveScreenBrightnessRule(),
        ElevatedTemperatureRule(),
        ChargingHeatRule(),
        OvernightStandbyDrainRule(),
        ModemInducedHeatingRule(),
        WeakWifiDrainRule(),
        BatteryAgingRule(),
        HighDischargeCurrentRule(),
        FrequentNetworkTransitionsRule(),
        ThermalThrottlingRule(),
    )
}
