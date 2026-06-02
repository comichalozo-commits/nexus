package de.nexus.agent.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getMessagesForConversationSync(conversationId: String, limit: Int = 100): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, content: String)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int
}

@Dao
interface ToolDao {
    @Query("SELECT * FROM tool_calls WHERE messageId = :messageId ORDER BY timestamp ASC")
    suspend fun getToolsForMessage(messageId: String): List<ToolEntity>

    @Query("SELECT * FROM tool_calls WHERE id = :toolId")
    suspend fun getToolById(toolId: String): ToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTool(tool: ToolEntity)

    @Update
    suspend fun updateTool(tool: ToolEntity)

    @Query("UPDATE tool_calls SET result = :result, status = :status WHERE id = :toolId")
    suspend fun updateToolResult(toolId: String, result: String, status: String)

    @Query("DELETE FROM tool_calls WHERE messageId = :messageId")
    suspend fun deleteToolsForMessage(messageId: String)
}

@Dao
interface MemoryFactDao {
    @Query("SELECT * FROM memory_facts ORDER BY timestamp DESC")
    fun getAllFacts(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFacts(limit: Int = 20): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE content LIKE '%' || :query || '%' ORDER BY relevance DESC")
    suspend fun searchFacts(query: String): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: MemoryFactEntity)

    @Delete
    suspend fun deleteFact(fact: MemoryFactEntity)

    @Query("DELETE FROM memory_facts WHERE id = :factId")
    suspend fun deleteFactById(factId: String)

    @Query("DELETE FROM memory_facts")
    suspend fun deleteAllFacts()

    @Query("SELECT COUNT(*) FROM memory_facts")
    suspend fun getFactCount(): Int
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY installedTimestamp DESC")
    fun getAllSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE isEnabled = 1")
    fun getEnabledSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE id = :skillId")
    suspend fun getSkillById(skillId: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntity)

    @Update
    suspend fun updateSkill(skill: SkillEntity)

    @Query("DELETE FROM skills WHERE id = :skillId")
    suspend fun deleteSkillById(skillId: String)
}

@Dao
interface ScheduledJobDao {
    @Query("SELECT * FROM scheduled_jobs ORDER BY nextRunTimestamp ASC")
    fun getAllJobs(): Flow<List<ScheduledJobEntity>>

    @Query("SELECT * FROM scheduled_jobs WHERE isEnabled = 1")
    suspend fun getEnabledJobs(): List<ScheduledJobEntity>

    @Query("SELECT * FROM scheduled_jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: String): ScheduledJobEntity?

    @Query("SELECT * FROM scheduled_jobs WHERE jobType = :jobType LIMIT 1")
    suspend fun getJobByType(jobType: String): ScheduledJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ScheduledJobEntity)

    @Update
    suspend fun updateJob(job: ScheduledJobEntity)

    @Query("UPDATE scheduled_jobs SET isEnabled = :enabled WHERE id = :jobId")
    suspend fun setJobEnabled(jobId: String, enabled: Boolean)

    @Query("UPDATE scheduled_jobs SET lastRunTimestamp = :timestamp, nextRunTimestamp = :nextTimestamp WHERE id = :jobId")
    suspend fun updateJobRunTimestamps(jobId: String, timestamp: Long, nextTimestamp: Long?)

    @Delete
    suspend fun deleteJob(job: ScheduledJobEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isActive = 0 WHERE id = :id")
    suspend fun deactivateConversation(id: String)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)
}
