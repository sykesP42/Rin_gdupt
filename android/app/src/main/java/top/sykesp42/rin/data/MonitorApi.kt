package top.sykesp42.rin.data

/**
 * Read surface kept from the full client, minus every sign-side call: the
 * room recommendation and the cached timetable overview.
 */
interface MonitorApi {
    suspend fun recommendation(): ApiRecommendation
    suspend fun schedule(): ApiScheduleOverview
}
