package top.sykesp42.rin.data

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/** Manual dependency container: no Hilt/Koin, keeps the APK small. */
class AppContainer private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsRepository(appContext)
    val manualSchedule = ManualScheduleStore(appContext)
    val courseReminders = CourseReminderStore(appContext)
    val checklists = ChecklistStore(appContext)
    val courseOverrides = CourseOverridesStore(appContext)
    val customTheme = CustomThemeStore(appContext)
    val todayFrame = TodayFrameStore(appContext)
    val weather = PetWeatherRepository()
    val cache = LocalCache(appContext)
    val jwxtSessions: SessionStore = KeystoreSessionStore(File(appContext.filesDir, "jwxt_session.bin"))

    /**
     * Remembered 学号/密码 for the login page's autofill, Keystore-encrypted
     * like the session and never leaving the device. Captured while the user
     * types on the school's own page; the school page still does the actual
     * login and the slider, this only spares the retyping.
     */
    val jwxtCredentialStore: SessionStore =
        KeystoreSessionStore(File(appContext.filesDir, "jwxt_credential.bin"))

    val appVersionName: String by lazy {
        runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
        }.getOrNull().orEmpty()
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
    }
}
