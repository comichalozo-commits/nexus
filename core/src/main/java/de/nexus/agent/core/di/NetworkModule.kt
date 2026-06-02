package de.nexus.agent.core.di

import android.content.Context
import de.nexus.agent.core.data.provider.AnthropicProvider
import de.nexus.agent.core.data.provider.GeminiProvider
import de.nexus.agent.core.data.provider.OpenAiProvider
import de.nexus.agent.core.data.provider.OpenRouterProvider
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.core.data.model.ProviderType
import de.nexus.agent.core.data.provider.LlmProviderInterface
import de.nexus.agent.core.data.provider.LlmRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun provideOkHttpClient(): OkHttpClient = okHttpClient

    fun provideJson() = json

    fun provideOpenRouterProvider(): OpenRouterProvider = OpenRouterProvider(okHttpClient, json)

    private fun createLlmProvider(id: String, name: String, baseUrl: String, apiKey: String): LlmProvider {
        return LlmProvider(
            id = id,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            isEnabled = apiKey.isNotBlank()
        )
    }

    fun provideAnthropicProvider(apiKey: String = ""): AnthropicProvider {
        val config = createLlmProvider("anthropic", "Anthropic", "https://api.anthropic.com", apiKey)
        return AnthropicProvider(config)
    }

    fun provideOpenAiProvider(apiKey: String = ""): OpenAiProvider {
        val config = createLlmProvider("openai", "OpenAI", "https://api.openai.com", apiKey)
        return OpenAiProvider(config)
    }

    fun provideGeminiProvider(apiKey: String = ""): GeminiProvider {
        val config = createLlmProvider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com", apiKey)
        return GeminiProvider(config)
    }

    fun provideLlmRouter(context: Context): LlmRouter {
        val providers = mutableMapOf<ProviderType, LlmProviderInterface>()
        providers[ProviderType.OPENROUTER] = provideOpenRouterProvider()
        providers[ProviderType.ANTHROPIC] = provideAnthropicProvider()
        providers[ProviderType.OPENAI] = provideOpenAiProvider()
        providers[ProviderType.GEMINI] = provideGeminiProvider()

        return LlmRouter(providers, MutableStateFlow(null))
    }
}
