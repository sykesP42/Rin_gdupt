package top.sykesp42.rin.data

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Local-mode MonitorApi: derives the read-only course recommendation from the
 * cached timetable and serves the schedule. No sign-side endpoints exist in
 * this build, so room-code matching never happens and statuses stay honest.
 */
class LocalSchoolApi(
    private val scheduleProvider: suspend () -> ApiScheduleOverview = { ApiScheduleOverview() },
    private val clock: () -> Instant = Instant::now,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(java.time.Duration.ofSeconds(8)).build()
) : MonitorApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val classTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Local classroom recognition: current/next event from the schedule,
     * resolved through the learned location→code memory.
     */
    override suspend fun recommendation(): ApiRecommendation {
        val overview = runCatching { scheduleProvider() }.getOrNull()
            ?: return ApiRecommendation(status = "unavailable")
        val now = clock().atZone(shanghai)
        val minutes = now.hour * 60 + now.minute
        val today = now.toLocalDate().toString()
        // overview.today is a snapshot from fetch time and goes stale across
        // midnight; the dated days list is authoritative for "today".
        val events = (overview.days.firstOrNull { it.date == today }?.events
            ?: overview.today)
            .sortedBy { it.startTime }
        if (events.isEmpty()) return ApiRecommendation(status = "no_course_today")
        val current = events.firstOrNull {
            sectionMinutes(it.startTime) <= minutes && minutes <= sectionMinutes(it.endTime)
        }
        val next = events.firstOrNull { sectionMinutes(it.startTime) > minutes }
        val event = current ?: next ?: return ApiRecommendation(status = "day_done")
        val phase = if (event === current) "current" else "pre"
        return ApiRecommendation(
            status = "matched", phase = phase,
            room = ApiRoomRef(roomName = event.location)
        )
    }

    private fun sectionMinutes(clock: String): Int {
        val parts = clock.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    override suspend fun schedule(): ApiScheduleOverview = scheduleProvider()
}
