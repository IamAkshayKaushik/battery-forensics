package com.batteryforensics.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.batteryforensics.analytics.NetworkForensics
import com.batteryforensics.battery.ChemistryEngine
import com.batteryforensics.diagnostics.DifferentialAnalyzer
import com.batteryforensics.diagnostics.NightWindow
import com.batteryforensics.diagnostics.NightWindowFinder
import com.batteryforensics.monitoring.MonitoringRepository
import com.batteryforensics.settings.SettingsRepository
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
import kotlinx.coroutines.flow.first
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
    val nights: List<NightWindow> = emptyList(),
    val healthyNightId: String? = null,
    val problemNightId: String? = null,
    val healthySampleCount: Int = 0,
    val problemSampleCount: Int = 0,
    val report: DifferentialAnalyzer.DifferentialReport? = null,
    val message: String? = null,
    val comparing: Boolean = false,
)

@HiltViewModel
class DifferentialViewModel @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DifferentialUiState())
    val uiState: StateFlow<DifferentialUiState> = _uiState.asStateFlow()

    init {
        refreshWindows()
    }

    fun refreshWindows() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nights = NightWindowFinder.candidateNights(now)
            val lookbackStart = nights.lastOrNull()?.startEpochMs ?: (now - 7 * 24 * 60 * 60 * 1000L)
            val samples = monitoringRepository.samplesBetween(lookbackStart, now)
            val prefs = settingsRepository.settings.first()
            val defaultPair = NightWindowFinder.defaultHealthyAndProblem(nights, samples)
            val healthyId = prefs.healthyNightWindowId
                ?.takeIf { id -> nights.any { it.id == id } }
                ?: defaultPair?.healthy?.id
            val problemId = prefs.problemNightWindowId
                ?.takeIf { id -> nights.any { it.id == id } }
                ?: defaultPair?.problem?.id
            val healthy = nights.firstOrNull { it.id == healthyId }
            val problem = nights.firstOrNull { it.id == problemId }
            _uiState.update {
                it.copy(
                    nights = nights,
                    healthyNightId = healthyId,
                    problemNightId = problemId,
                    healthySampleCount = healthy?.let { NightWindowFinder.samplesIn(samples, it).size } ?: 0,
                    problemSampleCount = problem?.let { NightWindowFinder.samplesIn(samples, it).size } ?: 0,
                    message = null,
                )
            }
        }
    }

    fun selectHealthy(nightId: String) {
        viewModelScope.launch {
            settingsRepository.setHealthyNightWindowId(nightId)
            _uiState.update { it.copy(healthyNightId = nightId, report = null) }
            refreshSampleCounts()
        }
    }

    fun selectProblem(nightId: String) {
        viewModelScope.launch {
            settingsRepository.setProblemNightWindowId(nightId)
            _uiState.update { it.copy(problemNightId = nightId, report = null) }
            refreshSampleCounts()
        }
    }

    private suspend fun refreshSampleCounts() {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val lookbackStart = state.nights.lastOrNull()?.startEpochMs ?: (now - 7 * 24 * 60 * 60 * 1000L)
        val samples = monitoringRepository.samplesBetween(lookbackStart, now)
        val healthy = state.nights.firstOrNull { it.id == state.healthyNightId }
        val problem = state.nights.firstOrNull { it.id == state.problemNightId }
        _uiState.update {
            it.copy(
                healthySampleCount = healthy?.let { NightWindowFinder.samplesIn(samples, it).size } ?: 0,
                problemSampleCount = problem?.let { NightWindowFinder.samplesIn(samples, it).size } ?: 0,
            )
        }
    }

    /** Compares user-picked healthy vs problem overnight windows (22:00–08:00). */
    fun compareNights() {
        viewModelScope.launch {
            _uiState.update { it.copy(comparing = true, message = null) }
            val state = _uiState.value
            val healthyWin = state.nights.firstOrNull { it.id == state.healthyNightId }
            val problemWin = state.nights.firstOrNull { it.id == state.problemNightId }
            if (healthyWin == null || problemWin == null) {
                _uiState.update {
                    it.copy(comparing = false, message = "Pick a healthy night and a problem night to compare.")
                }
                return@launch
            }
            if (healthyWin.id == problemWin.id) {
                _uiState.update {
                    it.copy(comparing = false, message = "Healthy and problem windows must be different nights.")
                }
                return@launch
            }
            val healthy = monitoringRepository.samplesBetween(healthyWin.startEpochMs, healthyWin.endEpochMs)
            val problem = monitoringRepository.samplesBetween(problemWin.startEpochMs, problemWin.endEpochMs)
            if (problem.size < 3 || healthy.size < 3) {
                _uiState.update {
                    it.copy(
                        comparing = false,
                        healthySampleCount = healthy.size,
                        problemSampleCount = problem.size,
                        message = "Need ≥3 samples in both windows (healthy=${healthy.size}, problem=${problem.size}). " +
                            "Start Flight Recorder overnight, then compare.",
                        report = null,
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    comparing = false,
                    healthySampleCount = healthy.size,
                    problemSampleCount = problem.size,
                    report = DifferentialAnalyzer.compare(healthy, problem),
                    message = null,
                )
            }
        }
    }
}
