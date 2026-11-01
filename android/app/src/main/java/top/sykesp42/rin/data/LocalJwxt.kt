package top.sykesp42.rin.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.jsoup.Jsoup

/**
 * Local JWXT (正方教务) client. Supports two session sources: an embedded
 * WebView login (captcha/WebVPN) or programmatic login with stored
 * credentials (works whenever the school does not require a captcha).
 * Ported from the Go server: internal/jwxt/service.go + parser.go.
 */
class LocalJwxtApi(
    private val baseUrl: String,
    private val cookieHeader: () -> String?,
    // JWXT/WebVPN occasionally rotates a cookie on a normal request. Persist
    // that rotation so the next process start does not fall back to an older
    // ticket and trigger an unnecessary login.
    private val onCookieUpdate: (String) -> Unit = {},
    // School clock: overview()'s current/next minutes must match the class
    // windows even when the device timezone is wrong.
    private val clock: LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .readTimeout(java.time.Duration.ofSeconds(20))
        .dns(PreferIpv4Dns)
        .build()
) {
    class JwxtSessionExpired(message: String = "教务登录已过期") : Exception(message)
    class JwxtCaptchaRequired : Exception("教务需要验证码，请用网页登录一次")

    data class Credentials(val account: String, val password: String)

    /**
     * Programmatic login (read-only flow): fresh session → POST credentials.
     * Returns the raw Cookie header to persist. Throws [JwxtCaptchaRequired]
     * when the school asks for a verification code this time.
     */
    fun login(credentials: Credentials): String {
        val sessionResponse = client.newCall(
            Request.Builder().url("$baseUrl/").header("User-Agent", USER_AGENT).get().build()
        ).execute()
        sessionResponse.use {
            val sessionId = it.headers("Set-Cookie")
                .firstOrNull { cookie -> cookie.startsWith("JSESSIONID=") }
                ?.substringAfter("=")?.substringBefore(";")
                ?: throw JwxtSessionExpired("教务会话建立失败")
            val needVerify = runCatching {
                client.newCall(
                    Request.Builder()
                        .url("$baseUrl/login!isNeedVerify.action")
                        .header("Cookie", "JSESSIONID=$sessionId")
                        .header("User-Agent", USER_AGENT)
                        .get().build()
                ).execute().use { response -> response.body?.string()?.trim() }
            }.getOrNull()
            if (needVerify == "1") throw JwxtCaptchaRequired()
            val encoded = java.util.Base64.getEncoder()
                .encodeToString(credentials.password.toByteArray(Charsets.UTF_8))
            val form = FormBody.Builder()
                .add("account", credentials.account)
                .add("pwd", encoded)
                .add("verifycode", "")
                .build()
            val loginResponse = client.newCall(
                Request.Builder()
                    .url("$baseUrl/login!doLogin.action")
                    .header("Cookie", "JSESSIONID=$sessionId")
                    .header("User-Agent", USER_AGENT)
                    .post(form)
                    .build()
            ).execute()
            loginResponse.use {
                val body = it.body?.string().orEmpty()
                if (body.contains("\"status\":\"y\"") || body.contains("\"status\": \"y\"")) {
                    return "JSESSIONID=$sessionId"
                }
                throw JwxtSessionExpired("教务账号或密码不正确")
            }
        }
    }

    /**
     * One lightweight read validates the current cookie and lets the server
     * rotate it; used by the daily background keep-alive, not by every page.
     */
    suspend fun touchSession() = withContext(Dispatchers.IO) {
        fetchContext()
    }

    // ---- new data endpoints (verified read-only, 2026-10-24) ----------------

    /** Course grades; xskccjxx!getDataList.action. Empty term = all terms. */
    suspend fun fetchGrades(term: String = ""): List<JwxtGrade> = withContext(Dispatchers.IO) {
        val query = if (term.isBlank()) "" else "&xnxqdm=$term"
        val body = get("$baseUrl/xskccjxx!getDataList.action?page=1&rows=200$query")
        val root = json.parseToJsonElement(body).jsonObject
        val rows = root["rows"]?.jsonArray ?: return@withContext emptyList()
        rows.mapNotNull { row ->
            val record = row as? JsonObject ?: return@mapNotNull null
            JwxtGrade(
                term = record.stringField("xnxqmc") ?: "",
                courseName = record.stringField("kcmc") ?: return@mapNotNull null,
                score = record.stringField("zcj") ?: "",
                gradePoint = record.stringField("cjjd")?.toDoubleOrNull(),
                credits = record.stringField("xf")?.toDoubleOrNull(),
                category = record.stringField("kcdlmc") ?: "",
                studyMode = record.stringField("xdfsmc") ?: "",
                active = record.stringField("isactive") != "0",
                recordId = record.stringField("cjdm") ?: ""
            )
        }
    }

    /** Exam arrangements; arrangeexam!getMyExamInfo.action (often empty mid-term). */
    suspend fun fetchExams(): List<JwxtExam> = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/arrangeexam!getMyExamInfo.action")
        val root = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()
            ?: return@withContext emptyList()
        root.mapNotNull { row ->
            val record = row as? JsonObject ?: return@mapNotNull null
            JwxtExam(
                courseName = record.stringField("kcmc")
                    ?: record.stringField("kcmcxs")
                    ?: return@mapNotNull null,
                time = record.stringField("kssj") ?: record.stringField("jssj") ?: "",
                location = record.stringField("kscd") ?: record.stringField("jsmc") ?: "",
                seat = record.stringField("zwh") ?: "",
                note = record.stringField("kssm") ?: ""
            )
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val shanghai = ZoneId.of("Asia/Shanghai")

    /** One course slot on one weekday inside a week. */
    data class LocalEvent(
        val courseName: String,
        val teacher: String = "",
        val location: String = "",
        val weekday: Int,
        val sectionText: String,
        val startTime: String,
        val endTime: String
    )

    /** GET /xsgrkbcx!getXsgrbkList.action → terms/weeks selects. */
    internal fun parseContext(body: String): JwxtContext {
        val document = Jsoup.parse(body)
        val terms = selectOptions(document, "xnxqdm")
        val weeks = selectOptions(document, "zc")
        if (terms.isEmpty()) throw IllegalStateException("教务页面解析失败")
        // The zc select is filled by JS on the real page; zf also renders the
        // current week and max week into JS literals when it does.
        val jsWeek = WEEK_LITERAL.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val jsMaxWeek = WEEK_LOOP.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val maxWeek = weeks.mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..30 }.maxOrNull()
            ?: jsMaxWeek?.takeIf { it in 1..30 }
            ?: 20
        return JwxtContext(
            terms = terms,
            currentTerm = terms.firstOrNull { it.selected }?.value ?: terms.first().value,
            currentTermLabel = terms.firstOrNull { it.selected }?.label ?: terms.first().label,
            currentWeek = weeks.firstOrNull { it.selected }?.value?.toIntOrNull()
                ?: jsWeek?.takeIf { it in 1..maxWeek }
                ?: 1,
            maxWeek = maxWeek
        )
    }

    private fun selectOptions(document: org.jsoup.nodes.Document, selectName: String): List<JwxtOption> {
        val select = document.selectFirst("select[name=$selectName]") ?: return emptyList()
        return select.select("option").mapNotNull { option ->
            val value = option.attr("value").trim()
            if (value.isEmpty()) null
            else JwxtOption(value = value, label = option.text().trim(), selected = option.hasAttr("selected"))
        }
    }

    /**
     * GET /xsgrkbcx!getKbRq.action → JSON [events, dates]; expands weekday +
     * section records into dated events for the week starting [weekStart].
     */
    internal fun parseWeek(body: String, weekStart: LocalDate): List<List<LocalEvent>> {
        val root = json.parseToJsonElement(body).jsonArray
        if (root.size != 2) throw IllegalStateException("教务课表返回异常")
        val records = root[0].jsonArray.mapNotNull { it as? JsonObject }
        val days = List(7) { mutableListOf<LocalEvent>() }
        for (record in records) {
            val event = normalizeEvent(record, weekStart) ?: continue
            days[event.weekday - 1].add(event)
        }
        days.forEach { events -> events.sortBy { sectionStartMinutes(it.startTime) } }
        return days
    }

    private fun normalizeEvent(record: JsonObject, weekStart: LocalDate): LocalEvent? {
        val weekday = parseWeekday(record.stringField("xq") ?: "")
        if (weekday !in 1..7) return null
        val sections = parseSections(
            record.stringField("jcdm")
                ?: record.stringField("jcdm2")
                ?: record.stringField("jc")
                ?: ""
        )
        if (sections.isEmpty()) return null
        val first = sectionTimes[sections.first()]
        val last = sectionTimes[sections.last()]
        if (first == null || last == null) return null
        return LocalEvent(
            courseName = record.stringField("kcmc")
                ?: record.stringField("kcmcxs")
                ?: "未命名课程",
            teacher = record.stringField("teaxms") ?: record.stringField("jsxm") ?: "",
            location = record.stringField("jxcdmc")
                ?: record.stringField("jxcdmcs")
                ?: record.stringField("jxcdmc2")
                ?: "",
            weekday = weekday,
            sectionText = sections.joinToString(","),
            startTime = first.first,
            endTime = last.second
        )
    }

    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    internal fun parseWeekday(value: String): Int = when (value.trim()) {
        "一", "星期一", "周一", "1" -> 1
        "二", "星期二", "周二", "2" -> 2
        "三", "星期三", "周三", "3" -> 3
        "四", "星期四", "周四", "4" -> 4
        "五", "星期五", "周五", "5" -> 5
        "六", "星期六", "周六", "6" -> 6
        "日", "天", "星期日", "星期天", "周日", "7" -> 7
        else -> -1
    }

    /** Mirrors the Go section parser, including packed forms like "0102". */
    internal fun parseSections(value: String): List<Int> {
        val matches = Regex("\\d+").findAll(value).map { it.value }.toList()
        val seen = mutableSetOf<Int>()
        val sections = mutableListOf<Int>()
        if (matches.size == 1 && matches[0].length > 2 && matches[0].length % 2 == 0) {
            val packed = matches[0]
            var index = 0
            while (index < packed.length) {
                addSection(packed.substring(index, index + 2), seen, sections)
                index += 2
            }
        } else {
            matches.forEach { addSection(it, seen, sections) }
        }
        return sections.sorted()
    }

    private fun addSection(value: String, seen: MutableSet<Int>, sections: MutableList<Int>) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed !in 1..10 || !seen.add(parsed)) return
        sections.add(parsed)
    }

    internal fun sectionStartMinutes(clock: String): Int {
        val parts = clock.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return 0
        return hour * 60 + minute
    }

    /** Full overview for [date] (Shanghai), mirroring the server's shape. */
    suspend fun overview(date: LocalDate = LocalDate.now(shanghai), weekOverride: Int = 0): ApiScheduleOverview =
        withContext(Dispatchers.IO) {
            val context = fetchContext()
            val today = date
            // Authoritative calendar first; JS-week derivation as fallback.
            val calendarMonday = runCatching {
                parseCalendarFirstMonday(get("$baseUrl/xlxx!getXlxx.action"))
            }.getOrNull()
            val firstWeekMonday = calendarMonday
                ?: today.with(DayOfWeek.MONDAY).minusWeeks((context.currentWeek - 1).toLong())
            var week = if (weekOverride > 0) weekOverride else weekForDate(firstWeekMonday, today)
            if (week < 1) week = 1
            if (week > context.maxWeek) week = context.maxWeek
            val weekStart = firstWeekMonday.plusWeeks((week - 1).toLong())
            val days = fetchWeek(context.currentTerm, week, weekStart)
            val todayEvents = days.getOrNull(today.dayOfWeek.value - 1).orEmpty()
            val minutes = clock.hour * 60 + clock.minute
            var current: ApiScheduleEvent? = null
            var next: ApiScheduleEvent? = null
            for (event in todayEvents) {
                val start = sectionStartMinutes(event.startTime)
                val end = sectionStartMinutes(event.endTime)
                if (current == null && start <= minutes && minutes <= end) current = event.toApi(null)
                if (next == null && end > minutes && start > minutes) next = event.toApi(null)
            }
            ApiScheduleOverview(
                term = context.currentTermLabel,
                week = week,
                date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                current = current,
                next = next,
                today = todayEvents.map { it.toApi(null) },
                days = days.mapIndexed { index, events ->
                    ApiScheduleDay(
                        date = weekStart.plusDays(index.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE),
                        weekday = index + 1,
                        events = events.map { it.toApi(null) }
                    )
                }
            )
        }

    private fun weekForDate(firstWeekMonday: LocalDate, day: LocalDate): Int {
        val days = java.time.temporal.ChronoUnit.DAYS.between(firstWeekMonday, day).toInt()
        return days / 7 + 1
    }

    private fun LocalEvent.toApi(@Suppress("UNUSED_PARAMETER") unused: Any?): ApiScheduleEvent =
        ApiScheduleEvent(
            courseName = courseName,
            location = location,
            sectionText = sectionText,
            startTime = startTime,
            endTime = endTime
        )

    /**
     * Free classrooms for one weekday+week+section pair
     * (teajssqxx!getPlJsDataList.action; read-only query).
     */
    suspend fun fetchFreeClassrooms(weekday: Int, week: Int, sections: String): List<JwxtFreeRoom> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("?primarySort=jxcddm%20desc&page=1&rows=200")
                append("&xnxqdm=").append(fetchContext().currentTerm)
                append("&xq=").append(freeRoomApiWeekday(weekday))
                append("&zc=").append(week)
                if (sections.isNotBlank()) append("&jc=").append(urlEncodeSections(sections))
            }
            val body = get("$baseUrl/teajssqxx!getPlJsDataList.action$query")
            parseClassroomPage(body).rooms.map { row ->
                JwxtFreeRoom(
                    roomCode = row.code,
                    roomName = row.name,
                    campus = row.campus,
                    building = row.building,
                    capacity = row.capacity,
                    area = row.area
                )
            }.sortedWith(compareBy({ it.campus }, { it.building }, { it.roomName }))
        }

    /**
     * Classroom catalog: current-week free-room rows supply verified four-digit
     * codes; the room-picker page supplies the remaining names. Rooms without a
     * verified code stay searchable but are marked “编号待确认”.
     */
    suspend fun fetchClassroomCatalog(): List<JwxtClassroom> = withContext(Dispatchers.IO) {
        val context = fetchContext()
        val all = linkedMapOf<String, JwxtClassroom>()
        val weeks = context.currentWeek.toString()
        // The free-room endpoint only answers for one teaching week. Current-week
        // rows provide verified jxcdbh codes; the picker page below adds names
        // that are busy all week but do not expose a verified code.
        for (apiWeekday in 1..7) {
            var page = 1
            while (page <= 10) {
                val query = "?primarySort=jxcddm%20desc&page=$page&rows=500" +
                    "&xnxqdm=${context.currentTerm}&xq=$apiWeekday&zc=$weeks&isqy=1"
                val parsed = parseClassroomPage(get("$baseUrl/teajssqxx!getPlJsDataList.action$query"))
                parsed.rooms.forEach { mergeRoom(all, it) }
                if (parsed.rooms.size < 500) break
                page += 1
            }
        }
        // The room picker page maps names that never appeared as free. Its
        // option values are internal ids, NOT room codes, so they stay blank
        // until a jxcdbh is seen.
        val page = runCatching { get("$baseUrl/teajssqxx!teakxjspllist.action") }.getOrNull()
        if (page != null) {
            val knownBuildings = all.values.map { it.building to it.campus }.filter { it.first.isNotBlank() }
            parseRoomOptions(page).forEach { candidate ->
                val exists = all.values.any {
                    it.name == candidate.name &&
                        (it.code.isNotBlank() || (candidate.campus.isNotBlank() && it.campus == candidate.campus))
                }
                if (!exists) {
                    all[candidate.key()] = candidate.copy(
                        campus = guessCampus(candidate.name, knownBuildings),
                        building = guessBuilding(candidate.name, knownBuildings)
                    )
                }
            }
        }
        // Room scan: the grid without the "free only" filter lists every room
        // with its jxcdbh, including rooms that are busy all week (exactly the
        // ones the grid needs). The grid also accepts the whole term's weeks.
        runCatching {
            val scanWeeks = catalogWeekRange(context.maxWeek)
            var scanPage = 1
            while (scanPage <= 5) {
                val query = "?primarySort=jxcddm%20desc&page=$scanPage&rows=1000" +
                    "&xnxqdm=${context.currentTerm}&xq=1&zc=$scanWeeks&isqy="
                val parsed = parseClassroomPage(get("$baseUrl/teajssqxx!getPlJsDataList.action$query"))
                parsed.rooms.forEach { mergeRoom(all, it) }
                if (parsed.rooms.size < 1000) break
                scanPage += 1
            }
        }
        all.values.sortedWith(
            compareBy(
                { it.campus.ifBlank { "\uFFFF" } },
                { it.building.ifBlank { "\uFFFF" } },
                { it.name }
            )
        )
    }

    /**
     * Keep one row per room, and let a verified code upgrade a name-only row
     * instead of duplicating it.
     */
    internal fun mergeRoom(all: MutableMap<String, JwxtClassroom>, room: JwxtClassroom) {
        if (room.code.isBlank()) {
            all.putIfAbsent(room.key(), room)
            return
        }
        val existing = all.values.firstOrNull { it.name == room.name }
        if (existing == null) {
            all[room.key()] = room
        } else {
            all[existing.key()] = existing.copy(
                code = room.code,
                capacity = maxOf(existing.capacity, room.capacity),
                area = existing.area.ifBlank { room.area }
            )
        }
    }

    internal fun parseRoomOptions(html: String): List<JwxtClassroom> {
        val select = Regex(
            """<select[^>]*id=["']jxcddm["'][^>]*>(.*?)</select>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex(
            """<option\s+value=["']([^"']*)["'][^>]*>(.*?)</option>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).findAll(select).mapNotNull { match ->
            val name = match.groupValues[2]
                .replace("&nbsp;", " ")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            when {
                name.isBlank() || name == "全部" -> null
                else -> JwxtClassroom(code = "", name = name, sourceId = match.groupValues[1])
            }
        }.toList()
    }

    internal fun guessCampus(
        name: String,
        knownBuildings: List<Pair<String, String>>
    ): String {
        knownBuildings
            .filter { it.first.isNotBlank() && name.contains(it.first) }
            .maxByOrNull { it.first.length }
            ?.let { return it.second }
        return when {
            name.contains("光华") || name.startsWith("一号楼") || name.startsWith("三号楼") -> "光华校区"
            name.startsWith("一教") || name.startsWith("二教") || name.startsWith("主教") ||
                name.contains("实验楼") || name.startsWith("学科楼") || name.startsWith("阶梯") -> "官渡校区"
            name.contains("西城") -> "西城校区"
            else -> ""
        }
    }

    internal fun guessBuilding(
        name: String,
        knownBuildings: List<Pair<String, String>>
    ): String {
        knownBuildings
            .map { it.first }
            .filter { it.isNotBlank() && name.contains(it) }
            .maxByOrNull { it.length }
            ?.let { return it }
        val prefixes = listOf(
            "二教A", "二教B", "一教", "主教", "实验楼", "学科楼", "阶梯",
            "光华1号楼", "光华3号楼", "西城教学综合体", "外语楼", "经管楼",
            "石油大楼", "化学楼", "东创楼", "双创楼", "生食学院楼", "环境学院楼",
            "建工学院楼", "艺术学院楼", "理学院学院楼", "中专楼", "化工楼"
        )
        return prefixes.filter { name.contains(it) }.maxByOrNull { it.length }.orEmpty()
    }

    internal fun parseClassroomPage(body: String): ClassroomPage {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return ClassroomPage()
        val total = root["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val rows = root["rows"]?.jsonArray ?: return ClassroomPage(total = total)
        val rooms = rows.mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val name = record.stringField("jxcdmc") ?: return@mapNotNull null
            val rawCode = record.stringField("jxcdbh")
                ?.takeIf { it.isNotBlank() }
                ?: Regex("(?<!\\d)(\\d{4})(?!\\d)").find(name)?.groupValues?.getOrNull(1)
                ?: record.stringField("jxcddm").orEmpty()
            JwxtClassroom(
                code = normalizeRoomCode(rawCode),
                name = name,
                campus = record.stringField("xqmc") ?: "",
                building = record.stringField("jzwmc") ?: "",
                capacity = record.stringField("rnskrs")?.toIntOrNull() ?: 0,
                area = record.stringField("gnqmc") ?: ""
            )
        }
        return ClassroomPage(total = maxOf(total, rooms.size), rooms = rooms)
    }

    private fun urlEncodeSections(sections: String): String =
        java.net.URLEncoder.encode(sections, "UTF-8")

    /**
     * Whole-term course summary (xsgrkbcx!xsAllKbList.action). The HTML page
     * embeds the records in a `var kbxx = [...]` script variable; grouped by
     * course name for the term overview.
     */
    suspend fun fetchTermCourses(): List<JwxtTermCourse> = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/xsgrkbcx!xsAllKbList.action?xnxqdm=${fetchContext().currentTerm}")
        val arrayText = TermExtractor.extractArray(body, "kbxx") ?: return@withContext emptyList()
        val array = runCatching { json.parseToJsonElement(arrayText).jsonArray }.getOrNull()
            ?: return@withContext emptyList()
        val slots = array.mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val name = record.stringField("kcmc") ?: return@mapNotNull null
            val weekday = parseWeekday(record.stringField("xq") ?: "")
            val sections = parseSections(
                record.stringField("jcdm2")
                    ?: record.stringField("jcdm")
                    ?: record.stringField("jc")
                    ?: ""
            )
            val weeks = TermExtractor.parseWeeks(record.stringField("zcs") ?: "")
            if (weekday !in 1..7 || sections.isEmpty() || weeks.isEmpty()) return@mapNotNull null
            JwxtTermSlot(
                courseName = name,
                teacher = record.stringField("teaxms")?.takeIf { it != "None" }
                    ?: record.stringField("jsxm")?.takeIf { it != "None" } ?: "",
                location = record.stringField("jxcdmc")?.takeIf { it != "None" } ?: "",
                weekday = weekday,
                sectionText = sections.joinToString(","),
                firstWeek = weeks.first(),
                lastWeek = weeks.last(),
                weekCount = weeks.size
            )
        }
        return@withContext slots
            .groupBy { it.courseName }
            .map { (name, group) ->
                JwxtTermCourse(
                    courseName = name,
                    teacher = group.firstNotNullOfOrNull { slot -> slot.teacher.takeIf { t -> t.isNotBlank() } } ?: "",
                    location = group.firstNotNullOfOrNull { slot -> slot.location.takeIf { l -> l.isNotBlank() } } ?: "",
                    firstWeek = group.minOf { it.firstWeek },
                    lastWeek = group.maxOf { it.lastWeek },
                    weekCount = group.sumOf { it.weekCount },
                    slots = group.sortedBy { it.weekday * 100 + (it.sectionText.split(",").firstOrNull()?.toIntOrNull() ?: 0) }
                )
            }
            .sortedBy { it.firstWeek }
    }

    /** Score composition (平时/期中/实验/期末 with weights); read-only. */
    suspend fun fetchGradeDetail(recordId: String): JwxtGradeDetail? = withContext(Dispatchers.IO) {
        if (recordId.isBlank()) return@withContext null
        val body = get("$baseUrl/xskccjxx!getDetail.action?cjdm=$recordId")
        val root = json.parseToJsonElement(body).jsonArray
        val record = root.firstOrNull() as? JsonObject ?: return@withContext null
        fun part(index: Int): JwxtGradeDetailItem? {
            val name = record.stringField("bl${index}mc") ?: return null
            val score = record.stringField("cj$index").orEmpty()
            val weight = record.stringField("bl$index").orEmpty()
            if (score.isBlank() && weight.isBlank()) return null
            return JwxtGradeDetailItem(name, score, weight)
        }
        JwxtGradeDetail(
            items = (1..4).mapNotNull(::part),
            total = record.stringField("zcj") ?: ""
        )
    }

    /**
     * Current-term school calendar. The term picker page can fail its selector
     * parse while the calendar endpoint still works, so fall back to the term
     * label already shown by the schedule. Throws [JwxtSessionExpired] when no
     * term code can be resolved.
     */
    suspend fun fetchCalendar(termLabel: String = ""): JwxtCalendar? = withContext(Dispatchers.IO) {
        val context = runCatching { fetchContext() }.getOrNull()
        val term = context?.currentTerm?.takeIf { it.isNotBlank() }
            ?: termCodeFromLabel(termLabel)
            ?: throw JwxtSessionExpired()
        val label = context?.currentTermLabel?.takeIf { it.isNotBlank() }
            ?: termLabel
        val body = get("$baseUrl/xlxx!getXlxx.action?xnxqdm=$term")
        JwxtCalendar(
            term = term,
            termLabel = label,
            days = parseCalendar(body)
        )
    }

    internal fun termCodeFromLabel(label: String): String? {
        val match = Regex("""(\d{4})-\d{4}-([12])""").find(label) ?: return null
        return match.groupValues[1] + match.groupValues[2]
    }

    /**
     * Term calendar (xlxx!getXlxx.action) is a weekly HTML table. Rows are
     * Sunday..Saturday, and a week can cross a month boundary; the first
     * Monday anchors every following week so dates are never guessed from the
     * month cell alone.
     */
    internal fun parseCalendar(body: String): List<JwxtCalendarDay> {
        val document = Jsoup.parse("<table>" + body + "</table>")
        val rows = document.select("tr").map { it.select("td") }.filter { it.size >= 8 }
        if (rows.isEmpty()) return emptyList()

        var currentMonth = ""
        var firstMonday: LocalDate? = null
        var firstWeek: Int? = null
        var rowOffset = 0
        val result = mutableListOf<JwxtCalendarDay>()

        for (cells in rows) {
            val weekRaw = cells[0].text().trim()
            val weekNumber = Regex("\\d+").find(weekRaw)?.value?.toIntOrNull()
            val hasMonth = cells.size >= 9 || cells[1].hasClass("month")
            val dayStart = if (hasMonth) {
                normalizeCalendarMonth(cells[1].text())?.let { currentMonth = it }
                2
            } else {
                1
            }

            if (firstMonday == null) {
                val month = parseCalendarMonth(currentMonth) ?: continue
                val sundayDay = calendarDayNumber(cells.getOrNull(dayStart))
                val mondayDay = calendarDayNumber(cells.getOrNull(dayStart + 1)) ?: continue
                firstMonday = findCalendarMonday(month, mondayDay, sundayDay) ?: continue
                firstWeek = weekNumber ?: 1
                rowOffset = 0
            }

            val week = weekNumber ?: ((firstWeek ?: 1) + rowOffset)
            val monday = firstMonday.plusWeeks((week - (firstWeek ?: 1)).toLong())
            for (index in 0..6) {
                val cell = cells.getOrNull(dayStart + index) ?: continue
                val date = monday.plusDays((index - 1).toLong())
                val cellDay = calendarDayNumber(cell)
                if (cellDay != null && cellDay != date.dayOfMonth) continue
                if (!cell.hasClass("day") && cellDay == null) continue
                val status = when {
                    cell.hasClass("skcolor") -> JwxtCalendarDay.CLASS_DAY
                    cell.hasClass("xxcolor") -> JwxtCalendarDay.REST_DAY
                    else -> JwxtCalendarDay.UNKNOWN
                }
                val note = cell.selectFirst("span[bz]")?.attr("bz")?.trim().orEmpty()
                    .ifBlank { cell.selectFirst("span")?.text()?.trim().orEmpty() }
                result += JwxtCalendarDay(
                    date = date.toString(),
                    week = "第 $week 周",
                    status = status,
                    note = note
                )
            }
            rowOffset++
        }
        return result.distinctBy { it.date }.sortedBy { it.date }
    }

    private fun normalizeCalendarMonth(value: String): String? {
        val match = Regex("""(\d{4})\D+(\d{1,2})""").find(value) ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return "%04d-%02d".format(match.groupValues[1].toInt(), month)
    }

    private fun parseCalendarMonth(value: String): YearMonth? {
        val match = Regex("""(\d{4})-(\d{2})""").find(value) ?: return null
        return runCatching {
            YearMonth.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }.getOrNull()
    }

    private fun calendarDayNumber(cell: org.jsoup.nodes.Element?): Int? {
        if (cell == null) return null
        val clone = cell.clone()
        clone.select("span").remove()
        return clone.text().trim().toIntOrNull()
    }

    private fun findCalendarMonday(
        month: YearMonth,
        mondayDay: Int,
        sundayDay: Int?
    ): LocalDate? {
        val candidates = listOf(month, month.plusMonths(1)).mapNotNull { candidate ->
            runCatching { candidate.atDay(mondayDay) }.getOrNull()
        }.filter { it.dayOfWeek == DayOfWeek.MONDAY }
        return candidates.firstOrNull { date ->
            sundayDay == null || date.minusDays(1).dayOfMonth == sundayDay
        } ?: candidates.firstOrNull()
    }

    internal fun parseCalendarFirstMonday(body: String): LocalDate? {
        // zf emits bare <tr> rows; HTML5 parsing drops them outside a table.
        val document = Jsoup.parse("<table>" + body + "</table>")
        for (row in document.select("tr")) {
            val cells = row.select("td")
            if (cells.size < 4) continue
            if (cells[0].text().trim() != "1") continue
            val month = cells[1].text().trim() // e.g. 2026-08
            val mondayText = cells[3].text().trim() // cells[2]=Sun, cells[3]=Mon
            val day = mondayText.toIntOrNull() ?: continue
            val parts = month.split("-")
            if (parts.size != 2) continue
            val year = parts[0].toIntOrNull() ?: continue
            val monthValue = parts[1].toIntOrNull() ?: continue
            return runCatching { LocalDate.of(year, monthValue, day) }.getOrNull()
        }
        return null
    }

    /** Student card (server-rendered page); only the display name is kept. */
    suspend fun fetchStudentInfo(): JwxtStudentInfo? = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/xjkpxx!xjkpList.action")
        val text = Jsoup.parse(body).wholeText()
        fun after(label: String): String {
            val index = text.indexOf(label)
            if (index < 0) return ""
            val rest = text.substring(index + label.length).trim()
            val stopWords = listOf("学号", "姓名", "入学年份", "学院", "专业", "班级", "所在年级",
                "学籍状态", "外语语种", "性别", "出生日期", "辅修专业", "状态")
            var end = rest.length
            for (word in stopWords) {
                val at = rest.indexOf(word)
                if (at in 1 until end) end = at
            }
            return rest.substring(0, end).trim().take(40)
        }
        val name = after("姓名：")
        if (name.isBlank()) return@withContext null
        JwxtStudentInfo(
            name = name,
            studentId = after("学号："),
            major = after("专业："),
            className = after("班级："),
            grade = after("所在年级：").ifBlank { after("入学年份：") },
            status = after("学籍状态：")
        )
    }

    /** Grade-publish notices (newest first page). */
    suspend fun fetchNotices(): List<JwxtNotice> = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/xxtz!getDataList.action?page=1&rows=20")
        val root = json.parseToJsonElement(body).jsonObject
        val rows = root["rows"]?.jsonArray ?: return@withContext emptyList()
        rows.mapNotNull { row ->
            val record = row as? JsonObject ?: return@mapNotNull null
            val raw = record.stringField("msg") ?: return@mapNotNull null
            val inner = runCatching {
                json.parseToJsonElement(raw).jsonObject
            }.getOrNull() ?: return@mapNotNull null
            fun field(key: String) =
                (inner[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            JwxtNotice(
                courseName = field("kcmc"),
                score = field("zcj"),
                gradePoint = field("jd"),
                createdAt = record.stringField("cjsj") ?: ""
            )
        }.filter { it.courseName.isNotBlank() }
    }

    /** Course tasks of a term (assessment modes included). */
    suspend fun fetchCourseTasks(term: String): List<JwxtCourseTask> = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/xskktzd!getDataList.action?page=1&rows=50&xnxqdm=$term")
        val root = json.parseToJsonElement(body).jsonObject
        val rows = root["rows"]?.jsonArray ?: return@withContext emptyList()
        rows.mapNotNull { row ->
            val record = row as? JsonObject ?: return@mapNotNull null
            val name = record.stringField("kcmc") ?: return@mapNotNull null
            JwxtCourseTask(
                courseName = name,
                taskId = record.stringField("kcrwdm") ?: "",
                assessMode = record.stringField("khfsmc") ?: "",
                category = record.stringField("kcdlmc") ?: ""
            )
        }
    }

    /** Teaching calendar: what happens each week for one course task. */
    suspend fun fetchTeachingCalendar(taskId: String): List<JwxtWeekEntry> = withContext(Dispatchers.IO) {
        if (taskId.isBlank()) return@withContext emptyList()
        val body = get("$baseUrl/xskktzd!getZhouli.action?kcrwdm=$taskId&page=1&rows=60")
        val root = json.parseToJsonElement(body).jsonObject
        val rows = root["rows"]?.jsonArray ?: return@withContext emptyList()
        rows.mapNotNull { row ->
            val record = row as? JsonObject ?: return@mapNotNull null
            JwxtWeekEntry(
                week = record.stringField("zc")?.toIntOrNull() ?: 0,
                weekday = record.stringField("xq")?.toIntOrNull() ?: 0,
                sections = record.stringField("jcdm2") ?: "",
                content = decodeHtmlEntities(record.stringField("sknrjj") ?: ""),
                mode = decodeHtmlEntities(record.stringField("jxhjmc") ?: ""),
                location = decodeHtmlEntities(record.stringField("zdjxcdmc") ?: ""),
                teacher = decodeHtmlEntities(record.stringField("teaxms") ?: "")
            )
        }.sortedBy { it.week }
    }

    // ---- HTTP ---------------------------------------------------------------

    private fun fetchContext(): JwxtContext {
        val body = get("$baseUrl/xsgrkbcx!getXsgrbkList.action")
        return parseContext(body)
    }

    private fun fetchWeek(term: String, week: Int, weekStart: LocalDate): List<List<LocalEvent>> {
        val body = get("$baseUrl/xsgrkbcx!getKbRq.action?xnxqdm=$term&zc=$week")
        return parseWeek(body, weekStart)
    }

    private fun get(url: String): String {
        val cookie = cookieHeader() ?: throw JwxtSessionExpired()
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            mergeCookieHeader(cookie, response.headers("Set-Cookie"))
                ?.let(onCookieUpdate)
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("教务系统不可达 HTTP ${response.code}")
            if (isLoginHtml(body)) throw JwxtSessionExpired()
            return body
        }
    }

    private fun isLoginHtml(body: String): Boolean {
        val lower = body.lowercase()
        val jwxtLogin = lower.contains("login!dologin.action") &&
            (lower.contains("name=\"account\"") || lower.contains("id=\"account\""))
        // WebVPN wraps the JWXT host in its own unified-auth page; an expired
        // WebVPN session otherwise looks like an empty calendar/response.
        val webVpnLogin = lower.contains("<title>统一身份认证</title>") ||
            lower.contains("authserver/login") ||
            lower.contains("authserver\\login")
        return jwxtLogin || webVpnLogin
    }

    companion object {
        /** Some campus hosts answer IPv6 but drop it; prefer A records. */
        private object PreferIpv4Dns : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> =
                okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it.address.size }
        }

        private val WEEK_LITERAL = Regex("""\|\|\s*'(\d{1,2})'\s*\|\|""")
        private val WEEK_LOOP = Regex("""for\s*\(\s*var\s+i\s*=\s*0\s*;\s*i\s*<\s*(\d{1,2})\s*;""")
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 SuiSign/0.1.0"

        /** Section → (start, end) clocks; identical to the Go table. */
        internal val sectionTimes: Map<Int, Pair<String, String>> = mapOf(
            1 to Pair("08:00", "08:45"),
            2 to Pair("08:55", "09:40"),
            3 to Pair("10:00", "10:45"),
            4 to Pair("10:55", "11:40"),
            5 to Pair("14:30", "15:15"),
            6 to Pair("15:25", "16:10"),
            7 to Pair("16:20", "17:00"),
            8 to Pair("17:00", "17:50"),
            9 to Pair("19:40", "20:25"),
            10 to Pair("20:35", "21:20")
        )
    }
}

