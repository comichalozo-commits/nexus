package de.nexus.agent.core.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import de.nexus.agent.core.data.provider.AnthropicProvider
import de.nexus.agent.core.data.provider.GeminiProvider
import de.nexus.agent.core.data.provider.OpenAiProvider
import de.nexus.agent.core.data.provider.OpenRouterProvider
import de.nexus.agent.core.data.model.ProviderType
import de.nexus.agent.core.data.provider.LlmProviderInterface
import de.nexus.agent.core.data.provider.LlmRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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

    fun provideAnthropicProvider(): AnthropicProvider = AnthropicProvider(okHttpClient, json)

    fun provideOpenAiProvider(): OpenAiProvider = OpenAiProvider(okHttpClient, json)

    fun provideGeminiProvider(): GeminiProvider = GeminiProvider(okHttpClient, json)

    fun provideLlmRouter(context: Context): LlmRouter {
        val providers = mutableMapOf<ProviderType, LlmProviderInterface>()
        providers[ProviderType.OPENROUTER] = provideOpenRouterProvider()
        providers[ProviderType.ANTHROPIC] = provideAnthropicProvider()
        providers[ProviderType.OPENAI] = provideOpenAiProvider()
        providers[ProviderType.GEMINI] = provideGeminiProvider()

        return LlmRouter(providers, MutableStateFlow(null))
    }
}
