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
        assertThat(md).contains("LLM Instruction Block")
        assertThat(md).contains("Demo cause")
        assertThat(md).contains("Investigation Window")
        assertThat(artifacts.first { it.format == ExportFormat.HTML }.content).contains("Root causes")
        assertThat(artifacts.first { it.format == ExportFormat.CSV }.content).contains("batteryPercent")
        assertThat(artifacts.first { it.format == ExportFormat.ZIP }.bytes).isNotNull()
        assertThat(artifacts.first { it.format == ExportFormat.SQLITE_SNAPSHOT }.content).contains("CREATE TABLE")
        assertThat(AiReportGenerator().toMarkdown(report)).contains("Don't guess")
    }
}
