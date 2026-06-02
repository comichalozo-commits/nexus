package de.nexus.agent.core.common

object Constants {
    const val DATABASE_NAME = "nexus_agent_db"
    const val DATABASE_VERSION = 1

    const val PREFS_NAME = "nexus_agent_prefs"

    const val KEY_SELECTED_PROVIDER = "selected_llm_provider"
    const val KEY_AUTONOMY_LEVEL = "autonomy_level"
    const val KEY_HEARTBEAT_INTERVAL = "heartbeat_interval_minutes"
    const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    const val KEY_NOTIFICATION_LISTENER = "notification_listener_enabled"

    const val DEFAULT_HEARTBEAT_INTERVAL_MINUTES = 30L
    const val DEFAULT_AUTONOMY_LEVEL = 1

    const val MAX_MESSAGE_LENGTH = 10_000
    const val MAX_TOOL_OUTPUT_LENGTH = 50_000
    const val MAX_CONTEXT_MESSAGES = 50
    const val STREAM_BUFFER_SIZE = 256

    const val NOTIFICATION_CHANNEL_ID = "nexus_agent_foreground"
    const val NOTIFICATION_CHANNEL_NAME = "Nexus Agent"

    val SUPPORTED_PROVIDERS = listOf("openrouter", "anthropic", "openai", "gemini")
}
