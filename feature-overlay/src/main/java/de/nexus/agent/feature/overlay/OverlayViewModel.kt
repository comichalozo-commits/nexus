package de.nexus.agent.feature.overlay

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OverlayViewModel @Inject constructor() : ViewModel() {

    private val _currentApp = MutableStateFlow("")
    val currentApp: StateFlow<String> = _currentApp.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _overlayMessages = MutableStateFlow<List<OverlayMessage>>(emptyList())
    val overlayMessages: StateFlow<List<OverlayMessage>> = _overlayMessages.asStateFlow()

    fun onAppChanged(packageName: String) {
        _currentApp.value = packageName
    }

    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }

    fun addMessage(message: OverlayMessage) {
        _overlayMessages.value = _overlayMessages.value + message
    }

    fun clearMessages() {
        _overlayMessages.value = emptyList()
    }
}

data class OverlayMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
