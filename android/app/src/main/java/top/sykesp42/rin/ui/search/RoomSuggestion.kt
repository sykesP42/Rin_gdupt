package top.sykesp42.rin.ui.search

data class RoomSuggestion(val code: String, val courseName: String)

/**
 * Picks the room worth suggesting: the live monitor room first, then today's
 * scheduled room. A manually entered code suppresses a duplicate suggestion.
 */
fun suggestActiveRoom(
    live: RoomSuggestion?,
    scheduled: RoomSuggestion?,
    manualCode: String
): RoomSuggestion? = (live ?: scheduled)?.takeIf { it.code != manualCode }
