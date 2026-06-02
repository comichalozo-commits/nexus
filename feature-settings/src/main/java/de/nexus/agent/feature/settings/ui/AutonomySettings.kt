package de.nexus.agent.feature.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.nexus.agent.core.R

private enum class AutonomyPolicy(val label: String) {
    ASK("Fragen"),
    CONFIRM("Bestätigen"),
    AUTO("Automatisch")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutonomySettingsScreen(
    onNavigateBack: () -> Unit
) {
    var selectedPolicy by remember { mutableIntStateOf(1) }
    var customPolicyMode by remember { mutableStateOf(false) }

    val toolCategories = remember {
        listOf(
            ToolCategory(
                name = "Nachrichten",
                icon = Icons.Default.Message,
                tools = listOf(
                    ToolEntry("SMS lesen", AutonomyPolicy.CONFIRM),
                    ToolEntry("SMS senden", AutonomyPolicy.ASK),
                    ToolEntry("E-Mail", AutonomyPolicy.CONFIRM)
                )
            ),
            ToolCategory(
                name = "Telefon",
                icon = Icons.Default.Contacts,
                tools = listOf(
                    ToolEntry("Kontakte lesen", AutonomyPolicy.AUTO),
                    ToolEntry("Anrufen", AutonomyPolicy.ASK)
                )
            ),
            ToolCategory(
                name = "Gerät",
                icon = Icons.Default.PhotoCamera,
                tools = listOf(
                    ToolEntry("Kamera", AutonomyPolicy.CONFIRM),
                    ToolEntry("Standort", AutonomyPolicy.AUTO),
                    ToolEntry("Speicher", AutonomyPolicy.CONFIRM)
                )
            ),
            ToolCategory(
                name = "System",
                icon = Icons.Default.Tune,
                tools = listOf(
                    ToolEntry("Kalender", AutonomyPolicy.CONFIRM),
                    ToolEntry("Benachrichtigungen", AutonomyPolicy.AUTO),
                    ToolEntry("Overlay", AutonomyPolicy.AUTO)
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.autonomy_settings_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                actions = {
                    if (customPolicyMode) {
                        Button(
                            onClick = { customPolicyMode = false },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Fertig")
                        }
                    } else {
                        IconButton(onClick = { customPolicyMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Anpassen"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
            // ── Policy Overview Cards ────────────────────────────
            Text(
                text = "Richtlinie",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            PolicyCard(
                title = stringResource(R.string.autonomy_cautious),
                description = stringResource(R.string.autonomy_cautious_desc),
                icon = Icons.Default.Notifications,
                isSelected = selectedPolicy == 0,
                onClick = { selectedPolicy = 0 }
            )

            PolicyCard(
                title = stringResource(R.string.autonomy_balanced),
                description = stringResource(R.string.autonomy_balanced_desc),
                icon = Icons.Default.Tune,
                isSelected = selectedPolicy == 1,
                onClick = { selectedPolicy = 1 }
            )

            PolicyCard(
                title = stringResource(R.string.autonomy_full_auto),
                description = stringResource(R.string.autonomy_full_auto_desc),
                icon = Icons.Default.Build,
                isSelected = selectedPolicy == 2,
                onClick = { selectedPolicy = 2 }
            )

            // ── Tool Category Overview ───────────────────────────
            Text(
                text = stringResource(R.string.tool_category),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (customPolicyMode) {
                CustomPolicyBuilder(toolCategories = toolCategories)
            } else {
                ToolCategoryOverview(
                    toolCategories = toolCategories,
                    policyIndex = selectedPolicy
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PolicyCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(200),
        label = "policy_border"
    )
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolCategoryOverview(
    toolCategories: List<ToolCategory>,
    policyIndex: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Richtlinie ${when (policyIndex) {
                    0 -> "Vorsichtig"
                    1 -> "Ausbalanciert"
                    else -> "Vollautomatisch"
                }}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            toolCategories.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        category.tools.forEach { tool ->
                            PolicyChip(
                                label = tool.name,
                                policy = tool.policy,
                                index = policyIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomPolicyBuilder(toolCategories: List<ToolCategory>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.custom_policy),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            toolCategories.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        category.tools.forEach { tool ->
                            CustomPolicyRow(toolEntry = tool)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomPolicyRow(toolEntry: ToolEntry) {
    var policy by remember { mutableStateOf(toolEntry.policy) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = toolEntry.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AutonomyPolicy.entries.forEach { p ->
                PolicyChip(
                    label = p.label,
                    policy = p,
                    index = if (policy == p) 0 else 1,
                    small = true,
                    onClick = { policy = p }
                )
            }
        }
    }
}

@Composable
private fun PolicyChip(
    label: String,
    policy: AutonomyPolicy,
    index: Int,
    small: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val color = when (policy) {
        AutonomyPolicy.ASK -> MaterialTheme.colorScheme.error
        AutonomyPolicy.CONFIRM -> MaterialTheme.colorScheme.tertiary
        AutonomyPolicy.AUTO -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(if (small) 4.dp else 6.dp))
                .clickable { onClick() }
        } else {
            Modifier
        },
        shape = RoundedCornerShape(if (small) 4.dp else 6.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(
                horizontal = if (small) 6.dp else 10.dp,
                vertical = if (small) 2.dp else 4.dp
            )
        )
    }
}

private data class ToolCategory(
    val name: String,
    val icon: ImageVector,
    val tools: List<ToolEntry>
)

private data class ToolEntry(
    val name: String,
    val policy: AutonomyPolicy
)
