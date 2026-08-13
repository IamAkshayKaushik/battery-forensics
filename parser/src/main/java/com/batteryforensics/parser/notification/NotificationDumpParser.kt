package com.batteryforensics.parser.notification

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.NotificationDumpSummary
import com.batteryforensics.parser.ParseResult

/**
 * Best-effort `dumpsys notification` — active count + listener package hints.
 * Useful for wake-adjacent notification traffic; not a full notification inbox.
 */
class NotificationDumpParser : DumpsysParser<NotificationDumpSummary> {
    override val sourceName: String = "dumpsys notification"

    override fun parse(rawDump: String): ParseResult<NotificationDumpSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty notification dump")
        val count = Regex("""(?:NotificationRecord|enqueued)\s""", RegexOption.IGNORE_CASE)
            .findAll(rawDump).count().takeIf { it > 0 }
            ?: Regex("""Active notifications?:\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(rawDump)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val listeners = Regex(
            """(?:NotificationListenerService|listener|pkg)=([\w.]+(?:\.[\w.]+)+)""",
            RegexOption.IGNORE_CASE,
        ).findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.contains('.') && !it.startsWith("android.") }
            .distinct()
            .take(12)
            .toList()
        return ParseResult.Success(
            NotificationDumpSummary(
                activeNotificationCount = count,
                listenerHints = listeners,
                notes = listOf(
                    "Notification dump hints are Derived — wake correlation is Inferred when used in rules",
                ),
            ),
        )
    }
}
