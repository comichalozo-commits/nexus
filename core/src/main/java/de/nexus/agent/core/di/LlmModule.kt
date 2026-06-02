package de.nexus.agent.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.nexus.agent.core.data.model.ProviderType
import de.nexus.agent.core.data.provider.AnthropicProvider
import de.nexus.agent.core.data.provider.DefaultLlmProvider
import de.nexus.agent.core.data.provider.GeminiProvider
import de.nexus.agent.core.data.provider.LlmProviderInterface
import de.nexus.agent.core.data.provider.LlmRouter
import de.nexus.agent.core.data.provider.OpenAiProvider
import de.nexus.agent.core.data.provider.OpenRouterProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Named
import javax.inject.Singleton

private val Context.llmDataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_config")

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    private val KEY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
    private val KEY_ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
    private val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
    private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")

    @Provides
    @Singleton
    fun provideLlmRouter(
        @Named("openRouter") openRouterProvider: LlmProviderInterface,
        @Named("anthropic") anthropicProvider: LlmProviderInterface,
        @Named("openAi") openAiProvider: LlmProviderInterface,
        @Named("gemini") geminiProvider: LlmProviderInterface,
        defaultProvider: DefaultLlmProvider
    ): LlmRouter {
        val providers = mutableMapOf<ProviderType, LlmProviderInterface>()
        providers[ProviderType.OPENROUTER] = openRouterProvider
        providers[ProviderType.ANTHROPIC] = anthropicProvider
        providers[ProviderType.OPENAI] = openAiProvider
        providers[ProviderType.GEMINI] = geminiProvider
        return LlmRouter(providers)
    }

    @Provides
    @Singleton
    @Named("openRouter")
    fun provideOpenRouterProvider(
        @ApplicationContext context: Context,
        okHttpClient: okhttp3.OkHttpClient
    ): LlmProviderInterface {
        val apiKey = runBlocking {
            context.llmDataStore.data.first()[KEY_OPENROUTER_API_KEY] ?: ""
        }
        return OpenRouterProvider(okHttpClient).also {
            // Provider reads API key from config at call time
        }
    }

    @Provides
    @Singleton
    @Named("anthropic")
    fun provideAnthropicProvider(
        @ApplicationContext context: Context
    ): LlmProviderInterface {
        val apiKey = runBlocking {
            context.llmDataStore.data.first()[KEY_ANTHROPIC_API_KEY] ?: ""
        }
        val config = de.nexus.agent.core.data.model.LlmProvider(
            id = "anthropic",
            name = "Anthropic",
            baseUrl = "https://api.anthropic.com",
            apiKey = apiKey,
            model = "claude-3-5-haiku-20241022",
            supportsVision = true,
            supportsTools = true
        )
        return AnthropicProvider(config)
    }

    @Provides
    @Singleton
    @Named("openAi")
    fun provideOpenAiProvider(
        @ApplicationContext context: Context
    ): LlmProviderInterface {
        val apiKey = runBlocking {
            context.llmDataStore.data.first()[KEY_OPENAI_API_KEY] ?: ""
        }
        val config = de.nexus.agent.core.data.model.LlmProvider(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = apiKey,
            model = "gpt-4o-mini",
            supportsVision = true,
            supportsTools = true
        )
        return OpenAiProvider(config)
    }

    @Provides
    @Singleton
    @Named("gemini")
    fun provideGeminiProvider(
        @ApplicationContext context: Context
    ): LlmProviderInterface {
        val apiKey = runBlocking {
            context.llmDataStore.data.first()[KEY_GEMINI_API_KEY] ?: ""
        }
        val config = de.nexus.agent.core.data.model.LlmProvider(
            id = "gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com",
            apiKey = apiKey,
            model = "gemini-2.0-flash",
            supportsVision = true,
            supportsTools = true
        )
        return GeminiProvider(config)
    }

    @Provides
    @Singleton
    fun provideDefaultLlmProvider(): DefaultLlmProvider {
        return DefaultLlmProvider()
    }
}
