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
    val notes: List<String> = emptyList(),
)

data class UsageStatsSummary(
    val standbyBucketHints: List<String>,
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
