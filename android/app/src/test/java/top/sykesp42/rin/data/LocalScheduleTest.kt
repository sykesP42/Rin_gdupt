package top.sykesp42.rin.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalScheduleTest {
    private val settings = AppSettings(termStartDate = "2026-10-26")
    private val sunday = LocalDate.of(2026, 11, 1)
    private val payload = ManualPayload(
        courses = listOf(
            ManualCourse(
                id = "demo",
                name = "高等数学",
                location = "A101",
                weekday = 7,
                sectionText = "1-2",
                firstWeek = 1,
                lastWeek = 20
            )
        )
    )

    @Test
    fun `builds the current week from local courses`() {
        val overview = buildWeekOverview(payload, settings, weekOverride = 0, today = sunday, nowMinutes = 600)

        assertEquals(1, overview.week)
        assertEquals(sunday.toString(), overview.date)
        val today = overview.days.single { it.weekday == 7 }
        assertEquals("2026-11-01", today.date)
        assertEquals(1, today.events.size)
        assertEquals("08:00", today.events[0].startTime)
        assertEquals("09:40", today.events[0].endTime)
    }

    @Test
    fun `switching week keeps the same course on its weekday`() {
        val overview = buildWeekOverview(payload, settings, weekOverride = 2, today = sunday, nowMinutes = 600)

        assertEquals(2, overview.week)
        val sunday = overview.days.single { it.weekday == 7 }
        assertEquals("2026-11-08", sunday.date)
        assertEquals(1, sunday.events.size)
        assertTrue(overview.today.isEmpty())
    }

    @Test
    fun `course outside its week range is hidden`() {
        val short = payload.copy(courses = payload.courses.map { it.copy(lastWeek = 1) })
        val overview = buildWeekOverview(short, settings, weekOverride = 2, today = sunday, nowMinutes = 600)

        assertTrue(overview.days.all { it.events.isEmpty() })
    }
}
