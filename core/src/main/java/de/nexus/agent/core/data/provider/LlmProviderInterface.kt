package de.nexus.agent.core.data.provider

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.StreamingChatResponse

/**
 * Common interface for all LLM provider implementations.
 */
interface LlmProviderInterface {

    /**
     * Provider identifier (e.g. "openrouter", "anthropic", "openai", "gemini").
     */
    val providerType: String

    /**
     * Sends a chat request with optional tool definitions and returns a streaming response.
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse

    /**
     * Simple completion without streaming or tools.
     */
    suspend fun complete(
        prompt: String,
        config: LlmConfig
    ): String

    /**
     * Generates an embedding vector for the given text.
     */
    suspend fun embed(text: String, config: LlmConfig? = null): FloatArray

    /**
     * Returns true if this provider supports tool/function calling.
     */
    fun supportsTools(): Boolean

    /**
     * Returns true if this provider supports streaming responses.
     */
    fun supportsStreaming(): Boolean

    /**
     * Returns true if this provider supports vision (image inputs).
     */
    fun supportsVision(): Boolean

    /**
     * Returns true if the provider is reachable and configured correctly.
     */
    suspend fun healthCheck(): Boolean
}
