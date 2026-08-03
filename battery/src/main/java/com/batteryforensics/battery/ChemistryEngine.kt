package com.batteryforensics.battery

import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.core.time.TimeConstants
import kotlin.math.abs

/**
 * Battery chemistry forensics from measured voltage/current/charge-counter history.
 * Does not rely solely on Android HEALTH_* constants.
 */
object ChemistryEngine {

    data class InternalResistanceSample(
        val timestampEpochMs: Long,
        val resistanceMilliohms: Double,
        val deltaVoltageMv: Double,
        val deltaCurrentMa: Double,
    )

    data class VoltageSagReading(
        val openCircuitMv: Int?,
        val underLoadMv: Int?,
        val sagMv: Int?,
        val confidence: ConfidenceLevel,
    )

    data class WearTrend(
        /** Approximate full-charge capacity from charge-counter peaks, mAh. */
        val estimatedCapacityMah: Double?,
        /** Slope of capacity over time (mAh per day). Negative = wear. */
        val capacitySlopeMahPerDay: Double?,
        val cycleEstimate: Double?,
        val confidence: ConfidenceLevel,
    )

    data class ChargingEfficiency(
        val energyInMah: Double?,
        val percentGained: Int?,
        /** Estimated mAh gained from percent × capacity estimate. */
        val estimatedMahGained: Double?,
        /** energyIn / estimatedMahGained when both known; >1 means overhead/heat loss. */
        val efficiencyRatio: Double?,
        val confidence: ConfidenceLevel,
    )

    data class ChemistryReport(
        val voltageSag: VoltageSagReading,
        val medianInternalResistanceMilliohms: Double?,
        val resistanceSamples: List<InternalResistanceSample>,
        val wear: WearTrend,
        val chargingEfficiency: ChargingEfficiency,
        val chargeCounterSpanMah: Int?,
        val notes: List<String>,
    )

    fun analyze(samples: List<MonitoringSample>, ratedCapacityMah: Int? = null): ChemistryReport {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val ri = computeInternalResistance(ordered)
        val sag = computeVoltageSag(ordered)
        val wear = estimateWear(ordered, ratedCapacityMah)
        val efficiency = estimateChargingEfficiency(ordered, wear.estimatedCapacityMah ?: ratedCapacityMah?.toDouble())
        val counters = ordered.mapNotNull { it.chargeCounterMah }
        val span = if (counters.size >= 2) counters.max() - counters.min() else null

        val notes = buildList {
            if (ordered.size < 8) add("Short history — Ri and wear need more samples for stronger confidence")
            if (ri.isEmpty()) add("Insufficient ΔV/ΔI pairs for internal resistance (need concurrent voltage + current changes)")
            if (counters.isEmpty()) add("Charge counter unavailable on this device/session")
            add("Android HEALTH_* is not used as sole evidence; chemistry is derived from voltage/current/counter history")
        }

        return ChemistryReport(
            voltageSag = sag,
            medianInternalResistanceMilliohms = ri.map { it.resistanceMilliohms }.medianOrNull(),
            resistanceSamples = ri,
            wear = wear,
            chargingEfficiency = efficiency,
            chargeCounterSpanMah = span,
            notes = notes,
        )
    }