/**
 * Merge Set-Cookie values into an existing Cookie header without dropping
 * WebVPN cookies. Returns null when nothing changed, so the caller can avoid
 * rewriting encrypted storage on every response.
 */
internal fun mergeCookieHeader(current: String, setCookies: List<String>): String? {
    if (setCookies.isEmpty()) return null
    val parts = current.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableList()
    var changed = false
    for (raw in setCookies) {
        val pair = raw.substringBefore(';').trim()
        val separator = pair.indexOf('=')
        if (separator <= 0) continue
        val name = pair.substring(0, separator).trim()
        val value = pair.substring(separator + 1).trim()
        val lower = raw.lowercase()
        val expired = lower.contains("max-age=0") ||
            lower.contains("expires=thu, 01 jan 1970")
        val index = parts.indexOfFirst {
            it.substringBefore('=').trim().equals(name, ignoreCase = true)
        }
        if (expired) {
            if (index >= 0) {
                parts.removeAt(index)
                changed = true
            }
            continue
        }
        val next = "$name=$value"
        if (index >= 0) {
            if (parts[index] != next) {
                parts[index] = next
                changed = true
            }
        } else {
            parts += next
            changed = true
        }
    }
    return if (changed) parts.joinToString("; ") else null
}

data class JwxtOption(val value: String, val label: String, val selected: Boolean)

