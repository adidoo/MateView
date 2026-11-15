// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Talks to Grafana at its own address, with an optional service-account token. */
class DirectTransport(
    private val baseUrl: HttpUrl,
    private val token: String?,
    private val client: OkHttpClient,
) : Transport {

    override suspend fun execute(request: ApiRequest): ApiResponse {
        val builder = Request.Builder()
            .url(baseUrl.resolveUnder(request.path))
            .method(request.method, request.jsonBody?.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return try {
            client.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw TransportException(Stage.GRAFANA, e.message ?: e.javaClass.simpleName, e)
        }
    }
}

/** Appends [path] (which may carry a query string) below this URL, keeping any sub-path it already has. */
internal fun HttpUrl.resolveUnder(path: String): HttpUrl =
    (toString().trimEnd('/') + path).toHttpUrl()
