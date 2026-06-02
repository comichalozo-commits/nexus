package de.nexus.agent.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val conversationId: String = "default"
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

@Serializable
data class StreamingResponse(
    val text: String,
    val isComplete: Boolean = false,
    val error: String? = null,
    val toolCalls: List<ToolCall> = emptyList()
)
