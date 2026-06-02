package de.nexus.agent.core.data.provider

import de.nexus.agent.core.common.safeCall
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmStreamChunk
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.data.model.ToolCall
import de.nexus.agent.core.data.model.ToolCallStatus
import de.nexus.agent.core.data.model.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val providerConfig: LlmProvider
) : LlmProviderInterface {

    override val providerId: String = "gemini"

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
        val contents = messages.filter { it.role != MessageRole.SYSTEM }
            .map { msg ->
                when (msg.role) {
                    MessageRole.ASSISTANT -> "model" to msg.content
                    else -> "user" to msg.content
                }
            }

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                contents.forEach { (role, text) ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(role))
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", JsonPrimitive(text))
                            })
                        })
                    })
                }
            })

            messages.firstOrNull { it.role == MessageRole.SYSTEM }?.let { sysMsg ->
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", JsonPrimitive(sysMsg.content))
                        })
                    })
                })
            }

            tools?.takeIf { it.isNotEmpty() }?.let { toolList ->
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            toolList.forEach { tool ->
                                add(buildJsonObject {
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
                                                tool.parameters.required.forEach {
                                                    add(JsonPrimitive(it))
                                                }
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    })
                })
            }
        }.toString()

        val modelName = providerConfig.model.ifBlank { "gemini-2.0-flash" }
        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/v1beta/models/$modelName:streamGenerateContent?alt=sse")
            .addHeader("x-goog-api-key", providerConfig.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("Gemini error: ${response.code} - $errorBody")
        }

        val source = response.body?.source() ?: throw Exception("Empty response body")

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank() || !line.startsWith("data: ")) continue

            val data = line.removePrefix("data: ").trim()
            if (data.isBlank()) continue

            try {
                val parsed = json.parseToJsonElement(data) as? JsonObject
                val candidates = parsed?.get("candidates")?.let {
                    try {
                        json.decodeFromString<List<GeminiCandidate>>(it.toString())
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                candidates?.forEach { candidate ->
                    val parts = candidate.content?.parts
                    parts?.forEach { part ->
                        part.text?.takeIf { it.isNotBlank() }?.let { text ->
                            emit(LlmStreamChunk(content = text))
                        }
                        part.functionCall?.let { functionCall ->
                            emit(LlmStreamChunk(
                                content = "",
                                toolCalls = listOf(
                                    ToolCall(
                                        id = functionCall.name,
                                        name = functionCall.name,
                                        arguments = functionCall.args.toString(),
                                        status = ToolCallStatus.PENDING
                                    )
                                )
                            ))
                        }
                    }

                    candidate.finishReason?.let {
                        emit(LlmStreamChunk(
                            content = "",
                            isComplete = true
                        ))
                    }
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
        tools: List<ToolDefinition>?
    ) = safeCall {
        val contents = messages.filter { it.role != MessageRole.SYSTEM }
            .map { msg ->
                when (msg.role) {
                    MessageRole.ASSISTANT -> "model" to msg.content
                    else -> "user" to msg.content
                }
            }

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                contents.forEach { (role, text) ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(role))
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", JsonPrimitive(text))
                            })
                        })
                    })
                }
            })
        }.toString()

        val modelName = providerConfig.model.ifBlank { "gemini-2.0-flash" }
        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/v1beta/models/$modelName:generateContent")
            .addHeader("x-goog-api-key", providerConfig.apiKey)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("Gemini error: ${response.code} - $errorBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val parsed = json.parseToJsonElement(responseBody) as? JsonObject
        val candidates = parsed?.get("candidates")?.let {
            try {
                json.decodeFromString<List<GeminiCandidate>>(it.toString())
            } catch (_: Exception) {
                emptyList()
            }
        }
        candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
    }

    override suspend fun testConnection() = safeCall {
        val modelName = providerConfig.model.ifBlank { "gemini-2.0-flash" }
        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", JsonPrimitive("OK"))
                        })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("${providerConfig.baseUrl}/v1beta/models/$modelName:generateContent")
            .addHeader("x-goog-api-key", providerConfig.apiKey)
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
            model = providerConfig.model.ifBlank { "gemini-2.0-flash" },
            messages = messages,
            tools = tools,
            temperature = providerConfig.temperature,
            maxTokens = providerConfig.maxTokens,
            stream = true
        )
    }
}

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>? = null
)

@Serializable
private data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null
)

@Serializable
private data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, String> = emptyMap()
)

class GeminiProviderFactory : LlmProviderFactory {
    override fun create(provider: LlmProvider): LlmProviderInterface {
        return GeminiProvider(provider)
    }
}
