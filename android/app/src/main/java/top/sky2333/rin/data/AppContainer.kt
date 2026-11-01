package top.sky2333.rin.data

import android.content.Context
import android.content.pm.PackageManager

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
