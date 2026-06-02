package de.nexus.agent.core.domain.agent

import de.nexus.agent.core.data.model.ToolDef
import de.nexus.agent.core.domain.tools.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor() {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun unregister(toolName: String) {
        tools.remove(toolName)
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun getToolDefinitions(): List<ToolDef> {
        return tools.values.map { tool ->
            ToolDef(
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters
            )
        }
    }

    fun clear() {
        tools.clear()
    }

    fun getToolCount(): Int = tools.size
}
