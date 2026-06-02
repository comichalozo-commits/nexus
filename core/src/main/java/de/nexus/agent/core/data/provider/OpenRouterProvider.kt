package de.nexus.agent.core.data.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.data.model.StreamingChatResponse
import de.nexus.agent.core.data.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterProvider(
    private val okHttpClient: OkHttpClient,
    private val json: kotlinx.serialization.json.Json
) : LlmProviderInterface {

    private val gson = Gson()

    override val providerType: String = "openrouter"

    private fun buildUrl(config: LlmConfig): String {
        return (config.baseUrl ?: "https://openrouter.ai/api/v1") + "/chat/completions"
    }

    private fun buildHeaders(config: LlmConfig): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer ${config.apiKey}",
            "Content-Type" to "application/json",
            "HTTP-Referer" to "https://nexus-agent.app",
            "X-Title" to "Nexus Agent"
        )
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig,
        stream: Boolean
    ): String {
        val body = JsonObject()
        body.addProperty("model", config.model)
        body.addProperty("temperature", config.temperature)
        body.addProperty("max_tokens", config.maxTokens)
        body.addProperty("stream", stream)

        val messagesArray = JsonArray()
        messages.forEach { msg ->
            val msgObj = JsonObject()
            msgObj.addProperty("role", msg.role.name.lowercase())
            if (msg.toolCalls.isNotEmpty()) {
                val tcArray = JsonArray()
                msg.toolCalls.forEach { tc ->
                    val tcObj = JsonObject()
                    tcObj.addProperty("id", tc.id)
                    tcObj.addProperty("type", "function")
                    val fnObj = JsonObject()
                    fnObj.addProperty("name", tc.name)
                    fnObj.addProperty("arguments", tc.arguments)
                    tcObj.add("function", fnObj)
                    tcArray.add(tcObj)
                }
                msgObj.add("tool_calls", tcArray)
                msgObj.addProperty("content", msg.content)
            } else {
                msgObj.addProperty("content", msg.content)
            }
            if (msg.toolCallId != null) {
                msgObj.addProperty("tool_call_id", msg.toolCallId)
            }
            // Only add name for tool role messages (OpenRouter API requirement)
            if (msg.role == MessageRole.TOOL && msg.toolCallId != null) {
                val toolName = msg.toolCalls.firstOrNull()?.name
                if (toolName != null) {
                    msgObj.addProperty("name", toolName)
                }
            }
            messagesArray.add(msgObj)
        }
        body.add("messages", messagesArray)

        if (!tools.isNullOrEmpty()) {
            val toolsArray = JsonArray()
            tools.forEach { tool ->
                val toolObj = JsonObject()
                toolObj.addProperty("type", "function")
                val entry = gson.toJsonTree(tool).asJsonObject
                toolObj.add("function", entry)
                toolsArray.add(toolObj)
            }
            body.add("tools", toolsArray)
            body.add("tool_choice", JsonObject().apply { addProperty("type", "auto") })
        }

        return gson.toJson(body)
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        config: LlmConfig
    ): StreamingChatResponse {
        val response = StreamingChatResponse()
        val requestBody = buildRequestBody(messages, tools, config, stream = true)
        val url = buildUrl(config)

        val requestBuilder = Request.Builder().url(url)
        buildHeaders(config).forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val request = requestBuilder.post(requestBody.toRequestBody("application/json".toMediaType())).build()

        try {
            val dedicatedClient = okHttpClient.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
            val call = dedicatedClient.newCall(request)

            parseSseStream(call).collect { event ->
                response.emit(event)
                if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) {
                    return@collect
                }
            }
        } catch (e: Exception) {
            response.emit(ChatStreamEvent.Error("OpenRouter request failed: ${e.message}", e))
        }

        return response
    }

    private fun parseSseStream(call: okhttp3.Call): Flow<ChatStreamEvent> = flow {
        val collectedDeltas = StringBuilder()
        val toolCallAccumulators = mutableMapOf<String, Pair<String, StringBuilder>>()

        val httpResponse = withContext(Dispatchers.IO) { call.execute() }

        if (!httpResponse.isSuccessful) {
            val bodyStr = httpResponse.body?.string() ?: "Unknown error"
            emit(ChatStreamEvent.Error("HTTP ${httpResponse.code}: $bodyStr"))
            emit(ChatStreamEvent.Done())
            return@flow
        }

        val body = httpResponse.body ?: run {
            emit(ChatStreamEvent.Error("Empty response body"))
            emit(ChatStreamEvent.Done())
            return@flow
        }

        try {
            body.source().use { source ->
                var streamDone = false
                while (!source.exhausted() && !streamDone) {
                    val line = source.readUtf8Line() ?: ""
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        val fullToolCalls = toolCallAccumulators.map { (id, pair) ->
                            ToolCall(id, pair.first, pair.second.toString())
                        }
                        emit(ChatStreamEvent.Done(collectedDeltas.toString(), fullToolCalls))
                        streamDone = true
                        continue
                    }

                    try {
                        val jsonElement = JsonParser.parseString(data).asJsonObject
                        val choices = jsonElement.getAsJsonArray("choices") ?: continue
                        for (choiceElement in choices) {
                            val choice = choiceElement.asJsonObject
                            val delta = choice.getAsJsonObject("delta") ?: continue

                            if (delta.has("content") && !delta.get("content").isJsonNull) {
                                val text = delta.get("content").asString
                                if (text.isNotEmpty()) {
                                    collectedDeltas.append(text)
                                    emit(ChatStreamEvent.TextDelta(text))
                                }
                            }

                            if (delta.has("tool_calls")) {
                                val toolCallsArr = delta.getAsJsonArray("tool_calls")
                                for (tcElement in toolCallsArr) {
                                    val tcObj = tcElement.asJsonObject
                                    val index = tcObj.get("index")?.asInt ?: 0
                                    val tcId = tcObj.get("id")?.asString ?: "tc_$index"

                                    if (tcObj.has("function")) {
                                        val fnObj = tcObj.getAsJsonObject("function")
                                        val name = fnObj.get("name")?.asString ?: ""
                                        val args = fnObj.get("arguments")?.asString ?: ""

                                        if (!toolCallAccumulators.containsKey(tcId)) {
                                            toolCallAccumulators[tcId] = name to StringBuilder()
                                            emit(ChatStreamEvent.ToolCallStart(tcId, name))
                                        }
                                        toolCallAccumulators[tcId]?.second?.append(args)
                                        emit(ChatStreamEvent.ToolCallDelta(tcId, args))
                                    }
                                }
                            }

                            val finishReason = choice.get("finish_reason")?.asString
                            if (finishReason == "stop" || finishReason == "tool_calls") {
                                val fullToolCalls = toolCallAccumulators.map { (id, pair) ->
                                    ToolCall(id, pair.first, pair.second.toString())
                                }
                                emit(ChatStreamEvent.Done(collectedDeltas.toString(), fullToolCalls))
                                streamDone = true
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            emit(ChatStreamEvent.Error("Stream processing error: ${e.message}", e))
        }
        emit(ChatStreamEvent.Done())
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

    override suspend fun embed(text: String, config: LlmConfig?): FloatArray {
        if (config == null) throw IllegalArgumentException("Embedding requires a config with API key")

        val url = (config.baseUrl ?: "https://openrouter.ai/api/v1") + "/embeddings"
        val body = JsonObject().apply {
            addProperty("model", "text-embedding-ada-002")
            addProperty("input", text)
        }

        val request = Request.Builder().url(url).apply {
            buildHeaders(config).forEach { (k, v) -> addHeader(k, v) }
        }.post(gson.toJson(body).toRequestBody("application/json".toMediaType())).build()

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Embedding failed: ${resp.code}")
                val jsonStr = resp.body?.string() ?: throw IOException("Empty embedding response")
                val jsonObj = JsonParser.parseString(jsonStr).asJsonObject
                val data = jsonObj.getAsJsonArray("data")
                if (data != null && data.size() > 0) {
                    val embeddingArray = data[0].asJsonObject.getAsJsonArray("embedding")
                    FloatArray(embeddingArray.size()) { i -> embeddingArray[i].asFloat }
                } else {
                    throw IOException("No embedding data in response")
                }
            }
        }
    }

    override fun supportsTools(): Boolean = true
    override fun supportsStreaming(): Boolean = true
    override fun supportsVision(): Boolean = true

    override suspend fun healthCheck(): Boolean {
        return try {
            val config = LlmConfig(
                provider = de.nexus.agent.core.data.model.ProviderType.OPENROUTER,
                model = "openai/gpt-4o-mini",
                apiKey = "",
                maxTokens = 1
            )
            val body = JsonObject().apply {
                addProperty("model", "openai/gpt-4o-mini")
                addProperty("max_tokens", 1)
                val msgs = JsonArray()
                val msg = JsonObject()
                msg.addProperty("role", "user")
                msg.addProperty("content", "ping")
                msgs.add(msg)
                add("messages", msgs)
                addProperty("stream", false)
            }
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                .build()
            okHttpClient.newCall(request).execute().use { it.code != 401 }
        } catch (e: Exception) {
            false
        }
    }
}
