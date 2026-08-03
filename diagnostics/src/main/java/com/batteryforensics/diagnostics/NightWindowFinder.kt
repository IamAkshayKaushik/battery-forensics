package com.batteryforensics.diagnostics

import com.batteryforensics.core.model.MonitoringSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds user-pickable overnight windows (22:00 → 08:00 local) for differential analysis.
 * Pure JVM logic — unit-tested without Android.
 */
data class NightWindow(
    val id: String,
    val label: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
)

data class NightWindowPair(
    val healthy: NightWindow,
    val problem: NightWindow,
)

object NightWindowFinder {

    private val dayFmt = DateTimeFormatter.ofPattern("MMM d")

    /**
     * @param lookbackDays how many overnight windows to offer (newest first).
     * Night [i] ends on (today - i) at [nightEndHour] and starts the prior calendar day at [nightStartHour].
     */
    fun candidateNights(
        nowEpochMs: Long,
        lookbackDays: Int = 7,
        nightStartHour: Int = 22,
        nightEndHour: Int = 8,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<NightWindow> {
        require(lookbackDays >= 1)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        return (0 until lookbackDays).map { offset ->
            val endDate = today.minusDays(offset.toLong())
            val startDate = endDate.minusDays(1)
            val start = startDate.atTime(nightStartHour, 0).atZone(zone).toInstant().toEpochMilli()
            val end = endDate.atTime(nightEndHour, 0).atZone(zone).toInstant().toEpochMilli()
            val label = when (offset) {
                0 -> "Last night (${dayFmt.format(startDate)}–${dayFmt.format(endDate)})"
                1 -> "Night before (${dayFmt.format(startDate)}–${dayFmt.format(endDate)})"
                else -> "${offset} nights ago (${dayFmt.format(startDate)}–${dayFmt.format(endDate)})"
            }
            NightWindow(
                id = "night-$offset",
                label = label,
                startEpochMs = start,
                endEpochMs = end,
            )
        }
    }

    fun samplesIn(samples: List<MonitoringSample>, window: NightWindow): List<MonitoringSample> =
        samples.filter { it.timestampEpochMs >= window.startEpochMs && it.timestampEpochMs < window.endEpochMs }

    /**
     * Prefer night-1 (healthy) vs night-0 (problem) when both have enough samples;
     * otherwise first two nights that meet [minSamples].
     */
    fun defaultHealthyAndProblem(
        nights: List<NightWindow>,
        samples: List<MonitoringSample>,
        minSamples: Int = 3,
    ): NightWindowPair? {
        if (nights.size < 2) return null
        fun count(w: NightWindow) = samplesIn(samples, w).size
        if (count(nights[1]) >= minSamples && count(nights[0]) >= minSamples) {
            return NightWindowPair(healthy = nights[1], problem = nights[0])
        }
        val rich = nights.filter { count(it) >= minSamples }
        if (rich.size < 2) return null
        return NightWindowPair(healthy = rich[1], problem = rich[0])
    }
}
