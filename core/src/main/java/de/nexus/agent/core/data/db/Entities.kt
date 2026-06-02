package de.nexus.agent.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val content: String,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val timestamp: Long,
    val conversationId: String = "default"
)

@Entity(tableName = "tool_calls")
data class ToolEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    val content: String,
    val source: String = "",
    val relevance: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val embeddingJson: String? = null
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val manifestJson: String = "",
    val isEnabled: Boolean = false,
    val installedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "scheduled_jobs")
data class ScheduledJobEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val workRequestId: String? = null,
    val intervalMinutes: Long = 30,
    val isEnabled: Boolean = false,
    val lastRunTimestamp: Long? = null,
    val nextRunTimestamp: Long? = null,
    val jobType: String = "HEARTBEAT"
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
