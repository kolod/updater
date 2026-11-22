package io.github.kolod.updater.core.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumFileTest {
    private val hex = "20796066420f296e09d4ad39063cd58a62e2ffc6077d9324df6c93bb6e7e0ba3"

    @Test
    fun parsesTheFormatPublishedByHentaiReader() {
        assertEquals(hex, ChecksumFile.parse("$hex  hentai-reader.apk\n", "hentai-reader.apk"))
    }

    @Test
    fun parsesBinaryModeMarkerBareHexAndUpperCase() {
        assertEquals(hex, ChecksumFile.parse("$hex *hentai-reader.apk", "hentai-reader.apk"))
        assertEquals(hex, ChecksumFile.parse("$hex\n", "hentai-reader.apk"))
        assertEquals(hex, ChecksumFile.parse(hex.uppercase() + "  hentai-reader.apk", "hentai-reader.apk"))
    }

    @Test
    fun picksTheLineOfTheRequestedFile() {
        val other = "a".repeat(64)
        val text = "$other  other.apk\n$hex  hentai-reader.apk\n"
        assertEquals(hex, ChecksumFile.parse(text, "hentai-reader.apk"))
    }

    @Test
    fun returnsNullForOtherFilesAndGarbage() {
        assertNull(ChecksumFile.parse("$hex  other.apk", "hentai-reader.apk"))
        assertNull(ChecksumFile.parse("", "x.apk"))
        assertNull(ChecksumFile.parse("not a checksum", "x.apk"))
        assertNull(ChecksumFile.parse("abc123  x.apk", "x.apk"))
        assertNull(ChecksumFile.parse("<html>" + "a".repeat(64), "x.apk"))
    }

    @Test
    fun sha256OfKnownInput() {
        // SHA-256("abc") from FIPS 180-2.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".toByteArray()),
        )
    }

    @Test
    fun comparisonIsCaseInsensitiveAndRequiresEveryPublishedValue() {
        assertTrue(Sha256.allMatch(hex, listOf(hex.uppercase())))
        assertTrue(Sha256.allMatch(hex, emptyList()))
        assertTrue(Sha256.allMatch(hex, listOf(hex, hex)))
        assertFalse(Sha256.allMatch(hex, listOf(hex, "b".repeat(64))))
        assertFalse(Sha256.allMatch(hex, listOf("b".repeat(64))))
    }
}
