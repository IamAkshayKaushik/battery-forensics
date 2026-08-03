package com.batteryforensics.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitoring_samples")
data class MonitoringSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val chargingCurrentMicroamps: Int? = null,
    val orientation: String? = null,
    val cellId: String? = null,
    val carrierName: String? = null,
    val cellularBand: String? = null,
    val bluetoothOn: Boolean? = null,
    val bluetoothConnected: Boolean? = null,
    val locationEnabled: Boolean? = null,
    val nfcEnabled: Boolean? = null,
    val hotspotOn: Boolean? = null,
    val foregroundApp: String? = null,
    val memoryPressure: String? = null,
    val storageFreeBytes: Long? = null,
    val storageFreePercent: Float? = null,
)

@Entity(tableName = "timeline_events")
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val eventType: String,
    val title: String,
    val detail: String,
    val severity: String,
)
