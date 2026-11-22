package io.github.kolod.updater.data

import io.github.kolod.updater.core.net.NetworkPolicy
import io.github.kolod.updater.core.net.RedirectDecision
import java.net.HttpURLConnection
import java.net.URL

/** A URL was refused by the network policy (not an I/O problem, so not reported as "offline"). */
class RefusedUrlException(message: String) : RuntimeException(message)

internal object Http {
    const val USER_AGENT = "Updater"

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    /**
     * Opens [startUrl] with only the headers allowed by contracts/network-policy.md and follows
     * redirects manually, re-checking every hop against [NetworkPolicy]. The returned connection
     * has its response code available; the caller must disconnect it.
     */
    fun open(startUrl: String, accept: String, readTimeoutMs: Int, extraHeaders: Map<String, String> = emptyMap()): HttpURLConnection {
        var url = startUrl
        var hops = 0
        while (true) {
            if (!NetworkPolicy.isAllowed(url)) throw RefusedUrlException("URL refused by network policy")
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", USER_AGENT)
            extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            if (code !in REDIRECT_CODES) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location == null) throw RefusedUrlException("Redirect without location")
            url = when (val decision = NetworkPolicy.nextRedirect(url, location, hops)) {
                is RedirectDecision.Follow -> decision.url
                RedirectDecision.Refused -> throw RefusedUrlException("Redirect refused by network policy")
            }
            hops++
        }
    }

    fun headersOf(connection: HttpURLConnection): Map<String, String> =
        connection.headerFields
            .filterKeys { it != null }
            .mapValues { it.value.firstOrNull().orEmpty() }
}
