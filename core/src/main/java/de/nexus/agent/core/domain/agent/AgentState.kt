package de.nexus.agent.core.domain.agent

sealed class AgentState {
    object Idle : AgentState()
    object Thinking : AgentState()
    data class Streaming(val partialText: String) : AgentState()
    data class ExecutingTool(val toolName: String, val arguments: String) : AgentState()
    data class ToolResultState(val toolName: String, val result: String) : AgentState()
    data class Error(val message: String) : AgentState()
    object Complete : AgentState()
}
