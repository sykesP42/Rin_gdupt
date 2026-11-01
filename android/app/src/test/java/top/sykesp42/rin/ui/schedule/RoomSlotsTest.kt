package top.sykesp42.rin.ui.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomSlotsTest {

    @Test
    fun `slot lookup hits the block that contains the minute`() {
        assertEquals("0102", roomSlotAt(8 * 60 + 20)?.key)
        assertEquals("0304", roomSlotAt(10 * 60 + 30)?.key)
        assertEquals("0506", roomSlotAt(15 * 60 + 50)?.key)
        assertEquals("0910", roomSlotAt(20 * 60)?.key)
    }

    @Test
    fun `between blocks the next block is answered`() {
        // 09:45 sits in the 1-2 / 3-4 break; the honest answer is the 3-4 block.
        assertEquals("0304", roomSlotAt(9 * 60 + 45)?.key)
        // 12:30 is lunch; the next block is the afternoon one.
        assertEquals("0506", roomSlotAt(12 * 60 + 30)?.key)
        // 18:30 is dinner; the evening block is next.
        assertEquals("0910", roomSlotAt(18 * 60 + 30)?.key)
    }

    @Test
    fun `after the last block there is no slot`() {
        assertNull(roomSlotAt(22 * 60))
    }

    @Test
    fun `every slot exposes a printable line`() {
        assertEquals("第 3-4 节 · 10:00-11:40", roomSlotLine(ROOM_SLOTS[1]))
    }

    @Test
    fun `room names compare ignoring case spaces and dashes`() {
        val free = setOf("二教B-308", "B201", "光华1号楼-201")
        assertTrue(isRoomFree(free, "二教b308"))
        assertTrue(isRoomFree(free, "二教B-308"))
        assertTrue(isRoomFree(free, " B201 "))
        assertTrue(isRoomFree(free, "光华1号楼201"))
        assertFalse(isRoomFree(free, "二教B-309"))
    }

    @Test
    fun `blank room name never matches`() {
        assertFalse(isRoomFree(setOf("B201"), "   "))
    }

    @Test
    fun `week number follows the school clock across the sunday boundary`() {
        val sunday = java.time.LocalDate.of(2026, 11, 1)
        val monday = sunday.plusDays(1)
        assertEquals(9, weekNumberFor(sunday, currentWeek = 9, today = sunday))
        assertEquals(10, weekNumberFor(monday, currentWeek = 9, today = sunday))
        assertEquals(8, weekNumberFor(sunday.minusWeeks(1), currentWeek = 9, today = sunday))
    }

    @Test
    fun `week number stays inside the teaching term`() {
        val today = java.time.LocalDate.of(2026, 11, 1)
        assertEquals(1, weekNumberFor(today.minusWeeks(20), currentWeek = 2, today = today))
        assertEquals(30, weekNumberFor(today.plusWeeks(40), currentWeek = 2, today = today))
    }
}
