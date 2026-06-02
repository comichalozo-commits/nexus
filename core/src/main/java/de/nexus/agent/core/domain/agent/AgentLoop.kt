package de.nexus.agent.core.domain.agent

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.ProviderType
import de.nexus.agent.core.data.model.ToolCall
import de.nexus.agent.core.data.provider.LlmProviderInterface
import de.nexus.agent.core.data.provider.LlmRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Central Agent Think-Act-Observes loop.
 * Takes messages, sends to LLM, processes tool calls, feeds results back, repeats.
 */
class AgentLoop(
    private val provider: LlmRouter,
    private val toolRegistry: ToolRegistry
) {
    companion object {
        private const val MAX_ITERATIONS = 10
        private const val MAX_TOOL_OUTPUT_LENGTH = 50_000
    }

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state

    fun run(messages: List<ChatMessage>): Flow<AgentState> = flow {
        _state.value = AgentState.Thinking
        emit(AgentState.Thinking)

        val conversation = messages.toMutableList()
        var iteration = 0

        while (iteration < MAX_ITERATIONS) {
            iteration++

            try {
                val config = LlmConfig(
                    provider = ProviderType.OPENROUTER,
                    model = "openrouter/auto",
                    apiKey = "",
                    maxTokens = 4096,
                    temperature = 0.7
                )

                val toolDefs = toolRegistry.getToolDefinitions()
                val response = provider.chat(
                    messages = conversation,
                    tools = toolDefs.ifEmpty { null },
                    config = config
                )

                var assistantContent = StringBuilder()
                val toolCalls = mutableListOf<ToolCall>()
                var isDone = false

                withContext(Dispatchers.IO) {
                    response.events.collect { event ->
                        when (event) {
                            is ChatStreamEvent.TextDelta -> {
                                assistantContent.append(event.delta)
                                _state.value = AgentState.Streaming(assistantContent.toString())
                                emit(AgentState.Streaming(assistantContent.toString()))
                            }
                            is ChatStreamEvent.Done -> {
                                toolCalls.addAll(event.fullToolCalls)
                                isDone = true
                            }
                            is ChatStreamEvent.Error -> {
                                _state.value = AgentState.Error(event.message)
                                emit(AgentState.Error(event.message))
                                isDone = true
                            }
                            else -> {}
                        }
                    }
                }

                if (isDone && toolCalls.isEmpty()) {
                    _state.value = AgentState.Complete
                    emit(AgentState.Complete)
                    return@flow
                }

                // Execute tool calls
                for (toolCall in toolCalls) {
                    _state.value = AgentState.ExecutingTool(toolCall.name, toolCall.arguments)
                    emit(AgentState.ExecutingTool(toolCall.name, toolCall.arguments))

                    val tool = toolRegistry.getTool(toolCall.name)
                    val result = if (tool != null) {
                        try {
                            withContext(Dispatchers.IO) {
                                tool.execute(toolCall.arguments)
                            }
                        } catch (e: Exception) {
                            """{"error": "${e.message}"}"""
                        }
                    } else {
                        """{"error": "Tool '${toolCall.name}' not found"}"""
                    }

                    val truncatedResult = if (result.length > MAX_TOOL_OUTPUT_LENGTH) {
                        result.take(MAX_TOOL_OUTPUT_LENGTH) + "\n... (truncated)"
                    } else result

                    conversation.add(
                        ChatMessage(
                            role = "tool",
                            content = truncatedResult,
                            toolCallId = toolCall.id,
                            name = toolCall.name
                        )
                    )

                    _state.value = AgentState.ToolResultState(toolCall.name, truncatedResult)
                    emit(AgentState.ToolResultState(toolCall.name, truncatedResult))
                }

            } catch (e: Exception) {
                _state.value = AgentState.Error(e.message ?: "Unbekannter Fehler")
                emit(AgentState.Error(e.message ?: "Unbekannter Fehler"))
                return@flow
            }
        }

        _state.value = AgentState.Error("Maximale Iterationen ($MAX_ITERATIONS) erreicht")
        emit(AgentState.Error("Maximale Iterationen ($MAX_ITERATIONS) erreicht"))
    }
}
