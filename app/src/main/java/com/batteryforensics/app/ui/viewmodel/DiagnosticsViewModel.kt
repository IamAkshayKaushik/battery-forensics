package com.batteryforensics.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.diagnostics.DiagnosticsEngine
import com.batteryforensics.monitoring.MonitoringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val diagnoses: List<Diagnosis> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val engine = DiagnosticsEngine()
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    fun investigate() {
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            val start = end - 12 * 60 * 60 * 1000L
            val samples = monitoringRepository.samplesBetween(start, end)
            if (samples.isEmpty()) {
                _uiState.update {
                    it.copy(
                        diagnoses = emptyList(),
                        message = "No samples in the last 12 hours. Capture samples from Live Monitor first.",
                    )
                }
                return@launch
            }
            val result = engine.investigate(samples)
            _uiState.update {
                it.copy(
                    diagnoses = result.diagnoses,
                    message = if (result.diagnoses.isEmpty()) {
                        "Evaluated ${result.sampleCount} samples — no rules crossed their evidence thresholds."
                    } else {
                        null
                    },
                )
            }
        }
    }
}
