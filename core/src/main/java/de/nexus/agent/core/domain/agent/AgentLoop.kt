package de.nexus.agent.core.domain.agent

import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.ToolCall
import de.nexus.agent.core.data.provider.LlmProviderInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Events emitted by the agent loop to inform the UI.
 */
sealed class AgentEvent {
    object ThinkingStarted : AgentEvent()
    data class ToolExecuting(val toolName: String, val arguments: String) : AgentEvent()
    data class ToolCompleted(val toolName: String, val result: String) : AgentEvent()
    data class TextResponse(val text: String) : AgentEvent()
    data class Error(val message: String, val toolName: String? = null) : AgentEvent()
    object AgentDone : AgentEvent()
    data class TokenUsageUpdated(val usage: TokenUsage) : AgentEvent()
}

/**
 * Interface for tool execution. Each tool implements this to handle its own logic.
 */
interface AgentTool {
    val name: String
    val description: String
    val parametersSchema: Map<String, Any>

    suspend fun execute(arguments: String): String
}

/**
 * Central Agent Think-Act-Observe loop.
 *
 * Takes messages, sends to LLM, processes tool calls, feeds results back, repeats.
 */
@Singleton
class AgentLoop @Inject constructor(
    private val llmProvider: LlmProviderInterface
) {
    companion object {
        private const val MAX_ITERATIONS = 10
    }

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<AgentState>(AgentState.Idle)
    val state: kotlinx.coroutines.flow.StateFlow<AgentState> = _state

    private val registeredTools = mutableMapOf<String, AgentTool>()

    /**
     * Register a tool that the agent can call.
     */
    fun registerTool(tool: AgentTool) {
        registeredTools[tool.name] = tool
    }

    /**
     * Unregister a tool by name.
     */
    fun unregisterTool(name: String) {
        registeredTools.remove(name)
    }

    /**
     * Returns the list of tool definitions in OpenAI function calling format.
     */
    private fun getToolDefinitions(): List<Map<String, Any>> {
        return registeredTools.values.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parametersSchema
            )
        }
    }

    /**
     * Runs the agent loop with the given context.
     * Emits AgentEvent items as the loop progresses.
     */
    fun run(context: AgentContext): Flow<AgentEvent> = flow {
        _state.value = AgentState.Thinking
        emit(AgentEvent.ThinkingStarted)

        var iteration = 0
        var totalUsage = TokenUsage()

        while (iteration < MAX_ITERATIONS) {
            iteration++

            try {
                val toolDefs = getToolDefinitions()
                val response = llmProvider.chat(
                    messages = context.messages,
                    tools = toolDefs.ifEmpty { null },
                    config = context.config
                )

                // Collect streaming events
                var assistantContent = ""
                val toolCalls = mutableListOf<ToolCall>()
                var streamDone = false

                withContext(Dispatchers.IO) {
                    response.events.collect { event ->
                        when (event) {
                            is ChatStreamEvent.TextDelta -> {
                                assistantContent += event.delta
                                _state.value = AgentState.Streaming
                                emit(AgentEvent.TextResponse(event.delta))
                            }
                            is ChatStreamEvent.ToolCallStart -> {
                                _state.value = AgentState.ExecutingTool(event.name)
                                emit(AgentEvent.ToolExecuting(event.name, ""))
                            }
                            is ChatStreamEvent.ToolCallDelta -> {
                                // Accumulate tool call arguments
                            }
                            is ChatStreamEvent.Done -> {
                                toolCalls.addAll(event.fullToolCalls)
                                streamDone = true
                            }
                            is ChatStreamEvent.Error -> {
                                _state.value = AgentState.Error(event.message)
                                emit(AgentEvent.Error(event.message))
                                streamDone = true
                            }
                            is ChatStreamEvent.UsageInfo -> {
                                val usage = TokenUsage(
                                    event.promptTokens,
                                    event.completionTokens,
                                    event.totalTokens
                                )
                                totalUsage = totalUsage.plus(usage)
                                emit(AgentEvent.TokenUsageUpdated(totalUsage))
                            }
                        }
                    }
                }

                // Add assistant message to context
                val assistantMsg = ChatMessage(
                    role = "assistant",
                    content = assistantContent.ifEmpty { null },
                    toolCalls = toolCalls.ifEmpty { null }
                )
                context.messages.add(assistantMsg)

                // If no tool calls, we're done
                if (toolCalls.isEmpty()) {
                    _state.value = AgentState.Completed
                    emit(AgentEvent.AgentDone)
                    return@flow
                }

                // Execute each tool call
                for (toolCall in toolCalls) {
                    val tool = registeredTools[toolCall.name]
                    if (tool == null) {
                        val errorMsg = "Tool '${toolCall.name}' not found"
                        emit(AgentEvent.Error(errorMsg, toolCall.name))
                        context.messages.add(
                            ChatMessage(
                                role = "tool",
                                content = """{"error": "$errorMsg"}""",
                                toolCallId = toolCall.id,
                                name = toolCall.name
                            )
                        )
                        continue
                    }

                    _state.value = AgentState.ExecutingTool(toolCall.name)
                    emit(AgentEvent.ToolExecuting(toolCall.name, toolCall.arguments))

                    try {
                        val result = withContext(Dispatchers.IO) {
                            tool.execute(toolCall.arguments)
                        }
                        emit(AgentEvent.ToolCompleted(toolCall.name, result))

                        context.messages.add(
                            ChatMessage(
                                role = "tool",
                                content = result,
                                toolCallId = toolCall.id,
                                name = toolCall.name
                            )
                        )
                    } catch (e: Exception) {
                        val errorMsg = "Tool '${toolCall.name}' failed: ${e.message}"
                        emit(AgentEvent.Error(errorMsg, toolCall.name))

                        // Feed error back to LLM so it can recover
                        context.messages.add(
                            ChatMessage(
                                role = "tool",
                                content = """{"error": "$errorMsg"}""",
                                toolCallId = toolCall.id,
                                name = toolCall.name
                            )
                        )
                    }
                }

                // Continue loop - the LLM will see tool results and respond
                _state.value = AgentState.Thinking
                emit(AgentEvent.ThinkingStarted)

            } catch (e: Exception) {
                _state.value = AgentState.Error(e.message ?: "Unknown error")
                emit(AgentEvent.Error("Agent loop error: ${e.message}"))
                return@flow
            }
        }

        // Max iterations reached
        _state.value = AgentState.Error("Max iterations ($MAX_ITERATIONS) reached")
        emit(AgentEvent.Error("Max iterations ($MAX_ITERATIONS) reached"))
    }

    /**
     * Simple single-turn chat without tool execution.
     */
    suspend fun simpleChat(
        messages: List<ChatMessage>,
        config: LlmConfig
    ): String {
        _state.value = AgentState.Thinking
        return try {
            val response = llmProvider.chat(messages, null, config)
            var result = ""
            withContext(Dispatchers.IO) {
                response.events.collect { event ->
                    when (event) {
                        is ChatStreamEvent.TextDelta -> result += event.delta
                        is ChatStreamEvent.Done -> result = event.fullContent
                        is ChatStreamEvent.Error -> throw RuntimeException(event.message, event.cause)
                        else -> {}
                    }
                }
            }
            _state.value = AgentState.Completed
            result
        } catch (e: Exception) {
            _state.value = AgentState.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    /**
     * Reset the agent state to Idle.
     */
    fun reset() {
        _state.value = AgentState.Idle
    }
}
