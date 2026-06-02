package de.nexus.agent.core.domain.tools

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tool(
    name = "schedule",
    description = "Create, list, delete, or run scheduled tasks. Supports cron-like expressions and WorkManager integration.",
    category = "system"
)
class ScheduleTool(
    private val context: Context
) : Tool {

    override val name: String = "schedule"
    override val description: String = "Create, list, delete, or run scheduled tasks with cron-like expressions."
    override val parameterSchema: JsonSchema = JsonSchema(
        properties = mapOf(
            "action" to PropertySchema("string", "Action to perform", null, enum = listOf("create", "list", "delete", "run")),
            "jobId" to PropertySchema("string", "Job ID (required for delete/run)", null),
            "name" to PropertySchema("string", "Human-readable job name (required for create)", null),
            "cronExpr" to PropertySchema("string", "Cron expression e.g. '0 9 * * 1-5' for weekdays at 9am", null),
            "prompt" to PropertySchema("string", "Prompt/instruction to execute when triggered", null),
            "intervalMinutes" to PropertySchema("integer", "Interval in minutes (alternative to cron)", null)
        ),
        required = listOf("action")
    )

    private val workManager get() = WorkManager.getInstance(context)
    private val jobStore: MutableMap<String, ScheduleJob> = mutableMapOf()

    override suspend fun execute(params: ToolExecutionParams): ToolResult = withContext(Dispatchers.IO) {
        val action = params.params["action"] as? String
            ?: return@withContext ToolResult.fail("Parameter 'action' is required")

        when (action) {
            "create" -> createJob(params)
            "list" -> listJobs(params)
            "delete" -> deleteJob(params)
            "run" -> runJob(params)
            else -> ToolResult.fail("Unknown action: $action")
        }
    }

    private suspend fun createJob(params: ToolExecutionParams): ToolResult {
        val name = params.params["name"] as? String
            ?: return ToolResult.fail("Parameter 'name' is required")
        val prompt = params.params["prompt"] as? String ?: ""
        val cronExpr = params.params["cronExpr"] as? String
        val intervalMinutes = (params.params["intervalMinutes"] as? Number)?.toLong()

        val jobId = UUID.randomUUID().toString()

        if (cronExpr != null) {
            val minutes = parseCronToIntervalMinutes(cronExpr)
                ?: return ToolResult.fail("Invalid cron expression: $cronExpr")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(minutes, TimeUnit.MINUTES)
                .setInputData(workDataOf(
                    "job_id" to jobId,
                    "job_name" to name,
                    "prompt" to prompt
                ))
                .setConstraints(constraints)
                .addTag("nexus_schedule")
                .addTag("job_$jobId")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "schedule_$jobId",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            ).await()

            val job = ScheduleJob(
                id = jobId,
                name = name,
                cronExpression = cronExpr,
                prompt = prompt,
                workName = "schedule_$jobId",
                isActive = true
            )
            jobStore[jobId] = job

            return ToolResult.ok(
                "Scheduled job created: '$name' with cron '$cronExpr'",
                mapOf("jobId" to jobId, "name" to name, "cron" to cronExpr)
            )
        } else if (intervalMinutes != null && intervalMinutes > 0) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf(
                    "job_id" to jobId,
                    "job_name" to name,
                    "prompt" to prompt
                ))
                .setConstraints(constraints)
                .addTag("nexus_schedule")
                .addTag("job_$jobId")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "schedule_$jobId",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            ).await()

            val job = ScheduleJob(
                id = jobId,
                name = name,
                intervalMinutes = intervalMinutes,
                prompt = prompt,
                workName = "schedule_$jobId",
                isActive = true
            )
            jobStore[jobId] = job

            return ToolResult.ok(
                "Scheduled job created: '${name}' (every $intervalMinutes min)",
                mapOf("jobId" to jobId, "name" to name, "intervalMinutes" to intervalMinutes)
            )
        } else {
            // One-time job
            val workRequest = OneTimeWorkRequestBuilder<ScheduleWorker>()
                .setInputData(workDataOf(
                    "job_id" to jobId,
                    "job_name" to name,
                    "prompt" to prompt
                ))
                .addTag("nexus_schedule")
                .addTag("job_$jobId")
                .build()

            workManager.enqueueUniqueWork(
                "schedule_$jobId",
                ExistingWorkPolicy.KEEP,
                workRequest
            ).await()

            val job = ScheduleJob(
                id = jobId,
                name = name,
                prompt = prompt,
                workName = "schedule_$jobId",
                isActive = true,
                isOneTime = true
            )
            jobStore[jobId] = job

            return ToolResult.ok(
                "One-time job created: '$name'",
                mapOf("jobId" to jobId, "name" to name)
            )
        }
    }

    private suspend fun listJobs(params: ToolExecutionParams): ToolResult {
        val jobs = workManager.getWorkInfosByTag("nexus_schedule").await()
        val details = jobs.map { info ->
            val tags = info.tags.filter { it.startsWith("job_") }.map { it.removePrefix("job_") }
            mapOf(
                "id" to (tags.firstOrNull() ?: info.id.toString()),
                "state" to info.state.name,
                "runAttemptCount" to info.runAttemptCount,
                "tags" to tags
            )
        }
        val formatted = if (details.isEmpty()) "No scheduled jobs." else
            details.joinToString("\n") { "• Run #${it["runAttemptCount"]}) - State: ${it["state"]}" }
        ToolResult.ok("Scheduled jobs:\n$formatted", mapOf("jobs" to details))
    }

    private suspend fun deleteJob(params: ToolExecutionParams): ToolResult {
        val jobId = params.params["jobId"] as? String
            ?: return ToolResult.fail("Parameter 'jobId' is required")
        val job = jobStore[jobId]
        if (job != null) {
            workManager.cancelUniqueWork(job.workName).await()
            jobStore.remove(jobId)
            ToolResult.ok("Deleted job: ${job.name}", mapOf("jobId" to jobId))
        } else {
            workManager.cancelAllWorkByTag("job_$jobId").await()
            ToolResult.ok("Attempted deletion of job: $jobId")
        }
    }

    private suspend fun runJob(params: ToolExecutionParams): ToolResult {
        val jobId = params.params["jobId"] as? String
            ?: return ToolResult.fail("Parameter 'jobId' is required")
        val job = jobStore[jobId] ?: return ToolResult.fail("Job not found: $jobId")

        val workRequest = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInputData(workDataOf(
                "job_id" to jobId,
                "job_name" to job.name,
                "prompt" to job.prompt
            ))
            .addTag("nexus_schedule_run")
            .build()

        workManager.enqueue(workRequest).await()
        ToolResult.ok("Triggered immediate run for: ${job.name}", mapOf("jobId" to jobId))
    }

    /**
     * Simple cron-to-interval parser.
     * Supports: "*/N * * * *" -> every N minutes, "0 H * * *" -> daily at H:00,
     * "0 H * * DOW" -> weekly, etc. Returns interval in minutes or null.
     */
    private fun parseCronToIntervalMinutes(cron: String): Long? {
        val parts = cron.trim().split(Regex("\\s+"))
        if (parts.size != 5) return null

        val minute = parts[0]
        val hour = parts[1]
        val dom = parts[2]
        val month = parts[3]
        val dow = parts[4]

        // Every N minutes: "*/N * * * *"
        if (minute.startsWith("*/")) {
            val n = minute.removePrefix("*/").toLongOrNull() ?: return null
            if (hour == "*" && dom == "*" && month == "*" && dow == "*") return n
        }

        // Daily at specific time: "0 H * * *"
        if (minute != "*" && hour != "*" && dom == "*" && month == "*" && dow == "*") {
            return 24 * 60L // Run daily
        }

        // Weekly: "0 H * * D"
        if (minute != "*" && hour != "*" && dom == "*" && month == "*" && dow != "*" && dow != "?") {
            return 7 * 24 * 60L // Run weekly
        }

        // Hourly: "0 * * * *"
        if (minute == "0" && hour == "*" && dom == "*" && month == "*" && dow == "*") {
            return 60L
        }

        // Every 30 min: "*/30 * * * *"
        if (minute == "*/30") return 30L

        // Default: daily
        return 24 * 60L
    }
}

data class ScheduleJob(
    val id: String,
    val name: String,
    val prompt: String,
    val cronExpression: String? = null,
    val intervalMinutes: Long? = null,
    val workName: String = "",
    val isActive: Boolean = true,
    val isOneTime: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
