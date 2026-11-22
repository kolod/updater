package io.github.kolod.updater.data

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Remembers what the last completed check found, so the list is not empty when the next launch is
 * offline. Holds only catalog ids, versions and times (data-model.md, LastCheckSnapshot).
 */
class LastCheckStore(private val file: File) {
    data class Entry(val latest: String?, val checkedAt: Long)

    /** Missing, corrupt or unknown-version files are treated as "no data". */
    fun load(): Map<String, Entry> = try {
        if (!file.isFile) {
            emptyMap()
        } else {
            val root = JSONObject(file.readText())
            val apps = root.optJSONObject("apps")
            if (root.optInt("version", 0) != VERSION || apps == null) {
                emptyMap()
            } else {
                buildMap {
                    for (id in apps.keys()) {
                        val entry = apps.optJSONObject(id) ?: continue
                        put(id, Entry(if (entry.isNull("latest")) null else entry.optString("latest"), entry.optLong("checkedAt", 0L)))
                    }
                }
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    /** Writes atomically (temp file, then rename) so a crash cannot leave a half-written file. */
    fun save(entries: Map<String, Entry>) {
        val apps = JSONObject()
        entries.forEach { (id, entry) ->
            apps.put(id, JSONObject().put("latest", entry.latest ?: JSONObject.NULL).put("checkedAt", entry.checkedAt))
        }
        val json = JSONObject().put("version", VERSION).put("apps", apps).toString()
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val VERSION = 1
    }
}
