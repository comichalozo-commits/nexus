package de.nexus.agent.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val arguments: String = "",
    val result: String? = null,
    val status: ToolCallStatus = ToolCallStatus.PENDING
)

@Serializable
enum class ToolCallStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    ERROR
}

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameterSchema
)

@Serializable
data class ToolParameterSchema(
    val type: String = "object",
    val properties: Map<String, ToolProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class ToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

@Serializable
data class ToolResult(
    val toolCallId: String,
    val content: String,
    val isError: Boolean = false
)

@Serializable
data class LlmRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition>? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val stream: Boolean = true
)

@Serializable
data class LlmStreamChunk(
    val id: String = "",
    val model: String = "",
    val content: String = "",
    val finishReason: String? = null,
    val toolCalls: List<ToolCallChunk> = emptyList()
)

@Serializable
data class ToolCallChunk(
    val index: Int = 0,
    val id: String = "",
    val name: String = "",
    val arguments: String = ""
)

@Serializable
data class MemoryFact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val source: String = "",
    val relevance: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryFact) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
