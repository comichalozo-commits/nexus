package de.nexus.agent.engine

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.nexus.agent.core.common.Constants
import de.nexus.agent.core.data.db.ScheduledJobDao
import de.nexus.agent.core.data.db.ScheduledJobEntity
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleManager @Inject constructor(
    private val workManager: WorkManager,
    private val scheduledJobDao: ScheduledJobDao
) {
    suspend fun createJob(
        name: String,
        description: String = "",
        jobType: String = "HEARTBEAT",
        intervalMinutes: Long = Constants.DEFAULT_HEARTBEAT_INTERVAL_MINUTES,
        enabled: Boolean = true
    ): String {
        val jobId = java.util.UUID.randomUUID().toString()

        val entity = ScheduledJobEntity(
            id = jobId,
            name = name,
            description = description,
            jobType = jobType,
            intervalMinutes = intervalMinutes,
            isEnabled = enabled,
            nextRunTimestamp = System.currentTimeMillis() + (intervalMinutes * 60 * 1000)
        )

        scheduledJobDao.insertJob(entity)

        if (enabled) {
            scheduleWork(jobId, name, intervalMinutes)
        }

        return jobId
    }

    suspend fun enableJob(jobId: String) {
        val job = scheduledJobDao.getJobById(jobId) ?: return
        scheduledJobDao.setJobEnabled(jobId, true)
        scheduleWork(jobId, job.name, job.intervalMinutes)
    }

    suspend fun disableJob(jobId: String) {
        scheduledJobDao.setJobEnabled(jobId, false)
        workManager.cancelUniqueWork("nexus_scheduled_$jobId")
    }

    suspend fun deleteJob(jobId: String) {
        val job = scheduledJobDao.getJobById(jobId) ?: return
        workManager.cancelUniqueWork("nexus_scheduled_$jobId")
        scheduledJobDao.deleteJob(job)
    }

    suspend fun updateJobInterval(jobId: String, intervalMinutes: Long) {
        val job = scheduledJobDao.getJobById(jobId) ?: return
        scheduledJobDao.updateJob(job.copy(intervalMinutes = intervalMinutes))
        if (job.isEnabled) {
            scheduleWork(jobId, job.name, intervalMinutes)
        }
    }

    suspend fun getActiveJobCount(): Int {
        val jobs = scheduledJobDao.getEnabledJobs()
        return jobs.size
    }

    private fun scheduleWork(jobId: String, name: String, intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequest.Builder(
            ScheduledJobWorker::class.java,
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString("job_id", jobId)
                    .putString("job_name", name)
                    .build()
            )
            .addTag("nexus_scheduled")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "nexus_scheduled_$jobId",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun scheduleOneTimeWork(
        name: String,
        delayMinutes: Long,
        inputData: Data
    ): String {
        val requestId = java.util.UUID.randomUUID().toString()
        val workRequest = OneTimeWorkRequest.Builder(ScheduledJobWorker::class.java)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(inputData)
            .addTag("nexus_onetime")
            .build()

        workManager.enqueueUniqueWork(
            "nexus_onetime_$requestId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        return requestId
    }

    fun cancelAllScheduledWork() {
        workManager.cancelAllWorkByTag("nexus_scheduled")
    }
}

class ScheduledJobWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString("job_id") ?: return Result.failure()
        val jobName = inputData.getString("job_name") ?: "Unknown"

        return try {
            // Execute scheduled job logic
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
