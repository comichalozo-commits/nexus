package de.nexus.agent.feature.chat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SsidChart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.nexus.agent.feature.chat.model.ProviderStatus

private data class ExamplePrompt(
    val icon: ImageVector,
    val label: String,
    val prompt: String
)

private val examplePrompts = listOf(
    ExamplePrompt(
        icon = Icons.Default.Search,
        label = "Suche",
        prompt = "Suche nach den neuesten Entwicklungen in KI-Technologie"
    ),
    ExamplePrompt(
        icon = Icons.Default.Code,
        label = "Erstelle",
        prompt = "Erstelle einen Kotlin-Code für eine einfache REST-API"
    ),
    ExamplePrompt(
        icon = Icons.Default.SsidChart,
        label = "Analysiere",
        prompt = "Analysiere die Vor- und Nachteile von Jetpack Compose vs XML"
    ),
    ExamplePrompt(
        icon = Icons.Default.WbSunny,
        label = "Plane",
        prompt = "Plane eine Wetter-basierte Aktivität für morgen"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyState(
    onPromptClick: (String) -> Unit,
    providerStatus: ProviderStatus = ProviderStatus(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Assistant,
                        contentDescription = "Nexus Agent",
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Ich bin dein Nexus Agent",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ich kann dir helfen beim Suchen, Programmieren, Analysieren und vieles mehr. Stelle mir einfach eine Frage!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            ProviderIndicator(providerStatus = providerStatus)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Beispiel-Anfragen:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examplePrompts.forEach { example ->
                    SuggestionChip(
                        onClick = { onPromptClick(example.prompt) },
                        label = {
                            Text(
                                text = "${example.icon.let { "${example.label}" }}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = example.icon,
                                contentDescription = example.label,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            iconContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Updated example prompts with proper text shown as cards
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                examplePrompts.forEach { example ->
                    ExamplePromptCard(
                        icon = example.icon,
                        label = example.label,
                        prompt = example.prompt,
                        onClick = { onPromptClick(example.prompt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamplePromptCard(
    icon: ImageVector,
    label: String,
    prompt: String,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        label = {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ProviderIndicator(providerStatus: ProviderStatus) {
    val alpha by animateFloatAsState(
        targetValue = if (providerStatus.isConnected) 1f else 0.5f,
        animationSpec = tween(300),
        label = "provider_alpha"
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (providerStatus.isConnected)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        modifier = Modifier.alpha(alpha)
    ) {
        Text(
            text = if (providerStatus.isConnected)
                "${providerStatus.providerName} · ${providerStatus.modelName}"
            else
                "Keine Verbindung",
            style = MaterialTheme.typography.labelMedium,
            color = if (providerStatus.isConnected)
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
