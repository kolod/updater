package io.github.kolod.updater.core.verify

import java.security.MessageDigest

object Sha256 {
    fun hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** True when [actualHex] equals every value in [expected]; an empty list means nothing was published. */
    fun allMatch(actualHex: String, expected: List<String>): Boolean =
        expected.all { it.equals(actualHex, ignoreCase = true) }
}
