package top.sykesp42.rin.data

import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer

/**
 * Local classroom catalog. One JSON snapshot is enough for a school-sized
 * catalog; search runs in memory and never needs the cloud service.
 */
class ClassroomCatalogRepository(private val cache: LocalCache) {
    private val serializer = ListSerializer(JwxtClassroom.serializer())

    fun cached(): Pair<Instant, List<JwxtClassroom>>? =
        cache.read(CACHE_KEY, serializer)?.takeIf { it.second.isNotEmpty() }

    fun save(rooms: List<JwxtClassroom>) {
        if (rooms.isNotEmpty()) cache.write(CACHE_KEY, serializer, rooms)
    }

    fun search(rooms: List<JwxtClassroom>, query: String, campus: String, limit: Int = 80): List<JwxtClassroom> =
        filterClassrooms(rooms, query, campus, limit)

    fun campuses(rooms: List<JwxtClassroom>): List<String> = classroomCampuses(rooms)

    companion object {
        private const val CACHE_KEY = "classrooms_v1"
    }
}

internal fun filterClassrooms(
    rooms: List<JwxtClassroom>,
    query: String,
    campus: String,
    limit: Int = 80
): List<JwxtClassroom> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return rooms.asSequence()
        .filter { campus.isBlank() || it.campus == campus }
        .filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.code.contains(needle, ignoreCase = true) ||
                it.building.contains(needle, ignoreCase = true) ||
                it.campus.contains(needle, ignoreCase = true)
        }
        .take(limit)
        .toList()
}

internal fun classroomCampuses(rooms: List<JwxtClassroom>): List<String> =
    rooms.map { it.campus }.filter { it.isNotBlank() }.distinct().sorted()

/**
 * Resolve a schedule location such as `二教A-505` to its four-digit code, but
 * only from rooms whose code has been verified. Unverified rooms return null so
 * callers never monitor a guessed code.
 */
internal fun verifiedRoomCode(location: String, rooms: List<JwxtClassroom>): String? {
    val needle = location.filterNot { it.isWhitespace() }
    if (needle.isEmpty()) return null
    return rooms.firstOrNull { room ->
        room.code.isNotBlank() &&
            room.name.filterNot { it.isWhitespace() }.equals(needle, ignoreCase = true)
    }?.code
}
