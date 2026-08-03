package com.batteryforensics.parser.deviceidle

import com.batteryforensics.parser.DeviceIdleSummary
import com.batteryforensics.parser.DozeStateTransition
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
 * Motion/location interruption counts are Derived from dump reason tokens — not a Measured sensor log.
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
        val observed = knownStates.filter { rawDump.contains(it) }
        val transitions = parseTransitions(rawDump)
        val motion = countMotionInterrupts(rawDump, transitions)
        val location = countLocationInterrupts(rawDump, transitions)
        return ParseResult.Success(
            DozeTimelineSummary(
                state = summary.state,
                deepEnabled = summary.deepEnabled,
                lightEnabled = summary.lightEnabled,
                historyHints = observed,
                observedStates = observed,
                transitions = transitions,
                motionTriggeredInterruptions = motion,
                locationTriggeredInterruptions = location,
                notes = summary.notes + listOf(
                    "Doze state names found in dump are Measured; time-in-state needs history section when present",
                    "Motion/location interruption counts are Derived from reason tokens in dumpsys — not IMU logs",
                ),
            ),
            warnings = base.warnings,
        )
    }

    private fun parseTransitions(raw: String): List<DozeStateTransition> {
        val stateLine = Regex(
            """state=(\w+)(?:\s+reason[=:]?\s*(\w+))?""",
            RegexOption.IGNORE_CASE,
        )
        val states = mutableListOf<Pair<String, String?>>()
        for (m in stateLine.findAll(raw)) {
            val st = m.groupValues[1].uppercase()
            if (st !in knownStates && st != "LIGHT_IDLE" && st != "LIGHT_MAINTENANCE") continue
            val reason = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.uppercase()
            states += st to reason
        }
        // Also capture "reason=motion" / "reason=location" adjacent lines
        if (states.isEmpty()) {
            for (hint in knownStates) {
                if (raw.contains(hint)) states += hint to null
            }
        }
        val out = mutableListOf<DozeStateTransition>()
        var prev: String? = null
        for ((st, reason) in states) {
            val hint = when {
                reason != null && reason.contains("MOTION") -> "MOTION"
                reason != null && reason.contains("LOCAT") -> "LOCATION"
                else -> reason
            }
            out += DozeStateTransition(fromState = prev, toState = st, reasonHint = hint)
            prev = st
        }
        return out
    }

    private fun countMotionInterrupts(raw: String, transitions: List<DozeStateTransition>): Int {
        val fromTransitions = transitions.count { it.reasonHint == "MOTION" }
        val tokenHits = Regex(
            """significant\s+motion|mMotionListener|reason[=:]?\s*motion|motion\s+detected""",
            RegexOption.IGNORE_CASE,
        ).findAll(raw).count()
        return maxOf(fromTransitions, tokenHits)
    }

    private fun countLocationInterrupts(raw: String, transitions: List<DozeStateTransition>): Int {
        val fromTransitions = transitions.count { it.reasonHint == "LOCATION" }
        val tokenHits = Regex(
            """location\s+update\s+wake|reason[=:]?\s*location|locating\s+exit""",
            RegexOption.IGNORE_CASE,
        ).findAll(raw).count()
        return maxOf(fromTransitions, tokenHits)
    }
}
