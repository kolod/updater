package io.github.kolod.updater.core.catalog

import io.github.kolod.updater.core.net.NetworkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private val packagePattern = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")

    @Test
    fun containsTheFiveExpectedApps() {
        assertEquals(
            listOf("ghostbms", "whiskergrid", "hentaireader", "circuitopedia", "updater"),
            Catalog.apps.map { it.id },
        )
    }

    @Test
    fun entriesMatchTheContract() {
        val g = Catalog.byId("ghostbms")
        assertEquals("io.github.kolod.ghostbms", g.packageName)
        assertEquals("kolod/ghost-bms-unofficial", "${g.repoOwner}/${g.repoName}")
        assertEquals("v", g.tagPrefix)
        assertEquals("ghost-bms-unofficial.apk", g.apkAssetName)

        val w = Catalog.byId("whiskergrid")
        assertEquals("io.github.kolod.whiskergrid", w.packageName)
        assertEquals("whisker-grid.apk", w.apkAssetName)

        val h = Catalog.byId("hentaireader")
        assertEquals("io.github.kolod.hentaireader", h.packageName)
        assertEquals("android-v", h.tagPrefix)
        assertEquals("hentai-reader.apk", h.apkAssetName)
        assertEquals("hentai-reader.apk.sha256", h.checksumAssetName)

        val c = Catalog.byId("circuitopedia")
        assertEquals("io.github.kolod.circuitopedia", c.packageName)
        assertEquals("circuitopedia.apk", c.apkAssetName)
        assertNull(c.checksumAssetName)

        val u = Catalog.byId("updater")
        assertEquals("io.github.kolod.updater", u.packageName)
        assertEquals("updater.apk", u.apkAssetName)
        assertEquals("updater.apk.sha256", u.checksumAssetName)
    }

    @Test
    fun idsAndPackageNamesAreUniqueAndValid() {
        assertEquals(Catalog.apps.size, Catalog.apps.map { it.id }.toSet().size)
        assertEquals(Catalog.apps.size, Catalog.apps.map { it.packageName }.toSet().size)
        Catalog.apps.forEach {
            assertTrue(it.packageName, packagePattern.matches(it.packageName))
            assertTrue(it.tagPrefix.isNotEmpty())
        }
    }

    @Test
    fun exactlyOneSelfEntryAndItIsLast() {
        assertEquals(1, Catalog.apps.count { it.isSelf })
        assertTrue(Catalog.apps.last().isSelf)
        assertEquals("updater", Catalog.self.id)
    }

    @Test
    fun everyRepositoryIsReachableUnderTheNetworkPolicy() {
        Catalog.apps.forEach {
            val url = "https://github.com/${it.repoOwner}/${it.repoName}/releases/download/v1.0.0/${it.apkAssetName}"
            assertTrue(url, NetworkPolicy.isAllowed(url, it))
            assertTrue(NetworkPolicy.isAllowed("https://api.github.com/repos/${it.repoOwner}/${it.repoName}/releases"))
        }
    }
}
