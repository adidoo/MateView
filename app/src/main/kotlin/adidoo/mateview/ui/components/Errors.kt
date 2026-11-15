// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import adidoo.mateview.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Stored in a screen's state instead of a message when the server simply cannot be reached. */
const val ERROR_OFFLINE = "\u0000offline"

/** What a ViewModel keeps about a failure: [ERROR_OFFLINE], or the message as it came. */
fun errorText(error: IOException): String {
    val causes = generateSequence<Throwable>(error) { it.cause }.take(5)
    val unreachable = causes.any {
        it is UnknownHostException || it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException
    }
    return if (unreachable) ERROR_OFFLINE else error.message ?: error.javaClass.simpleName
}

/** The text to show for an error kept with [errorText]. */
@Composable
fun errorMessage(error: String): String = if (error == ERROR_OFFLINE) stringResource(R.string.error_offline) else error
