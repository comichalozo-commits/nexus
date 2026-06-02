package de.nexus.agent.core.domain.tools

/**
 * Defines the autonomy level for tool execution.
 *
 * - FULL_AUTO: Tool can run without any confirmation.
 * - BALANCED: Destructive or external-facing tools require confirmation.
 * - CAUTIOUS: Most tools require explicit user confirmation.
 */
enum class AutonomyPolicy {
    FULL_AUTO,
    BALANCED,
    CAUTIOUS
}

/**
 * Engine that determines whether a tool is allowed to run
 * and whether it requires user confirmation based on the
 * current autonomy policy.
 */
object AutonomyPolicyEngine {

    /**
     * Maps tool names to the minimum autonomy policy required
     * for execution without confirmation.
     */
    private val toolPolicyMap: Map<String, AutonomyPolicy> = mapOf(
        // External communication — always cautious
        "sms.send" to AutonomyPolicy.CAUTIOUS,
        "phone.call" to AutonomyPolicy.CAUTIOUS,
        "email.send" to AutonomyPolicy.CAUTIOUS,
        "messenger.send" to AutonomyPolicy.CAUTIOUS,

        // Destructive operations — cautious
        "file.delete" to AutonomyPolicy.CAUTIOUS,
        "shell.execute" to AutonomyPolicy.CAUTIOUS,
        "shell.exec" to AutonomyPolicy.CAUTIOUS,
        "app.uninstall" to AutonomyPolicy.CAUTIOUS,
        "settings.modify" to AutonomyPolicy.CAUTIOUS,

        // Sensitive data access — balanced
        "notification" to AutonomyPolicy.BALANCED,
        "clipboard" to AutonomyPolicy.BALANCED,
        "location" to AutonomyPolicy.BALANCED,
        "camera" to AutonomyPolicy.BALANCED,
        "microphone" to AutonomyPolicy.BALANCED,
        "contacts" to AutonomyPolicy.BALANCED,
        "calendar" to AutonomyPolicy.BALANCED,
        "call_log" to AutonomyPolicy.BALANCED,

        // File operations — balanced (read is safer than write)
        "file_operation" to AutonomyPolicy.BALANCED,
        "file.write" to AutonomyPolicy.BALANCED,
        "file.read" to AutonomyPolicy.FULL_AUTO,

        // Safe operations — full auto
        "web_search" to AutonomyPolicy.FULL_AUTO,
        "web_fetch" to AutonomyPolicy.FULL_AUTO,
        "web.search" to AutonomyPolicy.FULL_AUTO,
        "web.fetch" to AutonomyPolicy.FULL_AUTO,
        "memory" to AutonomyPolicy.FULL_AUTO,
        "memory.store" to AutonomyPolicy.FULL_AUTO,
        "memory.recall" to AutonomyPolicy.FULL_AUTO,
        "memory.search" to AutonomyPolicy.FULL_AUTO,
        "schedule" to AutonomyPolicy.FULL_AUTO,
        "browser" to AutonomyPolicy.FULL_AUTO,
        "weather" to AutonomyPolicy.FULL_AUTO,
        "calculator" to AutonomyPolicy.FULL_AUTO,
        "timer" to AutonomyPolicy.FULL_AUTO,
        "alarm" to AutonomyPolicy.FULL_AUTO
    )

    /**
     * Returns true if the tool is allowed to run under the given policy.
     * All tools are allowed under FULL_AUTO. Under CAUTIOUS, only
     * FULL_AUTO-mapped tools run without confirmation.
     */
    fun isAllowed(toolName: String, policy: AutonomyPolicy): Boolean {
        val minPolicy = getMinPolicy(toolName)
        return when (policy) {
            AutonomyPolicy.FULL_AUTO -> true
            AutonomyPolicy.BALANCED -> minPolicy != AutonomyPolicy.CAUTIOUS
            AutonomyPolicy.CAUTIOUS -> minPolicy == AutonomyPolicy.FULL_AUTO
        }
    }

    /**
     * Returns true if the tool requires explicit user confirmation
     * under the given policy.
     */
    fun requiresConfirmation(toolName: String, policy: AutonomyPolicy): Boolean {
        val minPolicy = getMinPolicy(toolName)
        return when (policy) {
            AutonomyPolicy.FULL_AUTO -> false
            AutonomyPolicy.BALANCED -> minPolicy == AutonomyPolicy.CAUTIOUS
            AutonomyPolicy.CAUTIOUS -> minPolicy != AutonomyPolicy.FULL_AUTO
        }
    }

    /**
     * Returns the minimum autonomy policy required for the given tool.
     * Tools not in the default to BALANCED.
     */
    fun getMinPolicy(toolName: String): AutonomyPolicy {
        return toolPolicyMap[toolName] ?: AutonomyPolicy.BALANCED
    }

    /**
     * Returns a human-readable description of the given policy level.
     */
    fun getPolicyDescription(policy: AutonomyPolicy): String {
        return when (policy) {
            AutonomyPolicy.FULL_AUTO ->
                "Full Autonomy: All tools run without confirmation. " +
                    "The agent can execute any action independently."

            AutonomyPolicy.BALANCED ->
                "Balanced: Safe tools (search, memory, weather) run automatically. " +
                    "Sensitive tools (notifications, location, file operations) require confirmation. " +
                    "Destructive tools (shell, delete) are blocked."

            AutonomyPolicy.CAUTIOUS ->
                "Cautious: Only read-only tools (search, memory recall, weather) run automatically. " +
                    "All other tools require explicit user confirmation before execution."
        }
    }

    /**
     * Returns all tools mapped to a given minimum policy.
     */
    fun getToolsByMinPolicy(policy: AutonomyPolicy): List<String> {
        return toolPolicyMap.filter { it.value == policy }.keys.toList().sorted()
    }

    /**
     * Returns a summary of all tool policies.
     */
    fun getPolicySummary(): String {
        val fullAuto = getToolsByMinPolicy(AutonomyPolicy.FULL_AUTO)
        val balanced = getToolsByMinPolicy(AutonomyPolicy.BALANCED)
        val cautious = getToolsByMinPolicy(AutonomyPolicy.CAUTIOUS)

        return buildString {
            appendLine("Autonomy Policy Tool Mapping:")
            appendLine()
            appendLine("FULL_AUTO (no confirmation needed):")
            fullAuto.forEach { appendLine("  • $it") }
            appendLine()
            appendLine("BALANCED (confirmation in cautious mode):")
            balanced.forEach { appendLine("  • $it") }
            appendLine()
            appendLine("CAUTIOUS (always requires confirmation):")
            cautious.forEach { appendLine("  • $it") }
        }.trimEnd()
    }
}
