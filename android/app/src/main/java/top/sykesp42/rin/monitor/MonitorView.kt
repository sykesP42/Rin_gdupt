package top.sykesp42.rin.monitor

import java.time.Instant
import top.sykesp42.rin.data.ApiRoster
import top.sykesp42.rin.data.ApiRosterPerson
import top.sykesp42.rin.domain.State

/** How the signed-in student appears in the current activity's roster. */
enum class MySignState { SIGNED, UNSIGNED, NOT_IN_ROSTER, AMBIGUOUS, UNKNOWN }

/**
 * Aggregate roster state for the UI. The detailed sheet uses [RosterEntries]
 * with masked names; this summary keeps only counts for compact cards and is
 * the only roster data that may enter persisted receipts.
 */
data class MonitorRoster(
    val signed: Int = 0,
    val unsigned: Int = 0,
    val teachers: Int = 0,
    val me: MySignState = MySignState.UNKNOWN
) {
    val total: Int get() = signed + unsigned
    val known: Boolean get() = total > 0 || me != MySignState.UNKNOWN
}

fun ApiRoster.toMonitorRoster(): MonitorRoster {
    val meHits = signed.count { it.isCurrentUser } + unsigned.count { it.isCurrentUser }
    val me = when {
        meHits > 1 -> MySignState.AMBIGUOUS
        signed.any { it.isCurrentUser } -> MySignState.SIGNED
        unsigned.any { it.isCurrentUser } -> MySignState.UNSIGNED
        signed.isEmpty() && unsigned.isEmpty() -> MySignState.UNKNOWN
        else -> MySignState.NOT_IN_ROSTER
    }
    return MonitorRoster(
        signed = signed.size,
        unsigned = unsigned.size,
        teachers = excludedCount,
        me = me
    )
}

/**
 * One masked roster row, matching the original web list: name, signed time,
 * sign mode and late flag. The current user is highlighted by [isMe].
 */
data class RosterEntry(
    val name: String,
    val signed: Boolean,
    val isMe: Boolean = false,
    val isTeacher: Boolean = false,
    val signedAt: Instant? = null,
    val signModeLabel: String = "",
    val isLate: Boolean = false
)

data class RosterEntries(
    val signed: List<RosterEntry> = emptyList(),
    val unsigned: List<RosterEntry> = emptyList(),
    val excluded: Int = 0
) {
    val known: Boolean get() = signed.isNotEmpty() || unsigned.isNotEmpty()
}

/** "张三" -> "张*", "欧阳娜娜" -> "欧*娜"; never reveal a full classmate name. */
fun maskRosterName(raw: String): String {
    val chars = raw.trim().toCharArray()
    return when (chars.size) {
        0, 1 -> "*"
        2 -> "${chars[0]}*"
        else -> "${chars[0]}*${chars[chars.size - 1]}"
    }
}

fun signModeLabel(mode: String): String = when (mode) {
    "0" -> "扫码签到"
    "1" -> "班牌签到"
    "2" -> "雨课堂签到"
    "3" -> "超星签到"
    "4" -> "奕课堂签到"
    else -> ""
}

private fun ApiRosterPerson.toEntry(): RosterEntry = RosterEntry(
    name = maskRosterName(name),
    signed = signed || status == "1" || status == "2",
    isMe = isCurrentUser,
    isTeacher = isTeacher,
    signedAt = signTime.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    signModeLabel = signModeLabel(signMode),
    isLate = isLate
)

fun ApiRoster.toRosterEntries(): RosterEntries {
    val rows = if (people.isNotEmpty()) people else signed + unsigned
    if (rows.isEmpty()) return RosterEntries()
    // The cloud payload may omit the boolean and only send the status code.
    fun isSigned(person: ApiRosterPerson) =
        person.signed || person.status == "1" || person.status == "2"
    val signedRows = rows.filter { isSigned(it) }.map { it.toEntry() }
    val unsignedRows = rows.filter { it.status == "0" && !isSigned(it) }.map { it.toEntry() }
    val excluded = rows.count { !isSigned(it) && it.status != "0" } +
        if (people.isEmpty()) excludedCount else 0
    return RosterEntries(signed = signedRows, unsigned = unsignedRows, excluded = excluded)
}

/** QR is only offered when the original server would hand one out. */
fun qrEligible(view: MonitorView): Boolean =
    view.state == State.READY &&
        view.signAccessAllowed &&
        view.signId.isNotBlank() &&
        view.roomCode.isNotBlank()

/**
 * The monitor snapshot the service and UI share. It mirrors the Windows
 * monitor.View so both clients describe the same state the same way.
 */
data class MonitorView(
    val state: State,
    val autoAllowed: Boolean = false,
    val manualRequiresConfirmation: Boolean = false,
    val signId: String = "",
    val roomCode: String = "",
    val courseName: String = "",
    val location: String = "",
    val teacherNames: List<String> = emptyList(),
    val sections: String = "",
    val signedCount: Int = 0,
    val rosterCount: Int = 0,
    val roster: MonitorRoster = MonitorRoster(),
    val rosterPeople: RosterEntries = RosterEntries(),
    val signAccessAllowed: Boolean = false,
    val threshold: Int = 0,
    val effectiveSignEndAt: Instant? = null,
    val qrRefreshIntervalSeconds: Int = 0,
    val qrType: String = "",
    val errorCode: String = "",
    val serverTime: Instant? = null,
    /**
     * Why the engine is IDLE: the raw recommendation status ("matched",
     * "no_course_today", "unknown_room", "unavailable"...). Empty once a
     * course was actually polled, so the UI can tell "今天没课" apart from
     * "教室码没确认" instead of blaming the user's empty day.
     */
    val recommendationStatus: String = ""
) {
    val canAct: Boolean get() = state == State.READY || state == State.WAITING_THRESHOLD
}
