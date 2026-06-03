package de.nexus.agent.feature.settings.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.nexus.agent.core.ServiceLocator
import de.nexus.agent.core.data.db.MemoryFactDao
import de.nexus.agent.core.data.db.ScheduledJobDao
import de.nexus.agent.core.data.db.SkillDao
import de.nexus.agent.core.data.model.LlmProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class SettingsState(
    val selectedProviderId: String = "openrouter",
    val overlayEnabled: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val autonomyLevel: Int = 1,
    val memoryFactCount: Int = 0,
    val scheduledJobCount: Int = 0,
    val skillCount: Int = 0
)

data class TestConnectionResult(
    val success: Boolean,
    val message: String,
    val isLoading: Boolean = false
)

private val Application.dataStore by preferencesDataStore(name = "nexus_settings")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.dataStore
    private val db = ServiceLocator.db
    private val llmRouter = ServiceLocator.providers

    private val memoryFactDao: MemoryFactDao = db.memoryFactDao()
    private val scheduledJobDao: ScheduledJobDao = db.scheduledJobDao()
    private val skillDao: SkillDao = db.skillDao()

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _testConnectionResult = MutableStateFlow<TestConnectionResult?>(null)
    val testConnectionResult: StateFlow<TestConnectionResult?> = _testConnectionResult.asStateFlow()

    private val _providers = MutableStateFlow<Map<String, LlmProvider>>(emptyMap())

    init {
        loadSettings()
        observeCounts()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                val selectedId = prefs[Keys.SELECTED_PROVIDER] ?: "openrouter"
                val overlay = prefs[Keys.OVERLAY_ENABLED] ?: false
                val notifications = prefs[Keys.NOTIFICATION_LISTENER] ?: false
                val autonomy = prefs[Keys.AUTONOMY_LEVEL] ?: 1

                _settingsState.value = _settingsState.value.copy(
                    selectedProviderId = selectedId,
                    overlayEnabled = overlay,
                    notificationListenerEnabled = notifications,
                    autonomyLevel = autonomy
                )

                val providers = mutableMapOf<String, LlmProvider>()
                listOf("openrouter", "anthropic", "openai", "gemini").forEach { id ->
                    providers[id] = loadProviderFromPrefs(prefs, id)
                }
                _providers.value = providers
            }
        }
    }

    private fun loadProviderFromPrefs(prefs: androidx.datastore.preferences.core.Preferences, providerId: String): LlmProvider {
        val prefix = "${providerId}_"
        return LlmProvider(
            id = providerId,
            name = providerId.replaceFirstChar { it.uppercase() },
            baseUrl = prefs[stringPreferencesKey("${prefix}base_url")] ?: getDefaultBaseUrl(providerId),
            apiKey = prefs[stringPreferencesKey("${prefix}api_key")] ?: "",
            model = prefs[stringPreferencesKey("${prefix}model")] ?: "",
            temperature = prefs[stringPreferencesKey("${prefix}temperature")]?.toFloatOrNull() ?: 0.7f,
            maxTokens = prefs[stringPreferencesKey("${prefix}max_tokens")]?.toIntOrNull() ?: 4096,
            isEnabled = _settingsState.value.selectedProviderId == providerId,
            supportsStreaming = true,
            supportsVision = providerId == "openai" || providerId == "gemini",
            supportsTools = true
        )
    }

    private fun getDefaultBaseUrl(providerId: String): String = when (providerId) {
        "openrouter" -> "https://openrouter.ai/api/v1"
        "anthropic" -> "https://api.anthropic.com"
        "openai" -> "https://api.openai.com/v1"
        "gemini" -> "https://generativelanguage.googleapis.com"
        else -> ""
    }

    private fun observeCounts() {
        viewModelScope.launch {
            memoryFactDao.getAllFacts().collect { facts ->
                _settingsState.value = _settingsState.value.copy(memoryFactCount = facts.size)
            }
        }
        viewModelScope.launch {
            scheduledJobDao.getAllJobs().collect { jobs ->
                _settingsState.value = _settingsState.value.copy(
                    scheduledJobCount = jobs.count { it.isEnabled }
                )
            }
        }
        viewModelScope.launch {
            skillDao.getAllSkills().collect { skills ->
                _settingsState.value = _settingsState.value.copy(skillCount = skills.size)
            }
        }
    }

    fun getProvider(providerId: String): StateFlow<LlmProvider> {
        return _providers.map { it[providerId] ?: LlmProvider(id = providerId, name = providerId, baseUrl = "") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
                _providers.value[providerId] ?: LlmProvider(id = providerId, name = providerId, baseUrl = ""))
    }

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[Keys.SELECTED_PROVIDER] = providerId
            }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[Keys.OVERLAY_ENABLED] = enabled
            }
        }
    }

    fun setNotificationListenerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[Keys.NOTIFICATION_LISTENER] = enabled
            }
        }
    }

    fun setAutonomyLevel(level: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[Keys.AUTONOMY_LEVEL] = level
            }
        }
    }

    private fun updateProviderField(providerId: String, update: LlmProvider.() -> LlmProvider) {
        viewModelScope.launch {
            _providers.value = _providers.value.toMutableMap().also { map ->
                map[providerId] = map[providerId]?.update() ?: LlmProvider(id = providerId, name = providerId, baseUrl = "")
            }
        }
    }

    fun updateProviderApiKey(providerId: String, key: String) {
        updateProviderString(providerId, "api_key", key)
        updateProviderField(providerId) { copy(apiKey = key) }
    }

    fun updateProviderBaseUrl(providerId: String, url: String) {
        updateProviderString(providerId, "base_url", url)
        updateProviderField(providerId) { copy(baseUrl = url) }
    }

    fun updateProviderModel(providerId: String, model: String) {
        updateProviderString(providerId, "model", model)
        updateProviderField(providerId) { copy(model = model) }
    }

    fun updateProviderTemperature(providerId: String, temp: Float) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("${providerId}_temperature")] = temp.toString()
            }
        }
        updateProviderField(providerId) { copy(temperature = temp) }
    }

    fun updateProviderMaxTokens(providerId: String, tokens: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[intPreferencesKey("${providerId}_max_tokens")] = tokens
            }
        }
        updateProviderField(providerId) { copy(maxTokens = tokens) }
    }

    private fun updateProviderString(providerId: String, field: String, value: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("${providerId}_$field")] = value
            }
        }
    }

    fun testProviderConnection(providerId: String) {
        viewModelScope.launch {
            _testConnectionResult.value = TestConnectionResult(
                success = false,
                message = "Teste Verbindung...",
                isLoading = true
            )

            try {
                val provider = _providers.value[providerId] ?: run {
                    _testConnectionResult.value = TestConnectionResult(
                        success = false,
                        message = "Provider nicht gefunden"
                    )
                    return@launch
                }

                if (provider.apiKey.isBlank()) {
                    _testConnectionResult.value = TestConnectionResult(
                        success = false,
                        message = "Bitte zuerst einen API-Schlüssel eingeben"
                    )
                    return@launch
                }

                val result = llmRouter.healthCheck()
                val isHealthy = result[de.nexus.agent.core.data.model.ProviderType.valueOf(providerId.uppercase())] ?: false

                if (isHealthy) {
                    _testConnectionResult.value = TestConnectionResult(
                        success = true,
                        message = "✅ Verbindung erfolgreich!"
                    )
                } else {
                    _testConnectionResult.value = TestConnectionResult(
                        success = false,
                        message = "❌ Verbindung fehlgeschlagen"
                    )
                }
            } catch (e: Exception) {
                _testConnectionResult.value = TestConnectionResult(
                    success = false,
                    message = "❌ Fehler: ${e.message}"
                )
            }
        }
    }

    fun clearTestResult() {
        _testConnectionResult.value = null
    }

    private object Keys {
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val NOTIFICATION_LISTENER = booleanPreferencesKey("notification_listener")
        val AUTONOMY_LEVEL = intPreferencesKey("autonomy_level")
    }
}
