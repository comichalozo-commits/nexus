package de.nexus.agent.core.data.provider

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.ProviderType
import de.nexus.agent.core.data.model.StreamingChatResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Task types used for intelligent provider routing.
 */
enum class TaskType {
    CHAT,
    TOOL_CALL,
    VISION,
    EMBEDDING,
    REASONING
}

/**
 * Routes LLM requests across multiple providers with fallback support.
 *
 * Providers are tried in priority order (primary → secondary → tertiary).
 * If a provider fails, the next one is attempted automatically.
 */
@Singleton
class LlmRouter @Inject constructor(
    private val providers: Map<ProviderType, @JvmSuppressWildcards LlmProviderInterface>
) {
    private val _activeProvider = MutableStateFlow<ProviderType?>(null)
    val activeProviderType: StateFlow<ProviderType?> = _activeProvider

    /**
     * Sends a chat request, trying providers in fallback order.
     * Starts with the config's provider, then tries all others.
     */
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
                // Try next provider
                continue
            }
        }

        // All providers failed — return error stream
        val errorFlow = MutableStateFlow<ChatStreamEvent>(
            ChatStreamEvent.Error(
                message = "All providers failed. Last error: ${lastException?.message ?: "Unknown"}",
                cause = lastException
            )
        )
        return StreamingChatResponse(events = errorFlow)
    }

    /**
     * Checks health of all configured providers.
     */
    suspend fun healthCheck(): Map<ProviderType, Boolean> {
        val results = mutableMapOf<ProviderType, Boolean>()
        for ((type, provider) in providers) {
            results[type] = try {
                provider.healthCheck()
            } catch (_: Exception) {
                false
            }
        }
        return results
    }

    /**
     * Returns the currently active (last used) provider.
     */
    fun getActiveProvider(): LlmProviderInterface? {
        val type = _activeProvider.value
        return if (type != null) providers[type] else providers.values.firstOrNull()
    }

    /**
     * Selects the best provider for a given task type based on capabilities.
     */
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

    /**
     * Builds the fallback order: primary first, then all others in priority order.
     */
    private fun buildFallbackOrder(primary: ProviderType): List<Pair<ProviderType, LlmProviderInterface>> {
        val ordered = mutableListOf<Pair<ProviderType, LlmProviderInterface>>()

        // Add primary first
        providers[primary]?.let { ordered.add(primary to it) }

        // Add remaining providers in enum order
        for (type in ProviderType.entries) {
            if (type != primary && providers.containsKey(type)) {
                ordered.add(type to providers[type]!!)
            }
        }

        return ordered
    }
}
