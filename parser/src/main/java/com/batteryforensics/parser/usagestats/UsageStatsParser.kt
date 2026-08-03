package com.batteryforensics.parser.usagestats

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.UsageStatsSummary

class UsageStatsParser : DumpsysParser<UsageStatsSummary> {
    override val sourceName: String = "dumpsys usagestats"

    override fun parse(rawDump: String): ParseResult<UsageStatsSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty usagestats dump")
        val buckets = Regex("""standby[_ ]?bucket[=: ]+(\w+)""", RegexOption.IGNORE_CASE)
            .findAll(rawDump)
            .map { it.groupValues[1] }
            .distinct()
            .take(20)
            .toList()
        return ParseResult.Success(
            UsageStatsSummary(
                standbyBucketHints = buckets,
                notes = buildList {
                    if (buckets.isEmpty()) add("No standby bucket tokens found — OEM dump format may differ")
                    add("App Standby buckets require dumpsys usagestats (Shizuku) for full fidelity")
                },
            ),
        )
    }
}
