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

class GeminiProvider(
    private val providerConfig: LlmProvider
) : LlmProviderInterface {

    override val providerType: String = "gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse {
        val response = StreamingChatResponse()
        try {
            val modelName = providerConfig.model.ifBlank { "gemini-1.5-flash" }
            val url = "${providerConfig.baseUrl}/v1beta/models/$modelName:streamGenerateContent?key=${providerConfig.apiKey}"

            val requestBody = buildGeminiRequestBody(messages)
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val httpResponse = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!httpResponse.isSuccessful) {
                response.emit(ChatStreamEvent.Error("Gemini error: ${httpResponse.code}"))
                return response
            }

            val body = httpResponse.body ?: run {
                response.emit(ChatStreamEvent.Error("Empty response body"))
                return response
            }

            val responseText = body.string()
            try {
                val parsed = json.parseToJsonElement(responseText)
                val text = extractGeminiText(parsed)
                if (text.isNotEmpty()) {
                    response.emit(ChatStreamEvent.TextDelta(text))
                }
                response.emit(ChatStreamEvent.Done(text))
            } catch (e: Exception) {
                response.emit(ChatStreamEvent.Done(responseText))
            }
        } catch (e: Exception) {
            response.emit(ChatStreamEvent.Error(e.message ?: "Gemini error"))
        }
        return response
    }

    private fun extractGeminiText(element: kotlinx.serialization.json.JsonElement): String {
        return try {
            val content = (element as? JsonObject)?.get("content") as? JsonObject
            val parts = content?.get("parts") as? kotlinx.serialization.json.JsonArray
            parts?.firstOrNull()?.let { (it as? JsonObject)?.get("text")?.jsonPrimitive?.content } ?: ""
        } catch (_: Exception) { "" }
    }

    override suspend fun complete(prompt: String, config: LlmConfig): String {
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = prompt))
        val response = chat(messages, null, config)
        var result = ""
        response.events.collect { event ->
            when (event) {
                is ChatStreamEvent.TextDelta -> result += event.delta
                is ChatStreamEvent.Done -> { }
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
        val modelName = providerConfig.model.ifBlank { "gemini-1.5-flash" }
        val url = "${providerConfig.baseUrl}/v1beta/models/$modelName:generateContent?key=${providerConfig.apiKey}"
        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive("ping")) })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        response.isSuccessful
    }.getOrNull() ?: false } catch(_: Exception) { false } }

    private fun buildGeminiRequestBody(messages: List<ChatMessage>): String {
        return buildJsonObject {
            put("contents", buildJsonArray {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        val geminiRole = when (msg.role) {
                            MessageRole.ASSISTANT -> "model"
                            else -> "user"
                        }
                        put("role", JsonPrimitive(geminiRole))
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(msg.content)) })
                        })
                    })
                }
            })
        }.toString()
    }
}