    /**
     * Dynamic Ri ≈ ΔV / ΔI from consecutive samples where current changes meaningfully.
     * Current is in µA (discharge often negative); voltage in mV.
     * Result in milliohms.
     */
    fun computeInternalResistance(samples: List<MonitoringSample>): List<InternalResistanceSample> {
        if (samples.size < 2) return emptyList()
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val results = mutableListOf<InternalResistanceSample>()
        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val curr = ordered[i]
            val v0 = prev.voltageMv ?: continue
            val v1 = curr.voltageMv ?: continue
            val i0Ua = prev.currentMicroamps ?: continue
            val i1Ua = curr.currentMicroamps ?: continue
            val deltaVMv = (v1 - v0).toDouble()
            val deltaIMa = (i1Ua - i0Ua) / 1_000.0
            if (abs(deltaIMa) < MIN_DELTA_CURRENT_MA) continue
            // Ri = ΔV(V) / ΔI(A) → mΩ: (ΔV_mV / 1000) / (ΔI_mA / 1000) * 1000 = ΔV_mV / ΔI_mA * 1000
            val riMilliohms = abs(deltaVMv / deltaIMa) * 1_000.0
            if (riMilliohms !in MIN_RI_MOHMS..MAX_RI_MOHMS) continue
            results += InternalResistanceSample(
                timestampEpochMs = curr.timestampEpochMs,
                resistanceMilliohms = riMilliohms,
                deltaVoltageMv = deltaVMv,
                deltaCurrentMa = deltaIMa,
            )
        }
        return results
    }

    fun computeVoltageSag(samples: List<MonitoringSample>): VoltageSagReading {
        val rest = samples.filter {
            val ua = it.currentMicroamps ?: return@filter false
            abs(ua) < REST_CURRENT_UA && it.voltageMv != null
        }
        val load = samples.filter {
            val ua = it.currentMicroamps ?: return@filter false
            ua < -LOAD_CURRENT_UA && it.voltageMv != null
        }
        val openCircuit = rest.mapNotNull { it.voltageMv }.medianIntOrNull()
        val underLoad = load.mapNotNull { it.voltageMv }.medianIntOrNull()
        val sag = if (openCircuit != null && underLoad != null) openCircuit - underLoad else null
        val level = when {
            sag != null && rest.size >= 2 && load.size >= 2 -> ConfidenceLevel.DERIVED
            sag != null -> ConfidenceLevel.INFERRED
            else -> ConfidenceLevel.SPECULATIVE
        }
        return VoltageSagReading(
            openCircuitMv = openCircuit,
            underLoadMv = underLoad,
            sagMv = sag?.coerceAtLeast(0),
            confidence = level,
        )
    }

    fun estimateWear(samples: List<MonitoringSample>, ratedCapacityMah: Int?): WearTrend {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val capacityPoints = estimateCapacityPoints(ordered)
        val estimated = capacityPoints.lastOrNull()?.second
            ?: ratedCapacityMah?.toDouble()
        val slope = if (capacityPoints.size >= 2) {
            val (t0, c0) = capacityPoints.first()
            val (t1, c1) = capacityPoints.last()
            val days = (t1 - t0).toDouble() / TimeConstants.MILLIS_PER_HOUR / 24.0
            if (days > 0.05) (c1 - c0) / days else null
        } else {
            null
        }
        val cycles = estimateCycles(ordered)
        val level = when {
            capacityPoints.size >= 3 -> ConfidenceLevel.DERIVED
            capacityPoints.isNotEmpty() || cycles != null -> ConfidenceLevel.INFERRED
            else -> ConfidenceLevel.SPECULATIVE
        }
        return WearTrend(
            estimatedCapacityMah = estimated,
            capacitySlopeMahPerDay = slope,
            cycleEstimate = cycles,
            confidence = level,
        )
    }

    fun estimateChargingEfficiency(
        samples: List<MonitoringSample>,
        capacityMah: Double?,
    ): ChargingEfficiency {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val charging = ordered.filter { it.isCharging == true }
        if (charging.size < 2) {
            return ChargingEfficiency(null, null, null, null, ConfidenceLevel.SPECULATIVE)
        }
        val start = charging.first()
        val end = charging.last()
        val pctStart = start.batteryPercent
        val pctEnd = end.batteryPercent
        val percentGained = if (pctStart != null && pctEnd != null) (pctEnd - pctStart).coerceAtLeast(0) else null

        val counterStart = start.chargeCounterMah
        val counterEnd = end.chargeCounterMah
        val energyIn = if (counterStart != null && counterEnd != null) {
            (counterEnd - counterStart).toDouble().coerceAtLeast(0.0)
        } else {
            // Integrate current while charging (µA → mAh)
            integrateChargeMah(charging)
        }

        val estimatedGained = if (percentGained != null && capacityMah != null && capacityMah > 0) {
            percentGained / 100.0 * capacityMah
        } else {
            null
        }

        val ratio = if (energyIn != null && estimatedGained != null && estimatedGained > 0) {
            energyIn / estimatedGained
        } else {
            null
        }

        val level = when {
            energyIn != null && estimatedGained != null -> ConfidenceLevel.DERIVED
            energyIn != null || percentGained != null -> ConfidenceLevel.INFERRED
            else -> ConfidenceLevel.SPECULATIVE
        }
        return ChargingEfficiency(
            energyInMah = energyIn,
            percentGained = percentGained,
            estimatedMahGained = estimatedGained,
            efficiencyRatio = ratio,
            confidence = level,
        )
    }

    /** Half-cycles from charge-counter direction reversals / large swings. */
    fun estimateCycles(samples: List<MonitoringSample>): Double? {
        val counters = samples.sortedBy { it.timestampEpochMs }.mapNotNull { it.chargeCounterMah }
        if (counters.size < 4) return null
        var direction = 0
        var reversals = 0
        var last = counters.first()
        for (c in counters.drop(1)) {
            val d = when {
                c > last + 20 -> 1
                c < last - 20 -> -1
                else -> 0
            }
            if (d != 0 && direction != 0 && d != direction) reversals++
            if (d != 0) direction = d
            last = c
        }
        // Full cycle ≈ charge + discharge = 2 reversals
        return reversals / 2.0
    }

    private fun estimateCapacityPoints(samples: List<MonitoringSample>): List<Pair<Long, Double>> {
        // Approximate usable capacity from charge-counter peaks during near-full charge windows
        val points = mutableListOf<Pair<Long, Double>>()
        val ordered = samples.sortedBy { it.timestampEpochMs }
        var i = 0
        while (i < ordered.size) {
            val s = ordered[i]
            val pct = s.batteryPercent
            val counter = s.chargeCounterMah
            if (pct != null && pct >= 95 && counter != null && counter > 500) {
                // Extrapolate to 100%: counter / (pct/100)
                val full = counter / (pct / 100.0)
                if (full in 800.0..8_000.0) {
                    points += s.timestampEpochMs to full
                }
            }
            i++
        }
        return points
    }

    private fun integrateChargeMah(chargingSamples: List<MonitoringSample>): Double? {
        if (chargingSamples.size < 2) return null
        var mah = 0.0
        var used = false
        for (i in 1 until chargingSamples.size) {
            val a = chargingSamples[i - 1]
            val b = chargingSamples[i]
            val ua = listOfNotNull(a.currentMicroamps, b.currentMicroamps).average().takeIf { !it.isNaN() }
                ?: continue
            val hours = (b.timestampEpochMs - a.timestampEpochMs).toDouble() / TimeConstants.MILLIS_PER_HOUR
            if (hours <= 0) continue
            // Charging current may be reported positive or negative depending on OEM
            mah += abs(ua) / 1_000.0 * hours
            used = true
        }
        return if (used) mah else null
    }

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun List<Int>.medianIntOrNull(): Int? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    const val MIN_DELTA_CURRENT_MA = 50.0
    const val MIN_RI_MOHMS = 10.0
    const val MAX_RI_MOHMS = 500.0
    const val REST_CURRENT_UA = 80_000
    const val LOAD_CURRENT_UA = 400_000
}
