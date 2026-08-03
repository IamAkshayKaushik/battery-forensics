package com.batteryforensics.analytics

import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.core.time.TimeConstants

/**
 * Network & radio forensics from monitoring samples.
 * Never claims measured RRC unless the platform exposes it — radio-active time is Inferred.
 */
object NetworkForensics {

    data class NetworkReport(
        val cellChangeCount: Int,
        val networkTransitionCount: Int,
        val wifiConnectedRatio: Double?,
        val avgWifiRssiDbm: Double?,
        val avgCellularRssiDbm: Double?,
        val weakCellularRatio: Double?,
        /** Inferred estimate — not RRC state machine. */
        val radioActiveMinutesEstimate: Double?,
        val dominantNetworkType: String?,
        val confidence: ConfidenceLevel,
        val notes: List<String>,
    )

    fun analyze(samples: List<MonitoringSample>): NetworkReport {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val transitions = countTransitions(ordered) { it.networkType }
        // Cell changes approximated by network type flips + large RSSI jumps (no Cell ID without privileged APIs)
        val rssiJumps = countRssiJumps(ordered)
        val cellChanges = transitions + rssiJumps
        val wifiRatio = ratio(ordered) { it.wifiConnected == true }
        val avgWifi = ordered.mapNotNull { it.wifiRssiDbm?.toDouble() }.averageOrNull()
        val cellular = ordered.mapNotNull { it.cellularRssiDbm }
        val avgCell = cellular.map { it.toDouble() }.averageOrNull()
        val weakRatio = if (cellular.isNotEmpty()) {
            cellular.count { it <= TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD }.toDouble() / cellular.size
        } else {
            null
        }
        val radioMin = estimateRadioActiveMinutes(ordered)
        val dominant = ordered.mapNotNull { it.networkType }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val level = when {
            cellular.isNotEmpty() && ordered.size >= 6 -> ConfidenceLevel.MEASURED
            ordered.any { it.wifiRssiDbm != null || it.networkType != null } -> ConfidenceLevel.DERIVED
            else -> ConfidenceLevel.SPECULATIVE
        }

        val notes = buildList {
            add("Radio-active duration is Inferred from weak signal / non-Wi-Fi windows — not measured RRC")
            if (cellular.isEmpty()) {
                add("Cellular RSSI missing — grant location/phone permission or use Shizuku SignalStrength path")
            }
            add("Cell changes approximated without Cell ID (API limits on unprivileged apps)")
        }

        return NetworkReport(
            cellChangeCount = cellChanges,
            networkTransitionCount = transitions,
            wifiConnectedRatio = wifiRatio,
            avgWifiRssiDbm = avgWifi,
            avgCellularRssiDbm = avgCell,
            weakCellularRatio = weakRatio,
            radioActiveMinutesEstimate = radioMin,
            dominantNetworkType = dominant,
            confidence = level,
            notes = notes,
        )
    }

    private fun estimateRadioActiveMinutes(samples: List<MonitoringSample>): Double? {
        if (samples.size < 2) return null
        var activeMs = 0L
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]
            val weak = (prev.cellularRssiDbm ?: 0) <= TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD ||
                (curr.cellularRssiDbm ?: 0) <= TimeConstants.WEAK_SIGNAL_DBM_THRESHOLD
            val onCell = prev.wifiConnected != true && !prev.networkType.isNullOrBlank()
            if (weak || onCell) {
                activeMs += (curr.timestampEpochMs - prev.timestampEpochMs).coerceAtLeast(0)
            }
        }
        return activeMs.toDouble() / TimeConstants.MILLIS_PER_MINUTE
    }

    private fun countTransitions(samples: List<MonitoringSample>, selector: (MonitoringSample) -> String?): Int {
        var n = 0
        for (i in 1 until samples.size) {
            val a = selector(samples[i - 1])
            val b = selector(samples[i])
            if (a != null && b != null && a != b) n++
        }
        return n
    }

    private fun countRssiJumps(samples: List<MonitoringSample>): Int {
        var n = 0
        for (i in 1 until samples.size) {
            val a = samples[i - 1].cellularRssiDbm ?: continue
            val b = samples[i].cellularRssiDbm ?: continue
            if (kotlin.math.abs(a - b) >= 12) n++
        }
        return n
    }

    private fun ratio(samples: List<MonitoringSample>, pred: (MonitoringSample) -> Boolean): Double? {
        if (samples.isEmpty()) return null
        return samples.count(pred).toDouble() / samples.size
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
