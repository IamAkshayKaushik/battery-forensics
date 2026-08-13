package com.batteryforensics.parser.connectivity

import com.batteryforensics.parser.ConnectivitySummary
import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult

/**
 * Best-effort `dumpsys connectivity` parser for active transport context.
 */
class ConnectivityParser : DumpsysParser<ConnectivitySummary> {
    override val sourceName: String = "dumpsys connectivity"

    override fun parse(rawDump: String): ParseResult<ConnectivitySummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty connectivity dump")
        val defaultNetwork = Regex(
            """(?:Active default network|Default network|mActiveDefaultNetwork)\s*[:=]?\s*([^\n\r]+)""",
            RegexOption.IGNORE_CASE,
        ).find(rawDump)?.groupValues?.getOrNull(1)?.trim()?.take(80)

        val transports = buildList {
            if (Regex("""TRANSPORT_WIFI|WIFI""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump) &&
                Regex("""(?:validated|CONNECTED|CONNECTED_ROAMING)""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(rawDump)
            ) {
                // Prefer explicit transport tokens when present
            }
            Regex("""TRANSPORT_(\w+)""", RegexOption.IGNORE_CASE).findAll(rawDump)
                .map { it.groupValues[1].uppercase() }
                .distinct()
                .take(6)
                .forEach { add(it) }
        }.ifEmpty {
            buildList {
                if (Regex("""\bWIFI\b""").containsMatchIn(rawDump)) add("WIFI")
                if (Regex("""\bCELLULAR\b|\bMOBILE\b""").containsMatchIn(rawDump)) add("CELLULAR")
                if (Regex("""\bBLUETOOTH\b""").containsMatchIn(rawDump)) add("BLUETOOTH")
                if (Regex("""\bETHERNET\b""").containsMatchIn(rawDump)) add("ETHERNET")
            }
        }

        val validated = when {
            Regex("""validated\s*[:=]\s*true|VALIDATED""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump) -> true
            Regex("""validated\s*[:=]\s*false|NOT_VALIDATED""", RegexOption.IGNORE_CASE).containsMatchIn(rawDump) -> false
            else -> null
        }
        val networkCount = Regex("""NetworkAgentInfo""").findAll(rawDump).count().takeIf { it > 0 }
            ?: Regex("""Network\{\d+""").findAll(rawDump).count().takeIf { it > 0 }

        return ParseResult.Success(
            ConnectivitySummary(
                activeDefaultNetwork = defaultNetwork,
                transports = transports.distinct(),
                validated = validated,
                networkAgentCount = networkCount,
                notes = listOf(
                    "Connectivity dump is Derived context for radio drain — not Measured airtime",
                ),
            ),
        )
    }
}
