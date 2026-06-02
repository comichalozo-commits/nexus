package de.nexus.agent.core.data.provider

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.ProviderType
import de.nexus.agent.core.data.model.StreamingChatResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TaskType {
    CHAT, TOOL_CALL, VISION, EMBEDDING, REASONING
}

class LlmRouter(
    private val providers: Map<ProviderType, LlmProviderInterface>,
    private val _activeProvider: MutableStateFlow<ProviderType?>
) {
    val activeProviderType: StateFlow<ProviderType?> = _activeProvider

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse {
        val orderedProviders = buildFallbackOrder(config.provider)
        var lastException: Exception? = null

        for ((type, provider) in orderedProviders) {
            try {
                _activeProvider.value = type
                return provider.chat(messages, tools, config)
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }

        val errorFlow = MutableStateFlow<ChatStreamEvent>(
            ChatStreamEvent.Error(
                message = "All providers failed. Last error: ${lastException?.message ?: "Unknown"}",
                cause = lastException
            )
        )
        return StreamingChatResponse(events = errorFlow)
    }

    suspend fun healthCheck(): Map<ProviderType, Boolean> {
        val results = mutableMapOf<ProviderType, Boolean>()
        for ((type, provider) in providers) {
            results[type] = try { provider.healthCheck() } catch (_: Exception) { false }
        }
        return results
    }

    fun getActiveProvider(): LlmProviderInterface? {
        val type = _activeProvider.value
        return if (type != null) providers[type] else providers.values.firstOrNull()
    }

    fun bestProviderForTask(task: TaskType): LlmProviderInterface? {
        return when (task) {
            TaskType.CHAT -> providers.values.firstOrNull()
            TaskType.TOOL_CALL -> providers.values.filter { it.supportsTools() }.firstOrNull()
                ?: providers.values.firstOrNull()
            TaskType.VISION -> providers.values.filter { it.supportsVision() }.firstOrNull()
                ?: providers.values.firstOrNull()
            TaskType.EMBEDDING -> providers.values.firstOrNull()
            TaskType.REASONING -> providers[ProviderType.OPENROUTER]
                ?: providers[ProviderType.ANTHROPIC]
                ?: providers.values.firstOrNull()
        }
    }

    private fun buildFallbackOrder(primary: ProviderType): List<Pair<ProviderType, LlmProviderInterface>> {
        val ordered = mutableListOf<Pair<ProviderType, LlmProviderInterface>>()
        providers[primary]?.let { ordered.add(primary to it) }
        for (type in ProviderType.entries) {
            if (type != primary && providers.containsKey(type)) {
                ordered.add(type to providers[type]!!)
            }
        }
        return ordered
    }
}
