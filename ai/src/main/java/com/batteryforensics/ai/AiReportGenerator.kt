package com.batteryforensics.ai

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
        appendLine("## Device Information")
        appendLine()
        appendLine("- Manufacturer: ${report.device.manufacturer}")
        appendLine("- Model: ${report.device.model}")
        appendLine("- Android: ${report.device.androidVersion} (SDK ${report.device.sdkInt})")
        report.device.batteryCapacityMah?.let { appendLine("- Rated capacity: ${it} mAh") }
        appendLine()
        appendLine("## Investigation Window")
        appendLine()
        val ordered = report.samples.sortedBy { it.timestampEpochMs }
        if (ordered.isEmpty()) {
            appendLine("_No samples._")
        } else {
            appendLine("- Start (epoch ms): ${ordered.first().timestampEpochMs}")
            appendLine("- End (epoch ms): ${ordered.last().timestampEpochMs}")
            appendLine("- Sample count: ${ordered.size}")
            val startPct = ordered.first().batteryPercent
            val endPct = ordered.last().batteryPercent
            if (startPct != null && endPct != null) {
                appendLine("- Battery: $startPct% → $endPct% (Δ ${endPct - startPct})")
            }
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
            appendLine("**Confidence:** ${d.confidence.scorePercent}% — ${d.confidence.starsLabel}")
            appendLine()
            appendLine("Supporting evidence:")
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
            appendLine("Recommended actions:")
            d.recommendedActions.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("## Root Cause Ranking")
        appendLine()
        appendLine("| Rank | Cause | Probability | Confidence | Category |")
        appendLine("| --- | --- | --- | --- | --- |")
        report.diagnoses.forEachIndexed { i, d ->
            appendLine("| ${i + 1} | ${d.title} | ${d.probabilityPercent}% | ${d.confidence.starsLabel} | ${d.category} |")
        }
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
            appendLine("- Charge counter: ${latest.chargeCounterMah} mAh")
            appendLine("- Temperature: ${latest.temperatureC}°C")
            appendLine("- Charging: ${latest.isCharging} (${latest.chargePlug})")
            appendLine("- Screen on: ${latest.screenOn}")
            appendLine("- Brightness: ${latest.brightnessPercent}%")
            appendLine("- Refresh: ${latest.refreshRateHz} Hz")
            appendLine("- Thermal status: ${latest.thermalStatus}")
            appendLine("- Wi-Fi connected: ${latest.wifiConnected} (RSSI ${latest.wifiRssiDbm})")
            appendLine("- Cellular RSSI: ${latest.cellularRssiDbm} (${latest.networkType})")
        }
        appendLine()
        appendLine("## Historical / Differential Notes")
        appendLine()
        appendLine("Use in-app Differential Analysis to compare a healthy night vs a problem night.")
        appendLine("Largest deviations typically appear in wakeups, deep-sleep proxy, radio activity, signal, temperature, and battery loss.")
        appendLine()
        appendLine("## Unknown Factors")
        appendLine()
        report.unknownFactors.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Timeline Notes")
        appendLine()
        appendLine("Sample window: ${report.samples.size} points. Replay meaningful events (not every sample) inside the Timeline / overnight replay screens.")
        appendLine()
        appendLine("## Privacy")
        appendLine()
        appendLine("- No analytics, telemetry, or advertising SDKs")
        appendLine("- Report generated on-device; share only if you choose")
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
            """.trimMargin(),
        )
        appendLine()
        appendLine("---")
        appendLine("_Don't guess. Investigate._")
    }
}
