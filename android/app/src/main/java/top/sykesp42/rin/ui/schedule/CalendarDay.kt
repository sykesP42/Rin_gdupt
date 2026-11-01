package top.sykesp42.rin.ui.schedule

import top.sykesp42.rin.data.JwxtTermCourse

/** A class that runs on one calendar day, derived from the term schedule. */
data class DayCourse(
    val courseName: String,
    val sectionText: String,
    val location: String,
    val teacher: String
)

/** `第 10 周` / `第10周` → 10. */
fun weekNumberFromLabel(label: String): Int? =
    Regex("\\d+").find(label)?.value?.toIntOrNull()

/**
 * Slots that fall on a given weekday within a given teaching week. Term-course
 * rows carry no per-date clock, so this returns section text and room only.
 */
fun coursesOnDate(
    courses: List<JwxtTermCourse>,
    weekday: Int,
    week: Int
): List<DayCourse> =
    courses.asSequence()
        .flatMap { course -> course.slots.asSequence() }
        .filter { slot -> slot.weekday == weekday && week in slot.firstWeek..slot.lastWeek }
        .sortedWith(
            compareBy({ slot -> slot.sectionText.firstSectionNumber() }, { slot -> slot.courseName })
        )
        .map { slot ->
            DayCourse(
                courseName = slot.courseName,
                sectionText = slot.sectionText,
                location = slot.location,
                teacher = slot.teacher
            )
        }
        .toList()

private fun String.firstSectionNumber(): Int =
    Regex("\\d+").find(this)?.value?.toIntOrNull() ?: 99
