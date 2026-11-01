package top.sykesp42.rin.data

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Local-mode MonitorApi: talks to the school's read-only attendance endpoints
 * directly (the same ones the cloud server proxies), so the phone keeps
 * monitoring and signing with no external services. Roster matching uses the
 * locally configured real name; the safety threshold stays enforced here.
 *
 * Ported from the Go server: internal/upstream/client.go (read-only calls)
 * and internal/course/service.go (roster/threshold/status rules).
 */
class LocalSchoolApi(
    private val baseUrl: String = "https://wa.gdupt.edu.cn",
    private val schoolId: String = "11656",
    private val rosterName: () -> String,
    private val threshold: () -> Int,
    private val scheduleProvider: suspend () -> ApiScheduleOverview = { ApiScheduleOverview() },
    private val roomCodeFor: suspend (String) -> String? = { null },
    private val clock: () -> Instant = Instant::now,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(java.time.Duration.ofSeconds(8)).build()
) : MonitorApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val classTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Local classroom recognition: current/next event from the schedule,
     * resolved through the learned location→code memory.
     */
    override suspend fun recommendation(): ApiRecommendation {
        val overview = runCatching { scheduleProvider() }.getOrNull()
            ?: return ApiRecommendation(status = "unavailable")
        val now = clock().atZone(shanghai)
        val minutes = now.hour * 60 + now.minute
        val today = now.toLocalDate().toString()
        // overview.today is a snapshot from fetch time and goes stale across
        // midnight; the dated days list is authoritative for "today".
        val events = (overview.days.firstOrNull { it.date == today }?.events
            ?: overview.today)
            .sortedBy { it.startTime }
        if (events.isEmpty()) return ApiRecommendation(status = "no_course_today")
        val current = events.firstOrNull {
            sectionMinutes(it.startTime) <= minutes && minutes <= sectionMinutes(it.endTime)
        }
        val next = events.firstOrNull { sectionMinutes(it.startTime) > minutes }
        val event = current ?: next ?: return ApiRecommendation(status = "day_done")
        val phase = if (event === current) "current" else "pre"
        val code = roomCodeFor(event.location) ?: return ApiRecommendation(
            status = "unknown_room", phase = phase, room = ApiRoomRef(roomName = event.location)
        )
        return ApiRecommendation(
            status = "matched", phase = phase,
            room = ApiRoomRef(roomCode = code, roomName = event.location)
        )
    }

    private fun sectionMinutes(clock: String): Int {
        val parts = clock.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    override suspend fun schedule(): ApiScheduleOverview = scheduleProvider()

    override suspend fun course(code: String): ApiCourse = withContext(Dispatchers.IO) {
        loadDetails(code).toApiCourse()
    }

    override suspend fun selfSigned(code: String): Boolean = withContext(Dispatchers.IO) {
        val details = loadDetails(code)
        val mine = details.people.filter { it.matchesMe }
        mine.size == 1 && mine.single().status in SIGNED_STATUSES
    }

    /**
     * Threshold-gated QR fetch, mirroring the server's auto path: never hands
     * out a code while below the safety threshold or outside the window.
     */
    override suspend fun qrPng(code: String): ByteArray? = withContext(Dispatchers.IO) {
        val details = loadDetails(code)
        if (!details.access.allowed || details.access.requiresConfirmation) return@withContext null
        if (details.signId.isBlank()) return@withContext null
        val png = fetchQr(details.signId) ?: return@withContext null
        png.takeIf(::looksLikePng)
    }

    // ---- ported school rules -------------------------------------------------

    private class LocalDetails(
        val roomCode: String,
        val courseName: String = "",
        val location: String = "",
        val teacherNames: List<String> = emptyList(),
        val sections: String = "",
        val signId: String = "",
        val status: String = "",
        val effectiveSignEndAt: Instant? = null,
        val qrRefreshSeconds: Int = 0,
        val qrType: String = "",
        val people: List<LocalPerson> = emptyList(),
        val roster: ApiRoster = ApiRoster(),
        val access: ApiSignAccess = ApiSignAccess(reason = "no_activity")
    )

    private class LocalPerson(
        val name: String,
        val status: String,
        val identityTeacher: Boolean,
        val signTime: String,
        val signMode: String,
        val isLate: Boolean
    ) {
        var matchesMe: Boolean = false
    }

    private fun loadDetails(roomCode: String): LocalDetails {
        val now = clock()
        val upstreamCourse = fetchCurrentCourse(roomCode, now)
        val base = LocalDetails(
            roomCode = roomCode,
            courseName = upstreamCourse?.courseName.orEmpty(),
            location = upstreamCourse?.classLocation.orEmpty(),
            teacherNames = upstreamCourse?.teacherNameList.orEmpty().mapNotNull { it.teacherName },
            sections = upstreamCourse?.sections.orEmpty(),
            signId = upstreamCourse?.signId.orEmpty(),
            access = ApiSignAccess(reason = "no_activity", rosterThreshold = threshold())
        )
        if (base.signId.isBlank()) return base

        val activity = fetchActivity(base.signId) ?: return base
        val start = activity.signStartTime.toLongOrNull() ?: return base
        val end = activity.signEndTime.toLongOrNull() ?: return base
        val effectiveEnd = Instant.ofEpochMilli(end).minusSeconds(EFFECTIVE_END_MARGIN_SECONDS)
        val status = when {
            now.isBefore(Instant.ofEpochMilli(start)) -> "NOT_STARTED"
            !now.isBefore(effectiveEnd) -> "END"
            else -> "STARTED"
        }
        val withActivity = LocalDetails(
            roomCode = roomCode,
            courseName = base.courseName,
            location = base.location,
            teacherNames = base.teacherNames,
            sections = base.sections,
            signId = base.signId,
            status = status,
            effectiveSignEndAt = effectiveEnd,
            qrRefreshSeconds = activity.qrCodeFreshTime,
            qrType = if (activity.signQRcodeTypeEnum == "1") "STATIC" else "DYNAMIC",
            access = ApiSignAccess(reason = "activity_$status", rosterThreshold = threshold())
        )
        val people: List<LocalPerson>
        if (status == "STARTED") {
            people = fetchPeople(base.signId).map { it.toLocalPerson() }
        } else {
            // Before the window / after it, the roster is a preview. A failure
            // here must not turn a NOT_STARTED poll into an error page.
            people = runCatching { fetchPeople(base.signId) }
                .getOrElse { emptyList() }
                .map { it.toLocalPerson() }
        }
        val matches = people.count { it.matchesMe }
        val signedCount = people.count { !it.identityTeacher && it.status in SIGNED_STATUSES }
        val currentThreshold = threshold()
        val roster = ApiRoster(
            signed = people
                .filter { !it.identityTeacher && it.status in SIGNED_STATUSES }
                .map { it.toApiPerson() },
            unsigned = people
                .filter { !it.identityTeacher && it.status !in SIGNED_STATUSES }
                .map { it.toApiPerson() },
            excludedCount = people.count { it.identityTeacher },
            people = people.map { it.toApiPerson() }
        )
        val access = when {
            status != "STARTED" -> ApiSignAccess(
                reason = "activity_$status",
                signedCount = signedCount,
                rosterCount = people.size,
                rosterThreshold = currentThreshold
            )
            matches == 1 -> {
                val requiresConfirmation = currentThreshold > 0 && signedCount < currentThreshold
                ApiSignAccess(
                    allowed = true,
                    reason = if (requiresConfirmation) "roster_below_threshold" else "roster_match",
                    requiresConfirmation = requiresConfirmation,
                    signedCount = signedCount,
                    rosterCount = people.size,
                    rosterThreshold = currentThreshold
                )
            }
            matches == 0 -> ApiSignAccess(
                allowed = false, reason = "not_in_roster",
                signedCount = signedCount, rosterCount = people.size, rosterThreshold = currentThreshold
            )
            else -> ApiSignAccess(
                allowed = false, reason = "ambiguous_name",
                signedCount = signedCount, rosterCount = people.size, rosterThreshold = currentThreshold
            )
        }
        return LocalDetails(
            roomCode = roomCode, courseName = base.courseName, location = base.location,
            teacherNames = base.teacherNames, sections = base.sections, signId = base.signId,
            status = status, effectiveSignEndAt = effectiveEnd,
            qrRefreshSeconds = withActivity.qrRefreshSeconds, qrType = withActivity.qrType,
            people = people, roster = roster, access = access
        )
    }

    private fun UpPerson.toLocalPerson(): LocalPerson {
        val normalizedMe = normalizeName(rosterName())
        return LocalPerson(
            name = userName,
            status = userSignStatusEnum,
            identityTeacher = userIdentity == "1",
            signTime = signTime,
            signMode = signModeEnum,
            isLate = isLate
        ).apply {
            matchesMe = normalizedMe != null && normalizeName(userName) == normalizedMe
        }
    }

    private fun LocalPerson.toApiPerson(): ApiRosterPerson = ApiRosterPerson(
        name = name,
        status = status,
        isCurrentUser = matchesMe,
        signed = status in SIGNED_STATUSES,
        signTime = signTime,
        signMode = signMode,
        isLate = isLate,
        isTeacher = identityTeacher
    )

    private fun LocalDetails.toApiCourse(): ApiCourse = ApiCourse(
        roomCode = roomCode,
        courseName = courseName,
        location = location,
        teacherNames = teacherNames,
        sections = sections,
        activity = if (signId.isBlank()) null else ApiActivity(
            signId = signId,
            status = status,
            effectiveSignEndAt = effectiveSignEndAt?.toString().orEmpty(),
            qrRefreshIntervalSeconds = qrRefreshSeconds,
            qrType = qrType
        ),
        signAccess = access,
        roster = roster,
        serverTime = clock().toString()
    )

    /** NFC-fold whitespace then lowercase, mirroring the server's rule. */
    private fun normalizeName(raw: String): String? {
        // Mirrors the Go server (auth.NormalizeRealName): NFC, collapse every
        // Unicode space (Go's unicode.IsSpace includes NBSP and U+3000), reject
        // 2..64 code points and control characters, then lowercase. Java's
        // ASCII-only \s would leave an invisible NBSP in a roster row and
        // wrongly report "not in roster".
        val name = buildString {
            var pendingSpace = false
            for (ch in Normalizer.normalize(raw, Normalizer.Form.NFC)) {
                if (ch.isWhitespace() || Character.isSpaceChar(ch)) {
                    if (isNotEmpty()) pendingSpace = true
                } else {
                    if (pendingSpace) {
                        append(' ')
                        pendingSpace = false
                    }
                    append(ch)
                }
            }
        }
        val length = name.codePointCount(0, name.length)
        if (length < 2 || length > 64) return null
        if (name.any { Character.isISOControl(it) }) return null
        return name.lowercase()
    }

    // ---- upstream read-only calls -------------------------------------------

    private fun fetchCurrentCourse(roomCode: String, now: Instant): UpCourse? {
        val classTime = ZonedDateTime.ofInstant(now, shanghai).format(classTimeFormat)
        val envelope = getEnvelope(
            "/v1/classTimeTables?classTime=${urlEncode(classTime)}&roomCode=${urlEncode(roomCode)}"
        )
        if (envelope.code != 200) throw ApiError(502, "UPSTREAM_${envelope.code}", envelope.message)
        val data = envelope.data ?: return null
        val courses = json.decodeFromJsonElement(CourseListSerializer, data)
        return courses.firstOrNull()
    }

    private fun fetchActivity(signId: String): UpActivity? {
        val envelope = getEnvelope("/v1/active/sign/${urlEncode(signId)}")
        if (envelope.code != 200) throw ApiError(502, "UPSTREAM_${envelope.code}", envelope.message)
        val data = envelope.data ?: return null
        return json.decodeFromJsonElement(UpActivity.serializer(), data)
    }

    private fun fetchPeople(signId: String): List<UpPerson> {
        val envelope = getEnvelope("/v1/active/sign/${urlEncode(signId)}/users")
        if (envelope.code != 200) throw ApiError(502, "UPSTREAM_${envelope.code}", envelope.message)
        val data = envelope.data ?: return emptyList()
        return json.decodeFromJsonElement(PersonListSerializer, data)
    }

    private fun fetchQr(signId: String): ByteArray? {
        val envelope = getEnvelope("/v1/active/sign/${urlEncode(signId)}/getQRCodeImg")
        if (envelope.code != 200) throw ApiError(502, "UPSTREAM_${envelope.code}", envelope.message)
        val encoded = envelope.data?.jsonObject?.get("qrcodeImg")?.jsonPrimitive?.content ?: return null
        return runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
    }

    private fun getEnvelope(path: String): Envelope {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .header("schoolId", schoolId)
            .header("token", "")
            .header("userId", "")
            .header("User-Agent", "SuiSign/0.1.0 (android; local-mode)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiError(502, "UPSTREAM_HTTP_${response.code}", "学校接口不可达")
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: throw ApiError(502, "UPSTREAM_PARSE", "学校接口返回异常")
            return Envelope(
                code = root["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                message = root["message"]?.jsonPrimitive?.contentOrNull ?: "",
                data = root["data"]
            )
        }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun looksLikePng(bytes: ByteArray): Boolean =
        bytes.size > 100 && bytes[0] == PNG_MAGIC_0 && bytes[1] == PNG_MAGIC_1

    // ---- upstream DTOs ------------------------------------------------------

    @Serializable
    private data class Envelope(val code: Int = -1, val message: String = "", val data: JsonElement? = null)

    @Serializable
    internal data class UpTeacher(val teacherName: String = "")

    @Serializable
    internal data class UpCourse(
        val signId: String = "",
        val courseName: String = "",
        val classLocation: String = "",
        val teacherNameList: List<UpTeacher> = emptyList(),
        val sections: String = ""
    )

    @Serializable
    internal data class UpActivity(
        val signId: String = "",
        val signStartTime: String = "",
        val signEndTime: String = "",
        val qrCodeFreshTime: Int = 0,
        val signQRcodeTypeEnum: String = ""
    )

    @Serializable
    internal data class UpPerson(
        val userName: String = "",
        val userSignStatusEnum: String = "",
        val userIdentity: String = "",
        val signTime: String = "",
        val signModeEnum: String = "",
        val isLate: Boolean = false
    )

    companion object {
        private val SIGNED_STATUSES = setOf("1", "2")
        private const val EFFECTIVE_END_MARGIN_SECONDS = 30L
        private val PNG_MAGIC_0: Byte = 0x89.toByte()
        private val PNG_MAGIC_1: Byte = 0x50.toByte()
        private val CourseListSerializer =
            kotlinx.serialization.builtins.ListSerializer(UpCourse.serializer())
        private val PersonListSerializer =
            kotlinx.serialization.builtins.ListSerializer(UpPerson.serializer())
    }
}
