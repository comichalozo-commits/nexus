package de.nexus.agent.feature.overlay

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayViewModel  constructor() : ViewModel() {

    private val _currentApp = MutableStateFlow("")
    val currentApp: StateFlow<String> = _currentApp.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _uiState = MutableStateFlow<OverlayUiState>(OverlayUiState.Minimized)
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private val _overlayMessages = MutableStateFlow<List<OverlayUiMessage>>(emptyList())
    val messages: StateFlow<List<OverlayUiMessage>> = _overlayMessages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun onAppChanged(packageName: String) {
        _currentApp.value = packageName
    }

    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }

    fun addMessage(message: OverlayUiMessage) {
        _overlayMessages.value = _overlayMessages.value + message
    }

    fun clearMessages() {
        _overlayMessages.value = emptyList()
    }

    fun expand() {
        _uiState.value = OverlayUiState.Expanded
        _isExpanded.value = true
    }

    fun minimize() {
        _uiState.value = OverlayUiState.Minimized
        _isExpanded.value = false
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        addMessage(OverlayUiMessage(text = text, isUser = true))
        _inputText.value = ""
        _isLoading.value = true
        // TODO: Integrate with actual agent engine
        _isLoading.value = false
    }
}

data class OverlayUiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
