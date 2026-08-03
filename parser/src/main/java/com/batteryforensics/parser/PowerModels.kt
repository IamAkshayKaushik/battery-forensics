package com.batteryforensics.parser

data class AlarmSummary(
    val wakeupAlarmCount: Int?,
    val topPackages: List<PackageCount>,
    val notes: List<String> = emptyList(),
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
)

data class DozeTimelineSummary(
    val state: String?,
    val deepEnabled: Boolean?,
    val lightEnabled: Boolean?,
    val historyHints: List<String>,
    val notes: List<String> = emptyList(),
)
