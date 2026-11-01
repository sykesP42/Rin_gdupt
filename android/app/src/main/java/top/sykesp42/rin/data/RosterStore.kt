package top.sykesp42.rin.data

import java.time.Instant
import kotlinx.serialization.Serializable
import top.sykesp42.rin.monitor.MonitorView
import top.sykesp42.rin.monitor.MySignState

/**
 * The last roster receipt, kept on disk so the Today page can still say what
 * the last known state was after a restart or while the school is unreachable.
 * Only counts and the user's own status are stored — never classmate names.
 */
@Serializable
data class RosterSnapshot(
    val signId: String = "",
    val courseName: String = "",
    val location: String = "",
    val signed: Int = 0,
    val unsigned: Int = 0,
    val teachers: Int = 0,
    val me: String = MySignState.UNKNOWN.name,
    val threshold: Int = 0,
    val savedAt: String = ""
) {
    val myState: MySignState
        get() = runCatching { MySignState.valueOf(me) }.getOrDefault(MySignState.UNKNOWN)
}

/** Null when there is nothing worth remembering (no activity or no roster). */
internal fun rosterSnapshotOf(view: MonitorView): RosterSnapshot? {
    if (view.signId.isBlank() || !view.roster.known) return null
    return RosterSnapshot(
        signId = view.signId,
        courseName = view.courseName,
        location = view.location,
        signed = view.roster.signed,
        unsigned = view.roster.unsigned,
        teachers = view.roster.teachers,
        me = view.roster.me.name,
        threshold = view.threshold,
        savedAt = Instant.now().toString()
    )
}

/** Same activity and same numbers: no need to rewrite the cache file. */
internal fun RosterSnapshot.sameContent(other: RosterSnapshot): Boolean =
    signId == other.signId &&
        courseName == other.courseName &&
        location == other.location &&
        signed == other.signed &&
        unsigned == other.unsigned &&
        teachers == other.teachers &&
        me == other.me &&
        threshold == other.threshold

class RosterStore(private val cache: LocalCache) {
    @Volatile
    private var last: RosterSnapshot? = null

    /** Returns the stored snapshot so the caller can show it immediately. */
    @Synchronized
    fun save(view: MonitorView): RosterSnapshot? {
        val snapshot = rosterSnapshotOf(view) ?: return null
        val previous = last ?: load()?.second
        // Polling runs every few seconds; only touch disk when the receipt
        // actually changed, and keep the original timestamp of the change.
        if (previous != null && previous.sameContent(snapshot)) {
            last = previous
            return previous
        }
        cache.write(KEY, RosterSnapshot.serializer(), snapshot)
        last = snapshot
        return snapshot
    }

    fun load(): Pair<Instant, RosterSnapshot>? =
        cache.read(KEY, RosterSnapshot.serializer())

    fun clear() {
        last = null
        cache.remove(KEY)
    }

    companion object {
        private const val KEY = "roster_last_v1"
    }
}
