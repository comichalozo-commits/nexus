package de.nexus.agent.core.data.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.ChatStreamEvent
import de.nexus.agent.core.data.model.LlmConfig
import de.nexus.agent.core.data.model.StreamingChatResponse
import de.nexus.agent.core.data.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LlmProviderInterface {

    override val providerType: String = "openrouter"
    private val gson = Gson()

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
            msgObj.addProperty("role", msg.role)
            if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
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
                msgObj.addProperty("content", msg.content ?: "")
            } else if (msg.content != null) {
                if (msg.content.startsWith("[{") && msg.content.contains("image_url")) {
                    try {
                        val parts = JsonParser.parseString(msg.content).asJsonArray
                        msgObj.add("content", parts)
                    } catch (_: Exception) {
                        msgObj.addProperty("content", msg.content)
                    }
                } else {
                    msgObj.addProperty("content", msg.content)
                }
            } else {
                msgObj.addProperty("content", "")
            }
            if (msg.toolCallId != null) {
                msgObj.addProperty("tool_call_id", msg.toolCallId)
            }
            if (msg.name != null) {
                msgObj.addProperty("name", msg.name)
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

            val toolChoice = JsonObject()
            toolChoice.addProperty("type", "auto")
            body.add("tool_choice", autoToolChoice(tools))
        }

        return gson.toJson(body)
    }

    private fun autoToolChoice(tools: List<Map<String, Any>>): JsonObject {
        return JsonObject().apply { addProperty("type", "auto") }
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
            val call = okHttpClient.newBuilder()
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
                .newCall(call)

            val sseEvents = parseSseStream(call)

            withContext(Dispatchers.IO) {
                sseEvents.collect { event ->
                    response.emit(event)
                    if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) {
                        return@collect
                    }
                }
            }
        } catch (e: Exception) {
            response.emit(ChatStreamEvent.Error("OpenRouter request failed: ${e.message}", e))
        }

        return response
    }

    private fun parseSseStream(call: Call): Flow<ChatStreamEvent> = callbackFlow {
        val collectedDeltas = StringBuilder()
        val toolCallAccumulators = mutableMapOf<String, Pair<String, StringBuilder>>()

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(ChatStreamEvent.Error("SSE stream failed: ${e.message}", e))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "Unknown error"
                    trySend(ChatStreamEvent.Error("HTTP ${response.code}: $bodyStr"))
                    close()
                    return
                }

                val body = response.body ?: run {
                    trySend(ChatStreamEvent.Error("Empty response body"))
                    close()
                    return
                }

                try {
                    body.source().use { source ->
                        processSseSource(source, collectedDeltas, toolCallAccumulators, this@callbackFlow, call)
                    }
                } catch (e: Exception) {
                    trySend(ChatStreamEvent.Error("Stream processing error: ${e.message}", e))
                }
                close()
            }
        })

        awaitClose { call.cancel() }
    }

    private fun processSseSource(
        source: BufferedSource,
        collectedDeltas: StringBuilder,
        toolCallAccumulators: MutableMap<String, Pair<String, StringBuilder>>,
        flow: kotlinx.coroutines.channels.SendChannel<ChatStreamEvent>,
        call: Call
    ) {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ").trim()
            if (data == "[DONE]") {
                val fullToolCalls = toolCallAccumulators.map { (id, pair) ->
                    ToolCall(id, pair.first, pair.second.toString())
                }
                flow.trySend(ChatStreamEvent.Done(collectedDeltas.toString(), fullToolCalls))
                break
            }

            try {
                val json = JsonParser.parseString(data).asJsonObject
                val choices = json.getAsJsonArray("choices") ?: continue
                for (choiceElement in choices) {
                    val choice = choiceElement.asJsonObject
                    val delta = choice.getAsJsonObject("delta") ?: continue

                    // Text content
                    if (delta.has("content") && !delta.get("content").isJsonNull) {
                        val text = delta.get("content").asString
                        if (text.isNotEmpty()) {
                            collectedDeltas.append(text)
                            flow.trySend(ChatStreamEvent.TextDelta(text))
                        }
                    }

                    // Tool calls
                    if (delta.has("tool_calls")) {
                        val toolCalls = delta.getAsJsonArray("tool_calls")
                        for (tcElement in toolCalls) {
                            val tcObj = tcElement.asJsonObject
                            val index = tcObj.get("index")?.asInt ?: 0
                            val tcId = tcObj.get("id")?.asString ?: "tc_$index"

                            if (tcObj.has("function")) {
                                val fnObj = tcObj.getAsJsonObject("function")
                                val name = fnObj.get("name")?.asString ?: ""
                                val args = fnObj.get("arguments")?.asString ?: ""

                                if (!toolCallAccumulators.containsKey(tcId)) {
                                    toolCallAccumulators[tcId] = name to StringBuilder()
                                    flow.trySend(ChatStreamEvent.ToolCallStart(tcId, name))
                                }
                                toolCallAccumulators[tcId]?.second?.append(args)
                                flow.trySend(ChatStreamEvent.ToolCallDelta(tcId, args))
                            }
                        }
                    }

                    // Finish reason
                    val finishReason = choice.get("finish_reason")?.asString
                    if (finishReason == "stop" || finishReason == "tool_calls") {
                        val fullToolCalls = toolCallAccumulators.map { (id, pair) ->
                            ToolCall(id, pair.first, pair.second.toString())
                        }
                        flow.trySend(ChatStreamEvent.Done(collectedDeltas.toString(), fullToolCalls))
                        call.cancel()
                        return
                    }

                    // Usage
                    if (json.has("usage") && !json.get("usage").isJsonNull) {
                        val usage = json.getAsJsonObject("usage")
                        val promptTokens = usage.get("prompt_tokens")?.asInt ?: 0
                        val completionTokens = usage.get("completion_tokens")?.asInt ?: 0
                        val totalTokens = usage.get("total_tokens")?.asInt ?: 0
                        flow.trySend(ChatStreamEvent.UsageInfo(promptTokens, completionTokens, totalTokens))
                    }
                }
            } catch (_: Exception) {
                // Skip malformed SSE data
            }
        }
    }

    override suspend fun complete(prompt: String, config: LlmConfig): String {
        val messages = listOf(ChatMessage(role = "user", content = prompt))
        val response = chat(messages, null, config)
        return withContext(Dispatchers.IO) {
            var result = ""
            response.events.collect { event ->
                when (event) {
                    is ChatStreamEvent.TextDelta -> result += event.delta
                    is ChatStreamEvent.Done -> result = event.fullContent
                    is ChatStreamEvent.Error -> throw RuntimeException(event.message, event.cause)
                    else -> {}
                }
            }
            result
        }
    }

    override suspend fun embed(text: String, config: LlmConfig?): FloatArray {
        if (config == null) throw IllegalArgumentException("Embedding requires a config with API key")

        val url = (config.baseUrl ?: "https://openrouter.ai/api/v1") + "/embeddings"
        val body = JsonObject().apply {
            addProperty("model", "text-embedding-ada-002")
            addProperty("input", text)
        }

        val requestBuilder = Request.Builder().url(url)
        buildHeaders(config).forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val request = requestBuilder
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Embedding request failed: ${response.code}")
                }
                val json = JsonParser.parseString(response.body?.string() ?: "").asJsonObject
                val data = json.getAsJsonArray("data")
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
                provider = providerType,
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
