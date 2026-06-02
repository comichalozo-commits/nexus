package de.nexus.agent.core.domain.agent

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmConfig

/**
 * Represents the current state of the agent.
 */
sealed class AgentState {
    object Idle : AgentState()
    object Thinking : AgentState()
    data class ExecutingTool(val toolName: String) : AgentState()
    object Streaming : Streaming()
    data class Error(val message: String) : AgentState()
    object Completed : AgentState()
}

/**
 * Context carried through an agent conversation session.
 */
data class AgentContext(
    val conversationId: String,
    val messages: MutableList<ChatMessage>,
    val availableTools: List<Map<String, Any>>,
    val config: LlmConfig,
    val tokenUsage: TokenUsage = TokenUsage()
)

/**
 * Tracks token usage across iterations.
 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
) {
    fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        totalTokens = totalTokens + other.totalTokens
    )
}
