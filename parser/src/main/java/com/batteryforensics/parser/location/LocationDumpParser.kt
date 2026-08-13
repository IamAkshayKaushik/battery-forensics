package com.batteryforensics.parser.location

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.LocationDumpSummary
import com.batteryforensics.parser.ParseResult

/** Best-effort `dumpsys location` — providers and request package hints. */
class LocationDumpParser : DumpsysParser<LocationDumpSummary> {
    override val sourceName: String = "dumpsys location"

    override fun parse(rawDump: String): ParseResult<LocationDumpSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty location dump")
        val providers = buildList {
            if (Regex("""\bgps\b.*(?:enabled|true)|provider=gps""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(rawDump)
            ) {
                add("gps")
            }
            if (Regex("""\bnetwork\b.*(?:enabled|true)|provider=network""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(rawDump)
            ) {
                add("network")
            }
            if (Regex("""\bfused\b|provider=fused""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump)) {
                add("fused")
            }
            Regex("""provider[s]?\s*[:=]\s*\[?([^\]]+)""", RegexOption.IGNORE_CASE)
                .find(rawDump)?.groupValues?.getOrNull(1)
                ?.split(',', ' ')
                ?.map { it.trim().lowercase() }
                ?.filter { it in setOf("gps", "network", "fused", "passive") }
                ?.forEach { add(it) }
        }.distinct()

        val requests = Regex(
            """(?:LocationRequest|request from|by package)\s*[:=]?\s*([\w.]+(?:\.[\w.]+)+)""",
            RegexOption.IGNORE_CASE,
        ).findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.contains('.') }
            .distinct()
            .take(12)
            .toList()

        val gpsListeners = Regex("""gps.*?listeners?\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(rawDump)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""GpsLocationProvider""").findAll(rawDump).count().takeIf { it > 0 }

        return ParseResult.Success(
            LocationDumpSummary(
                providersEnabled = providers,
                activeRequestHints = requests,
                gpsListenerCount = gpsListeners,
                notes = listOf(
                    "Location dump is Derived — provider tokens only, not continuous GPS logs",
                ),
            ),
        )
    }
}
