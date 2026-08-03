package com.batteryforensics.parser.power

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.PackageCount
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.PowerSummary
import com.batteryforensics.parser.WakeLockSummary

class PowerParser : DumpsysParser<PowerSummary> {
    override val sourceName: String = "dumpsys power"

    override fun parse(rawDump: String): ParseResult<PowerSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty power dump")
        val wakeLocks = Regex("""Wake Locks: size=(\d+)""").find(rawDump)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""mWakeLocks\.size=(\d+)""").find(rawDump)
                ?.groupValues?.get(1)?.toIntOrNull()
        return ParseResult.Success(
            PowerSummary(
                wakeLockCount = wakeLocks,
                notes = if (wakeLocks == null) listOf("Wake lock size not found in dump") else emptyList(),
            ),
        )
    }
}

/** Richer wake-lock categorization when dump text allows. */
class WakeLockParser : DumpsysParser<WakeLockSummary> {
    override val sourceName: String = "dumpsys power (wakelocks)"

    override fun parse(rawDump: String): ParseResult<WakeLockSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty power dump")
        val total = Regex("""Wake Locks: size=(\d+)""").find(rawDump)
            ?.groupValues?.get(1)?.toIntOrNull()
        val kernel = rawDump.lineSequence().count {
            it.contains("kernel", ignoreCase = true) && it.contains("wake", ignoreCase = true)
        }.takeIf { it > 0 }

        val tags = Regex("""uid=(\d+)\s+pid=\d+\s+tag=([^\s]+)""")
            .findAll(rawDump)
            .map { PackageCount(it.groupValues[2], 1) }
            .groupBy { it.packageName }
            .map { (tag, list) -> PackageCount(tag, list.size) }
            .sortedByDescending { it.count }
            .take(20)

        fun countCategory(predicate: (String) -> Boolean): Int =
            tags.count { predicate(it.packageName) } +
                rawDump.lineSequence().count { line ->
                    predicate(line) && line.contains("wake", ignoreCase = true)
                }.let { if (it > 0 && tags.none { t -> predicate(t.packageName) }) it else 0 }

        val modem = countCategory { t ->
            t.contains("modem", ignoreCase = true) ||
                t.contains("radio", ignoreCase = true) ||
                t.contains("RIL", ignoreCase = true)
        }.takeIf { it > 0 }
        val wifi = countCategory { t ->
            t.contains("wifi", ignoreCase = true) ||
                t.contains("wlan", ignoreCase = true)
        }.takeIf { it > 0 }
        val sensors = countCategory { t ->
            t.contains("sensor", ignoreCase = true) ||
                t.contains("Gps", ignoreCase = true) ||
                t.contains("location", ignoreCase = true)
        }.takeIf { it > 0 }
        val powerHal = countCategory { t ->
            t.contains("PowerManager", ignoreCase = true) ||
                t.contains("PowerHAL", ignoreCase = true) ||
                t.contains("PowerHal", ignoreCase = true)
        }.takeIf { it > 0 }
        val drivers = countCategory { t ->
            t.contains("driver", ignoreCase = true) ||
                t.startsWith("*") && !t.contains("alarm", ignoreCase = true)
        }.takeIf { it > 0 }

        val classified = listOfNotNull(modem, wifi, sensors, powerHal).sum()
        val app = tags.sumOf { it.count }.takeIf { it > 0 }?.let { totalTags ->
            (totalTags - classified).coerceAtLeast(0).takeIf { it > 0 } ?: totalTags
        }

        val taxonomyNotes = buildList {
            add("Taxonomy (modem/wifi/sensors/HAL/app) is best-effort from tag text — OEM formats vary")
            add("Never claim Measured for kernel vs HAL without matching dump attribution")
        }

        return ParseResult.Success(
            WakeLockSummary(
                totalLocks = total,
                appLocks = app,
                kernelLocks = kernel,
                topTags = tags.take(10),
                modemLocks = modem,
                wifiLocks = wifi,
                sensorLocks = sensors,
                powerHalLocks = powerHal,
                driverLocks = drivers,
                taxonomyNotes = taxonomyNotes,
                notes = buildList {
                    addAll(taxonomyNotes)
                    if (total == null && tags.isEmpty()) add("No wake lock attribution found")
                },
            ),
        )
    }
}
