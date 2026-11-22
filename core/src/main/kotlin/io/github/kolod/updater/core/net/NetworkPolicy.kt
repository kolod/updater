package io.github.kolod.updater.core.net

import io.github.kolod.updater.core.catalog.ManagedApp
import java.net.URI
import java.net.URISyntaxException

sealed interface RedirectDecision {
    data class Follow(val url: String) : RedirectDecision
    data object Refused : RedirectDecision
}

/**
 * The single place that decides which URLs the app may contact (constitution, Principles I and II).
 * See specs/001-check-and-install-updates/contracts/network-policy.md.
 */
object NetworkPolicy {
    /** Extending this list requires a constitution amendment. */
    val ALLOWED_HOSTS: Set<String> = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    const val MAX_REDIRECTS = 5

    /**
     * True when [url] is HTTPS, on an allowed host, on the default port and without user-info.
     * With [app], additionally requires the URL to be a release download of that app's repository.
     */
    fun isAllowed(url: String, app: ManagedApp? = null): Boolean {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        if (uri.rawUserInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        if (host !in ALLOWED_HOSTS) return false
        if (app != null) {
            val path = uri.path ?: return false
            if (host != "github.com") return false
            if (path.split('/').any { it == ".." }) return false
            val prefix = "/${app.repoOwner}/${app.repoName}/releases/download/"
            if (!path.startsWith(prefix, ignoreCase = true)) return false
        }
        return true
    }

    /**
     * Decides whether to follow a redirect. [hops] is the number of redirects already followed for
     * this request. [location] may be relative to [current].
     */
    fun nextRedirect(current: String, location: String, hops: Int): RedirectDecision {
        if (hops >= MAX_REDIRECTS) return RedirectDecision.Refused
        val resolved = try {
            URI(current).resolve(location).toString()
        } catch (_: URISyntaxException) {
            return RedirectDecision.Refused
        } catch (_: IllegalArgumentException) {
            return RedirectDecision.Refused
        }
        return if (isAllowed(resolved)) RedirectDecision.Follow(resolved) else RedirectDecision.Refused
    }
}
