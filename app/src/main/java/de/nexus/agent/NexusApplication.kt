package de.nexus.agent

import android.app.Application
import de.nexus.agent.core.ServiceLocator
import timber.log.Timber

class NexusApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("NexusApplication initialized")

        // Initialize ServiceLocator with application context
        ServiceLocator.initialize(this)

        Timber.d("ServiceLocator and Room database initialized")
    }
}
