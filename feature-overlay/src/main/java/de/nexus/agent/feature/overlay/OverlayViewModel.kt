package de.nexus.agent.feature.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.nexus.agent.core.ServiceLocator
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.domain.agent.AgentLoop
import de.nexus.agent.core.domain.agent.AgentState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing the overlay chat state.
 *
 * Responsibilities:
 * - Tracks UI state (Minimized / Expanded / Hidden)
 * - Manages the chat message list and input text
 * - Forwards user messages to the core [AgentLoop] and streams responses back
 */
class OverlayViewModel : ViewModel() {

    // -- UI state --------------------------------------------------------

    private val _currentApp = MutableStateFlow("")
    val currentApp: StateFlow<String> = _currentApp.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _uiState = MutableStateFlow<OverlayUiState>(OverlayUiState.Minimized)
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    // -- Chat state ------------------------------------------------------

    private val _messages = MutableStateFlow<List<OverlayUiMessage>>(emptyList())
    val messages: StateFlow<List<OverlayUiMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -- Agent -----------------------------------------------------------

    private var agentLoop: AgentLoop? = null
    private var agentJob: Job? = null

    // -- Public API ------------------------------------------------------

    fun onAppChanged(packageName: String) {
        _currentApp.value = packageName
    }

    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }

    fun expand() {
        _uiState.value = OverlayUiState.Expanded
        _isExpanded.value = true
    }

    fun minimize() {
        _uiState.value = OverlayUiState.Minimized
        _isExpanded.value = false
    }

    fun hide() {
        _uiState.value = OverlayUiState.Hidden
        _isExpanded.value = false
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    // -- Messaging -------------------------------------------------------

    /**
     * Sends the current input text as a user message and triggers the
     * [AgentLoop] to get a response. The response is streamed back
     * into the overlay message list.
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        agentJob?.cancel()

        val userMsg = OverlayUiMessage(
            text = text,
            isUser = true
        )
        _messages.value = _messages.value + userMsg
        _inputText.value = ""
        _isLoading.value = true

        val conversation = _messages.value.map { msg ->
            ChatMessage(
                role = if (msg.isUser) MessageRole.USER else MessageRole.ASSISTANT,
                content = msg.text
            )
        }

        if (agentLoop == null) {
            try {
                val router = ServiceLocator.providers
                val tools = ServiceLocator.tools
                agentLoop = AgentLoop(router, tools)
            } catch (_: Exception) {
                _messages.value = _messages.value + OverlayUiMessage(
                    text = "Fehler: AgentLoop konnte nicht initialisiert werden.",
                    isUser = false
                )
                _isLoading.value = false
                return
            }
        }

        val loop = agentLoop!!

        agentJob = viewModelScope.launch {
            try {
                var streamingMessageAdded = false

                loop.run(conversation).collect { state ->
                    when (state) {
                        is AgentState.Streaming -> {
                            if (!streamingMessageAdded) {
                                val assistantMsg = OverlayUiMessage(
                                    text = state.partialText,
                                    isUser = false
                                )
                                _messages.value = _messages.value + assistantMsg
                                streamingMessageAdded = true
                            } else {
                                val lastMsg = _messages.value.lastOrNull()
                                if (lastMsg != null && !lastMsg.isUser) {
                                    val updated = lastMsg.copy(text = state.partialText)
                                    _messages.value = _messages.value.toMutableList().apply {
                                        set(lastIndex, updated)
                                    }
                                }
                            }
                        }
                        is AgentState.Complete -> {
                            _isLoading.value = false
                        }
                        is AgentState.Error -> {
                            _messages.value = _messages.value + OverlayUiMessage(
                                text = "Fehler: ${state.message}",
                                isUser = false
                            )
                            _isLoading.value = false
                        }
                        else -> { /* Thinking, ExecutingTool, ToolResultState */ }
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + OverlayUiMessage(
                    text = "Fehler: ${e.message ?: "Unbekannter Fehler"}",
                    isUser = false
                )
                _isLoading.value = false
            }
        }
    }
}

/** A single message in the overlay chat. */
data class OverlayUiMessage(
    val text: String,
    val isUser: Boolean,
    val id: String = java.util.UUID.randomUUID().toString()
)
