package de.nexus.agent

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.nexus.agent.engine.HeartbeatScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NexusApplication : Application() {

    @Inject
    lateinit var workManager: WorkManager

    override fun onCreate() {
        super.onCreate()

        initLogging()
        initCrashReporting()
        initWorkManager()

        Timber.i("NexusApplication initialized")
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber debug logging enabled")
        } else {
            Timber.plant(ReleaseCrashReportingTree())
        }
    }

    private fun initCrashReporting() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { throwable, exception ->
            Timber.e(exception, "Uncaught exception: ${exception.message}")
            defaultHandler?.uncaughtException(throwable, exception)
        }
    }

    private fun initWorkManager() {
        val heartbeatConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatScheduler>(
            repeatInterval = 30,
            TimeUnit.MINUTES,
            flexTimeInterval = 5,
            TimeUnit.MINUTES
        )
            .setConstraints(heartbeatConstraints)
            .addTag("heartbeat")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "nexus_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            heartbeatRequest
        )

        Timber.i("WorkManager: Heartbeat worker scheduled (every 30 min)")
    }

    private class ReleaseCrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.ERROR || priority == Log.WARN) {
                // In a production app, this would send to Crashlytics / Sentry etc.
                // For now we only log errors and warnings to logcat in release mode.
            }
        }
    }
}
