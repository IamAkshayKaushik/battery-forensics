package com.batteryforensics.parser.activity

import com.batteryforensics.parser.ActivitySummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult

/** Best-effort `dumpsys activity` / `activity services` — FGS + running service hints. */
class ActivityParser : DumpsysParser<ActivitySummary> {
    override val sourceName: String = "dumpsys activity"

    override fun parse(rawDump: String): ParseResult<ActivitySummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty activity dump")
        val fgs = Regex(
            """(?:isForeground|mForeground|FGS|foregroundService|isForeground=true)[^\n]*?([\w.]+(?:\.[\w.]+)+)""",
            RegexOption.IGNORE_CASE,
        ).findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.contains('.') && !it.startsWith("android.") }
            .distinct()
            .take(12)
            .toList()
        val services = Regex(
            """ServiceRecord\{[^}]*?([\w.]+(?:\.[\w.]+)+)/""",
            RegexOption.IGNORE_CASE,
        ).findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.contains('.') && !it.startsWith("android.") }
            .distinct()
            .take(16)
            .toList()
            .ifEmpty {
                Regex("""app=ProcessRecord\{[^}]*\s([\w.]+(?:\.[\w.]+)+):""")
                    .findAll(rawDump)
                    .mapNotNull { it.groupValues.getOrNull(1) }
                    .filter { it.contains('.') }
                    .distinct()
                    .take(12)
                    .toList()
            }
        val resumed = Regex("""(?:topResumedActivity|mResumedActivity)=([^\s}\]]+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.getOrNull(1)
        return ParseResult.Success(
            ActivitySummary(
                foregroundServiceHints = fgs.ifEmpty { services.take(4) },
                topResumedActivity = resumed,
                runningServiceHints = services,
                notes = buildList {
                    if (fgs.isEmpty()) add("No clear FGS package tokens — OEM dump formats vary")
                    if (services.isNotEmpty()) add("Running service package hints: ${services.size}")
                    add("Activity dump attribution is Derived/Inferred — never Measured RRC")
                },
            ),
        )
    }
}
