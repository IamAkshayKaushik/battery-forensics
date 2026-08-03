package com.batteryforensics.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.batteryforensics.core.model.MetricKind

data class ThermalReading(
    /** PowerManager thermal status constant, or null if unavailable. */
    val thermalStatus: Int?,
    val kind: MetricKind = MetricKind.Measured,
)

class ThermalMetricsCollector(
    private val context: Context,
) {
    fun read(): ThermalReading {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // getCurrentThermalStatus() requires API 29 (Android 10+)
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            null
        }
        return ThermalReading(thermalStatus = status)
    }
}
