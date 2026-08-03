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
    /** Absolute charge current while plugged (µA), when measurable. */
    val chargingCurrentMicroamps: Int? = null,
    /** portrait / landscape / unknown */
    val orientation: String? = null,
    val cellId: String? = null,
    val carrierName: String? = null,
    /** Best-effort band / RAT hint (e.g. n78, Band 3). */
    val cellularBand: String? = null,
    val bluetoothOn: Boolean? = null,
    val bluetoothConnected: Boolean? = null,
    val locationEnabled: Boolean? = null,
    val nfcEnabled: Boolean? = null,
    val hotspotOn: Boolean? = null,
    /** Package name from UsageStats when permitted. */
    val foregroundApp: String? = null,
    /** low / moderate / critical / unknown */
    val memoryPressure: String? = null,
    val storageFreeBytes: Long? = null,
    val storageFreePercent: Float? = null,
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
