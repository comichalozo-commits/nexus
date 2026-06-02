package de.nexus.agent.core

import android.content.Context
import de.nexus.agent.core.data.db.NexusDatabase
import de.nexus.agent.core.data.model.ProviderType
import de.nexus.agent.core.data.provider.DefaultLlmProvider
import de.nexus.agent.core.data.provider.LlmProviderInterface
import de.nexus.agent.core.data.provider.LlmRouter
import de.nexus.agent.core.domain.agent.ToolRegistry
import de.nexus.agent.core.domain.tools.FileTool
import de.nexus.agent.core.domain.tools.MemoryTool
import de.nexus.agent.core.domain.tools.ScheduleTool
import de.nexus.agent.core.domain.tools.ShellTool
import de.nexus.agent.core.domain.tools.WebFetchTool
import de.nexus.agent.core.domain.tools.WebSearchTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simple Service Locator for manual dependency injection.
 */
object ServiceLocator {

    private var database: NexusDatabase? = null
    private var toolRegistry: ToolRegistry? = null
    private var _llmRouter: LlmRouter? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        database = NexusDatabase.getInstance(appContext)

        val webSearchTool = WebSearchTool()
        val webFetchTool = WebFetchTool()
        val fileTool = FileTool(appContext)
        val shellTool = ShellTool()
        val memoryTool = MemoryTool(appContext)
        val scheduleTool = ScheduleTool(appContext)

        toolRegistry = ToolRegistry().apply {
            register(webSearchTool)
            register(webFetchTool)
            register(fileTool)
            register(shellTool)
            register(memoryTool)
            register(scheduleTool)
        }

        val providers = mutableMapOf<ProviderType, LlmProviderInterface>()
        providers[ProviderType.OPENROUTER] = DefaultLlmProvider()
        providers[ProviderType.ANTHROPIC] = DefaultLlmProvider()
        providers[ProviderType.OPENAI] = DefaultLlmProvider()
        providers[ProviderType.GEMINI] = DefaultLlmProvider()

        _llmRouter = LlmRouter(providers, MutableStateFlow(null))
    }

    val db: NexusDatabase get() = database!!
    val tools: ToolRegistry get() = toolRegistry!!
    val providers: LlmRouter get() = _llmRouter!!
}
