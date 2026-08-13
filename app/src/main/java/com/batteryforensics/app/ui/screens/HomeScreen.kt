package com.batteryforensics.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryforensics.app.ui.components.DiagnosisCard
import com.batteryforensics.app.ui.components.EmptyInvestigationHint
import com.batteryforensics.app.ui.components.FocusNavRow
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.components.InvestigationHero
import com.batteryforensics.app.ui.components.MetricRow
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.StatusPanel
import com.batteryforensics.app.ui.permissions.rememberPermissionController
import com.batteryforensics.app.ui.viewmodel.HomeViewModel
import com.batteryforensics.monitoring.service.FlightRecorderService
import com.batteryforensics.permissions.AppPermissions
import com.batteryforensics.shizuku.ShizukuAvailability

@Composable
fun HomeScreen(
    onOpenChemistry: () -> Unit,
    onOpenThermal: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenLive: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberPermissionController()

    LaunchedEffect(permissions.revision, state.settings.initialPermissionPromptShown) {
        if (state.settings.initialPermissionPromptShown) return@LaunchedEffect
        val missing = permissions.missingRuntime(AppPermissions.monitoringRuntime)
        if (missing.isNotEmpty()) {
            permissions.requestMonitoringPermissions()
        }
        viewModel.markInitialPromptShown()
    }

    ForensicScreen(
        title = "Battery Forensics",
        subtitle = "Don't guess. Investigate. Evidence-first diagnostics stay on this device.",
    ) {
        InvestigationHero(
            batteryPercent = state.latestBatteryPercent,
            drainLabel = state.overallDrainLabel,
            standbyLabel = state.overnightDrainLabel,
            freshnessLabel = state.lastSampleAgeLabel?.let { "Sample $it" },
            sparklineValues = state.recentBatteryPercents,
        )

        val showSetup = state.missingPermissions.isNotEmpty() && !state.settings.setupBannerDismissed
        if (showSetup) {
            Spacer(Modifier.height(16.dp))
            StatusPanel(
                title = "Setup incomplete",
                body = buildString {
                    append(AppPermissions.RATIONALE)
                    append("\n\nMissing: ")
                    append(state.missingPermissions.joinToString { it.title })
                },
                statusLabel = "${state.missingPermissions.size} needed",
                primaryAction = "Grant permissions",
                onPrimary = { permissions.requestCriticalPermissions() },
                secondaryAction = "Open Settings",
                onSecondary = permissions.openAppSettings,
            )
            TextButton(onClick = viewModel::dismissSetupBanner) {
                Text("Hide for now")
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Evidence snapshot", "Measured signals feeding the investigation.")
        MetricRow("Samples captured", state.sampleCount.toString(), "MEASURED")
        state.latestTempC?.let {
            MetricRow("Latest temperature", "${"%.1f".format(it)}°C", "MEASURED")
        }
        state.latestNetwork?.let {
            MetricRow("Network", it, "MEASURED")
        } ?: MetricRow(
            "Network",
            "Locked — grant location + phone state",
            "NEEDS PERMISSION",
        )

        Spacer(Modifier.height(16.dp))
        StatusPanel(
            title = "Advanced diagnostics (Shizuku)",
            body = when (state.shizukuAvailability) {
                ShizukuAvailability.Available ->
                    "Dumpsys collectors deepen Doze, wake locks, alarms, Wi-Fi, location, sensors, and notification evidence."
                ShizukuAvailability.NotRunning ->
                    "Shizuku is installed but the service is not running. Open Shizuku and start it, then return here."
                ShizukuAvailability.PermissionDenied ->
                    "Shizuku is running. Authorize Battery Forensics to unlock dumpsys collectors."
                ShizukuAvailability.NotInstalled ->
                    "Install Shizuku, start its service, then authorize this app. Without it: ${state.shizukuLimited.joinToString()}."
                ShizukuAvailability.Unsupported ->
                    state.shizukuStatus
            },
            statusLabel = state.shizukuStatus,
            primaryAction = when (state.shizukuAvailability) {
                ShizukuAvailability.PermissionDenied -> "Authorize Shizuku"
                ShizukuAvailability.NotInstalled -> "Open Shizuku"
                ShizukuAvailability.NotRunning -> "Open Shizuku"
                else -> "Manage in Settings"
            },
            onPrimary = when (state.shizukuAvailability) {
                ShizukuAvailability.PermissionDenied -> viewModel::requestShizukuPermission
                ShizukuAvailability.NotInstalled, ShizukuAvailability.NotRunning ->
                    viewModel::openShizukuManager
                else -> onOpenSettings
            },
        )

        if (state.topCauses.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Top causes", "Tap a card for the full Causes investigation.")
            state.topCauses.forEach { d ->
                DiagnosisCard(diagnosis = d, onClick = onOpenDiagnostics)
            }
        } else {
            Spacer(Modifier.height(16.dp))
            EmptyInvestigationHint(
                title = "Case file empty",
                text = when {
                    state.sampleCount == 0L ->
                        "No samples yet. Capture now, or start Flight Recorder overnight for standby evidence."
                    state.missingPermissions.any { it.title.contains("Location", ignoreCase = true) } ->
                        "Grant location to unlock cellular forensics, then investigate."
                    state.shizukuAvailability != ShizukuAvailability.Available ->
                        "Start Flight Recorder overnight, or connect Shizuku for dumpsys depth."
                    else ->
                        "Keep sampling. Causes appear when rules cross their evidence thresholds."
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        StatusPanel(
            title = "Flight Recorder",
            body = if (state.flightRecorderOn) {
                "Foreground sampling active. Notification stays visible while recording."
            } else {
                "Enable for continuous forensic timelines (especially overnight). WorkManager alone is too coarse."
            },
            statusLabel = if (state.flightRecorderOn) "ON" else "OFF",
            primaryAction = if (state.flightRecorderOn) "Stop recorder" else "Start Flight Recorder",
            onPrimary = {
                if (!state.flightRecorderOn) {
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
            secondaryAction = "Open Live Monitor",
            onSecondary = onOpenLive,
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenDiagnostics,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Investigate causes" },
        ) {
            Text("Investigate causes")
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = viewModel::captureNow,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Capture sample now" },
        ) {
            Text("Capture sample now")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        SectionHeader("Focus areas")
        FocusNavRow("Chemistry", "Ri, voltage sag, wear, charge efficiency", onOpenChemistry)
        FocusNavRow("Thermal", "ΔT/Δt, charging heat, throttling", onOpenThermal)
        FocusNavRow("Network", "Signal, transitions, inferred radio-active time", onOpenNetwork)
        FocusNavRow("Export", "JSON / CSV / HTML / ZIP / Markdown AI report", onOpenExport)
    }
}
