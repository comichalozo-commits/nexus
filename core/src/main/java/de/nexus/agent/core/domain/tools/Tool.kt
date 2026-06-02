package de.nexus.agent.core.domain.tools

import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty

interface Tool {
    val name: String
    val description: String
    val parameters: ToolParameterSchema

    suspend fun execute(arguments: String): String
}

abstract class BaseTool : Tool {
    protected fun getStringParam(arguments: String, key: String, default: String = ""): String {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(arguments)?.groupValues?.get(1) ?: default
    }

    protected fun getIntParam(arguments: String, key: String, default: Int = 0): Int {
        val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return regex.find(arguments)?.groupValues?.get(1)?.toIntOrNull() ?: default
    }

    protected fun getBooleanParam(arguments: String, key: String, default: Boolean = false): Boolean {
        val regex = Regex("\"$key\"\\s*:\\s*(true|false)")
        return regex.find(arguments)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: default
    }
}
