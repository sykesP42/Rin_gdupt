package top.sykesp42.rin.data

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Today's schedule as last accepted from the school. Used only to spot
 * room/time changes between refreshes; one snapshot per school day.
 */
@Serializable
data class TodayScheduleSnapshot(
    val date: String = "",
    val events: List<ApiScheduleEvent> = emptyList()
)

class ScheduleChangeStore(private val cache: LocalCache) {
    fun load(): Pair<Instant, TodayScheduleSnapshot>? =
        cache.read(KEY, TodayScheduleSnapshot.serializer())

    fun save(snapshot: TodayScheduleSnapshot) {
        cache.write(KEY, TodayScheduleSnapshot.serializer(), snapshot)
    }

    fun clear() = cache.remove(KEY)

    companion object {
        private const val KEY = "schedule_today_seen"
    }
}
