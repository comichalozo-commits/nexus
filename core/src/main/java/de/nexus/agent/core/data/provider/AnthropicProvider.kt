package de.nexus.agent.core.data.provider

import de.nexus.agent.core.common.safeCall
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.data.model.StreamingChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnthropicProvider(
    private val providerConfig: LlmProvider
) : LlmProviderInterface {

    override val providerType: String = "anthropic"

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

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse {
        val response = StreamingChatResponse()
        try {
            val result = streamChatInternal(messages, tools)
            var content = ""
            result.collect { chunk ->
                when {
                    chunk.startsWith("data: ") -> {
                        val data = chunk.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            response.emit(ChatStreamEvent.Done(content))
                            return@collect
                        }
                        try {
                            val parsed = json.parseToJsonElement(data)
                            val text = extractAnthropicText(parsed)
                            if (text.isNotEmpty()) {
                                content += text
                                response.emit(ChatStreamEvent.TextDelta(text))
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            response.emit(ChatStreamEvent.Error(e.message ?: "Anthropic error"))
        }
        return response
    }

    private fun extractAnthropicText(element: kotlinx.serialization.json.JsonElement): String {
        return try {
            val obj = element as? JsonObject ?: return ""
            if (obj["type"]?.let { (it as? JsonPrimitive)?.content } == "content_block_delta") {
                val delta = obj["delta"] as? JsonObject ?: return ""
                delta["text"]?.let { (it as? JsonPrimitive)?.content } ?: ""
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun streamChatInternal(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?
    ): Flow<String> = flow {
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

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            throw Exception("Anthropic error: ${response.code}")
        }

        response.body?.source()?.use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                emit(line)
            }
        }
    }.flowOn(Dispatchers.IO).catch { e ->
        emit("data: ${buildJsonObject { put("type", JsonPrimitive("error")); put("message", JsonPrimitive(e.message ?: "Unknown")) }.toString()}")
    }

    override suspend fun complete(prompt: String, config: LlmConfig): String {
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = prompt))
        val response = chat(messages, null, config)
        var result = ""
        response.events.collect { event ->
            when (event) {
                is ChatStreamEvent.TextDelta -> result += event.delta
                is ChatStreamEvent.Done -> { /* done */ }
                is ChatStreamEvent.Error -> throw RuntimeException(event.message, event.cause)
                else -> {}
            }
        }
        return result
    }

    override suspend fun embed(text: String, config: LlmConfig?): FloatArray = FloatArray(0)

    override fun supportsTools(): Boolean = true
    override fun supportsStreaming(): Boolean = true
    override fun supportsVision(): Boolean = true

    override suspend fun healthCheck(): Boolean = run { try { safeCall {
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

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        response.isSuccessful
    }.getOrNull() ?: false } catch(_: Exception) { false } }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        systemContent: String?,
        tools: List<Map<String, Any>>?,
        stream: Boolean
    ): String {
        return buildJsonObject {
            systemContent?.takeIf { it.isNotBlank() }?.let { put("system", JsonPrimitive(it)) }
            put("model", JsonPrimitive(providerConfig.model.ifBlank { "claude-3-5-haiku-20241022" }))
            put("max_tokens", JsonPrimitive(providerConfig.maxTokens))
            put("temperature", JsonPrimitive(providerConfig.temperature))
            put("stream", JsonPrimitive(stream))

            put("messages", buildJsonArray {
                messages.filter { it.role != MessageRole.SYSTEM }.forEach { msg ->
                    add(buildJsonObject {
                        val anthropicRole = when (msg.role) {
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.TOOL -> "user"
                            else -> "user"
                        }
                        put("role", JsonPrimitive(anthropicRole))
                        put("content", JsonPrimitive(msg.content))
                    })
                }
            })
        }.toString()
    }
}

@Serializable
private data class AnthropicContentBlock(val type: String = "", val text: String = "")
