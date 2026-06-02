package de.nexus.agent.core

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.nexus.agent.core.data.db.NexusDatabase
import de.nexus.agent.core.data.db.MessageDao
import de.nexus.agent.core.data.db.ToolDao
import de.nexus.agent.core.data.db.MemoryFactDao
import de.nexus.agent.core.data.db.SkillDao
import de.nexus.agent.core.data.db.ScheduledJobDao
import de.nexus.agent.core.data.db.ConversationDao
import de.nexus.agent.core.data.provider.AnthropicProviderFactory
import de.nexus.agent.core.data.provider.CompositeProviderFactory
import de.nexus.agent.core.data.provider.GeminiProviderFactory
import de.nexus.agent.core.data.provider.OpenAiProviderFactory
import de.nexus.agent.core.data.provider.OpenRouterProviderFactory
import de.nexus.agent.core.domain.agent.ToolRegistry
import de.nexus.agent.core.domain.memory.EmbeddingService
import de.nexus.agent.core.domain.memory.MemorySystem
import de.nexus.agent.core.domain.tools.FileTool
import de.nexus.agent.core.domain.tools.MemoryTool
import de.nexus.agent.core.domain.tools.ScheduleTool
import de.nexus.agent.core.domain.tools.ShellTool
import de.nexus.agent.core.domain.tools.WebFetchTool
import de.nexus.agent.core.domain.tools.WebSearchTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexusDatabase {
        return NexusDatabase.getInstance(context)
    }

    @Provides
    fun provideMessageDao(database: NexusDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideToolDao(database: NexusDatabase): ToolDao = database.toolDao()

    @Provides
    fun provideMemoryFactDao(database: NexusDatabase): MemoryFactDao = database.memoryFactDao()

    @Provides
    fun provideSkillDao(database: NexusDatabase): SkillDao = database.skillDao()

    @Provides
    fun provideScheduledJobDao(database: NexusDatabase): ScheduledJobDao = database.scheduledJobDao()

    @Provides
    fun provideConversationDao(database: NexusDatabase): ConversationDao = database.conversationDao()
}

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    fun provideProviderFactory(): CompositeProviderFactory {
        return CompositeProviderFactory(
            mapOf(
                "openrouter" to OpenRouterProviderFactory(),
                "anthropic" to AnthropicProviderFactory(),
                "openai" to OpenAiProviderFactory(),
                "gemini" to GeminiProviderFactory()
            )
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ToolModule {

    @Provides
    @Singleton
    fun provideToolRegistry(
        webSearchTool: WebSearchTool,
        webFetchTool: WebFetchTool,
        fileTool: FileTool,
        shellTool: ShellTool,
        memoryTool: MemoryTool,
        scheduleTool: ScheduleTool
    ): ToolRegistry {
        val registry = ToolRegistry()
        registry.register(webSearchTool)
        registry.register(webFetchTool)
        registry.register(fileTool)
        registry.register(shellTool)
        registry.register(memoryTool)
        registry.register(scheduleTool)
        return registry
    }

    @Provides
    @Singleton
    fun provideWebSearchTool(): WebSearchTool = WebSearchTool()

    @Provides
    @Singleton
    fun provideWebFetchTool(): WebFetchTool = WebFetchTool()

    @Provides
    @Singleton
    fun provideShellTool(): ShellTool = ShellTool()
}

@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {

    @Provides
    @Singleton
    fun provideEmbeddingService(): EmbeddingService = EmbeddingService()
}
