package de.nexus.agent.core.domain.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Tool(
    name = "accessibility_control",
    description = "Control the device via Accessibility Service: tap, swipe, type text, or read UI structure. Requires active NexusAccessibilityService.",
    category = "system"
)
class AccessibilityControlTool : Tool {

    override val name: String = "accessibility_control"
    override val description: String = "Control the device via Accessibility Service: tap, swipe, type text, or read UI."
    override val parameterSchema: JsonSchema = JsonSchema(
        properties = mapOf(
            "action" to PropertySchema("string", "Action to perform", null, enum = listOf("tap", "swipe", "type", "read", "globalAction")),
            "x" to PropertySchema("integer", "X coordinate for tap/swipe", null),
            "y" to PropertySchema("integer", "Y coordinate for tap/swipe", null),
            "text" to PropertySchema("string", "Text to type (required for type action)", null),
            "direction" to PropertySchema("string", "Swipe direction", "up", enum = listOf("up", "down", "left", "right")),
            "duration" to PropertySchema("integer", "Swipe duration in ms (default 300)", 300),
            "globalAction" to PropertySchema("string", "Global action", null, enum = listOf("back", "home", "recents", "notifications", "powerDialog"))
        ),
        required = listOf("action")
    )

    override suspend fun execute(params: ToolExecutionParams): ToolResult = withContext(Dispatchers.Main) {
        val action = params.params["action"] as? String
            ?: return@withContext ToolResult.fail("Parameter 'action' is required")

        val service = NexusAccessibilityService.instance
        if (service == null) {
            return@withContext ToolResult.fail(
                "NexusAccessibilityService is not running. Enable it in Settings > Accessibility."
            )
        }

        when (action) {
            "tap" -> performTap(service, params)
            "swipe" -> performSwipe(service, params)
            "type" -> performType(service, params)
            "read" -> readUiStructure(service, params)
            "globalAction" -> performGlobalAction(service, params)
            else -> ToolResult.fail("Unknown action: $action")
        }
    }

