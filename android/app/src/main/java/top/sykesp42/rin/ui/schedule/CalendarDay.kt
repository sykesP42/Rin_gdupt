package top.sykesp42.rin.ui.schedule

import top.sykesp42.rin.data.JwxtTermCourse
import top.sykesp42.rin.data.ManualCourse

/** A class that runs on one calendar day, derived from the term schedule. */
data class DayCourse(
    val courseName: String,
    val sectionText: String,
    val location: String,
    val teacher: String,
    /**
     * Where this row came from. The full client only ever had the school
     * timetable; the public build also accepts hand-typed courses, and the day
     * sheet says which is which instead of mixing them silently.
     */
    val source: String = SOURCE_JWXT
) {
    companion object {
        const val SOURCE_JWXT = "jwxt"
        const val SOURCE_MANUAL = "manual"
    }
}

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

/** The hand-typed classes for one weekday in one teaching week. */
fun manualCoursesOnDate(
    courses: List<ManualCourse>,
    weekday: Int,
    week: Int
): List<DayCourse> =
    courses.asSequence()
        .filter { course -> course.weekday == weekday && week in course.firstWeek..course.lastWeek }
        .sortedBy { it.sectionText.firstSectionNumber() }
        .map { course ->
            DayCourse(
                courseName = course.name,
                sectionText = course.sectionText,
                location = course.location,
                teacher = course.teacher,
                source = DayCourse.SOURCE_MANUAL
            )
        }
        .toList()

/** Both sources, school first, de-duplicated on name + block. */
fun dayCourses(
    termCourses: List<JwxtTermCourse>,
    manualCourses: List<ManualCourse>,
    weekday: Int,
    week: Int
): List<DayCourse> =
    (coursesOnDate(termCourses, weekday, week) + manualCoursesOnDate(manualCourses, weekday, week))
        .distinctBy { it.courseName.trim() to it.sectionText.trim() }
        .sortedBy { it.sectionText.firstSectionNumber() }

private fun String.firstSectionNumber(): Int =
    Regex("\\d+").find(this)?.value?.toIntOrNull() ?: 99
