package com.batteryforensics.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.diagnostics.DiagnosticsEngine
import com.batteryforensics.monitoring.MonitoringRepository
import com.batteryforensics.settings.SettingsRepository
import com.batteryforensics.shizuku.ShizukuAvailability
import com.batteryforensics.shizuku.ShizukuDiagnosticsCollector
import com.batteryforensics.shizuku.ShizukuGateway
import com.batteryforensics.shizuku.label
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PrivilegedUiSummary(
    val availabilityLabel: String,
    val lines: List<String>,
    val errors: List<String>,
    val usedDumpsys: Boolean,
)

data class DiagnosticsUiState(
    val diagnoses: List<Diagnosis> = emptyList(),
    val message: String? = null,
    val sampleCount: Int = 0,
    val privileged: PrivilegedUiSummary? = null,
    val investigating: Boolean = false,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
    private val shizukuGateway: ShizukuGateway,
    private val shizukuCollector: ShizukuDiagnosticsCollector,
) : ViewModel() {
    private val engine = DiagnosticsEngine()
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    fun investigate() {
        viewModelScope.launch {
            _uiState.update { it.copy(investigating = true, message = null) }
            val end = System.currentTimeMillis()
            val start = end - 12 * 60 * 60 * 1000L
            val samples = monitoringRepository.samplesBetween(start, end)
            if (samples.isEmpty()) {
                _uiState.update {
                    it.copy(
                        diagnoses = emptyList(),
                        sampleCount = 0,
                        investigating = false,
                        message = "No samples in the last 12 hours. Capture samples from Live Monitor or start Flight Recorder first.",
                        privileged = null,
                    )
                }
                return@launch
            }

            // Prior window as baseline for regression rule (previous 12h before current).
            val baseline = monitoringRepository.samplesBetween(start - 12 * 60 * 60 * 1000L, start)

            val settings = settingsRepository.settings.first()
            val (privilegedUi, privilegedEvidence) = if (settings.advancedDiagnosticsEnabled) {
                withContext(Dispatchers.IO) {
                    val snap = shizukuCollector.collect(context)
                    val ui = PrivilegedUiSummary(
                        availabilityLabel = snap.availability.label(),
                        usedDumpsys = snap.availability == ShizukuAvailability.Available && snap.hasAnyData,
                        errors = snap.errors,
                        lines = buildList {
                            snap.deviceIdle?.let { idle ->
                                add(
                                    "Doze/deviceidle: state=${idle.state ?: "?"} deep=${idle.deepEnabled} light=${idle.lightEnabled}",
                                )
                            }
                            snap.doze?.let { d ->
                                add("Doze timeline: state=${d.state ?: "?"} hints=${d.historyHints.take(3).joinToString()}")
                            }
                            snap.wakeLocks?.let { wl ->
                                add(
                                    "Wake locks: total=${wl.totalLocks ?: "?"} app=${wl.appLocks ?: "?"} " +
                                        "top=${wl.topTags.take(3).joinToString { it.packageName }}",
                                )
                            }
                            snap.alarms?.let { a ->
                                add(
                                    "Alarms: wakeups=${a.wakeupAlarmCount ?: "?"} " +
                                        "top=${a.topPackages.take(3).joinToString { it.packageName }}",
                                )
                            }
                            snap.jobs?.let { j ->
                                add("Jobs: pending=${j.pendingJobCount ?: "?"} running=${j.runningJobCount ?: "?"}")
                            }
                            snap.batteryStats?.let { b ->
                                add(
                                    "BatteryStats: capacity=${b.capacityMah ?: b.estimatedBatteryCapacityMah ?: "?"} mAh",
                                )
                            }
                            snap.power?.let { p ->
                                add("Power: wakeLockCount=${p.wakeLockCount ?: "?"}")
                            }
                            snap.activity?.let { a ->
                                add(
                                    "Activity: resumed=${a.topResumedActivity ?: "?"} " +
                                        "fgs=${a.foregroundServiceHints.take(3).joinToString()}",
                                )
                            }
                            snap.cmdBattery?.let { c ->
                                add("cmd battery: level=${c.level ?: "?"} status=${c.statusLine ?: "?"}")
                            }
                            snap.usageStats?.standbyBucketHints?.take(3)?.forEach { add("Standby: $it") }
                            snap.thermalService?.currentStatus?.let { add("Thermal service: $it") }
                            if (isEmpty() && snap.availability != ShizukuAvailability.Available) {
                                add("Public APIs only — authorize Shizuku for dumpsys depth.")
                            }
                        },
                    )
                    ui to snap.toPrivilegedEvidence().takeIf { it.hasAnyData }
                }
            } else {
                PrivilegedUiSummary(
                    availabilityLabel = "Advanced diagnostics disabled in Settings",
                    usedDumpsys = false,
                    errors = emptyList(),
                    lines = listOf("Enable Advanced diagnostics (Shizuku) in Settings to run dumpsys collectors."),
                ) to null
            }

            val result = engine.investigate(
                samples = samples,
                privileged = privilegedEvidence,
                baselineSamples = baseline,
            )
            _uiState.update {
                it.copy(
                    diagnoses = result.diagnoses,
                    sampleCount = result.sampleCount,
                    investigating = false,
                    privileged = privilegedUi,
                    message = when {
                        result.diagnoses.isEmpty() && privilegedUi.usedDumpsys ->
                            "Evaluated ${result.sampleCount} samples — no rules crossed thresholds. Shizuku dumpsys evidence fed the rule engine."
                        result.diagnoses.isEmpty() ->
                            "Evaluated ${result.sampleCount} samples — no rules crossed their evidence thresholds."
                        else -> null
                    },
                )
            }
        }
    }

    fun requestShizukuPermission() {
        shizukuGateway.requestPermission()
    }
}
