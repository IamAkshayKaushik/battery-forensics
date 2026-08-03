package com.batteryforensics.parser.cmd

import com.batteryforensics.parser.CmdBatterySummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.JobSchedulerSummary
import com.batteryforensics.parser.ParseResult

/** Best-effort `cmd battery` output parser. */
class CmdBatteryParser : DumpsysParser<CmdBatterySummary> {
    override val sourceName: String = "cmd battery"

    override fun parse(rawDump: String): ParseResult<CmdBatterySummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty cmd battery output")
        val level = Regex("""level:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
        val temp = Regex("""temperature:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
        val status = Regex("""status:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)
            ?: rawDump.lineSequence().firstOrNull()?.trim()?.take(120)
        return ParseResult.Success(
            CmdBatterySummary(
                statusLine = status,
                level = level,
                temperatureTenthsC = temp,
                notes = listOf("cmd battery is a privileged stub summary — complements BatteryManager samples"),
            ),
        )
    }
}

/**
 * Alternate jobs path via `cmd jobscheduler` (when dumpsys jobscheduler is sparse).
 * Reuses [JobSchedulerSummary].
 */
class CmdJobSchedulerParser : DumpsysParser<JobSchedulerSummary> {
    override val sourceName: String = "cmd jobscheduler"

    override fun parse(rawDump: String): ParseResult<JobSchedulerSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty cmd jobscheduler output")
        val pending = Regex("""Pending(?: jobs)?:?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
            ?: rawDump.lineSequence().count { it.contains("JobStatus{", ignoreCase = false) }
                .takeIf { it > 0 }
        val running = Regex("""Running(?: jobs)?:?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
        return ParseResult.Success(
            JobSchedulerSummary(
                pendingJobCount = pending,
                runningJobCount = running,
                notes = listOf("Parsed from cmd jobscheduler — OEM formats vary"),
            ),
        )
    }
}
