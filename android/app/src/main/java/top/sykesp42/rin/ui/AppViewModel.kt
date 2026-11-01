package top.sykesp42.rin.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.sykesp42.rin.data.ApiClient
import top.sykesp42.rin.data.ApiError
import top.sykesp42.rin.data.MonitorApi
import top.sykesp42.rin.data.ApiRoom
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.CampusNewsItem
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.HistoryEvent
import top.sykesp42.rin.data.HistoryKind
import top.sykesp42.rin.data.JwxtCalendar
import top.sykesp42.rin.data.JwxtClassroom
import top.sykesp42.rin.data.PetWeatherSnapshot
import top.sykesp42.rin.data.appVersionName
import top.sykesp42.rin.data.TodayScheduleSnapshot
import top.sykesp42.rin.ui.schedule.diffSchedule
import top.sykesp42.rin.data.User
import top.sykesp42.rin.monitor.NoticeFaces
import top.sykesp42.rin.monitor.NoticeIcon
import top.sykesp42.rin.monitor.NoticeSlot
import top.sykesp42.rin.ui.theme.ThemeMode
import top.sykesp42.rin.ui.today.TodayCards

sealed interface AuthState {
    data object Loading : AuthState
    data class LoggedOut(val reason: String = "") : AuthState
    data class LoggedIn(val user: User) : AuthState
}

data class LoginUiState(
    val busy: Boolean = false,
    val error: String = ""
)

/** A learned classroom→code pair, surfaced on Today for temporary classes. */
data class RoomShortcut(val name: String, val code: String)

