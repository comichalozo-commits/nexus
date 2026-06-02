package de.nexus.agent.core.common

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

fun <T> Flow<T>.asResult(): Flow<Result<T>> = this
    .map<T, Result<T>> { Result.Success(it) }
    .onStart { emit(Result.Loading) }
    .catch { emit(Result.Error(it, it.message)) }

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun String.truncate(maxLength: Int): String {
    return if (length <= maxLength) this else take(maxLength - 3) + "..."
}

fun String.sanitizeForLog(): String {
    return this.replace(Regex("[\\r\\n]"), " ")
        .take(200)
}

fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

fun formatDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

suspend fun retryWithBackoff(
    times: Int = 3,
    initialDelayMs: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> Unit
) {
    var currentDelay = initialDelayMs
    repeat(times - 1) {
        try {
            block()
            return
        } catch (e: Exception) {
            kotlinx.coroutines.delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    block()
}
