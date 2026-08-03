package com.batteryforensics.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.viewmodel.SettingsViewModel
import com.batteryforensics.monitoring.service.FlightRecorderService
import com.batteryforensics.shizuku.GracefulShizukuGateway
import com.batteryforensics.shizuku.ShizukuAvailability

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shizuku = GracefulShizukuGateway().availability()
    ForensicScreen(
        title = "Settings",
        subtitle = "Privacy-first. No analytics, no telemetry, no ads.",
    ) {
        Text("Periodic monitoring", style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = state.periodicMonitoringEnabled,
            onCheckedChange = viewModel::setPeriodicMonitoring,
        )
        Spacer(Modifier.height(12.dp))
        Text("Flight Recorder (foreground)", style = MaterialTheme.typography.titleMedium)
        Text(
            "15s continuous sampling while enabled. Uses a foreground service intentionally — WorkManager alone is too coarse for forensic timelines.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = state.flightRecorderEnabled,
            onCheckedChange = { enabled ->
                viewModel.setFlightRecorder(enabled)
                if (enabled) {
                    context.startForegroundService(Intent(context, FlightRecorderService::class.java))
                } else {
                    context.startService(
                        Intent(context, FlightRecorderService::class.java).apply {
                            action = FlightRecorderService.ACTION_STOP
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(20.dp))
        Text("Shizuku", style = MaterialTheme.typography.titleMedium)
        Text(
            when (shizuku) {
                ShizukuAvailability.Available -> "Available — advanced dumpsys path ready (wiring deferred)"
                ShizukuAvailability.PermissionDenied -> "Installed but permission denied"
                ShizukuAvailability.NotInstalled -> "Not installed — app continues with public APIs"
                ShizukuAvailability.Unsupported -> "Unsupported on this device"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
