package com.batteryforensics.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.monitoring.work.MonitoringWorker
import com.batteryforensics.settings.SettingsRepository
import com.batteryforensics.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val uiState: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setPeriodicMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPeriodicMonitoring(enabled)
            if (enabled) MonitoringWorker.enqueue(context) else MonitoringWorker.cancel(context)
        }
    }

    fun setFlightRecorder(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFlightRecorder(enabled) }
    }
}
