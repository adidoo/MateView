// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A request to the Grafana HTTP API; [path] starts with a slash and is relative to Grafana's root. */
data class ApiRequest(val method: String, val path: String, val jsonBody: String? = null)

data class ApiResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** The path the requests take to reach Grafana. The rest of the app only sees this interface. */
interface Transport {
    suspend fun execute(request: ApiRequest): ApiResponse
}

/** Where a connection attempt failed, so the settings screen can point at the right step. */
enum class Stage { REACH_HA, AUTH_HA, FIND_PANEL, OPEN_SESSION, GRAFANA, DATASOURCE, DATABASE }

/**
 * The failures the app can explain in the user's language. [TransportException.message]
 * stays in English for logs; the interface uses the problem and its detail when present.
 */
enum class Problem {
    NOT_HOME_ASSISTANT, TOKEN_REJECTED, PANEL_NOT_FOUND, NO_SESSION, NO_ANSWER,
    PROXY_REFUSED, NO_GRAFANA, DATASOURCES_REFUSED, NO_POSTGRES, QUERIES_REFUSED,
}

class TransportException(
    val stage: Stage,
    message: String,
    cause: Throwable? = null,
    val problem: Problem? = null,
    /** What completes the explanation of [problem]: an HTTP status, a list of names... */
    val detail: String = "",
) : IOException(message, cause)

/** Runs the call without blocking a thread and reads the whole body. */
internal suspend fun Call.await(): ApiResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use { ApiResponse(it.code, it.body.string()) }
                continuation.resume(result)
            } catch (e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        }
    })
}
