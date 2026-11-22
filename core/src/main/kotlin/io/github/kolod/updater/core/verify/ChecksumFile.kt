package io.github.kolod.updater.core.verify

/** Reads `sha256sum`-style files: `<hex>  <name>`, `<hex> *<name>` or a bare `<hex>`. */
object ChecksumFile {
    private val LINE = Regex("^([0-9a-fA-F]{64})(?:\\s+\\*?(.+))?$")

    /** Returns the lower-case hex for [assetName], or null when the text has no matching line. */
    fun parse(text: String, assetName: String): String? {
        for (raw in text.lineSequence()) {
            val match = LINE.matchEntire(raw.trim()) ?: continue
            val name = match.groupValues[2].trim().removePrefix("./")
            if (name.isEmpty() || name == assetName) return match.groupValues[1].lowercase()
        }
        return null
    }
}