data class TodayUiState(
    val loading: Boolean = false,
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class ScheduleUiState(
    val overview: ApiScheduleOverview? = null,
    val jwxtConfigured: Boolean = true,
    val loading: Boolean = false,
    val error: String = "",
    val fromCache: Boolean = false,
    val cachedAt: String = "",
    val currentWeek: Int = 0
)

data class SearchUiState(
    val query: String = "",
    val rooms: List<ApiRoom> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String = ""
)

data class ClassroomCatalogUiState(
    val loading: Boolean = false,
    val total: Int = 0,
    val campuses: List<String> = emptyList(),
    val selectedCampus: String = "",
    val refreshedAt: String = "",
    val error: String = "",
    val rooms: List<ApiRoom> = emptyList()
)

data class CalendarUiState(
    val loading: Boolean = false,
    val days: List<top.sykesp42.rin.data.JwxtCalendarDay> = emptyList(),
    val term: String = "",
    val termLabel: String = "",
    val error: String = "",
    val fromCache: Boolean = false,
    val cachedAt: String = ""
)

data class CampusNewsUiState(
    val loading: Boolean = false,
    val items: List<top.sykesp42.rin.data.CampusNewsItem> = emptyList(),
    val error: String = "",
    val fetchedAt: String = ""
)

data class HistoryUiState(
    val events: List<HistoryEvent> = emptyList(),
    val loading: Boolean = false
)





data class CourseDetailUiState(
    val courseName: String = "",
    val assessMode: String = "",
    val entries: List<top.sykesp42.rin.data.JwxtWeekEntry> = emptyList(),
    val loading: Boolean = false,
    val teacher: String = "",
    val location: String = "",
    val slots: List<top.sykesp42.rin.data.JwxtTermSlot> = emptyList(),
    val roomCode: String = "",
    val freeRoomDay: Int = 0,
    val freeRoomSections: String = "",
    val checklist: List<top.sykesp42.rin.data.ChecklistItem> = emptyList(),
    val checklistLoading: Boolean = false,
    val reminderDate: String = "",
    val reminderStart: String = "",
    val reminderEnd: String = ""
)

data class GradesUiState(
    val grades: List<top.sykesp42.rin.data.JwxtGrade> = emptyList(),
    val exams: List<top.sykesp42.rin.data.JwxtExam> = emptyList(),
    val notices: List<top.sykesp42.rin.data.JwxtNotice> = emptyList(),
    val student: top.sykesp42.rin.data.JwxtStudentInfo? = null,
    val detailFor: String = "",
    val detail: top.sykesp42.rin.data.JwxtGradeDetail? = null,
    val creditsByCategory: List<Pair<String, Double>> = emptyList(),
    val gpaByTerm: List<Pair<String, Double>> = emptyList(),
    val courseDetail: CourseDetailUiState = CourseDetailUiState(),
    val freeRooms: List<top.sykesp42.rin.data.JwxtFreeRoom> = emptyList(),
    val freeRoomLoading: Boolean = false,
    val freeRoomError: String = "",
    val freeRoomDay: Int = 0,
    val freeRoomSections: String = "0102",
    val termCourses: List<top.sykesp42.rin.data.JwxtTermCourse> = emptyList(),
    val termLoading: Boolean = false,
    val detailRoomCode: String = "",
    val detailFreeRoomDay: Int = 0,
    val detailFreeRoomSections: String = "0102",
    val difficultyProfile: List<top.sykesp42.rin.data.CourseDifficulty> = emptyList(),
    val gpa: Double? = null,
    val loading: Boolean = false,
    val error: String = "",
    val fromCache: Boolean = false,
    val cachedAt: String = "",
    val authExpired: Boolean = false
)

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    /** Manual entries, merged on top of whatever the crawler returns. */
    val manualSchedule = container.manualSchedule
    val courseReminders = container.courseReminders

    private val _settings = MutableStateFlow<AppSettings?>(null)
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    private val _today = MutableStateFlow(TodayUiState())
    val today: StateFlow<TodayUiState> = _today.asStateFlow()

    private val _schedule = MutableStateFlow(ScheduleUiState())
    val schedule: StateFlow<ScheduleUiState> = _schedule.asStateFlow()

    private val _calendar = MutableStateFlow(CalendarUiState())
    val calendar: StateFlow<CalendarUiState> = _calendar.asStateFlow()
    private var calendarJob: Job? = null

    private val _campusNews = MutableStateFlow(CampusNewsUiState())
    val campusNews: StateFlow<CampusNewsUiState> = _campusNews.asStateFlow()
    private var campusNewsJob: Job? = null

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _classroomCatalog = MutableStateFlow(ClassroomCatalogUiState())
    val classroomCatalog: StateFlow<ClassroomCatalogUiState> = _classroomCatalog.asStateFlow()
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    private val _roomShortcuts = MutableStateFlow<List<RoomShortcut>>(emptyList())
    val roomShortcuts: StateFlow<List<RoomShortcut>> = _roomShortcuts.asStateFlow()
    private var catalogRooms: List<top.sykesp42.rin.data.JwxtClassroom> = emptyList()
    private var catalogJob: Job? = null

    private val _history = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = _history.asStateFlow()


    private var qrJob: Job? = null


    private val _petWeather = MutableStateFlow<PetWeatherSnapshot?>(null)
    val petWeather: StateFlow<PetWeatherSnapshot?> = _petWeather.asStateFlow()
    private var petWeatherJob: Job? = null


    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val authMutex = Mutex()
    private var searchJob: Job? = null
    private var todayCardPresetChecked = false

    init {
        viewModelScope.launch {
            var seenConnectedAt: String? = null
            container.settings.flow.collect { value ->
                _settings.value = value
                // Notification builders run outside this scope and need the chosen
                // outfit synchronously.
                NoticeFaces.publish(value)
                top.sykesp42.rin.ui.profile.Profile.publish(value)
                if (!todayCardPresetChecked) {
                    todayCardPresetChecked = true
                    if (value.todayCardsPreset < TodayCards.PRESET_VERSION) {
                        viewModelScope.launch { applyTodayCardPreset() }
                    }
                }
                _classroomCatalog.update { it.copy(selectedCampus = value.selectedCampus) }
                // First emission = cold start with a stored connection; any later
                // change = a fresh WebView login. Both are the moment to refresh
                // every term-stable cache while the session is known good.
                val changed = seenConnectedAt != value.jwxtConnectedAt
                if (changed) {
                    seenConnectedAt = value.jwxtConnectedAt
                    if (value.jwxtConnectedAt.isNotBlank() && value.localMode) warmLocalCaches()
                }
            }
        }
        viewModelScope.launch {
            container.favorites.flow.collect {
                _favorites.value = it
                refreshRoomShortcuts()
            }
        }
        restoreSession()
    }

    fun login(serverUrl: String, username: String, password: String) {
        val url = serverUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _login.value = LoginUiState(error = "服务地址要以 http:// 或 https:// 开头")
            return
        }
        if (username.isBlank() || password.isEmpty()) {
            _login.value = LoginUiState(error = "填写用户名和密码")
            return
        }
        viewModelScope.launch {
            _login.value = LoginUiState(busy = true)
            try {
                val previous = container.settings.current().serverUrl
                if (previous.isNotBlank() && previous != url) container.sessionStore.clear()
                container.settings.setServerUrl(url)
                val api = container.client(url) ?: throw IllegalArgumentException("invalid server url")
                val user = withContext(Dispatchers.IO) { api.login(username.trim(), password) }
                container.settings.setAccount(user.username, user.displayName)
                _today.value = TodayUiState()
                _schedule.value = ScheduleUiState()
                _search.value = SearchUiState()
                _history.value = HistoryUiState()
                _auth.value = AuthState.LoggedIn(user)
                _login.value = LoginUiState()
                runCatching { container.history.append(HistoryKind.LOGIN) }
                bootstrapData()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _login.value = LoginUiState(error = ErrorCopy.login(error))
            }
        }
    }

    fun completeOnboarding() {
        if (_auth.value !is AuthState.LoggedIn) return
        viewModelScope.launch {
            container.settings.setOnboarded(true)
            _messages.emit("设置完成")
        }
    }

    fun logout() {
        viewModelScope.launch {
            val current = container.settings.current()
            runCatching {
                withContext(Dispatchers.IO) { container.client(current.serverUrl)?.logout() }
            }
            container.sessionStore.clear()
            container.settings.resetForLogout()
            runCatching { container.history.append(HistoryKind.LOGOUT) }
            _auth.value = AuthState.LoggedOut()
            _login.value = LoginUiState()
            _today.value = TodayUiState()
            _schedule.value = ScheduleUiState()
            _search.value = SearchUiState()
            _history.value = HistoryUiState()
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setTheme(mode.key) }
    }

    fun setThemeAccent(value: String) {
        viewModelScope.launch { container.settings.setThemeAccent(value) }
    }

    fun setThemeCorners(value: String) {
        viewModelScope.launch { container.settings.setThemeCorners(value) }
    }

    fun setThemeDecorations(value: Boolean) {
        viewModelScope.launch { container.settings.setThemeDecorations(value) }
    }

    fun setCustomThemeCover(uri: Uri) {
        viewModelScope.launch {
            val saved = runCatching { container.customTheme.install(uri) }.getOrNull()
            if (saved == null) {
                _messages.emit("图片读取失败，换一张试试")
                return@launch
            }
            container.settings.setCustomThemeCover(saved.fileName, saved.seed)
            _messages.emit("封面已应用")
        }
    }

    fun clearCustomThemeCover() {
        viewModelScope.launch {
            val current = container.settings.current()
            container.customTheme.delete(current.customThemeImage)
            container.settings.clearCustomThemeCover()
            _messages.emit("已还原为内置封面")
        }
    }

    fun setCustomThemeFocus(value: String) {
        viewModelScope.launch { container.settings.setCustomThemeFocus(value) }
    }

    fun setNoticeOutfit(value: String) {
        viewModelScope.launch { container.settings.setNoticeOutfit(value) }
    }

    fun setNoticePlate(value: String) {
        viewModelScope.launch { container.settings.setNoticePlate(value) }
    }

    fun setNoticeInShade(value: Boolean) {
        viewModelScope.launch { container.settings.setNoticeInShade(value) }
    }

    fun setNoticeInApp(value: Boolean) {
        viewModelScope.launch { container.settings.setNoticeInApp(value) }
    }

    fun setNoticeSlotFace(slot: NoticeSlot, face: NoticeIcon?) {
        val current = NoticeFaces.parse(_settings.value?.noticeOverrides.orEmpty()).toMutableMap()
        if (face == null || face == slot.default) current.remove(slot.key) else current[slot.key] = face
        val serialised = current.entries.joinToString(";") { (key, value) -> "$key=${value.name}" }
        viewModelScope.launch { container.settings.setNoticeOverrides(serialised) }
    }

    fun setCustomThemeScale(value: Float) {
        viewModelScope.launch { container.settings.setCustomThemeScale(value) }
    }

    fun setCustomThemeBlur(value: Float) {
        viewModelScope.launch { container.settings.setCustomThemeBlur(value) }
    }

    fun setCustomThemeFrame(scale: Float, offsetX: Float, offsetY: Float) {
        viewModelScope.launch {
            container.settings.setCustomThemeFrame(scale, offsetX, offsetY)
        }
    }

    fun setCustomThemePalette(value: String) {
        viewModelScope.launch { container.settings.setCustomThemePalette(value) }
    }

    fun notify(text: String) {
        message(text)
    }

    fun setSplashEnabled(value: Boolean) {
        viewModelScope.launch {
            container.settings.setSplashEnabled(value)
        }
    }

    fun setMinimalMode(value: Boolean) {
        viewModelScope.launch {
            container.settings.setMinimalMode(value)
        }
    }

    fun setAnimationsEnabled(value: Boolean) {
        viewModelScope.launch {
            container.settings.setAnimationsEnabled(value)
        }
    }

    fun setPetEnabled(value: Boolean) {
        viewModelScope.launch { container.settings.setPetEnabled(value) }
    }

    fun setPetContextAware(value: Boolean) {
        viewModelScope.launch { container.settings.setPetContextAware(value) }
    }

    fun setPetWeatherEnabled(value: Boolean) {
        viewModelScope.launch { container.settings.setPetWeatherEnabled(value) }
    }

    /**
     * Campus weather, cached for 30 minutes. Coordinates are the school's, so this
     * needs no permission and touches no personal data; consumers decide whether to
     * use it (the pet scenes and the cover chip both gate on their own settings).
     */
    fun refreshPetWeather() {
        if (petWeatherJob?.isActive == true) return
        petWeatherJob = viewModelScope.launch {
            container.petWeather.current()?.let { _petWeather.value = it }
        }
    }

    fun setWeekDefaultTable(value: Boolean) {
        viewModelScope.launch { container.settings.setWeekDefaultTable(value) }
    }

    fun setDaySwipeCrossWeek(value: Boolean) {
        viewModelScope.launch { container.settings.setDaySwipeCrossWeek(value) }
    }

    fun addManualCourse(course: top.sykesp42.rin.data.ManualCourse) = container.manualSchedule.addCourse(course)
    fun removeManualCourse(id: String) = container.manualSchedule.removeCourse(id)
    fun addManualExam(exam: top.sykesp42.rin.data.ManualExam) = container.manualSchedule.addExam(exam)
    fun removeManualExam(id: String) = container.manualSchedule.removeExam(id)
    fun addManualTodo(todo: top.sykesp42.rin.data.ManualTodo) = container.manualSchedule.addTodo(todo)
    fun toggleManualTodo(id: String) = container.manualSchedule.toggleTodo(id)
    fun removeManualTodo(id: String) = container.manualSchedule.removeTodo(id)
    fun addManualMove(move: top.sykesp42.rin.data.ManualMove) = container.manualSchedule.addMove(move)
    fun removeManualMove(id: String) = container.manualSchedule.removeMove(id)
    fun setManualDayMark(date: String, kind: String) = container.manualSchedule.setDayMark(date, kind)

    /** 重置数据：drop every manual entry and keep the crawled schedule. */
    fun resetManualData() {
        container.manualSchedule.resetAll()
        notify("已清除手动数据")
    }

    /** One-off course reminder: save, arm the alarm, and optionally open the calendar. */
    fun saveCourseReminder(
        reminder: top.sykesp42.rin.data.CourseReminder,
        prefs: top.sykesp42.rin.data.ReminderPrefs
    ): Boolean {
        val trigger = reminder.triggerAt()
        if (trigger == null || !trigger.isAfter(java.time.LocalDateTime.now())) {
            notify("提醒时间已过")
            return false
        }
        container.courseReminders.add(reminder, prefs)
        if (reminder.appReminder || reminder.alarm) {
            top.sykesp42.rin.reminder.CourseReminderScheduler.schedule(
                container.appContext,
                reminder
            )
        }
        if (reminder.calendarEvent) {
            val opened = top.sykesp42.rin.reminder.CourseReminderCalendar.insert(
                container.appContext,
                reminder
            )
            if (!opened) notify("没有可用的系统日历")
        }
        notify(reminder.resultLine())
        return true
    }

    fun removeCourseReminder(id: String) {
        val reminder = container.courseReminders.payload.value.reminders.firstOrNull { it.id == id }
        if (reminder != null) {
            top.sykesp42.rin.reminder.CourseReminderScheduler.cancel(container.appContext, reminder)
        }
        container.courseReminders.remove(id)
        notify("已取消提醒")
    }

    fun setReminderSound(value: String) {
        viewModelScope.launch { container.settings.setReminderSound(value) }
    }

    fun setReminderVolume(value: Int) {
        viewModelScope.launch { container.settings.setReminderVolume(value) }
    }

    fun setReminderVibrate(value: Boolean) {
        viewModelScope.launch { container.settings.setReminderVibrate(value) }
    }

    fun setReminderVibrationMode(value: String) {
        viewModelScope.launch { container.settings.setReminderVibrationMode(value) }
    }

    fun setReminderHeadsUp(value: Boolean) {
        viewModelScope.launch { container.settings.setReminderHeadsUp(value) }
    }

    fun setTodayCardEnabled(id: top.sykesp42.rin.ui.today.TodayCardId, enabled: Boolean) {
        viewModelScope.launch {
            val current = container.settings.current()
            val stored = current.todayCardsDisabled
            val disabled = if (stored.isBlank()) {
                top.sykesp42.rin.ui.today.TodayCardId.entries
                    .filterNot { it.defaultEnabled }
                    .map { it.key }
                    .toMutableSet()
            } else {
                stored.split(',').filter { it.isNotBlank() }.toMutableSet()
            }
            if (enabled) disabled.remove(id.key) else disabled.add(id.key)
            container.settings.setTodayCardsDisabled(
                if (disabled.isEmpty()) "none" else disabled.joinToString(",")
            )
            container.settings.setTodayCardsPreset(TodayCards.PRESET_VERSION)
        }
    }

    fun resetTodayCards() {
        viewModelScope.launch {
            applyTodayCardPreset()
            notify("已恢复默认卡片顺序")
        }
    }

    private suspend fun applyTodayCardPreset() {
        container.settings.setTodayCardsDisabled(TodayCards.defaultDisabledCsv())
        container.settings.setTodayCardsOrder(TodayCards.defaultOrderCsv())
        container.settings.setTodayCardsPreset(TodayCards.PRESET_VERSION)
    }

    fun moveTodayCard(id: top.sykesp42.rin.ui.today.TodayCardId, delta: Int) {
        viewModelScope.launch {
            val current = container.settings.current()
            val order = top.sykesp42.rin.ui.today.TodayCards.ordered(current).toMutableList()
            val index = order.indexOf(id)
            val target = (index + delta).coerceIn(0, order.lastIndex)
            if (index < 0 || index == target) return@launch
            order.removeAt(index)
            order.add(target, id)
            container.settings.setTodayCardsOrder(order.joinToString(",") { it.key })
            container.settings.setTodayCardsPreset(TodayCards.PRESET_VERSION)
        }
    }

    fun setShareStyle(value: String) {
        viewModelScope.launch { container.settings.setShareStyle(value) }
    }

    fun setShareFormat(value: String) {
        viewModelScope.launch { container.settings.setShareFormat(value) }
    }

    fun setProfileName(value: String) {
        viewModelScope.launch { container.settings.setProfileName(value) }
    }

    fun setHidePersonal(value: Boolean) {
        viewModelScope.launch {
            container.settings.setHidePersonal(value)
            val current = _settings.value
            when {
                // Turning privacy on without a name: mint a fresh pseudonym each time.
                value && current?.profileName.isNullOrBlank() ->
                    container.settings.setProfileNameAuto(top.sykesp42.rin.ui.Identity.randomName())
                // Turning it off: drop a generated name, keep one the user typed.
                !value && current?.profileNameAuto == true ->
                    container.settings.setProfileName("")
            }
        }
    }

    fun setAvatar(fileName: String) {
        viewModelScope.launch { container.settings.setAvatar(fileName) }
    }

    fun setAvatarFrame(scale: Float, offsetX: Float, offsetY: Float) {
        viewModelScope.launch { container.settings.setAvatarFrame(scale, offsetX, offsetY) }
    }

    fun setPetScale(value: Float) {
        viewModelScope.launch { container.settings.setPetScale(value) }
    }

    fun setPetOffsetX(value: Float) {
        viewModelScope.launch { container.settings.setPetOffsetX(value) }
    }

    fun setPetOffsetY(value: Float) {
        viewModelScope.launch { container.settings.setPetOffsetY(value) }
    }

    fun setWeekDefaultFullscreen(value: Boolean) {
        viewModelScope.launch { container.settings.setWeekDefaultFullscreen(value) }
    }

    private val _grades = MutableStateFlow(GradesUiState())
    val grades: StateFlow<GradesUiState> = _grades.asStateFlow()

    private val _courseOverrides = MutableStateFlow<Map<String, top.sykesp42.rin.data.CourseOverride>>(emptyMap())
    val courseOverrides: StateFlow<Map<String, top.sykesp42.rin.data.CourseOverride>> = _courseOverrides.asStateFlow()

    fun setCourseOverride(courseName: String, alias: String, colorIndex: Int) {
        viewModelScope.launch {
            runCatching {
                container.courseOverrides.save(
                    courseName,
                    top.sykesp42.rin.data.CourseOverride(alias.trim(), colorIndex)
                )
            }
            _courseOverrides.value = runCatching { container.courseOverrides.all() }.getOrDefault(emptyMap())
        }
    }

    init {
        viewModelScope.launch {
            _courseOverrides.value = runCatching { container.courseOverrides.all() }.getOrDefault(emptyMap())
        }
        viewModelScope.launch {
            container.courseReminders.prune()
            top.sykesp42.rin.reminder.CourseReminderScheduler.rearmAll(
                container.appContext,
                container.courseReminders.payload.value.reminders
            )
        }
    }

    /** Save JWXT credentials and try a programmatic login right away. */
    fun saveJwxtCredentials(account: String, password: String) {
        if (account.isBlank() || password.isEmpty()) {
            message("填写教务账号和密码")
            return
        }
        viewModelScope.launch {
            val settings = container.settings.current()
            try {
                withContext(Dispatchers.IO) {
                    container.signInJwxt(settings, account.trim(), password)
                }
                container.settings.setJwxtConnectedAt(Instant.now().toString())
                loadSchedule()
                // force: the cache render above must not shadow the fresh
                // fetch this login just made possible.
                loadTermCourses(force = true)
                loadGrades(force = true)
                _messages.emit("教务已连接，只保存加密的账号密码")
            } catch (error: top.sykesp42.rin.data.LocalJwxtApi.JwxtCaptchaRequired) {
                _messages.emit("这次要验证码：点「登录教务」在网页里登录一次")
            } catch (error: Exception) {
                _messages.emit("教务连接失败：${error.message ?: "网络异常"}")
            }
        }
    }

    /** Course detail: summary slots + assessment + teaching calendar + linkage. */
    fun openCourseDetail(
        courseName: String,
        locationHint: String = "",
        occurrenceDate: String = "",
        occurrenceStart: String = "",
        occurrenceEnd: String = ""
    ) {
        _grades.update {
            it.copy(
                courseDetail = CourseDetailUiState(
                    courseName = courseName,
                    loading = true,
                    checklistLoading = true,
                    reminderDate = occurrenceDate,
                    reminderStart = occurrenceStart,
                    reminderEnd = occurrenceEnd
                )
            )
        }
        // The checklist is a local read; it must never wait for the teaching
        // calendar or the term-course request.
        viewModelScope.launch {
            val items = runCatching { container.checklists.items(courseName) }.getOrDefault(emptyList())
            _grades.update { current ->
                if (current.courseDetail.courseName == courseName) {
                    current.copy(
                        courseDetail = current.courseDetail.copy(
                            checklist = items,
                            checklistLoading = false
                        )
                    )
                } else {
                    current
                }
            }
        }
        viewModelScope.launch {
            val settings = container.settings.current()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    container.withJwxt(settings) { api ->
                        val tasks = api.fetchCourseTasks("")
                        val task = tasks.firstOrNull { task -> task.courseName.startsWith(courseName.take(4)) }
                        val calendar = task?.let { runCatching { api.fetchTeachingCalendar(it.taskId) }.getOrElse { emptyList() } }
                        task to (calendar ?: emptyList())
                    }
                }.getOrNull()
            }
            val termCourses = _grades.value.termCourses.ifEmpty {
                withContext(Dispatchers.IO) {
                    runCatching {
                        container.withJwxt(settings) { api -> api.fetchTermCourses() }
                    }.getOrNull() ?: emptyList()
                }
            }
            val matched = termCourses.firstOrNull { course ->
                course.courseName == courseName || course.courseName.startsWith(courseName.take(4))
            }
            // Term-course rows can omit the room even when the schedule event has
            // it. Use the event location as a fallback for both display and the
            // verified-code lookup.
            val location = matched?.location?.takeIf { it.isNotBlank() } ?: locationHint
            val firstSlot = matched?.slots?.firstOrNull()
            val code = ""
            _grades.update { current ->
                val previous = current.courseDetail
                val sameCourse = previous.courseName == courseName
                current.copy(
                    termCourses = termCourses,
                    courseDetail = CourseDetailUiState(
                        courseName = courseName,
                        assessMode = result?.first?.assessMode ?: "",
                        entries = result?.second ?: emptyList(),
                        loading = false,
                        teacher = matched?.teacher ?: "",
                        location = location,
                        slots = matched?.slots ?: emptyList(),
                        roomCode = code,
                        freeRoomDay = firstSlot?.weekday ?: 0,
                        freeRoomSections = firstSlot?.sectionText
                            ?.split(",")?.joinToString("") { section -> section.padStart(2, '0') } ?: "",
                        checklist = if (sameCourse) previous.checklist else emptyList(),
                        checklistLoading = if (sameCourse) previous.checklistLoading else false,
                        reminderDate = if (sameCourse) previous.reminderDate else "",
                        reminderStart = if (sameCourse) previous.reminderStart else "",
                        reminderEnd = if (sameCourse) previous.reminderEnd else ""
                    )
                )
            }
        }
    }

    fun dismissCourseDetail() {
        _grades.update { it.copy(courseDetail = CourseDetailUiState()) }
    }

    /** Load the schedule for another week (local mode supports arbitrary weeks). */
    fun switchWeek(delta: Int) {
        val currentWeek = _schedule.value.overview?.week ?: return
        val target = currentWeek + delta
        if (target < 1 || target > 30) return
        viewModelScope.launch {
            val settings = container.settings.current()
            val local = container.localJwxtApi(settings)
            if (local == null) {
                _messages.emit("换周只在本地模式可用")
                return@launch
            }
            _schedule.update { it.copy(loading = true, error = "") }
            // withJwxt re-logins on session expiry; the direct call below is
            // only the no-credential fallback with the old silent behavior.
            val overview = withContext(Dispatchers.IO) {
                runCatching {
                    container.withJwxt(settings) { api -> api.overview(weekOverride = target) }
                        ?: local.overview(weekOverride = target)
                }.getOrNull()
            }
            if (overview != null) {
                container.localCache.write(
                    "schedule_week$target",
                    top.sykesp42.rin.data.ApiScheduleOverview.serializer(),
                    overview
                )
                _schedule.update { it.copy(overview = overview, loading = false, fromCache = false) }
                return@launch
            }
            val cached = container.localCache.read(
                "schedule_week$target",
                top.sykesp42.rin.data.ApiScheduleOverview.serializer()
            )
            if (cached != null) {
                _schedule.update {
                    it.copy(overview = cached.second, loading = false, fromCache = true, cachedAt = cached.first.toString())
                }
            } else {
                _schedule.update { it.copy(loading = false, error = "第 $target 周没拉到") }
            }
        }
    }

    /** Free classrooms for a weekday/sections pair (JWXT read-only query). */
    fun loadFreeRooms(day: Int, sections: String) {
        if (_grades.value.freeRoomLoading) return
        _grades.update {
            it.copy(freeRoomLoading = true, freeRoomError = "", freeRoomDay = day, freeRoomSections = sections)
        }
        viewModelScope.launch {
            val settings = container.settings.current()
            val week = _schedule.value.overview?.week ?: 0
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    container.withJwxt(settings) { api -> api.fetchFreeClassrooms(day, week, sections) }
                }
            }
            val rooms = result.getOrNull().orEmpty()
            _grades.update {
                it.copy(
                    freeRooms = rooms,
                    freeRoomLoading = false,
                    freeRoomError = when {
                        result.isFailure -> ErrorCopy.from(result.exceptionOrNull()!!)
                        result.getOrNull() == null -> "教务登录失效了，重新连接后再查。"
                        else -> ""
                    }
                )
            }
            val campuses = rooms.map { it.campus }.filter { it.isNotBlank() }.distinct()
            if (campuses.isNotEmpty()) {
                _classroomCatalog.update { state ->
                    state.copy(campuses = (state.campuses + campuses).distinct().sorted())
                }
            }
        }
    }

    /** Whole-term course summary (term overview screen), refreshed by TTL. */
    fun loadTermCourses(force: Boolean = false) {
        if (_grades.value.termLoading) return
        val listSerializer = kotlinx.serialization.builtins.ListSerializer(top.sykesp42.rin.data.JwxtTermCourse.serializer())
        val cached = container.localCache.read(TERM_CACHE, listSerializer)
        if (cached != null && cached.second.isNotEmpty()) {
            _grades.update { it.copy(termCourses = cached.second, termLoading = false) }
            if (!force && top.sykesp42.rin.data.isCacheFresh(cached.first, TERM_CACHE_TTL)) return
        } else if (!force && _grades.value.termCourses.isNotEmpty()) {
            // Already hydrated by an earlier live fetch; reuse until it is stale.
            val age = container.localCache.cachedAt(TERM_CACHE)
            if (age != null && top.sykesp42.rin.data.isCacheFresh(age, TERM_CACHE_TTL)) return
        }
        _grades.update { it.copy(termLoading = true) }
        viewModelScope.launch {
            val settings = container.settings.current()
            val courses = withContext(Dispatchers.IO) {
                runCatching {
                    container.withJwxt(settings) { api -> api.fetchTermCourses() }
                }.getOrNull() ?: emptyList()
            }
            if (courses.isNotEmpty()) {
                container.localCache.write(TERM_CACHE, listSerializer, courses)
                _grades.update { it.copy(termCourses = courses, termLoading = false) }
            } else {
                // Keep the cached term schedule; an expired session must not wipe it.
                _grades.update { it.copy(termLoading = false) }
            }
        }
    }

    /** Room code (if learned) for a course location, cached at detail open. */
    fun exportGradesCsv(): String =
        top.sykesp42.rin.data.DataExporter.gradesCsv(_grades.value.grades)

    fun exportScheduleIcal(): String? {
        val courses = _grades.value.termCourses
        if (courses.isEmpty()) return null
        // School clock: the term anchor must not shift with the device timezone.
        val termStart = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
            .with(java.time.DayOfWeek.MONDAY)
            .minusWeeks((_schedule.value.overview?.week ?: 1) - 1L)
        return top.sykesp42.rin.data.DataExporter.scheduleIcal(
            courses, termStart, _schedule.value.overview?.term ?: "学期"
        )
    }

    fun saveChecklist(courseName: String, items: List<top.sykesp42.rin.data.ChecklistItem>) {
        _grades.update { current ->
            if (current.courseDetail.courseName == courseName) {
                current.copy(courseDetail = current.courseDetail.copy(checklist = items))
            } else {
                current
            }
        }
        viewModelScope.launch {
            container.checklists.save(courseName, items)
        }
    }

    /** Toggle the score-composition detail of one grade card. */
    fun toggleGradeDetail(recordId: String) {
        val current = _grades.value
        if (current.detailFor == recordId) {
            _grades.update { it.copy(detailFor = "", detail = null) }
            return
        }
        _grades.update { it.copy(detailFor = recordId, detail = null) }
        viewModelScope.launch {
            val settings = container.settings.current()
            val detail = withContext(Dispatchers.IO) {
                runCatching {
                    container.withJwxt(settings) { api -> api.fetchGradeDetail(recordId) }
                }.getOrNull()
            }
            if (_grades.value.detailFor == recordId) {
                _grades.update { it.copy(detail = detail) }
            }
        }
    }

    /** Fill roster matching name from the JWXT student card. */
    fun loadGrades(force: Boolean = false) {
        if (_grades.value.loading) return
        val cached = container.localCache.read(
            GRADES_CACHE,
            top.sykesp42.rin.data.JwxtGradeBundle.serializer()
        )
        if (cached != null) {
            applyGradeBundle(cached.second, fromCache = true, cachedAt = cached.first.toString())
            if (!force && top.sykesp42.rin.data.isCacheFresh(cached.first, GRADES_CACHE_TTL)) {
                _grades.update { it.copy(error = "") }
                return
            }
        }
        _grades.update { it.copy(loading = true, error = "", authExpired = false) }
        viewModelScope.launch {
            val settings = container.settings.current()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    container.withJwxt(settings) { api ->
                        top.sykesp42.rin.data.JwxtGradeBundle(
                            grades = runCatching { api.fetchGrades() }.getOrElse { emptyList() },
                            exams = runCatching { api.fetchExams() }.getOrElse { emptyList() },
                            notices = runCatching { api.fetchNotices() }.getOrElse { emptyList() },
                            student = runCatching { api.fetchStudentInfo() }.getOrNull()
                        )
                    }
                }.getOrNull()
            }
            if (result == null || top.sykesp42.rin.data.isEmptyGradeBundle(result)) {
                if (_grades.value.grades.isEmpty() && _grades.value.exams.isEmpty()) {
                    _grades.update {
                        it.copy(
                            loading = false,
                            error = "教务登录已失效，重新连接后才能读取成绩。",
                            authExpired = true
                        )
                    }
                } else {
                    _grades.update { it.copy(loading = false, fromCache = true) }
                }
                return@launch
            }
            container.localCache.write(GRADES_CACHE, top.sykesp42.rin.data.JwxtGradeBundle.serializer(), result)
            applyGradeBundle(result, fromCache = false, cachedAt = Instant.now().toString())
        }
    }

    private fun applyGradeBundle(bundle: top.sykesp42.rin.data.JwxtGradeBundle, fromCache: Boolean, cachedAt: String) {
        _grades.update {
            it.copy(
                grades = bundle.grades,
                exams = bundle.exams,
                notices = bundle.notices,
                student = bundle.student,
                creditsByCategory = top.sykesp42.rin.data.creditsByCategory(bundle.grades),
                gpaByTerm = top.sykesp42.rin.data.gpaByTerm(bundle.grades),
                difficultyProfile = top.sykesp42.rin.data.StatsCalculator.difficultyProfile(bundle.grades),
                gpa = top.sykesp42.rin.data.weightedGpa(bundle.grades),
                loading = false,
                fromCache = fromCache,
                cachedAt = cachedAt,
                authExpired = false
            )
        }
    }

    fun ensureScheduleLoaded() {
        if (_schedule.value.overview == null && !_schedule.value.loading) loadSchedule()
    }

    /** A user-initiated refresh may retry the school after the backoff window. */
    fun retryJwxtNow() {
        container.noteJwxtHealthy()
    }

    /** Validate/renew the JWXT cookie now and refresh stale offline caches. */
    fun renewJwxtSession() {
        viewModelScope.launch {
            val current = container.settings.current()
            if (!current.localMode) {
                message("云端模式不使用本机教务会话")
                return@launch
            }
            if (current.jwxtNeedsLogin) {
                message("需要网页重连：在课表横幅点「重连」完成滑块验证")
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching { container.keepJwxtSession(current) }.getOrDefault(false)
            }
            if (ok) {
                message("教务会话已续期，正在刷新缓存")
                loadSchedule()
                loadTermCourses(force = true)
                loadGrades(force = true)
                loadCalendar()
                refreshClassroomCatalog(notify = false)
            } else {
                message("续期失败：保留已有缓存，可稍后重试或重新登录")
            }
        }
    }

    fun loadSchedule(weekOverride: Int = 0) {
        if (_schedule.value.loading && weekOverride == 0) return
        _schedule.update { it.copy(loading = true, error = "") }
        viewModelScope.launch {
            try {
                val current = container.settings.current()
                val cacheName = "schedule_week${weekOverride.takeIf { it > 0 } ?: 0}"
                val cached = container.localCache.read(cacheName, top.sykesp42.rin.data.ApiScheduleOverview.serializer())
                if (cached != null && cached.second.days.isNotEmpty()) {
                    // Show the stored week immediately — including week 0, so a
                    // dead JWXT session still opens onto real courses instead of
                    // an empty page while the refresh is in flight.
                    _schedule.update {
                        it.copy(
                            overview = cached.second,
                            jwxtConfigured = configuredFlag(current),
                            loading = if (weekOverride > 0) false else it.loading,
                            fromCache = true,
                            cachedAt = cached.first.toString()
                        )
                    }
                }
                var fromCacheNow = false
                var fetched = withContext(Dispatchers.IO) {
                    val local = container.localJwxtApi(current)
                    when {
                        current.localMode && weekOverride == 0 -> {
                            // Ask JWXT directly so live and cache are distinguishable.
                            // Equality with the cached copy is not proof of fallback.
                            val live = runCatching {
                                container.withJwxt(current) { api -> api.overview() }
                            }.getOrNull()
                            if (live != null && (live.days.isNotEmpty() || live.today.isNotEmpty())) {
                                live
                            } else {
                                val fallback = cached?.second
                                if (fallback != null) {
                                    fromCacheNow = true
                                    fallback
                                } else {
                                    throw IllegalStateException("课表未缓存")
                                }
                            }
                        }
                        local != null && weekOverride > 0 -> {
                            // Same renewal rule as switchWeek: browsing another
                            // week must survive an expired JWXT session. withJwxt
                            // returns null only when renewal is impossible; the
                            // direct call then keeps the old throw-to-cache path.
                            container.withJwxt(current) { api -> api.overview(weekOverride = weekOverride) }
                                ?: local.overview(weekOverride = weekOverride)
                        }
                        else -> (local
                            ?: throw IllegalStateException("教务未连接"))
                            .overview(weekOverride = weekOverride.takeIf { it > 0 } ?: 0)
                    }
                }
                var cachedAtNow = ""
                val todayStr = java.time.LocalDate.now(
                    java.time.ZoneId.of("Asia/Shanghai")
                ).toString()
                if (current.localMode && fetched.days.isNotEmpty() &&
                    fetched.days.none { it.date == todayStr } &&
                    fetched.date != todayStr
                ) {
                    // Cached overview is from a different day — stale.
                    // Try refreshing before showing stale data.
                    val refreshed = withContext(Dispatchers.IO) {
                        runCatching {
                            container.withJwxt(current) { api -> api.overview() }
                        }.getOrNull()
                    }
                    if (refreshed != null && refreshed.days.isNotEmpty()) fetched = refreshed
                }
                // A stale date only affects the `today` snapshot; the week grid
                // itself stays intact. Keep every day's events for 周/表.
                fetched = top.sykesp42.rin.ui.schedule.withToday(
                    fetched,
                    java.time.LocalDate.parse(todayStr)
                )
                if (current.localMode && fetched.days.isEmpty()) {
                    // Session may be dead: keep showing the cached week.
                    container.localCache.read(cacheName, top.sykesp42.rin.data.ApiScheduleOverview.serializer())
                        ?.let { cached ->
                            if (cached.second.days.isNotEmpty()) {
                                fetched = cached.second
                                fromCacheNow = true
                                cachedAtNow = cached.first.toString()
                            }
                        }
                } else if (current.localMode && weekOverride > 0 &&
                    cached != null && fetched == cached.second
                ) {
                    // The live provider already fell back to this same cached
                    // overview; do not present it as a fresh network result.
                    fromCacheNow = true
                    cachedAtNow = cached.first.toString()
                }
                val overview = fetched.also { fresh ->
                    if (current.localMode && !fromCacheNow && fresh.days.isNotEmpty()) {
                        container.localCache.write(cacheName, top.sykesp42.rin.data.ApiScheduleOverview.serializer(), fresh)
                    }
                }
                if (weekOverride == 0 && !fromCacheNow) {
                    checkScheduleChanges(overview, todayStr)
                }
                // Arm today's alarm-only class reminders. They deliberately do
                // not depend on 后台监控: a reminder that needs the service is a
                // reminder that dies with it. Cached data is good enough here.
                if (weekOverride == 0) {
                    runCatching {
                        container.classReminders.reschedule(overview)
                    }
                }
                val configured = container.localJwxtStore.load() != null
                _schedule.value = ScheduleUiState(
                    overview = overview,
                    jwxtConfigured = configured,
                    loading = false,
                    fromCache = fromCacheNow,
                    // Keep the last successful load time even when the data was
                    // live then; once the session expires this is the honest
                    // "缓存 · HH:mm" age shown on Today and Schedule.
                    cachedAt = if (fromCacheNow) cachedAtNow else Instant.now().toString(),
                    currentWeek = if (weekOverride == 0) overview.week else _schedule.value.currentWeek
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiError) {
                if (ErrorCopy.isAuth(error.code, error.status)) {
                    expireSession("登录已失效，重新登录")
                } else if (error.code == "JWXT_ACCOUNT_REQUIRED") {
                    _schedule.update { it.copy(loading = false, jwxtConfigured = false, error = "") }
                } else {
                    _schedule.update { it.copy(loading = false, error = ErrorCopy.from(error)) }
                }
            } catch (error: Exception) {
                val cached = container.localCache.read(
                    "schedule_week${weekOverride.takeIf { it > 0 } ?: 0}",
                    top.sykesp42.rin.data.ApiScheduleOverview.serializer()
                )
                if (cached != null) {
                    _schedule.update {
                        it.copy(
                            overview = cached.second,
                            jwxtConfigured = true,
                            loading = false,
                            fromCache = true,
                            cachedAt = cached.first.toString()
                        )
                    }
                } else {
                    _schedule.update { it.copy(loading = false, error = ErrorCopy.from(error)) }
                }
            }
        }
    }

    /**
     * Alert once when today's timetable changes (room/time/added/canceled).
     * The first fetch of a day only records a baseline; cached replays and
     * transient empty responses never alert.
     */
    private suspend fun checkScheduleChanges(overview: ApiScheduleOverview, today: String) {
        val events = (overview.days.firstOrNull { it.date == today }?.events ?: overview.today)
            .sortedBy { it.startTime }
        val previous = withContext(Dispatchers.IO) {
            runCatching { container.scheduleChanges.load() }.getOrNull()?.second
        }
        // A sudden empty result is far more likely a bad fetch than a day where
        // every class was canceled at once.
        if (events.isEmpty() && previous?.events?.isNotEmpty() == true) return
        withContext(Dispatchers.IO) {
            runCatching {
                container.scheduleChanges.save(TodayScheduleSnapshot(date = today, events = events))
            }
        }
        if (previous == null || previous.date != today) return
        val changes = diffSchedule(previous.events, events)
        if (changes.isEmpty()) return
        val first = changes.first()
        val text = if (changes.size == 1) {
            first.message()
        } else {
            "${first.message()}（共 ${changes.size} 处）"
        }
        _messages.emit(text)
        runCatching { container.notifications.showScheduleChange(text) }
        runCatching {
            container.history.append(
                HistoryKind.SCHEDULE_CHANGE,
                first.courseName,
                first.newLocation.ifBlank { first.oldLocation },
                first.kind.name
            )
        }
    }

    /** Read the cached calendar immediately, then refresh only when stale. */
    fun ensureCalendarLoaded() {
        if (_calendar.value.loading || calendarJob?.isActive == true) return
        calendarJob = viewModelScope.launch {
            val cached = readCalendarCache()
            if (cached != null) {
                _calendar.update {
                    it.copy(
                        days = cached.second.days,
                        term = cached.second.term,
                        termLabel = cached.second.termLabel,
                        fromCache = true,
                        cachedAt = cached.first.toString(),
                        loading = false
                    )
                }
                if (top.sykesp42.rin.data.isCacheFresh(cached.first, CALENDAR_CACHE_TTL)) {
                    _calendar.update { it.copy(error = "") }
                    return@launch
                }
            }
            loadCalendarInternal()
        }
    }

    fun loadCalendar() {
        if (calendarJob?.isActive == true) return
        calendarJob = viewModelScope.launch { loadCalendarInternal() }
    }

    private suspend fun loadCalendarInternal() {
        val current = container.settings.current() ?: return
        val api = container.localJwxtApi(current)
        if (api == null) {
            val cached = readCalendarCache()
            _calendar.update {
                it.copy(
                    loading = false,
                    days = cached?.second?.days ?: it.days,
                    term = cached?.second?.term ?: it.term,
                    termLabel = cached?.second?.termLabel ?: it.termLabel,
                    fromCache = cached != null,
                    cachedAt = cached?.first?.toString() ?: it.cachedAt,
                    error = if (cached == null) "连上教务后才能查看校历。" else "刷新失败，显示缓存"
                )
            }
            return
        }
        _calendar.update { it.copy(loading = true, error = "") }
        val termLabel = _schedule.value.overview?.term.orEmpty()
        val direct = withContext(Dispatchers.IO) {
            runCatching { api.fetchCalendar(termLabel) }
        }
        var fetched = direct.getOrNull()
        var failure = direct.exceptionOrNull()
        if (fetched == null || fetched.days.isEmpty()) {
            // Same recovery as the catalog: a stale JWXT cookie can surface as
            // a generic parse/HTTP failure, so retry once through withJwxt.
            val retry = withContext(Dispatchers.IO) {
                runCatching { container.withJwxt(current) { it.fetchCalendar(termLabel) } }
            }
            val fresh = retry.getOrNull()
            if (fresh != null && fresh.days.isNotEmpty()) {
                fetched = fresh
                failure = null
            } else if (failure == null) {
                failure = retry.exceptionOrNull()
            }
        }
        if (fetched != null && fetched.days.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    container.localCache.write(CALENDAR_CACHE, JwxtCalendar.serializer(), fetched)
                }
            }
            _calendar.update {
                it.copy(
                    days = fetched.days,
                    term = fetched.term,
                    termLabel = fetched.termLabel,
                    loading = false,
                    fromCache = false,
                    cachedAt = "",
                    error = ""
                )
            }
            return
        }
        val cached = readCalendarCache()
        if (cached != null) {
            _calendar.update {
                it.copy(
                    days = cached.second.days,
                    term = cached.second.term,
                    termLabel = cached.second.termLabel,
                    loading = false,
                    fromCache = true,
                    cachedAt = cached.first.toString(),
                    error = "刷新失败，显示缓存"
                )
            }
        } else {
            val error = when (failure) {
                is top.sykesp42.rin.data.LocalJwxtApi.JwxtSessionExpired ->
                    "教务会话已过期，去「我的」重连"
                null -> "校历为空，学校暂未发布"
                else -> "校历没拉到，稍后重试"
            }
            _calendar.update { it.copy(loading = false, error = error) }
        }
    }

    private suspend fun readCalendarCache(): Pair<Instant, JwxtCalendar>? =
        withContext(Dispatchers.IO) {
            runCatching {
                container.localCache.read(CALENDAR_CACHE, JwxtCalendar.serializer())
            }.getOrNull()
        }

    /** Show cached news first; refresh at most every few hours unless forced. */
    fun ensureCampusNewsLoaded() {
        if (campusNewsJob?.isActive == true) return
        campusNewsJob = viewModelScope.launch {
            val cached = readCampusNewsCache()
            if (cached != null) {
                _campusNews.update {
                    it.copy(items = cached.second, fetchedAt = cached.first.toString())
                }
                val fresh = cached.second.isNotEmpty() &&
                    Duration.between(cached.first, Instant.now()) < Duration.ofHours(6)
                if (fresh) {
                    _campusNews.update { it.copy(loading = false) }
                    return@launch
                }
            }
            loadCampusNewsInternal()
        }
    }

    fun refreshCampusNews() {
        if (campusNewsJob?.isActive == true) return
        campusNewsJob = viewModelScope.launch { loadCampusNewsInternal() }
    }

    private suspend fun loadCampusNewsInternal() {
        _campusNews.update { it.copy(loading = true, error = "") }
        val result = withContext(Dispatchers.IO) {
            runCatching { container.campusNews.fetch() }
        }
        val items = result.getOrNull()
        if (!items.isNullOrEmpty()) {
            val now = Instant.now()
            withContext(Dispatchers.IO) {
                runCatching {
                    container.localCache.write(
                        CAMPUS_NEWS_CACHE,
                        kotlinx.serialization.builtins.ListSerializer(CampusNewsItem.serializer()),
                        items
                    )
                }
            }
            _campusNews.update {
                it.copy(items = items, loading = false, fetchedAt = now.toString(), error = "")
            }
            return
        }
        val cached = readCampusNewsCache()
        val shown = cached?.second?.takeIf { it.isNotEmpty() } ?: _campusNews.value.items
        _campusNews.update {
            it.copy(
                loading = false,
                items = shown,
                fetchedAt = cached?.first?.toString() ?: it.fetchedAt,
                error = if (shown.isNotEmpty()) "刷新失败，显示缓存" else "校园要闻没拉到，稍后重试"
            )
        }
    }

    private suspend fun readCampusNewsCache(): Pair<Instant, List<CampusNewsItem>>? =
        withContext(Dispatchers.IO) {
            runCatching {
                container.localCache.read(
                    CAMPUS_NEWS_CACHE,
                    kotlinx.serialization.builtins.ListSerializer(CampusNewsItem.serializer())
                )
            }.getOrNull()
        }

    fun ensureClassroomCatalogLoaded() {
        val current = _settings.value ?: return
        if (!current.localMode) return
        if (catalogJob?.isActive == true) return
        val cached = container.classrooms.cached()
        if (cached != null) {
            applyClassroomCatalog(cached.second, cached.first)
            if (top.sykesp42.rin.data.isCacheFresh(cached.first, CLASSROOM_CACHE_TTL)) return
        } else if (catalogRooms.isNotEmpty()) {
            return
        }
        refreshClassroomCatalog(notify = false)
    }

    fun refreshClassroomCatalog(notify: Boolean = true) {
        if (catalogJob?.isActive == true) return
        val current = _settings.value ?: return
        if (!current.localMode) {
            _classroomCatalog.update { it.copy(loading = false, error = "本地模式未开启") }
            return
        }
        catalogJob = viewModelScope.launch {
            _classroomCatalog.update { it.copy(loading = true, error = "") }
            val api = container.localJwxtApi(current)
            if (api == null) {
                _classroomCatalog.update {
                    it.copy(loading = false, error = "教务登录失效了，重新连接后再试。")
                }
                return@launch
            }
            val direct = withContext(Dispatchers.IO) {
                runCatching { api.fetchClassroomCatalog() }
            }
            var fresh = direct.getOrNull()
            var failure = direct.exceptionOrNull()
            if (fresh == null && failure != null) {
                // A stale JWXT cookie usually surfaces as a login-page parse
                // failure, not JwxtSessionExpired. Retry once through withJwxt,
                // which uses the encrypted credentials for a fresh login.
                fresh = withContext(Dispatchers.IO) {
                    runCatching {
                        container.withJwxt(current) { it.fetchClassroomCatalog() }
                    }.getOrNull()
                }
                if (fresh == null && failure == null) {
                    failure = IllegalStateException("教务会话已过期")
                }
            }
            if (!fresh.isNullOrEmpty()) {
                container.classrooms.save(fresh)
                applyClassroomCatalog(fresh, Instant.now())
                if (notify) _messages.emit("教室目录已更新：${fresh.size} 间")
            } else {
                val cached = container.classrooms.cached()
                if (cached != null) {
                    applyClassroomCatalog(cached.second, cached.first, error = "刷新失败，显示缓存")
                    if (notify) _messages.emit("刷新失败，仍显示缓存")
                } else {
                    _classroomCatalog.update {
                        it.copy(
                            loading = false,
                            error = when {
                                failure is top.sykesp42.rin.data.LocalJwxtApi.JwxtSessionExpired ->
                                    "教务登录已过期，重新连接后再试。"
                                failure != null -> "教室目录没拉到，重新登录教务后再试。"
                                else -> "目录为空，教务没有返回教室"
                            }
                        )
                    }
                }
            }
            if (_search.value.query.isNotBlank()) searchRooms(_search.value.query)
        }
    }

    fun selectCampus(campus: String) {
        _classroomCatalog.update { it.copy(selectedCampus = campus) }
        viewModelScope.launch { container.settings.setSelectedCampus(campus) }
        if (_search.value.query.isNotBlank()) searchRooms(_search.value.query)
    }

    fun toggleFavorite(name: String) {
        viewModelScope.launch { container.favorites.toggle(name) }
    }

    /** Pinned (favorited) rooms first; the quick sheet lists names only. */
    fun refreshRoomShortcuts() {
        viewModelScope.launch {
            val favorites = _favorites.value
            _roomShortcuts.value = favorites
                .map { RoomShortcut(it, "") }
                .sortedBy { it.name }
                .take(6)
        }
    }

    /** User supplies the four-digit code for a room the picker only knows by name. */
    private fun applyClassroomCatalog(
        rooms: List<JwxtClassroom>,
        at: Instant,
        error: String = ""
    ) {
        catalogRooms = rooms
        _classroomCatalog.update {
            it.copy(
                loading = false,
                total = rooms.size,
                campuses = container.classrooms.campuses(rooms),
                refreshedAt = at.toString(),
                error = error,
                rooms = rooms.map { room ->
                    ApiRoom(
                        roomCode = room.code,
                        roomName = room.name,
                        campus = room.campus,
                        building = room.building,
                        capacity = room.capacity,
                        suggestedCode = ""
                    )
                }
            )
        }
    }

    fun searchRooms(query: String) {
        val trimmed = query.trim()
        _search.update { it.copy(query = query, searched = true) }
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _search.update { it.copy(rooms = emptyList(), loading = false, error = "") }
            return
        }
        val current = _settings.value
        if (current?.localMode == true) {
            if (catalogRooms.isEmpty()) {
                container.classrooms.cached()?.let { (at, rooms) -> applyClassroomCatalog(rooms, at) }
            }
            if (catalogRooms.isEmpty()) {
                _search.update { it.copy(loading = true, error = "") }
                ensureClassroomCatalogLoaded()
                return
            }
            val campus = _classroomCatalog.value.selectedCampus
            val rooms = container.classrooms.search(catalogRooms, trimmed, campus).map { room ->
                ApiRoom(
                    roomCode = room.code,
                    roomName = room.name,
                    campus = room.campus,
                    building = room.building,
                    capacity = room.capacity,
                    suggestedCode = ""
                )
            }
            _search.update {
                it.copy(
                    rooms = rooms,
                    loading = false,
                    error = if (rooms.isEmpty()) "" else it.error
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            _search.update { it.copy(loading = true, error = "") }
            try {
                val api = container.client(current?.serverUrl.orEmpty())
                    ?: throw IllegalArgumentException("invalid server url")
                val rooms = withContext(Dispatchers.IO) { api.rooms(trimmed) }
                if (_search.value.query.trim() == trimmed) {
                    _search.update { it.copy(rooms = rooms, loading = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiError) {
                if (ErrorCopy.isAuth(error.code, error.status)) {
                    expireSession("登录已失效，重新登录")
                } else if (_search.value.query.trim() == trimmed) {
                    _search.update { it.copy(loading = false, error = ErrorCopy.from(error)) }
                }
            } catch (error: Exception) {
                if (_search.value.query.trim() == trimmed) {
                    _search.update { it.copy(loading = false, error = ErrorCopy.from(error)) }
                }
            }
        }
    }

    fun ensureHistoryLoaded() {
        if (_history.value.events.isEmpty() && !_history.value.loading) loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _history.update { it.copy(loading = true) }
            val events = runCatching { container.history.read() }.getOrDefault(emptyList())
            _history.value = HistoryUiState(events = events, loading = false)
        }
    }

    /** Fetch a fresh QR for the current live activity; gates mirror the server. */
    fun clearHistory() {
        viewModelScope.launch {
            container.history.clear()
            _history.value = HistoryUiState()
            _messages.emit("记录已清空")
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            // No license server in this build: the local profile is the account.
            val current = container.settings.current()
            _auth.value = AuthState.LoggedIn(
                User(
                    id = "",
                    username = current.username.ifBlank { "local" },
                    displayName = current.displayName.ifBlank { current.username.ifBlank { "同学" } },
                    role = ""
                )
            )
            bootstrapData()
        }
    }

    private fun bootstrapData() {
        loadSchedule()
        loadHistory()
    }

    /**
     * Pull the term-stable datasets once while the JWXT session is freshly
     * verified, so 课表 / 校历 / 成绩 / 空教室 / 要闻 all survive a later
     * session expiry. Read-only; a failed fetch keeps the previous cache.
     */
    private fun warmLocalCaches() {
        viewModelScope.launch {
            val current = container.settings.current()
            if (!current.localMode) return@launch
            val connected = withContext(Dispatchers.IO) {
                container.localJwxtStore.load() != null || container.jwxtCredentialStore.load() != null
            }
            if (!connected) return@launch
            loadTermCourses()
            loadGrades()
            ensureCalendarLoaded()
            ensureCampusNewsLoaded()
            ensureClassroomCatalogLoaded()
        }
    }

    private fun configuredFlag(current: AppSettings): Boolean =
        if (current.localMode) container.localJwxtStore.load() != null else true

    private suspend fun expireSession(reason: String) {
        authMutex.withLock {
            container.sessionStore.clear()
            runCatching { container.history.append(HistoryKind.SESSION_EXPIRED) }
            _auth.value = AuthState.LoggedOut(reason)
            _login.value = LoginUiState()
            _schedule.value = ScheduleUiState()
            _search.value = SearchUiState()
            _history.value = HistoryUiState()
            _messages.emit(reason)
        }
    }

    private fun message(text: String) {
        viewModelScope.launch { _messages.emit(text) }
    }

    companion object {
        private const val CALENDAR_CACHE = "school_calendar"
        private const val TERM_CACHE = "term_courses"
        private const val GRADES_CACHE = "grades"
        private const val CLASSROOM_CACHE = "classrooms_v1"
        private const val CAMPUS_NEWS_CACHE = "campus_news_v1"
        private val FRESH_FOR: Duration = Duration.ofSeconds(45)
        private val TERM_CACHE_TTL: Duration = Duration.ofHours(24)
        private val GRADES_CACHE_TTL: Duration = Duration.ofHours(6)
        private val CALENDAR_CACHE_TTL: Duration = Duration.ofHours(12)
        private val CLASSROOM_CACHE_TTL: Duration = Duration.ofHours(24)

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
