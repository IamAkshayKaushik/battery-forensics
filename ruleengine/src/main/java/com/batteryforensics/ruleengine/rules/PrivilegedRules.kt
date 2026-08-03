package com.batteryforensics.ruleengine.rules

import com.batteryforensics.core.evidence.Confidence
import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.evidence.DiagnosticCategory
import com.batteryforensics.core.evidence.Evidence
import com.batteryforensics.core.evidence.SupportingMetric
import com.batteryforensics.core.time.TimeConstants
import com.batteryforensics.ruleengine.ForensicRule
import com.batteryforensics.ruleengine.RuleContext
import com.batteryforensics.ruleengine.RuleEvaluation

/** Doze failed to enter IDLE / stayed ACTIVE — from deviceidle dumpsys. */
class DozeFailureRule : ForensicRule {
    override val id: String = "doze_failure_to_enter"
    override val title: String = "Doze failure to enter deep idle"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val priv = context.privileged ?: return null
        val doze = priv.doze
        val idle = priv.deviceIdle
        if (doze == null && idle == null) return null

        val state = (doze?.state ?: idle?.state)?.uppercase().orEmpty()
        val deepOff = idle?.deepEnabled == false || doze?.deepEnabled == false
        val stuckActive = state in setOf("ACTIVE", "INACTIVE", "IDLE_PENDING", "SENSING", "LOCATING")
        val hints = doze?.historyHints.orEmpty()
        val noIdleHint = hints.isNotEmpty() && hints.none { it == "IDLE" || it == "LIGHT_IDLE" }

        if (!deepOff && !stuckActive && !noIdleHint) return null

