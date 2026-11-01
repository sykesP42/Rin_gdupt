package top.sykesp42.rin.ui.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import top.sykesp42.rin.data.ManualCourse

class SemesterReportTest {
    @Test
    fun `two section meetings count one session but two periods`() {
        val course = ManualCourse(
            id = "demo",
            name = "高等数学",
            weekday = 1,
            sectionText = "1-2",
            firstWeek = 1,
            lastWeek = 20
        )
        val report = buildSemesterReport(
            termLabel = "本学期",
            currentWeek = 1,
            slots = semesterSlots(emptyList(), manualCourses = listOf(course))
        )

        assertEquals("20", report.stats.first { it.label == "学期课次" }.value)
        assertEquals("40", report.stats.first { it.label == "总课时" }.value)
        assertEquals("2", report.stats.first { it.label == "周均课时" }.value)
    }
}