data class JwxtContext(
    val terms: List<JwxtOption>,
    val currentTerm: String,
    val currentTermLabel: String,
    val currentWeek: Int,
    val maxWeek: Int
)

@Serializable
data class JwxtGrade(
    val term: String,
    val courseName: String,
    val score: String,
    val gradePoint: Double?,
    val credits: Double?,
    val category: String,
    val studyMode: String,
    val active: Boolean,
    val recordId: String = ""
)

@Serializable
data class JwxtGradeBundle(
    val grades: List<JwxtGrade> = emptyList(),
    val exams: List<JwxtExam> = emptyList(),
    val notices: List<JwxtNotice> = emptyList(),
    val student: JwxtStudentInfo? = null
)

internal fun isEmptyGradeBundle(bundle: JwxtGradeBundle): Boolean =
    bundle.grades.isEmpty() && bundle.exams.isEmpty() &&
        bundle.notices.isEmpty() && bundle.student == null

@Serializable
data class JwxtGradeDetail(
    val items: List<JwxtGradeDetailItem>,
    val total: String
)

@Serializable
data class JwxtGradeDetailItem(val name: String, val score: String, val weight: String)

@Serializable
data class JwxtExam(
    val courseName: String,
    val time: String,
    val location: String,
    val seat: String,
    val note: String
)

