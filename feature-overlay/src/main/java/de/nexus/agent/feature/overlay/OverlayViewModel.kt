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

    private val _overlayMessages = MutableStateFlow<List<OverlayUiMessage>>(emptyList())
    val overlayMessages: StateFlow<List<OverlayUiMessage>> = _overlayMessages.asStateFlow()

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
}

data class OverlayUiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
