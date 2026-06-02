package de.nexus.agent.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LlmProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val isEnabled: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = true
)

val LlmProvider.safeName: String
    get() = name.ifBlank { id }

val LlmProvider.isConfigured: Boolean
    get() = apiKey.isNotBlank() && baseUrl.isNotBlank()

fun LlmProvider.listedModelSelection(): List<String> {
    return when (id) {
        "openrouter" -> listOf(
            "openrouter/auto",
            "anthropic/claude-sonnet-4",
            "anthropic/claude-opus-4",
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "deepseek/deepseek-r1",
            "google/gemini-2.0-flash"
        )
        "anthropic" -> listOf(
            "claude-sonnet-4-20250514",
            "claude-opus-4-20250514",
            "claude-3-5-haiku-20241022"
        )
        "openai" -> listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "o3",
            "o4-mini"
        )
        "gemini" -> listOf(
            "gemini-2.0-flash",
            "gemini-2.5-pro-preview",
            "gemini-1.5-pro"
        )
        else -> emptyList()
    }
}
