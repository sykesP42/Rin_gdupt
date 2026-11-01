package top.sykesp42.rin.data

import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object HistoryKind {
    const val READY = "ready"
    const val CONFIRMED = "confirmed"
    const val CLASS_REMINDER = "class_reminder"
    const val SCHEDULE_CHANGE = "schedule_change"
    const val LOGIN = "login"
    const val LOGOUT = "logout"
    const val MONITOR_ENABLED = "monitor_enabled"
    const val MONITOR_DISABLED = "monitor_disabled"
    const val SESSION_EXPIRED = "session_expired"
    const val HANDOFF_OPENED = "handoff_opened"
    const val HANDOFF_SHARED = "handoff_shared"
    const val HANDOFF_FAILED = "handoff_failed"
    const val HANDOFF_UNCONFIRMED = "handoff_unconfirmed"
    const val AUTO_ENABLED = "auto_enabled"
    const val AUTO_DISABLED = "auto_disabled"
    const val AUTO_STARTED = "auto_started"
    const val AUTO_DEFERRED = "auto_deferred"
    const val AUTO_FAILED = "auto_failed"
    const val AUTO_CALIBRATION = "auto_calibration"
}

data class HistoryEvent(
    val at: Instant,
    val kind: String,
    val courseName: String = "",
    val roomCode: String = "",
    val state: String = ""
)

/**
 * Append-only redacted JSONL. There is deliberately no field for URLs, QR
 * payloads, cookies, student or teacher names.
 */
class HistoryStore(
    private val file: File,
    private val clock: () -> Instant = Instant::now
) {
    @Serializable
    private data class Payload(
        val at: String,
        val kind: String,
        val course: String = "",
        val room: String = "",
        val state: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun append(
        kind: String,
        courseName: String = "",
        roomCode: String = "",
        state: String = ""
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = clock()
            val cutoff = now.minus(RETENTION)
            val payloads = readPayloads()
                .filter { payload -> parseInstant(payload.at)?.let { !it.isBefore(cutoff) } ?: false }
                .toMutableList()
            val newKind = safe(kind, 40)
            val newCourse = safe(courseName)
            val newRoom = safe(roomCode, 12)
            val newState = safe(state, 24)
            val last = payloads.lastOrNull()
            val duplicate = last != null &&
                last.kind == newKind &&
                last.course == newCourse &&
                last.room == newRoom &&
                last.state == newState &&
                parseInstant(last.at)?.isAfter(now.minus(DEDUPE_WINDOW)) == true
            if (!duplicate) {
                payloads += Payload(
                    at = now.toString(),
                    kind = newKind,
                    course = newCourse,
                    room = newRoom,
                    state = newState
                )
            }
            write(payloads.takeLast(MAX_EVENTS))
        }
    }

    suspend fun read(): List<HistoryEvent> = withContext(Dispatchers.IO) {
        mutex.withLock { readPayloads().mapNotNull { it.toEvent() }.asReversed() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) file.delete()
        }
    }

    private fun readPayloads(): List<Payload> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            runCatching { json.decodeFromString(Payload.serializer(), line) }.getOrNull()
        }
    }

    private fun write(payloads: List<Payload>) {
        file.parentFile?.mkdirs()
        file.writeText(payloads.joinToString(separator = "\n", postfix = "\n") {
            json.encodeToString(Payload.serializer(), it)
        })
    }

    private fun Payload.toEvent(): HistoryEvent? {
        val at = parseInstant(at) ?: return null
        return HistoryEvent(at = at, kind = kind, courseName = course, roomCode = room, state = state)
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private fun safe(value: String, max: Int = 80): String {
        val oneLine = value.replace('\n', ' ').replace('\r', ' ').trim()
        if (oneLine.contains("http://") || oneLine.contains("https://") || oneLine.contains("data=")) {
            return ""
        }
        return oneLine.take(max)
    }

    companion object {
        private val RETENTION: Duration = Duration.ofDays(90)
        private val DEDUPE_WINDOW: Duration = Duration.ofMinutes(1)
        private const val MAX_EVENTS = 500
    }
}