@Serializable
data class JwxtCalendarDay(
    val date: String,
    val week: String,
    val status: String,
    val note: String = ""
) {
    companion object {
        const val CLASS_DAY = "class_day"
        const val REST_DAY = "rest_day"
        const val UNKNOWN = "unknown"
    }
}

@Serializable
data class JwxtCalendar(
    val term: String,
    val termLabel: String,
    val days: List<JwxtCalendarDay>
)

@Serializable
data class JwxtStudentInfo(
    val name: String,
    val studentId: String,
    val major: String,
    val className: String,
    val grade: String,
    val status: String
)

@Serializable
data class JwxtNotice(
    val courseName: String,
    val score: String,
    val gradePoint: String,
    val createdAt: String
)

data class JwxtCourseTask(
    val courseName: String,
    val taskId: String,
    val assessMode: String = "",
    val category: String = ""
)

data class JwxtWeekEntry(
    val week: Int,
    val weekday: Int,
    val sections: String,
    val content: String,
    val mode: String,
    val location: String,
    val teacher: String
)

/** Credit summary by course category over graded courses. */
fun creditsByCategory(grades: List<JwxtGrade>): List<Pair<String, Double>> {
    val totals = linkedMapOf<String, Double>()
    for (grade in grades) {
        if (!grade.active) continue
        val credit = grade.credits ?: continue
        if (credit <= 0) continue
        totals[grade.category.ifBlank { "其他" }] = (totals[grade.category.ifBlank { "其他" }] ?: 0.0) + credit
    }
    return totals.entries
        .map { it.key to it.value }
        .sortedByDescending { it.second }
}

