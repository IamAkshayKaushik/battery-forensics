package com.batteryforensics.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.batteryforensics.export.ExportArtifact
import com.batteryforensics.export.ExportFormat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists export artifacts under app-specific storage and builds share Intents via FileProvider.
 */
object ExportShareHelper {
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val EXPORT_DIR = "exports"

    fun authority(context: Context): String = "${context.packageName}$AUTHORITY_SUFFIX"

    fun exportRoot(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, EXPORT_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveArtifacts(
        context: Context,
        artifacts: List<ExportArtifact>,
        roomDbFile: File? = null,
    ): SavedExport {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sessionDir = File(exportRoot(context), "session_$stamp").also { it.mkdirs() }
        val saved = mutableListOf<File>()
        artifacts.forEach { artifact ->
            val file = File(sessionDir, artifact.fileName)
            when {
                artifact.bytes != null -> file.writeBytes(artifact.bytes!!)
                else -> file.writeText(artifact.content)
            }
            saved += file
        }
        // Prefer copying live Room DB into the session (and note SQL text is a portable snapshot).
        roomDbFile?.takeIf { it.exists() }?.let { db ->
            val dest = File(sessionDir, "battery_forensics.db")
            db.copyTo(dest, overwrite = true)
            saved += dest
            // Also append into ZIP if present
            artifacts.firstOrNull { it.format == ExportFormat.ZIP }?.let { zipArt ->
                val zipFile = saved.firstOrNull { it.name == zipArt.fileName }
                if (zipFile != null) {
                    // Leave separate .db alongside ZIP; ZIP already has SQL text labeled honestly.
                }
            }
        }
        val readme = File(sessionDir, "EXPORT_README.txt")
        readme.writeText(
            buildString {
                appendLine("Battery Forensics local export")
                appendLine("Saved under app-specific storage (no cloud upload).")
                appendLine("SQL snapshot (.sql) is portable SQL text — not a binary SQLite file.")
                appendLine("battery_forensics.db (if present) is a copy of the Room database file.")
                appendLine("Share via the in-app Share action (FileProvider).")
            },
        )
        saved += readme
        return SavedExport(sessionDir = sessionDir, files = saved)
    }

    fun shareIntent(context: Context, saved: SavedExport): Intent {
        val zip = saved.files.firstOrNull { it.name.endsWith(".zip") }
        val primary = zip ?: saved.files.firstOrNull { it.name.endsWith(".md") } ?: saved.files.first()
        val uri: Uri = FileProvider.getUriForFile(context, authority(context), primary)
        return Intent(Intent.ACTION_SEND).apply {
            type = when {
                primary.name.endsWith(".zip") -> "application/zip"
                primary.name.endsWith(".md") -> "text/markdown"
                primary.name.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Battery Forensics export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Share Battery Forensics export") }
    }

    data class SavedExport(
        val sessionDir: File,
        val files: List<File>,
    )
}
