package com.batteryforensics.parser.deviceidle

import com.batteryforensics.parser.DeviceIdleSummary
import com.batteryforensics.parser.DozeTimelineSummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult

class DeviceIdleParser : DumpsysParser<DeviceIdleSummary> {
    override val sourceName: String = "dumpsys deviceidle"

    override fun parse(rawDump: String): ParseResult<DeviceIdleSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty deviceidle dump")
        val mEnabled = Regex("""mEnabled=(\w+)""").find(rawDump)?.groupValues?.get(1)
        val mForceIdle = Regex("""mForceIdle=(\w+)""").find(rawDump)?.groupValues?.get(1)
        val state = Regex("""mState=(\w+)""").find(rawDump)?.groupValues?.get(1)
        val light = Regex("""mLightEnabled=(\w+)""").find(rawDump)?.groupValues?.get(1)
        return ParseResult.Success(
            DeviceIdleSummary(
                deepEnabled = mEnabled?.equals("true", ignoreCase = true),
                lightEnabled = light?.equals("true", ignoreCase = true),
                state = state,
                notes = listOfNotNull(mForceIdle?.let { "mForceIdle=$it" }),
            ),
        )
    }
}

/**
 * Maps deviceidle dump into Doze timeline-oriented summary.
 * States: ACTIVE, INACTIVE, IDLE_PENDING, SENSING, LOCATING, IDLE, IDLE_MAINTENANCE, etc.
 */
class DozeParser : DumpsysParser<DozeTimelineSummary> {
    override val sourceName: String = "dumpsys deviceidle (doze)"

    private val knownStates = listOf(
        "ACTIVE", "INACTIVE", "IDLE_PENDING", "SENSING", "LOCATING",
        "IDLE", "IDLE_MAINTENANCE", "LIGHT_IDLE", "LIGHT_MAINTENANCE",
    )

    override fun parse(rawDump: String): ParseResult<DozeTimelineSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty deviceidle dump")
        val base = DeviceIdleParser().parse(rawDump)
        if (base is ParseResult.Failure) return base
        val summary = (base as ParseResult.Success).value
        val hints = knownStates.filter { rawDump.contains(it) }
        return ParseResult.Success(
            DozeTimelineSummary(
                state = summary.state,
                deepEnabled = summary.deepEnabled,
                lightEnabled = summary.lightEnabled,
                historyHints = hints,
                notes = summary.notes + listOf(
                    "Doze state names found in dump are Measured; time-in-state needs history section when present",
                ),
            ),
            warnings = base.warnings,
        )
    }
}
