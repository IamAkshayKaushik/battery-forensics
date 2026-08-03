package com.batteryforensics.timeline

import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.core.time.TimeConstants
import kotlin.math.abs

enum class TimelineSeverity {
    INFO,
    NOTICE,
    WARNING,
    CRITICAL,
}

data class TimelineEvent(
    val timestampEpochMs: Long,
    val eventType: String,
    val title: String,
    val detail: String,
    val severity: TimelineSeverity,
)

/**
 * Privileged dumpsys-derived hints for timeline ingest.
 * Kept as a lightweight DTO so :timeline does not depend on :parser.
 */
data class PrivilegedTimelineInput(
    val referenceEpochMs: Long = 0L,
    val dozeState: String? = null,
    val dozeHistoryHints: List<String> = emptyList(),
    val motionInterruptions: Int = 0,
    val locationInterruptions: Int = 0,
    val alarmWakeups: Int? = null,
    val topAlarmPackages: List<String> = emptyList(),
    val gmsWakeupHints: List<String> = emptyList(),
)

/**
 * Builds a chronological flight-recorder style event log.
 * Emits meaningful transitions only — not every sample.
 */
object TimelineBuilder {

    fun build(
        samples: List<MonitoringSample>,
        diagnoses: List<Diagnosis> = emptyList(),
        privileged: PrivilegedTimelineInput? = null,
    ): List<TimelineEvent> {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        if (ordered.isEmpty()) return emptyList()

        val events = mutableListOf<TimelineEvent>()
        events += TimelineEvent(
            timestampEpochMs = ordered.first().timestampEpochMs,
            eventType = "WINDOW_START",
            title = "Investigation window opened",
            detail = "${ordered.size} samples in window",
            severity = TimelineSeverity.INFO,
        )

        var prev = ordered.first()
        for (i in 1 until ordered.size) {
            val curr = ordered[i]
            maybeChargeTransition(prev, curr)?.let { events += it }
            maybeScreenTransition(prev, curr)?.let { events += it }
            maybeBatteryStep(prev, curr)?.let { events += it }
            maybeThermalSpike(prev, curr)?.let { events += it }
            maybeNetworkChange(prev, curr)?.let { events += it }
            maybeWifiChange(prev, curr)?.let { events += it }
            prev = curr
        }

        events += privilegedEvents(privileged, ordered.last().timestampEpochMs)

        diagnoses.forEach { d ->
            val ts = ordered.last().timestampEpochMs
            events += TimelineEvent(
                timestampEpochMs = ts,
                eventType = "RULE_TRIGGER",
                title = d.title,
                detail = "${d.probabilityPercent}% · ${d.confidence.starsLabel}",
                severity = when {
                    d.probabilityPercent >= 85 -> TimelineSeverity.CRITICAL
                    d.probabilityPercent >= 70 -> TimelineSeverity.WARNING
                    else -> TimelineSeverity.NOTICE
                },
            )
        }

        events += TimelineEvent(
            timestampEpochMs = ordered.last().timestampEpochMs,
            eventType = "WINDOW_END",
            title = "Investigation window closed",
            detail = "Latest battery ${ordered.last().batteryPercent ?: "—"}%",
            severity = TimelineSeverity.INFO,
        )

        return events.sortedBy { it.timestampEpochMs }
    }

    private fun privilegedEvents(
        privileged: PrivilegedTimelineInput?,
        fallbackTs: Long,
    ): List<TimelineEvent> {
        if (privileged == null) return emptyList()
        val ts = privileged.referenceEpochMs.takeIf { it > 0 } ?: fallbackTs
        return buildList {
            if (!privileged.dozeState.isNullOrBlank() || privileged.dozeHistoryHints.isNotEmpty()) {
                add(
                    TimelineEvent(
                        timestampEpochMs = ts,
                        eventType = "DOZE_STATE",
                        title = "Doze state ${privileged.dozeState ?: "unknown"}",
                        detail = "Hints: ${privileged.dozeHistoryHints.joinToString().ifBlank { "—" }}",
                        severity = if (privileged.dozeState.equals("ACTIVE", true)) {
                            TimelineSeverity.WARNING
                        } else {
                            TimelineSeverity.NOTICE
                        },
                    ),
                )
            }
            if (privileged.motionInterruptions > 0) {
                add(
                    TimelineEvent(
                        timestampEpochMs = ts,
                        eventType = "DOZE_MOTION_INTERRUPT",
                        title = "Motion-triggered Doze exits",
                        detail = "${privileged.motionInterruptions} motion interrupt hint(s) from dumpsys",
                        severity = TimelineSeverity.WARNING,
                    ),
                )
            }
            if (privileged.locationInterruptions > 0) {
                add(
                    TimelineEvent(
                        timestampEpochMs = ts,
                        eventType = "DOZE_LOCATION_INTERRUPT",
                        title = "Location-triggered Doze exits",
                        detail = "${privileged.locationInterruptions} location interrupt hint(s) from dumpsys",
                        severity = TimelineSeverity.WARNING,
                    ),
                )
            }
            privileged.alarmWakeups?.let { count ->
                add(
                    TimelineEvent(
                        timestampEpochMs = ts,
                        eventType = "ALARM_WAKEUPS",
                        title = "Alarm wakeups ~$count",
                        detail = "Top: ${privileged.topAlarmPackages.take(3).joinToString().ifBlank { "—" }}",
                        severity = if (count >= 40) TimelineSeverity.WARNING else TimelineSeverity.NOTICE,
                    ),
                )
            }
            if (privileged.gmsWakeupHints.isNotEmpty()) {
                add(
                    TimelineEvent(
                        timestampEpochMs = ts,
                        eventType = "GMS_WAKEUP",
                        title = "GMS wakeup attribution",
                        detail = privileged.gmsWakeupHints.take(5).joinToString(),
                        severity = TimelineSeverity.NOTICE,
                    ),
                )
            }
        }
    }

