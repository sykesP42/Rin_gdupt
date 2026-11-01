package top.sky2333.rin.data

import kotlinx.serialization.Serializable

@Serializable
data class ApiScheduleEvent(
    val courseName: String = "",
    val location: String = "",
    val sectionText: String = "",
    val startTime: String = "",
    val endTime: String = ""
)

@Serializable
data class ApiScheduleDay(
    val date: String = "",
    val weekday: Int = 0,
    val events: List<ApiScheduleEvent> = emptyList()
)

@Serializable
data class ApiScheduleOverview(
    val term: String = "",
    val week: Int = 0,
    val date: String = "",
    val current: ApiScheduleEvent? = null,
    val next: ApiScheduleEvent? = null,
    val today: List<ApiScheduleEvent> = emptyList(),
    val days: List<ApiScheduleDay> = emptyList()
)
