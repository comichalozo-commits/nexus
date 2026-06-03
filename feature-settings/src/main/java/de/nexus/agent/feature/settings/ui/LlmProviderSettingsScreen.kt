package de.nexus.agent.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.nexus.agent.core.data.model.listedModelSelection
import de.nexus.agent.feature.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmProviderSettingsScreen(
    providerId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val providerState by viewModel.getProvider(providerId).collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()

    var apiKey by remember(providerState.apiKey) { mutableStateOf(providerState.apiKey) }
    var baseUrl by remember(providerState.baseUrl) { mutableStateOf(providerState.baseUrl) }
    var model by remember(providerState.model) { mutableStateOf(providerState.model) }
    var temperature by remember(providerState.temperature) { mutableStateOf(providerState.temperature.toString()) }
    var maxTokens by remember(providerState.maxTokens) { mutableStateOf(providerState.maxTokens.toString()) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val availableModels = remember(providerState) { providerState.listedModelSelection() }

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
                            contentDescription = "Zurück"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
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
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    viewModel.updateProviderApiKey(providerId, it)
                },
                label = { Text("API-Schlüssel") },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (showApiKey) "Verbergen" else "Anzeigen"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Base URL
            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    viewModel.updateProviderBaseUrl(providerId, it)
                },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Model Selection
            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        viewModel.updateProviderModel(providerId, it)
                    },
                    label = { Text("Modell") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    availableModels.forEach { modelOption: String ->
                        DropdownMenuItem(
                            text = { Text(modelOption) },
                            onClick = {
                                model = modelOption
                                viewModel.updateProviderModel(providerId, modelOption)
                                modelDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Temperature Slider
            Text(
                text = "Temperatur: ${temperature.toFloatOrNull() ?: 0.7f}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = temperature.toFloatOrNull() ?: 0.7f,
                onValueChange = { newVal ->
                    temperature = newVal.toString()
                    viewModel.updateProviderTemperature(providerId, newVal)
                },
                valueRange = 0f..2f,
                steps = 19
            )

            // Max Tokens
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { newVal ->
                    maxTokens = newVal
                    newVal.toIntOrNull()?.let { viewModel.updateProviderMaxTokens(providerId, it) }
                },
                label = { Text("Max Tokens") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Test Connection Button
            Button(
                onClick = {
                    viewModel.testProviderConnection(providerId)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !testResult?.isLoading!!
            ) {
                if (testResult?.isLoading == true) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (testResult?.isLoading == true) "Teste..." else "Verbindung testen")
            }

            // Test Result
            testResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result.success)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.success)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Active provider button
            Button(
                onClick = {
                    viewModel.selectProvider(providerId)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (providerState.isEnabled) "Aktiver Provider" else "Als aktiven Provider festlegen")
            }
        }
    }
}
