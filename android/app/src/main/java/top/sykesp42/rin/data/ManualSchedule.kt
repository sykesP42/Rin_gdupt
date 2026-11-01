package top.sykesp42.rin.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ManualCourse(
    val id: String,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val weekday: Int,
    val sectionText: String,
    val firstWeek: Int = 1,
    val lastWeek: Int = 20,
    val colorIndex: Int = 0
)

@Serializable
data class ManualExam(
    val id: String,
    val name: String,
    val time: String,
    val location: String = "",
    val note: String = ""
)

@Serializable
data class ManualTodo(
    val id: String,
    val title: String,
    val due: String = "",
    val done: Boolean = false
)

/** A one-off move: the named course sits at the target slot in that week. */
@Serializable
data class ManualMove(
    val id: String,
    val courseName: String,
    val week: Int,
    val weekday: Int,
    val sectionText: String
)

/**
 * A day the user marked themselves. The school calendar cannot be trusted for
 * make-up days and moved holidays, so the app never guesses and stores the
 * reader's own call instead.
 */
@Serializable
data class ManualDayMark(val date: String, val kind: String) {
    companion object {
        const val OFF = "off"
        const val MAKEUP = "makeup"
        const val EXAM = "exam"
        const val STUDY = "study"

        val kinds = listOf(OFF, MAKEUP, EXAM, STUDY)

        fun label(kind: String): String = when (kind) {
            OFF -> "放假"
            MAKEUP -> "调休"
            EXAM -> "考试"
            STUDY -> "自习"
            else -> "未标记"
        }
    }
}

@Serializable
data class ManualPayload(
    val courses: List<ManualCourse> = emptyList(),
    val exams: List<ManualExam> = emptyList(),
    val todos: List<ManualTodo> = emptyList(),
    val moves: List<ManualMove> = emptyList(),
    val dayMarks: List<ManualDayMark> = emptyList()
) {
    val isEmpty: Boolean
        get() = courses.isEmpty() && exams.isEmpty() && todos.isEmpty() &&
            moves.isEmpty() && dayMarks.isEmpty()
}

/**
 * Everything the user typed in themselves. Kept apart from the crawled schedule
 * so a refresh can never erase it, and so one button can drop all of it.
 */
class ManualScheduleStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file: File get() = File(context.filesDir, "manual_schedule.json")

    private val _payload = MutableStateFlow(load())
    val payload: StateFlow<ManualPayload> = _payload.asStateFlow()

    private fun load(): ManualPayload =
        runCatching { json.decodeFromString<ManualPayload>(file.readText()) }.getOrDefault(ManualPayload())

    private fun save(next: ManualPayload) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(ManualPayload.serializer(), next))
        }
        _payload.value = next
    }

    fun addCourse(course: ManualCourse) = save(_payload.value.copy(courses = _payload.value.courses + course))

    fun removeCourse(id: String) = save(_payload.value.copy(courses = _payload.value.courses.filterNot { it.id == id }))

    fun addExam(exam: ManualExam) = save(_payload.value.copy(exams = _payload.value.exams + exam))

    fun removeExam(id: String) = save(_payload.value.copy(exams = _payload.value.exams.filterNot { it.id == id }))

    fun addTodo(todo: ManualTodo) = save(_payload.value.copy(todos = _payload.value.todos + todo))

    fun toggleTodo(id: String) = save(
        _payload.value.copy(todos = _payload.value.todos.map { if (it.id == id) it.copy(done = !it.done) else it })
    )

    fun removeTodo(id: String) = save(_payload.value.copy(todos = _payload.value.todos.filterNot { it.id == id }))

    fun addMove(move: ManualMove) = save(_payload.value.copy(moves = _payload.value.moves + move))

    fun removeMove(id: String) = save(_payload.value.copy(moves = _payload.value.moves.filterNot { it.id == id }))

    /** Empty kind clears the mark; a different kind replaces it. */
    fun setDayMark(date: String, kind: String) {
        val rest = _payload.value.dayMarks.filterNot { it.date == date }
        val next = if (kind.isBlank()) rest else rest + ManualDayMark(date = date, kind = kind)
        save(_payload.value.copy(dayMarks = next.sortedBy { it.date }))
    }

    /** 重置：drop every manual entry, leaving only what was crawled. */
    fun resetAll() = save(ManualPayload())
}

/** Does this manually typed course collide with one already in the week? */
fun findCourseConflict(courses: List<ManualCourse>, candidate: ManualCourse): ManualCourse? =
    courses.firstOrNull {
        it.id != candidate.id &&
            it.name.trim() == candidate.name.trim() &&
            it.weekday == candidate.weekday &&
            it.sectionText.trim() == candidate.sectionText.trim()
    }

/**
 * Overlays manual courses and one-off moves on the crawled week.
 *
 * Manual rows win on a name clash, and a move removes the course from its old
 * slot for that week only — so refreshing the crawled data can never undo either.
 */
fun withManualEntries(overview: ApiScheduleOverview, payload: ManualPayload): ApiScheduleOverview {
    if (payload.isEmpty) return overview
    val week = overview.week
    val movedAway = payload.moves.filter { it.week == week }.map { it.courseName.trim() }.toSet()
    val days = (1..7).map { weekday ->
        val source = overview.days.firstOrNull { it.weekday == weekday }
        val crawled = source?.events.orEmpty().filterNot { it.courseName.trim() in movedAway }
        val manualHere = payload.courses
            .filter { it.weekday == weekday && week in it.firstWeek..it.lastWeek }
            .map {
                ApiScheduleEvent(
                    courseName = it.name,
                    location = it.location,
                    sectionText = it.sectionText
                )
            }
        val movedHere = payload.moves
            .filter { it.week == week && it.weekday == weekday }
            .map { ApiScheduleEvent(courseName = it.courseName, sectionText = it.sectionText) }
        val manualNames = (manualHere + movedHere).map { it.courseName.trim() }.toSet()
        val events = crawled.filterNot { it.courseName.trim() in manualNames } + manualHere + movedHere
        (source ?: ApiScheduleDay(weekday = weekday)).copy(events = events)
    }
    val todayWeekday = days.firstOrNull { it.date == overview.date }?.weekday
    return overview.copy(
        days = days,
        today = todayWeekday?.let { weekday -> days.first { it.weekday == weekday }.events }
            ?: overview.today
    )
}
