package com.batteryforensics.export

import com.batteryforensics.ai.AiReportGenerator
import com.batteryforensics.reporting.ForensicReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat {
    JSON,
    MARKDOWN,
    CSV,
    HTML,
    ZIP,
    SQLITE_SNAPSHOT,
}

data class ExportArtifact(
    val format: ExportFormat,
    val fileName: String,
    val mimeType: String,
    val content: String,
    /** Binary payload for ZIP / SQLite when applicable. */
    val bytes: ByteArray? = null,
)

class ExportGenerator(
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
    private val aiReportGenerator: AiReportGenerator = AiReportGenerator(),
) {
    fun export(
        report: ForensicReport,
        formats: Set<ExportFormat>,
        roomDbBytes: ByteArray? = null,
    ): List<ExportArtifact> =
        formats.map { format ->
            when (format) {
                ExportFormat.JSON -> ExportArtifact(
                    format = format,
                    fileName = "battery_forensics_report.json",
                    mimeType = "application/json",
                    content = json.encodeToString(report),
                )
                ExportFormat.MARKDOWN -> ExportArtifact(
                    format = format,
                    fileName = "battery_forensics_ai_report.md",
                    mimeType = "text/markdown",
                    content = aiReportGenerator.toMarkdown(report),
                )
                ExportFormat.CSV -> ExportArtifact(
                    format = format,
                    fileName = "battery_forensics_samples.csv",
                    mimeType = "text/csv",
                    content = samplesCsv(report),
                )
                ExportFormat.HTML -> ExportArtifact(
                    format = format,
                    fileName = "battery_forensics_report.html",
                    mimeType = "text/html",
                    content = htmlReport(report),
                )
                ExportFormat.ZIP -> zipBundle(report, roomDbBytes)
                ExportFormat.SQLITE_SNAPSHOT -> sqliteSnapshot(report)
            }
        }

    private fun zipBundle(report: ForensicReport, roomDbBytes: ByteArray?): ExportArtifact {
        val jsonContent = json.encodeToString(report)
        val md = aiReportGenerator.toMarkdown(report)
        val csv = samplesCsv(report)
        val html = htmlReport(report)
        val sql = sqliteSnapshot(report).content
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            fun putBytes(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("battery_forensics_report.json", jsonContent)
            put("battery_forensics_ai_report.md", md)
            put("battery_forensics_samples.csv", csv)
            put("battery_forensics_report.html", html)
            put("battery_forensics_snapshot.sql", sql)
            put(
                "README.txt",
                "Battery Forensics diagnostic bundle.\n" +
                    "- .sql is portable SQL TEXT (not a binary SQLite file).\n" +
                    "- battery_forensics.db (if present) is a binary Room DB copy.\n" +
                    "All data generated on-device. Privacy-first — no cloud upload.\n",
            )
            if (roomDbBytes != null) {
                putBytes("battery_forensics.db", roomDbBytes)
            }
        }
        val bytes = baos.toByteArray()
        return ExportArtifact(
            format = ExportFormat.ZIP,
            fileName = "battery_forensics_bundle.zip",
            mimeType = "application/zip",
            content = "ZIP bundle (${bytes.size} bytes) containing JSON, Markdown, CSV, HTML, SQL" +
                if (roomDbBytes != null) ", Room DB" else "",
            bytes = bytes,
        )
    }

    /**
     * Portable SQL text snapshot the user can import.
     * Labeled honestly — this is NOT a binary SQLite file. Prefer also shipping Room DB in ZIP.
     */
    private fun sqliteSnapshot(report: ForensicReport): ExportArtifact {
        val sql = buildString {
            appendLine("-- Battery Forensics SQL TEXT snapshot (not a binary .db file)")
            appendLine("CREATE TABLE IF NOT EXISTS monitoring_samples (")
            appendLine("  timestampEpochMs INTEGER, batteryPercent INTEGER, voltageMv INTEGER,")
            appendLine("  currentMicroamps INTEGER, chargeCounterMah INTEGER, temperatureC REAL,")
            appendLine("  isCharging INTEGER, screenOn INTEGER, brightnessPercent INTEGER,")
            appendLine("  refreshRateHz REAL, thermalStatus INTEGER, wifiConnected INTEGER,")
            appendLine("  wifiRssiDbm INTEGER, cellularRssiDbm INTEGER, networkType TEXT,")
            appendLine("  chargingCurrentMicroamps INTEGER, orientation TEXT, cellId TEXT,")
            appendLine("  carrierName TEXT, cellularBand TEXT, bluetoothOn INTEGER,")
            appendLine("  bluetoothConnected INTEGER, locationEnabled INTEGER, nfcEnabled INTEGER,")
            appendLine("  hotspotOn INTEGER, foregroundApp TEXT, memoryPressure TEXT,")
            appendLine("  storageFreeBytes INTEGER, storageFreePercent REAL")
            appendLine(");")
            appendLine("CREATE TABLE IF NOT EXISTS diagnoses (")
            appendLine("  id TEXT, title TEXT, category TEXT, probability INTEGER, confidence TEXT, explanation TEXT")
            appendLine(");")
            report.samples.forEach { s ->
                appendLine(
                    "INSERT INTO monitoring_samples VALUES (" +
                        "${s.timestampEpochMs},${s.batteryPercent.sqlInt},${s.voltageMv.sqlInt}," +
                        "${s.currentMicroamps.sqlInt},${s.chargeCounterMah.sqlInt},${s.temperatureC.sqlReal}," +
                        "${s.isCharging.sqlBool},${s.screenOn.sqlBool},${s.brightnessPercent.sqlInt}," +
                        "${s.refreshRateHz.sqlReal},${s.thermalStatus.sqlInt},${s.wifiConnected.sqlBool}," +
                        "${s.wifiRssiDbm.sqlInt},${s.cellularRssiDbm.sqlInt},${s.networkType.sqlText}," +
                        "${s.chargingCurrentMicroamps.sqlInt},${s.orientation.sqlText},${s.cellId.sqlText}," +
                        "${s.carrierName.sqlText},${s.cellularBand.sqlText},${s.bluetoothOn.sqlBool}," +
                        "${s.bluetoothConnected.sqlBool},${s.locationEnabled.sqlBool},${s.nfcEnabled.sqlBool}," +
                        "${s.hotspotOn.sqlBool},${s.foregroundApp.sqlText},${s.memoryPressure.sqlText}," +
                        "${s.storageFreeBytes.sqlLong},${s.storageFreePercent.sqlReal});",
                )
            }
            report.diagnoses.forEach { d ->
                appendLine(
                    "INSERT INTO diagnoses VALUES (" +
                        "'${d.id.escapeSql}','${d.title.escapeSql}','${d.category}',${d.probabilityPercent}," +
                        "'${d.confidence.starsLabel.escapeSql}','${d.explanation.escapeSql}');",
                )
            }
        }
        return ExportArtifact(
            format = ExportFormat.SQLITE_SNAPSHOT,
            fileName = "battery_forensics_snapshot.sql",
            mimeType = "application/sql",
            content = sql,
        )
    }

    private fun samplesCsv(report: ForensicReport): String = buildString {
        appendLine(
            "timestampEpochMs,batteryPercent,voltageMv,currentMicroamps,chargeCounterMah," +
                "temperatureC,isCharging,chargePlug,screenOn,brightnessPercent,refreshRateHz," +
                "thermalStatus,wifiConnected,wifiRssiDbm,cellularRssiDbm,networkType," +
                "chargingCurrentMicroamps,orientation,cellId,carrierName,cellularBand," +
                "bluetoothOn,bluetoothConnected,locationEnabled,nfcEnabled,hotspotOn," +
                "foregroundApp,memoryPressure,storageFreeBytes,storageFreePercent",
        )
        report.samples.forEach { s ->
            appendLine(
                listOf(
                    s.timestampEpochMs,
                    s.batteryPercent,
                    s.voltageMv,
                    s.currentMicroamps,
                    s.chargeCounterMah,
                    s.temperatureC,
                    s.isCharging,
                    s.chargePlug,
                    s.screenOn,
                    s.brightnessPercent,
                    s.refreshRateHz,
                    s.thermalStatus,
                    s.wifiConnected,
                    s.wifiRssiDbm,
                    s.cellularRssiDbm,
                    s.networkType,
                    s.chargingCurrentMicroamps,
                    s.orientation,
                    s.cellId,
                    s.carrierName,
                    s.cellularBand,
                    s.bluetoothOn,
                    s.bluetoothConnected,
                    s.locationEnabled,
                    s.nfcEnabled,
                    s.hotspotOn,
                    s.foregroundApp,
                    s.memoryPressure,
                    s.storageFreeBytes,
                    s.storageFreePercent,
                ).joinToString(","),
            )
        }
    }

    private fun htmlReport(report: ForensicReport): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("<title>Battery Forensics Report</title>")
        appendLine("<style>")
        appendLine("body{font-family:system-ui,sans-serif;margin:2rem;max-width:900px;line-height:1.45;color:#0b1f17;background:#f7faf8}")
        appendLine("h1,h2{color:#1b7a4e} table{border-collapse:collapse;width:100%} th,td{border:1px solid #c5d9ce;padding:.4rem .6rem;text-align:left}")
        appendLine(".badge{display:inline-block;padding:.15rem .5rem;border-radius:4px;background:#e7f2ec;font-size:.85rem}")
        appendLine("</style></head><body>")
        appendLine("<h1>Battery Forensics</h1>")
        appendLine("<p><em>Don't guess. Investigate.</em> Generated locally — no telemetry.</p>")
        appendLine("<h2>Device</h2>")
        appendLine("<p>${report.device.manufacturer} ${report.device.model} · Android ${report.device.androidVersion} (SDK ${report.device.sdkInt})</p>")
        appendLine("<h2>Root causes (${report.diagnoses.size})</h2>")
        if (report.diagnoses.isEmpty()) {
            appendLine("<p>No rules triggered on ${report.sampleCount} samples.</p>")
        } else {
            appendLine("<table><tr><th>Rank</th><th>Cause</th><th>Probability</th><th>Confidence</th></tr>")
            report.diagnoses.forEachIndexed { i, d ->
                appendLine("<tr><td>${i + 1}</td><td>${d.title.escapeHtml}</td><td>${d.probabilityPercent}%</td><td>${d.confidence.starsLabel.escapeHtml}</td></tr>")
            }
            appendLine("</table>")
            report.diagnoses.forEach { d ->
                appendLine("<h3>${d.title.escapeHtml}</h3>")
                appendLine("<p>${d.explanation.escapeHtml}</p>")
                appendLine("<ul>")
                d.evidence.forEach { e ->
                    appendLine("<li>[${e.confidenceLevel}] ${e.description.escapeHtml}: ${e.observedValue.escapeHtml}</li>")
                }
                appendLine("</ul>")
            }
        }
        appendLine("<h2>Samples</h2>")
        appendLine("<p>${report.sampleCount} monitoring samples in export.</p>")
        appendLine("<h2>Unknown factors</h2><ul>")
        report.unknownFactors.forEach { appendLine("<li>${it.escapeHtml}</li>") }
        appendLine("</ul>")
        appendLine("<p class=\"badge\">Privacy: on-device only</p>")
        appendLine("</body></html>")
    }

    private val Int?.sqlInt: String get() = this?.toString() ?: "NULL"
    private val Long?.sqlLong: String get() = this?.toString() ?: "NULL"
    private val Float?.sqlReal: String get() = this?.toString() ?: "NULL"
    private val Boolean?.sqlBool: String get() = when (this) {
        true -> "1"
        false -> "0"
        null -> "NULL"
    }
    private val String?.sqlText: String get() = this?.let { "'${it.escapeSql}'" } ?: "NULL"
    private val String.escapeSql: String get() = replace("'", "''")
    private val String.escapeHtml: String
        get() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
