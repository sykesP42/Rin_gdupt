package top.sykesp42.rin.data

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import top.sykesp42.rin.domain.State
import top.sykesp42.rin.monitor.MonitorView
import top.sykesp42.rin.monitor.MySignState

object AttendanceStatus {
    const val SIGNED = "signed"
    const val MISSED = "missed"
    const val UNKNOWN = "unknown"
}

/**
 * One finished sign activity for this student. Only terminal outcomes are
 * stored (confirmed, or expired with a known roster); counts are class-wide,
 * never names.
 */
@Serializable
data class AttendanceRecord(
    val signId: String = "",
    val date: String = "",
    val courseName: String = "",
    val location: String = "",
    val roomCode: String = "",
    val status: String = AttendanceStatus.UNKNOWN,
    val signed: Int = 0,
    val roster: Int = 0,
    val threshold: Int = 0,
    val at: String = ""
)

/** Terminal observations only; an in-progress poll is not history yet. */
internal fun attendanceRecordOf(
    view: MonitorView,
    zone: ZoneId = ZoneId.of("Asia/Shanghai")
): AttendanceRecord? {
    if (view.signId.isBlank() || view.courseName.isBlank()) return null
    val status = when (view.state) {
        State.CONFIRMED -> AttendanceStatus.SIGNED
        State.EXPIRED -> when (view.roster.me) {
            MySignState.SIGNED -> AttendanceStatus.SIGNED
            MySignState.UNSIGNED -> AttendanceStatus.MISSED
            else -> AttendanceStatus.UNKNOWN
        }
        else -> return null
    }
    val observed = view.serverTime ?: Instant.now()
    return AttendanceRecord(
        signId = view.signId,
        date = observed.atZone(zone).toLocalDate().toString(),
        courseName = view.courseName,
        location = view.location,
        roomCode = view.roomCode,
        status = status,
        signed = view.roster.signed.takeIf { view.roster.known } ?: view.signedCount,
        roster = view.roster.total.takeIf { it > 0 } ?: view.rosterCount,
        threshold = view.threshold,
        at = observed.toString()
    )
}

/**
 * Idempotent upsert by signId. A later confirmation may upgrade an earlier
 * unknown/missed row; a confirmation is never downgraded.
 */
internal fun mergeAttendance(
    existing: List<AttendanceRecord>,
    record: AttendanceRecord
): List<AttendanceRecord> {
    val index = existing.indexOfFirst { it.signId == record.signId }
    if (index < 0) return existing + record
    val old = existing[index]
    if (old.status == AttendanceStatus.SIGNED) return existing
    if (old == record) return existing
    if (old.status == AttendanceStatus.MISSED && record.status == AttendanceStatus.UNKNOWN) return existing
    return existing.toMutableList().also { it[index] = record }
}

data class CourseAttendance(
    val courseName: String,
    val signed: Int,
    val missed: Int,
    val unknown: Int
) {
    val total: Int get() = signed + missed + unknown
}

/** Course-level tally, newest course first by its latest record. */
internal fun courseAttendance(records: List<AttendanceRecord>): List<CourseAttendance> =
    records.groupBy { it.courseName }
        .map { (course, rows) ->
            CourseAttendance(
                courseName = course,
                signed = rows.count { it.status == AttendanceStatus.SIGNED },
                missed = rows.count { it.status == AttendanceStatus.MISSED },
                unknown = rows.count { it.status == AttendanceStatus.UNKNOWN }
            )
        }

/** Keep the newest [max] rows so the ledger cannot grow without bound. */
internal fun trimAttendance(
    records: List<AttendanceRecord>,
    max: Int = AttendanceLedger.MAX_RECORDS
): List<AttendanceRecord> =
    if (records.size <= max) records else records.sortedByDescending { it.at }.take(max)

class AttendanceLedger(private val cache: LocalCache) {
    private val serializer = ListSerializer(AttendanceRecord.serializer())

    /** True when the stored ledger changed. */
    @Synchronized
    fun record(view: MonitorView): Boolean {
        val record = attendanceRecordOf(view) ?: return false
        val existing = readRows()
        val merged = mergeAttendance(existing, record)
        if (merged == existing) return false
        writeRows(merged)
        return true
    }

    /** Newest observation first. Falls back to the backup if the main file is corrupt. */
    fun all(): List<AttendanceRecord> =
        readRows().sortedByDescending { it.at }

    fun courses(): List<CourseAttendance> = courseAttendance(all())

    fun clear() {
        cache.remove(KEY)
        cache.remove(BACKUP_KEY)
    }

    private fun readRows(): List<AttendanceRecord> =
        cache.read(KEY, serializer)?.second
            ?: cache.read(BACKUP_KEY, serializer)?.second
            ?: emptyList()

    private fun writeRows(rows: List<AttendanceRecord>) {
        val trimmed = trimAttendance(rows)
        cache.write(KEY, serializer, trimmed)
        // A second copy protects the history from a single corrupted file.
        cache.write(BACKUP_KEY, serializer, trimmed)
    }

    companion object {
        private const val KEY = "attendance_v1"
        private const val BACKUP_KEY = "attendance_v1_bak"
        const val MAX_RECORDS = 500
    }
}
