package com.batteryforensics.statistics

import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.core.time.TimeConstants
import kotlin.math.abs
import kotlin.math.sqrt

/** Derived drain / efficiency / anomaly helpers. */
object DrainStats {
    fun percentPerHour(startPercent: Int, endPercent: Int, durationMs: Long): Double {
        if (durationMs <= 0L) return 0.0
        val hours = durationMs / 3_600_000.0
        if (hours <= 0.0) return 0.0
        return (startPercent - endPercent) / hours
    }
}

object StatisticsEngine {

    data class MovingAveragePoint(
        val timestampEpochMs: Long,
        val value: Double,
    )

    data class AnomalyHook(
        val timestampEpochMs: Long,
        val metric: String,
        val observed: Double,
        val baseline: Double,
        val zScore: Double,
        val confidence: ConfidenceLevel,
    )

    data class DrainBreakdown(
        val standbyDrainPercentPerHour: Double?,
        val screenOnDrainPercentPerHour: Double?,
        val overallDrainPercentPerHour: Double?,
        val screenOnRatio: Double?,
        val dischargeCurve: List<Pair<Long, Int>>,
        val batteryMovingAverage: List<MovingAveragePoint>,
        val anomalies: List<AnomalyHook>,
        val confidence: ConfidenceLevel,
        val notes: List<String>,
    )

    data class BaselineComparison(
        val metric: String,
        val baselineValue: Double,
        val currentValue: Double,
        val delta: Double,
        val deltaPercent: Double?,
    )

    fun analyze(samples: List<MonitoringSample>, windowSize: Int = 5): DrainBreakdown {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        val overall = overallDrain(ordered)
        val standby = segmentDrain(ordered.filter { it.screenOn != true && it.isCharging != true })
        val screen = segmentDrain(ordered.filter { it.screenOn == true && it.isCharging != true })
        val screenRatio = if (ordered.isNotEmpty()) {
            ordered.count { it.screenOn == true }.toDouble() / ordered.size
        } else {
            null
        }
        val curve = ordered.mapNotNull { s ->
            s.batteryPercent?.let { s.timestampEpochMs to it }
        }
        val ma = movingAverage(
            curve.map { (t, v) -> t to v.toDouble() },
            windowSize,
        )
        val anomalies = detectAnomalies(curve.map { (t, v) -> t to v.toDouble() }, metric = "battery_percent")

        val level = when {
            ordered.size >= 12 -> ConfidenceLevel.DERIVED
            ordered.size >= 4 -> ConfidenceLevel.INFERRED
            else -> ConfidenceLevel.SPECULATIVE
        }
        val notes = buildList {
            if (ordered.size < 4) add("Need more samples for stable drain rates")
            add("Standby vs screen drain are Derived from screen-on flags and percent deltas")
            add("Anomaly hooks use simple z-score against the window mean — not ML")
        }
        return DrainBreakdown(
            standbyDrainPercentPerHour = standby,
            screenOnDrainPercentPerHour = screen,
            overallDrainPercentPerHour = overall,
            screenOnRatio = screenRatio,
            dischargeCurve = curve,
            batteryMovingAverage = ma,
            anomalies = anomalies,
            confidence = level,
            notes = notes,
        )
    }

    fun compareBaselines(
        baseline: List<MonitoringSample>,
        current: List<MonitoringSample>,
    ): List<BaselineComparison> {
        val b = analyze(baseline)
        val c = analyze(current)
        return buildList {
            fun addMetric(name: String, bv: Double?, cv: Double?) {
                if (bv == null || cv == null) return
                val delta = cv - bv
                val pct = if (abs(bv) > 1e-6) delta / abs(bv) * 100.0 else null
                add(BaselineComparison(name, bv, cv, delta, pct))
            }
            addMetric("standby_drain_percent_per_hour", b.standbyDrainPercentPerHour, c.standbyDrainPercentPerHour)
            addMetric("screen_drain_percent_per_hour", b.screenOnDrainPercentPerHour, c.screenOnDrainPercentPerHour)
            addMetric("overall_drain_percent_per_hour", b.overallDrainPercentPerHour, c.overallDrainPercentPerHour)
            addMetric("screen_on_ratio", b.screenOnRatio, c.screenOnRatio)
            val bTemp = baseline.mapNotNull { it.temperatureC?.toDouble() }.averageOrNull()
            val cTemp = current.mapNotNull { it.temperatureC?.toDouble() }.averageOrNull()
            addMetric("avg_temperature_c", bTemp, cTemp)
            val bSignal = baseline.mapNotNull { it.cellularRssiDbm?.toDouble() }.averageOrNull()
            val cSignal = current.mapNotNull { it.cellularRssiDbm?.toDouble() }.averageOrNull()
            addMetric("avg_cellular_rssi_dbm", bSignal, cSignal)
        }.sortedByDescending { abs(it.delta) }
    }

    fun movingAverage(points: List<Pair<Long, Double>>, window: Int): List<MovingAveragePoint> {
        if (points.isEmpty() || window <= 0) return emptyList()
        val out = mutableListOf<MovingAveragePoint>()
        for (i in points.indices) {
            val from = (i - window + 1).coerceAtLeast(0)
            val slice = points.subList(from, i + 1).map { it.second }
            out += MovingAveragePoint(points[i].first, slice.average())
        }
        return out
    }

    fun detectAnomalies(
        points: List<Pair<Long, Double>>,
        metric: String,
        zThreshold: Double = 2.5,
    ): List<AnomalyHook> {
        if (points.size < 6) return emptyList()
        val values = points.map { it.second }
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance)
        if (std < 1e-6) return emptyList()
        return points.mapNotNull { (t, v) ->
            val z = abs(v - mean) / std
            if (z >= zThreshold) {
                AnomalyHook(
                    timestampEpochMs = t,
                    metric = metric,
                    observed = v,
                    baseline = mean,
                    zScore = z,
                    confidence = ConfidenceLevel.DERIVED,
                )
            } else {
                null
            }
        }
    }

    private fun overallDrain(samples: List<MonitoringSample>): Double? {
        val withPct = samples.mapNotNull { s -> s.batteryPercent?.let { s.timestampEpochMs to it } }
        if (withPct.size < 2) return null
        val (t0, p0) = withPct.first()
        val (t1, p1) = withPct.last()
        return DrainStats.percentPerHour(p0, p1, t1 - t0).takeIf { it > 0 }
    }

    private fun segmentDrain(samples: List<MonitoringSample>): Double? {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        if (ordered.size < 2) return null
        val start = ordered.first().batteryPercent ?: return null
        val end = ordered.last().batteryPercent ?: return null
        val duration = ordered.last().timestampEpochMs - ordered.first().timestampEpochMs
        if (duration < TimeConstants.MILLIS_PER_MINUTE * 30) return null
        return DrainStats.percentPerHour(start, end, duration).takeIf { it >= 0 }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
