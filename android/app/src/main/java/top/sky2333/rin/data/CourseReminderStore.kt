package top.sky2333.rin.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Remembered form values for the next reminder; force-unmute is never included. */
@Serializable
data class ReminderPrefs(
    val leadMinutes: Int = 15,
    val appReminder: Boolean = true,
    val calendarEvent: Boolean = false,
    val alarm: Boolean = true,
    val volume: Int = 70,
    val vibrate: Boolean = true
)

/** A one-off reminder created from a concrete course occurrence. */
@Serializable
data class CourseReminder(
    val id: String,
    val courseName: String,
    val date: String,
    val startTime: String,
    val endTime: String = "",
    val location: String = "",
    val leadMinutes: Int = 15,
    val appReminder: Boolean = true,
    val calendarEvent: Boolean = false,
    val alarm: Boolean = true,
    val forceUnmute: Boolean = false,
    val volume: Int = 70,
    val volumeEnabled: Boolean = false,
    val vibrate: Boolean = false,
    val signReminder: Boolean = false,
    val createdAt: String = ""
) {
    fun triggerAt(): LocalDateTime? = runCatching {
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(startTime))
            .minusMinutes(leadMinutes.toLong())
    }.getOrNull()

    fun classAt(): LocalDateTime? = runCatching {
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(startTime))
    }.getOrNull()

    fun endAt(): LocalDateTime? {
        val start = classAt() ?: return null
        val end = runCatching { LocalDate.parse(date).atTime(LocalTime.parse(endTime)) }.getOrNull()
        return end ?: start.plusMinutes(95)
    }

    fun resultLine(): String {
        val trigger = triggerAt() ?: return "时间待定"
        return "将在 ${trigger.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))} 响铃"
    }
}

@Serializable
data class ReminderPayload(
    val reminders: List<CourseReminder> = emptyList(),
    val prefs: ReminderPrefs = ReminderPrefs()
)

/**
 * One-off course reminders live beside (not inside) the schedule cache, so a
 * timetable refresh can never cancel an alarm the reader set deliberately.
 */
class CourseReminderStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file: File get() = File(context.filesDir, "course_reminders.json")

    private val _payload = MutableStateFlow(load())
    val payload: StateFlow<ReminderPayload> = _payload.asStateFlow()

    private fun load(): ReminderPayload =
        runCatching { json.decodeFromString<ReminderPayload>(file.readText()) }
            .getOrDefault(ReminderPayload())

    private fun save(next: ReminderPayload) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(ReminderPayload.serializer(), next))
        }
        _payload.value = next
    }

    fun add(reminder: CourseReminder, prefs: ReminderPrefs) {
        val kept = _payload.value.reminders.filterNot { it.id == reminder.id }
        save(_payload.value.copy(reminders = (kept + reminder).sortedBy { it.date + it.startTime }, prefs = prefs))
    }

    fun remove(id: String) {
        save(_payload.value.copy(reminders = _payload.value.reminders.filterNot { it.id == id }))
    }

    fun savePrefs(prefs: ReminderPrefs) {
        save(_payload.value.copy(prefs = prefs))
    }

    fun prune(now: LocalDateTime = LocalDateTime.now()) {
        val kept = _payload.value.reminders.filter { reminder ->
            reminder.classAt()?.isAfter(now.minusDays(1)) != false
        }
        if (kept.size != _payload.value.reminders.size) {
            save(_payload.value.copy(reminders = kept))
        }
    }
}
