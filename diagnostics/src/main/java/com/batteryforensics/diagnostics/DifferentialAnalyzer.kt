package com.batteryforensics.diagnostics

import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.core.time.TimeConstants
import com.batteryforensics.statistics.DrainStats
import kotlin.math.abs

/**
 * Compares a healthy baseline night/window against a problem window.
 * Highlights largest deviations that help explain drain differences.
 */
object DifferentialAnalyzer {

    data class MetricDelta(
        val key: String,
        val label: String,
        val healthyValue: String,
        val problemValue: String,
        val deltaDisplay: String,
        val magnitude: Double,
        val confidence: ConfidenceLevel,
    )

    data class DifferentialReport(
        val healthySampleCount: Int,
        val problemSampleCount: Int,
        val deltas: List<MetricDelta>,
        val summary: String,
        val confidence: ConfidenceLevel,
    )

    fun compare(
        healthy: List<MonitoringSample>,
        problem: List<MonitoringSample>,
    ): DifferentialReport {
        val h = summarize(healthy)
        val p = summarize(problem)
        val deltas = buildList {
            addDelta("battery_loss_percent", "Battery loss", h.batteryLossPercent, p.batteryLossPercent, "%") { a, b -> abs(b - a) }
            addDelta("drain_percent_per_hour", "Drain rate", h.drainPerHour, p.drainPerHour, "%/h") { a, b -> abs(b - a) }
            addDelta("screen_on_ratio", "Screen-on ratio", h.screenOnRatio, p.screenOnRatio, "") { a, b -> abs(b - a) }
            addDelta("avg_temp_c", "Avg temperature", h.avgTempC, p.avgTempC, "°C") { a, b -> abs(b - a) }
            addDelta("max_temp_c", "Max temperature", h.maxTempC, p.maxTempC, "°C") { a, b -> abs(b - a) }
            addDelta("avg_cellular_rssi", "Avg cellular RSSI", h.avgCellularRssi, p.avgCellularRssi, "dBm") { a, b -> abs(b - a) }
            addDelta("wifi_connected_ratio", "Wi-Fi connected ratio", h.wifiConnectedRatio, p.wifiConnectedRatio, "") { a, b -> abs(b - a) }
            addDelta("network_transitions", "Network type changes", h.networkTransitions.toDouble(), p.networkTransitions.toDouble(), "") { a, b -> abs(b - a) }
            addDelta("radio_active_estimate_min", "Radio-active estimate", h.radioActiveMinutes, p.radioActiveMinutes, "min") { a, b -> abs(b - a) }
            addDelta("charging_ratio", "Charging ratio", h.chargingRatio, p.chargingRatio, "") { a, b -> abs(b - a) }
            addDelta("deep_idle_proxy", "Deep-idle proxy (screen-off quiet)", h.deepIdleProxy, p.deepIdleProxy, "") { a, b -> abs(b - a) }
        }.sortedByDescending { it.magnitude }

        val top = deltas.take(3).joinToString("; ") { "${it.label}: ${it.deltaDisplay}" }
        val level = when {
            healthy.size >= 8 && problem.size >= 8 -> ConfidenceLevel.DERIVED
            healthy.size >= 3 && problem.size >= 3 -> ConfidenceLevel.INFERRED
            else -> ConfidenceLevel.SPECULATIVE
        }
        return DifferentialReport(
            healthySampleCount = healthy.size,
            problemSampleCount = problem.size,
            deltas = deltas,
            summary = if (deltas.isEmpty()) {
                "Insufficient overlapping metrics to compare windows."
            } else {
                "Largest deviations — $top"
            },
            confidence = level,
        )
    }

    private data class WindowSummary(
        val batteryLossPercent: Double?,
        val drainPerHour: Double?,
        val screenOnRatio: Double?,
        val avgTempC: Double?,
        val maxTempC: Double?,
        val avgCellularRssi: Double?,
        val wifiConnectedRatio: Double?,
        val networkTransitions: Int,
        val radioActiveMinutes: Double?,
        val chargingRatio: Double?,
        val deepIdleProxy: Double?,
    )

