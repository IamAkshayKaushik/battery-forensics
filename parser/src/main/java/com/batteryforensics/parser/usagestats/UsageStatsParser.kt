package com.batteryforensics.parser.usagestats

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.UsageStatsSummary

class UsageStatsParser : DumpsysParser<UsageStatsSummary> {
    override val sourceName: String = "dumpsys usagestats"

    private val allBuckets = listOf(
        "ACTIVE", "WORKING_SET", "FREQUENT", "RARE", "RESTRICTED",
    )

    override fun parse(rawDump: String): ParseResult<UsageStatsSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty usagestats dump")
        val bucketHits = Regex("""standby[_ ]?bucket[=: ]+(\w+)""", RegexOption.IGNORE_CASE)
            .findAll(rawDump)
            .map { it.groupValues[1].uppercase() }
            .toList()
        val hints = bucketHits.distinct().take(20)
        val counts = allBuckets.associateWith { name ->
            bucketHits.count { it == name || it.contains(name) }
        }.filterValues { it > 0 }

        val elevatedPkgs = Regex(
            """([\w.]+)\s+.*standby[_ ]?bucket[=: ]+(ACTIVE|WORKING_SET|FREQUENT)""",
            RegexOption.IGNORE_CASE,
        ).findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1)?.takeIf { p -> p.contains('.') } }
            .distinct()
            .take(10)
            .toList()

        val bypass = Regex(
            """([\w.]+).*standby[_ ]?bucket[=: ]+\w+.*(?:exempt(?:ed)?|bypass|whitelist|unrestricted)\s*=?\s*true""",
            RegexOption.IGNORE_CASE,
        ).findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1)?.takeIf { p -> p.contains('.') } }
            .distinct()
            .take(10)
            .toList()
            .ifEmpty {
                Regex(
                    """([\w.]+).*?(?:exempt(?:ed)?|bypass)\s*=\s*true""",
                    RegexOption.IGNORE_CASE,
                ).findAll(rawDump)
                    .mapNotNull { it.groupValues.getOrNull(1)?.takeIf { p -> p.contains('.') } }
                    .distinct()
                    .take(10)
                    .toList()
            }

        return ParseResult.Success(
            UsageStatsSummary(
                standbyBucketHints = hints.ifEmpty { counts.keys.toList() },
                elevatedBucketPackages = elevatedPkgs,
                bucketCounts = counts,
                bypassPackageHints = bypass,
                notes = buildList {
                    if (hints.isEmpty() && counts.isEmpty()) {
                        add("No standby bucket tokens found — OEM dump format may differ")
                    }
                    add("App Standby buckets: ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED")
                    add("Bypass/exemption hints are Derived from dumpsys text when present")
                },
            ),
        )
    }
}
