package top.sykesp42.rin.data

/**
 * Read surface kept from the full client, minus every sign-side call: room
 * recommendation, course detail and the cached timetable overview.
 */
interface MonitorApi {
    suspend fun recommendation(): ApiRecommendation
    suspend fun course(code: String): ApiCourse
    suspend fun schedule(): ApiScheduleOverview
}
