package top.sykesp42.rin.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pure formatting for data export: grades CSV, schedule iCal, full JSON backup.
 * No Android dependencies — the caller writes the string to a file or share sheet.
 */
object DataExporter {

    /** Grades as CSV (BOM-prefixed for Excel Chinese support). */
    fun gradesCsv(grades: List<JwxtGrade>): String {
        val header = "学期,课程名称,成绩,绩点,学分,课程大类,修读方式,有效"
        val rows = grades.map { g ->
            listOf(
                g.term, g.courseName, g.score,
                g.gradePoint?.toString() ?: "", g.credits?.toString() ?: "",
                g.category, g.studyMode, if (g.active) "是" else "否"
            ).joinToString(",") { field -> "\"${field.replace("\"", "\"\"")}\"" }
        }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\n")
    }

    /**
     * Week schedule as iCal VEVENTs. Each class slot becomes a recurring
     * weekly event during its week range, with the classroom in LOCATION.
     */
    fun scheduleIcal(
        termCourses: List<JwxtTermCourse>,
        termStart: LocalDate,
        termName: String
    ): String {
        val dtFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        val now = java.time.LocalDateTime.now().format(dtFormat)
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Rin//Schedule//CN")
        sb.appendLine("X-WR-CALNAME:$termName 课表")
        for (course in termCourses) {
            for (slot in course.slots) {
                val firstDate = termStart.plusDays((slot.weekday - 1).toLong())
                    .plusWeeks((slot.firstWeek - 1).toLong())
                val startTime = sectionClock(slot.sectionText, isStart = true)
                val endTime = sectionClock(slot.sectionText, isStart = false)
                if (startTime == null || endTime == null) continue
                val dateStr = firstDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                sb.appendLine("BEGIN:VEVENT")
                sb.appendLine("UID:rin-${slot.courseName.hashCode()}-${slot.weekday}-${slot.sectionText}@rin.app")
                sb.appendLine("DTSTAMP:$now")
                sb.appendLine("DTSTART;TZID=Asia/Shanghai:${dateStr}T${startTime.replace(":", "")}00")
                sb.appendLine("DTEND;TZID=Asia/Shanghai:${dateStr}T${endTime.replace(":", "")}00")
                sb.appendLine("RRULE:FREQ=WEEKLY;COUNT=${slot.lastWeek - slot.firstWeek + 1}")
                sb.appendLine("SUMMARY:${slot.courseName}")
                if (slot.location.isNotBlank()) sb.appendLine("LOCATION:${slot.location}")
                if (course.teacher.isNotBlank()) sb.appendLine("DESCRIPTION:教师 ${course.teacher}")
                sb.appendLine("END:VEVENT")
            }
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    /** Full backup as pretty JSON. */
    fun fullBackup(
        grades: List<JwxtGrade>,
        termCourses: List<JwxtTermCourse>,
        student: JwxtStudentInfo?
    ): String {
        val map = mapOf(
            "exportedAt" to java.time.Instant.now().toString(),
            "student" to student?.let {
                mapOf("name" to it.name, "studentId" to it.studentId,
                    "major" to it.major, "className" to it.className)
            },
            "grades" to grades.map { g ->
                mapOf("term" to g.term, "course" to g.courseName, "score" to g.score,
                    "gpa" to g.gradePoint, "credits" to g.credits, "active" to g.active)
            },
            "courses" to termCourses.map { c ->
                mapOf("name" to c.courseName, "teacher" to c.teacher,
                    "location" to c.location, "weeks" to "${c.firstWeek}-${c.lastWeek}")
            }
        )
        return jsonPretty(map)
    }

    private fun jsonPretty(obj: Any?): String =
        kotlinx.serialization.json.Json { prettyPrint = true }
            .encodeToString(kotlinx.serialization.json.JsonElement.serializer(),
                kotlinx.serialization.json.Json.parseToJsonElement(
                    kotlinx.serialization.json.Json.encodeToString(
                        kotlinx.serialization.serializer<Map<String, Any?>>(),
                        @Suppress("UNCHECKED_CAST") obj as Map<String, Any?>
                    )
                ))

    /** First/last clock time for a section list like "3,4" → "10:00"/"11:40". */
    private fun sectionClock(sectionText: String, isStart: Boolean): String? {
        val sections = sectionText.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (sections.isEmpty()) return null
        val bounds = mapOf(
            1 to "08:00", 2 to "08:55", 3 to "10:00", 4 to "10:55",
            5 to "14:30", 6 to "15:25", 7 to "16:20", 8 to "17:00",
            9 to "19:40", 10 to "20:35"
        )
        val ends = mapOf(
            1 to "08:45", 2 to "09:40", 3 to "10:45", 4 to "11:40",
            5 to "15:15", 6 to "16:10", 7 to "17:00", 8 to "17:50",
            9 to "20:25", 10 to "21:20"
        )
        val target = if (isStart) sections.first() else sections.last()
        return (if (isStart) bounds else ends)[target]
    }
}
