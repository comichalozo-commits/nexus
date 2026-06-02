package de.nexus.agent.feature.chat.ui.previews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.nexus.agent.feature.chat.model.ChatMessage
import de.nexus.agent.feature.chat.model.MessageRole
import de.nexus.agent.feature.chat.model.ProviderStatus
import de.nexus.agent.feature.chat.model.ToolCall
import de.nexus.agent.feature.chat.model.ToolStatus
import de.nexus.agent.feature.chat.ui.ChatScreen
import de.nexus.agent.feature.chat.ui.EmptyState
import de.nexus.agent.feature.chat.ui.InputBar
import de.nexus.agent.feature.chat.ui.MessageBubble
import de.nexus.agent.feature.chat.ui.StreamingText
import de.nexus.agent.feature.chat.ui.ToolCallCard
import de.nexus.agent.ui.theme.NexusAgentTheme

private val sampleUserMessage = ChatMessage(
    role = MessageRole.USER,
    content = "Kannst du mir helfen, eine Kotlin REST-API zu erstellen?"
)

private val sampleAssistantMessage = ChatMessage(
    role = MessageRole.ASSISTANT,
    content = "Natürlich! Hier ist ein einfaches Beispiel für eine Kotlin REST-API mit Ktor:\n\n```kotlin\nfun Application.module() {\n    routing {\n        get(\"/hello\") {\n            call.respond(\"Hello, World!\")\n        }\n    }\n}\n```\n\nDas ist ein minimaler Start. Soll ich mehr Details hinzufügen?"
)

private val sampleStreamingMessage = ChatMessage(
    role = MessageRole.ASSISTANT,
    content = "Ich verarbeite deine Anfrage...",
    isStreaming = true
)

private val sampleToolCallMessage = ChatMessage(
    role = MessageRole.ASSISTANT,
    content = "Ich suche nach den neuesten Informationen...",
    toolCalls = listOf(
        ToolCall(
            name = "web_search",
            parameters = mapOf("query" to "Kotlin REST API Ktor"),
            status = ToolStatus.SUCCESS,
            result = "Found 15 relevant results about Ktor REST APIs"
        ),
        ToolCall(
            name = "code_exec",
            parameters = mapOf("language" to "kotlin"),
            status = ToolStatus.RUNNING
        )
    )
)

private val sampleErrorMessage = ChatMessage(
    role = MessageRole.ASSISTANT,
    content = "Fehler: Die Anfrage konnte nicht verarbeitet werden."
)

@Preview(name = "Chat Screen Light", showBackground = true)
@Composable
private fun ChatScreenLightPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen()
        }
    }
}

@Preview(name = "Chat Screen Dark", showBackground = true)
@Composable
private fun ChatScreenDarkPreview() {
    NexusAgentTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen()
        }
    }
}

@Preview(name = "Empty State Light", showBackground = true)
@Composable
private fun EmptyStateLightPreview() {
    NexusAgentTheme(darkTheme = false) {
        EmptyState(
            onPromptClick = { },
            providerStatus = ProviderStatus(
                providerName = "Nexus Agent",
                modelName = "GPT-4",
                isConnected = true
            )
        )
    }
}

@Preview(name = "Empty State Dark", showBackground = true)
@Composable
private fun EmptyStateDarkPreview() {
    NexusAgentTheme(darkTheme = true) {
        EmptyState(
            onPromptClick = { },
            providerStatus = ProviderStatus(
                providerName = "Nexus Agent",
                modelName = "GPT-4",
                isConnected = false
            )
        )
    }
}

@Preview(name = "User Message Bubble", showBackground = true)
@Composable
private fun UserMessageBubblePreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            MessageBubble(
                message = sampleUserMessage,
                isUser = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "Assistant Message Bubble", showBackground = true)
@Composable
private fun AssistantMessageBubblePreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            MessageBubble(
                message = sampleAssistantMessage,
                isUser = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "Streaming Message Bubble", showBackground = true)
@Composable
private fun StreamingMessageBubblePreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            MessageBubble(
                message = sampleStreamingMessage,
                isUser = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "ToolCallCard - Pending", showBackground = true)
@Composable
private fun ToolCallCardPendingPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            ToolCallCard(
                toolCall = ToolCall(
                    name = "web_search",
                    parameters = mapOf("query" to "Kotlin REST API"),
                    status = ToolStatus.PENDING
                ),
                status = ToolStatus.PENDING
            )
        }
    }
}