    private fun performTap(service: NexusAccessibilityService, params: ToolExecutionParams): ToolResult {
        val x = (params.params["x"] as? Number)?.toFloat() ?: return ToolResult.fail("Parameter 'x' required")
        val y = (params.params["y"] as? Number)?.toFloat() ?: return ToolResult.fail("Parameter 'y' required")

        return try {
            val path = Path()
            path.moveTo(x, y)
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            var success = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    success = false
                }
            }, null)

            // Wait a bit for the gesture to complete
            Thread.sleep(200)
            if (success) {
                ToolResult.ok("Tap performed at ($x, $y)")
            } else {
                ToolResult.ok("Tap dispatched at ($x, $y) (result pending)")
            }
        } catch (e: Exception) {
            ToolResult.fail("Tap failed: ${e.message}")
        }
    }

    private fun performSwipe(service: NexusAccessibilityService, params: ToolExecutionParams): ToolResult {
        val direction = params.params["direction"] as? String ?: "up"
        val duration = (params.params["duration"] as? Number)?.toInt() ?: 300
        val x = (params.params["x"] as? Number)?.toFloat() ?: 540f
        val maxY = (params.params["y"] as? Number)?.toFloat() ?: 1800f

        val startX = x
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction) {
            "up" -> {
                startY = maxY * 0.8f
                endY = maxY * 0.2f
                endX = startX
            }
            "down" -> {
                startY = maxY * 0.2f
                endY = maxY * 0.8f
                endX = startX
            }
            "left" -> {
                startY = maxY * 0.5f
                endY = startY
                startX = x
                endX = x - 400f
            }
            "right" -> {
                startY = maxY * 0.5f
                endY = startY
                startX = x
                endX = x + 400f
            }
            else -> return ToolResult.fail("Unknown direction: $direction")
        }

        return try {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            val stroke = GestureDescription.StrokeDescription(path, 0, duration.toLong())
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {}
                override fun onCancelled(gestureDescription: GestureDescription?) {}
            }, null)

            ToolResult.ok("Swipe $direction performed from ($startX,$startY) to ($endX,$endY)")
        } catch (e: Exception) {
            ToolResult.fail("Swipe failed: ${e.message}")
        }
    }

    private fun performType(service: NexusAccessibilityService, params: ToolExecutionParams): ToolResult {
        val text = params.params["text"] as? String
            ?: return ToolResult.fail("Parameter 'text' is required for type action")

        return try {
            val rootNode = service.rootInActiveWindow
                ?: return ToolResult.fail("No active window node available")

            // Find the focused editable node
            val targetNode = findEditableNode(rootNode)
            if (targetNode == null) {
                // Try setting text on focused node
                rootNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                return ToolResult.fail("No editable text field found in active window")
            }

            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (success) {
                    ToolResult.ok("Text set: \"$text\"")
                } else {
                    ToolResult.fail("Failed to set text on node")
                }
            } else {
                // Fallback: clipboard + paste
                ToolResult.fail("Text input requires Android 5.0+ (API 21)")
            }
        } catch (e: Exception) {
            ToolResult.fail("Type action failed: ${e.message}")
        }
    }

    private fun readUiStructure(service: NexusAccessibilityService, params: ToolExecutionParams): ToolResult {
        return try {
            val rootNode = service.rootInActiveWindow
                ?: return ToolResult.fail("No active window node available")

            val elements = mutableListOf<UiElement>()
            collectNodes(rootNode, elements, 0)

            if (elements.isEmpty()) {
                ToolResult.ok("UI structure is empty.")
            } else {
                val formatted = elements.joinToString("\n") { element ->
                    val indent = "  ".repeat(element.depth)
                    val info = buildString {
                        append("[${element.className.substringAfterLast('.')}]")
                        if (element.text.isNotEmpty()) append(" \"${element.text.take(50)}\"")
                        if (element.contentDescription?.isNotEmpty() == true) append(" (desc: ${element.contentDescription})")
                        if (element.isClickable) append(" [clickable]")
                        if (element.isEditable) append(" [editable]")
                    }
                    "$indent• $info"
                }
                ToolResult.ok("UI structure (${elements.size} elements):\n$formatted", mapOf("elements" to elements))
            }
        } catch (e: Exception) {
            ToolResult.fail("Failed to read UI: ${e.message}")
        }
    }

    private fun performGlobalAction(service: NexusAccessibilityService, params: ToolExecutionParams): ToolResult {
        val actionName = params.params["globalAction"] as? String
            ?: return ToolResult.fail("Parameter 'globalAction' is required")

        val action = when (actionName) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "powerDialog" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            else -> return ToolResult.fail("Unknown global action: $actionName")
        }

        val success = service.performGlobalAction(action)
        if (success) {
            ToolResult.ok("Global action performed: $actionName")
        } else {
            ToolResult.fail("Global action failed: $actionName")
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun collectNodes(node: AccessibilityNodeInfo, results: MutableList<UiElement>, depth: Int) {
        results.add(
            UiElement(
                className = node.className?.toString() ?: "",
                text = node.text?.toString() ?: "",
                contentDescription = node.contentDescription?.toString(),
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isEnabled = node.isEnabled,
                bounds = mapOf(
                    "left" to node boundsInScreen.let { node.getBoundsInScreen(android.graphics.Rect()); 0 },
                    "depth" to depth
                ),
                depth = depth
            )
        )

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodes(child, results, depth + 1)
        }
    }
}

data class UiElement(
    val className: String,
    val text: String,
    val contentDescription: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isEnabled: Boolean,
    val bounds: Map<String, Int>,
    val depth: Int
)

/**
 * Reference to the Nexus Accessibility Service singleton.
 * This is a companion bridge - actual service implementation lives in feature-accessibility module.
 */
object NexusAccessibilityService {
    var instance: AccessibilityService? = null
}
