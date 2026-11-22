package io.github.kolod.updater.core.release

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.version.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSelectorTest {
    private fun release(tag: String) = Release(tag, SemVer.parse(tag.removePrefix("v"))!!, null, null)

    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun picksTheHighestVersionRegardlessOfOrder() {
        val list = listOf(release("v0.9.0"), release("v1.10.0"), release("v1.2.0"), release("v1.0.0-rc.1"))
        assertEquals("v1.10.0", selectRelease(list)!!.tag)
    }

    @Test
    fun stableBeatsItsOwnPreReleaseVersion() {
        assertEquals("v1.0.0", selectRelease(listOf(release("v1.0.0-rc.1"), release("v1.0.0")))!!.tag)
    }

    @Test
    fun emptyListHasNoRelease() {
        assertNull(selectRelease(emptyList()))
    }

    @Test
    fun hentaiReaderPicksTheAndroidReleaseNotTheNewerWindowsOne() {
        val app = Catalog.byId("hentaireader")
        val list = (parseReleases(fixture("hentai-reader-releases.json"), app) as ParseResult.Ok).releases
        assertEquals("android-v0.1.0", selectRelease(list)!!.tag)
    }

    @Test
    fun allRecordedRepositoriesSelectTheirNewestRelease() {
        fun newest(file: String, id: String) =
            selectRelease((parseReleases(fixture(file), Catalog.byId(id)) as ParseResult.Ok).releases)!!.tag
        assertEquals("v0.2.0", newest("circuitopedia-releases.json", "circuitopedia"))
        assertEquals("v1.1.0", newest("whiskergrid-releases.json", "whiskergrid"))
        assertEquals("v1.0.4", newest("ghost-bms-releases.json", "ghostbms"))
    }
}
