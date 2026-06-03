package de.nexus.agent.feature.chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.nexus.agent.core.ServiceLocator
import de.nexus.agent.core.data.db.MessageDao
import de.nexus.agent.core.data.db.MessageEntity
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.domain.agent.AgentLoop
import de.nexus.agent.core.domain.agent.AgentState
import de.nexus.agent.core.data.provider.LlmRouter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val agentState: AgentState = AgentState.Idle,
    val streamingText: String = "",
    val selectedProviderId: String = "openrouter",
    val isGenerating: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ServiceLocator.db
    private val messageDao: MessageDao = db.messageDao()
    private val toolRegistry = ServiceLocator.tools
    private val llmRouter = ServiceLocator.providers

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _providerStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val providerStatus: StateFlow<Map<String, Boolean>> = _providerStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    sealed class ChatEvent {
        data class ShowSnackbar(val message: String) : ChatEvent()
        object ConversationCleared : ChatEvent()
    }

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private val currentConversationId = "default"
    private var generationJob: kotlinx.coroutines.Job? = null

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

                val agentLoop = AgentLoop(
                    provider = llmRouter,
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
                            // Tool results handled in agent loop
                        }
                        else -> { /* Other states handled by UI */ }
                    }
                }
            } catch (e: Exception) {
                _agentState.value = AgentState.Error(e.message ?: "Unbekannter Fehler")
                _streamingText.value = ""

                val errorMsg = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "Fehler: ${e.message}"
                )
                messageDao.insertMessage(errorMsg.toEntity(currentConversationId))
                _messages.update { it + errorMsg }
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

    fun dismissError() {
        _errorMessage.value = null
    }

    fun onScrollPositionChanged(isAtBottom: Boolean) {
        // Track scroll position for auto-scroll behavior
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
