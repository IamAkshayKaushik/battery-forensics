package com.batteryforensics.ai

import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.evidence.DiagnosticCategory
import com.batteryforensics.reporting.ForensicReport

/**
 * Builds a Markdown diagnostic report suitable for pasting into an LLM.
 * The rule engine already produced conclusions — the LLM is an assistant, not the engine.
 */
class AiReportGenerator {
    fun toMarkdown(report: ForensicReport): String = buildString {
        appendLine("# Battery Forensics — AI Diagnostic Report")
        appendLine()
        appendLine("> Generated locally. No data left the device. LLMs are assistants — not the diagnostic engine.")
        appendLine()

        appendLine("## Executive Summary")
        appendLine()
        if (report.diagnoses.isEmpty()) {
            appendLine("No high-confidence root causes triggered on the current evidence window (${report.sampleCount} samples).")
            appendLine("This is not a clean bill of health — it means evidence was insufficient or thresholds were not met.")
        } else {
            appendLine("Ranked ${report.diagnoses.size} plausible root cause(s) from ${report.sampleCount} monitoring samples.")
            report.diagnoses.take(5).forEachIndexed { index, d ->
                appendLine("${index + 1}. **${d.title}** — ${d.probabilityPercent}% probability (${d.confidence.starsLabel})")
            }
        }
        appendLine()

        appendLine("## Battery Overview")
        appendLine()
        val ordered = report.samples.sortedBy { it.timestampEpochMs }
        if (ordered.isEmpty()) {
            appendLine("_No samples._")
        } else {
            val startPct = ordered.first().batteryPercent
            val endPct = ordered.last().batteryPercent
            val temps = ordered.mapNotNull { it.temperatureC }
            val currents = ordered.mapNotNull { it.currentMicroamps }
            appendLine("- Samples: ${ordered.size}")
            if (startPct != null && endPct != null) {
                appendLine("- Battery: $startPct% → $endPct% (Δ ${endPct - startPct})")
            }
            if (temps.isNotEmpty()) {
                appendLine("- Temperature: ${"%.1f".format(temps.min())}–${"%.1f".format(temps.max())}°C")
            }
            if (currents.isNotEmpty()) {
                appendLine("- Current span: ${currents.min()}…${currents.max()} µA")
            }
            ordered.lastOrNull()?.chargeCounterMah?.let { appendLine("- Latest charge counter: $it mAh") }
        }
        appendLine()

        appendLine("## Device Info")
        appendLine()
        appendLine("- Manufacturer: ${report.device.manufacturer}")
        appendLine("- Model: ${report.device.model}")
        appendLine("- Android: ${report.device.androidVersion} (SDK ${report.device.sdkInt})")
        report.device.batteryCapacityMah?.let { appendLine("- Rated capacity: $it mAh") }
        appendLine()

        appendLine("## Timeline")
        appendLine()
        if (report.timelineNotes.isEmpty()) {
            appendLine("Sample window: ${report.samples.size} points.")
        } else {
            report.timelineNotes.forEach { appendLine("- $it") }
        }
        appendLine()

        appendLine("## Historical Stats")
        appendLine()
        if (report.historicalNotes.isEmpty()) {
            appendLine("Compare with a prior healthy window via Differential Analysis / baseline samples.")
        } else {
            report.historicalNotes.forEach { appendLine("- $it") }
        }
        appendLine()

        appendLine("## Evidence")
        appendLine()
        report.diagnoses.forEach { d ->
            appendLine("### ${d.title}")
            appendLine()
            appendLine(d.explanation)
            appendLine()
            appendLine("**Category:** ${d.category}")
            appendLine()
            d.evidence.forEach { e ->
                appendLine(
                    "- [${e.confidenceLevel}] ${e.description}: ${e.observedValue}" +
                        (e.threshold?.let { " (threshold $it)" } ?: ""),
                )
            }
            if (d.supportingMetrics.isNotEmpty()) {
                appendLine()
                appendLine("Supporting metrics:")
                d.supportingMetrics.forEach { m ->
                    appendLine("- ${m.label}: ${m.value}${m.unit?.let { " $it" } ?: ""}")
                }
            }
            if (d.counterEvidence.isNotEmpty()) {
                appendLine()
                appendLine("Counter-evidence:")
                d.counterEvidence.forEach { e ->
                    appendLine("- [${e.confidenceLevel}] ${e.description}: ${e.observedValue}")
                }
            }
            appendLine()
        }
        if (report.diagnoses.isEmpty()) appendLine("_No triggered diagnoses._")
        appendLine()

        appendLine("## Confidence")
        appendLine()
        appendLine("| Cause | Score | Level |")
        appendLine("| --- | --- | --- |")
        report.diagnoses.forEach { d ->
            appendLine("| ${d.title} | ${d.confidence.scorePercent}% | ${d.confidence.starsLabel} |")
        }
        appendLine()

        appendLine("## Measured / Derived / Inferred")
        appendLine()
        fun bucket(level: ConfidenceLevel) =
            report.diagnoses.filter { it.confidence.level == level }.joinToString { it.title }
                .ifBlank { "_(none)_" }
        appendLine("- **Measured ★★★★★:** ${bucket(ConfidenceLevel.MEASURED)}")
        appendLine("- **Derived ★★★★☆:** ${bucket(ConfidenceLevel.DERIVED)}")
        appendLine("- **Inferred ★★★☆☆:** ${bucket(ConfidenceLevel.INFERRED)}")
        appendLine("- **Speculative ★☆☆☆☆:** ${bucket(ConfidenceLevel.SPECULATIVE)}")
        appendLine()

        appendLine("## Charts refs")
        appendLine()
        report.chartRefs.forEach { appendLine("- $it") }
        appendLine()

        fun sectionFor(category: DiagnosticCategory, title: String) {
            appendLine("## $title")
            appendLine()
            val hits = report.diagnoses.filter { it.category == category }
            if (hits.isEmpty()) {
                appendLine("_No rules in this category triggered._")
            } else {
                hits.forEach { d ->
                    appendLine("- **${d.title}** (${d.probabilityPercent}% · ${d.confidence.starsLabel})")
                    appendLine("  - ${d.explanation}")
                }
            }
            appendLine()
        }
        sectionFor(DiagnosticCategory.BATTERY_CHEMISTRY, "Chemistry")
        sectionFor(DiagnosticCategory.THERMAL, "Thermal")
        sectionFor(DiagnosticCategory.NETWORK, "Network")
        sectionFor(DiagnosticCategory.WAKE_LOCKS, "Wake locks")
        sectionFor(DiagnosticCategory.ALARM_MANAGER, "Alarms")
        sectionFor(DiagnosticCategory.DOZE, "Doze")
        sectionFor(DiagnosticCategory.FOREGROUND_SERVICES, "Apps")
        // Also list APP_STANDBY under Apps
        val appsExtra = report.diagnoses.filter {
            it.category == DiagnosticCategory.APP_STANDBY || it.category == DiagnosticCategory.JOBS
        }
        if (appsExtra.isNotEmpty()) {
            appsExtra.forEach { d ->
                appendLine("- **${d.title}** (${d.probabilityPercent}% · ${d.confidence.starsLabel})")
            }
            appendLine()
        }

        appendLine("## Shizuku Findings")
        appendLine()
        if (report.privilegedFindings.isEmpty()) {
            appendLine("_No privileged dumpsys findings attached to this export (Shizuku unused or unavailable)._")
        } else {
            report.privilegedFindings.forEach { appendLine("- $it") }
        }
        appendLine()

        appendLine("## Differential")
        appendLine()
        report.differentialNotes.forEach { appendLine("- $it") }
        appendLine()

        appendLine("## Root Cause Ranking")
        appendLine()
        appendLine("| Rank | Cause | Probability | Confidence | Category |")
        appendLine("| --- | --- | --- | --- | --- |")
        report.diagnoses.forEachIndexed { i, d ->
            appendLine("| ${i + 1} | ${d.title} | ${d.probabilityPercent}% | ${d.confidence.starsLabel} | ${d.category} |")
        }
        appendLine()

        appendLine("## Recommendations")
        appendLine()
        val actions = report.diagnoses.flatMap { d -> d.recommendedActions.map { d.title to it } }.distinctBy { it.second }
        if (actions.isEmpty()) {
            appendLine("_Collect more evidence (Flight Recorder overnight + Shizuku dumpsys)._")
        } else {
            actions.forEach { (cause, action) -> appendLine("- ($cause) $action") }
        }
        appendLine()

        appendLine("## Unknown Factors")
        appendLine()
        report.unknownFactors.forEach { appendLine("- $it") }
        appendLine()

        appendLine("## Supporting Metrics Snapshot")
        appendLine()
        val latest = report.samples.maxByOrNull { it.timestampEpochMs }
        if (latest == null) {
            appendLine("_No samples available._")
        } else {
            appendLine("- Battery: ${latest.batteryPercent}%")
            appendLine("- Voltage: ${latest.voltageMv} mV")
            appendLine("- Current: ${latest.currentMicroamps} µA")
            appendLine("- Charge current: ${latest.chargingCurrentMicroamps} µA")
            appendLine("- Temperature: ${latest.temperatureC}°C")
            appendLine("- Charging: ${latest.isCharging} (${latest.chargePlug})")
            appendLine("- Screen on: ${latest.screenOn} · brightness ${latest.brightnessPercent}% · ${latest.refreshRateHz} Hz")
            appendLine("- Thermal status: ${latest.thermalStatus}")
            appendLine("- Wi-Fi: ${latest.wifiConnected} (${latest.wifiRssiDbm} dBm) · hotspot=${latest.hotspotOn}")
            appendLine("- Cellular: ${latest.cellularRssiDbm} dBm (${latest.networkType}) carrier=${latest.carrierName} cell=${latest.cellId}")
            appendLine("- BT on/connected: ${latest.bluetoothOn}/${latest.bluetoothConnected}")
            appendLine("- Location/NFC: ${latest.locationEnabled}/${latest.nfcEnabled}")
            appendLine("- Foreground: ${latest.foregroundApp}")
            appendLine("- Memory: ${latest.memoryPressure} · storage free ${latest.storageFreePercent}%")
            appendLine("- Orientation: ${latest.orientation}")
        }
        appendLine()

        appendLine("## Raw appendix")
        appendLine()
        appendLine("- Sample count: ${report.sampleCount}")
        appendLine("- Generated at epoch ms: ${report.generatedAtEpochMs}")
        appendLine("- Diagnosis ids: ${report.diagnoses.joinToString { it.id }}")
        appendLine("- Full JSON/CSV/SQL accompany this Markdown in ZIP exports.")
        appendLine()

        appendLine("## LLM Instruction Block")
        appendLine()
        appendLine(
            """
            | You are assisting with an Android battery forensics report produced by Battery Forensics.
            | Treat Measured evidence as highest priority, then Derived, then Inferred. Never upgrade Speculative claims to facts.
            | Do not invent dumpsys fields that are not present. Prefer asking for missing evidence over guessing.
            | Never claim measured RRC / modem state machines unless explicitly present in the report.
            | Suggest concrete next measurements (Flight Recorder, Shizuku dumpsys) when confidence is limited.
            | Rank multiple plausible causes; do not collapse to a single unverified culprit.
            | Privacy: this report was generated on-device; do not assume cloud telemetry exists.
            |
            | After reviewing the evidence above, estimate probabilities (0–100%, summing need not be 100) for:
            | 1. Hardware issue (board/PMIC/antenna/thermal design)
            | 2. Software / app misbehavior
            | 3. Battery degraded (chemistry / high Ri / capacity loss)
            | 4. Modem / radio responsible
            | 5. Rogue app (FGS / alarms / wake locks)
            | 6. Android / OEM bug or regression
            | 7. Battery replacement would help (yes/no + confidence)
            | Cite which report sections support each estimate. Label each as Measured/Derived/Inferred/Speculative.
            """.trimMargin(),
        )
        appendLine()
        appendLine("---")
        appendLine("_Don't guess. Investigate._")
    }
}