    private fun summarize(samples: List<MonitoringSample>): WindowSummary {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val startPct = ordered.firstOrNull()?.batteryPercent
        val endPct = ordered.lastOrNull()?.batteryPercent
        val loss = if (startPct != null && endPct != null) (startPct - endPct).toDouble() else null
        val duration = if (ordered.size >= 2) {
            ordered.last().timestampEpochMs - ordered.first().timestampEpochMs
        } else {
            0L
        }
        val drain = if (startPct != null && endPct != null && duration > 0) {
            DrainStats.percentPerHour(startPct, endPct, duration)
        } else {
            null
        }
        val screenRatio = ratio(ordered) { it.screenOn == true }
        val chargingRatio = ratio(ordered) { it.isCharging == true }
        val wifiRatio = ratio(ordered) { it.wifiConnected == true }
        val avgTemp = ordered.mapNotNull { it.temperatureC?.toDouble() }.averageOrNull()
        val maxTemp = ordered.mapNotNull { it.temperatureC?.toDouble() }.maxOrNull()
        val avgRssi = ordered.mapNotNull { it.cellularRssiDbm?.toDouble() }.averageOrNull()
        val transitions = countNetworkTransitions(ordered)
        val radioMin = estimateRadioActiveMinutes(ordered)
        // Proxy for deep sleep: fraction of screen-off samples with low |current|
        val quiet = ordered.filter { it.screenOn != true }
        val deepProxy = if (quiet.isNotEmpty()) {
            quiet.count {
                val ua = it.currentMicroamps ?: return@count false
                abs(ua) < 100_000
            }.toDouble() / quiet.size
        } else {
            null
        }
        return WindowSummary(
            batteryLossPercent = loss,
            drainPerHour = drain,
            screenOnRatio = screenRatio,
            avgTempC = avgTemp,
            maxTempC = maxTemp,
            avgCellularRssi = avgRssi,
            wifiConnectedRatio = wifiRatio,
            networkTransitions = transitions,
            radioActiveMinutes = radioMin,
            chargingRatio = chargingRatio,
            deepIdleProxy = deepProxy,
        )
    }

    private fun MutableList<MetricDelta>.addDelta(
        key: String,
        label: String,
        healthy: Double?,
        problem: Double?,
        unit: String,
        magnitude: (Double, Double) -> Double,
    ) {
        if (healthy == null || problem == null) return
        val mag = magnitude(healthy, problem)
        val delta = problem - healthy
        val deltaDisplay = when {
            unit.isEmpty() -> "${"%+.2f".format(delta)}"
            else -> "${"%+.2f".format(delta)} $unit"
        }
        add(
            MetricDelta(
                key = key,
                label = label,
                healthyValue = format(healthy, unit),
                problemValue = format(problem, unit),
                deltaDisplay = deltaDisplay,
                magnitude = mag,
                confidence = ConfidenceLevel.DERIVED,
            ),
        )
    }

    private fun format(v: Double, unit: String): String =
        if (unit.isEmpty()) "%.2f".format(v) else "${"%.2f".format(v)} $unit"

    private fun ratio(samples: List<MonitoringSample>, pred: (MonitoringSample) -> Boolean): Double? {
        if (samples.isEmpty()) return null
        return samples.count(pred).toDouble() / samples.size
    }

    private fun countNetworkTransitions(samples: List<MonitoringSample>): Int {
        val types = samples.map { it.networkType }
        var changes = 0
        for (i in 1 until types.size) {
            if (types[i] != null && types[i - 1] != null && types[i] != types[i - 1]) changes++
        }
        return changes
    }

    /**
     * Inferred radio-active duration: consecutive samples with weak signal or cellular
     * network type changes / non-wifi preference. Labeled inferred — not RRC.
     */
    private fun estimateRadioActiveMinutes(samples: List<MonitoringSample>): Double? {
        if (samples.size < 2) return null
        val ordered = samples.sortedBy { it.timestampEpochMs }
        var activeMs = 0L
        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val curr = ordered[i]
            val prevRssi = prev.cellularRssiDbm
            val currRssi = curr.cellularRssiDbm
            val weak = (prevRssi != null && prevRssi <= TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD) ||
                (currRssi != null && currRssi <= TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD)
            val onCell = prev.wifiConnected != true && prev.networkType != null && prev.networkType != "unknown"
            if (weak || onCell) {
                activeMs += (curr.timestampEpochMs - prev.timestampEpochMs).coerceAtLeast(0)
            }
        }
        return activeMs.toDouble() / TimeConstants.MILLIS_PER_MINUTE
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
