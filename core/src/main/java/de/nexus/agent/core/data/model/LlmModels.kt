package de.nexus.agent.core.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Configuration for an LLM request.
 */
@Serializable
data class LlmConfig(
    val provider: ProviderType = ProviderType.OPENROUTER,
    val model: String = "openrouter/auto",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val apiKey: String = "",
    val baseUrl: String? = null
)

/**
 * Supported LLM provider types.
 */
@Serializable
enum class ProviderType {
    OPENROUTER,
    ANTHROPIC,
    OPENAI,
    GEMINI
}

/**
 * Events emitted during a streaming chat response.
 */
@Serializable
sealed class ChatStreamEvent {
    data class TextDelta(val delta: String) : ChatStreamEvent()
    data class ToolCallStart(val id: String, val name: String) : ChatStreamEvent()
    data class ToolCallDelta(val id: String, val args: String) : ChatStreamEvent()
    data class Done(
        val fullContent: String = "",
        val fullToolCalls: List<ToolCall> = emptyList()
    ) : ChatStreamEvent()
    data class Error(val message: String, val cause: Throwable? = null) : ChatStreamEvent()
    data class UsageInfo(
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0
    ) : ChatStreamEvent()
}

/**
 * Wrapper around a stream of chat events.
 * Can be constructed with an existing StateFlow or with an internal MutableStateFlow.
 */
class StreamingChatResponse(
    events: StateFlow<ChatStreamEvent>? = null
) {
    private val _events: MutableStateFlow<ChatStreamEvent> = if (events != null) {
        MutableStateFlow(events.value)
    } else {
        MutableStateFlow(ChatStreamEvent.Done())
    }

    val events: StateFlow<ChatStreamEvent> = _events

    fun emit(event: ChatStreamEvent) {
        _events.value = event
    }
}

/**
 * Token usage tracking for LLM requests.
 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
) {
    fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        promptTokens = this.promptTokens + other.promptTokens,
        completionTokens = this.completionTokens + other.completionTokens,
        totalTokens = this.totalTokens + other.totalTokens
    )
}

/**
 * Tool definition in OpenAI function-calling format.
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, @Contextual Any> = emptyMap()
)

/**
 * Context for a single agent loop run.
 */
data class AgentContext(
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val config: LlmConfig = LlmConfig(),
    val conversationId: String = "default"
)
