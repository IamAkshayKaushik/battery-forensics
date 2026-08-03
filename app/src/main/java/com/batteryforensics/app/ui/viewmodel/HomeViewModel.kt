package com.batteryforensics.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.diagnostics.DiagnosticsEngine
import com.batteryforensics.monitoring.MonitoringRepositoryImpl
import com.batteryforensics.permissions.AppPermissions
import com.batteryforensics.permissions.PermissionSpec
import com.batteryforensics.settings.SettingsRepository
import com.batteryforensics.settings.UserSettings
import com.batteryforensics.shizuku.ShizukuAvailability
import com.batteryforensics.shizuku.ShizukuGateway
import com.batteryforensics.shizuku.label
import com.batteryforensics.shizuku.limitedFeatures
import com.batteryforensics.statistics.StatisticsEngine
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

data class HomeUiState(
    val sampleCount: Long = 0,
    val latestBatteryPercent: Int? = null,
    val latestTempC: Float? = null,
    val latestNetwork: String? = null,
    val lastSampleAgeLabel: String? = null,
    val overnightDrainLabel: String? = null,
    val overallDrainLabel: String? = null,
    val topCauses: List<Diagnosis> = emptyList(),
    val missingPermissions: List<PermissionSpec> = emptyList(),
    val shizukuStatus: String = "Checking…",
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.NotInstalled,
    val shizukuLimited: List<String> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val flightRecorderOn: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitoringRepository: MonitoringRepositoryImpl,
    private val settingsRepository: SettingsRepository,
    private val shizukuGateway: ShizukuGateway,
) : ViewModel() {
    private val diagnosticsEngine = DiagnosticsEngine()
    private val shizukuTick = MutableStateFlow(0)
    private var binderReceived: Shizuku.OnBinderReceivedListener? = null
    private var binderDead: Shizuku.OnBinderDeadListener? = null
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null

    val uiState: StateFlow<HomeUiState> = combine(
        monitoringRepository.observeLatest(96),
        settingsRepository.settings,
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(monitoringRepository.sampleCount())
                kotlinx.coroutines.delay(4_000)
            }
        },
        shizukuTick,
    ) { latest, settings, count, _ ->
        val sample = latest.firstOrNull()
        val now = System.currentTimeMillis()
        val ageLabel = sample?.timestampEpochMs?.let { ageLabel(now - it) }
        val windowStart = now - 12 * 60 * 60 * 1000L
        val windowSamples = latest.filter { it.timestampEpochMs >= windowStart }
        val stats = if (windowSamples.size >= 4) {
            StatisticsEngine.analyze(windowSamples)
        } else {
            null
        }
        val overnight = stats?.standbyDrainPercentPerHour?.let {
            "${"%.1f".format(it)}%/h standby (screen-off)"
        }
        val overall = stats?.overallDrainPercentPerHour?.let {
            "${"%.1f".format(it)}%/h overall"
        }
        val causes = if (windowSamples.size >= 4) {
            diagnosticsEngine.investigate(windowSamples).diagnoses.take(3)
        } else {
            emptyList()
        }
        val shizuku = shizukuGateway.availability(context)
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
            lastSampleAgeLabel = ageLabel,
            overnightDrainLabel = overnight,
            overallDrainLabel = overall,
            topCauses = causes,
            missingPermissions = AppPermissions.criticalMissing(context),
            shizukuStatus = shizuku.label(),
            shizukuAvailability = shizuku,
            shizukuLimited = shizuku.limitedFeatures(),
            settings = settings,
            flightRecorderOn = settings.flightRecorderEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        val received = Shizuku.OnBinderReceivedListener { shizukuTick.update { it + 1 } }
        val dead = Shizuku.OnBinderDeadListener { shizukuTick.update { it + 1 } }
        val perm = Shizuku.OnRequestPermissionResultListener { _, _ -> shizukuTick.update { it + 1 } }
        binderReceived = received
        binderDead = dead
        permissionListener = perm
        shizukuGateway.addBinderReceivedListener(received)
        shizukuGateway.addBinderDeadListener(dead)
        shizukuGateway.addPermissionResultListener(perm)
    }

    override fun onCleared() {
        binderReceived?.let { shizukuGateway.removeBinderReceivedListener(it) }
        binderDead?.let { shizukuGateway.removeBinderDeadListener(it) }
        permissionListener?.let { shizukuGateway.removePermissionResultListener(it) }
        super.onCleared()
    }

    fun captureNow() {
        viewModelScope.launch { monitoringRepository.captureAndPersist() }
    }

    fun dismissSetupBanner() {
        viewModelScope.launch { settingsRepository.setSetupBannerDismissed(true) }
    }

    fun markInitialPromptShown() {
        viewModelScope.launch { settingsRepository.setInitialPermissionPromptShown(true) }
    }

    fun setFlightRecorder(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFlightRecorder(enabled) }
    }

    fun openShizukuManager() {
        shizukuGateway.managerLaunchIntent(context)?.let { context.startActivity(it) }
    }

    fun requestShizukuPermission() {
        shizukuGateway.requestPermission()
        shizukuTick.update { it + 1 }
    }

    private fun ageLabel(ageMs: Long): String {
        val mins = ageMs / 60_000L
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            else -> "${mins / 60}h ${mins % 60}m ago"
        }
    }
}
