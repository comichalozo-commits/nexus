package de.nexus.agent.core.common

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class ResultError(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is ResultError
    val isLoading get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is ResultError -> exception
        else -> null
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is ResultError -> this
        is Loading -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is ResultError -> this
        is Loading -> this
    }
}

suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.ResultError(e, e.message)
    }
}