    /** Overnight drain replay: screen-off heavy window condensed to meaningful events. */
    fun overnightReplay(samples: List<MonitoringSample>): List<TimelineEvent> {
        val ordered = samples.sortedBy { it.timestampEpochMs }
        if (ordered.size < 2) return emptyList()
        val screenOff = ordered.filter { it.screenOn != true }
        val base = if (screenOff.size >= ordered.size * 0.7) screenOff else ordered
        val events = build(base)
        val startPct = base.first().batteryPercent
        val endPct = base.last().batteryPercent
        val hours = (base.last().timestampEpochMs - base.first().timestampEpochMs)
            .toDouble() / TimeConstants.MILLIS_PER_HOUR
        if (startPct != null && endPct != null && hours > 0) {
            val rate = (startPct - endPct) / hours
            return listOf(
                TimelineEvent(
                    timestampEpochMs = base.first().timestampEpochMs,
                    eventType = "OVERNIGHT_REPLAY",
                    title = "Overnight drain replay",
                    detail = "$startPct% → $endPct% over ${"%.1f".format(hours)}h (${"%.2f".format(rate)}%/h)",
                    severity = if (rate >= TimeConstants.OVERNIGHT_STANDBY_DRAIN_PERCENT_PER_HOUR) {
                        TimelineSeverity.WARNING
                    } else {
                        TimelineSeverity.INFO
                    },
                ),
            ) + events.filter { it.eventType != "WINDOW_START" && it.eventType != "WINDOW_END" }
        }
        return events
    }

    private fun maybeChargeTransition(prev: MonitoringSample, curr: MonitoringSample): TimelineEvent? {
        if (prev.isCharging == curr.isCharging) return null
        val on = curr.isCharging == true
        return TimelineEvent(
            timestampEpochMs = curr.timestampEpochMs,
            eventType = if (on) "CHARGE_START" else "CHARGE_STOP",
            title = if (on) "Charging started" else "Charging stopped",
            detail = "Plug=${curr.chargePlug ?: "—"} · ${curr.batteryPercent ?: "—"}%",
            severity = TimelineSeverity.NOTICE,
        )
    }

    private fun maybeScreenTransition(prev: MonitoringSample, curr: MonitoringSample): TimelineEvent? {
        if (prev.screenOn == curr.screenOn) return null
        val on = curr.screenOn == true
        return TimelineEvent(
            timestampEpochMs = curr.timestampEpochMs,
            eventType = if (on) "SCREEN_ON" else "SCREEN_OFF",
            title = if (on) "Screen on" else "Screen off",
            detail = "Brightness ${curr.brightnessPercent ?: "—"}% · ${curr.refreshRateHz ?: "—"} Hz",
            severity = TimelineSeverity.INFO,
        )
    }

    private fun maybeBatteryStep(prev: MonitoringSample, curr: MonitoringSample): TimelineEvent? {
        val a = prev.batteryPercent ?: return null
        val b = curr.batteryPercent ?: return null
        if (abs(a - b) < 2) return null
        return TimelineEvent(
            timestampEpochMs = curr.timestampEpochMs,
            eventType = "BATTERY_STEP",
            title = "Battery $a% → $b%",
            detail = "Δ=${b - a}%",
            severity = if (a - b >= 5) TimelineSeverity.WARNING else TimelineSeverity.NOTICE,
        )
    }

    private fun maybeThermalSpike(prev: MonitoringSample, curr: MonitoringSample): TimelineEvent? {
        val t0 = prev.temperatureC ?: return null
        val t1 = curr.temperatureC ?: return null
        val delta = t1 - t0
        if (delta < 2f && t1 < TimeConstants.ELEVATED_TEMP_C) return null
        if (delta < TimeConstants.RAPID_HEAT_DELTA_C && t1 < TimeConstants.ELEVATED_TEMP_C) return null
        return TimelineEvent(
            timestampEpochMs = curr.timestampEpochMs,
            eventType = "THERMAL",
            title = "Temperature ${"%.1f".format(t1)}°C",
            detail = "Δ ${"%+.1f".format(delta)}°C from prior sample",
            severity = if (t1 >= TimeConstants.ELEVATED_TEMP_C) TimelineSeverity.WARNING else TimelineSeverity.NOTICE,
        )
    }

    private fun maybeNetworkChange(prev: MonitoringSample, curr: MonitoringSample): TimelineEvent? {
        if (prev.networkType == null || curr.networkType == null) return null
        if (prev.networkType == curr.networkType) return null
        return TimelineEvent(
            timestampEpochMs = curr.timestampEpochMs,
            eventType = "NETWORK_TRANSITION",
            title = "Network ${prev.networkType} → ${curr.networkType}",
            detail = "Cellular RSSI ${curr.cellularRssiDbm ?: "—"} dBm",
            severity = TimelineSeverity.NOTICE,
        )
    }

    private fun maybeWifiChange(prev: MonitoringSample, curr: MonitoringSample): TimelineEvent? {
        if (prev.wifiConnected == curr.wifiConnected) return null
        val on = curr.wifiConnected == true
        return TimelineEvent(
            timestampEpochMs = curr.timestampEpochMs,
            eventType = if (on) "WIFI_CONNECT" else "WIFI_DISCONNECT",
            title = if (on) "Wi-Fi connected" else "Wi-Fi disconnected",
            detail = "RSSI ${curr.wifiRssiDbm ?: "—"} dBm",
            severity = TimelineSeverity.INFO,
        )
    }
}
