package top.sykesp42.rin.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.CourseOverride
import top.sykesp42.rin.data.CourseReminder
import top.sykesp42.rin.data.JwxtCalendarDay
import top.sykesp42.rin.data.JwxtExam
import top.sykesp42.rin.data.JwxtFreeRoom
import top.sykesp42.rin.data.JwxtGrade
import top.sykesp42.rin.data.JwxtTermCourse
import top.sykesp42.rin.data.ManualCourse
import top.sykesp42.rin.data.ManualExam
import top.sykesp42.rin.data.ManualMove
import top.sykesp42.rin.data.ManualTodo
import top.sykesp42.rin.data.ReminderPrefs
import top.sykesp42.rin.data.buildWeekOverview
import top.sykesp42.rin.data.currentWeekNumber
import top.sykesp42.rin.data.defaultTermStart
import top.sykesp42.rin.data.LocalJwxtApi
import top.sykesp42.rin.data.SessionCookie
import top.sykesp42.rin.reminder.CourseReminderScheduler
import top.sykesp42.rin.ui.faces.NoticeFaces
import top.sykesp42.rin.ui.faces.NoticeIcon
import top.sykesp42.rin.ui.faces.NoticeSlot
import top.sykesp42.rin.ui.profile.Profile
import top.sykesp42.rin.ui.schedule.ROOM_SLOTS
import top.sykesp42.rin.ui.schedule.RoomSlot
import top.sykesp42.rin.ui.schedule.campusZone
import top.sykesp42.rin.ui.schedule.isRoomFree
import top.sykesp42.rin.ui.schedule.normalizeRoomName
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.roomSlotAt
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.schedule.weekNumberFor
import top.sykesp42.rin.ui.today.TodayCardId
import top.sykesp42.rin.ui.today.TodayCards

data class ScheduleUiState(
    val overview: ApiScheduleOverview? = null,
    val loading: Boolean = true,
    val currentWeek: Int = 0,
    val termStart: String = "",
    val warning: String? = null,
    // Cache footnote support, same as the full client: a stale read is still
    // useful and must say when it was taken.
    val fromCache: Boolean = false,
    val cachedAt: String = "",
    val error: String = ""
)

data class CourseMeeting(
    val weekday: Int,
    val sectionText: String,
    val firstWeek: Int,
    val lastWeek: Int,
    val location: String
)

data class CourseDetailUiState(
    val open: Boolean = false,
    val courseName: String = "",
    val teacher: String = "",
    val location: String = "",
    val sectionText: String = "",
    val weekday: Int = 0,
    val firstWeek: Int = 1,
    val lastWeek: Int = 20,
    val alias: String = "",
    val colorIndex: Int = 0,
    val meetings: List<CourseMeeting> = emptyList()
)

/** One teaching block of a room, as the school answers it. */
data class RoomSlotState(
    val slot: RoomSlot,
    // null = the school has no occupancy answer for this room in this block.
    val free: Boolean?
)

/**
 * The room helper's detail: what the school knows about one room today plus
 * whatever the local timetable can add ("your class is here at 14:30").
 */
data class RoomDetailUiState(
    val roomName: String = "",
    val campus: String = "",
    val building: String = "",
    val area: String = "",
    val capacity: Int = 0,
    val slots: List<RoomSlotState> = emptyList(),
    val loading: Boolean = true,
    val myCourses: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val error: String = ""
)

/**
 * 校历 state, same shape as the full client's so the calendar screens port over
 * unchanged: loading/error/term plus the flat day list.
 */
data class CalendarUiState(
    val loading: Boolean = false,
    val days: List<JwxtCalendarDay> = emptyList(),
    val term: String = "",
    val termLabel: String = "",
    val error: String = "",
    val fromCache: Boolean = false,
    val cachedAt: String = ""
)

/**
 * 校务 page state: the room helper's free-room answer for one day + block,
 * plus the read-only academic lists (exams, grades, calendar) on the same page.
 */
