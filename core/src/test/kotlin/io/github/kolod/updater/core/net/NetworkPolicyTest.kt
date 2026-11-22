package io.github.kolod.updater.core.net

import io.github.kolod.updater.core.catalog.Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {
    private val circuitopedia = Catalog.byId("circuitopedia")

    @Test
    fun allowsTheGitHubHostsOverHttps() {
        listOf(
            "https://api.github.com/repos/kolod/circuitopedia/releases?per_page=30",
            "https://github.com/kolod/circuitopedia/releases/download/v0.2.0/circuitopedia.apk",
            "https://objects.githubusercontent.com/github-production-release-asset/1/2?sp=r",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sp=r",
            "https://API.GITHUB.COM/x",
            "https://api.github.com:443/x",
        ).forEach { assertTrue(it, NetworkPolicy.isAllowed(it)) }
    }

    @Test
    fun refusesEverythingElse() {
        listOf(
            "http://api.github.com/x",
            "ftp://github.com/x",
            "https://example.com/x",
            "https://github.com.evil.net/x",
            "https://evilgithub.com/x",
            "https://notgithub.com/x",
            "https://raw.githubusercontent.com/x",
            "https://github.com@evil.net/x",
            "https://user:pw@github.com/x",
            "https://github.com:8443/x",
            "https://evil.net\\@github.com/x",
            "not a url",
            "",
        ).forEach { assertFalse(it, NetworkPolicy.isAllowed(it)) }
    }

    @Test
    fun assetUrlMustBelongToTheCatalogRepository() {
        val good = "https://github.com/kolod/circuitopedia/releases/download/v0.2.0/circuitopedia.apk"
        assertTrue(NetworkPolicy.isAllowed(good, circuitopedia))
        assertFalse(NetworkPolicy.isAllowed(good.replace("circuitopedia/", "whiskergrid/"), circuitopedia))
        assertFalse(NetworkPolicy.isAllowed("https://github.com/kolod/circuitopedia/archive/main.zip", circuitopedia))
        assertFalse(
            NetworkPolicy.isAllowed(
                "https://github.com/kolod/circuitopedia/releases/download/../../../evil/x.apk",
                circuitopedia,
            ),
        )
        // Redirect targets live on other GitHub hosts and are checked without an app.
        assertFalse(
            NetworkPolicy.isAllowed("https://release-assets.githubusercontent.com/a/b", circuitopedia),
        )
    }

    @Test
    fun followsAllowedRedirects() {
        val current = "https://github.com/kolod/circuitopedia/releases/download/v0.2.0/circuitopedia.apk"
        val target = "https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sp=r"
        assertEquals(RedirectDecision.Follow(target), NetworkPolicy.nextRedirect(current, target, 0))
    }

    @Test
    fun resolvesRelativeRedirects() {
        assertEquals(
            RedirectDecision.Follow("https://github.com/other/path"),
            NetworkPolicy.nextRedirect("https://github.com/a/b", "/other/path", 1),
        )
    }

    @Test
    fun refusesRedirectsToForeignHostsOrDowngradedSchemes() {
        val current = "https://github.com/a/b"
        assertEquals(RedirectDecision.Refused, NetworkPolicy.nextRedirect(current, "https://evil.net/x", 0))
        assertEquals(RedirectDecision.Refused, NetworkPolicy.nextRedirect(current, "http://github.com/x", 0))
        assertEquals(RedirectDecision.Refused, NetworkPolicy.nextRedirect(current, "https://github.com.evil.net/x", 0))
    }

    @Test
    fun refusesMoreThanFiveHops() {
        val current = "https://github.com/a/b"
        assertTrue(NetworkPolicy.nextRedirect(current, "https://github.com/c", 4) is RedirectDecision.Follow)
        assertEquals(RedirectDecision.Refused, NetworkPolicy.nextRedirect(current, "https://github.com/c", 5))
    }
}
