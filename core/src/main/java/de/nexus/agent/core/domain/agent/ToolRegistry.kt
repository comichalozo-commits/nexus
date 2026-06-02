package de.nexus.agent.core.domain.agent

import de.nexus.agent.core.data.model.ToolDef
import de.nexus.agent.core.domain.tools.Tool

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun unregister(toolName: String) {
        tools.remove(toolName)
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun getToolDefinitions(): List<Map<String, Any>> {
        return tools.values.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parameters
            )
        }
    }

    fun clear() {
        tools.clear()
    }

    fun getToolCount(): Int = tools.size
}
