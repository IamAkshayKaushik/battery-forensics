package com.batteryforensics.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.core.time.TimeConstants
import com.batteryforensics.monitoring.work.MonitoringWorker
import com.batteryforensics.permissions.AppPermissions
import com.batteryforensics.permissions.PermissionGrantStatus
import com.batteryforensics.permissions.PermissionSpec
import com.batteryforensics.settings.SettingsRepository
import com.batteryforensics.settings.UserSettings
import com.batteryforensics.shizuku.ShizukuAvailability
import com.batteryforensics.shizuku.ShizukuGateway
import com.batteryforensics.shizuku.label
import com.batteryforensics.shizuku.limitedFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject

data class PermissionRowUi(
    val spec: PermissionSpec,
    val status: PermissionGrantStatus,
)

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val permissions: List<PermissionRowUi> = emptyList(),
    val shizuku: ShizukuAvailability = ShizukuAvailability.NotInstalled,
    val shizukuLabel: String = "",
    val shizukuLimited: List<String> = emptyList(),
    val refreshTick: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val shizukuGateway: ShizukuGateway,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null
    private var binderReceived: Shizuku.OnBinderReceivedListener? = null
    private var binderDead: Shizuku.OnBinderDeadListener? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        refresh,
    ) { settings, tick ->
        val shizuku = shizukuGateway.availability(context)
        SettingsUiState(
            settings = settings,
            permissions = AppPermissions.allSpecs.map { spec ->
                PermissionRowUi(spec, AppPermissions.statusOf(context, spec))
            },
            shizuku = shizuku,
            shizukuLabel = shizuku.label(),
            shizukuLimited = shizuku.limitedFeatures(),
            refreshTick = tick,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        val received = Shizuku.OnBinderReceivedListener { refresh() }
        val dead = Shizuku.OnBinderDeadListener { refresh() }
        permissionListener = permListener
        binderReceived = received
        binderDead = dead
        shizukuGateway.addPermissionResultListener(permListener)
        shizukuGateway.addBinderReceivedListener(received)
        shizukuGateway.addBinderDeadListener(dead)
    }

    override fun onCleared() {
        permissionListener?.let { shizukuGateway.removePermissionResultListener(it) }
        binderReceived?.let { shizukuGateway.removeBinderReceivedListener(it) }
        binderDead?.let { shizukuGateway.removeBinderDeadListener(it) }
        super.onCleared()
    }

    fun refresh() {
        refresh.update { it + 1 }
    }

    fun setPeriodicMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPeriodicMonitoring(enabled)
            if (enabled) MonitoringWorker.enqueue(context) else MonitoringWorker.cancel(context)
        }
    }

    fun setFlightRecorder(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFlightRecorder(enabled) }
    }

    fun setSampleIntervalMs(intervalMs: Long) {
        viewModelScope.launch { settingsRepository.setSampleIntervalMs(intervalMs) }
    }

    fun setAdvancedDiagnostics(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAdvancedDiagnostics(enabled)
            if (enabled) {
                val avail = shizukuGateway.availability(context)
                if (avail == ShizukuAvailability.PermissionDenied || avail == ShizukuAvailability.Available) {
                    shizukuGateway.requestPermission()
                }
            }
            refresh()
        }
    }

    fun requestShizukuPermission() {
        shizukuGateway.requestPermission()
        refresh()
    }

    fun openShizukuManager() {
        shizukuGateway.managerLaunchIntent(context)?.let { context.startActivity(it) }
        refresh()
    }

    fun cycleSampleInterval() {
        val current = uiState.value.settings.sampleIntervalMs
        val next = when (current) {
            TimeConstants.FLIGHT_RECORDER_INTERVAL_MS -> TimeConstants.DEFAULT_SAMPLE_INTERVAL_MS
            TimeConstants.DEFAULT_SAMPLE_INTERVAL_MS -> 5 * TimeConstants.MILLIS_PER_MINUTE
            5 * TimeConstants.MILLIS_PER_MINUTE -> 15 * TimeConstants.MILLIS_PER_MINUTE
            else -> TimeConstants.FLIGHT_RECORDER_INTERVAL_MS
        }
        setSampleIntervalMs(next)
    }
}
