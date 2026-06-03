package de.nexus.agent.feature.overlay

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber

/**
 * Listens to notifications and forwards them to the agent system.
 */
class NotificationListenerServiceImpl : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.i("NotificationListener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            Timber.d("Notification posted: ${it.packageName} - ${it.notification.tickerText}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            Timber.d("Notification removed: ${it.packageName}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Timber.i("NotificationListener disconnected")
    }
}
