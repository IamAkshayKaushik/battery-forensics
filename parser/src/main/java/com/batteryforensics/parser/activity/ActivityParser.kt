package com.batteryforensics.parser.activity

import com.batteryforensics.parser.ActivitySummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult

/** Best-effort `dumpsys activity` parser — FGS / resumed activity hints only. */
class ActivityParser : DumpsysParser<ActivitySummary> {
    override val sourceName: String = "dumpsys activity"

    override fun parse(rawDump: String): ParseResult<ActivitySummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty activity dump")
        val fgs = Regex("""(?:isForeground|mForeground|FGS|foregroundService)[^\n]*?([\w.]+(?:\.[\w.]+)+)""", RegexOption.IGNORE_CASE)
            .findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.contains('.') && !it.startsWith("android.") }
            .distinct()
            .take(12)
            .toList()
        val resumed = Regex("""(?:topResumedActivity|mResumedActivity)=([^\s}\]]+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.getOrNull(1)
        return ParseResult.Success(
            ActivitySummary(
                foregroundServiceHints = fgs,
                topResumedActivity = resumed,
                notes = buildList {
                    if (fgs.isEmpty()) add("No clear FGS package tokens — OEM dump formats vary")
                    add("Activity dump attribution is Derived/Inferred — never Measured RRC")
                },
            ),
        )
    }
}
