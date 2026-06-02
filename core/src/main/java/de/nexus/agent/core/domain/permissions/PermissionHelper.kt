package de.nexus.agent.core.domain.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {
    val REQUIRED_PERMISSIONS = buildList {
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.FOREGROUND_SERVICE)
        add(Manifest.permission.WAKE_LOCK)
        add(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getMissingPermissions(context: Context): List<String> {
        val allPermissions = REQUIRED_PERMISSIONS + SMS_PERMISSIONS
        return allPermissions.filter { !hasPermission(context, it) }
    }

    fun hasAllPermissions(context: Context): Boolean {
        val allPermissions = REQUIRED_PERMISSIONS + SMS_PERMISSIONS
        return allPermissions.all { hasPermission(context, it) }
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedServiceName = "${context.packageName}/${serviceClass.name}"
        return enabledServices.contains(expectedServiceName) ||
            enabledServices.contains(context.packageName)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }
}
