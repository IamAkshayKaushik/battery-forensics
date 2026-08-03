package com.batteryforensics.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.components.MetricRow
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.StatusPanel
import com.batteryforensics.app.ui.permissions.rememberPermissionController
import com.batteryforensics.app.ui.viewmodel.SettingsViewModel
import com.batteryforensics.core.time.TimeConstants
import com.batteryforensics.monitoring.service.FlightRecorderService
import com.batteryforensics.permissions.AppPermissions
import com.batteryforensics.permissions.PermissionGrantStatus
import com.batteryforensics.shizuku.ShizukuAvailability

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberPermissionController { viewModel.refresh() }

    LaunchedEffect(permissions.revision) {
        viewModel.refresh()
    }

    ForensicScreen(
        title = "Settings",
        subtitle = "Privacy-first. No analytics, no telemetry, no ads. ${AppPermissions.RATIONALE}",
    ) {
        SectionHeader("Monitoring")
        Text("Periodic monitoring (WorkManager)", style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = state.settings.periodicMonitoringEnabled,
            onCheckedChange = {
                if (it) permissions.requestMonitoringPermissions()
                viewModel.setPeriodicMonitoring(it)
            },
        )
        Spacer(Modifier.height(8.dp))
        MetricRow(
            "Sample interval",
            when (state.settings.sampleIntervalMs) {
                TimeConstants.DEFAULT_SAMPLE_INTERVAL_MS -> "1 min (default target)"
                5 * TimeConstants.MILLIS_PER_MINUTE -> "5 min"
                15 * TimeConstants.MILLIS_PER_MINUTE -> "15 min"
                else -> "${state.settings.sampleIntervalMs / 1000}s"
            },
            "WorkManager minimum period is 15 min; Flight Recorder uses 15s",
        )
        TextButton(onClick = viewModel::cycleSampleInterval) {
            Text("Cycle interval preference")
        }

        Spacer(Modifier.height(12.dp))
        Text("Flight Recorder (foreground)", style = MaterialTheme.typography.titleMedium)
        Text(
            "15s continuous sampling while enabled. Uses a foreground service intentionally — WorkManager alone is too coarse for forensic timelines.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = state.settings.flightRecorderEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    permissions.requestFlightRecorderPermissions()
                    viewModel.setFlightRecorder(true)
                    context.startForegroundService(Intent(context, FlightRecorderService::class.java))
                } else {
                    viewModel.setFlightRecorder(false)
                    context.startService(
                        Intent(context, FlightRecorderService::class.java).apply {
                            action = FlightRecorderService.ACTION_STOP
                        },
                    )
                }
            },
        )

        Spacer(Modifier.height(20.dp))
        SectionHeader(
            "Permissions",
            "Requested when you enable features. Denied permanently? Use App Settings. Documented in docs/PERMISSIONS.md.",
        )
        state.permissions.forEach { row ->
            StatusPanel(
                title = row.spec.title,
                body = "${row.spec.rationale}\nUnlocks: ${row.spec.unlocks}",
                statusLabel = when (row.status) {
                    PermissionGrantStatus.Granted -> "GRANTED"
                    PermissionGrantStatus.NotGranted -> "MISSING"
                },
                primaryAction = when (row.status) {
                    PermissionGrantStatus.Granted -> null
                    PermissionGrantStatus.NotGranted -> "Request"
                },
                onPrimary = when (row.status) {
                    PermissionGrantStatus.Granted -> null
                    PermissionGrantStatus.NotGranted -> {
                        { permissions.requestSpec(row.spec) }
                    }
                },
                secondaryAction = if (row.status == PermissionGrantStatus.NotGranted) "App Settings" else null,
                onSecondary = if (row.status == PermissionGrantStatus.NotGranted) {
                    permissions.openAppSettings
                } else {
                    null
                },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Advanced diagnostics (Shizuku)")
        Text(
            when (state.shizuku) {
                ShizukuAvailability.Available ->
                    "Authorized. Investigate runs dumpsys → typed parsers for Doze, wake locks, alarms, jobs."
                ShizukuAvailability.NotInstalled ->
                    "Install Shizuku from the Play Store / GitHub, start it via wireless debugging or root, then authorize this app."
                ShizukuAvailability.NotRunning ->
                    "Shizuku is installed but the service is not running. Open Shizuku and start the server."
                ShizukuAvailability.PermissionDenied ->
                    "Shizuku is running. Tap Authorize to grant dumpsys access to Battery Forensics."
                ShizukuAvailability.Unsupported ->
                    "This Shizuku version or device is unsupported (need Shizuku v11+)."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        MetricRow("Status", state.shizukuLabel, "CHECKED ON RESUME")
        if (state.shizukuLimited.isNotEmpty()) {
            MetricRow(
                "Limited without Shizuku",
                state.shizukuLimited.joinToString(),
                "FALLBACK",
            )
        }
        Text("Use advanced diagnostics when available", style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = state.settings.advancedDiagnosticsEnabled,
            onCheckedChange = viewModel::setAdvancedDiagnostics,
        )
        when (state.shizuku) {
            ShizukuAvailability.PermissionDenied, ShizukuAvailability.Available -> {
                TextButton(onClick = viewModel::requestShizukuPermission) {
                    Text(
                        if (state.shizuku == ShizukuAvailability.Available) {
                            "Re-check authorization"
                        } else {
                            "Authorize Shizuku"
                        },
                    )
                }
            }
            ShizukuAvailability.NotRunning -> {
                TextButton(onClick = viewModel::openShizukuManager) {
                    Text("Open Shizuku — start the service")
                }
            }
            ShizukuAvailability.NotInstalled -> {
                TextButton(onClick = viewModel::openShizukuManager) {
                    Text("Open / install Shizuku")
                }
            }
            ShizukuAvailability.Unsupported -> Unit
        }
    }
}
