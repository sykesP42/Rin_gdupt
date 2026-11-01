package top.sykesp42.rin.ui.history

import java.time.LocalDate
import java.time.ZoneId
import top.sykesp42.rin.data.HistoryEvent

data class HistoryDay(val date: LocalDate, val events: List<HistoryEvent>)

/** Groups history events by local calendar day, newest day first and newest event first. */
fun groupHistoryByDay(
    events: List<HistoryEvent>,
    zone: ZoneId = ZoneId.systemDefault()
): List<HistoryDay> =
    events.groupBy { it.at.atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, list) -> HistoryDay(date, list.sortedByDescending { it.at }) }
