package com.batteryforensics.parser.alarm

import com.batteryforensics.parser.AlarmSummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.PackageCount
import com.batteryforensics.parser.ParseResult

/**
 * Parses `dumpsys alarm` for wakeup frequency hints and top packages.
 * Typed models only — raw dump never reaches UI.
 */
class AlarmParser : DumpsysParser<AlarmSummary> {
    override val sourceName: String = "dumpsys alarm"

    override fun parse(rawDump: String): ParseResult<AlarmSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty alarm dump")

        val wakeup = Regex("""(\d+)\s+wakeup(?:s)?""", RegexOption.IGNORE_CASE)
            .findAll(rawDump)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull()

        val rtc = Regex("""RTC_WAKEUP(?:\s+alarms)?[=:\s]+(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""type\s*=\s*0\b""").findAll(rawDump).count().takeIf { it > 0 }

        val elapsed = Regex(
            """ELAPSED_REALTIME_WAKEUP(?:\s+alarms)?[=:\s]+(\d+)""",
            RegexOption.IGNORE_CASE,
        ).find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""type\s*=\s*2\b""").findAll(rawDump).count().takeIf { it > 0 }

        val packageHits = Regex("""u0a\d+|([\w.]+):\s+(\d+)\s+wakeup""", RegexOption.IGNORE_CASE)
            .findAll(rawDump)
            .mapNotNull { m ->
                val pkg = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val count = m.groupValues.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                PackageCount(pkg, count)
            }
            .groupBy { it.packageName }
            .map { (pkg, list) -> PackageCount(pkg, list.sumOf { it.count }) }
            .sortedByDescending { it.count }
            .take(8)

        val alarmLines = rawDump.lineSequence().count { it.contains("Alarm{", ignoreCase = false) }
        val count = wakeup ?: alarmLines.takeIf { it > 0 }

        val uptimeMs = Regex(
            """Elapsed realtime since boot[=:\s]+(\d+)\s*ms""",
            RegexOption.IGNORE_CASE,
        ).find(rawDump)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("""elapsed\s+realtime[=:\s]+(\d+)""", RegexOption.IGNORE_CASE)
                .find(rawDump)?.groupValues?.get(1)?.toLongOrNull()

        val wakeupsPerHour = if (count != null && uptimeMs != null && uptimeMs > 0) {
            count.toDouble() / (uptimeMs.toDouble() / 3_600_000.0)
        } else {
            null
        }

        val impact = buildImpact(count, wakeupsPerHour, rtc, elapsed)

        return ParseResult.Success(
            AlarmSummary(
                wakeupAlarmCount = count,
                topPackages = packageHits,
                rtcWakeupCount = rtc,
                elapsedRealtimeWakeupCount = elapsed,
                wakeupsPerHour = wakeupsPerHour,
                impactEstimate = impact,
                notes = buildList {
                    if (packageHits.isEmpty()) add("Top packages not clearly attributed in this dump format")
                    add("Wakeup counts are Measured from dumpsys when Shizuku provides the dump")
                    add("RTC vs ELAPSED split is Derived when type counts appear in dump text")
                    if (wakeupsPerHour != null) {
                        add("wakeups/hour Derived from wakeup count ÷ elapsed realtime since boot")
                    }
                },
            ),
        )
    }

    private fun buildImpact(
        count: Int?,
        perHour: Double?,
        rtc: Int?,
        elapsed: Int?,
    ): String {
        val rate = perHour?.let { "%.1f wakeups/h".format(it) } ?: "rate unknown"
        val split = when {
            rtc != null && elapsed != null -> "RTC_WAKEUP=$rtc ELAPSED_WAKEUP=$elapsed"
            rtc != null -> "RTC_WAKEUP=$rtc"
            elapsed != null -> "ELAPSED_WAKEUP=$elapsed"
            else -> "type split unavailable"
        }
        val severity = when {
            (perHour != null && perHour >= 15) || (count != null && count >= 40) -> "High idle-wake risk"
            (perHour != null && perHour >= 5) || (count != null && count >= 15) -> "Moderate idle-wake risk"
            else -> "Low–moderate wake pressure"
        }
        return "$severity · ~${count ?: "?"} wakeups · $rate · $split"
    }
}
