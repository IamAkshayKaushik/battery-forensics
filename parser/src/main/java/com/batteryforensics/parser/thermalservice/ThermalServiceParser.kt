package com.batteryforensics.parser.thermalservice

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.ThermalServiceSummary

class ThermalServiceParser : DumpsysParser<ThermalServiceSummary> {
    override val sourceName: String = "dumpsys thermalservice"

    override fun parse(rawDump: String): ParseResult<ThermalServiceSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty thermalservice dump")
        val status = Regex("""Status:\s*(\w+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)
            ?: Regex("""mStatus[=: ]+(\w+)""").find(rawDump)?.groupValues?.get(1)
        return ParseResult.Success(
            ThermalServiceSummary(
                currentStatus = status,
                notes = if (status == null) listOf("Thermal status token not found") else emptyList(),
            ),
        )
    }
}
