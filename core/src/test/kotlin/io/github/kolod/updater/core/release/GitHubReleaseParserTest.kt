package io.github.kolod.updater.core.release

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.version.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    private fun fixture(name: String): String =
        javaClass.getResource("/fixtures/$name")!!.readText()

    private fun releases(name: String, appId: String): List<Release> =
        (parseReleases(fixture(name), Catalog.byId(appId)) as ParseResult.Ok).releases

    @Test
    fun parsesPlainTagsWithDigest() {
        val list = releases("circuitopedia-releases.json", "circuitopedia")
        assertEquals(listOf("v0.2.0", "v0.1.0"), list.map { it.tag })
        val apk = list.first().apk!!
        assertEquals("circuitopedia.apk", apk.name)
        assertEquals(1988722L, apk.size)
        assertEquals("5d0186f1aea5c8d45a8f7631230cb572dc35e9113101c35dd83aacbb77de9659", apk.sha256)
        assertEquals(SemVer.parse("0.2.0"), list.first().version)
        assertNull(list.first().checksumAsset)
    }

    @Test
    fun monorepoReleasesOfOtherPlatformsAreIgnored() {
        val list = releases("hentai-reader-releases.json", "hentaireader")
        assertEquals(listOf("android-v0.1.0"), list.map { it.tag })
        val release = list.single()
        assertEquals("hentai-reader.apk", release.apk!!.name)
        assertEquals("hentai-reader.apk.sha256", release.checksumAsset!!.name)
    }

    @Test
    fun plainPrefixDoesNotMatchPlatformPrefixedTags() {
        // A "v" prefix must not accept "windows-v0.4.0" or "android-v0.1.0".
        val app = Catalog.byId("circuitopedia")
        val json = """[{"tag_name":"android-v0.1.0","draft":false,"prerelease":false,"assets":[]}]"""
        assertEquals(ParseResult.Ok(emptyList()), parseReleases(json, app))
    }

    @Test
    fun draftsAndPreReleasesAreIgnored() {
        val list = releases("prerelease-and-draft.json", "circuitopedia")
        assertEquals(listOf("v0.2.0"), list.map { it.tag })
    }

    @Test
    fun releaseWithoutTheApkAssetIsKeptWithoutApk() {
        val list = releases("no-apk-asset.json", "circuitopedia")
        assertEquals(listOf("v0.3.0", "v0.2.0"), list.map { it.tag })
        assertNull(list.first().apk)
        assertNotNull(list.last().apk)
        assertNull(list.last().apk!!.sha256)
    }

    @Test
    fun tagsThatAreNotSemanticVersionsAreIgnored() {
        val app = Catalog.byId("circuitopedia")
        val json = """[{"tag_name":"vnext","draft":false,"prerelease":false,"assets":[]},
            {"tag_name":"v1.2","draft":false,"prerelease":false,"assets":[]}]"""
        assertEquals(ParseResult.Ok(emptyList()), parseReleases(json, app))
    }

    @Test
    fun assetsOutsideTheRepositoryOrOnForeignHostsAreIgnored() {
        val app = Catalog.byId("circuitopedia")
        val json = """[{"tag_name":"v1.0.0","draft":false,"prerelease":false,"assets":[
            {"name":"circuitopedia.apk","size":1,"browser_download_url":"https://evil.net/circuitopedia.apk"}]},
            {"tag_name":"v0.9.0","draft":false,"prerelease":false,"assets":[
            {"name":"circuitopedia.apk","size":1,
             "browser_download_url":"https://github.com/someone-else/circuitopedia/releases/download/v0.9.0/circuitopedia.apk"}]}]"""
        val list = (parseReleases(json, app) as ParseResult.Ok).releases
        assertEquals(2, list.size)
        assertTrue(list.all { it.apk == null })
    }

    @Test
    fun onlySha256DigestsWithValidHexAreAccepted() {
        val app = Catalog.byId("circuitopedia")
        fun apkOf(digest: String): ReleaseAsset? {
            val json = """[{"tag_name":"v1.0.0","draft":false,"prerelease":false,"assets":[
                {"name":"circuitopedia.apk","size":1,${digest}
                 "browser_download_url":"https://github.com/kolod/circuitopedia/releases/download/v1.0.0/circuitopedia.apk"}]}]"""
            return (parseReleases(json, app) as ParseResult.Ok).releases.single().apk
        }
        assertNull(apkOf("\"digest\":\"sha512:abcd\",")!!.sha256)
        assertNull(apkOf("\"digest\":\"sha256:xyz\",")!!.sha256)
        assertNull(apkOf("")!!.sha256)
        assertEquals("a".repeat(64), apkOf("\"digest\":\"sha256:${"A".repeat(64)}\",")!!.sha256)
    }

    @Test
    fun entriesWithoutATagAreSkipped() {
        val app = Catalog.byId("circuitopedia")
        assertEquals(ParseResult.Ok(emptyList()), parseReleases("""[{"draft":false},"text",42,null]""", app))
    }

    @Test
    fun emptyListIsOkButEmpty() {
        assertEquals(ParseResult.Ok(emptyList()), parseReleases(fixture("empty.json"), Catalog.byId("circuitopedia")))
    }

    @Test
    fun malformedJsonIsABadResponse() {
        val app = Catalog.byId("circuitopedia")
        assertEquals(ParseResult.BadResponse, parseReleases(fixture("malformed.json"), app))
        assertEquals(ParseResult.BadResponse, parseReleases("", app))
        assertEquals(ParseResult.BadResponse, parseReleases("<html>", app))
    }
}
