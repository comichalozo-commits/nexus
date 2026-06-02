package de.nexus.agent.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("Heartbeat check started")

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                HeartbeatEntryPoint::class.java
            )

            val notificationCheck = checkNotifications()
            val calendarCheck = checkCalendar()
            val locationCheck = checkLocation()

            val hasNotifications = notificationCheck.isNotEmpty()
            val hasCalendarEvents = calendarCheck.isNotEmpty()
            val hasLocationRelevance = locationCheck

            if (hasNotifications || hasCalendarEvents) {
                val message = buildProactiveMessage(
                    notifications = notificationCheck,
                    calendarEvents = calendarCheck
                )
                Timber.i("Proactive notification: $message")
                // In production: show notification via NotificationHelper
            }

            Timber.i("Heartbeat check completed. Notifications: $hasNotifications, Calendar: $hasCalendarEvents, Location: $hasLocationRelevance")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Heartbeat check failed: ${e.message}")
            return Result.retry()
        }
    }

    private fun checkNotifications(): List<String> {
        // In production: query NotificationListenerService data
        // For now, return empty list
        return emptyList()
    }

    private fun checkCalendar(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()

        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("Calendar permission not granted")
            return events
        }

        val now = Calendar.getInstance()
        val later = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 2)
        }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(
            now.timeInMillis.toString(),
            later.timeInMillis.toString()
        )

        try {
            applicationContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val locationIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

                while (cursor.moveToNext()) {
                    val title = if (titleIndex >= 0) cursor.getString(titleIndex) else "Unbenannt"
                    val startTime = if (startIndex >= 0) cursor.getLong(startIndex) else 0L
                    val endTime = if (endIndex >= 0) cursor.getLong(endIndex) else 0L
                    val location = if (locationIndex >= 0) cursor.getString(locationIndex) else ""

                    events.add(
                        CalendarEvent(
                            title = title,
                            startTime = startTime,
                            endTime = endTime,
                            location = location
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query calendar: ${e.message}")
        }

        return events
    }

    private fun checkLocation(): Boolean {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        return isGpsEnabled || isNetworkEnabled
    }

    private fun buildProactiveMessage(
        notifications: List<String>,
        calendarEvents: List<CalendarEvent>
    ): String {
        val parts = mutableListOf<String>()

        if (calendarEvents.isNotEmpty()) {
            val next = calendarEvents.first()
            val minutesUntil = ((next.startTime - System.currentTimeMillis()) / 60000).toInt()
            parts.add("In ${minutesUntil} Min: ${next.title}")
        }

        if (notifications.isNotEmpty()) {
            parts.add("${notifications.size} neue Benachrichtigungen")
        }

        return parts.joinToString(" Â· ")
    }

    data class CalendarEvent(
        val title: String,
        val startTime: Long,
        val endTime: Long,
        val location: String
    )
}

@EntryPoint
(SingletonComponent::class)
interface HeartbeatEntryPoint {
    fun heartbeatWorker(): HeartbeatWorker
}
