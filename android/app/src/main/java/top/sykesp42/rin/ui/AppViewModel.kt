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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.CourseOverride
import top.sykesp42.rin.data.CourseReminder
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
import top.sykesp42.rin.ui.schedule.campusZone
import top.sykesp42.rin.ui.faces.NoticeFaces
import top.sykesp42.rin.ui.faces.NoticeIcon
import top.sykesp42.rin.ui.faces.NoticeSlot
import top.sykesp42.rin.ui.profile.Profile
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.today.TodayCardId
import top.sykesp42.rin.ui.today.TodayCards

data class ScheduleUiState(
    val overview: ApiScheduleOverview? = null,
    val loading: Boolean = true,
    val currentWeek: Int = 0,
    val termStart: String = "",
    val warning: String? = null
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
                rebuild(snapshot, weekOverride = _schedule.value.overview?.week ?: 0)
                if (snapshot.petWeatherEnabled) refreshPetWeather()
                if (firstSettings) {
                    firstSettings = false
                    if (!snapshot.simpleMode && _jwxtConnected.value) refreshJwxt(0)
                }
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
                    val expired = error is LocalJwxtApi.JwxtSessionExpired ||
                        error is LocalJwxtApi.JwxtCaptchaRequired
                    if (expired) {
                        jwxtOverview = null
                        container.jwxtSessions.clear()
                        _jwxtConnected.value = false
                        settingsRepo.setJwxtConnectedAt("")
                    }
                    _schedule.value = _schedule.value.copy(
                        warning = if (expired) {
                            "教务登录已过期，请到「我的 → 教务连接」重新登录。"
                        } else {
                            "教务暂时不可用，当前显示本机课表。"
                        }
                    )
                }
        }
    }

    fun setTermStart(date: LocalDate) {
        viewModelScope.launch {
            settingsRepo.setTermStartDate(date.toString())
            _settings.value?.let { rebuild(it, 0) }
        }
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
        if (overview.week == _schedule.value.currentWeek || overview.week == 0) {
            container.cache.write("schedule_week0", ApiScheduleOverview.serializer(), overview)
        }
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
