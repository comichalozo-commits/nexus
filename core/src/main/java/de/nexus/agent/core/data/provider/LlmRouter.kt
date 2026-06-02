package de.nexus.agent.core.data.provider

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.StreamingChatResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class LlmTask {
    CHAT,
    EMBEDDING,
    VISION,
    TOOL_CALL,
    QUICK_RESPONSE
}

/**
 * Router that manages multiple LLM providers, handles fallback, and health checks.
 */
@Singleton
class LlmRouter @Inject constructor(
    private val openRouterProvider: OpenRouterProvider,
    private val openAiProvider: OpenAiProvider,
    private val anthropicProvider: AnthropicProvider,
    private val geminiProvider: GeminiProvider
) : LlmProviderInterface {

    override val providerType: String = "router"

    private val providers = mapOf(
        "openrouter" to openRouterProvider,
        "openai" to openAiProvider,
        "anthropic" to anthropicProvider,
        "gemini" to geminiProvider
    )

    private val healthStatus = mutableMapOf<String, Boolean>()
    private val providerMutex = Mutex()

    /**
     * Returns the list of providers ordered by priority and health.
     */
    private fun getOrderedProviders(requestedProvider: String? = null): List<LlmProviderInterface> {
        if (requestedProvider != null && providers.containsKey(requestedProvider)) {
            val requested = providers[requestedProvider]!!
            val rest = providers.values.filter { it.providerType != requestedProvider }
                .sortedByIndexed { _, p -> if (healthStatus[p.providerType] == true) 0 else 1 }
            return listOf(requested) + rest
        }

        // Auto-detect: sort by health
        return providers.values.sortedByIndexed { _, p ->
            if (healthStatus[p.providerType] == true) 0 else 1
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T> Iterable<T>.sortedByIndexed(crossinline selector: (Int, T) -> Comparable<*>?): List<T> {
        return mapIndexed { index, item -> index to item }
            .sortedWith(compareBy({ selector(it.first, it.second) as Comparable<Any> }, { it.first }))
            .map { it.second }
    }

    /**
     * Determines the best provider for a given task based on capabilities.
     */
    fun bestProviderForTask(task: LlmTask): String {
        return when (task) {
            LlmTask.EMBEDDING -> "openai" // text-embedding-ada-002
            LlmTask.VISION -> "openai" // GPT-4o vision
            LlmTask.TOOL_CALL -> "anthropic" // Claude best-in-class tool use
            LlmTask.QUICK_RESPONSE -> "gemini" // Gemini 2.5 Flash
            LlmTask.CHAT -> "anthropic" // Default to Claude
        }
    }

    /**
     * Returns the currently active (healthy) provider with highest priority.
     */
    fun getActiveProvider(): LlmProviderInterface {
        return getOrderedProviders().firstOrNull { p ->
            healthStatus[p.providerType] != false
        } ?: anthropicProvider
    }

    /**
     * Runs health checks on all providers.
     */
    suspend fun healthCheck(): Map<String, Boolean> {
        providerMutex.withLock {
            providers.forEach { (type, provider) ->
                healthStatus[type] = try {
                    provider.healthCheck()
                } catch (e: Exception) {
                    false
                }
            }
            return healthStatus.toMap()
        }
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse {
        val orderedProviders = getOrderedProviders(config.provider)
        var lastException: Exception? = null

        for (provider in orderedProviders) {
            try {
                if (!provider.supportsTools() && !tools.isNullOrEmpty()) continue

                val response = provider.chat(messages, tools, config)

                // Check if the response produced any content or error
                var hasError = false
                response.events.collect { event ->
                    if (event is de.nexus.agent.core.data.model.ChatStreamEvent.Error) {
                        hasError = true
                        lastException = event.cause ?: RuntimeException(event.message)
                    }
                }

                if (!hasError) {
                    // Return the already-consumed response from this attempt
                    return provider.chat(messages, tools, config)
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }

        // All providers failed
        val errorResponse = StreamingChatResponse()
        errorResponse.emit(
            de.nexus.agent.core.data.model.ChatStreamEvent.Error(
                "All providers failed: ${lastException?.message ?: "Unknown error"}",
                lastException
            )
        )
        return errorResponse
    }

    override suspend fun complete(prompt: String, config: LlmConfig): String {
        val orderedProviders = getOrderedProviders(config.provider)

        for (provider in orderedProviders) {
            try {
                return provider.complete(prompt, config)
            } catch (e: Exception) {
                continue
            }
        }

        throw RuntimeException("All providers failed for complete()")
    }

    override suspend fun embed(text: String, config: LlmConfig?): FloatArray {
        val embeddingConfig = config ?: LlmConfig(
            provider = "openai",
            model = "text-embedding-ada-002",
            apiKey = ""
        )

        val orderedProviders = getOrderedProviders(embeddingConfig.provider)
            .filter { it.supportsTools() } // Proxy for "supports strong API"

        for (provider in orderedProviders) {
            try {
                return provider.embed(text, embeddingConfig)
            } catch (e: Exception) {
                continue
            }
        }

        throw RuntimeException("All providers failed for embed()")
    }

    override fun supportsTools(): Boolean = true
    override fun supportsStreaming(): Boolean = true
    override fun supportsVision(): Boolean = true

    override suspend fun healthCheck(): Boolean {
        val results = healthCheck()
        return results.values.any { it }
    }

    /**
     * Get a direct reference to a specific provider.
     */
    fun getProvider(type: String): LlmProviderInterface? = providers[type]
}
