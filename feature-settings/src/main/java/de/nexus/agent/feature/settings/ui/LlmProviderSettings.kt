package de.nexus.agent.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.feature.settings.viewmodel.SettingsViewModel
import de.nexus.agent.ui.theme.NexusAgentTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmProviderSettingsScreen(
    providerId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val provider by viewModel.getProvider(providerId).collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val testResult by viewModel.testConnectionResult.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(testResult?.message) {
        testResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTestResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (providerId) {
                            "openrouter" -> "OpenRouter"
                            "anthropic" -> "Anthropic"
                            "openai" -> "OpenAI"
                            "gemini" -> "Google Gemini"
                            else -> providerId
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ZurÃ¼ck"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key
            OutlinedTextField(
                value = provider.apiKey,
                onValueChange = { viewModel.updateProviderApiKey(providerId, it) },
                label = { Text("API-SchlÃ¼ssel") },
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Text(if (showApiKey) "ðŸ”’" else "ðŸ‘")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Base URL
            OutlinedTextField(
                value = provider.baseUrl,
                onValueChange = { viewModel.updateProviderBaseUrl(providerId, it) },
                label = { Text("Basis-URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            // Model Selection
            ModelDropdown(
                provider = provider,
                onModelSelected = { viewModel.updateProviderModel(providerId, it) }
            )

            // Temperature
            Column {
                Text(
                    text = "Temperatur: ${provider.temperature}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = provider.temperature,
                    onValueChange = { viewModel.updateProviderTemperatureProviderId, it) },
                    valueRange = 0f..2f,
                    steps = 19
                )
            }

            // Max Tokens
            OutlinedTextField(
                value = provider.maxTokens.toString(),
                onValueChange = {
                    val tokens = it.toIntOrNull() ?: provider.maxTokens
                    viewModel.updateProviderMaxTokens(providerId, tokens)
                },
                label = { Text("Maximale Tokens") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Test Connection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Verbindungstest",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Teste ob die API-Verbindung mit den aktuellen Einstellungen funktioniert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { viewModel.testProviderConnection(providerId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !testResult?.isLoading!!
                    ) {
                        if (testResult?.isLoading == true) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Verbindung testen")
                        }
                    }

                    testResult?.let { result ->
                        if (!result.isLoading) {
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdown(
    provider: LlmProvider,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val models = provider.listedModelSelection()
    val selectedModel = provider.model.ifBlank { models.firstOrNull() ?: "" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedModel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Modell") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model, maxLines = 1) },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LlmProviderSettingsPreview() {
    NexusAgentTheme {
        LlmProviderSettingsScreen(
            providerId = "openrouter",
            onNavigateBack = {}
        )
    }
}
