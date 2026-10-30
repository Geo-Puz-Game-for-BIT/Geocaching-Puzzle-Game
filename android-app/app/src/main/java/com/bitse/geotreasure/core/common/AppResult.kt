package com.bitse.geotreasure.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val cause: Throwable? = null,
)
