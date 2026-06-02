package de.nexus.agent.core.domain.tools

import android.content.Context
import de.nexus.agent.core.data.model.ToolDefinition
import de.nexus.agent.core.data.model.ToolParameterSchema
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ToolRegistry for the domain/tools package.
 * Manages all tool instances and provides lookup, listing, and schema export.
 * Supports both old-style (BaseTool) and new-style (@Tool annotated) tools.
 *
 * Distinct from the agent-domain ToolRegistry which is Hilt-injected.
 * This version can be used standalone or as a supplementary registry.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val webSearchTool: WebSearchTool,
    private val webFetchTool: WebFetchTool,
    private val fileTool: FileTool,
    private val shellTool: ShellTool,
    private val memoryTool: MemoryTool,
    private val scheduleTool: ScheduleTool,
    private val clipboardTool: ClipboardTool,
    private val notificationTool: NotificationTool,
    private val locationTool: LocationTool,
    private val browserTool: BrowserTool,
    private val autonomyPolicy: AutonomyPolicy
) {
    private val tools: Map<String, Tool> by lazy {
        val allTools = mutableMapOf<String, Tool>()
        // Old-style tools
        allTools[webSearchTool.name] = webSearchTool
        allTools[webFetchTool.name] = webFetchTool
        allTools[fileTool.name] = fileTool
        allTools[shellTool.name] = shellTool
        allTools[memoryTool.name] = memoryTool
        allTools[scheduleTool.name] = scheduleTool
        // New-style tools
        allTools[clipboardTool.name] = clipboardTool
        allTools[notificationTool.name] = notificationTool
        allTools[locationTool.name] = locationTool
        allTools[browserTool.name] = browserTool
        allTools
    }

    private val oldStyleTools: List<Tool> by lazy {
        listOf(webSearchTool, webFetchTool, fileTool, shellTool, memoryTool, scheduleTool)
    }

    private val newStyleTools: List<Tool> by lazy {
        listOf(clipboardTool, notificationTool, locationTool, browserTool)
    }

    /**
     * Returns all registered tools.
     */
    fun getAll(): List<Tool> = tools.values.toList()

    /**
     * Finds a tool by its name (exact match).
     */
    fun findByName(name: String): Tool? = tools[name]

    /**
     * Returns tools that are currently enabled based on AutonomyPolicy.
     */
    fun getEnabledTools(): List<Tool> {
        return tools.values.filter { tool ->
            autonomyPolicy.isAllowed(tool.name)
        }
    }

    /**
     * Returns old-style (BaseTool) tools for backward compatibility.
     */
    fun getOldStyleTools(): List<Tool> = oldStyleTools

    /**
     * Returns new-style (@Tool annotated) tools.
     */
    fun getNewStyleTools(): List<Tool> = newStyleTools

    /**
     * Returns JSON schemas for all enabled tools (for LLM function calling).
     * Uses ToolParameterSchema format for old-style tools.
     */
    fun getSchemas(): List<Map<String, Any>> {
        return getEnabledTools().map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to schemaToMap(tool.parameters)
            )
        }
    }

    /**
     * Returns JSON schemas for new-style (@Tool annotated) tools.
     * Uses JsonSchema format.
     */
    fun getNewStyleSchemas(): List<Map<String, Any>> {
        return newStyleTools.map { tool ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to jsonSchemaToMap(tool.parameterSchema)
                )
            )
        }
    }

    /**
     * Checks if the given tool requires confirmation before execution
     * based on the current AutonomyPolicy.
     */
    fun requiresConfirmation(toolName: String): Boolean {
        return autonomyPolicy.requiresConfirmation(toolName)
    }

    /**
     * Returns the AutonomyPolicy associated with this registry.
     */
    fun getAutonomyPolicy(): AutonomyPolicy = autonomyPolicy

    /**
     * Returns the number of registered tools.
     */
    fun getToolCount(): Int = tools.size

    /**
     * Returns a summary of all tools for debugging.
     */
    fun getSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("ToolRegistry (${tools.size} tools):")
        sb.appendLine("  Autonomy Policy: ${autonomyPolicy.currentLevel()}")
        sb.appendLine("  Old-style tools (${oldStyleTools.size}):")
        oldStyleTools.forEach { sb.appendLine("    - ${it.name}: ${it.description.take(60)}") }
        sb.appendLine("  New-style tools (${newStyleTools.size}):")
        newStyleTools.forEach { sb.appendLine("    - ${it.name}: ${it.description.take(60)}") }
        return sb.toString().trimEnd()
    }

    private fun schemaToMap(schema: ToolParameterSchema): Map<String, Any> {
        return mapOf(
            "type" to schema.type,
            "properties" to schema.properties.mapValues { (_, prop) ->
                val map = mutableMapOf<String, Any>("type" to prop.type, "description" to prop.description)
                prop.enum?.let { map["enum"] = it }
                map
            },
            "required" to schema.required
        )
    }

    private fun jsonSchemaToMap(schema: JsonSchema): Map<String, Any> {
        return mapOf(
            "type" to schema.type,
            "properties" to schema.properties.mapValues { (_, prop) ->
                val map = mutableMapOf<String, Any>("type" to prop.type, "description" to prop.description)
                prop.enum?.let { map["enum"] = it }
                map
            },
            "required" to schema.required
        )
    }
}
