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
    /** Extreme ΔT distinct from mild rapid heat — thermal runaway-ish. */
    const val THERMAL_RUNAWAY_DELTA_C = 12f
    const val THERMAL_RUNAWAY_TEMP_C = 48f
    const val ALARM_WAKEUP_STORM_THRESHOLD = 40
    const val WAKE_LOCK_ABUSE_THRESHOLD = 8
    const val JOB_PENDING_THRASH_THRESHOLD = 25
    const val HIGH_REFRESH_DEDICATED_HZ = 120f
    const val INEFFICIENT_CHARGE_CURRENT_UA = 500_000
    const val STORAGE_LOW_PERCENT = 8f
    const val WORKMANAGER_MIN_PERIOD_MS = 15 * MILLIS_PER_MINUTE
    /** Fine Flight Recorder floor (user preference may be higher). */
    const val FLIGHT_RECORDER_MIN_INTERVAL_MS = 5_000L
}
