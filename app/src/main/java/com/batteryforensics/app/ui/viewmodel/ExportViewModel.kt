package com.batteryforensics.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.app.export.ExportShareHelper
import com.batteryforensics.core.model.DeviceInfo
import com.batteryforensics.diagnostics.DiagnosticsEngine
import com.batteryforensics.export.ExportFormat
import com.batteryforensics.export.ExportGenerator
import com.batteryforensics.monitoring.MonitoringRepository
import com.batteryforensics.reporting.ReportBuilder
import com.batteryforensics.settings.SettingsRepository
import com.batteryforensics.shizuku.ShizukuAvailability
import com.batteryforensics.shizuku.ShizukuDiagnosticsCollector
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
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val markdownPreview: String = "",
    val jsonLength: Int = 0,
    val formatsSummary: String = "",
    val savedPath: String? = null,
    val shareReady: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
    private val shizukuCollector: ShizukuDiagnosticsCollector,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var lastSaved: ExportShareHelper.SavedExport? = null

    fun generate() {
        viewModelScope.launch {
            runCatching {
                val end = System.currentTimeMillis()
                val start = end - 24 * 60 * 60 * 1000L
                val samples = monitoringRepository.samplesBetween(start, end)
                val baseline = monitoringRepository.samplesBetween(start - 24 * 60 * 60 * 1000L, start)
                val settings = settingsRepository.settings.first()
                val (privilegedFindings, privilegedEvidence) = if (settings.advancedDiagnosticsEnabled) {
                    withContext(Dispatchers.IO) {
                        val snap = shizukuCollector.collect(context)
                        val lines = buildList {
                            if (snap.availability == ShizukuAvailability.Available && snap.hasAnyData) {
                                snap.deviceIdle?.let { add("deviceidle state=${it.state} deep=${it.deepEnabled}") }
                                snap.alarms?.let { add("alarms wakeups=${it.wakeupAlarmCount}") }
                                snap.wakeLocks?.let { add("wakelocks total=${it.totalLocks}") }
                                snap.jobs?.let { add("jobs pending=${it.pendingJobCount}") }
                                snap.activity?.let { add("activity fgs=${it.foregroundServiceHints.take(3)}") }
                                snap.cmdBattery?.let { add("cmd battery level=${it.level}") }
                                snap.wifi?.let {
                                    add("wifi rssi=${it.connectedRssiDbm} scanning=${it.isScanning}")
                                }
                                snap.connectivity?.let {
                                    add("connectivity transports=${it.transports}")
                                }
                                snap.location?.let {
                                    add("location providers=${it.providersEnabled}")
                                }
                                snap.sensors?.let {
                                    add("sensors active=${it.activeSensorCount}")
                                }
                                snap.notifications?.let {
                                    add("notifications active=${it.activeNotificationCount}")
                                }
                                snap.batteryStats?.checkinUidDrainHints?.take(3)?.forEach {
                                    add("batterystats checkin ${it.packageName}=${it.count}")
                                }
                            } else {
                                add(snap.availability.toString())
                            }
                            addAll(snap.errors.take(5))
                        }
                        lines to snap.toPrivilegedEvidence().takeIf { it.hasAnyData }
                    }
                } else {
                    listOf("Advanced diagnostics disabled") to null
                }

                val investigation = DiagnosticsEngine().investigate(
                    samples = samples,
                    privileged = privilegedEvidence,
                    baselineSamples = baseline,
                )
                val report = ReportBuilder().build(
                    device = DeviceInfo(
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        sdkInt = Build.VERSION.SDK_INT,
                    ),
                    samples = samples,
                    investigation = investigation,
                    privilegedFindings = privilegedFindings,
                )
                val roomDb = File(context.getDatabasePath("battery_forensics.db").absolutePath)
                val roomBytes = withContext(Dispatchers.IO) {
                    roomDb.takeIf { it.exists() }?.readBytes()
                }
                val artifacts = ExportGenerator().export(
                    report,
                    ExportFormat.entries.toSet(),
                    roomDbBytes = roomBytes,
                )
                val saved = withContext(Dispatchers.IO) {
                    ExportShareHelper.saveArtifacts(context, artifacts, roomDbFile = roomDb)
                }
                lastSaved = saved
                val md = artifacts.first { it.format == ExportFormat.MARKDOWN }.content
                val json = artifacts.first { it.format == ExportFormat.JSON }.content
                _uiState.update {
                    ExportUiState(
                        markdownPreview = md,
                        jsonLength = json.length,
                        formatsSummary = artifacts.joinToString { "${it.format}: ${it.fileName}" },
                        savedPath = saved.sessionDir.absolutePath,
                        shareReady = true,
                        error = null,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Export failed", shareReady = false) }
            }
        }
    }

    fun shareIntentOrNull(): Intent? {
        val saved = lastSaved ?: return null
        return ExportShareHelper.shareIntent(context, saved)
    }
}
