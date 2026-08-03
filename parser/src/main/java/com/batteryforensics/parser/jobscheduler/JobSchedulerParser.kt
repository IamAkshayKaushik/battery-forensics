package com.batteryforensics.parser.jobscheduler

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.JobSchedulerSummary
import com.batteryforensics.parser.ParseResult

class JobSchedulerParser : DumpsysParser<JobSchedulerSummary> {
    override val sourceName: String = "dumpsys jobscheduler"

    override fun parse(rawDump: String): ParseResult<JobSchedulerSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty jobscheduler dump")
        val pending = Regex("""Pending(?: jobs)?:?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(\d+)\s+pending""", RegexOption.IGNORE_CASE)
                .find(rawDump)?.groupValues?.get(1)?.toIntOrNull()
            ?: rawDump.lineSequence().count { it.contains("JobStatus{", ignoreCase = false) }
                .takeIf { it > 0 }

        return ParseResult.Success(
            JobSchedulerSummary(
                pendingJobCount = pending,
                notes = if (pending == null) listOf("Pending job count not found") else emptyList(),
            ),
        )
    }
}
