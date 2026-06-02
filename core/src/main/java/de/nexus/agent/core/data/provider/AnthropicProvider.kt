package de.nexus.agent.core.data.provider

import de.nexus.agent.core.common.safeCall
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmStreamChunk
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.data.model.ToolCall
import de.nexus.agent.core.data.model.ToolDefinition
import de.nexus.agent.core.data.model.ToolCallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnthropicProvider(
    private val providerConfig: LlmProvider
) : LlmProviderInterface {

    override val providerId: String = "anthropic"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun streamChat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ): Flow<LlmStreamChunk> = flow {
        val (systemMessages, conversationMessages) = messages.partition { it.role == MessageRole.SYSTEM }
        val systemContent = systemMessages.joinToString("\n") { it.content }

        val requestBody = buildRequestBody(conversationMessages, systemContent, tools, stream = true)
        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/v1/messages")
            .addHeader("x-api-key", providerConfig.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("Anthropic error: ${response.code} - $errorBody")
        }

        val source = response.body?.source() ?: throw Exception("Empty response body")
        val pendingToolCalls = mutableMapOf<String, ToolCall>()
        var currentToolId: String? = null

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue

            if (line.startsWith("event: ")) {
                val eventType = line.removePrefix("event: ").trim()
                val dataLine = source.readUtf8Line() ?: break
                if (!dataLine.startsWith("data: ")) continue
                val data = dataLine.removePrefix("data: ").trim()

                when (eventType) {
                    "content_block_start" -> {
                        try {
                            val block = json.decodeFromString<AnthropicEventContentBlockStart>(data)
                            if (block.contentBlock?.type == "tool_use" && block.contentBlock.id != null) {
                                currentToolId = block.contentBlock.id
                                pendingToolCalls[block.contentBlock.id] = ToolCall(
                                    id = block.contentBlock.id,
                                    name = block.contentBlock.name ?: "",
                                    arguments = "",
                                    status = ToolCallStatus.PENDING
                                )
                            }
                        } catch (_: Exception) {}
                    }
                    "content_block_delta" -> {
                        try {
                            val delta = json.decodeFromString<AnthropicEventContentBlockDelta>(data)
                            when (delta.delta?.type) {
                                "text_delta" -> {
                                    val text = delta.delta.text ?: ""
                                    if (text.isNotEmpty()) {
                                        emit(LlmStreamChunk(content = text))
                                    }
                                }
                                "input_json_delta" -> {
                                    val toolId = currentToolId
                                    if (toolId != null) {
                                        val existing = pendingToolCalls[toolId]
                                        if (existing != null) {
                                            pendingToolCalls[toolId] = existing.copy(
                                                arguments = existing.arguments + (delta.delta.partialJson ?: "")
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    "message_stop" -> {
                        emit(LlmStreamChunk(
                            content = "",
                            isComplete = true,
                            toolCalls = pendingToolCalls.values.toList()
                        ))
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO).catch { e ->
        emit(LlmStreamChunk(content = "", error = e.message))
    }

    override suspend fun completeChat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ) = safeCall {
        val (systemMessages, conversationMessages) = messages.partition { it.role == MessageRole.SYSTEM }
        val systemContent = systemMessages.joinToString("\n") { it.content }

        val requestBody = buildRequestBody(conversationMessages, systemContent, tools, stream = false)
        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/v1/messages")
            .addHeader("x-api-key", providerConfig.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("Anthropic error: ${response.code} - $errorBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val parsed = json.parseToJsonElement(responseBody) as? JsonObject
        val contentArray = parsed?.get("content")?.let {
            json.decodeFromString<List<AnthropicContentBlock>>(it.toString())
        }
        contentArray?.firstOrNull { it.type == "text" }?.text ?: ""
    }

    override suspend fun testConnection() = safeCall {
        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(providerConfig.model.ifBlank { "claude-3-5-haiku-20241022" }))
            put("max_tokens", JsonPrimitive(10))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive("OK"))
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/v1/messages")
            .addHeader("x-api-key", providerConfig.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        response.isSuccessful
    }

    override fun buildRequest(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ): de.nexus.agent.core.data.model.LlmRequest {
        return de.nexus.agent.core.data.model.LlmRequest(
            model = providerConfig.model.ifBlank { "claude-3-5-haiku-20241022" },
            messages = messages,
            tools = tools,
            temperature = providerConfig.temperature,
            maxTokens = providerConfig.maxTokens,
            stream = true
        )
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        systemContent: String?,
        tools: List<ToolDefinition>?,
        stream: Boolean
    ): String {
        val jsonMessages = buildJsonArray {
            messages.filter { it.role != MessageRole.SYSTEM }.forEach { msg ->
                add(buildJsonObject {
                    val anthropicRole = when (msg.role) {
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.TOOL -> "user"
                        else -> "user"
                    }
                    put("role", JsonPrimitive(anthropicRole))

                    if (msg.role == MessageRole.TOOL) {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", JsonPrimitive("tool_result"))
                                put("tool_use_id", JsonPrimitive(msg.toolCallId ?: ""))
                                put("content", JsonPrimitive(msg.content))
                            })
                        })
                    } else if (msg.role == MessageRole.ASSISTANT && msg.toolCalls.isNotEmpty()) {
                        put("content", buildJsonArray {
                            if (msg.content.isNotBlank()) {
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("text"))
                                    put("text", JsonPrimitive(msg.content))
                                })
                            }
                            msg.toolCalls.forEach { tc ->
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("tool_use"))
                                    put("id", JsonPrimitive(tc.id))
                                    put("name", JsonPrimitive(tc.name))
                                    val args = try {
                                        json.parseToJsonElement(tc.arguments)
                                    } catch (_: Exception) {
                                        buildJsonObject {}
                                    }
                                    put("input", args)
                                })
                            }
                        })
                    } else {
                        put("content", JsonPrimitive(msg.content))
                    }
                })
            }
        }

        return buildJsonObject {
            systemContent?.takeIf { it.isNotBlank() }?.let {
                put("system", JsonPrimitive(it))
            }
            put("model", JsonPrimitive(providerConfig.model.ifBlank { "claude-3-5-haiku-20241022" }))
            put("messages", jsonMessages)
            put("stream", JsonPrimitive(stream))
            put("max_tokens", JsonPrimitive(providerConfig.maxTokens))
            put("temperature", JsonPrimitive(providerConfig.temperature))

            tools?.takeIf { it.isNotEmpty() }?.let { toolList ->
                put("tools", buildJsonArray {
                    toolList.forEach { tool ->
                        add(buildJsonObject {
                            put("name", JsonPrimitive(tool.name))
                            put("description", JsonPrimitive(tool.description))
                            val params = buildJsonObject {
                                put("type", JsonPrimitive(tool.parameters.type))
                                if (tool.parameters.properties.isNotEmpty()) {
                                    put("properties", buildJsonObject {
                                        tool.parameters.properties.forEach { (name, prop) ->
                                            put(name, buildJsonObject {
                                                put("type", JsonPrimitive(prop.type))
                                                put("description", JsonPrimitive(prop.description))
                                                prop.enum?.let { values ->
                                                    put("enum", buildJsonArray {
                                                        values.forEach { add(JsonPrimitive(it)) }
                                                    })
                                                }
                                            })
                                        }
                                    })
                                }
                                if (tool.parameters.required.isNotEmpty()) {
                                    put("required", buildJsonArray {
                                        tool.parameters.required.forEach { add(JsonPrimitive(it)) }
                                    })
                                }
                            }
                            put("input_schema", params)
                        })
                    }
                })
            }
        }.toString()
    }
}

@Serializable
private data class AnthropicEventContentBlockStart(
    val type: String? = null,
    val index: Int = 0,
    val contentBlock: AnthropicContentBlock? = null
)

@Serializable
private data class AnthropicContentBlock(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null,
    val text: String? = null
)

@Serializable
private data class AnthropicEventContentBlockDelta(
    val type: String? = null,
    val index: Int = 0,
    val delta: AnthropicEventDelta? = null
)

@Serializable
private data class AnthropicEventDelta(
    val type: String? = null,
    val text: String? = null,
    val partialJson: String? = null
)

class AnthropicProviderFactory : LlmProviderFactory {
    override fun create(provider: LlmProvider): LlmProviderInterface {
        return AnthropicProvider(provider)
    }
}
