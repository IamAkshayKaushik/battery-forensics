package com.batteryforensics.reporting

import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.model.DeviceInfo
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.diagnostics.InvestigationResult
import kotlinx.serialization.Serializable

@Serializable
data class ForensicReport(
    val generatedAtEpochMs: Long,
    val device: DeviceInfo,
    val sampleCount: Int,
    val samples: List<MonitoringSample>,
    val diagnoses: List<Diagnosis>,
    val unknownFactors: List<String>,
    val privilegedFindings: List<String> = emptyList(),
    val timelineNotes: List<String> = emptyList(),
    val historicalNotes: List<String> = emptyList(),
    val differentialNotes: List<String> = emptyList(),
    val chartRefs: List<String> = emptyList(),
)

class ReportBuilder {
    fun build(
        device: DeviceInfo,
        samples: List<MonitoringSample>,
        investigation: InvestigationResult,
        unknownFactors: List<String> = defaultUnknowns(samples, investigation.privilegedUsed),
        privilegedFindings: List<String> = emptyList(),
        timelineNotes: List<String> = defaultTimeline(samples),
        historicalNotes: List<String> = emptyList(),
        differentialNotes: List<String> = listOf(
            "Use in-app Differential Analysis: pick Healthy vs Problem overnight windows (22:00–08:00).",
        ),
        chartRefs: List<String> = listOf(
            "In-app: Live Monitor Vico charts (battery %, temperature, cellular RSSI)",
            "In-app: MetricSparkline available for compact teasers",
            "In-app: Timeline / overnight replay for event markers",
        ),
    ): ForensicReport = ForensicReport(
        generatedAtEpochMs = investigation.evaluatedAtEpochMs,
        device = device,
        sampleCount = samples.size,
        samples = samples,
        diagnoses = investigation.diagnoses,
        unknownFactors = unknownFactors,
        privilegedFindings = privilegedFindings,
        timelineNotes = timelineNotes,
        historicalNotes = historicalNotes,
        differentialNotes = differentialNotes,
        chartRefs = chartRefs,
    )

    private fun defaultTimeline(samples: List<MonitoringSample>): List<String> {
        if (samples.isEmpty()) return listOf("No samples in window")
        val ordered = samples.sortedBy { it.timestampEpochMs }
        return listOf(
            "Window ${ordered.first().timestampEpochMs} → ${ordered.last().timestampEpochMs} (${samples.size} samples)",
            "Replay meaningful events inside Timeline / overnight replay — not every sample row.",
        )
    }

    private fun defaultUnknowns(samples: List<MonitoringSample>, privilegedUsed: Boolean): List<String> = buildList {
        if (samples.none { it.cellularRssiDbm != null }) {
            add("Cellular RSSI unavailable without location permission or privileged APIs")
        }
        if (samples.none { it.bluetoothOn != null }) {
            add("Bluetooth state unavailable (needs BLUETOOTH_CONNECT on API 31+)")
        }
        if (samples.none { it.cellularBand != null }) {
            add("Cellular band unavailable without OEM/privileged APIs")
        }
        if (samples.size < 10) {
            add("Short observation window — capture more Flight Recorder data for stronger confidence")
        }
        if (!privilegedUsed) {
            add("Wake locks / AlarmManager / JobScheduler / Doze depth require dumpsys (optional Shizuku path)")
        }
        add("Sensor HAL drain (IMU continuous) is not sampled — skipped unless OEM dumpsys provides it")
        add("Never treat dumpsys-inferred RRC / modem state machines as Measured")
    }
}
