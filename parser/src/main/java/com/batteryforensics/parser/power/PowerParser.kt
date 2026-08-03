package com.batteryforensics.parser.power

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.PackageCount
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.PowerSummary
import com.batteryforensics.parser.WakeLockSummary

class PowerParser : DumpsysParser<PowerSummary> {
    override val sourceName: String = "dumpsys power"

    override fun parse(rawDump: String): ParseResult<PowerSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty power dump")
        val wakeLocks = Regex("""Wake Locks: size=(\d+)""").find(rawDump)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""mWakeLocks\.size=(\d+)""").find(rawDump)
                ?.groupValues?.get(1)?.toIntOrNull()
        return ParseResult.Success(
            PowerSummary(
                wakeLockCount = wakeLocks,
                notes = if (wakeLocks == null) listOf("Wake lock size not found in dump") else emptyList(),
            ),
        )
    }
}

/** Richer wake-lock categorization when dump text allows. */
class WakeLockParser : DumpsysParser<WakeLockSummary> {
    override val sourceName: String = "dumpsys power (wakelocks)"

    override fun parse(rawDump: String): ParseResult<WakeLockSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty power dump")
        val total = Regex("""Wake Locks: size=(\d+)""").find(rawDump)
            ?.groupValues?.get(1)?.toIntOrNull()
        val kernel = rawDump.lineSequence().count {
            it.contains("kernel", ignoreCase = true) && it.contains("wake", ignoreCase = true)
        }.takeIf { it > 0 }
        val tags = Regex("""uid=(\d+)\s+pid=\d+\s+tag=([^\s]+)""")
            .findAll(rawDump)
            .map { PackageCount(it.groupValues[2], 1) }
            .groupBy { it.packageName }
            .map { (tag, list) -> PackageCount(tag, list.size) }
            .sortedByDescending { it.count }
            .take(10)
        val app = tags.sumOf { it.count }.takeIf { it > 0 }
        return ParseResult.Success(
            WakeLockSummary(
                totalLocks = total,
                appLocks = app,
                kernelLocks = kernel,
                topTags = tags,
                notes = buildList {
                    add("App vs kernel split is best-effort from dump text — OEM formats vary")
                    if (total == null && tags.isEmpty()) add("No wake lock attribution found")
                },
            ),
        )
    }
}
