package com.trailmap.gps.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.trailmap.gps.MainActivity
import com.trailmap.gps.R
import com.trailmap.gps.TrailMapApp
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.geo.ElevationStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecordingPhase {
    READY,
    RECORDING,
    PAUSED,
    STOPPED_UNSAVED
}

class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    companion object {
        const val CHANNEL_ID = "tracking"
        const val NOTIFICATION_ID = 1
        private val _phase = MutableStateFlow(RecordingPhase.READY)
        val phase: StateFlow<RecordingPhase> = _phase.asStateFlow()
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
        private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
        val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()
        private val _startedAt = MutableStateFlow(0L)
        val startedAt: StateFlow<Long> = _startedAt.asStateFlow()
        private val _pausedMs = MutableStateFlow(0L)
        val pausedMs: StateFlow<Long> = _pausedMs.asStateFlow()
        private var pauseStartedAt = 0L

        fun elapsedSeconds(now: Long = System.currentTimeMillis()): Long {
            val start = _startedAt.value
            if (start <= 0L) return 0L
            val paused = _pausedMs.value + if (_phase.value == RecordingPhase.PAUSED && pauseStartedAt > 0) {
                now - pauseStartedAt
            } else 0L
            return ((now - start - paused).coerceAtLeast(0L)) / 1000L
        }

        fun resetTrack() {
            _trackPoints.value = emptyList()
            _phase.value = RecordingPhase.READY
            _isRecording.value = false
            _startedAt.value = 0L
            _pausedMs.value = 0L
            pauseStartedAt = 0L
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.ACTION_START -> startRecording()
            Actions.ACTION_PAUSE -> pauseRecording()
            Actions.ACTION_RESUME -> resumeRecording()
            Actions.ACTION_STOP -> stopRecording()
            Actions.ACTION_DISCARD -> discardRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (_phase.value == RecordingPhase.RECORDING) return
        if (_phase.value != RecordingPhase.PAUSED) {
            _trackPoints.value = emptyList()
            _pausedMs.value = 0L
            _startedAt.value = System.currentTimeMillis()
        }
        _phase.value = RecordingPhase.RECORDING
        _isRecording.value = true
        startForeground(NOTIFICATION_ID, buildNotification("Recording track"))
        val app = application as TrailMapApp
        app.locationEngine.acquire(LocationSession.RECORDING)
        app.positionEngine.start()
        collectJob?.cancel()
        collectJob = scope.launch {
            app.positionEngine.snapshot.collect { snap ->
                val update = snap.toGpsUpdate() ?: return@collect
                if (!update.recordable) return@collect
                if (_phase.value == RecordingPhase.RECORDING) {
                    _trackPoints.value = _trackPoints.value + TrackPoint(
                        lat = update.latitude,
                        lon = update.longitude,
                        elevation = update.elevation,
                        time = update.timestamp
                    )
                }
            }
        }
    }

    private fun pauseRecording() {
        if (_phase.value != RecordingPhase.RECORDING) return
        _phase.value = RecordingPhase.PAUSED
        _isRecording.value = true
        pauseStartedAt = System.currentTimeMillis()
        startForeground(NOTIFICATION_ID, buildNotification("Recording paused"))
    }

    private fun resumeRecording() {
        if (_phase.value != RecordingPhase.PAUSED) return
        if (pauseStartedAt > 0L) {
            _pausedMs.value += System.currentTimeMillis() - pauseStartedAt
            pauseStartedAt = 0L
        }
        startRecording()
    }

    private fun stopRecording() {
        collectJob?.cancel()
        (application as TrailMapApp).locationEngine.release(LocationSession.RECORDING)
        if (pauseStartedAt > 0L) {
            _pausedMs.value += System.currentTimeMillis() - pauseStartedAt
            pauseStartedAt = 0L
        }
        _phase.value = if (_trackPoints.value.size >= 2) {
            RecordingPhase.STOPPED_UNSAVED
        } else {
            RecordingPhase.READY
        }
        _isRecording.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (_phase.value == RecordingPhase.READY) {
            resetTrack()
            stopSelf()
        }
    }

    private fun discardRecording() {
        collectJob?.cancel()
        (application as TrailMapApp).locationEngine.release(LocationSession.RECORDING)
        resetTrack()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "GPS Tracking", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    object Actions {
        const val ACTION_START = "com.trailmap.gps.START_TRACKING"
        const val ACTION_PAUSE = "com.trailmap.gps.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.trailmap.gps.RESUME_TRACKING"
        const val ACTION_STOP = "com.trailmap.gps.STOP_TRACKING"
        const val ACTION_DISCARD = "com.trailmap.gps.DISCARD_TRACKING"
    }
}
