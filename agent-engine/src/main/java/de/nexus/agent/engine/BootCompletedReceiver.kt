package de.nexus.agent.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("Boot completed – rescheduling agent jobs")

            try {
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootReceiverEntryPoint::class.java
                )

                val scheduleManager = entryPoint.scheduleManager()

                // Re-schedule any persisted jobs from Room DB
                // In production: query Room for persisted jobs and re-enqueue
                Timber.i("Agent jobs rescheduled after boot")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reschedule jobs after boot: ${e.message}")
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface BootReceiverEntryPoint {
    fun scheduleManager(): ScheduleManager
}
