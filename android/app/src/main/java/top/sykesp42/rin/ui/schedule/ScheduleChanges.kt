package top.sykesp42.rin.ui.schedule

import top.sykesp42.rin.data.ApiScheduleEvent

enum class ScheduleChangeKind { ADDED, REMOVED, MOVED, RETIMED }

data class ScheduleChange(
    val kind: ScheduleChangeKind,
    val courseName: String,
    val oldLocation: String = "",
    val newLocation: String = "",
    val oldTime: String = "",
    val newTime: String = ""
) {
    /** Short line for a notification or snackbar; no URLs, no names. */
    fun message(): String {
        val old = timeAndPlace(oldTime, oldLocation)
        val new = timeAndPlace(newTime, newLocation)
        return when (kind) {
            ScheduleChangeKind.ADDED -> "新增课程：$courseName $new"
            ScheduleChangeKind.REMOVED -> "停课：$courseName（原 $old）"
            ScheduleChangeKind.MOVED ->
                if (oldTime != newTime) "课程变动：$courseName $old → $new"
                else "换教室：$courseName $oldLocation → $newLocation"
            ScheduleChangeKind.RETIMED -> "调课：$courseName $old → $new"
        }
    }

    private fun timeAndPlace(time: String, place: String): String =
        listOf(time, place).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * Compare today's schedule with the last accepted copy. Matching is by course
 * name, then by sort order, because the school feed has no stable event id.
 */
internal fun diffSchedule(
    old: List<ApiScheduleEvent>,
    new: List<ApiScheduleEvent>
): List<ScheduleChange> {
    val oldGroups = old.filter { it.courseName.isNotBlank() }
        .sortedBy { it.startTime }
        .groupBy { it.courseName.trim() }
    val newGroups = new.filter { it.courseName.isNotBlank() }
        .sortedBy { it.startTime }
        .groupBy { it.courseName.trim() }
    val changes = mutableListOf<ScheduleChange>()
    for (name in oldGroups.keys + newGroups.keys) {
        val before = oldGroups[name].orEmpty()
        val after = newGroups[name].orEmpty()
        for (index in 0 until maxOf(before.size, after.size)) {
            val a = before.getOrNull(index)
            val b = after.getOrNull(index)
            if (a == null && b != null) {
                changes += ScheduleChange(
                    kind = ScheduleChangeKind.ADDED,
                    courseName = name,
                    newLocation = b.location.trim(),
                    newTime = timeOf(b)
                )
            } else if (a != null && b == null) {
                changes += ScheduleChange(
                    kind = ScheduleChangeKind.REMOVED,
                    courseName = name,
                    oldLocation = a.location.trim(),
                    oldTime = timeOf(a)
                )
            } else if (a != null && b != null) {
                val oldRoom = a.location.trim()
                val newRoom = b.location.trim()
                val oldTime = timeOf(a)
                val newTime = timeOf(b)
                if (oldRoom != newRoom) {
                    changes += ScheduleChange(
                        kind = ScheduleChangeKind.MOVED,
                        courseName = name,
                        oldLocation = oldRoom,
                        newLocation = newRoom,
                        oldTime = oldTime,
                        newTime = newTime
                    )
                } else if (oldTime != newTime) {
                    changes += ScheduleChange(
                        kind = ScheduleChangeKind.RETIMED,
                        courseName = name,
                        oldLocation = oldRoom,
                        newLocation = newRoom,
                        oldTime = oldTime,
                        newTime = newTime
                    )
                }
            }
        }
    }
    return changes
}

private fun timeOf(event: ApiScheduleEvent): String {
    val start = event.startTime.trim()
    val end = event.endTime.trim()
    return when {
        start.isBlank() && end.isBlank() -> ""
        end.isBlank() || end == start -> start
        else -> "$start–$end"
    }
}
