// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns what the user typed into a usable address, or explains why it cannot be used. */
object Addresses {

    sealed interface Result {
        data class Ok(val url: HttpUrl) : Result
        data object Empty : Result
        data object Invalid : Result

        /** Clear-text HTTP is only accepted on the local network. */
        data object InsecureRemote : Result
    }

    fun parse(input: String): Result {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty()) return Result.Empty
        val withScheme = when {
            text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) -> text
            isLocalHost(text.substringBefore('/').substringBefore(':')) -> "http://$text"
            else -> "https://$text"
        }
        val url = withScheme.toHttpUrlOrNull() ?: return Result.Invalid
        if (!url.isHttps && !isLocalHost(url.host)) return Result.InsecureRemote
        return Result.Ok(url)
    }

    /** Private IPv4 ranges, loopback, mDNS names and bare host names. */
    fun isLocalHost(host: String): Boolean {
        val name = host.lowercase()
        if (name == "localhost" || name.endsWith(".local") || name.endsWith(".lan") || name.endsWith(".home")) return true
        if ('.' !in name && ':' !in name) return true
        val parts = name.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || parts[0] == 127 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 100 && parts[1] in 64..127) // carrier-grade NAT range, used by mesh VPNs
    }
}
