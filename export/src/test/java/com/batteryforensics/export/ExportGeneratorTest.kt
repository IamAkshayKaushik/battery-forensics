package com.batteryforensics.export

import com.batteryforensics.ai.AiReportGenerator
import com.batteryforensics.core.evidence.Confidence
import com.batteryforensics.core.evidence.ConfidenceLevel
import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.core.evidence.DiagnosticCategory
import com.batteryforensics.core.model.DeviceInfo
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.reporting.ForensicReport
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportGeneratorTest {
    @Test
    fun exportsJsonAndMarkdown() {
        val report = ForensicReport(
            generatedAtEpochMs = 1L,
            device = DeviceInfo("Test", "Device", "14", 34),
            sampleCount = 1,
            samples = listOf(
                MonitoringSample(
                    timestampEpochMs = 1L,
                    batteryPercent = 50,
                    voltageMv = 3800,
                    currentMicroamps = -200000,
                    chargeCounterMah = 2000,
                    temperatureC = 35f,
                    isCharging = false,
                    chargePlug = null,
                    screenOn = false,
                    brightnessPercent = 20,
                    refreshRateHz = 60f,
                    thermalStatus = 0,
                    wifiConnected = true,
                    wifiRssiDbm = -50,
                    cellularRssiDbm = null,
                    networkType = "lte",
                ),
            ),
            diagnoses = listOf(
                Diagnosis(
                    id = "demo",
                    title = "Demo cause",
                    category = DiagnosticCategory.THERMAL,
                    explanation = "Example",
                    confidence = Confidence(80, ConfidenceLevel.MEASURED),
                    evidence = emptyList(),
                    supportingMetrics = emptyList(),
                    counterEvidence = emptyList(),
                    recommendedActions = listOf("Cool down"),
                    probabilityPercent = 80,
                ),
            ),
            unknownFactors = listOf("None"),
        )
        val artifacts = ExportGenerator().export(
            report,
            setOf(
                ExportFormat.JSON,
                ExportFormat.MARKDOWN,
                ExportFormat.CSV,
                ExportFormat.HTML,
                ExportFormat.ZIP,
                ExportFormat.SQLITE_SNAPSHOT,
            ),
        )
        assertThat(artifacts).hasSize(6)
        val md = artifacts.first { it.format == ExportFormat.MARKDOWN }.content
        assertThat(md).contains("Executive Summary")
        assertThat(md).contains("Battery Overview")
        assertThat(md).contains("Device Info")
        assertThat(md).contains("LLM Instruction Block")
        assertThat(md).contains("hardware issue".replaceFirstChar { it.uppercase() })
        assertThat(md).contains("Battery degraded")
        assertThat(md).contains("Modem / radio responsible")
        assertThat(md).contains("Rogue app")
        assertThat(md).contains("Battery replacement would help")
        assertThat(md).contains("Demo cause")
        assertThat(md).contains("Shizuku Findings")
        assertThat(md).contains("Root Cause Ranking")
        assertThat(artifacts.first { it.format == ExportFormat.HTML }.content).contains("Root causes")
        assertThat(artifacts.first { it.format == ExportFormat.CSV }.content).contains("batteryPercent")
        assertThat(artifacts.first { it.format == ExportFormat.CSV }.content).contains("hotspotOn")
        assertThat(artifacts.first { it.format == ExportFormat.ZIP }.bytes).isNotNull()
        assertThat(artifacts.first { it.format == ExportFormat.SQLITE_SNAPSHOT }.content).contains("CREATE TABLE")
        assertThat(artifacts.first { it.format == ExportFormat.SQLITE_SNAPSHOT }.content).contains("SQL TEXT")
        assertThat(AiReportGenerator().toMarkdown(report)).contains("Don't guess")
    }

    @Test
    fun exportsCompressedBfzBundle() {
        val report = ForensicReport(
            generatedAtEpochMs = 1L,
            device = DeviceInfo("Test", "Device", "14", 34),
            sampleCount = 1,
            samples = listOf(
                MonitoringSample(
                    timestampEpochMs = 1L,
                    batteryPercent = 50,
                    voltageMv = 3800,
                    currentMicroamps = -200000,
                    chargeCounterMah = 2000,
                    temperatureC = 35f,
                    isCharging = false,
                    chargePlug = null,
                    screenOn = false,
                    brightnessPercent = 20,
                    refreshRateHz = 60f,
                    thermalStatus = 0,
                    wifiConnected = true,
                    wifiRssiDbm = -50,
                    cellularRssiDbm = null,
                    networkType = "lte",
                ),
            ),
            diagnoses = emptyList(),
            unknownFactors = emptyList(),
        )
        val artifacts = ExportGenerator().export(report, setOf(ExportFormat.BFZ))
        assertThat(artifacts).hasSize(1)
        val bfz = artifacts.first()
        assertThat(bfz.format).isEqualTo(ExportFormat.BFZ)
        assertThat(bfz.fileName).endsWith(".bfz")
        assertThat(bfz.bytes).isNotNull()
        assertThat(bfz.bytes!!.size).isGreaterThan(20)
        // gzip magic
        assertThat(bfz.bytes!![0]).isEqualTo(0x1f.toByte())
        assertThat(bfz.bytes!![1]).isEqualTo(0x8b.toByte())
    }
}
