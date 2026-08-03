package com.batteryforensics.database.mapper

import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.database.entity.MonitoringSampleEntity

fun MonitoringSampleEntity.toDomain(): MonitoringSample = MonitoringSample(
    timestampEpochMs = timestampEpochMs,
    batteryPercent = batteryPercent,
    voltageMv = voltageMv,
    currentMicroamps = currentMicroamps,
    chargeCounterMah = chargeCounterMah,
    temperatureC = temperatureC,
    isCharging = isCharging,
    chargePlug = chargePlug,
    screenOn = screenOn,
    brightnessPercent = brightnessPercent,
    refreshRateHz = refreshRateHz,
    thermalStatus = thermalStatus,
    wifiConnected = wifiConnected,
    wifiRssiDbm = wifiRssiDbm,
    cellularRssiDbm = cellularRssiDbm,
    networkType = networkType,
)

fun MonitoringSample.toEntity(): MonitoringSampleEntity = MonitoringSampleEntity(
    timestampEpochMs = timestampEpochMs,
    batteryPercent = batteryPercent,
    voltageMv = voltageMv,
    currentMicroamps = currentMicroamps,
    chargeCounterMah = chargeCounterMah,
    temperatureC = temperatureC,
    isCharging = isCharging,
    chargePlug = chargePlug,
    screenOn = screenOn,
    brightnessPercent = brightnessPercent,
    refreshRateHz = refreshRateHz,
    thermalStatus = thermalStatus,
    wifiConnected = wifiConnected,
    wifiRssiDbm = wifiRssiDbm,
    cellularRssiDbm = cellularRssiDbm,
    networkType = networkType,
)
