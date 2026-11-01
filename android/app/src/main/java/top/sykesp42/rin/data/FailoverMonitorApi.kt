package top.sykesp42.rin.data

import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local-first monitor API with an automatic cloud fallback. After
 * [FAIL_THRESHOLD] consecutive failures of the primary, traffic shifts to
 * the fallback for [STAY_MS], then one probe returns to the primary.
 * Auth errors never trigger failover (they need user action on both sides).
 */
class FailoverMonitorApi(
    private val primary: MonitorApi,
    private val fallback: MonitorApi,
    private val clock: () -> Instant = Instant::now
) : MonitorApi {

    private val mutex = Mutex()
    private var primaryFailures = 0
    private var fallbackUntil: Instant? = null

    private suspend fun <T> call(block: suspend (MonitorApi) -> T): T {
        val useFallback: Boolean
        mutex.withLock {
            useFallback = (fallbackUntil?.let { clock().isBefore(it) } == true) ||
                primaryFailures >= FAIL_THRESHOLD
        }
        if (useFallback) {
            // Fallback failures are never counted — they must not extend the
            // stay or poison the next primary probe.
            return block(fallback).also {
                mutex.withLock {
                    primaryFailures = 0
                    fallbackUntil = null
                }
            }
        }
        return try {
            val result = block(primary)
            mutex.withLock { primaryFailures = 0 }
            result
        } catch (error: ApiError) {
            // Auth errors pass through unmarked: they need user action on one
            // side only, and counting them would flip traffic permanently.
            if (!top.sykesp42.rin.ui.ErrorCopy.isAuth(error.code, error.status)) markFailure()
            throw error
        } catch (error: Exception) {
            markFailure()
            throw error
        }
    }

    private suspend fun markFailure() {
        mutex.withLock {
            primaryFailures++
            if (primaryFailures >= FAIL_THRESHOLD) {
                fallbackUntil = clock().plusMillis(STAY_MS)
            }
        }
    }

    override suspend fun recommendation(): ApiRecommendation = call { it.recommendation() }

    override suspend fun course(code: String): ApiCourse = call { it.course(code) }

    override suspend fun selfSigned(code: String): Boolean = call { it.selfSigned(code) }

    override suspend fun roster(code: String): ApiRoster = call { it.roster(code) }

    override suspend fun qrPng(code: String): ByteArray? = call { it.qrPng(code) }

    override suspend fun schedule(): ApiScheduleOverview = call { it.schedule() }

    companion object {
        const val FAIL_THRESHOLD = 3
        const val STAY_MS = 10 * 60 * 1000L
    }
}
