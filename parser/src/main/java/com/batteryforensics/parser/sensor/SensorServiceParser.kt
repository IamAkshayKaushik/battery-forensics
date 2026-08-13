package com.batteryforensics.parser.sensor

import com.batteryforensics.parser.DumpsysParser
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.SensorServiceSummary

/**
 * Best-effort `dumpsys sensorservice` — continuous listener hints for wake/drain rules.
 * Not a continuous IMU sampler; dump tokens only.
 */
class SensorServiceParser : DumpsysParser<SensorServiceSummary> {
    override val sourceName: String = "dumpsys sensorservice"

    override fun parse(rawDump: String): ParseResult<SensorServiceSummary> {
        if (rawDump.isBlank()) return ParseResult.Failure("Empty sensorservice dump")
        val listeners = Regex(
            """(?:Connection|Listener|Package)\s*[:=]?\s*([\w.]+(?:\.[\w.]+)+)""",
            RegexOption.IGNORE_CASE,
        ).findAll(rawDump)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.contains('.') && !it.startsWith("android.hardware") }
            .distinct()
            .take(12)
            .toList()
        val continuous = Regex(
            """(?:continuous|ACTIVE|active connections?)\s*[:=]?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        ).find(rawDump)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""SensorEventConnection""").findAll(rawDump).count().takeIf { it > 0 }
            ?: listeners.size.takeIf { it > 0 }
        return ParseResult.Success(
            SensorServiceSummary(
                activeSensorCount = continuous,
                continuousListenerHints = listeners,
                notes = listOf(
                    "Sensor dump hints are Derived — not continuous HAL sampling",
                    "Never treat as Measured RRC or modem state",
                ),
            ),
        )
    }
}
