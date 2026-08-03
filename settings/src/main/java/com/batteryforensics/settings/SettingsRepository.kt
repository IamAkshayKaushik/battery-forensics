package com.batteryforensics.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.batteryforensics.core.time.TimeConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "battery_forensics_settings")

data class UserSettings(
    val periodicMonitoringEnabled: Boolean = true,
    val flightRecorderEnabled: Boolean = false,
    val sampleIntervalMs: Long = TimeConstants.DEFAULT_SAMPLE_INTERVAL_MS,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val periodicKey = booleanPreferencesKey("periodic_monitoring")
    private val flightKey = booleanPreferencesKey("flight_recorder")
    private val intervalKey = longPreferencesKey("sample_interval_ms")

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            periodicMonitoringEnabled = prefs[periodicKey] ?: true,
            flightRecorderEnabled = prefs[flightKey] ?: false,
            sampleIntervalMs = prefs[intervalKey] ?: TimeConstants.DEFAULT_SAMPLE_INTERVAL_MS,
        )
    }

    suspend fun setPeriodicMonitoring(enabled: Boolean) {
        context.dataStore.edit { it[periodicKey] = enabled }
    }

    suspend fun setFlightRecorder(enabled: Boolean) {
        context.dataStore.edit { it[flightKey] = enabled }
    }
}