data class AcademicUiState(
    val day: LocalDate = LocalDate.now(campusZone),
    val slot: RoomSlot = ROOM_SLOTS.first(),
    val week: Int = 0,
    val rooms: List<JwxtFreeRoom> = emptyList(),
    val campus: String = "",
    val loading: Boolean = false,
    val error: String = "",
    val updatedAt: String = "",
    val exams: List<JwxtExam> = emptyList(),
    val examsLoading: Boolean = false,
    val examsError: String = "",
    val grades: List<JwxtGrade> = emptyList(),
    val gradesLoading: Boolean = false,
    val gradesError: String = "",
    val termCourses: List<JwxtTermCourse> = emptyList(),
    val termCoursesLoading: Boolean = false,
    val termCoursesError: String = "",
    val detail: RoomDetailUiState? = null
)

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val settingsRepo = container.settings

    private val _settings = MutableStateFlow<AppSettings?>(null)
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    private val _schedule = MutableStateFlow(ScheduleUiState())
    val schedule: StateFlow<ScheduleUiState> = _schedule.asStateFlow()

    private val _courseDetail = MutableStateFlow(CourseDetailUiState())
    val courseDetail: StateFlow<CourseDetailUiState> = _courseDetail.asStateFlow()

    private val _petWeather = MutableStateFlow<top.sykesp42.rin.data.PetWeatherSnapshot?>(null)
    val petWeather: StateFlow<top.sykesp42.rin.data.PetWeatherSnapshot?> = _petWeather.asStateFlow()

    private val _courseOverrides = MutableStateFlow<Map<String, CourseOverride>>(emptyMap())
    val courseOverrides: StateFlow<Map<String, CourseOverride>> = _courseOverrides.asStateFlow()

    private val _todayFrame = MutableStateFlow<Bitmap?>(null)
    val todayFrame: StateFlow<Bitmap?> = _todayFrame.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _jwxtConnected = MutableStateFlow(container.jwxtSessions.load() != null)
    val jwxtConnected: StateFlow<Boolean> = _jwxtConnected.asStateFlow()

    private val _academic = MutableStateFlow(AcademicUiState())
    val academic: StateFlow<AcademicUiState> = _academic.asStateFlow()

    private val _calendar = MutableStateFlow(CalendarUiState())
    val calendar: StateFlow<CalendarUiState> = _calendar.asStateFlow()

    /**
     * weekday|block|week → the school's free-room answer. The room helper asks
     * the same five blocks again when a detail sheet opens, so this keeps that
     * to one network round per block per day.
     */
    private val roomFreeCache = mutableMapOf<String, List<JwxtFreeRoom>>()

    /**
     * Last overview read from the school. Kept separately because [rebuild]
     * runs on every settings/manual change and must not clobber the JWXT
     * timetable with an empty manual one.
     */
    private var jwxtOverview: ApiScheduleOverview? = null

    val manualSchedule = container.manualSchedule
    val courseReminders = container.courseReminders
    val checklists = container.checklists
    val appVersion: String get() = container.appVersionName

    init {
        var firstSettings = true
        viewModelScope.launch {
            settingsRepo.settings.collect { snapshot ->
                _settings.value = snapshot
                NoticeFaces.publish(snapshot)
                Profile.publish(snapshot)
                if (snapshot.termStartDate.isBlank()) {
                    settingsRepo.setTermStartDate(defaultTermStart(todayAtCampusClock()).toString())
                }
                if (snapshot.todayCardsPreset < TodayCards.PRESET_VERSION) {
                    settingsRepo.setTodayCards(
                        disabled = TodayCards.defaultDisabledCsv(),
                        order = TodayCards.defaultOrderCsv(),
                        preset = TodayCards.PRESET_VERSION
                    )
                }
                if (firstSettings) {
                    firstSettings = false
                    // Show the last good week immediately, then try a fresh
                    // read. Without this a dead session used to leave the
                    // page on an empty "第 1 周" until a login succeeded.
                    restoreScheduleFromCache(snapshot)
                    if (!snapshot.simpleMode && _jwxtConnected.value) refreshJwxt(0)
                }
                rebuild(snapshot, weekOverride = _schedule.value.overview?.week ?: 0)
                if (snapshot.petWeatherEnabled) refreshPetWeather()
            }
        }
        viewModelScope.launch {
            manualSchedule.payload.collect {
                _settings.value?.let { snapshot -> rebuild(snapshot, _schedule.value.overview?.week ?: 0) }
            }
        }
        viewModelScope.launch {
            _courseOverrides.value = container.courseOverrides.all()
        }
        viewModelScope.launch {
            _todayFrame.value = container.todayFrame.decode()
        }
        viewModelScope.launch {
            container.courseReminders.prune()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepo.setOnboarded(true) }
    }

    fun refreshSchedule() {
        val snapshot = _settings.value ?: return
        rebuild(snapshot, 0)
        if (!snapshot.simpleMode) refreshJwxt(0)
    }

    fun switchWeek(delta: Int) {
        val snapshot = _settings.value ?: return
        val current = _schedule.value.overview?.week ?: currentWeekNumber(snapshot, todayAtCampusClock())
        val target = (current + delta).coerceIn(1, 30)
        rebuild(snapshot, target)
        if (!snapshot.simpleMode) refreshJwxt(target)
    }

    fun backToCurrentWeek() {
        val snapshot = _settings.value ?: return
        rebuild(snapshot, 0)
        if (!snapshot.simpleMode) refreshJwxt(0)
    }

    fun setJwxtBaseUrl(value: String) {
        viewModelScope.launch {
            settingsRepo.setJwxtBaseUrl(value.trim().ifBlank { "https://jwxt.gdupt.edu.cn" })
            if (_jwxtConnected.value) refreshJwxt(0)
        }
    }

    fun saveJwxtSession(cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        container.jwxtSessions.save(SessionCookie("Cookie", cookieHeader))
        _jwxtConnected.value = true
        viewModelScope.launch {
            settingsRepo.setJwxtConnectedAt(java.time.Instant.now().toString())
            notify("教务已连接")
        }
        refreshJwxt(0)
    }

    fun disconnectJwxt() {
        container.jwxtSessions.clear()
        jwxtOverview = null
        _jwxtConnected.value = false
        viewModelScope.launch {
            settingsRepo.setJwxtConnectedAt("")
            notify("已断开教务连接")
        }
        _settings.value?.let { rebuild(it, 0) }
    }

    private fun refreshJwxt(weekOverride: Int) {
        val snapshot = _settings.value ?: return
        if (snapshot.simpleMode) return
        val stored = container.jwxtSessions.load() ?: run {
            _jwxtConnected.value = false
            return
        }
        viewModelScope.launch {
            val api = LocalJwxtApi(
                baseUrl = snapshot.jwxtBaseUrl,
                cookieHeader = { container.jwxtSessions.load()?.value ?: stored.value },
                onCookieUpdate = { header ->
                    runCatching { container.jwxtSessions.save(SessionCookie("Cookie", header)) }
                },
                clock = LocalDateTime.now(campusZone)
            )
            runCatching { api.overview(todayAtCampusClock(), weekOverride) }
                .onSuccess { overview ->
                    jwxtOverview = overview
                    _schedule.value = ScheduleUiState(
                        overview = overview,
                        loading = false,
                        currentWeek = if (weekOverride > 0) weekOverride else overview.week,
                        termStart = snapshot.termStartDate,
                        warning = null
                    )
                    container.cache.write("schedule_week0", ApiScheduleOverview.serializer(), overview)
                }
                .onFailure { error ->
                    handleJwxtFailure(error)
                }
        }
    }

    /**
     * Last good week from disk. The schedule cache is written on every
     * successful read; showing it stale beats showing an empty 第 1 周.
     */
    private fun restoreScheduleFromCache(snapshot: AppSettings) {
        if (_schedule.value.overview != null) return
        val cached = container.cache.read("schedule_week0", ApiScheduleOverview.serializer()) ?: return
        val (at, overview) = cached
        // Seed jwxtOverview too: rebuild() prefers the school timetable, so the
        // restored week survives settings changes instead of being replaced by
        // a hand-built empty week.
        jwxtOverview = overview
        _schedule.value = ScheduleUiState(
            overview = overview,
            loading = false,
            currentWeek = overview.week,
            termStart = snapshot.termStartDate,
            fromCache = true,
            cachedAt = at.toString()
        )
    }

    /**
     * One place that turns a failed read into the same state change everywhere:
     * an expired session disconnects, anything else only warns.
     */
    private fun handleJwxtFailure(error: Throwable) {
        val expired = error is LocalJwxtApi.JwxtSessionExpired ||
            error is LocalJwxtApi.JwxtCaptchaRequired
        if (expired) {
            jwxtOverview = null
            container.jwxtSessions.clear()
            _jwxtConnected.value = false
            viewModelScope.launch { settingsRepo.setJwxtConnectedAt("") }
            if (_schedule.value.overview == null) {
                restoreScheduleFromCache(_settings.value ?: return)
            }
            _schedule.value = _schedule.value.copy(
                warning = "教务登录已过期，请到「我的 → 教务连接」重新登录。"
            )
        } else {
            _schedule.value = _schedule.value.copy(
                warning = "教务暂时不可用，显示的是最近一次读取的课表。"
            )
        }
    }

    /** A JWXT client bound to the stored session, or null when not connected. */
    private fun jwxtApi(snapshot: AppSettings): LocalJwxtApi? {
        val stored = container.jwxtSessions.load() ?: return null
        return LocalJwxtApi(
            baseUrl = snapshot.jwxtBaseUrl,
            cookieHeader = { container.jwxtSessions.load()?.value ?: stored.value },
            onCookieUpdate = { header ->
                runCatching { container.jwxtSessions.save(SessionCookie("Cookie", header)) }
            },
            clock = LocalDateTime.now(campusZone)
        )
    }


    fun setTermStart(date: LocalDate) {
        viewModelScope.launch {
            settingsRepo.setTermStartDate(date.toString())
            _settings.value?.let { rebuild(it, 0) }
        }
    }

    // ---- 校务：教室助手 + 考试 + 成绩 --------------------------------------

    /**
     * First entry to the 校务 page. The block is chosen from the school clock
     * once; after that the user's own pick sticks until the page is refreshed.
     */
    fun openAcademic() {
        val snapshot = _settings.value ?: return
        if (snapshot.simpleMode) return
        val state = _academic.value
        // week > 0 only happens after a successful load, so this is "first time
        // this session" rather than "list is momentarily empty".
        val firstOpen = state.week == 0
        if (firstOpen) {
            _academic.update {
                it.copy(
                    day = todayAtCampusClock(),
                    slot = roomSlotAt(nowMinutesAt()) ?: ROOM_SLOTS.last()
                )
            }
        }
        if (firstOpen || (state.rooms.isEmpty() && state.error.isBlank() && !state.loading)) {
            loadFreeRooms()
        }
        if (state.exams.isEmpty() && state.examsError.isBlank() && !state.examsLoading) loadExams()
        if (state.grades.isEmpty() && state.gradesError.isBlank() && !state.gradesLoading) loadGrades()
    }

    fun selectAcademicDay(day: LocalDate) {
        if (day == _academic.value.day) return
        // Drop the previous day's answer so the header can never label one
        // day's rooms with another day's date.
        _academic.update { it.copy(day = day, rooms = emptyList(), error = "") }
        loadFreeRooms()
    }

    fun selectAcademicSlot(slot: RoomSlot) {
        if (slot == _academic.value.slot) return
        _academic.update { it.copy(slot = slot, rooms = emptyList(), error = "") }
        loadFreeRooms()
    }

    /** Pick a weekday inside the current teaching week, the way the full client does. */
    fun selectAcademicWeekday(weekday: Int) {
        if (weekday !in 1..7) return
        val today = todayAtCampusClock()
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        selectAcademicDay(monday.plusDays((weekday - 1).toLong()))
    }

    fun selectAcademicCampus(campus: String) {
        _academic.update { it.copy(campus = campus) }
    }

    fun refreshAcademic() {
        roomFreeCache.clear()
        loadFreeRooms(force = true)
        loadExams(force = true)
        loadGrades(force = true)
        loadCalendar(force = true)
    }

    /** The calendar segment asks for its own reads the first time it is shown. */
    fun ensureCalendar() {
        val state = _calendar.value
        if (state.days.isEmpty() && !state.loading && state.error.isBlank()) {
            loadCalendar()
        }
        ensureTermCourses()
    }

    /** The month grid and the calendar day sheet both need the term timetable. */
    fun ensureTermCourses() {
        val academic = _academic.value
        if (academic.termCourses.isEmpty() &&
            !academic.termCoursesLoading &&
            academic.termCoursesError.isBlank()
        ) {
            loadTermCourses()
        }
    }

    private fun loadTermCourses(force: Boolean = false) {
        val snapshot = _settings.value ?: return
        if (_academic.value.termCoursesLoading) return
        if (!force && _academic.value.termCourses.isNotEmpty()) return
        val api = jwxtApi(snapshot) ?: return
        _academic.update { it.copy(termCoursesLoading = true, termCoursesError = "") }
        viewModelScope.launch {
            runCatching { api.fetchTermCourses() }
                .onSuccess { courses ->
                    _academic.update { state ->
                        state.copy(termCourses = courses, termCoursesLoading = false)
                    }
                }
                .onFailure { error ->
                    _academic.update { state ->
                        state.copy(
                            termCoursesLoading = false,
                            termCoursesError = academicErrorText(error)
                        )
                    }
                }
        }
    }

    private fun loadCalendar(force: Boolean = false) {
        val snapshot = _settings.value ?: return
        if (_calendar.value.loading) return
        if (!force && _calendar.value.days.isNotEmpty()) return
        val api = jwxtApi(snapshot) ?: return
        _calendar.update { it.copy(loading = true, error = "") }
        viewModelScope.launch {
            runCatching { api.fetchCalendar() }
                .onSuccess { calendar ->
                    _calendar.value = CalendarUiState(
                        loading = false,
                        days = calendar?.days.orEmpty(),
                        term = calendar?.term.orEmpty(),
                        termLabel = calendar?.termLabel.orEmpty(),
                        error = if (calendar == null) "教务没有返回校历" else ""
                    )
                }
                .onFailure { error ->
                    _calendar.update { it.copy(loading = false, error = academicErrorText(error)) }
                }
        }
    }

    private fun loadFreeRooms(force: Boolean = false) {
        val snapshot = _settings.value ?: return
        val api = jwxtApi(snapshot) ?: run {
            _academic.update {
                it.copy(loading = false, error = "先在「我的 → 教务连接」登录教务，才能查教室")
            }
            return
        }
        val state = _academic.value
        _academic.update { it.copy(loading = true, error = "") }
        viewModelScope.launch {
            runCatching {
                val context = api.contextSnapshot()
                val weekday = state.day.dayOfWeek.value
                val week = weekNumberFor(state.day, context.currentWeek, todayAtCampusClock())
                val key = "$weekday|${state.slot.key}|$week"
                val cached = roomFreeCache[key].takeIf { !force && it != null }
                val rooms = cached ?: readFreeRooms(api, context.currentTerm, weekday, week, state.slot.key)
                    .also { roomFreeCache[key] = it }
                rooms to week
            }.onSuccess { (rooms, week) ->
                _academic.update {
                    it.copy(
                        rooms = rooms,
                        week = week,
                        loading = false,
                        error = "",
                        updatedAt = LocalTime.now(campusZone)
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    )
                }
            }.onFailure { error ->
                // A page-local read failure must not raise the schedule banner;
                // only an actually expired session is worth the global warning.
                if (error is LocalJwxtApi.JwxtSessionExpired ||
                    error is LocalJwxtApi.JwxtCaptchaRequired
                ) {
                    handleJwxtFailure(error)
                }
                _academic.update {
                    it.copy(loading = false, error = academicErrorText(error))
                }
            }
        }
    }

    /**
     * Campus wifi drops single requests now and then; one silent retry turns
     * that into a slower answer instead of an error the user has to tap away.
     */
    private suspend fun readFreeRooms(
        api: LocalJwxtApi,
        term: String,
        weekday: Int,
        week: Int,
        slotKey: String
    ): List<JwxtFreeRoom> = runCatching {
        api.freeRoomsIn(term, weekday, week, slotKey)
    }.getOrElse { error ->
        if (error is LocalJwxtApi.JwxtSessionExpired || error is LocalJwxtApi.JwxtCaptchaRequired) {
            throw error
        }
        kotlinx.coroutines.delay(700)
        api.freeRoomsIn(term, weekday, week, slotKey)
    }

    private fun loadExams(force: Boolean = false) {
        val snapshot = _settings.value ?: return
        if (_academic.value.examsLoading) return
        if (!force && _academic.value.exams.isNotEmpty()) return
        val api = jwxtApi(snapshot) ?: return
        _academic.update { it.copy(examsLoading = true, examsError = "") }
        viewModelScope.launch {
            runCatching { api.fetchExams() }
                .onSuccess { exams ->
                    _academic.update { state -> state.copy(exams = exams, examsLoading = false) }
                }
                .onFailure { error ->
                    _academic.update { state ->
                        state.copy(examsLoading = false, examsError = academicErrorText(error))
                    }
                }
        }
    }

    private fun loadGrades(force: Boolean = false) {
        val snapshot = _settings.value ?: return
        if (_academic.value.gradesLoading) return
        if (!force && _academic.value.grades.isNotEmpty()) return
        val api = jwxtApi(snapshot) ?: return
        _academic.update { it.copy(gradesLoading = true, gradesError = "") }
        viewModelScope.launch {
            runCatching { api.fetchGrades() }
                .onSuccess { grades ->
                    _academic.update { state -> state.copy(grades = grades, gradesLoading = false) }
                }
                .onFailure { error ->
                    _academic.update { state ->
                        state.copy(gradesLoading = false, gradesError = academicErrorText(error))
                    }
                }
        }
    }

    /**
     * Room detail: ask the school which of today's five blocks the room is free
     * in, then add what only the phone knows — your own class in that room.
     */
    fun openRoomDetail(room: JwxtFreeRoom) = openRoomDetail(
        roomName = room.roomName,
        campus = room.campus,
        building = room.building,
        area = room.area,
        capacity = room.capacity
    )

    fun openRoomDetail(
        roomName: String,
        campus: String = "",
        building: String = "",
        area: String = "",
        capacity: Int = 0
    ) {
        val snapshot = _settings.value ?: return
        _academic.update {
            it.copy(
                detail = RoomDetailUiState(
                    roomName = roomName,
                    campus = campus,
                    building = building,
                    area = area,
                    capacity = capacity,
                    loading = true
                )
            )
        }
        val api = jwxtApi(snapshot) ?: run {
            _academic.update { state ->
                state.copy(
                    detail = state.detail?.copy(
                        loading = false,
                        error = "先在「我的 → 教务连接」登录教务"
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                val context = api.contextSnapshot()
                val today = todayAtCampusClock()
                val weekday = today.dayOfWeek.value
                val week = weekNumberFor(today, context.currentWeek, today)
                val slots = coroutineScope {
                    ROOM_SLOTS.map { slot ->
                        async {
                            RoomSlotState(
                                slot = slot,
                                free = roomFreeIn(api, context.currentTerm, weekday, week, slot.key, roomName)
                            )
                        }
                    }.map { it.await() }
                }
                Pair(slots, week)
            }.onSuccess { (slots, week) ->
                val alternatives = _academic.value.rooms
                    .filter { it.roomName != roomName && (campus.isBlank() || it.campus == campus) }
                    .distinctBy { it.roomName }
                    .take(3)
                    .map { it.roomName }
                _academic.update { state ->
                    state.copy(
                        week = if (week > 0) week else state.week,
                        detail = state.detail?.copy(
                            slots = slots,
                            loading = false,
                            myCourses = myCoursesInRoom(roomName),
                            alternatives = alternatives,
                            error = ""
                        )
                    )
                }
            }.onFailure { error ->
                _academic.update { state ->
                    state.copy(
                        detail = state.detail?.copy(
                            loading = false,
                            error = academicErrorText(error)
                        )
                    )
                }
            }
        }
    }

    fun closeRoomDetail() {
        _academic.update { it.copy(detail = null) }
    }

    /** true/false from the school's answer, null when that block could not be read. */
    private suspend fun roomFreeIn(
        api: LocalJwxtApi,
        term: String,
        weekday: Int,
        week: Int,
        slotKey: String,
        roomName: String
    ): Boolean? {
        val key = "$weekday|$slotKey|$week"
        val rooms = roomFreeCache[key] ?: runCatching {
            readFreeRooms(api, term, weekday, week, slotKey)
        }.onSuccess { roomFreeCache[key] = it }.getOrNull() ?: return null
        return isRoomFree(rooms.map { it.roomName }.toSet(), roomName)
    }

    /** Your own classes in this room today, if the timetable is loaded. */
    private fun myCoursesInRoom(roomName: String): List<String> {
        val target = normalizeRoomName(roomName)
        if (target.isBlank()) return emptyList()
        val today = todayAtCampusClock()
        val events = _schedule.value.overview?.days
            ?.firstOrNull { it.date == today.toString() }
            ?.events
            .orEmpty()
        return events.filter { event ->
            val location = normalizeRoomName(event.location)
            location.isNotBlank() && (location.contains(target) || target.contains(location))
        }.map { "${it.startTime}-${it.endTime} ${it.courseName}" }
    }

    private fun academicErrorText(error: Throwable): String = when (error) {
        is LocalJwxtApi.JwxtSessionExpired -> "教务登录已过期，请到「我的 → 教务连接」重新登录。"
        is LocalJwxtApi.JwxtCaptchaRequired -> "教务需要验证码，请用网页登录一次。"
        else -> "教务暂时没有响应，稍后再试。"
    }

    private fun rebuild(snapshot: AppSettings, weekOverride: Int) {
        val today = todayAtCampusClock()
        // Prefer the school timetable whenever it covers the requested week.
        val fromSchool = jwxtOverview?.takeIf { school ->
            weekOverride == 0 || school.week == weekOverride
        }
        if (fromSchool != null) {
            _schedule.value = ScheduleUiState(
                overview = fromSchool,
                loading = false,
                currentWeek = fromSchool.week.takeIf { it > 0 } ?: currentWeekNumber(snapshot, today),
                termStart = snapshot.termStartDate.ifBlank { defaultTermStart(today).toString() },
                warning = _schedule.value.warning
            )
            return
        }
        val overview = buildWeekOverview(
            payload = manualSchedule.payload.value,
            settings = snapshot,
            weekOverride = weekOverride,
            today = today,
            nowMinutes = nowMinutesAt()
        )
        _schedule.value = ScheduleUiState(
            overview = overview,
            loading = false,
            currentWeek = currentWeekNumber(snapshot, today),
            termStart = snapshot.termStartDate.ifBlank { defaultTermStart(today).toString() }
        )
        // Never write the hand-built overview into schedule_week0: that key is
        // the school timetable's last-good copy, and a stale local build used
        // to overwrite it and blank the page after a session died.
    }

    fun openCourseDetail(event: ApiScheduleEvent, weekday: Int? = null) {
        val payload = manualSchedule.payload.value
        val course = payload.courses.firstOrNull { it.name == event.courseName }
        val override = _courseOverrides.value[event.courseName]
        val meetings = payload.courses
            .filter { it.name == event.courseName }
            .map { CourseMeeting(it.weekday, it.sectionText, it.firstWeek, it.lastWeek, it.location) } +
            payload.moves
                .filter { it.courseName == event.courseName }
                .map { CourseMeeting(it.weekday, it.sectionText, it.week, it.week, course?.location.orEmpty()) }
        _courseDetail.value = CourseDetailUiState(
            open = true,
            courseName = event.courseName,
            teacher = course?.teacher.orEmpty(),
            location = event.location.ifBlank { course?.location.orEmpty() },
            sectionText = event.sectionText,
            weekday = weekday ?: course?.weekday ?: 0,
            firstWeek = course?.firstWeek ?: 1,
            lastWeek = course?.lastWeek ?: 20,
            alias = override?.alias.orEmpty(),
            colorIndex = override?.colorIndex ?: 0,
            meetings = meetings.distinct()
        )
    }

    fun dismissCourseDetail() {
        _courseDetail.value = _courseDetail.value.copy(open = false)
    }

    fun setCourseOverride(courseName: String, alias: String, colorIndex: Int) {
        viewModelScope.launch {
            container.courseOverrides.save(courseName, CourseOverride(alias.trim(), colorIndex))
            _courseOverrides.value = container.courseOverrides.all()
            _courseDetail.value = _courseDetail.value.copy(alias = alias.trim(), colorIndex = colorIndex)
        }
    }

    fun addManualCourse(course: ManualCourse) = manualSchedule.addCourse(course)
    fun removeManualCourse(id: String) = manualSchedule.removeCourse(id)
    fun addManualExam(exam: ManualExam) = manualSchedule.addExam(exam)
    fun removeManualExam(id: String) = manualSchedule.removeExam(id)
    fun addManualTodo(todo: ManualTodo) = manualSchedule.addTodo(todo)
    fun toggleManualTodo(id: String) = manualSchedule.toggleTodo(id)
    fun removeManualTodo(id: String) = manualSchedule.removeTodo(id)
    fun addManualMove(move: ManualMove) = manualSchedule.addMove(move)
    fun removeManualMove(id: String) = manualSchedule.removeMove(id)
    fun setManualDayMark(date: String, kind: String) = manualSchedule.setDayMark(date, kind)
    fun resetManualData() = manualSchedule.resetAll()

    fun saveCourseReminder(
        courseName: String,
        date: LocalDate,
        startTime: String,
        endTime: String,
        location: String,
        prefs: ReminderPrefs
    ) {
        val reminder = CourseReminder(
            id = "$courseName-$date-$startTime",
            courseName = courseName,
            date = date.toString(),
            startTime = startTime,
            endTime = endTime,
            location = location,
            leadMinutes = prefs.leadMinutes,
            appReminder = prefs.appReminder,
            calendarEvent = prefs.calendarEvent,
            alarm = prefs.alarm,
            volume = prefs.volume,
            vibrate = prefs.vibrate,
            createdAt = java.time.Instant.now().toString()
        )
        container.courseReminders.add(reminder, prefs)
        if (prefs.alarm) CourseReminderScheduler.schedule(container.appContext, reminder)
        notify(reminder.resultLine())
    }

    fun saveReminder(reminder: CourseReminder, prefs: ReminderPrefs): Boolean {
        container.courseReminders.add(reminder, prefs)
        if (reminder.alarm) CourseReminderScheduler.schedule(container.appContext, reminder)
        notify(reminder.resultLine())
        return true
    }

    fun removeCourseReminder(reminder: CourseReminder) {
        container.courseReminders.remove(reminder.id)
        CourseReminderScheduler.cancel(container.appContext, reminder)
        notify("已取消提醒")
    }

    fun removeCourseReminderById(id: String) {
        val reminder = container.courseReminders.payload.value.reminders.firstOrNull { it.id == id } ?: return
        removeCourseReminder(reminder)
    }

    fun setReminderSound(value: String) = launchSetting { settingsRepo.setReminderSound(value) }
    fun setReminderVolume(value: Int) = launchSetting { settingsRepo.setReminderVolume(value) }
    fun setReminderVibrate(value: Boolean) = launchSetting { settingsRepo.setReminderVibrate(value) }
    fun setReminderVibrationMode(value: String) = launchSetting { settingsRepo.setReminderVibrationMode(value) }
    fun setReminderHeadsUp(value: Boolean) = launchSetting { settingsRepo.setReminderHeadsUp(value) }
    fun setSignReminderEnabled(value: Boolean) = launchSetting { settingsRepo.setSignReminderEnabled(value) }

    fun setTheme(mode: top.sykesp42.rin.ui.theme.ThemeMode) = launchSetting { settingsRepo.setTheme(mode.key) }
    fun setThemeAccent(value: String) = launchSetting { settingsRepo.setThemeAccent(value) }
    fun setThemeCorners(value: String) = launchSetting { settingsRepo.setThemeCorners(value) }
    fun setThemeDecorations(value: Boolean) = launchSetting { settingsRepo.setThemeDecorations(value) }
    fun setAnimationsEnabled(value: Boolean) = launchSetting { settingsRepo.setAnimationsEnabled(value) }
    fun setSplashEnabled(value: Boolean) = launchSetting { settingsRepo.setSplashEnabled(value) }
    fun setSimpleMode(value: Boolean) {
        launchSetting { settingsRepo.setSimpleMode(value) }
        _settings.value?.let { snapshot ->
            rebuild(snapshot, 0)
            if (!value) refreshJwxt(0)
        }
    }
    fun setPetEnabled(value: Boolean) = launchSetting { settingsRepo.setPetEnabled(value) }
    fun setPetContextAware(value: Boolean) = launchSetting { settingsRepo.setPetContextAware(value) }
    fun setPetWeatherEnabled(value: Boolean) {
        launchSetting { settingsRepo.setPetWeatherEnabled(value) }
        if (value) refreshPetWeather()
    }
    fun setPetScale(value: Float) = launchSetting { settingsRepo.setPetScale(value) }
    fun setPetOffsetX(value: Float) = launchSetting { settingsRepo.setPetOffsetX(value) }
    fun setPetOffsetY(value: Float) = launchSetting { settingsRepo.setPetOffsetY(value) }
    fun setNoticeOutfit(value: String) = launchSetting { settingsRepo.setNoticeOutfit(value) }
    fun setNoticePlate(value: String) = launchSetting { settingsRepo.setNoticePlate(value) }
    fun setNoticeInShade(value: Boolean) = launchSetting { settingsRepo.setNoticeInShade(value) }
    fun setNoticeInApp(value: Boolean) = launchSetting { settingsRepo.setNoticeInApp(value) }
    fun setNoticeSlotFace(slot: NoticeSlot, face: NoticeIcon?) {
        val current = _settings.value ?: return
        val map = NoticeFaces.parse(current.noticeOverrides).toMutableMap()
        if (face == null || face == slot.default) map.remove(slot.key) else map[slot.key] = face
        val raw = map.entries.joinToString(";") { (key, icon) -> "$key=${icon.name}" }
        launchSetting { settingsRepo.setNoticeOverrides(NoticeFaces.sanitize(raw)) }
    }
    fun setAvatar(name: String) = launchSetting { settingsRepo.setAvatar(name) }
    fun setAvatarFrame(scale: Float, x: Float, y: Float) = launchSetting { settingsRepo.setAvatarFrame(scale, x, y) }
    fun setProfileName(value: String) = launchSetting { settingsRepo.setProfileName(value.trim()) }
    fun setHidePersonal(value: Boolean) = launchSetting { settingsRepo.setHidePersonal(value) }
    fun setShareStyle(value: String) = launchSetting { settingsRepo.setShareStyle(value) }
    fun setShareFormat(value: String) = launchSetting { settingsRepo.setShareFormat(value) }
    fun setWeekDefaultTable(value: Boolean) = launchSetting { settingsRepo.setWeekDefaultTable(value) }
    fun setWeekDefaultFullscreen(value: Boolean) = launchSetting { settingsRepo.setWeekDefaultFullscreen(value) }
    fun setDaySwipeCrossWeek(value: Boolean) = launchSetting { settingsRepo.setDaySwipeCrossWeek(value) }
    fun setCustomThemeFocus(value: String) = launchSetting { settingsRepo.setCustomThemeFocus(value) }
    fun setCustomThemePalette(value: String) = launchSetting { settingsRepo.setCustomThemePalette(value) }
    fun setCustomThemeScale(value: Float) = launchSetting { settingsRepo.setCustomThemeScale(value) }
    fun setCustomThemeBlur(value: Float) = launchSetting { settingsRepo.setCustomThemeBlur(value) }
    fun setCustomThemeFrame(scale: Float, x: Float, y: Float) = launchSetting {
        settingsRepo.setCustomThemeFrame(scale, x, y)
    }

    fun setCustomThemeCover(uri: Uri) {
        viewModelScope.launch {
            val installed = container.customTheme.install(uri)
            if (installed == null) {
                notify("封面读取失败，换一张试试")
                return@launch
            }
            settingsRepo.setCustomThemeCover(installed.fileName, installed.seed)
            notify("封面已更新")
        }
    }

    fun clearCustomThemeCover() {
        val snapshot = _settings.value ?: return
        viewModelScope.launch {
            container.customTheme.delete(snapshot.customThemeImage)
            settingsRepo.clearCustomThemeCover()
            notify("已恢复主题封面")
        }
    }

    fun setTodayCardEnabled(id: TodayCardId, enabled: Boolean) {
        val snapshot = _settings.value ?: return
        val ordered = TodayCards.ordered(snapshot)
        val disabled = ordered.filter { card ->
            if (card == id) !enabled else !TodayCards.isEnabled(snapshot, card)
        }.map { it.key }
        viewModelScope.launch {
            settingsRepo.setTodayCards(
                disabled = (if (disabled.isEmpty()) listOf("none") else disabled).joinToString(","),
                order = snapshot.todayCardsOrder.ifBlank { TodayCards.defaultOrderCsv() },
                preset = TodayCards.PRESET_VERSION
            )
        }
    }

    fun moveTodayCard(id: TodayCardId, delta: Int) {
        val snapshot = _settings.value ?: return
        val order = TodayCards.ordered(snapshot).toMutableList()
        val index = order.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in order.indices) return
        order[index] = order[target].also { order[target] = order[index] }
        viewModelScope.launch {
            settingsRepo.setTodayCards(
                disabled = snapshot.todayCardsDisabled.ifBlank { TodayCards.defaultDisabledCsv() },
                order = order.joinToString(",") { it.key },
                preset = TodayCards.PRESET_VERSION
            )
        }
    }

    fun resetTodayCards() {
        viewModelScope.launch {
            settingsRepo.setTodayCards(
                disabled = TodayCards.defaultDisabledCsv(),
                order = TodayCards.defaultOrderCsv(),
                preset = TodayCards.PRESET_VERSION
            )
            notify("卡片已恢复默认")
        }
    }

    fun refreshPetWeather() {
        val snapshot = _settings.value
        if (snapshot?.petWeatherEnabled == false) return
        viewModelScope.launch {
            container.weather.current(force = false)?.let { _petWeather.value = it }
        }
    }

    fun pickTodayFrame(uri: Uri) {
        viewModelScope.launch {
            if (container.todayFrame.install(uri)) {
                _todayFrame.value = container.todayFrame.decode()
            } else {
                notify("图片读取失败")
            }
        }
    }

    fun clearTodayFrame() {
        container.todayFrame.clear()
        _todayFrame.value = null
    }

    fun notify(text: String) {
        _messages.tryEmit(text)
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(container) as T
            }
    }
}
