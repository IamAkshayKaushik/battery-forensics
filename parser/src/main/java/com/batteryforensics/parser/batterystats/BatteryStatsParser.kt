package com.batteryforensics.parser.batterystats

import com.batteryforensics.parser.BatteryStatsSummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult

/**
 * Minimal batterystats parser for common capacity / discharge fields.
 * Extensible stub — real OEM dumps vary widely.
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

        val warnings = buildList {
            if (capacity == null) add("Estimated battery capacity not found")
        }

        return ParseResult.Success(
            BatteryStatsSummary(
                capacityMah = capacity,
                dischargeDurationMs = null,
                screenOnDischargeMah = screenOn,
                estimatedBatteryCapacityMah = capacity,
                notes = listOfNotNull(dischargeMah?.let { "Computed drain ≈ ${it.toInt()} mAh" }),
            ),
            warnings = warnings,
        )
    }
}
