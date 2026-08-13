package com.batteryforensics.parser

/**
 * Converts raw dumpsys (or similar) text into typed models.
 * Raw dumps must never reach the UI.
 */
interface DumpsysParser<T> {
    val sourceName: String
    fun parse(rawDump: String): ParseResult<T>
}

sealed class ParseResult<out T> {
    data class Success<T>(val value: T, val warnings: List<String> = emptyList()) : ParseResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : ParseResult<Nothing>()
}

data class BatteryStatsSummary(
    val capacityMah: Int?,
    val dischargeDurationMs: Long?,
    val screenOnDischargeMah: Double?,
    val estimatedBatteryCapacityMah: Int?,
    val notes: List<String> = emptyList(),
    /** Checkin (`dumpsys batterystats -c`) uid drain hints — Derived. */
    val checkinUidDrainHints: List<PackageCount> = emptyList(),
    /** Radio / wifi active time hints from human or checkin dump when present. */
    val wifiRadioActiveMsHint: Long? = null,
)

data class DeviceIdleSummary(
    val deepEnabled: Boolean?,
    val lightEnabled: Boolean?,
    val state: String?,
    val notes: List<String> = emptyList(),
)

data class PowerSummary(
    val wakeLockCount: Int?,
    val notes: List<String> = emptyList(),
)
