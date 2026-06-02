package de.nexus.agent.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Boot completed - reschedule agent jobs
            // Note: Without Hilt, use ServiceLocator to access schedule manager
        }
    }
}
