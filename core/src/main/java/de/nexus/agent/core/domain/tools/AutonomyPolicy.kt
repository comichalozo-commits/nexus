package de.nexus.agent.core.domain.tools

/**
 * Defines the autonomy level for tool execution.
 * Controls which tools can run without user confirmation.
 */
enum class AutonomyPolicy {
    /**
     * Full automatic mode: All tools execute without confirmation.
     * Use with caution — potentially destructive operations will run immediately.
     */
    FULL_AUTO,

    /**
     * Balanced mode: Read-only and safe tools execute automatically.
     * Write operations, external actions, and sensitive tools require confirmation.
     */
    BALANCED,

    /**
     * Cautious mode: Every tool execution requires explicit user confirmation.
     * Recommended for initial setup or sensitive environments.
     */
    CAUTIOUS;

    /**
     * Returns the current policy level name.
     */
    fun currentLevel(): String = when (this) {
        FULL_AUTO -> "FULL_AUTO"
        BALANCED -> "BALANCED"
        CAUTIOUS -> "CAUTIOUS"
    }

    /**
     * Checks if a tool with the given name is allowed to execute
     * under this policy level (without explicit confirmation).
     */
    fun isAllowed(toolName: String): Boolean {
        return when (this) {
            FULL_AUTO -> true
            BALANCED -> toolName !in BLOCKED_TOOLS_BALANCED
            CAUTIOUS -> false
        }
    }

    /**
     * Checks if the given tool requires user confirmation
     * before execution under this policy.
     */
    fun requiresConfirmation(toolName: String): Boolean {
        return when (this) {
            FULL_AUTO -> false
            BALANCED -> toolName in CONFIRMATION_TOOLS_BALANCED
            CAUTIOUS -> true
        }
    }

    /**
     * Returns a human-readable description of this policy.
     */
    fun description(): String = when (this) {
        FULL_AUTO -> "All tools execute automatically without confirmation."
        BALANCED -> "Safe tools run automatically; sensitive operations require confirmation."
        CAUTIOUS -> "Every tool execution requires explicit user confirmation."
    }

    companion object {
        /**
         * Tools that are completely blocked in BALANCED mode.
         */
        private val BLOCKED_TOOLS_BALANCED = setOf(
            "shell_execute"
        )

        /**
         * Tools that require confirmation in BALANCED mode.
         */
        private val CONFIRMATION_TOOLS_BALANCED = setOf(
            "file_operation",
            "sms",
            "contacts",
            "calendar",
            "app_control",
            "camera",
            "screen_capture",
            "accessibility_control",
            "schedule",
            "notification",
            "location",
            "browser"
        )

        /**
         * Parse an AutonomyPolicy from a string (case-insensitive).
         * Defaults to BALANCED if the string doesn't match.
         */
        fun fromString(value: String): AutonomyPolicy {
            return when (value.uppercase().trim()) {
                "FULL_AUTO", "FULLAUTO", "AUTO", "UNRESTRICTED" -> FULL_AUTO
                "BALANCED", "DEFAULT", "NORMAL" -> BALANCED
                "CAUTIOUS", "SAFE", "RESTRICTED", "PARANOID" -> CAUTIOUS
                else -> BALANCED
            }
        }

        /**
         * Returns all available policy levels.
         */
        fun values(): List<AutonomyPolicy> = entries.toList()
    }
}
