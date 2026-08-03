package com.batteryforensics.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.core.model.MonitoringSample
import com.batteryforensics.monitoring.MonitoringRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveMonitorUiState(
    val latest: MonitoringSample? = null,
    val recent: List<MonitoringSample> = emptyList(),
)

@HiltViewModel
class LiveMonitorViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepositoryImpl,
) : ViewModel() {
    val uiState: StateFlow<LiveMonitorUiState> = monitoringRepository.observeLatest(48)
        .map { list ->
            LiveMonitorUiState(
                latest = list.firstOrNull(),
                recent = list.asReversed(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveMonitorUiState())

    fun captureNow() {
        viewModelScope.launch { monitoringRepository.captureAndPersist() }
    }
}