@Preview(name = "ToolCallCard - Running", showBackground = true)
@Composable
private fun ToolCallCardRunningPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            ToolCallCard(
                toolCall = ToolCall(
                    name = "code_exec",
                    parameters = mapOf("language" to "kotlin"),
                    status = ToolStatus.RUNNING
                ),
                status = ToolStatus.RUNNING
            )
        }
    }
}

@Preview(name = "ToolCallCard - Success", showBackground = true)
@Composable
private fun ToolCallCardSuccessPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            ToolCallCard(
                toolCall = ToolCall(
                    name = "web_search",
                    parameters = mapOf("query" to "Kotlin REST API"),
                    status = ToolStatus.SUCCESS,
                    result = "Found 15 relevant results"
                ),
                status = ToolStatus.SUCCESS
            )
        }
    }
}

@Preview(name = "ToolCallCard - Error", showBackground = true)
@Composable
private fun ToolCallCardErrorPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            ToolCallCard(
                toolCall = ToolCall(
                    name = "file_read",
                    parameters = mapOf("path" to "/tmp/data.json"),
                    status = ToolStatus.ERROR,
                    errorMessage = "File not found: /tmp/data.json"
                ),
                status = ToolStatus.ERROR
            )
        }
    }
}

@Preview(name = "ToolCallCard - Dark", showBackground = true)
@Composable
private fun ToolCallCardDarkPreview() {
    NexusAgentTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                ToolCallCard(
                    toolCall = ToolCall(
                        name = "web_search",
                        parameters = mapOf("query" to "test"),
                        status = ToolStatus.SUCCESS,
                        result = "Success result"
                    ),
                    status = ToolStatus.SUCCESS
                )
                Spacer(modifier = Modifier.height(8.dp))
                ToolCallCard(
                    toolCall = ToolCall(
                        name = "code_exec",
                        parameters = mapOf("lang" to "kt"),
                        status = ToolStatus.ERROR,
                        errorMessage = "Compilation failed"
                    ),
                    status = ToolStatus.ERROR
                )
            }
        }
    }
}

@Preview(name = "InputBar - Empty", showBackground = true)
@Composable
private fun InputBarEmptyPreview() {
    NexusAgentTheme(darkTheme = false) {
        InputBar(
            onSend = { },
            isProcessing = false
        )
    }
}

@Preview(name = "InputBar - With Text", showBackground = true)
@Composable
private fun InputBarWithTextPreview() {
    NexusAgentTheme(darkTheme = false) {
        InputBar(
            onSend = { },
            isProcessing = false
        )
    }
}

@Preview(name = "InputBar - Processing", showBackground = true)
@Composable
private fun InputBarProcessingPreview() {
    NexusAgentTheme(darkTheme = false) {
        InputBar(
            onSend = { },
            isProcessing = true
        )
    }
}

@Preview(name = "StreamingText - Normal", showBackground = true)
@Composable
private fun StreamingTextNormalPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            StreamingText(
                text = "Das ist ein **fetter** Text mit *kursiv* und `inline code`.",
                isStreaming = false
            )
        }
    }
}

@Preview(name = "StreamingText - Streaming", showBackground = true)
@Composable
private fun StreamingTextStreamingPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            StreamingText(
                text = "Streaming text mit **Markdown** support...",
                isStreaming = true
            )
        }
    }
}

@Preview(name = "Message with ToolCalls", showBackground = true)
@Composable
private fun MessageWithToolCallsPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            MessageBubble(
                message = sampleToolCallMessage,
                isUser = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "Full Chat Preview Light", showBackground = true, heightDp = 800)
@Composable
private fun FullChatLightPreview() {
    NexusAgentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                MessageBubble(
                    message = sampleUserMessage,
                    isUser = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                MessageBubble(
                    message = sampleAssistantMessage,
                    isUser = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                MessageBubble(
                    message = sampleUserMessage.copy(
                        content = "Erkläre mir den Code im Detail"
                    ),
                    isUser = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                MessageBubble(
                    message = sampleStreamingMessage,
                    isUser = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(name = "Full Chat Preview Dark", showBackground = true, heightDp = 800)
@Composable
private fun FullChatDarkPreview() {
    NexusAgentTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                MessageBubble(
                    message = sampleUserMessage,
                    isUser = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                MessageBubble(
                    message = sampleAssistantMessage,
                    isUser = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                MessageBubble(
                    message = sampleToolCallMessage,
                    isUser = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
