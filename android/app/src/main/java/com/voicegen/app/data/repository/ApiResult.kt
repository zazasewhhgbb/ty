package com.voicegen.app.data.repository

/** Wraps API results so ViewModels get human-readable errors, never raw stack traces (spec 37). */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

fun humanizeError(throwable: Throwable): String = when (throwable) {
    is java.net.UnknownHostException -> "Can't reach the server. Check the backend URL in Settings and your internet connection."
    is java.net.SocketTimeoutException -> "The server took too long to respond. It may be busy or unreachable."
    is java.net.ConnectException -> "Couldn't connect to the backend. Make sure it's running and the URL in Settings is correct."
    else -> throwable.message ?: "Something went wrong. Please try again."
}
