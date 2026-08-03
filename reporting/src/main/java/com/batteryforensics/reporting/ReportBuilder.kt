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
)

class ReportBuilder {
    fun build(
        device: DeviceInfo,
        samples: List<MonitoringSample>,
        investigation: InvestigationResult,
        unknownFactors: List<String> = defaultUnknowns(samples),
    ): ForensicReport = ForensicReport(
        generatedAtEpochMs = investigation.evaluatedAtEpochMs,
        device = device,
        sampleCount = samples.size,
        samples = samples,
        diagnoses = investigation.diagnoses,
        unknownFactors = unknownFactors,
    )

    private fun defaultUnknowns(samples: List<MonitoringSample>): List<String> = buildList {
        if (samples.none { it.cellularRssiDbm != null }) {
            add("Cellular RSSI unavailable without additional permissions or Shizuku")
        }
        if (samples.size < 10) {
            add("Short observation window — capture more Flight Recorder data for stronger confidence")
        }
        add("Wake locks / AlarmManager / JobScheduler require dumpsys (optional Shizuku path)")
    }
}
