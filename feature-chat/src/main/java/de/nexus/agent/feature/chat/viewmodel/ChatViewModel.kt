package de.nexus.agent.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.nexus.agent.core.data.db.MessageDao
import de.nexus.agent.core.data.db.ToolDao
import de.nexus.agent.core.data.db.ConversationDao
import de.nexus.agent.core.data.db.MessageEntity
import de.nexus.agent.core.data.db.ToolEntity
import de.nexus.agent.core.data.db.ConversationEntity
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.core.data.model.LlmStreamChunk
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.data.model.ToolCall
import de.nexus.agent.core.data.model.ToolCallStatus
import de.nexus.agent.core.data.provider.CompositeProviderFactory
import de.nexus.agent.core.domain.agent.AgentLoop
import de.nexus.agent.core.domain.agent.AgentState
import de.nexus.agent.core.domain.agent.ToolRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val agentState: AgentState = AgentState.Idle,
    val streamingText: String = "",
    val selectedProviderId: String = "openrouter",
    val isGenerating: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val toolDao: ToolDao,
    private val conversationDao: ConversationDao,
    private val providerFactory: CompositeProviderFactory,
    private val toolRegistry: ToolRegistry
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val currentConversationId = "default"
    private var generationJob: Job? = null

    init {
        loadMessages()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            messageDao.getMessagesForConversation(currentConversationId).collect { entities ->
                _messages.value = entities.map { it.toChatMessage() }
            }
        }
    }

    fun sendMessage(content: String) {
        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = content,
            conversationId = currentConversationId
        )

        viewModelScope.launch {
            messageDao.insertMessage(userMessage.toEntity(currentConversationId))
            _messages.update { it + userMessage }
            runAgentLoop()
        }
    }

    private suspend fun runAgentLoop() {
        generationJob = viewModelScope.launch {
            try {
                _agentState.value = AgentState.Thinking
                _streamingText.value = ""

                val recentMessages = messageDao.getRecentMessages(currentConversationId, 50)
                    .map { it.toChatMessage() }

                val messagesForAgent = recentMessages.filter {
                    it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT
                }

                // Get default provider config - in production, load from DataStore
                val provider = LlmProvider(
                    id = "openrouter",
                    name = "OpenRouter",
                    baseUrl = "https://openrouter.ai/api/v1",
                    apiKey = "",
                    model = "openrouter/auto"
                )

                if (!provider.isConfigured) {
                    _agentState.value = AgentState.Error("Kein LLM-Provider konfiguriert. Bitte erstelle einen API-Key in den Einstellungen.")

                    val errorMessage = ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = "Kein LLM-Provider konfiguriert. Bitte gehe zu den Einstellungen und füge einen API-Key hinzu."
                    )
                    messageDao.insertMessage(errorMessage.toEntity(currentConversationId))
                    _messages.update { it + errorMessage }
                    return@launch
                }

                val llmProvider = providerFactory.create(provider)
                val agentLoop = AgentLoop(
                    provider = llmProvider,
                    toolRegistry = toolRegistry
                )

                agentLoop.run(messagesForAgent).collect { state ->
                    _agentState.value = state

                    when (state) {
                        is AgentState.Streaming -> {
                            _streamingText.value = state.partialText
                        }
                        is AgentState.Complete -> {
                            _streamingText.value = ""
                            _agentState.value = AgentState.Idle
                            loadMessagesFromDb()
                        }
                        is AgentState.Error -> {
                            _streamingText.value = ""
                            val errorMsg = ChatMessage(
                                role = MessageRole.ASSISTANT,
                                content = "Fehler: ${state.message}"
                            )
                            messageDao.insertMessage(errorMsg.toEntity(currentConversationId))
                            _messages.update { it + errorMsg }
                            _agentState.value = AgentState.Idle
                        }
                        is AgentState.ToolResultState -> {
                            // Tool results are handled in the agent loop
                        }
                        else -> { /* Other states handled by UI */ }
                    }
                }
            } catch (e: Exception) {
                _agentState.value = AgentState.Error(e.message ?: "Unbekannter Fehler")
                _streamingText.value = ""

                val errorMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "Fehler: ${e.message}"
                )
                messageDao.insertMessage(errorMessage.toEntity(currentConversationId))
                _messages.update { it + errorMessage }
            }
        }
    }

    private suspend fun loadMessagesFromDb() {
        val entities = messageDao.getMessagesForConversationSync(currentConversationId)
        _messages.value = entities.map { it.toChatMessage() }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _agentState.value = AgentState.Idle
        _streamingText.value = ""
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = this.id,
            role = try { MessageRole.valueOf(this.role) } catch (_: Exception) { MessageRole.USER },
            content = this.content,
            timestamp = this.timestamp
        )
    }

    private fun ChatMessage.toEntity(conversationId: String): MessageEntity {
        return MessageEntity(
            id = this.id,
            role = this.role.name,
            content = this.content,
            timestamp = this.timestamp,
            conversationId = conversationId
        )
    }
}
