package com.batteryforensics.monitoring.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.batteryforensics.core.time.TimeConstants
import com.batteryforensics.monitoring.MonitoringRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MonitoringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MonitoringRepositoryImpl,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        repository.captureAndPersist()
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val UNIQUE_WORK = "battery_forensics_periodic_monitor"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(
                15,
                TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}

/** Interval constants used by Flight Recorder (foreground path). */
object MonitoringIntervals {
    val periodicMs: Long = TimeConstants.DEFAULT_SAMPLE_INTERVAL_MS
    val flightRecorderMs: Long = TimeConstants.FLIGHT_RECORDER_INTERVAL_MS
}
