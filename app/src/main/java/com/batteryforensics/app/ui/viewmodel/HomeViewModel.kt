package com.batteryforensics.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.monitoring.MonitoringRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sampleCount: Long = 0,
    val latestBatteryPercent: Int? = null,
    val latestTempC: Float? = null,
    val latestNetwork: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepositoryImpl,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        monitoringRepository.observeLatest(1),
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(monitoringRepository.sampleCount())
                kotlinx.coroutines.delay(5_000)
            }
        },
    ) { latest, count ->
        val sample = latest.firstOrNull()
        HomeUiState(
            sampleCount = count,
            latestBatteryPercent = sample?.batteryPercent,
            latestTempC = sample?.temperatureC,
            latestNetwork = sample?.networkType?.let { type ->
                buildString {
                    append(type)
                    sample.cellularRssiDbm?.let { append(" · $it dBm") }
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun captureNow() {
        viewModelScope.launch { monitoringRepository.captureAndPersist() }
    }
}
