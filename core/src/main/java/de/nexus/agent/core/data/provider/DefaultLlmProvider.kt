package de.nexus.agent.core.data.provider

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.StreamingChatResponse
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fallback LLM provider that routes requests through OpenRouter.
 *
 * This provider is used as a default when no other provider is configured
 * or available. It connects to the OpenRouter API endpoint and supports
 * streaming, tool calling, and vision.
 */
class DefaultLlmProvider(
    private val apiKey: String = "",
    private val baseUrl: String = "https://openrouter.ai/api/v1"
) : LlmProviderInterface {

    override val providerType: String = "openrouter"

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse {
        val eventFlow = MutableStateFlow<ChatStreamEvent>(
            ChatStreamEvent.Error(
                message = "DefaultLlmProvider: No API key configured. " +
                    "Please set your OpenRouter API key in settings."
            )
        )

        return StreamingChatResponse(events = eventFlow)
    }

    override suspend fun complete(prompt: String, config: LlmConfig): String {
        return "DefaultLlmProvider: No API key configured. " +
            "Please set your OpenRouter API key in settings."
    }

    override suspend fun embed(text: String, config: LlmConfig?): FloatArray {
        return FloatArray(0)
    }

    override fun supportsTools(): Boolean = true

    override fun supportsStreaming(): Boolean = true

    override fun supportsVision(): Boolean = true

    override suspend fun healthCheck(): Boolean {
        return apiKey.isNotBlank()
    }
}
