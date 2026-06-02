package de.nexus.agent.core.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a single chat message sent to or received from an LLM.
 */
data class ChatMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
)

/**
 * Represents a tool/function call requested by the LLM.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string of arguments
)

/**
 * Sealed class representing events emitted during a streaming chat response.
 */
sealed class ChatStreamEvent {
    data class TextDelta(val delta: String) : ChatStreamEvent()
    data class ToolCallStart(val id: String, val name: String) : ChatStreamEvent()
    data class ToolCallDelta(val id: String, val partialArguments: String) : ChatStreamEvent()
    data class Done(val fullContent: String, val fullToolCalls: List<ToolCall>) : ChatStreamEvent()
    data class Error(val message: String, val cause: Throwable? = null) : ChatStreamEvent()
    data class UsageInfo(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : ChatStreamEvent()
}

/**
 * Configuration for an LLM request.
 */
data class LlmConfig(
    val provider: String, // "openrouter", "openai", "anthropic", "gemini"
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val apiKey: String,
    val baseUrl: String? = null
)

/**
 * Wraps a streaming chat response exposing events as a StateFlow.
 */
class StreamingChatResponse {
    private val _events = MutableStateFlow<ChatStreamEvent?>(null)
    val events: StateFlow<ChatStreamEvent?> = _events.asStateFlow()

    private val _fullContent = StringBuilder()
    private val _toolCallBuilders = mutableMapOf<String, ToolCallBuilder>()
    private var _isComplete = false

    val isComplete: Boolean get() = _isComplete
    val fullContent: String get() = _fullContent.toString()

    fun emit(event: ChatStreamEvent) {
        when (event) {
            is ChatStreamEvent.TextDelta -> _fullContent.append(event.delta)
            is ChatStreamEvent.ToolCallStart -> {
                _toolCallBuilders[event.id] = ToolCallBuilder(event.id, event.name)
            }
            is ChatStreamEvent.ToolCallDelta -> {
                _toolCallBuilders[event.id]?.appendArguments(event.partialArguments)
            }
            is ChatStreamEvent.Done -> {
                _isComplete = true
            }
            is ChatStreamEvent.Error -> {
                _isComplete = true
            }
            else -> {}
        }
        _events.value = event
    }

    fun getToolCalls(): List<ToolCall> {
        return _toolCallBuilders.values.map { it.build() }
    }

    private class ToolCallBuilder(val id: String, val name: String) {
        private val argsBuilder = StringBuilder()
        fun appendArguments(partial: String) { argsBuilder.append(partial) }
        fun build(): ToolCall = ToolCall(id, name, argsBuilder.toString())
    }
}
