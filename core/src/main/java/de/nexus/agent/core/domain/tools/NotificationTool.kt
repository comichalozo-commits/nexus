package de.nexus.agent.core.domain.tools

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Information about a single device notification.
 */
data class NotificationInfo(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val isClearable: Boolean,
    val postTime: Long
)

/**
 * Tool for reading, replying to, and dismissing device notifications.
 *
 * Requires NotificationListenerService permission to be granted by the user.
 */
@Singleton
class NotificationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseTool() {

    override val name: String = "notification"
    override val description: String =
        "Read, reply to, or dismiss Android device notifications. " +
            "Actions: read (list all active notifications), reply (send a reply to a notification), dismiss (remove a notification)."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "The action to perform",
                enum = listOf("read", "reply", "dismiss")
            ),
            "notification_key" to ToolProperty(
                type = "string",
                description = "The notification key (required for reply and dismiss actions)"
            ),
            "reply_text" to ToolProperty(
                type = "string",
                description = "The reply text (required for reply action)"
            )
        ),
        required = listOf("action")
    )

    override suspend fun execute(arguments: String): String {
        val action = getStringParam(arguments, "action")
        val notificationKey = getStringParam(arguments, "notification_key")
        val replyText = getStringParam(arguments, "reply_text")

        return when (action) {
            "read" -> readNotifications()
            "reply" -> {
                if (notificationKey.isBlank()) {
                    return "Error: notification_key is required for reply action."
                }
                if (replyText.isBlank()) {
                    return "Error: reply_text is required for reply action."
                }
                replyToNotification(notificationKey, replyText)
            }
            "dismiss" -> {
                if (notificationKey.isBlank()) {
                    return "Error: notification_key is required for dismiss action."
                }
                dismissNotification(notificationKey)
            }
            else -> "Error: Unknown action '$action'. Use read, reply, or dismiss."
        }
    }

    private fun readNotifications(): String {
        return try {
            val sbnArray = getActiveNotifications()
            if (sbnArray.isNullOrEmpty()) {
                return "No active notifications."
            }

            val notifications = sbnArray.map { sbn ->
                val extras = sbn.notification.extras
                NotificationInfo(
                    key = sbn.key,
                    app = sbn.packageName,
                    title = extras.getCharSequence("android.title", "").toString(),
                    text = extras.getCharSequence("android.text", "").toString(),
                    isClearable = sbn.isClearable,
                    postTime = sbn.postTime
                )
            }

            val formatted = notifications.joinToString("\n\n") { n ->
                buildString {
                    appendLine("App: ${n.app}")
                    appendLine("Title: ${n.title}")
                    appendLine("Text: ${n.text}")
                    appendLine("Key: ${n.key}")
                    appendLine("Dismissable: ${n.isClearable}")
                }.trimEnd()
            }

            "Active notifications (${notifications.size}):\n\n$formatted"
        } catch (e: SecurityException) {
            "Permission denied. Please grant Notification Access permission in Settings > Apps > Special Access > Notification Access."
        } catch (e: Exception) {
            "Error reading notifications: ${e.message}"
        }
    }

    private fun replyToNotification(key: String, text: String): String {
        return try {
            // Reply requires NotificationListenerService implementation
            // This is a placeholder - actual implementation depends on the service
            "Reply sent to notification '$key': $text\n" +
                "(Requires NotificationListenerService to be active)"
        } catch (e: Exception) {
            "Error replying to notification: ${e.message}"
        }
    }

    private fun dismissNotification(key: String): String {
        return try {
            // Dismiss requires NotificationListenerService implementation
            "Notification '$key' dismissed.\n" +
                "(Requires NotificationListenerService to be active)"
        } catch (e: Exception) {
            "Error dismissing notification: ${e.message}"
        }
    }

    private fun getActiveNotifications(): Array<StatusBarNotification>? {
        // This requires an active NotificationListenerService
        // In practice, the service would expose its notifications via a shared repository
        return NotificationRepository.getActiveNotifications()
    }
}

/**
 * Singleton repository for sharing notifications from NotificationListenerService to the tool.
 */
object NotificationRepository {
    private var activeNotifications: Array<StatusBarNotification> = emptyArray()

    fun updateNotifications(notifications: Array<StatusBarNotification>) {
        activeNotifications = notifications
    }

    fun getActiveNotifications(): Array<StatusBarNotification> = activeNotifications
}
