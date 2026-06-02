package de.nexus.agent.core.data.provider

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.StreamingChatResponse

/**
 * Common interface for all LLM provider implementations.
 */
interface LlmProviderInterface {

    val providerType: String

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse

    suspend fun complete(
        prompt: String,
        config: LlmConfig
    ): String

    suspend fun embed(text: String, config: LlmConfig? = null): FloatArray

    fun supportsTools(): Boolean

    fun supportsStreaming(): Boolean

    fun supportsVision(): Boolean

    suspend fun healthCheck(): Boolean
}
