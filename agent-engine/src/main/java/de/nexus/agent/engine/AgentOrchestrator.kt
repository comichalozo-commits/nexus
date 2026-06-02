package de.nexus.agent.engine

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import de.nexus.agent.core.common.Constants
import de.nexus.agent.core.data.db.MessageDao
import de.nexus.agent.core.data.db.ScheduledJobDao
import de.nexus.agent.core.data.db.ConversationDao
import de.nexus.agent.core.data.db.ToolDao
import de.nexus.agent.core.data.model.ChatMessage
import de.nexus.agent.core.data.model.LlmProvider
import de.nexus.agent.core.data.model.MessageRole
import de.nexus.agent.core.data.model.ToolCall
import de.nexus.agent.core.data.model.ToolDefinition
import de.nexus.agent.core.data.provider.CompositeProviderFactory
import de.nexus.agent.core.domain.agent.AgentLoop
import de.nexus.agent.core.domain.agent.AgentState
import de.nexus.agent.core.domain.agent.ToolRegistry
import de.nexus.agent.core.domain.memory.MemorySystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val toolDao: ToolDao,
    private val conversationDao: ConversationDao,
    private val scheduledJobDao: ScheduledJobDao,
    private val providerFactory: CompositeProviderFactory,
    private val toolRegistry: ToolRegistry,
    private val memorySystem: MemorySystem
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun runHeartbeat() {
        val enabledJobs = scheduledJobDao.getEnabledJobs()

        for (job in enabledJobs) {
            val lastRun = job.lastRunTimestamp ?: 0L
            val intervalMs = job.intervalMinutes * 60 * 1000
            val now = System.currentTimeMillis()

            if (now - lastRun >= intervalMs) {
                executeJob(job.id, job.name, job.jobType)
                scheduledJobDao.updateJobRunTimestamps(
                    job.id,
                    now,
                    now + intervalMs
                )
            }
        }
    }

    private suspend fun executeJob(jobId: String, jobName: String, jobType: String) {
        when (jobType) {
            "HEARTBEAT" -> runHeartbeatCheck()
            "MEMORY_CLEANUP" -> runMemoryCleanup()
            "CUSTOM" -> runCustomJob(jobName)
        }
    }

    private suspend fun runHeartbeatCheck() {
        // Check for pending tasks, process scheduled items
        val enabledJobs = scheduledJobDao.getEnabledJobs()
        val facts = memorySystem.getFactCount()

        // Log heartbeat activity
        val conversationId = "heartbeat_${System.currentTimeMillis()}"
        val heartbeatMsg = de.nexus.agent.core.data.db.MessageEntity(
            id = java.util.UUID.randomUUID().toString(),
            role = MessageRole.SYSTEM.name,
            content = "Heartbeat: ${enabledJobs.size} jobs aktiv, $facts Erinnerungen gespeichert",
            timestamp = System.currentTimeMillis(),
            conversationId = conversationId
        )
        // Heartbeat doesn't need to store messages - it's internal
    }

    private suspend fun runMemoryCleanup() {
        val facts = memorySystem.getFactCount()
        if (facts > 500) {
            // Remove oldest, least relevant facts
            val allFacts = memorySystem.getAllFacts()
            val sorted = allFacts.sortedBy { it.relevance * it.timestamp }

            val toRemove = sorted.take(facts - 500)
            toRemove.forEach { fact ->
                memorySystem.forget(fact.id)
            }
        }
    }

    private suspend fun runCustomJob(jobName: String) {
        // Execute custom agent job from WorkManager context
    }

    fun scheduleHeartbeat(intervalMinutes: Long = Constants.DEFAULT_HEARTBEAT_INTERVAL_MINUTES) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequest.Builder(
            HeartbeatWorker::class.java,
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.MINUTES
            )
            .addTag("nexus_heartbeat")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "nexus_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun scheduleOneTimeJob(
        name: String,
        delayMinutes: Long = 0,
        inputData: androidx.work.Data = androidx.work.Data.EMPTY
    ) {
        val workRequest = OneTimeWorkRequest.Builder(HeartbeatWorker::class.java)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(inputData)
            .addTag(name)
            .build()

        workManager.enqueueUniqueWork(
            "nexus_$name",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelJob(name: String) {
        workManager.cancelUniqueWork("nexus_$name")
    }

    fun cancelAllJobs() {
        workManager.cancelAllWorkByTag("nexus_heartbeat")
    }

    suspend fun processAgentRequest(
        conversationId: String,
        userMessage: String,
        provider: LlmProvider
    ): Result<String> {
        return try {
            val userMsg = ChatMessage(
                role = MessageRole.USER,
                content = userMessage
            )

            messageDao.insertMessage(
                de.nexus.agent.core.data.db.MessageEntity(
                    id = userMsg.id,
                    role = userMsg.role.name,
                    content = userMsg.content,
                    timestamp = userMsg.timestamp,
                    conversationId = conversationId
                )
            )

            val recentMessages = messageDao.getRecentMessages(conversationId, Constants.MAX_CONTEXT_MESSAGES)
                .map { entity ->
                    ChatMessage(
                        id = entity.id,
                        role = MessageRole.valueOf(entity.role),
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                }

            if (!provider.isConfigured) {
                return Result.failure(Exception("Provider nicht konfiguriert"))
            }

            val llmProvider = providerFactory.create(provider)
            val agentLoop = AgentLoop(
                provider = llmProvider,
                toolRegistry = toolRegistry,
                maxIterations = 10
            )

            val responseBuilder = StringBuilder()
            var finalState: AgentState = AgentState.Idle

            agentLoop.run(recentMessages).collect { state ->
                when (state) {
                    is AgentState.Streaming -> responseBuilder.clear().append(state.partialText)
                    is AgentState.Complete -> finalState = state
                    is AgentState.Error -> throw Exception(state.message)
                    else -> {}
                }
            }

            val assistantMessage = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = responseBuilder.toString()
            )

            messageDao.insertMessage(
                de.nexus.agent.core.data.db.MessageEntity(
                    id = assistantMessage.id,
                    role = assistantMessage.role.name,
                    content = assistantMessage.content,
                    timestamp = assistantMessage.timestamp,
                    conversationId = conversationId
                )
            )

            Result.success(responseBuilder.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            // Perform heartbeat logic
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
