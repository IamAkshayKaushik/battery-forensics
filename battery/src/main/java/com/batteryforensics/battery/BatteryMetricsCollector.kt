package com.batteryforensics.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.batteryforensics.core.model.MetricKind

data class BatteryReading(
    val percent: Int?,
    val voltageMv: Int?,
    val currentMicroamps: Int?,
    val chargeCounterMah: Int?,
    val temperatureC: Float?,
    val isCharging: Boolean,
    val chargePlug: String?,
    val kind: MetricKind = MetricKind.Measured,
)

class BatteryMetricsCollector(
    private val context: Context,
) {
    fun read(): BatteryReading {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )

        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargePlug = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> if (isCharging) "unknown" else null
        }

        val temperatureTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperatureC = temperatureTenths
            ?.takeIf { it != Int.MIN_VALUE }
            ?.div(10f)

        val voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }

        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it >= 0 }
        }

        val currentUa = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE && it != 0 || it == 0 }
            ?.let { if (it == Int.MIN_VALUE) null else it }

        val chargeCounter = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0 }
            ?.div(1_000)

        return BatteryReading(
            percent = percent,
            voltageMv = voltageMv,
            currentMicroamps = currentUa,
            chargeCounterMah = chargeCounter,
            temperatureC = temperatureC,
            isCharging = isCharging,
            chargePlug = chargePlug,
        )
    }
}
