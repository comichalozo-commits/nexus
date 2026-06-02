package de.nexus.agent.feature.settings.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.nexus.agent.core.common.Constants
import de.nexus.agent.feature.settings.viewmodel.SettingsViewModel
import de.nexus.agent.ui.theme.NexusAgentTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToProviderConfig: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = remember { SettingsViewModel() }
) {
    val settingsState by viewModel.settingsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection(title = "LLM-Provider") {
                Constants.SUPPORTED_PROVIDERS.forEach { providerId ->
                    val providerName = when (providerId) {
                        "openrouter" -> "OpenRouter"
                        "anthropic" -> "Anthropic (Claude)"
                        "openai" -> "OpenAI (GPT)"
                        "gemini" -> "Google Gemini"
                        else -> providerId
                    }

                    SettingsItem(
                        icon = Icons.Default.SmartToy,
                        title = providerName,
                        subtitle = if (providerId == settingsState.selectedProviderId) "Aktiv" else "Tippen zum Konfigurieren",
                        onClick = { onNavigateToProviderConfig(providerId) }
                    )
                }
            }

            SettingsSection(title = "Autonomie") {
                SettingsSwitchItem(
                    icon = Icons.Default.Security,
                    title = "Overlay-Modus",
                    subtitle = "Schwebendes Chat-Overlay Ã¼ber anderen Apps",
                    checked = settingsState.overlayEnabled,
                    onCheckedChange = { viewModel.setOverlayEnabled(it) }
                )

                SettingsSwitchItem(
                    icon = Icons.Default.Notifications,
                    title = "Benachrichtigungs-Zugriff",
                    subtitle = "Intelligente Antworten auf Benachrichtigungen",
                    checked = settingsState.notificationListenerEnabled,
                    onCheckedChange = { viewModel.setNotificationListenerEnabled(it) }
                )
            }

            SettingsSection(title = "Agent") {
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = "Erinnerungen",
                    subtitle = "${settingsState.memoryFactCount} gespeicherte Fakten",
                    onClick = { /* Navigate to memory management */ }
                )

                SettingsItem(
                    icon = Icons.Default.Schedule,
                    title = "Geplante Aufgaben",
                    subtitle = "${settingsState.scheduledJobCount} aktive Aufgaben",
                    onClick = { /* Navigate to scheduled tasks */ }
                )

                SettingsItem(
                    icon = Icons.Default.Build,
                    title = "FÃ¤higkeiten (Skills)",
                    subtitle = "${settingsState.skillCount} installierte Skills",
                    onClick = { /* Navigate to skills */ }
                )
            }

            SettingsSection(title = "Ãœber") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Nexus Agent",
                    subtitle = "Version 1.0.0",
                    onClick = { }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    NexusAgentTheme {
        SettingsScreen(
            onNavigateToProviderConfig = {},
            onNavigateBack = {}
        )
    }
}
