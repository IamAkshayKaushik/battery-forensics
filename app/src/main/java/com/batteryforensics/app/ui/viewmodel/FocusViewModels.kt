package com.batteryforensics.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.batteryforensics.analytics.NetworkForensics
import com.batteryforensics.battery.ChemistryEngine
import com.batteryforensics.diagnostics.DifferentialAnalyzer
import com.batteryforensics.monitoring.MonitoringRepository
import com.batteryforensics.shizuku.ShizukuDiagnosticsCollector
import com.batteryforensics.thermal.ThermalAnalyzer
import com.batteryforensics.timeline.PrivilegedTimelineInput
import com.batteryforensics.timeline.TimelineBuilder
import com.batteryforensics.timeline.TimelineEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChemistryUiState(
    val report: ChemistryEngine.ChemistryReport? = null,
    val sampleCount: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class ChemistryViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChemistryUiState())
    val uiState: StateFlow<ChemistryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            val start = end - 7 * 24 * 60 * 60 * 1000L
            val samples = monitoringRepository.samplesBetween(start, end)
            if (samples.size < 4) {
                _uiState.update {
                    ChemistryUiState(
                        sampleCount = samples.size,
                        message = "Need more charge/discharge history (have ${samples.size}). Keep monitoring running.",
                    )
                }
                return@launch
            }
            _uiState.update {
                ChemistryUiState(
                    report = ChemistryEngine.analyze(samples),
                    sampleCount = samples.size,
                )
            }
        }
    }
}

data class ThermalUiState(
    val report: ThermalAnalyzer.ThermalReport? = null,
    val sampleCount: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class ThermalViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000L
            val samples = monitoringRepository.samplesBetween(start, end)
            if (samples.isEmpty()) {
                _uiState.update {
                    ThermalUiState(message = "No samples in the last 24h. Capture from Live Monitor.")
                }
                return@launch
            }
            _uiState.update {
                ThermalUiState(
                    report = ThermalAnalyzer.analyze(samples, start, end),
                    sampleCount = samples.size,
                )
            }
        }
    }
}

data class NetworkUiState(
    val report: NetworkForensics.NetworkReport? = null,
    val sampleCount: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000L
            val samples = monitoringRepository.samplesBetween(start, end)
            if (samples.isEmpty()) {
                _uiState.update {
                    NetworkUiState(message = "No samples yet. Grant location permission for cellular RSSI when possible.")
                }
                return@launch
            }
            _uiState.update {
                NetworkUiState(
                    report = NetworkForensics.analyze(samples),
                    sampleCount = samples.size,
                )
            }
        }
    }
}

data class TimelineUiState(
    val events: List<TimelineEvent> = emptyList(),
    val overnight: List<TimelineEvent> = emptyList(),
    val mode: String = "all",
    val message: String? = null,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitoringRepository: MonitoringRepository,
    private val shizukuCollector: ShizukuDiagnosticsCollector,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            val start = end - 12 * 60 * 60 * 1000L
            val samples = monitoringRepository.samplesBetween(start, end)
            if (samples.isEmpty()) {
                _uiState.update {
                    TimelineUiState(message = "No samples to replay. Capture a Flight Recorder or periodic window first.")
                }
                return@launch
            }
            val privileged = withContext(Dispatchers.IO) {
                runCatching {
                    val snap = shizukuCollector.collect(context)
                    val ev = snap.toPrivilegedEvidence()
                    if (!ev.hasAnyData) null
                    else PrivilegedTimelineInput(
                        referenceEpochMs = end,
                        dozeState = ev.doze?.state ?: ev.deviceIdle?.state,
                        dozeHistoryHints = ev.doze?.historyHints.orEmpty(),
                        motionInterruptions = ev.doze?.motionTriggeredInterruptions ?: 0,
                        locationInterruptions = ev.doze?.locationTriggeredInterruptions ?: 0,
                        alarmWakeups = ev.alarms?.wakeupAlarmCount,
                        topAlarmPackages = ev.alarms?.topPackages.orEmpty().map { it.packageName },
                        gmsWakeupHints = ev.alarms?.topPackages.orEmpty()
                            .filter {
                                it.packageName.contains("gms", ignoreCase = true) ||
                                    it.packageName.contains("google", ignoreCase = true)
                            }
                            .map { it.packageName },
                    )
                }.getOrNull()
            }
            _uiState.update {
                TimelineUiState(
                    events = TimelineBuilder.build(samples, privileged = privileged),
                    overnight = TimelineBuilder.overnightReplay(samples),
                    mode = "all",
                )
            }
        }
    }

    fun showOvernight() {
        _uiState.update { it.copy(mode = "overnight") }
    }

    fun showAll() {
        _uiState.update { it.copy(mode = "all") }
    }
}

data class DifferentialUiState(
    val report: DifferentialAnalyzer.DifferentialReport? = null,
    val message: String? = null,
)

@HiltViewModel
class DifferentialViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DifferentialUiState())
    val uiState: StateFlow<DifferentialUiState> = _uiState.asStateFlow()

    /** Compares previous night (24–48h ago) vs last night (0–24h). */
    fun compareNights() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val day = 24 * 60 * 60 * 1000L
            val problem = monitoringRepository.samplesBetween(now - day, now)
            val healthy = monitoringRepository.samplesBetween(now - 2 * day, now - day)
            if (problem.size < 3 || healthy.size < 3) {
                _uiState.update {
                    DifferentialUiState(
                        message = "Need samples in both windows (healthy=${healthy.size}, problem=${problem.size}).",
                    )
                }
                return@launch
            }
            _uiState.update {
                DifferentialUiState(report = DifferentialAnalyzer.compare(healthy, problem))
            }
        }
    }
}