/** Per-term weighted GPA, oldest first. */
fun gpaByTerm(grades: List<JwxtGrade>): List<Pair<String, Double>> {
    return grades
        .filter { it.active && it.gradePoint != null && (it.credits ?: 0.0) > 0 }
        .groupBy { it.term }
        .map { (term, list) ->
            val weight = list.sumOf { it.credits ?: 0.0 }
            term to list.sumOf { (it.gradePoint ?: 0.0) * (it.credits ?: 0.0) } / weight
        }
        .sortedBy { it.first }
}

@Serializable
data class JwxtTermSlot(
    val courseName: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val sectionText: String,
    val firstWeek: Int,
    val lastWeek: Int,
    val weekCount: Int
)

@Serializable
data class JwxtTermCourse(
    val courseName: String,
    val teacher: String,
    val location: String,
    val firstWeek: Int,
    val lastWeek: Int,
    val weekCount: Int,
    val slots: List<JwxtTermSlot>
)

@Serializable
data class JwxtFreeRoom(
    val roomCode: String = "",
    val roomName: String,
    val campus: String = "",
    val building: String,
    val capacity: Int,
    val area: String
)

@Serializable
data class JwxtClassroom(
    val code: String = "",
    val name: String,
    val campus: String = "",
    val building: String = "",
    val capacity: Int = 0,
    val area: String = "",
    val sourceId: String = ""
) {
    fun key(): String = code.ifBlank { sourceId.ifBlank { "$name|$campus|$building" } }
}

