package com.batteryforensics.ruleengine

import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.parser.ActivitySummary
import com.batteryforensics.parser.AlarmSummary
import com.batteryforensics.parser.BatteryStatsSummary
import com.batteryforensics.parser.CmdBatterySummary
import com.batteryforensics.parser.DeviceIdleSummary
import com.batteryforensics.parser.DozeTimelineSummary
import com.batteryforensics.parser.JobSchedulerSummary
import com.batteryforensics.parser.PowerSummary
import com.batteryforensics.parser.ThermalServiceSummary
import com.batteryforensics.parser.UsageStatsSummary
import com.batteryforensics.parser.WakeLockSummary

/**
 * Forensic rule contract. Every rule must produce evidence, confidence,
 * explanation, and recommendations — never opaque scores alone.
 */
interface ForensicRule {
    val id: String
    val title: String

    fun evaluate(context: RuleContext): RuleEvaluation?
}

/**
 * Typed privileged dumpsys evidence for rules. Never raw dump text.
 * Populated when Shizuku (or equivalent) collection succeeds.
 */
data class PrivilegedEvidence(
    val batteryStats: BatteryStatsSummary? = null,
    val power: PowerSummary? = null,
    val wakeLocks: WakeLockSummary? = null,
    val deviceIdle: DeviceIdleSummary? = null,
    val doze: DozeTimelineSummary? = null,
    val alarms: AlarmSummary? = null,
    val jobs: JobSchedulerSummary? = null,
    val usageStats: UsageStatsSummary? = null,
    val thermalService: ThermalServiceSummary? = null,
    val activity: ActivitySummary? = null,
    val cmdBattery: CmdBatterySummary? = null,
    val collectionErrors: List<String> = emptyList(),
) {
    val hasAnyData: Boolean
        get() = batteryStats != null || power != null || wakeLocks != null ||
            deviceIdle != null || doze != null || alarms != null ||
            jobs != null || usageStats != null || thermalService != null ||
            activity != null || cmdBattery != null
}

data class RuleContext(
    val samples: List<MonitoringSample>,
    val nowEpochMs: Long = System.currentTimeMillis(),
    /** Privileged dumpsys summaries — Measured/Derived from parsers, never UI strings. */
    val privileged: PrivilegedEvidence? = null,
    /**
     * Earlier window for regression / baseline anomaly rules (Inferred).
     * Typically a prior healthy night or previous day of samples.
     */
    val baselineSamples: List<MonitoringSample> = emptyList(),
)

data class RuleEvaluation(
    val triggered: Boolean,
    val diagnosis: Diagnosis,
)
