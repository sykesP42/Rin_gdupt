package top.sykesp42.rin.handoff

import android.content.Context
import java.time.Duration
import java.time.Instant

private const val SEPARATOR = "\u001F"
private const val PREFS_FILE = "handoff_journal"
private const val PREFS_KEY = "pending"

internal fun encodePending(pending: PendingConfirmation): String = listOf(
    pending.roomCode,
    pending.signId,
    pending.courseName.replace(SEPARATOR, " "),
    pending.deadline.toEpochMilli().toString()
).joinToString(SEPARATOR)

internal fun decodePending(raw: String?): PendingConfirmation? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split(SEPARATOR)
    if (parts.size != 4) return null
    val room = parts[0].trim()
    val sign = parts[1].trim()
    val millis = parts[3].toLongOrNull() ?: return null
    if (room.isEmpty() || sign.isEmpty() || millis <= 0) return null
    return PendingConfirmation(
        roomCode = room,
        signId = sign,
        courseName = parts[2],
        deadline = Instant.ofEpochMilli(millis)
    )
}

/**
 * A pending window is still worth resuming shortly after its deadline:
 * Android may have killed the process mid-confirmation, and the resumed run
 * does one final roster check instead of assuming failure.
 */
internal val HANDOFF_RESUME_GRACE: Duration = Duration.ofMinutes(2)

internal fun resumablePending(
    raw: String?,
    now: Instant,
    grace: Duration = HANDOFF_RESUME_GRACE
): PendingConfirmation? =
    decodePending(raw)?.takeIf { !now.isAfter(it.deadline.plus(grace)) }

/** Foreground re-arm window, shared with [resumablePending]. */
internal fun withinHandoffGrace(pending: PendingConfirmation, now: Instant): Boolean =
    !now.isAfter(pending.deadline.plus(HANDOFF_RESUME_GRACE))

/**
 * Survives process death during the 60s confirmation window so an aggressive
 * OEM killer cannot silently drop the receipt. Stores room, sign id, course
 * name and the device-clock deadline only — never names or the sign URL.
 */
class HandoffJournal(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun save(pending: PendingConfirmation) {
        runCatching { prefs.edit().putString(PREFS_KEY, encodePending(pending)).commit() }
    }

    fun loadResumable(now: Instant = Instant.now()): PendingConfirmation? {
        val pending = resumablePending(prefs.getString(PREFS_KEY, null), now)
        if (pending == null) clear()
        return pending
    }

    fun clear() {
        runCatching { prefs.edit().remove(PREFS_KEY).commit() }
    }
}
