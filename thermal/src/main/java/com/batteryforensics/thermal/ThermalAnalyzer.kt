package com.batteryforensics.thermal

import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.core.time.TimeConstants

/** Thermal forensics from battery temperature + PowerManager thermal status samples. */
object ThermalAnalyzer {

    data class ThermalEvent(
        val timestampEpochMs: Long,
        val type: String,
        val detail: String,
        val temperatureC: Float?,
        val confidence: ConfidenceLevel,
    )

    data class ThermalReport(
        val heatingRateCPerMinute: Double?,
        val coolingRateCPerMinute: Double?,
        val maxTempC: Float?,
        val minTempC: Float?,
        val maxDailyTempC: Float?,
        val maxChargingTempC: Float?,
        val throttlingDetected: Boolean,
        val peakThermalStatus: Int?,
        val events: List<ThermalEvent>,
        val confidence: ConfidenceLevel,
        val notes: List<String>,
    )

    fun analyze(samples: List<MonitoringSample>, windowStartMs: Long? = null, windowEndMs: Long? = null): ThermalReport {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val temps = ordered.mapNotNull { s -> s.temperatureC?.let { s.timestampEpochMs to it } }
        val maxTemp = temps.maxOfOrNull { it.second }
        val minTemp = temps.minOfOrNull { it.second }
        val heating = maxRisingRate(temps)
        val cooling = maxFallingRate(temps)
        val chargingTemps = ordered.filter { it.isCharging == true }.mapNotNull { it.temperatureC }
        val maxCharging = chargingTemps.maxOrNull()

        val dayStart = windowStartMs ?: ordered.firstOrNull()?.timestampEpochMs
        val dayEnd = windowEndMs ?: ordered.lastOrNull()?.timestampEpochMs
        val maxDaily = if (dayStart != null && dayEnd != null) {
            ordered.filter { it.timestampEpochMs in dayStart..dayEnd }
                .mapNotNull { it.temperatureC }
                .maxOrNull()
        } else {
            maxTemp
        }

        val peakStatus = ordered.mapNotNull { it.thermalStatus }.maxOrNull()
        // PowerManager.THERMAL_STATUS_MODERATE = 2, SEVERE = 3, CRITICAL = 4, EMERGENCY = 5, SHUTDOWN = 6
        val throttling = (peakStatus != null && peakStatus >= 2) ||
            (maxTemp != null && maxTemp >= TimeConstants.ELEVATED_TEMP_C + 5f)

        val events = buildEvents(ordered, heating, cooling, throttling, peakStatus)

        val level = when {
            temps.size >= 6 -> ConfidenceLevel.MEASURED
            temps.size >= 2 -> ConfidenceLevel.DERIVED
            else -> ConfidenceLevel.SPECULATIVE
        }

        val notes = buildList {
            if (temps.size < 2) add("Need at least two temperature samples for rates")
            if (ordered.none { it.thermalStatus != null }) {
                add("PowerManager thermal status unavailable below API 29 or not reported")
            }
            add("Heating/cooling rates are Derived from ΔT/Δt between samples")
        }

        return ThermalReport(
            heatingRateCPerMinute = heating,
            coolingRateCPerMinute = cooling,
            maxTempC = maxTemp,
            minTempC = minTemp,
            maxDailyTempC = maxDaily,
            maxChargingTempC = maxCharging,
            throttlingDetected = throttling,
            peakThermalStatus = peakStatus,
            events = events,
            confidence = level,
            notes = notes,
        )
    }

    /** Max positive °C / minute over consecutive samples. */
    fun maxRisingRate(temps: List<Pair<Long, Float>>): Double? {
        if (temps.size < 2) return null
        var maxRate = 0.0
        var found = false
        for (i in 1 until temps.size) {
            val (t0, c0) = temps[i - 1]
            val (t1, c1) = temps[i]
            val minutes = (t1 - t0).toDouble() / TimeConstants.MILLIS_PER_MINUTE
            if (minutes <= 0) continue
            val rate = (c1 - c0) / minutes
            if (rate > maxRate) {
                maxRate = rate
                found = true
            }
        }
        return if (found && maxRate > 0) maxRate else null
    }

    fun maxFallingRate(temps: List<Pair<Long, Float>>): Double? {
        if (temps.size < 2) return null
        var maxCool = 0.0
        var found = false
        for (i in 1 until temps.size) {
            val (t0, c0) = temps[i - 1]
            val (t1, c1) = temps[i]
            val minutes = (t1 - t0).toDouble() / TimeConstants.MILLIS_PER_MINUTE
            if (minutes <= 0) continue
            val rate = (c0 - c1) / minutes
            if (rate > maxCool) {
                maxCool = rate
                found = true
            }
        }
        return if (found && maxCool > 0) maxCool else null
    }

    private fun buildEvents(
        ordered: List<MonitoringSample>,
        heating: Double?,
        cooling: Double?,
        throttling: Boolean,
        peakStatus: Int?,
    ): List<ThermalEvent> {
        val events = mutableListOf<ThermalEvent>()
        if (heating != null && heating >= RAPID_HEAT_C_PER_MIN) {
            val peak = ordered.maxByOrNull { it.temperatureC ?: Float.MIN_VALUE }
            events += ThermalEvent(
                timestampEpochMs = peak?.timestampEpochMs ?: ordered.last().timestampEpochMs,
                type = "RAPID_HEATING",
                detail = "Heating rate ${"%.2f".format(heating)}°C/min",
                temperatureC = peak?.temperatureC,
                confidence = ConfidenceLevel.DERIVED,
            )
        }
        if (cooling != null && cooling >= RAPID_COOL_C_PER_MIN) {
            val trough = ordered.minByOrNull { it.temperatureC ?: Float.MAX_VALUE }
            events += ThermalEvent(
                timestampEpochMs = trough?.timestampEpochMs ?: ordered.last().timestampEpochMs,
                type = "RAPID_COOLING",
                detail = "Cooling rate ${"%.2f".format(cooling)}°C/min",
                temperatureC = trough?.temperatureC,
                confidence = ConfidenceLevel.DERIVED,
            )
        }
        ordered.filter { (it.temperatureC ?: 0f) >= TimeConstants.CHARGING_HEAT_TEMP_C && it.isCharging == true }
            .maxByOrNull { it.temperatureC ?: 0f }
            ?.let { hot ->
                events += ThermalEvent(
                    timestampEpochMs = hot.timestampEpochMs,
                    type = "CHARGING_HEAT",
                    detail = "Charging at ${"%.1f".format(hot.temperatureC)}°C",
                    temperatureC = hot.temperatureC,
                    confidence = ConfidenceLevel.MEASURED,
                )
            }
        if (throttling) {
            val s = ordered.maxByOrNull { it.thermalStatus ?: -1 } ?: ordered.lastOrNull()
            if (s != null) {
                events += ThermalEvent(
                    timestampEpochMs = s.timestampEpochMs,
                    type = "THROTTLING",
                    detail = "Thermal status=${peakStatus ?: s.thermalStatus}; throttling inferred or measured",
                    temperatureC = s.temperatureC,
                    confidence = if (peakStatus != null && peakStatus >= 2) {
                        ConfidenceLevel.MEASURED
                    } else {
                        ConfidenceLevel.INFERRED
                    },
                )
            }
        }
        return events.sortedBy { it.timestampEpochMs }
    }

    const val RAPID_HEAT_C_PER_MIN = 0.4
    const val RAPID_COOL_C_PER_MIN = 0.3
}
