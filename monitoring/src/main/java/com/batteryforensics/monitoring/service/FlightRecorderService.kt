package com.batteryforensics.monitoring.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.batteryforensics.core.time.TimeConstants
import com.batteryforensics.monitoring.MonitoringRepositoryImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Optional continuous sampler ("Flight Recorder").
 * Justified as a foreground service because WorkManager's minimum period (15 min)
 * is too coarse for forensic timeline reconstruction when the user explicitly enables it.
 */
@AndroidEntryPoint
class FlightRecorderService : Service() {
    @Inject lateinit var repository: MonitoringRepositoryImpl

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startRecorder()
        }
        return START_STICKY
    }

    private fun startRecorder() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                runCatching { repository.captureAndPersist() }
                delay(TimeConstants.FLIGHT_RECORDER_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Flight Recorder",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Continuous on-device sampling for battery forensics"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FlightRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flight Recorder active")
            .setContentText("Sampling battery signals on-device. Nothing is uploaded.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "flight_recorder"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.batteryforensics.monitoring.STOP_FLIGHT_RECORDER"
    }
}
