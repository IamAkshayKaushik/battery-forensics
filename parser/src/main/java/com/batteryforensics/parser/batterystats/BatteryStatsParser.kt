package com.batteryforensics.parser.batterystats

import com.batteryforensics.parser.BatteryStatsSummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.PackageCount
import com.batteryforensics.parser.ParseResult

/**
 * Parses human `dumpsys batterystats` and optional checkin (`-c`) lines.
 * Checkin uid drain hints are Derived — codes vary by Android version.
 */
class BatteryStatsParser : DumpsysParser<BatteryStatsSummary> {
    override val sourceName: String = "dumpsys batterystats"

    override fun parse(rawDump: String): ParseResult<BatteryStatsSummary> {
        if (rawDump.isBlank()) {
            return ParseResult.Failure("Empty batterystats dump")
        }
        val capacity = Regex("""Estimated battery capacity:\s*(\d+)\s*mAh""")
            .find(rawDump)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        val computed = Regex("""Computed drain:\s*([\d.]+)\s*\((\d+)\s*mAh""")
            .find(rawDump)
        val dischargeMah = computed?.groupValues?.get(2)?.toDoubleOrNull()
        val screenOn = Regex("""Screen:\s*([\d.]+)\s*\(([\d.]+)\s*mAh""")
            .find(rawDump)
            ?.groupValues
            ?.get(2)
            ?.toDoubleOrNull()
        val wifiRadioMs = Regex(
            """(?:wifi\.radio|Wifi radio|wifi_radio).*?(\d+)\s*(?:ms|msec)""",
            RegexOption.IGNORE_CASE,
        ).find(rawDump)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: Regex(""",wr,(\d+)""").find(rawDump)?.groupValues?.getOrNull(1)?.toLongOrNull()

        val checkinHints = parseCheckinUidHints(rawDump)

        val warnings = buildList {
            if (capacity == null && checkinHints.isEmpty()) add("Estimated battery capacity not found")
        }

        return ParseResult.Success(
            BatteryStatsSummary(
                capacityMah = capacity,
                dischargeDurationMs = null,
                screenOnDischargeMah = screenOn,
                estimatedBatteryCapacityMah = capacity,
                notes = buildList {
                    dischargeMah?.let { add("Computed drain ≈ ${it.toInt()} mAh") }
                    if (checkinHints.isNotEmpty()) {
                        add("Checkin uid drain hints: ${checkinHints.size} entries (Derived)")
                    }
                },
                checkinUidDrainHints = checkinHints,
                wifiRadioActiveMsHint = wifiRadioMs,
            ),
            warnings = warnings,
        )
    }

    /**
     * Checkin format often includes `uid,uidnum,pkg` and power lines; extract package-like tokens
     * paired with numeric drain when present. Graceful on unfamiliar OEM checkin dialects.
     */
    private fun parseCheckinUidHints(raw: String): List<PackageCount> {
        if (!raw.contains(',') || raw.lines().size < 3) return emptyList()
        val pkgCounts = linkedMapOf<String, Int>()
        // Common: 9,0,i,uid,1000,com.android.systemui  or uid lines with package
        Regex("""(?:,|^)(?:uid|i),?(\d+),([\w.]+(?:\.[\w.]+)+)""", RegexOption.IGNORE_CASE)
            .findAll(raw)
            .forEach { m ->
                val pkg = m.groupValues.getOrNull(2) ?: return@forEach
                if (pkg.contains('.')) pkgCounts[pkg] = (pkgCounts[pkg] ?: 0) + 1
            }
        // Power checkin: pwi / uid power with package nearby
        Regex("""([\w.]+(?:\.[\w.]+)+),(\d{2,})""")
            .findAll(raw)
            .forEach { m ->
                val pkg = m.groupValues[1]
                val n = m.groupValues[2].toIntOrNull() ?: return@forEach
                if (pkg.contains('.') && !pkg.startsWith("android.os") && n in 1..50_000) {
                    pkgCounts[pkg] = maxOf(pkgCounts[pkg] ?: 0, n)
                }
            }
        return pkgCounts.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { PackageCount(it.key, it.value) }
    }
}
