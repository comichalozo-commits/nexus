package de.nexus.agent.core.domain.agent

sealed class AgentState {
    data object Idle : AgentState()
    data object Thinking : AgentState()
    data class Streaming(val partialText: String) : AgentState()
    data class ExecutingTool(val toolName: String, val arguments: String) : AgentState()
    data class ToolResult(val toolName: String, val result: String) : AgentState()
    data class Error(val message: String) : AgentState()
    data object Complete : AgentState()
}
