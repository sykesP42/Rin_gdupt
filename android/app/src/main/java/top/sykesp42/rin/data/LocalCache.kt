package top.sykesp42.rin.data

import android.content.Context
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * File cache for JWXT data (schedule weeks / term courses / grade bundle).
 * Stale-while-revalidate: the VM renders cached payloads instantly and
 * refreshes from the network in the background, so the app stays usable
 * offline and between session expiries. Payloads are stored with their
 * fetch timestamp; retention is capped by count.
 */
class LocalCache(private val context: Context) {

    @Serializable
    data class Entry(val at: String, val payload: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = java.io.File(context.filesDir, "jwxt_cache")

    private fun file(name: String) = java.io.File(dir.apply { mkdirs() }, "$name.json")

    fun readRaw(name: String): Entry? {
        val file = file(name)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(Entry.serializer(), file.readText())
        }.getOrNull()
    }

    fun writeRaw(name: String, payload: String) {
        runCatching {
            // Atomic swap so a killed process never leaves a truncated cache
            // entry behind (the reader would silently drop it).
            val target = file(name)
            val tmp = java.io.File(target.parentFile, target.name + ".tmp")
            tmp.writeText(
                json.encodeToString(
                    Entry.serializer(),
                    Entry(at = Instant.now().toString(), payload = payload)
                )
            )
            if (!tmp.renameTo(target)) {
                target.writeText(tmp.readText())
                tmp.delete()
            }
            trim()
        }
    }

    fun <T> read(name: String, serializer: kotlinx.serialization.KSerializer<T>): Pair<Instant, T>? {
        val entry = readRaw(name) ?: return null
        val payload = runCatching { json.decodeFromString(serializer, entry.payload) }.getOrNull()
            ?: return null
        return runCatching { Instant.parse(entry.at) }.getOrNull()?.to(payload)
    }

    fun <T> write(name: String, serializer: kotlinx.serialization.KSerializer<T>, payload: T) {
        writeRaw(name, json.encodeToString(serializer, payload))
    }

    fun cachedAt(name: String): Instant? =
        readRaw(name)?.let { entry -> runCatching { Instant.parse(entry.at) }.getOrNull() }

    /** TTL gate for silent background refreshes; manual refresh still bypasses it. */
    fun isFresh(name: String, ttl: Duration, now: Instant = Instant.now()): Boolean =
        cachedAt(name)?.let { isCacheFresh(it, ttl, now) } == true

    /** Keep the newest [max] entries (schedule weeks dominate the count). */
    private fun trim(max: Int = 40) {
        val files = dir.listFiles() ?: return
        if (files.size <= max) return
        files.sortedByDescending { it.lastModified() }
            .drop(max)
            .forEach { runCatching { it.delete() } }
    }

    fun clear() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun remove(name: String) {
        runCatching { file(name).delete() }
    }
}

internal fun isCacheFresh(
    cachedAt: Instant,
    ttl: Duration,
    now: Instant = Instant.now()
): Boolean {
    if (ttl.isNegative || ttl.isZero) return false
    return !cachedAt.isAfter(now) && Duration.between(cachedAt, now) <= ttl
}
