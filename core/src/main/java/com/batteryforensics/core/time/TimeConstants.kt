package com.batteryforensics.core.time

object TimeConstants {
    const val MILLIS_PER_SECOND = 1_000L
    const val MILLIS_PER_MINUTE = 60_000L
    const val MILLIS_PER_HOUR = 3_600_000L
    const val DEFAULT_SAMPLE_INTERVAL_MS = 60_000L
    const val FLIGHT_RECORDER_INTERVAL_MS = 15_000L
    const val WEAK_SIGNAL_DBM_THRESHOLD = -110
    const val HIGH_BRIGHTNESS_PERCENT = 80
    const val HIGH_REFRESH_RATE_HZ = 90f
    const val ELEVATED_TEMP_C = 40f
    const val CHARGING_HEAT_TEMP_C = 42f
    const val RAPID_HEAT_DELTA_C = 5f
    const val OVERNIGHT_STANDBY_DRAIN_PERCENT_PER_HOUR = 3.0
    const val WEAK_WIFI_DBM_THRESHOLD = -80
    const val HIGH_RI_MILLIOHMS = 120.0
    const val RAPID_HEAT_C_PER_MINUTE = 0.4
    const val MODEM_HEAT_TEMP_C = 39f
    const val FGS_DRAIN_PERCENT_PER_HOUR = 4.0
}
