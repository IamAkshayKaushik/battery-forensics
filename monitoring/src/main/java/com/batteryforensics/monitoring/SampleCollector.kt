package com.batteryforensics.monitoring

import android.content.Context
import com.batteryforensics.battery.BatteryMetricsCollector
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.display.DisplayMetricsCollector
import com.batteryforensics.telephony.TelephonyMetricsCollector
import com.batteryforensics.thermal.ThermalMetricsCollector
import com.batteryforensics.wifi.WifiMetricsCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SampleCollector @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val battery = BatteryMetricsCollector(context)
    private val display = DisplayMetricsCollector(context)
    private val thermal = ThermalMetricsCollector(context)
    private val telephony = TelephonyMetricsCollector(context)
    private val wifi = WifiMetricsCollector(context)
    private val system = SystemSignalsCollector(context)

    fun collect(nowEpochMs: Long = System.currentTimeMillis()): MonitoringSample {
        val b = battery.read()
        val d = display.read()
        val t = thermal.read()
        val cell = telephony.read()
        val w = wifi.read()
        val s = system.read()
        val chargingUa = if (b.isCharging) {
            b.currentMicroamps?.let { abs(it) }
        } else {
            null
        }
        return MonitoringSample(
            timestampEpochMs = nowEpochMs,
            batteryPercent = b.percent,
            voltageMv = b.voltageMv,
            currentMicroamps = b.currentMicroamps,
            chargeCounterMah = b.chargeCounterMah,
            temperatureC = b.temperatureC,
            isCharging = b.isCharging,
            chargePlug = b.chargePlug,
            screenOn = d.screenOn,
            brightnessPercent = d.brightnessPercent,
            refreshRateHz = d.refreshRateHz,
            thermalStatus = t.thermalStatus,
            wifiConnected = w.connected,
            wifiRssiDbm = w.rssiDbm,
            cellularRssiDbm = cell.cellularRssiDbm,
            networkType = cell.networkType,
            chargingCurrentMicroamps = chargingUa,
            orientation = s.orientation,
            cellId = s.cellId,
            carrierName = s.carrierName,
            cellularBand = s.cellularBand,
            bluetoothOn = s.bluetoothOn,
            bluetoothConnected = s.bluetoothConnected,
            locationEnabled = s.locationEnabled,
            nfcEnabled = s.nfcEnabled,
            hotspotOn = s.hotspotOn,
            foregroundApp = s.foregroundApp,
            memoryPressure = s.memoryPressure,
            storageFreeBytes = s.storageFreeBytes,
            storageFreePercent = s.storageFreePercent,
            hdrActive = d.hdrActive,
        )
    }
}