internal data class ClassroomPage(
    val total: Int = 0,
    val rooms: List<JwxtClassroom> = emptyList()
)

/** UI weekday is Monday=1..Sunday=7; the free-room page uses Sunday=1..Saturday=7. */
internal fun freeRoomApiWeekday(uiWeekday: Int): Int =
    if (uiWeekday == 7) 1 else uiWeekday + 1

/** CSV accepted by the free-room page's multi-select teaching-week field. */
internal fun catalogWeekRange(maxWeek: Int): String =
    (1..maxWeek.coerceAtLeast(1)).joinToString(",")

/**
 * JWXT often stores a classroom number without leading zeros (611, 424);
 * pad those to four digits for a stable lookup key.
 */
internal fun normalizeRoomCode(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.isNotEmpty() && digits.length <= 4) digits.padStart(4, '0') else raw.trim()
}

/** Weighted GPA over active graded courses. */
fun weightedGpa(grades: List<JwxtGrade>): Double? {
    var weightSum = 0.0
    var pointSum = 0.0
    for (grade in grades) {
        if (!grade.active) continue
        val point = grade.gradePoint ?: continue
        val credit = grade.credits ?: continue
        if (credit <= 0) continue
        weightSum += credit
        pointSum += point * credit
    }
    return if (weightSum > 0) pointSum / weightSum else null
}
