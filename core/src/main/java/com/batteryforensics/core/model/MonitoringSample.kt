package com.batteryforensics.core.model

import kotlinx.serialization.Serializable

/** Immutable snapshot of device power-related signals at one moment. */
@Serializable
data class MonitoringSample(
    val timestampEpochMs: Long,
    val batteryPercent: Int?,
    val voltageMv: Int?,
    val currentMicroamps: Int?,
    val chargeCounterMah: Int?,
    val temperatureC: Float?,
    val isCharging: Boolean?,
    val chargePlug: String?,
    val screenOn: Boolean?,
    val brightnessPercent: Int?,
    val refreshRateHz: Float?,
    val thermalStatus: Int?,
    val wifiConnected: Boolean?,
    val wifiRssiDbm: Int?,
    val cellularRssiDbm: Int?,
    val networkType: String?,
)

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val batteryCapacityMah: Int? = null,
)

@Serializable
sealed class MetricKind {
    data object Measured : MetricKind()
    data object Derived : MetricKind()
    data object Inferred : MetricKind()
}
