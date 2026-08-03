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
