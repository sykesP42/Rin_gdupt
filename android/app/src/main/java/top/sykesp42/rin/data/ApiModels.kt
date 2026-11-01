package top.sykesp42.rin.data

import kotlinx.serialization.Serializable

@Serializable
data class ApiActivity(
    val signId: String = "",
    val status: String = "",
    val effectiveSignEndAt: String = "",
    val qrRefreshIntervalSeconds: Int = 0,
    val qrType: String = ""
)

@Serializable
data class ApiSignAccess(
    val allowed: Boolean = false,
    val reason: String = "",
    val requiresConfirmation: Boolean = false,
    val signedCount: Int = 0,
    val rosterCount: Int = 0,
    val rosterThreshold: Int = 0
)

@Serializable
data class ApiCourse(
    val roomCode: String = "",
    val courseName: String = "",
    val location: String = "",
    val teacherNames: List<String> = emptyList(),
    val sections: String = "",
    val activity: ApiActivity? = null,
    val signAccess: ApiSignAccess = ApiSignAccess(),
    val roster: ApiRoster = ApiRoster(),
    val serverTime: String = ""
)

@Serializable
data class ApiRoomRef(val roomCode: String = "", val roomName: String = "")

@Serializable
data class ApiRecommendation(
    val status: String = "",
    val phase: String = "",
    val room: ApiRoomRef? = null
)

@Serializable
data class ApiRosterPerson(
    val name: String = "",
    val status: String = "",
    val isCurrentUser: Boolean = false,
    val signed: Boolean = false,
    val signTime: String = "",
    val signMode: String = "",
    val isLate: Boolean = false,
    val isTeacher: Boolean = false
)

@Serializable
data class ApiRoster(
    val signed: List<ApiRosterPerson> = emptyList(),
    val unsigned: List<ApiRosterPerson> = emptyList(),
    val excludedCount: Int = 0,
    /** Every row incl. teachers/other statuses, for the detailed sheet. */
    val people: List<ApiRosterPerson> = emptyList()
)

fun ApiRoster.hasPeople(): Boolean =
    signed.isNotEmpty() || unsigned.isNotEmpty() || people.isNotEmpty()

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

@Serializable
data class ApiRoom(
    val roomCode: String = "",
    val roomName: String = "",
    val lastCourseName: String = "",
    val campus: String = "",
    val building: String = "",
    val capacity: Int = 0,
    val suggestedCode: String = ""
)

@Serializable
data class ApiRoomList(
    val items: List<ApiRoom> = emptyList()
)

@Serializable
data class ApiSignSettings(
    val rosterThreshold: Int = 0,
    val minThreshold: Int = 0,
    val maxThreshold: Int = 30
)

@Serializable
data class ApiJwxtAccount(
    val configured: Boolean = false
)
/** The app's own version, used for cache stamps and share watermarks. */
fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "0.0.0" }
