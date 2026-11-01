package top.sykesp42.rin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import top.sykesp42.rin.monitor.NoticeFaces
import top.sykesp42.rin.monitor.NoticeOutfit
import top.sykesp42.rin.monitor.NoticePlate
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val serverUrl: String = "",
    val monitorEnabled: Boolean = false,
    val autoSignEnabled: Boolean = false,
    val localMode: Boolean = true,
    val rosterName: String = "",
    val localThreshold: Int = 10,
    val selectedCampus: String = "",
    val jwxtBaseUrl: String = "https://jwxt.gdupt.edu.cn",
    val jwxtConnectedAt: String = "",
    val jwxtNeedsLogin: Boolean = false,
    val lastSeenNoticeId: String = "",
    val lastRemindedClassKey: String = "",
    val splashEnabled: Boolean = true,
    val minimalMode: Boolean = false,
    val internalMode: Boolean = false,
    val animationsEnabled: Boolean = true,
    val petEnabled: Boolean = true,
    val petContextAware: Boolean = true,
    val petWeatherEnabled: Boolean = true,
    val manualCode: String = "",
    val lastHandledSignId: String = "",
    val lastNotifiedSignId: String = "",
    val theme: String = THEME_RIN,
    val themeAccent: String = "",
    val themeCorners: String = "native",
    val themeDecorations: Boolean = true,
    val customThemeEnabled: Boolean = false,
    val customThemeImage: String = "",
    val customThemeSeed: String = "",
    val customThemeFocus: String = "middle",
    val customThemePalette: String = "image",
    val customThemeScale: Float = 1f,
    val customThemeOffsetX: Float = 0f,
    val customThemeOffsetY: Float = 0f,
    val customThemeBlur: Float = 0f,
    val noticeOutfit: String = "academic",
    val noticePlate: String = "auto",
    val noticeInShade: Boolean = true,
    val noticeInApp: Boolean = true,
    val noticeOverrides: String = "",
    val weekDefaultTable: Boolean = false,
    val weekDefaultFullscreen: Boolean = false,
    val daySwipeCrossWeek: Boolean = true,
    val petScale: Float = 1f,
    val petOffsetX: Float = 0f,
    val petOffsetY: Float = 0f,
    val shareStyle: String = "plain",
    val shareFormat: String = "columns",
    val reminderSound: String = "default",
    val reminderVolume: Int = 70,
    val reminderVibrate: Boolean = true,
    val reminderVibrationMode: String = "default",
    val reminderHeadsUp: Boolean = true,
    val todayCardsDisabled: String = "",
    val todayCardsOrder: String = "",
    val todayCardsPreset: Int = 0,
    val profileName: String = "",
    val profileNameAuto: Boolean = false,
    val hidePersonal: Boolean = false,
    val avatarImage: String = "",
    val avatarScale: Float = 1f,
    val avatarOffsetX: Float = 0f,
    val avatarOffsetY: Float = 0f,
    val username: String = "",
    val displayName: String = "",
    val onboarded: Boolean = false
) {
    companion object {
        // Mirrors the server's roster threshold bounds so local mode can never
        // demand a threshold the cloud would clamp (0..30).
        const val LOCAL_THRESHOLD_MAX = 30
        const val THEME_RIN = "rin"
        const val THEME_MONET = "monet"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsStore)

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val monitorEnabled = booleanPreferencesKey("monitor_enabled")
        val autoSignEnabled = booleanPreferencesKey("auto_sign_enabled")
        val localMode = booleanPreferencesKey("local_mode")
        val rosterName = stringPreferencesKey("roster_name")
        val localThreshold = stringPreferencesKey("local_threshold")
        val selectedCampus = stringPreferencesKey("selected_campus")
        val jwxtBaseUrl = stringPreferencesKey("jwxt_base_url")
        val jwxtConnectedAt = stringPreferencesKey("jwxt_connected_at")
        val jwxtNeedsLogin = booleanPreferencesKey("jwxt_needs_login")
        val lastSeenNoticeId = stringPreferencesKey("last_seen_notice_id")
        val lastRemindedClassKey = stringPreferencesKey("last_reminded_class_key")
        val splashEnabled = booleanPreferencesKey("splash_enabled")
        val minimalMode = booleanPreferencesKey("minimal_mode")
        val internalMode = booleanPreferencesKey("internal_mode")
        val animationsEnabled = booleanPreferencesKey("animations_enabled")
        val petEnabled = booleanPreferencesKey("pet_enabled")
        val petContextAware = booleanPreferencesKey("pet_context_aware")
        val petWeatherEnabled = booleanPreferencesKey("pet_weather_enabled")
        val manualCode = stringPreferencesKey("manual_code")
        val lastHandledSignId = stringPreferencesKey("last_handled_sign_id")
        val lastNotifiedSignId = stringPreferencesKey("last_notified_sign_id")
        val theme = stringPreferencesKey("theme")
        val themeAccent = stringPreferencesKey("theme_accent")
        val themeCorners = stringPreferencesKey("theme_corners")
        val themeDecorations = booleanPreferencesKey("theme_decorations")
        val customThemeEnabled = booleanPreferencesKey("custom_theme_enabled")
        val customThemeImage = stringPreferencesKey("custom_theme_image")
        val customThemeSeed = stringPreferencesKey("custom_theme_seed")
        val customThemeFocus = stringPreferencesKey("custom_theme_focus")
        val customThemePalette = stringPreferencesKey("custom_theme_palette")
        val customThemeScale = floatPreferencesKey("custom_theme_scale")
        val customThemeOffsetX = floatPreferencesKey("custom_theme_offset_x")
        val customThemeOffsetY = floatPreferencesKey("custom_theme_offset_y")
        val customThemeBlur = floatPreferencesKey("custom_theme_blur")
        val noticeOutfit = stringPreferencesKey("notice_outfit")
        val noticePlate = stringPreferencesKey("notice_plate")
        val noticeInShade = booleanPreferencesKey("notice_in_shade")
        val noticeInApp = booleanPreferencesKey("notice_in_app")
        val noticeOverrides = stringPreferencesKey("notice_overrides")
        val weekDefaultTable = booleanPreferencesKey("week_default_table")
        val weekDefaultFullscreen = booleanPreferencesKey("week_default_fullscreen")
        val daySwipeCrossWeek = booleanPreferencesKey("day_swipe_cross_week")
        val petScale = floatPreferencesKey("pet_scale")
        val petOffsetX = floatPreferencesKey("pet_offset_x")
        val petOffsetY = floatPreferencesKey("pet_offset_y")
        val shareStyle = stringPreferencesKey("share_style")
        val shareFormat = stringPreferencesKey("share_format")
        val reminderSound = stringPreferencesKey("reminder_sound")
        val reminderVolume = stringPreferencesKey("reminder_volume")
        val reminderVibrate = booleanPreferencesKey("reminder_vibrate")
        val reminderVibrationMode = stringPreferencesKey("reminder_vibration_mode")
        val reminderHeadsUp = booleanPreferencesKey("reminder_heads_up")
        val todayCardsDisabled = stringPreferencesKey("today_cards_disabled")
        val todayCardsOrder = stringPreferencesKey("today_cards_order")
        val todayCardsPreset = intPreferencesKey("today_cards_preset")
        val profileName = stringPreferencesKey("profile_name")
        val profileNameAuto = booleanPreferencesKey("profile_name_auto")
        val hidePersonal = booleanPreferencesKey("hide_personal")
        val avatarImage = stringPreferencesKey("avatar_image")
        val avatarScale = floatPreferencesKey("avatar_scale")
        val avatarOffsetX = floatPreferencesKey("avatar_offset_x")
        val avatarOffsetY = floatPreferencesKey("avatar_offset_y")
        val username = stringPreferencesKey("username")
        val displayName = stringPreferencesKey("display_name")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val flow: Flow<AppSettings> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                serverUrl = prefs[Keys.serverUrl].orEmpty(),
                monitorEnabled = prefs[Keys.monitorEnabled] ?: false,
                autoSignEnabled = prefs[Keys.autoSignEnabled] ?: false,
                localMode = prefs[Keys.localMode] ?: true,
                rosterName = prefs[Keys.rosterName].orEmpty(),
                localThreshold = (prefs[Keys.localThreshold]?.toIntOrNull() ?: 10)
                    .coerceIn(0, AppSettings.LOCAL_THRESHOLD_MAX),
                selectedCampus = prefs[Keys.selectedCampus].orEmpty(),
                jwxtBaseUrl = prefs[Keys.jwxtBaseUrl].orEmpty().ifBlank { "https://jwxt.gdupt.edu.cn" },
                jwxtConnectedAt = prefs[Keys.jwxtConnectedAt].orEmpty(),
                jwxtNeedsLogin = prefs[Keys.jwxtNeedsLogin] ?: false,
                lastSeenNoticeId = prefs[Keys.lastSeenNoticeId].orEmpty(),
                lastRemindedClassKey = prefs[Keys.lastRemindedClassKey].orEmpty(),
                splashEnabled = prefs[Keys.splashEnabled] ?: true,
                minimalMode = prefs[Keys.minimalMode] ?: false,
                internalMode = prefs[Keys.internalMode] ?: false,
                animationsEnabled = prefs[Keys.animationsEnabled] ?: true,
                petEnabled = prefs[Keys.petEnabled] ?: true,
                petContextAware = prefs[Keys.petContextAware] ?: true,
                petWeatherEnabled = prefs[Keys.petWeatherEnabled] ?: true,
                manualCode = prefs[Keys.manualCode].orEmpty(),
                lastHandledSignId = prefs[Keys.lastHandledSignId].orEmpty(),
                lastNotifiedSignId = prefs[Keys.lastNotifiedSignId].orEmpty(),
                theme = top.sykesp42.rin.ui.theme.ThemeMode.from(prefs[Keys.theme].orEmpty()).key,
                themeAccent = prefs[Keys.themeAccent].orEmpty(),
                themeCorners = top.sykesp42.rin.ui.theme.ThemeCorners
                    .from(prefs[Keys.themeCorners].orEmpty()).key,
                themeDecorations = prefs[Keys.themeDecorations] ?: true,
                customThemeEnabled = prefs[Keys.customThemeEnabled] ?: false,
                customThemeImage = prefs[Keys.customThemeImage].orEmpty(),
                customThemeSeed = prefs[Keys.customThemeSeed].orEmpty(),
                customThemeFocus = top.sykesp42.rin.ui.theme.CustomThemeFocus
                    .from(prefs[Keys.customThemeFocus].orEmpty()).key,
                customThemePalette = top.sykesp42.rin.ui.theme.CustomThemeColorMode
                    .from(prefs[Keys.customThemePalette].orEmpty()).key,
                customThemeScale = (prefs[Keys.customThemeScale] ?: 1f).coerceIn(1f, 2.4f),
                customThemeOffsetX = (prefs[Keys.customThemeOffsetX] ?: 0f).coerceIn(-1f, 1f),
                customThemeOffsetY = (prefs[Keys.customThemeOffsetY] ?: 0f).coerceIn(-1f, 1f),
                customThemeBlur = (prefs[Keys.customThemeBlur] ?: 0f).coerceIn(0f, 24f),
                noticeOutfit = NoticeOutfit.from(prefs[Keys.noticeOutfit]).key,
                noticePlate = NoticePlate.from(prefs[Keys.noticePlate]).key,
                noticeInShade = prefs[Keys.noticeInShade] ?: true,
                noticeInApp = prefs[Keys.noticeInApp] ?: true,
                noticeOverrides = prefs[Keys.noticeOverrides].orEmpty(),
                weekDefaultTable = prefs[Keys.weekDefaultTable] ?: false,
                weekDefaultFullscreen = prefs[Keys.weekDefaultFullscreen] ?: false,
                daySwipeCrossWeek = prefs[Keys.daySwipeCrossWeek] ?: true,
                petScale = (prefs[Keys.petScale] ?: 1f).coerceIn(PET_SCALE_MIN, PET_SCALE_MAX),
                petOffsetX = (prefs[Keys.petOffsetX] ?: 0f).coerceIn(-PET_OFFSET_LIMIT, PET_OFFSET_LIMIT),
                petOffsetY = (prefs[Keys.petOffsetY] ?: 0f).coerceIn(-PET_OFFSET_LIMIT, PET_OFFSET_LIMIT),
                shareStyle = prefs[Keys.shareStyle].orEmpty().ifBlank { "plain" },
                shareFormat = prefs[Keys.shareFormat].orEmpty().ifBlank { "columns" },
                reminderSound = prefs[Keys.reminderSound].orEmpty().ifBlank { "default" },
                reminderVolume = (prefs[Keys.reminderVolume]?.toIntOrNull() ?: 70).coerceIn(0, 100),
                reminderVibrate = prefs[Keys.reminderVibrate] ?: true,
                reminderVibrationMode = prefs[Keys.reminderVibrationMode].orEmpty().ifBlank { "default" },
                reminderHeadsUp = prefs[Keys.reminderHeadsUp] ?: true,
                todayCardsDisabled = prefs[Keys.todayCardsDisabled].orEmpty(),
                todayCardsOrder = prefs[Keys.todayCardsOrder].orEmpty(),
                todayCardsPreset = prefs[Keys.todayCardsPreset] ?: 0,
                profileName = prefs[Keys.profileName].orEmpty(),
                profileNameAuto = prefs[Keys.profileNameAuto] ?: false,
                hidePersonal = prefs[Keys.hidePersonal] ?: false,
                avatarImage = prefs[Keys.avatarImage].orEmpty(),
                avatarScale = (prefs[Keys.avatarScale] ?: 1f).coerceIn(1f, 2.4f),
                avatarOffsetX = (prefs[Keys.avatarOffsetX] ?: 0f).coerceIn(-1f, 1f),
                avatarOffsetY = (prefs[Keys.avatarOffsetY] ?: 0f).coerceIn(-1f, 1f),
                username = prefs[Keys.username].orEmpty(),
                displayName = prefs[Keys.displayName].orEmpty(),
                onboarded = prefs[Keys.onboarded] ?: false
            )
        }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setServerUrl(value: String) = edit { it[Keys.serverUrl] = value.trim().trimEnd('/') }

    suspend fun setMonitorEnabled(enabled: Boolean) = edit { it[Keys.monitorEnabled] = enabled }

    suspend fun setAutoSignEnabled(enabled: Boolean) = edit { it[Keys.autoSignEnabled] = enabled }

    suspend fun setLocalMode(enabled: Boolean) = edit { it[Keys.localMode] = enabled }

    suspend fun setRosterName(name: String) = edit { it[Keys.rosterName] = name.trim() }

    suspend fun setLocalThreshold(value: Int) = edit {
        it[Keys.localThreshold] = value.coerceIn(0, AppSettings.LOCAL_THRESHOLD_MAX).toString()
    }

    suspend fun setSelectedCampus(value: String) = edit { it[Keys.selectedCampus] = value.trim() }

    suspend fun setJwxtBaseUrl(url: String) = edit {
        it[Keys.jwxtBaseUrl] = url.trim().trimEnd('/')
    }

    suspend fun setJwxtConnectedAt(value: String) = edit { it[Keys.jwxtConnectedAt] = value }

    /** True when silent renewal failed; every tab then shows its last cache. */
    suspend fun setJwxtNeedsLogin(value: Boolean) = edit { it[Keys.jwxtNeedsLogin] = value }

    suspend fun setLastSeenNoticeId(value: String) = edit { it[Keys.lastSeenNoticeId] = value }

    suspend fun setLastRemindedClassKey(value: String) = edit { it[Keys.lastRemindedClassKey] = value }

    suspend fun setSplashEnabled(value: Boolean) = edit { it[Keys.splashEnabled] = value }

    suspend fun setMinimalMode(value: Boolean) = edit { it[Keys.minimalMode] = value }

    suspend fun setInternalMode(value: Boolean) = edit { it[Keys.internalMode] = value }

    suspend fun setAnimationsEnabled(value: Boolean) = edit { it[Keys.animationsEnabled] = value }

    suspend fun setPetEnabled(value: Boolean) = edit { it[Keys.petEnabled] = value }

    suspend fun setPetContextAware(value: Boolean) = edit { it[Keys.petContextAware] = value }

    suspend fun setPetWeatherEnabled(value: Boolean) = edit { it[Keys.petWeatherEnabled] = value }

    suspend fun setManualCode(code: String) = edit { it[Keys.manualCode] = code.trim() }

    suspend fun setLastHandledSignId(signId: String) = edit { it[Keys.lastHandledSignId] = signId }

    suspend fun setLastNotifiedSignId(signId: String) = edit { it[Keys.lastNotifiedSignId] = signId }

    suspend fun setTheme(theme: String) = edit {
        it[Keys.theme] = top.sykesp42.rin.ui.theme.ThemeMode.from(theme).key
    }

    suspend fun setThemeAccent(value: String) = edit {
        val valid = top.sykesp42.rin.ui.theme.ThemeCatalog.parseHex(value) != null
        it[Keys.themeAccent] = if (valid) value.trim().lowercase() else ""
    }

    suspend fun setThemeCorners(value: String) = edit {
        it[Keys.themeCorners] = top.sykesp42.rin.ui.theme.ThemeCorners.from(value).key
    }

    suspend fun setThemeDecorations(value: Boolean) = edit { it[Keys.themeDecorations] = value }

    suspend fun setCustomThemeCover(image: String, seed: String) = edit {
        it[Keys.customThemeEnabled] = true
        it[Keys.customThemeImage] = image.trim()
        it[Keys.customThemeSeed] = seed.trim().lowercase()
    }

    suspend fun setCustomThemeFocus(value: String) = edit {
        it[Keys.customThemeFocus] = top.sykesp42.rin.ui.theme.CustomThemeFocus.from(value).key
    }

    suspend fun setDaySwipeCrossWeek(value: Boolean) = edit { it[Keys.daySwipeCrossWeek] = value }

    suspend fun setPetScale(value: Float) = edit {
        it[Keys.petScale] = value.coerceIn(PET_SCALE_MIN, PET_SCALE_MAX)
    }

    suspend fun setPetOffsetX(value: Float) = edit {
        it[Keys.petOffsetX] = value.coerceIn(-PET_OFFSET_LIMIT, PET_OFFSET_LIMIT)
    }

    suspend fun setPetOffsetY(value: Float) = edit {
        it[Keys.petOffsetY] = value.coerceIn(-PET_OFFSET_LIMIT, PET_OFFSET_LIMIT)
    }

    suspend fun setShareStyle(value: String) = edit { it[Keys.shareStyle] = value }

    suspend fun setShareFormat(value: String) = edit { it[Keys.shareFormat] = value }

    suspend fun setReminderSound(value: String) = edit { it[Keys.reminderSound] = value }

    suspend fun setReminderVolume(value: Int) = edit { it[Keys.reminderVolume] = value.coerceIn(0, 100).toString() }

    suspend fun setReminderVibrate(value: Boolean) = edit { it[Keys.reminderVibrate] = value }

    suspend fun setReminderVibrationMode(value: String) = edit { it[Keys.reminderVibrationMode] = value }

    suspend fun setReminderHeadsUp(value: Boolean) = edit { it[Keys.reminderHeadsUp] = value }

    suspend fun setTodayCardsDisabled(value: String) = edit { it[Keys.todayCardsDisabled] = value }

    suspend fun setTodayCardsOrder(value: String) = edit { it[Keys.todayCardsOrder] = value }

    suspend fun setTodayCardsPreset(value: Int) = edit { it[Keys.todayCardsPreset] = value }

    suspend fun setProfileName(value: String) = edit {
        val trimmed = value.trim().take(16)
        if (trimmed.isEmpty()) it.remove(Keys.profileName) else it[Keys.profileName] = trimmed
    }

    suspend fun setProfileNameAuto(value: String) = edit {
        val trimmed = value.trim().take(16)
        if (trimmed.isEmpty()) it.remove(Keys.profileName) else it[Keys.profileName] = trimmed
        it[Keys.profileNameAuto] = true
    }

    suspend fun setHidePersonal(value: Boolean) = edit { it[Keys.hidePersonal] = value }

    suspend fun setAvatar(image: String) = edit { it[Keys.avatarImage] = image }

    suspend fun clearAvatar() = edit { it.remove(Keys.avatarImage) }

    suspend fun setAvatarFrame(scale: Float, offsetX: Float, offsetY: Float) = edit {
        it[Keys.avatarScale] = scale.coerceIn(1f, 2.4f)
        it[Keys.avatarOffsetX] = offsetX.coerceIn(-1f, 1f)
        it[Keys.avatarOffsetY] = offsetY.coerceIn(-1f, 1f)
    }

    suspend fun setCustomThemeScale(value: Float) = edit {
        it[Keys.customThemeScale] = value.coerceIn(1f, 2.4f)
    }

    suspend fun setCustomThemeFrame(scale: Float, offsetX: Float, offsetY: Float) = edit {
        it[Keys.customThemeScale] = scale.coerceIn(1f, 2.4f)
        it[Keys.customThemeOffsetX] = offsetX.coerceIn(-1f, 1f)
        it[Keys.customThemeOffsetY] = offsetY.coerceIn(-1f, 1f)
    }

    suspend fun setCustomThemeBlur(value: Float) = edit {
        it[Keys.customThemeBlur] = value.coerceIn(0f, 24f)
    }

    suspend fun setCustomThemePalette(value: String) = edit {
        it[Keys.customThemePalette] = top.sykesp42.rin.ui.theme.CustomThemeColorMode.from(value).key
    }

    suspend fun setNoticeOutfit(value: String) = edit {
        it[Keys.noticeOutfit] = NoticeOutfit.from(value).key
    }

    suspend fun setNoticePlate(value: String) = edit {
        it[Keys.noticePlate] = NoticePlate.from(value).key
    }

    suspend fun setNoticeInShade(value: Boolean) = edit { it[Keys.noticeInShade] = value }

    suspend fun setNoticeInApp(value: Boolean) = edit { it[Keys.noticeInApp] = value }

    /** Serialised as `slot=FACE;slot=FACE`; an empty value means every slot keeps its default face. */
    suspend fun setNoticeOverrides(value: String) = edit {
        val cleaned = NoticeFaces.sanitize(value)
        if (cleaned.isEmpty()) it.remove(Keys.noticeOverrides) else it[Keys.noticeOverrides] = cleaned
    }

    suspend fun clearCustomThemeCover() = edit {
        it[Keys.customThemeEnabled] = false
        it.remove(Keys.customThemeImage)
        it.remove(Keys.customThemeSeed)
    }

    suspend fun setWeekDefaultTable(value: Boolean) = edit { it[Keys.weekDefaultTable] = value }

    suspend fun setWeekDefaultFullscreen(value: Boolean) = edit { it[Keys.weekDefaultFullscreen] = value }

    suspend fun setAccount(username: String, displayName: String) = edit {
        it[Keys.username] = username
        it[Keys.displayName] = displayName
    }

    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.onboarded] = value }

    suspend fun resetForLogout() = edit {
        it[Keys.monitorEnabled] = false
        it[Keys.autoSignEnabled] = false
        it[Keys.manualCode] = ""
        it[Keys.lastHandledSignId] = ""
        it[Keys.lastNotifiedSignId] = ""
        it.remove(Keys.username)
        it.remove(Keys.displayName)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
    }
}

/** Slider range for the pet: 0.7x-1.4x of its base size, ±18% of the stage. */
const val PET_SCALE_MIN = 0.7f
const val PET_SCALE_MAX = 2f
const val PET_OFFSET_LIMIT = 0.18f
