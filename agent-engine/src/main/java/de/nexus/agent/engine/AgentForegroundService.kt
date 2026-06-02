package de.nexus.agent.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject
    lateinit var wakeLock: PowerManager.WakeLock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTaskJob: Job? = null

    companion object {
        const val ACTION_START = "de.nexus.agent.engine.ACTION_START"
        const val ACTION_STOP = "de.nexus.agent.engine.ACTION_STOP"
        const val ACTION_UPDATE_PROGRESS = "de.nexus.agent.engine.ACTION_UPDATE_PROGRESS"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_MAX_PROGRESS = "max_progress"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "nexus_agent_foreground"
        const val CHANNEL_NAME = "Agent Hintergrundaufgaben"

        private const val INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
        private const val UPDATE_INTERVAL_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.i("AgentForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: "default"
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Nexus Agent"
                startAgentTask(taskId, taskName)
            }
            ACTION_STOP -> {
                stopAgentTask()
            }
            ACTION_UPDATE_PROGRESS -> {
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val maxProgress = intent.getIntExtra(EXTRA_MAX_PROGRESS, 100)
                updateProgress(progress, maxProgress)
            }
            else -> {
                Timber.w("Unknown action: ${intent?.action}")
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAgentTask(taskId: String, taskName: String) {
        // Cancel previous task
        currentTaskJob?.cancel()

        // Build and show initial notification
        val notification = buildNotification(
            title = taskName,
            message = "Agent-Aufgabe wird ausgeführt…",
            progress = 0,
            maxProgress = 100,
            indeterminate = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire(INACTIVITY_TIMEOUT_MS + 60_000L) // Extra minute buffer
            Timber.d("WakeLock acquired for task: $taskId")
        }

        // Start task execution
        currentTaskJob = serviceScope.launch {
            try {
                updateNotification("Ausführt…", 0, 100, true)

                // Simulate / actual task execution would hook in here
                // The task execution is managed by AgentTaskManager
                val startTime = System.currentTimeMillis()
                var progress = 0

                // Update progress periodically
                while (isActive && progress < 100) {
                    delay(UPDATE_INTERVAL_MS)
                    // Progress would be reported via broadcast/flow from actual task

                    // Check for inactivity timeout
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > INACTIVITY_TIMEOUT_MS) {
                        Timber.w("Inactivity timeout reached (${INACTIVITY_TIMEOUT_MS / 60000} min)")
                        updateNotification("Timeout – Aufgabe beendet", 100, 100, false)
                        break
                    }
                }

                if (progress >= 100) {
                    updateNotification("Aufgabe abgeschlossen", 100, 100, false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Agent task failed: ${e.message}")
                updateNotification("Fehler: ${e.message}", 0, 100, false)
            } finally {
                // Release wake lock when done
                if (wakeLock.isHeld) {
                    try {
                        wakeLock.release()
                        Timber.d("WakeLock released")
                    } catch (e: RuntimeException) {
                        Timber.w("WakeLock was already released")
                    }
                }

                // Keep notification briefly, then stop foreground
                delay(2000)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        Timber.i("Agent task started: $taskId ($taskName)")
    }

    private fun stopAgentTask() {
        currentTaskJob?.cancel()
        currentJob = null

        if (wakeLock.isHeld) {
            try {
                wakeLock.release()
            } catch (e: RuntimeException) {
                // Already released
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.i("Agent task stopped")
    }

    private fun updateProgress(progress: Int, maxProgress: Int) {
        Timber.d("Task progress: $progress / $maxProgress")
    }

    private fun updateNotification(
        message: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean
    ) {
        val notification = buildNotification(
            title = "Nexus Agent",
            message = message,
            progress = progress,
            maxProgress = maxProgress,
            indeterminate = indeterminate
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        message: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean
    ): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(maxProgress, progress, indeterminate)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt an, dass der Nexus Agent eine Hintergrundaufgabe ausführt"
            setShowBadge(false)
            setSound(null, null)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        currentTaskJob?.cancel()
        serviceScope.cancel()

        if (wakeLock.isHeld) {
            try {
                wakeLock.release()
            } catch (_: RuntimeException) {
            }
        }

        Timber.i("AgentForegroundService destroyed")
        super.onDestroy()
    }
}
