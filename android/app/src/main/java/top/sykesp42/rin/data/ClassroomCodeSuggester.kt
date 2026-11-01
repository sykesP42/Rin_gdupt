package top.sykesp42.rin.data

import kotlin.math.abs

/**
 * Conservatively infer a four-digit code for an unverified room from verified
 * neighbours in the same building. The caller must show the suggestion for
 * confirmation; it is never used as an automatic monitoring target.
 */
internal fun suggestClassroomCodes(rooms: List<JwxtClassroom>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    rooms.groupBy { it.building }
        .filterKeys { it.isNotBlank() }
        .forEach { (_, group) ->
            val ordered = group.mapNotNull { room ->
                roomNumber(room.name)?.let { Triple(it, room, 0) }
            }.sortedBy { it.first }
            if (ordered.size < 2) return@forEach
            val anchors = ordered.mapIndexedNotNull { index, item ->
                item.second.code.takeIf { code -> Regex("^\\d{4}$").matches(code) }
                    ?.let { index to it.toInt() }
            }
            if (anchors.isEmpty()) return@forEach
            ordered.forEachIndexed { index, (_, room, _) ->
                if (room.code.isNotBlank()) return@forEachIndexed
                val (anchorIndex, anchorCode) = anchors.minByOrNull { abs(it.first - index) }
                    ?: return@forEachIndexed
                if (abs(anchorIndex - index) > 10) return@forEachIndexed
                val candidate = (anchorCode + (index - anchorIndex))
                    .takeIf { it in 0..9999 }
                    ?.toString()
                    ?.padStart(4, '0')
                    ?: return@forEachIndexed
                if (group.none { it.code == candidate }) result[room.key()] = candidate
            }
        }
    return result
}

private fun roomNumber(name: String): Int? =
    Regex("\\d+").findAll(name).lastOrNull()?.value?.toIntOrNull()
