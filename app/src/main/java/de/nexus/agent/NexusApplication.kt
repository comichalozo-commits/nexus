package de.nexus.agent

import android.app.Application
import de.nexus.agent.core.ServiceLocator

class NexusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
