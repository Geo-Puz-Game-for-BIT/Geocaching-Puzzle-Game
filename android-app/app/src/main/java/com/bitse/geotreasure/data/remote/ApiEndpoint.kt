package com.bitse.geotreasure.data.remote

internal fun normalizedApiBaseUrl(rawValue: String): String {
    val value = rawValue.trim().trimEnd('/')
    return when {
        value.isBlank() -> ""
        value.endsWith("/api/v1") -> value
        else -> "$value/api/v1"
    }
}
