package de.nexus.agent.core.data.provider

import de.nexus.agent.core.common.safeCall
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmStreamChunk
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.data.model.ToolCall
import de.nexus.agent.core.data.model.ToolCallStatus
import de.nexus.agent.core.data.model.ToolDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAiProvider(
    private val providerConfig: LlmProvider
) : LlmProviderInterface {

    override val providerId: String = "openai"

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
        tools: List<ToolDef>?
    ): Flow<LlmStreamChunk> = flow {
        val requestBody = buildRequestBody(messages, tools, stream = true)
        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${providerConfig.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("OpenAI error: ${response.code} - $errorBody")
        }

        val source = response.body?.source() ?: throw Exception("Empty response body")
        val pendingToolCalls = mutableMapOf<Int, ToolCall>()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank() || !line.startsWith("data: ")) continue

            val data = line.removePrefix("data: ").trim()
            if (data == "[DONE]") break

            try {
                val chunk = json.decodeFromString<OpenAiStreamChunk>(data)
                val choice = chunk.choices?.firstOrNull()

                if (choice?.delta?.content != null) {
                    emit(LlmStreamChunk(
                        id = chunk.id,
                        model = chunk.model,
                        content = choice.delta.content
                    ))
                }

                choice?.delta?.toolCalls?.forEach { tcChunk ->
                    val idx = tcChunk.index
                    val existing = pendingToolCalls[idx]
                    if (existing == null) {
                        pendingToolCalls[idx] = ToolCall(
                            id = tcChunk.id,
                            name = tcChunk.function?.name ?: "",
                            arguments = tcChunk.function?.arguments ?: "",
                            status = ToolCallStatus.PENDING
                        )
                    } else {
                        pendingToolCalls[idx] = existing.copy(
                            id = existing.id.ifEmpty { tcChunk.id },
                            name = existing.name.ifEmpty { tcChunk.function?.name ?: "" },
                            arguments = existing.arguments + (tcChunk.function?.arguments ?: "")
                        )
                    }
                }

                if (choice?.finishReason != null) {
                    emit(LlmStreamChunk(
                        id = chunk.id,
                        model = chunk.model,
                        content = "",
                        isComplete = true,
                        toolCalls = pendingToolCalls.values.toList()
                    ))
                }
            } catch (_: Exception) {
                // Skip malformed chunks
            }
        }
    }.flowOn(Dispatchers.IO).catch { e ->
        emit(LlmStreamChunk(content = "", error = e.message))
    }

    override suspend fun completeChat(
        messages: List<ChatMessage>,
        tools: List<ToolDef>?
    ) = safeCall {
        val requestBody = buildRequestBody(messages, tools, stream = false)
        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${providerConfig.apiKey}")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("OpenAI error: ${response.code} - $errorBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val parsed = json.parseToJsonElement(responseBody) as? JsonObject
        parsed?.get("choices")?.let {
            val choices = json.decodeFromString<List<OpenAiChoice>>(it.toString())
            choices.firstOrNull()?.message?.content ?: ""
        } ?: ""
    }

    override suspend fun testConnection() = safeCall {
        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(providerConfig.model.ifBlank { "gpt-4o-mini" }))
            put("max_tokens", JsonPrimitive(10))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive("OK"))
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${providerConfig.apiKey}")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        response.isSuccessful
    }

    override fun buildRequest(
        messages: List<ChatMessage>,
        tools: List<ToolDef>?
    ): de.nexus.agent.core.data.model.LlmRequest {
        return de.nexus.agent.core.data.model.LlmRequest(
            model = providerConfig.model.ifBlank { "gpt-4o-mini" },
            messages = messages,
            tools = tools,
            temperature = providerConfig.temperature,
            maxTokens = providerConfig.maxTokens,
            stream = true
        )
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<ToolDef>?,
        stream: Boolean
    ): String {
        val jsonMessages = buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", JsonPrimitive(msg.role.name.lowercase()))
                    put("content", JsonPrimitive(msg.content))
                    msg.toolCallId?.let {
                        put("tool_call_id", JsonPrimitive(it))
                    }
                    if (msg.role == MessageRole.ASSISTANT && msg.toolCalls.isNotEmpty()) {
                        put("tool_calls", buildJsonArray {
                            msg.toolCalls.forEach { tc ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(tc.id))
                                    put("type", JsonPrimitive("function"))
                                    put("function", buildJsonObject {
                                        put("name", JsonPrimitive(tc.name))
                                        put("arguments", JsonPrimitive(tc.arguments))
                                    })
                                })
                            }
                        })
                    }
                    if (msg.role == MessageRole.TOOL) {
                        put("content", JsonPrimitive(msg.content))
                        put("tool_call_id", JsonPrimitive(msg.toolCallId ?: ""))
                    }
                })
            }
        }

        return buildJsonObject {
            put("model", JsonPrimitive(providerConfig.model.ifBlank { "gpt-4o-mini" }))
            put("messages", jsonMessages)
            put("stream", JsonPrimitive(stream))
            put("temperature", JsonPrimitive(providerConfig.temperature))
            put("max_tokens", JsonPrimitive(providerConfig.maxTokens))

            tools?.takeIf { it.isNotEmpty() }?.let { toolList ->
                put("tools", buildJsonArray {
                    toolList.forEach { tool ->
                        add(buildJsonObject {
                            put("type", JsonPrimitive("function"))
                            put("function", buildJsonObject {
                                put("name", JsonPrimitive(tool.name))
                                put("description", JsonPrimitive(tool.description))
                                put("parameters", buildJsonObject {
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
                                })
                            })
                        })
                    }
                })
                put("tool_choice", JsonPrimitive("auto"))
            }
        }.toString()
    }
}

@Serializable
private data class OpenAiStreamChunk(
    val id: String = "",
    val model: String = "",
    val choices: List<OpenAiChoice>? = null
)

@Serializable
private data class OpenAiChoice(
    val delta: OpenAiDelta? = null,
    val message: OpenAiMessage? = null,
    val finishReason: String? = null
)

@Serializable
private data class OpenAiDelta(
    val content: String? = null,
    val toolCalls: List<OpenAiToolCallChunk>? = null
)

@Serializable
private data class OpenAiMessage(
    val content: String? = null
)

@Serializable
private data class OpenAiToolCallChunk(
    val index: Int = 0,
    val id: String = "",
    val function: OpenAiFunctionChunk? = null
)

@Serializable
private data class OpenAiFunctionChunk(
    val name: String? = null,
    val arguments: String? = null
)

class OpenAiProviderFactory : LlmProviderFactory {
    override fun create(provider: LlmProvider): LlmProviderInterface {
        return OpenAiProvider(provider)
    }
}
