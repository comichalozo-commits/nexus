package de.nexus.agent.core.domain.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool for reading and writing clipboard content.
 */
@Singleton
class ClipboardTool @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseTool() {

    override val name: String = "clipboard"
    override val description: String =
        "Read or write clipboard content on the Android device. " +
            "Actions: read (get current clipboard text), write (set clipboard text)."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "The action to perform",
                enum = listOf("read", "write")
            ),
            "text" to ToolProperty(
                type = "string",
                description = "The text to write to clipboard (required for write action)"
            )
        ),
        required = listOf("action")
    )

    override suspend fun execute(arguments: String): String {
        val action = getStringParam(arguments, "action")
        val text = getStringParam(arguments, "text")

        return when (action) {
            "read" -> readClipboard()
            "write" -> {
                if (text.isBlank()) {
                    return "Error: text is required for write action."
                }
                writeClipboard(text)
            }
            else -> "Error: Unknown action '$action'. Use read or write."
        }
    }

    private fun readClipboard(): String {
        return try {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return "Error: Clipboard service not available."

            if (!clipboardManager.hasPrimaryClip()) {
                return "Clipboard is empty."
            }

            val clipData = clipboardManager.primaryClip
            if (clipData == null || clipData.itemCount == 0) {
                return "Clipboard is empty."
            }

            val item = clipData.getItemAt(0)
            val clipText = item.text?.toString() ?: item.coerceToText(context).toString()

            if (clipText.isBlank()) {
                "Clipboard is empty."
            } else {
                "Clipboard content:\n$clipText"
            }
        } catch (e: SecurityException) {
            "Permission denied: ${e.message}"
        } catch (e: Exception) {
            "Error reading clipboard: ${e.message}"
        }
    }

    private fun writeClipboard(text: String): String {
        return try {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return "Error: Clipboard service not available."

            val clipData = ClipData.newPlainText("Nexus Agent", text)
            clipboardManager.setPrimaryClip(clipData)

            "Clipboard set to: ${text.take(100)}${if (text.length > 100) "..." else ""}"
        } catch (e: Exception) {
            "Error writing clipboard: ${e.message}"
        }
    }
}
