package de.nexus.agent.feature.chat.model

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

enum class ToolStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR
}

data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parameters: Map<String, String> = emptyMap(),
    val result: String? = null,
    val status: ToolStatus = ToolStatus.PENDING,
    val errorMessage: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val toolCalls: List<ToolCall> = emptyList(),
    val imageUrl: String? = null
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Neue Konversation",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class UiState {
    IDLE,
    PROCESSING,
    STREAMING,
    ERROR
}

data class ProviderStatus(
    val providerName: String = "Nexus Agent",
    val modelName: String = "Default",
    val isConnected: Boolean = true,
    val latencyMs: Int = 0
)