        val score = when {
            deepOff && stuckActive -> 88
            deepOff || stuckActive -> 78
            else -> 68
        }
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.DOZE,
                explanation =
                    "DeviceIdle/Doze evidence shows state=${state.ifBlank { "?" }} " +
                        "(deepEnabled=${idle?.deepEnabled ?: doze?.deepEnabled}). " +
                        "Failure to reach IDLE keeps radios and apps eligible to wake — Derived from dumpsys, not Measured time-in-state.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = buildList {
                    add(
                        Evidence(
                            id = "doze_state",
                            description = "Current Doze / deviceidle state",
                            metricKey = "deviceidle_state",
                            observedValue = state.ifBlank { "unknown" },
                            threshold = "IDLE / LIGHT_IDLE expected overnight",
                            confidenceLevel = ConfidenceLevel.DERIVED,
                        ),
                    )
                    if (deepOff) {
                        add(
                            Evidence(
                                id = "deep_disabled",
                                description = "Deep Doze disabled",
                                metricKey = "deep_enabled",
                                observedValue = "false",
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                },
                supportingMetrics = listOf(
                    SupportingMetric("history_hints", "State tokens seen", doze?.historyHints?.joinToString().orEmpty()),
                ),
                counterEvidence = buildList {
                    if (state == "IDLE" || state == "LIGHT_IDLE") {
                        add(
                            Evidence(
                                id = "currently_idle",
                                description = "Device reports idle now — failure may be intermittent",
                                metricKey = "deviceidle_state",
                                observedValue = state,
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                },
                recommendedActions = listOf(
                    "Remove battery unrestricted apps that hold the device ACTIVE",
                    "Check for ignore-battery-optimizations exemptions",
                    "Capture overnight Flight Recorder + another dumpsys after screen-off 30+ min",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

/** Frequent Doze exits / maintenance thrash — Inferred from history hints. */
class FrequentDozeExitsRule : ForensicRule {
    override val id: String = "frequent_doze_exits"
    override val title: String = "Frequent Doze exits / maintenance"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val hints = context.privileged?.doze?.historyHints.orEmpty()
        if (hints.isEmpty()) return null
        val maintenance = hints.count { it.contains("MAINTENANCE", ignoreCase = true) }
        val active = hints.count { it == "ACTIVE" || it == "INACTIVE" }
        if (maintenance + active < 3) return null
        val score = (60 + (maintenance + active) * 5).coerceIn(60, 82)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.DOZE,
                explanation =
                    "Doze dump mentions maintenance/active transitions (${maintenance + active} tokens). " +
                        "Frequent exits prevent sustained deep idle — Inferred from dump tokens (not a Measured exit counter).",
                confidence = Confidence(score, ConfidenceLevel.INFERRED),
                evidence = listOf(
                    Evidence(
                        id = "doze_tokens",
                        description = "Doze history tokens suggesting exits",
                        metricKey = "doze_history_hints",
                        observedValue = hints.joinToString(),
                        confidenceLevel = ConfidenceLevel.INFERRED,
                    ),
                ),
                supportingMetrics = emptyList(),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Identify alarm/job packages waking during IDLE_MAINTENANCE",
                    "Defer sync-heavy apps overnight",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class AlarmStormRule : ForensicRule {
    override val id: String = "alarm_storm"
    override val title: String = "Alarm storm / high wakeup rate"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val alarms = context.privileged?.alarms ?: return null
        val count = alarms.wakeupAlarmCount ?: return null
        if (count < TimeConstants.ALARM_WAKEUP_STORM_THRESHOLD) return null
        val top = alarms.topPackages.take(3)
        val score = (70 + ((count - TimeConstants.ALARM_WAKEUP_STORM_THRESHOLD) / 5)).coerceIn(70, 94)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.ALARM_MANAGER,
                explanation =
                    "AlarmManager dump shows ~$count wakeup alarms" +
                        (alarms.wakeupsPerHour?.let { " (~${"%.1f".format(it)}/h)" } ?: "") +
                        (alarms.impactEstimate?.let { ". Impact: $it" } ?: "") +
                        ". RTC_WAKEUP=${alarms.rtcWakeupCount ?: "?"} ELAPSED_WAKEUP=${alarms.elapsedRealtimeWakeupCount ?: "?"}. " +
                        "High wakeup rates prevent Doze and raise idle drain. Counts are Derived from dumpsys text.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = buildList {
                    add(
                        Evidence(
                            id = "wakeup_count",
                            description = "Wakeup alarm count from dumpsys alarm",
                            metricKey = "alarm_wakeup_count",
                            observedValue = count.toString(),
                            threshold = "≥${TimeConstants.ALARM_WAKEUP_STORM_THRESHOLD}",
                            confidenceLevel = ConfidenceLevel.DERIVED,
                        ),
                    )
                    alarms.rtcWakeupCount?.let {
                        add(
                            Evidence(
                                id = "rtc_wakeup",
                                description = "RTC_WAKEUP alarms",
                                metricKey = "alarm_rtc_wakeup",
                                observedValue = it.toString(),
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                    alarms.elapsedRealtimeWakeupCount?.let {
                        add(
                            Evidence(
                                id = "elapsed_wakeup",
                                description = "ELAPSED_REALTIME_WAKEUP alarms",
                                metricKey = "alarm_elapsed_wakeup",
                                observedValue = it.toString(),
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                },
                supportingMetrics = buildList {
                    addAll(top.map {
                        SupportingMetric("pkg_${it.packageName}", it.packageName, it.count.toString(), "wakeups")
                    })
                    alarms.wakeupsPerHour?.let {
                        add(SupportingMetric("wakeups_per_hour", "Wakeups/hour", "%.2f".format(it)))
                    }
                },
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Inspect top wakeup packages and restrict background activity",
                    "Prefer inexact / non-wakeup alarms for non-critical work",
                    "Prefer ELAPSED_REALTIME over RTC_WAKEUP when wall-clock is unnecessary",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class WakeLockAbuseRule : ForensicRule {
    override val id: String = "wake_lock_abuse"
    override val title: String = "Wake lock abuse (app vs kernel)"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val wl = context.privileged?.wakeLocks ?: return null
        val total = wl.totalLocks ?: wl.appLocks ?: return null
        if (total < TimeConstants.WAKE_LOCK_ABUSE_THRESHOLD) return null
        val app = wl.appLocks
        val kernel = wl.kernelLocks
        val score = (72 + (total - TimeConstants.WAKE_LOCK_ABUSE_THRESHOLD)).coerceIn(72, 93)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.WAKE_LOCKS,
                explanation =
                    "Power dump shows ~$total wake locks (app≈${app ?: "?"}, kernel≈${kernel ?: "?"}" +
                        ", modem≈${wl.modemLocks ?: "?"}, wifi≈${wl.wifiLocks ?: "?"}, sensors≈${wl.sensorLocks ?: "?"}, HAL≈${wl.powerHalLocks ?: "?"}). " +
                        "Held locks keep the CPU awake. Taxonomy is best-effort Derived — OEM formats vary.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = buildList {
                    add(
                        Evidence(
                            id = "wl_total",
                            description = "Wake lock count",
                            metricKey = "wake_lock_count",
                            observedValue = total.toString(),
                            threshold = "≥${TimeConstants.WAKE_LOCK_ABUSE_THRESHOLD}",
                            confidenceLevel = ConfidenceLevel.DERIVED,
                        ),
                    )
                    if (app != null && kernel != null) {
                        add(
                            Evidence(
                                id = "wl_split",
                                description = "App vs kernel attribution (best-effort)",
                                metricKey = "wake_lock_split",
                                observedValue = "app=$app kernel=$kernel",
                                confidenceLevel = ConfidenceLevel.INFERRED,
                            ),
                        )
                    }
                    listOf(
                        "modem" to wl.modemLocks,
                        "wifi" to wl.wifiLocks,
                        "sensors" to wl.sensorLocks,
                        "power_hal" to wl.powerHalLocks,
                    ).forEach { (name, n) ->
                        if (n != null && n > 0) {
                            add(
                                Evidence(
                                    id = "wl_$name",
                                    description = "Wake lock taxonomy: $name",
                                    metricKey = "wake_lock_$name",
                                    observedValue = n.toString(),
                                    confidenceLevel = ConfidenceLevel.INFERRED,
                                ),
                            )
                        }
                    }
                },
                supportingMetrics = wl.topTags.take(5).map {
                    SupportingMetric("tag_${it.packageName}", it.packageName, it.count.toString())
                },
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Force-stop or restrict top wake-lock tags' packages",
                    "Check media / location / sync holders overnight",
                    "Separate modem/wifi/sensor holders from app PowerManager locks when tags allow",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class AppStandbyBypassRule : ForensicRule {
    override val id: String = "app_standby_bypass"
    override val title: String = "App Standby bypass patterns"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val usage = context.privileged?.usageStats ?: return null
        val elevated = usage.elevatedBucketPackages
        val bypass = usage.bypassPackageHints
        val hints = usage.standbyBucketHints.map { it.uppercase() }
        val hasActive = hints.any { it.contains("ACTIVE") || it.contains("WORKING_SET") }
        if (elevated.isEmpty() && bypass.isEmpty() && !hasActive) return null
        val score = when {
            bypass.isNotEmpty() -> 82
            elevated.isNotEmpty() -> 76
            else -> 64
        }
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.APP_STANDBY,
                explanation =
                    "UsageStats dump shows elevated standby buckets" +
                        (if (elevated.isNotEmpty()) " for ${elevated.take(5).joinToString()}" else "") +
                        (if (bypass.isNotEmpty()) "; bypass/exemption hints: ${bypass.take(5).joinToString()}" else "") +
                        ". Apps stuck ACTIVE/WORKING_SET or exempted bypass standby savings — Derived from dumpsys.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = buildList {
                    add(
                        Evidence(
                            id = "standby_buckets",
                            description = "Elevated App Standby buckets",
                            metricKey = "standby_bucket_hints",
                            observedValue = (elevated.ifEmpty { hints }).take(8).joinToString(),
                            confidenceLevel = ConfidenceLevel.DERIVED,
                        ),
                    )
                    if (bypass.isNotEmpty()) {
                        add(
                            Evidence(
                                id = "standby_bypass",
                                description = "Standby bypass / exemption packages",
                                metricKey = "standby_bypass_packages",
                                observedValue = bypass.take(8).joinToString(),
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                },
                supportingMetrics = usage.bucketCounts.entries.take(5).map {
                    SupportingMetric("bucket_${it.key}", it.key, it.value.toString())
                },
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Review unrestricted battery apps",
                    "Revoke unused notification listeners / accessibility that keep apps ACTIVE",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

class ForegroundServiceAbuseRule : ForensicRule {
    override val id: String = "fgs_abuse"
    override val title: String = "Foreground service drain / FGS abuse"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val fgsHints = context.privileged?.activity?.foregroundServiceHints.orEmpty()
        val screenOff = context.samples.filter { it.screenOn != true && it.isCharging != true }
        val highCurrentOff = screenOff.mapNotNull { it.currentMicroamps }.count { it < -350_000 }
        val fgApps = context.samples.mapNotNull { it.foregroundApp }.distinct()
        val drain = standbyDrainPerHour(context.samples)

        val fromDump = fgsHints.isNotEmpty()
        val fromProxy = highCurrentOff >= 3 && (drain != null && drain >= TimeConstants.FGS_DRAIN_PERCENT_PER_HOUR)
        if (!fromDump && !fromProxy) return null

        val level = if (fromDump) ConfidenceLevel.DERIVED else ConfidenceLevel.INFERRED
        val score = if (fromDump) 80 else 66
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.FOREGROUND_SERVICES,
                explanation =
                    if (fromDump) {
                        "Activity dump hints at foreground services: ${fgsHints.take(5).joinToString()}. " +
                            "Persistent FGS prevents Doze and raises standby cost — Derived."
                    } else {
                        "Screen-off high discharge (~${drain?.let { "%.1f".format(it) } ?: "?"} %/h) without dumpsys FGS tokens. " +
                            "Consistent with FGS/proxy holders — Inferred."
                    },
                confidence = Confidence(score, level),
                evidence = buildList {
                    if (fromDump) {
                        add(
                            Evidence(
                                id = "fgs_hints",
                                description = "Foreground service package hints",
                                metricKey = "fgs_packages",
                                observedValue = fgsHints.take(5).joinToString(),
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                    if (fromProxy) {
                        add(
                            Evidence(
                                id = "screen_off_current",
                                description = "High screen-off discharge samples",
                                metricKey = "screen_off_high_current_count",
                                observedValue = highCurrentOff.toString(),
                                confidenceLevel = ConfidenceLevel.INFERRED,
                            ),
                        )
                    }
                },
                supportingMetrics = fgApps.take(3).map {
                    SupportingMetric("fg_$it", "Foreground app seen", it)
                },
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Stop unused foreground services (music, tracking, VPN)",
                    "Check Settings → Apps → special app access → Battery",
                ),
                probabilityPercent = score,
            ),
        )
    }

    private fun standbyDrainPerHour(samples: List<com.batteryforensics.core.model.MonitoringSample>): Double? {
        val ordered = samples.filter { it.screenOn != true }.sortedBy { it.timestampEpochMs }
        if (ordered.size < 3) return null
        val start = ordered.first().batteryPercent ?: return null
        val end = ordered.last().batteryPercent ?: return null
        val hours = (ordered.last().timestampEpochMs - ordered.first().timestampEpochMs)
            .toDouble() / TimeConstants.MILLIS_PER_HOUR
        if (hours < 1.0) return null
        return (start - end) / hours
    }
}

/** GMS wakeup pattern — always Inferred; cite package attribution. */
class GooglePlayServicesWakeupRule : ForensicRule {
    override val id: String = "gms_wakeup_pattern"
    override val title: String = "Google Play Services wakeup pattern"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val priv = context.privileged ?: return null
        val alarmHits = priv.alarms?.topPackages.orEmpty()
            .filter { it.packageName.contains("gms", ignoreCase = true) ||
                it.packageName.contains("google", ignoreCase = true) }
        val wlHits = priv.wakeLocks?.topTags.orEmpty()
            .filter {
                it.packageName.contains("gms", ignoreCase = true) ||
                    it.packageName.contains("GoogleLocation", ignoreCase = true) ||
                    it.packageName.contains("play", ignoreCase = true)
            }
        if (alarmHits.isEmpty() && wlHits.isEmpty()) return null
        val score = 58 + (alarmHits.size + wlHits.size) * 4
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.ALARM_MANAGER,
                explanation =
                    "Alarm/wake-lock attribution mentions Google/GMS packages. " +
                        "This is Inferred correlation — GMS is often a wakeup hub, not proof of a GMS bug.",
                confidence = Confidence(score.coerceIn(55, 78), ConfidenceLevel.INFERRED),
                evidence = buildList {
                    alarmHits.take(3).forEach {
                        add(
                            Evidence(
                                id = "gms_alarm_${it.packageName}",
                                description = "Alarm package attribution",
                                metricKey = "alarm_package",
                                observedValue = "${it.packageName} × ${it.count}",
                                confidenceLevel = ConfidenceLevel.INFERRED,
                            ),
                        )
                    }
                    wlHits.take(3).forEach {
                        add(
                            Evidence(
                                id = "gms_wl_${it.packageName}",
                                description = "Wake lock tag attribution",
                                metricKey = "wakelock_tag",
                                observedValue = "${it.packageName} × ${it.count}",
                                confidenceLevel = ConfidenceLevel.INFERRED,
                            ),
                        )
                    }
                },
                supportingMetrics = emptyList(),
                counterEvidence = listOf(
                    Evidence(
                        id = "gms_caveat",
                        description = "GMS often proxies other apps' work — do not treat as sole root cause",
                        metricKey = "attribution_caveat",
                        observedValue = "Inferred only",
                        confidenceLevel = ConfidenceLevel.INFERRED,
                    ),
                ),
                recommendedActions = listOf(
                    "Check which apps use Google Play Services for location/push",
                    "Disable unused Find My Device / nearby / advertising ID features if policy allows",
                    "Compare alarm dumps after restricting suspect apps",
                ),
                probabilityPercent = score.coerceIn(55, 78),
            ),
        )
    }
}

class JobSchedulerThrashRule : ForensicRule {
    override val id: String = "jobscheduler_thrash"
    override val title: String = "JobScheduler thrash"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val jobs = context.privileged?.jobs ?: return null
        val pending = jobs.pendingJobCount ?: return null
        val running = jobs.runningJobCount ?: 0
        if (pending < TimeConstants.JOB_PENDING_THRASH_THRESHOLD && running < 8) return null
        val score = (65 + pending / 5).coerceIn(65, 90)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.JOBS,
                explanation =
                    "JobScheduler shows pending≈$pending running≈$running. " +
                        "Thrash keeps the device out of deep idle — Derived from dumpsys/cmd jobscheduler.",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = listOf(
                    Evidence(
                        id = "pending_jobs",
                        description = "Pending JobScheduler jobs",
                        metricKey = "jobs_pending",
                        observedValue = pending.toString(),
                        threshold = "≥${TimeConstants.JOB_PENDING_THRASH_THRESHOLD}",
                        confidenceLevel = ConfidenceLevel.DERIVED,
                    ),
                ),
                supportingMetrics = listOf(
                    SupportingMetric("running", "Running jobs", running.toString()),
                ),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Identify chatty sync adapters / WorkManager clients",
                    "Defer non-critical jobs to charging + unmetered",
                ),
                probabilityPercent = score,
            ),
        )
    }
}

/** Motion / location triggered Doze exits from dumpsys reason tokens. */
class DozeMotionLocationInterruptRule : ForensicRule {
    override val id: String = "doze_motion_location_interrupts"
    override val title: String = "Motion/location Doze interruptions"

    override fun evaluate(context: RuleContext): RuleEvaluation? {
        val doze = context.privileged?.doze ?: return null
        val motion = doze.motionTriggeredInterruptions
        val location = doze.locationTriggeredInterruptions
        if (motion + location < 1) return null
        val score = (70 + (motion + location) * 4).coerceIn(70, 92)
        return RuleEvaluation(
            triggered = true,
            diagnosis = Diagnosis(
                id = id,
                title = title,
                category = DiagnosticCategory.DOZE,
                explanation =
                    "DeviceIdle dump shows motion interrupts=$motion, location interrupts=$location. " +
                        "These exits prevent sustained IDLE — Derived from dumpsys reason tokens (not IMU logs).",
                confidence = Confidence(score, ConfidenceLevel.DERIVED),
                evidence = buildList {
                    if (motion > 0) {
                        add(
                            Evidence(
                                id = "motion_interrupts",
                                description = "Motion-triggered Doze exits",
                                metricKey = "doze_motion_interrupts",
                                observedValue = motion.toString(),
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                    if (location > 0) {
                        add(
                            Evidence(
                                id = "location_interrupts",
                                description = "Location-triggered Doze exits",
                                metricKey = "doze_location_interrupts",
                                observedValue = location.toString(),
                                confidenceLevel = ConfidenceLevel.DERIVED,
                            ),
                        )
                    }
                },
                supportingMetrics = listOf(
                    SupportingMetric("doze_state", "Doze state", doze.state.orEmpty()),
                ),
                counterEvidence = emptyList(),
                recommendedActions = listOf(
                    "Disable Always-on Display / pick-up gestures overnight if OEM allows",
                    "Restrict background location for non-essential apps",
                    "Re-check dumpsys deviceidle after screen-off 30+ min stationary",
                ),
                probabilityPercent = score,
            ),
        )
    }
}
