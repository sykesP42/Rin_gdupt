package top.sykesp42.rin.data

import android.content.Context
import java.io.File
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import top.sykesp42.rin.monitor.MonitorAlarms
import top.sykesp42.rin.monitor.ClassReminderAlarms
import top.sykesp42.rin.monitor.MonitorNotifications

/** Manual dependency container: no Hilt/Koin, keeps the APK small. */
class AppContainer private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsRepository(appContext)
    val sessionStore: SessionStore = KeystoreSessionStore(File(appContext.filesDir, "session.bin"))
    val localJwxtStore: SessionStore = KeystoreSessionStore(File(appContext.filesDir, "jwxt_session.bin"))
    val jwxtCredentialStore: SessionStore = KeystoreSessionStore(File(appContext.filesDir, "jwxt_credential.bin"))
    val favorites = FavoriteRoomsStore(appContext)
    val manualSchedule = ManualScheduleStore(appContext)
    val courseReminders = CourseReminderStore(appContext)
    val localCache = LocalCache(appContext)
    val scheduleChanges = ScheduleChangeStore(localCache)
    val checklists = ChecklistStore(appContext)
    val courseOverrides = CourseOverridesStore(appContext)
    val customTheme = CustomThemeStore(appContext)
    val classrooms = ClassroomCatalogRepository(localCache)
    val petWeather = PetWeatherRepository()

    /** Public school-news fetcher (HTTPS, single list page, cached by the VM). */
    val campusNews by lazy {
        CampusNewsFetcher(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .readTimeout(java.time.Duration.ofSeconds(20))
                .build()
        )
    }

    /** Local JWXT client bound to the current base URL + session cookie. */
    fun localJwxtApi(settings: AppSettings): LocalJwxtApi? {
        if (settings.localMode) {
            val cookie = localJwxtStore.load()?.value ?: return null
            return LocalJwxtApi(settings.jwxtBaseUrl, cookieHeader = { cookie })
        }
        return null
    }

    /**
     * Failed silent re-logins back off for a few minutes so every tab and poll
     * does not hammer the school; a WebView login clears it via [noteJwxtHealthy].
     */
    @Volatile
    private var jwxtRetryAfter: Long = 0L

    /** All JWXT session use and renewal is serialized through this lock. */
    private val jwxtMutex = Mutex()

    fun noteJwxtHealthy() {
        jwxtRetryAfter = 0L
    }

    @Volatile
    private var scheduleCacheSignature: Int = 0
    @Volatile
    private var scheduleCacheAt: Long = 0L
    private val scheduleCacheInterval = 10 * 60_000L
    private val termCacheTtl = Duration.ofDays(7)
    private val gradesCacheTtl = Duration.ofHours(24)
    private val calendarCacheTtl = Duration.ofDays(7)

    /**
     * Background monitor polls also feed the offline timetable. Without this,
     * a headless class-window run refreshed the live view but left the cache
     * from the last time the UI happened to be opened.
     */
    private fun cacheLiveSchedule(overview: ApiScheduleOverview) {
        val signature = overview.hashCode()
        val now = System.currentTimeMillis()
        if (signature == scheduleCacheSignature && now - scheduleCacheAt < scheduleCacheInterval) return
        runCatching {
            localCache.write(
                "schedule_week0",
                ApiScheduleOverview.serializer(),
                overview
            )
        }.onSuccess {
            scheduleCacheSignature = signature
            scheduleCacheAt = now
        }
    }

    private fun persistJwxtCookie(value: String) {
        runCatching {
            localJwxtStore.save(SessionCookie(name = "jwxt", value = value))
        }.onFailure { traceJwxt("cookie persist failed: ${describe(it)}") }
    }

    /**
     * Explicit credential login shares the same lock as silent renewal; a
     * background warm-up must never rotate the session while the user connects.
     */
    suspend fun signInJwxt(
        settings: AppSettings,
        account: String,
        password: String
    ): String = jwxtMutex.withLock {
        val fresh = LocalJwxtApi(settings.jwxtBaseUrl, cookieHeader = { null })
            .login(LocalJwxtApi.Credentials(account, password))
        persistJwxtCookie(fresh)
        jwxtCredentialStore.save(SessionCookie(name = account, value = password))
        jwxtRetryAfter = 0L
        markSessionHealthy(settings)
        fresh
    }

    /** Adopt a cookie captured by the WebView login without racing a renewal. */
    suspend fun adoptJwxtSession(cookie: String) = jwxtMutex.withLock {
        persistJwxtCookie(cookie)
        jwxtRetryAfter = 0L
    }

    private val casClient by lazy { CasClient() }

    /**
     * Run [block] with a live JWXT session; on expiry, re-login once with the
     * stored credentials (encrypted) and retry. When the WebVPN ticket itself
     * expired, rebuild it through CAS before trying the JWXT form again.
     */
    suspend fun <T> withJwxt(
        settings: AppSettings,
        block: suspend (LocalJwxtApi) -> T
    ): T? {
        if (!settings.localMode) return null
        // A CAS captcha can only be cleared by the WebView login. Until that
        // happens, retrying here just hammers CAS from every tab and background
        // wake, so wait for JwxtLoginActivity to clear the flag.
        if (settings.jwxtNeedsLogin) return null
        // Single-flight: concurrent tab loads used to each notice an expired
        // cookie and log in separately, rotating/invalidating each other.
        return jwxtMutex.withLock {
            if (System.currentTimeMillis() < jwxtRetryAfter) return@withLock null
            var cookie = localJwxtStore.load()?.value
            if (cookie == null) {
                val credentials = jwxtCredentialStore.load() ?: return@withLock null
                val direct = runCatching {
                    LocalJwxtApi(settings.jwxtBaseUrl, cookieHeader = { null })
                        .login(LocalJwxtApi.Credentials(credentials.name, credentials.value))
                }.onFailure {
                    traceJwxt("initial login failed: ${describe(it)}")
                }.getOrNull()
                val fresh = direct ?: loginViaCas(settings, credentials) ?: run {
                    markNeedsLogin(settings)
                    return@withLock null
                }
                traceJwxt("initial login ok via ${if (direct != null) "cookie" else "cas"}")
                markSessionHealthy(settings)
                jwxtRetryAfter = 0L
                persistJwxtCookie(fresh)
                cookie = fresh
            }

            var liveCookie = cookie
            fun api(): LocalJwxtApi = LocalJwxtApi(
                baseUrl = settings.jwxtBaseUrl,
                cookieHeader = { liveCookie },
                onCookieUpdate = { updated ->
                    if (updated != liveCookie) {
                        liveCookie = updated
                        persistJwxtCookie(updated)
                    }
                }
            )

            try {
                // Note: callers may swallow per-endpoint errors inside `block`, so
                // success here does NOT prove the session is healthy.
                val result = block(api())
                markSessionHealthy(settings)
                jwxtRetryAfter = 0L
                return@withLock result
            } catch (error: LocalJwxtApi.JwxtSessionExpired) {
                traceJwxt("session expired: ${describe(error)}")
                // Re-login THROUGH the existing cookie (WebVPN session still alive
                // even when the inner JWXT session expired — the proxy forwards
                // with its own auth). Passing null would strip WebVPN cookies and
                // the request would bounce to CAS.
                val credentials = jwxtCredentialStore.load() ?: return@withLock null
                val renewed = runCatching {
                    LocalJwxtApi(settings.jwxtBaseUrl, cookieHeader = { liveCookie })
                        .login(LocalJwxtApi.Credentials(credentials.name, credentials.value))
                }.onFailure { traceJwxt("renewal login failed: ${describe(it)}") }
                    .getOrNull()
                // Cookie renewal fails once the WebVPN ticket is gone; rebuild the
                // portal session through CAS, then log in to JWXT inside it.
                val fresh = renewed ?: loginViaCas(settings, credentials) ?: run {
                    markNeedsLogin(settings)
                    jwxtRetryAfter = System.currentTimeMillis() + 5 * 60_000L
                    return@withLock null
                }
                traceJwxt("renewal ok via ${if (renewed != null) "cookie" else "cas"}")
                markSessionHealthy(settings)
                jwxtRetryAfter = 0L
                persistJwxtCookie(fresh)
                liveCookie = fresh
                return@withLock runCatching { block(api()) }
                    .onFailure { traceJwxt("fetch after renewal failed: ${describe(it)}") }
                    .getOrNull()
            } catch (error: Exception) {
                traceJwxt("fetch failed: ${describe(error)}")
                return@withLock null
            }
        }
    }

    /**
     * Read-only daily heartbeat used by the background maintainer. It keeps the
     * cookie warm and lets the normal single-flight path renew it if needed.
     */
    suspend fun keepJwxtSession(settings: AppSettings): Boolean {
        val ok = withJwxt(settings) { api ->
            api.touchSession()
            true
        } ?: false
        traceJwxt(if (ok) "keepalive ok" else "keepalive failed/skipped")
        return ok
    }

    /**
     * Headless cache maintenance for the phone-as-scanner path. Only fills
     * caches that are stale/missing; no UI state is needed. Read-only.
     */
    suspend fun warmOfflineCaches(settings: AppSettings, termLabel: String = ""): Int {
        if (!settings.localMode) return 0
        var warmed = 0
        if (!localCache.isFresh(TERM_CACHE, termCacheTtl)) {
            val courses = withJwxt(settings) { api -> api.fetchTermCourses() }.orEmpty()
            if (courses.isNotEmpty()) {
                localCache.write(TERM_CACHE, ListSerializer(JwxtTermCourse.serializer()), courses)
                warmed++
            }
        }
        if (!localCache.isFresh(GRADES_CACHE, gradesCacheTtl)) {
            val bundle = withJwxt(settings) { api ->
                JwxtGradeBundle(
                    grades = runCatching { api.fetchGrades() }.getOrElse { emptyList() },
                    exams = runCatching { api.fetchExams() }.getOrElse { emptyList() },
                    notices = runCatching { api.fetchNotices() }.getOrElse { emptyList() },
                    student = runCatching { api.fetchStudentInfo() }.getOrNull()
                )
            }
            if (bundle != null && !isEmptyGradeBundle(bundle)) {
                localCache.write(GRADES_CACHE, JwxtGradeBundle.serializer(), bundle)
                warmed++
            }
        }
        if (!localCache.isFresh(CALENDAR_CACHE, calendarCacheTtl)) {
            val label = termLabel.ifBlank {
                localCache.read(SCHEDULE_CACHE, ApiScheduleOverview.serializer())
                    ?.second?.term.orEmpty()
            }
            val calendar = withJwxt(settings) { api -> api.fetchCalendar(label) }
            if (calendar != null && calendar.days.isNotEmpty()) {
                localCache.write(CALENDAR_CACHE, JwxtCalendar.serializer(), calendar)
                warmed++
            }
        }
        return warmed
    }

    /**
     * CAS → fresh WebVPN cookies → JWXT form login. Only meaningful for the
     * WebVPN-wrapped base; a direct campus host does not go through CAS.
     */
    private suspend fun loginViaCas(settings: AppSettings, credentials: SessionCookie): String? =
        withContext(Dispatchers.IO) {
            if (!settings.jwxtBaseUrl.startsWith("https://webvpn2.gdupt.edu.cn")) return@withContext null
            val cas = try {
                casClient.loginWebVpn(credentials.name, credentials.value)
            } catch (error: Exception) {
                traceJwxt("cas login failed: ${describe(error)}")
                if (error is CasClient.CasCaptchaRequired || error is CasClient.CasRejected) {
                    markNeedsLogin(settings)
                    jwxtRetryAfter = System.currentTimeMillis() + 5 * 60_000L
                }
                return@withContext null
            }
            traceJwxt("cas login ok")
            val jwxt = try {
                LocalJwxtApi(settings.jwxtBaseUrl, cookieHeader = { cas.cookieHeader })
                    .login(LocalJwxtApi.Credentials(credentials.name, credentials.value))
            } catch (error: Exception) {
                traceJwxt("jwxt login after cas failed: ${describe(error)}")
                return@withContext null
            }
            // Keep both layers: the portal ticket plus the fresh JWXT session.
            cas.cookieHeader + "; " + jwxt
        }

    private suspend fun markNeedsLogin(snapshot: AppSettings) {
        if (!snapshot.jwxtNeedsLogin) runCatching { settings.setJwxtNeedsLogin(true) }
    }

    private suspend fun markSessionHealthy(snapshot: AppSettings) {
        if (snapshot.jwxtNeedsLogin) runCatching { settings.setJwxtNeedsLogin(false) }
    }

    private fun describe(error: Throwable): String =
        "${error.javaClass.simpleName}: ${(error.message ?: "").take(120)}"

    /** Session diagnostics without cookies or credentials. */
    private fun traceJwxt(line: String) {
        runCatching {
            File(appContext.filesDir, "jwxt_trace.txt")
                .appendText("${System.currentTimeMillis()} net $line\n")
        }
    }
    val history = HistoryStore(File(appContext.filesDir, "history.jsonl"))
    val notifications = MonitorNotifications(appContext)
    val alarms = MonitorAlarms(appContext)
    val classReminders by lazy { ClassReminderAlarms(alarms, settings) }

    fun client(serverUrl: String): ApiClient? {
        if (serverUrl.isBlank()) return null
        return runCatching { ApiClient(serverUrl, sessionStore) }.getOrNull()
    }

    companion object {
        private const val TERM_CACHE = "term_courses"
        private const val GRADES_CACHE = "grades"
        private const val CALENDAR_CACHE = "school_calendar"
        private const val SCHEDULE_CACHE = "schedule_week0"

        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
    }
}
