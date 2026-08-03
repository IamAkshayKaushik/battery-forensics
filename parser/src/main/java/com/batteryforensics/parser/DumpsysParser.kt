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
