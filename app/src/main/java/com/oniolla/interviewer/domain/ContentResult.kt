package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.toApiException
import kotlinx.coroutines.CancellationException

sealed interface ContentResult<out T> {
    data class Success<T>(val content: T) : ContentResult<T>
    data class Error(val message: String) : ContentResult<Nothing>
    data object SessionExpired : ContentResult<Nothing>
}

internal suspend fun <T> loadContent(block: suspend () -> T): ContentResult<T> = try {
    ContentResult.Success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    when (val apiError = error.toApiException()) {
        is ApiException.Unauthorized -> ContentResult.SessionExpired
        is ApiException.NetworkError -> ContentResult.Error("Something unexpected happened")
        else -> ContentResult.Error(apiError.message ?: "Unknown error")
    }
}
