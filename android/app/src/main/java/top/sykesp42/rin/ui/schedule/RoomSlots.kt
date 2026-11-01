package top.sykesp42.rin.ui.schedule

/**
 * One teaching block as the school pairs it: two 45-minute periods with the
 * break already folded in. [key] is the `jc` value the free-room endpoint
 * expects (`0102`), [label] is what the room helper shows.
 */
data class RoomSlot(
    val key: String,
    val label: String,
    val short: String,
    val start: String,
    val end: String
)

/** The five blocks a GDUPT day is timetabled in, in clock order. */
val ROOM_SLOTS: List<RoomSlot> = listOf(
    RoomSlot("0102", "第 1-2 节", "1-2", "08:00", "09:40"),
    RoomSlot("0304", "第 3-4 节", "3-4", "10:00", "11:40"),
    RoomSlot("0506", "第 5-6 节", "5-6", "14:30", "16:10"),
    RoomSlot("0708", "第 7-8 节", "7-8", "16:20", "17:50"),
    RoomSlot("0910", "第 9-10 节", "9-10", "19:40", "21:20")
)

/**
 * The block that contains [minutes], else the next block that has not started
 * yet (so an 11:00 query still answers for 14:30), else null after the day.
 */
fun roomSlotAt(minutes: Int): RoomSlot? {
    ROOM_SLOTS.firstOrNull { slot ->
        val start = clockToMinutes(slot.start) ?: return@firstOrNull false
        val end = clockToMinutes(slot.end) ?: return@firstOrNull false
        minutes in start..end
    }?.let { return it }
    return ROOM_SLOTS.firstOrNull { slot ->
        val start = clockToMinutes(slot.start) ?: return@firstOrNull false
        start > minutes
    }
}

/** Human line for the slot header: "第 3-4 节 · 10:00-11:40". */
fun roomSlotLine(slot: RoomSlot): String = "${slot.label} · ${slot.start}-${slot.end}"

/**
 * Room names come straight from the school ("二教B-308", "B201"). Comparing
 * them has to ignore case, spaces and dashes so a name typed by hand and the
 * name printed on the list still match.
 */
fun normalizeRoomName(value: String): String =
    value.lowercase().filterNot { it.isWhitespace() || it == '-' || it == '_' || it == '－' }

/** True when [roomName] appears in the school's free-room answer for a slot. */
fun isRoomFree(freeNames: Set<String>, roomName: String): Boolean {
    val target = normalizeRoomName(roomName)
    if (target.isBlank()) return false
    return freeNames.any { normalizeRoomName(it) == target }
}

/** Which weekday (1=Mon … 7=Sun) [date] is, ready for the UI chips. */
fun weekdayOf(date: java.time.LocalDate): Int = date.dayOfWeek.value

/**
 * Teaching week [day] falls in, derived from the school's current week.
 * "Tomorrow" on a Sunday belongs to the next teaching week, which is exactly
 * why this is a week delta and not a date guess.
 */
fun weekNumberFor(day: java.time.LocalDate, currentWeek: Int, today: java.time.LocalDate): Int {
    val base = today.with(java.time.DayOfWeek.MONDAY)
    val target = day.with(java.time.DayOfWeek.MONDAY)
    val delta = java.time.temporal.ChronoUnit.WEEKS.between(base, target).toInt()
    return (currentWeek + delta).coerceIn(1, 30)
}
