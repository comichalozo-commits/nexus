package de.nexus.agent.feature.overlay

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import de.nexus.agent.core.ServiceLocator

/**
 * Accessibility-based service that renders a floating chat overlay on screen.
 *
 * Capabilities:
 * - Shows a floating chat head that can be tapped to expand
 * - Detects foreground app changes via accessibility events
 * - Sends user messages to the core AgentLoop
 * - Runs as a foreground service with a persistent notification
 *
 * To start: bind from an Activity or call startService with the action START.
 */
class FloatingChatService : AccessibilityService() {

    companion object {
        const val CHANNEL_ID = "nexus_overlay_channel"
        const val CHANNEL_NAME = "Nexus Overlay Service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "de.nexus.agent.feature.overlay.ACTION_START"
        const val ACTION_STOP = "de.nexus.agent.feature.overlay.ACTION_STOP"
        const val ACTION_TOGGLE = "de.nexus.agent.feature.overlay.ACTION_TOGGLE"
    }

    private var overlayView: OverlayView? = null
    private var viewModel: OverlayViewModel? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        if (viewModel == null) {
            viewModel = OverlayViewModel()
        }

        try {
            ServiceLocator.initialize(applicationContext)
        } catch (_: Exception) {
            // Already initialized
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                viewModel?.onAppChanged(event.packageName?.toString() ?: "")
            }
        }
    }

    override fun onInterrupt() {
        // No-op - overlay persists across interruptions
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                hideOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                overlayView?.toggle()
            }
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView?.isShowing == true) return

        val vm = viewModel ?: return
        val view = OverlayView(this, vm)
        view.show()
        overlayView = view
    }

    private fun hideOverlay() {
        overlayView?.hide()
        overlayView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zeigt an, dass Nexus Agent als Overlay läuft"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Nexus Agent Overlay")
                .setContentText("Overlay ist aktiv")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Nexus Agent Overlay")
                .setContentText("Overlay ist aktiv")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setOngoing(true)
                .build()
        }
    }
}
