package top.sky2333.rin.ui.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionTextTest {
    @Test
    fun `parses ranges, lists and packed pairs`() {
        assertEquals(listOf(1, 2), parseSections("1-2"))
        assertEquals(listOf(1, 2), parseSections("0102"))
        assertEquals(listOf(3, 4, 9), parseSections("3-4, 9"))
    }

    @Test
    fun `ignores malformed and out of range tokens`() {
        assertEquals(emptyList(), parseSections("0, 11, bad, 1-"))
        assertEquals(listOf(10), parseSections("10"))
    }

    @Test
    fun `sectionStart uses the campus time table`() {
        assertEquals("08:00", sectionStart("1-2"))
        assertEquals("14:30", sectionStart("5-6"))
        assertEquals("", sectionStart(""))
    }
}
