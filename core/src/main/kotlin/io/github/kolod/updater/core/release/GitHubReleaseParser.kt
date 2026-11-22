package io.github.kolod.updater.core.release

import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.net.NetworkPolicy
import io.github.kolod.updater.core.version.SemVer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val SHA256_HEX = Regex("[0-9a-fA-F]{64}")

/**
 * Parses the GitHub "list releases" response (see contracts/github-releases.md) into the releases
 * that are eligible for [app]. Never throws: invalid input yields [ParseResult.BadResponse].
 */
fun parseReleases(json: String, app: ManagedApp): ParseResult {
    val array = try {
        JSONArray(json)
    } catch (_: JSONException) {
        return ParseResult.BadResponse
    }
    val releases = mutableListOf<Release>()
    for (i in 0 until array.length()) {
        val entry = array.optJSONObject(i) ?: continue
        if (entry.optBoolean("draft", false) || entry.optBoolean("prerelease", false)) continue
        val tag = entry.optString("tag_name", "")
        if (!tag.startsWith(app.tagPrefix)) continue
        val version = SemVer.parse(tag.removePrefix(app.tagPrefix)) ?: continue
        val assets = entry.optJSONArray("assets")?.let { parseAssets(it, app) }.orEmpty()
        releases += Release(
            tag = tag,
            version = version,
            apk = assets.firstOrNull { it.name == app.apkAssetName },
            checksumAsset = app.checksumAssetName?.let { name -> assets.firstOrNull { it.name == name } },
        )
    }
    return ParseResult.Ok(releases)
}

private fun parseAssets(array: JSONArray, app: ManagedApp): List<ReleaseAsset> {
    val result = mutableListOf<ReleaseAsset>()
    for (i in 0 until array.length()) {
        val asset: JSONObject = array.optJSONObject(i) ?: continue
        val name = asset.optString("name", "")
        val url = asset.optString("browser_download_url", "")
        if (name.isEmpty() || !NetworkPolicy.isAllowed(url, app)) continue
        result += ReleaseAsset(
            name = name,
            size = asset.optLong("size", 0L),
            downloadUrl = url,
            sha256 = parseDigest(asset.optString("digest", "")),
        )
    }
    return result
}

private fun parseDigest(digest: String): String? {
    if (!digest.startsWith("sha256:")) return null
    val hex = digest.removePrefix("sha256:")
    return if (SHA256_HEX.matches(hex)) hex.lowercase() else null
}
