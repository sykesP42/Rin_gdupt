package top.sykesp42.rin.data

import android.content.Context
import java.time.Instant
import kotlinx.serialization.Serializable
import top.sykesp42.rin.domain.SignAccessState
import top.sykesp42.rin.domain.signAccessFromCache

@Serializable
data class SignAccessRemote(
    val status: String = "none",
    val source: String = "",
    val appliedVersion: String = "",
    val requestedAt: String = "",
    val decidedAt: String? = null,
    val revokedAt: String? = null,
    val lastCheckedAt: String? = null,
    val secret: String? = null
)

@Serializable
data class SignAccessCache(
    val status: String = "none",
    val appliedVersion: String = "",
    val source: String = ""
)

private const val SIGN_ACCESS_CACHE = "sign_access"

class SignAccessStore(private val cache: LocalCache) {

    fun read(): Pair<Instant, SignAccessCache>? =
        cache.read(SIGN_ACCESS_CACHE, SignAccessCache.serializer())

    fun write(remote: SignAccessRemote) {
        cache.write(
            SIGN_ACCESS_CACHE,
            SignAccessCache.serializer(),
            SignAccessCache(
                status = remote.status,
                appliedVersion = remote.appliedVersion,
                source = remote.source
            )
        )
    }

    fun clear() {
        cache.remove(SIGN_ACCESS_CACHE)
    }

    fun current(now: Instant = Instant.now()): SignAccessState {
        val cached = read() ?: return SignAccessState(
            status = top.sykesp42.rin.domain.SignAccessLevel.NONE
        )
        return signAccessFromCache(
            status = cached.second.status,
            appliedVersion = cached.second.appliedVersion,
            checkedAt = cached.first,
            now = now
        )
    }

    fun proEnabled(now: Instant = Instant.now()): Boolean = current(now).proEnabled
}

fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "0.0.0" }
