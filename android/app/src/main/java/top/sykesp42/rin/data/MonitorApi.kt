package top.sykesp42.rin.data

/** The read-only surface the monitor engine needs; ApiClient implements it. */
interface MonitorApi {
    suspend fun recommendation(): ApiRecommendation
    suspend fun course(code: String): ApiCourse
    suspend fun selfSigned(code: String): Boolean
    suspend fun schedule(): ApiScheduleOverview

    /** Full roster when the backend can provide it; empty otherwise. */
    suspend fun roster(code: String): ApiRoster = ApiRoster()

    /** Fresh sign QR PNG; null when the safety gates are not met. */
    suspend fun qrPng(code: String): ByteArray? = null
}
