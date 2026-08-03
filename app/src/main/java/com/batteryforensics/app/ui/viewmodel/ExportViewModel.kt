package com.batteryforensics.app.ui.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.core.model.DeviceInfo
import com.batteryforensics.diagnostics.DiagnosticsEngine
import com.batteryforensics.export.ExportFormat
import com.batteryforensics.export.ExportGenerator
import com.batteryforensics.monitoring.MonitoringRepository
import com.batteryforensics.reporting.ReportBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val markdownPreview: String = "",
    val jsonLength: Int = 0,
    val formatsSummary: String = "",
    val error: String? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun generate() {
        viewModelScope.launch {
            runCatching {
                val end = System.currentTimeMillis()
                val start = end - 24 * 60 * 60 * 1000L
                val samples = monitoringRepository.samplesBetween(start, end)
                val investigation = DiagnosticsEngine().investigate(samples)
                val report = ReportBuilder().build(
                    device = DeviceInfo(
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        sdkInt = Build.VERSION.SDK_INT,
                    ),
                    samples = samples,
                    investigation = investigation,
                )
                val artifacts = ExportGenerator().export(
                    report,
                    ExportFormat.entries.toSet(),
                )
                val md = artifacts.first { it.format == ExportFormat.MARKDOWN }.content
                val json = artifacts.first { it.format == ExportFormat.JSON }.content
                _uiState.update {
                    ExportUiState(
                        markdownPreview = md,
                        jsonLength = json.length,
                        formatsSummary = artifacts.joinToString { "${it.format}: ${it.fileName}" },
                        error = null,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Export failed") }
            }
        }
    }
}
