package com.batteryforensics.display

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import com.batteryforensics.core.model.MetricKind

data class DisplayReading(
    val screenOn: Boolean,
    val brightnessPercent: Int?,
    val refreshRateHz: Float?,
    val kind: MetricKind = MetricKind.Measured,
    /**
     * HDR active when API ≥ 34 exposes [android.view.Display.isHdr].
     * Otherwise null — never invent Measured HDR.
     */
    val hdrActive: Boolean? = null,
)

class DisplayMetricsCollector(
    private val context: Context,
) {
    fun read(): DisplayReading {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val screenOn = powerManager.isInteractive

        val brightnessRaw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()
        val brightnessPercent = brightnessRaw?.let { ((it / 255f) * 100f).toInt().coerceIn(0, 100) }

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }

        val refreshRateHz = display?.refreshRate

        val hdrActive = if (Build.VERSION.SDK_INT >= 34) {
            runCatching { display?.isHdr }.getOrNull()
        } else {
            null
        }

        return DisplayReading(
            screenOn = screenOn,
            brightnessPercent = brightnessPercent,
            refreshRateHz = refreshRateHz,
            hdrActive = hdrActive,
        )
    }
}
