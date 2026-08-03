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

        // Fallback: count "Alarm{" lines as proxy
        val alarmLines = rawDump.lineSequence().count { it.contains("Alarm{", ignoreCase = false) }
        val count = wakeup ?: alarmLines.takeIf { it > 0 }

        return ParseResult.Success(
            AlarmSummary(
                wakeupAlarmCount = count,
                topPackages = packageHits,
                notes = buildList {
                    if (packageHits.isEmpty()) add("Top packages not clearly attributed in this dump format")
                    add("Wakeup counts are Measured from dumpsys when Shizuku provides the dump")
                },
            ),
        )
    }
}
