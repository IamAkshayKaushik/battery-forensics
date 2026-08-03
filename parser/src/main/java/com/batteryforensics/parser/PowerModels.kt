package com.batteryforensics.parser

data class AlarmSummary(
    val wakeupAlarmCount: Int?,
    val topPackages: List<PackageCount>,
    val notes: List<String> = emptyList(),
    /** Measured/Derived count of RTC_WAKEUP alarms when dump exposes it. */
    val rtcWakeupCount: Int? = null,
    /** Measured/Derived count of ELAPSED_REALTIME_WAKEUP alarms. */
    val elapsedRealtimeWakeupCount: Int? = null,
    /** Derived wakeups/hour when dump uptime is available. */
    val wakeupsPerHour: Double? = null,
    /** Human-readable impact estimate (Derived). */
    val impactEstimate: String? = null,
)

data class PackageCount(
    val packageName: String,
    val count: Int,
)

data class JobSchedulerSummary(
    val pendingJobCount: Int?,
    val runningJobCount: Int? = null,
    val notes: List<String> = emptyList(),
)

data class UsageStatsSummary(
    val standbyBucketHints: List<String>,
    /** Packages hinted as ACTIVE / WORKING_SET while screen-off drain is high. */
    val elevatedBucketPackages: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    /** Counts per App Standby bucket name (ACTIVE…RESTRICTED). */
    val bucketCounts: Map<String, Int> = emptyMap(),
    /** Packages with exemption / bypass hints in dumpsys. */
    val bypassPackageHints: List<String> = emptyList(),
)

/** Best-effort `dumpsys activity` stub summary. */
data class ActivitySummary(
    val foregroundServiceHints: List<String> = emptyList(),
    val topResumedActivity: String? = null,
    val notes: List<String> = emptyList(),
)

/** Best-effort `cmd battery` stub summary. */
data class CmdBatterySummary(
    val statusLine: String? = null,
    val level: Int? = null,
    val temperatureTenthsC: Int? = null,
    val notes: List<String> = emptyList(),
)

data class ThermalServiceSummary(
    val currentStatus: String?,
    val notes: List<String> = emptyList(),
)

data class WakeLockSummary(
    val totalLocks: Int?,
    val appLocks: Int?,
    val kernelLocks: Int?,
    val topTags: List<PackageCount>,
    val notes: List<String> = emptyList(),
    val modemLocks: Int? = null,
    val wifiLocks: Int? = null,
    val sensorLocks: Int? = null,
    val powerHalLocks: Int? = null,
    val driverLocks: Int? = null,
    /** Taxonomy caveats — OEM dump formats vary. */
    val taxonomyNotes: List<String> = emptyList(),
)

data class DozeStateTransition(
    val fromState: String?,
    val toState: String,
    /** MOTION / LOCATION / ALARM / UNKNOWN when dump reason is present. */
    val reasonHint: String? = null,
)

data class DozeTimelineSummary(
    val state: String?,
    val deepEnabled: Boolean?,
    val lightEnabled: Boolean?,
    val historyHints: List<String>,
    val notes: List<String> = emptyList(),
    /** Distinct known Doze states observed in the dump. */
    val observedStates: List<String> = emptyList(),
    val transitions: List<DozeStateTransition> = emptyList(),
    val motionTriggeredInterruptions: Int = 0,
    val locationTriggeredInterruptions: Int = 0,
)
