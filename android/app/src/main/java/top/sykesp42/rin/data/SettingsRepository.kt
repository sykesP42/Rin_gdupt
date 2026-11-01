package top.sykesp42.rin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.rinSettings: DataStore<Preferences> by preferencesDataStore(name = "rin_settings")

data class AppSettings(
    val theme: String = THEME_RIN,
    val themeAccent: String = "",
    val themeCorners: String = "native",
    val themeDecorations: Boolean = true,
    val animationsEnabled: Boolean = true,
    val splashEnabled: Boolean = true,
    val simpleMode: Boolean = false,
    val onboarded: Boolean = false,
    val petEnabled: Boolean = true,
    val petContextAware: Boolean = true,
    val petWeatherEnabled: Boolean = true,
    val petScale: Float = 1f,
    val petOffsetX: Float = 0f,
    val petOffsetY: Float = 0f,
    val noticeOutfit: String = "academic",
    val noticePlate: String = "auto",
    val noticeInShade: Boolean = true,
    val noticeInApp: Boolean = true,
    val noticeOverrides: String = "",
    val avatarImage: String = "",
    val avatarScale: Float = 1f,
    val avatarOffsetX: Float = 0f,
    val avatarOffsetY: Float = 0f,
    val profileName: String = "",
    val profileNameAuto: Boolean = false,
    val hidePersonal: Boolean = false,
    val todayCardsDisabled: String = "",
    val todayCardsOrder: String = "",
    val todayCardsPreset: Int = 0,
    val shareStyle: String = "plain",
    val shareFormat: String = "columns",
    val weekDefaultTable: Boolean = false,
    val weekDefaultFullscreen: Boolean = false,
    val daySwipeCrossWeek: Boolean = true,
    val reminderSound: String = "default",
    val reminderVolume: Int = 70,
    val reminderVibrate: Boolean = true,
    val reminderVibrationMode: String = "default",
    val reminderHeadsUp: Boolean = true,
    val signReminderEnabled: Boolean = true,
    val customThemeEnabled: Boolean = false,
    val customThemeImage: String = "",
    val customThemeSeed: String = "",
    val customThemeFocus: String = "middle",
    val customThemePalette: String = "image",
    val customThemeScale: Float = 1f,
    val customThemeOffsetX: Float = 0f,
    val customThemeOffsetY: Float = 0f,
    val customThemeBlur: Float = 0f,
    val termStartDate: String = "",
    val jwxtBaseUrl: String = "https://jwxt.gdupt.edu.cn",
    val jwxtConnectedAt: String = ""
) {
    companion object {
        const val THEME_RIN = "rin"
        const val THEME_MONET = "monet"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.rinSettings)

    val settings: Flow<AppSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    suspend fun setTheme(value: String) = edit { it[Keys.theme] = value }
    suspend fun setThemeAccent(value: String) = edit { it[Keys.themeAccent] = value }
    suspend fun setThemeCorners(value: String) = edit { it[Keys.themeCorners] = value }
    suspend fun setThemeDecorations(value: Boolean) = edit { it[Keys.themeDecorations] = value }
    suspend fun setAnimationsEnabled(value: Boolean) = edit { it[Keys.animationsEnabled] = value }
    suspend fun setSplashEnabled(value: Boolean) = edit { it[Keys.splashEnabled] = value }
    suspend fun setSimpleMode(value: Boolean) = edit { it[Keys.simpleMode] = value }
    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.onboarded] = value }
    suspend fun setPetEnabled(value: Boolean) = edit { it[Keys.petEnabled] = value }
    suspend fun setPetContextAware(value: Boolean) = edit { it[Keys.petContextAware] = value }
    suspend fun setPetWeatherEnabled(value: Boolean) = edit { it[Keys.petWeatherEnabled] = value }
    suspend fun setPetScale(value: Float) = edit { it[Keys.petScale] = value }
    suspend fun setPetOffsetX(value: Float) = edit { it[Keys.petOffsetX] = value }
    suspend fun setPetOffsetY(value: Float) = edit { it[Keys.petOffsetY] = value }
    suspend fun setNoticeOutfit(value: String) = edit { it[Keys.noticeOutfit] = value }
    suspend fun setNoticePlate(value: String) = edit { it[Keys.noticePlate] = value }
    suspend fun setNoticeInShade(value: Boolean) = edit { it[Keys.noticeInShade] = value }
    suspend fun setNoticeInApp(value: Boolean) = edit { it[Keys.noticeInApp] = value }
    suspend fun setNoticeOverrides(value: String) = edit { it[Keys.noticeOverrides] = value }
    suspend fun setAvatar(value: String) = edit { it[Keys.avatarImage] = value }
    suspend fun setAvatarFrame(scale: Float, x: Float, y: Float) = edit {
        it[Keys.avatarScale] = scale
        it[Keys.avatarOffsetX] = x
        it[Keys.avatarOffsetY] = y
    }
    suspend fun setProfileName(value: String) = edit { it[Keys.profileName] = value }
    suspend fun setProfileNameAuto(value: Boolean) = edit { it[Keys.profileNameAuto] = value }
    suspend fun setHidePersonal(value: Boolean) = edit { it[Keys.hidePersonal] = value }
    suspend fun setTodayCards(disabled: String, order: String, preset: Int) = edit {
        it[Keys.todayCardsDisabled] = disabled
        it[Keys.todayCardsOrder] = order
        it[Keys.todayCardsPreset] = preset
    }
    suspend fun setShareStyle(value: String) = edit { it[Keys.shareStyle] = value }
    suspend fun setShareFormat(value: String) = edit { it[Keys.shareFormat] = value }
    suspend fun setWeekDefaultTable(value: Boolean) = edit { it[Keys.weekDefaultTable] = value }
    suspend fun setWeekDefaultFullscreen(value: Boolean) = edit { it[Keys.weekDefaultFullscreen] = value }
    suspend fun setDaySwipeCrossWeek(value: Boolean) = edit { it[Keys.daySwipeCrossWeek] = value }
    suspend fun setReminderSound(value: String) = edit { it[Keys.reminderSound] = value }
    suspend fun setReminderVolume(value: Int) = edit { it[Keys.reminderVolume] = value }
    suspend fun setReminderVibrate(value: Boolean) = edit { it[Keys.reminderVibrate] = value }
    suspend fun setReminderVibrationMode(value: String) = edit { it[Keys.reminderVibrationMode] = value }
    suspend fun setReminderHeadsUp(value: Boolean) = edit { it[Keys.reminderHeadsUp] = value }
    suspend fun setSignReminderEnabled(value: Boolean) = edit { it[Keys.signReminderEnabled] = value }
    suspend fun setCustomThemeCover(fileName: String, seed: String) = edit {
        it[Keys.customThemeEnabled] = fileName.isNotBlank()
        it[Keys.customThemeImage] = fileName
        it[Keys.customThemeSeed] = seed
    }
    suspend fun clearCustomThemeCover() = edit {
        it[Keys.customThemeEnabled] = false
        it[Keys.customThemeImage] = ""
        it[Keys.customThemeSeed] = ""
    }
    suspend fun setCustomThemeFocus(value: String) = edit { it[Keys.customThemeFocus] = value }
    suspend fun setCustomThemePalette(value: String) = edit { it[Keys.customThemePalette] = value }
    suspend fun setCustomThemeScale(value: Float) = edit { it[Keys.customThemeScale] = value }
    suspend fun setCustomThemeBlur(value: Float) = edit { it[Keys.customThemeBlur] = value }
    suspend fun setCustomThemeFrame(scale: Float, x: Float, y: Float) = edit {
        it[Keys.customThemeScale] = scale
        it[Keys.customThemeOffsetX] = x
        it[Keys.customThemeOffsetY] = y
    }
    suspend fun setTermStartDate(value: String) = edit { it[Keys.termStartDate] = value }
    suspend fun setJwxtBaseUrl(value: String) = edit { it[Keys.jwxtBaseUrl] = value }
    suspend fun setJwxtConnectedAt(value: String) = edit { it[Keys.jwxtConnectedAt] = value }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val themeAccent = stringPreferencesKey("theme_accent")
        val themeCorners = stringPreferencesKey("theme_corners")
        val themeDecorations = booleanPreferencesKey("theme_decorations")
        val animationsEnabled = booleanPreferencesKey("animations_enabled")
        val splashEnabled = booleanPreferencesKey("splash_enabled")
        val simpleMode = booleanPreferencesKey("simple_mode")
        val onboarded = booleanPreferencesKey("onboarded")
        val petEnabled = booleanPreferencesKey("pet_enabled")
        val petContextAware = booleanPreferencesKey("pet_context_aware")
        val petWeatherEnabled = booleanPreferencesKey("pet_weather_enabled")
        val petScale = floatPreferencesKey("pet_scale")
        val petOffsetX = floatPreferencesKey("pet_offset_x")
        val petOffsetY = floatPreferencesKey("pet_offset_y")
        val noticeOutfit = stringPreferencesKey("notice_outfit")
        val noticePlate = stringPreferencesKey("notice_plate")
        val noticeInShade = booleanPreferencesKey("notice_in_shade")
        val noticeInApp = booleanPreferencesKey("notice_in_app")
        val noticeOverrides = stringPreferencesKey("notice_overrides")
        val avatarImage = stringPreferencesKey("avatar_image")
        val avatarScale = floatPreferencesKey("avatar_scale")
        val avatarOffsetX = floatPreferencesKey("avatar_offset_x")
        val avatarOffsetY = floatPreferencesKey("avatar_offset_y")
        val profileName = stringPreferencesKey("profile_name")
        val profileNameAuto = booleanPreferencesKey("profile_name_auto")
        val hidePersonal = booleanPreferencesKey("hide_personal")
        val todayCardsDisabled = stringPreferencesKey("today_cards_disabled")
        val todayCardsOrder = stringPreferencesKey("today_cards_order")
        val todayCardsPreset = intPreferencesKey("today_cards_preset")
        val shareStyle = stringPreferencesKey("share_style")
        val shareFormat = stringPreferencesKey("share_format")
        val weekDefaultTable = booleanPreferencesKey("week_default_table")
        val weekDefaultFullscreen = booleanPreferencesKey("week_default_fullscreen")
        val daySwipeCrossWeek = booleanPreferencesKey("day_swipe_cross_week")
        val reminderSound = stringPreferencesKey("reminder_sound")
        val reminderVolume = intPreferencesKey("reminder_volume")
        val reminderVibrate = booleanPreferencesKey("reminder_vibrate")
        val reminderVibrationMode = stringPreferencesKey("reminder_vibration_mode")
        val reminderHeadsUp = booleanPreferencesKey("reminder_heads_up")
        val signReminderEnabled = booleanPreferencesKey("sign_reminder_enabled")
        val customThemeEnabled = booleanPreferencesKey("custom_theme_enabled")
        val customThemeImage = stringPreferencesKey("custom_theme_image")
        val customThemeSeed = stringPreferencesKey("custom_theme_seed")
        val customThemeFocus = stringPreferencesKey("custom_theme_focus")
        val customThemePalette = stringPreferencesKey("custom_theme_palette")
        val customThemeScale = floatPreferencesKey("custom_theme_scale")
        val customThemeOffsetX = floatPreferencesKey("custom_theme_offset_x")
        val customThemeOffsetY = floatPreferencesKey("custom_theme_offset_y")
        val customThemeBlur = floatPreferencesKey("custom_theme_blur")
        val termStartDate = stringPreferencesKey("term_start_date")
        val jwxtBaseUrl = stringPreferencesKey("jwxt_base_url")
        val jwxtConnectedAt = stringPreferencesKey("jwxt_connected_at")
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = this[Keys.theme] ?: defaults.theme,
            themeAccent = this[Keys.themeAccent] ?: defaults.themeAccent,
            themeCorners = this[Keys.themeCorners] ?: defaults.themeCorners,
            themeDecorations = this[Keys.themeDecorations] ?: defaults.themeDecorations,
            animationsEnabled = this[Keys.animationsEnabled] ?: defaults.animationsEnabled,
            splashEnabled = this[Keys.splashEnabled] ?: defaults.splashEnabled,
            simpleMode = this[Keys.simpleMode] ?: defaults.simpleMode,
            onboarded = this[Keys.onboarded] ?: defaults.onboarded,
            petEnabled = this[Keys.petEnabled] ?: defaults.petEnabled,
            petContextAware = this[Keys.petContextAware] ?: defaults.petContextAware,
            petWeatherEnabled = this[Keys.petWeatherEnabled] ?: defaults.petWeatherEnabled,
            petScale = this[Keys.petScale] ?: defaults.petScale,
            petOffsetX = this[Keys.petOffsetX] ?: defaults.petOffsetX,
            petOffsetY = this[Keys.petOffsetY] ?: defaults.petOffsetY,
            noticeOutfit = this[Keys.noticeOutfit] ?: defaults.noticeOutfit,
            noticePlate = this[Keys.noticePlate] ?: defaults.noticePlate,
            noticeInShade = this[Keys.noticeInShade] ?: defaults.noticeInShade,
            noticeInApp = this[Keys.noticeInApp] ?: defaults.noticeInApp,
            noticeOverrides = this[Keys.noticeOverrides] ?: defaults.noticeOverrides,
            avatarImage = this[Keys.avatarImage] ?: defaults.avatarImage,
            avatarScale = this[Keys.avatarScale] ?: defaults.avatarScale,
            avatarOffsetX = this[Keys.avatarOffsetX] ?: defaults.avatarOffsetX,
            avatarOffsetY = this[Keys.avatarOffsetY] ?: defaults.avatarOffsetY,
            profileName = this[Keys.profileName] ?: defaults.profileName,
            profileNameAuto = this[Keys.profileNameAuto] ?: defaults.profileNameAuto,
            hidePersonal = this[Keys.hidePersonal] ?: defaults.hidePersonal,
            todayCardsDisabled = this[Keys.todayCardsDisabled] ?: defaults.todayCardsDisabled,
            todayCardsOrder = this[Keys.todayCardsOrder] ?: defaults.todayCardsOrder,
            todayCardsPreset = this[Keys.todayCardsPreset] ?: defaults.todayCardsPreset,
            shareStyle = this[Keys.shareStyle] ?: defaults.shareStyle,
            shareFormat = this[Keys.shareFormat] ?: defaults.shareFormat,
            weekDefaultTable = this[Keys.weekDefaultTable] ?: defaults.weekDefaultTable,
            weekDefaultFullscreen = this[Keys.weekDefaultFullscreen] ?: defaults.weekDefaultFullscreen,
            daySwipeCrossWeek = this[Keys.daySwipeCrossWeek] ?: defaults.daySwipeCrossWeek,
            reminderSound = this[Keys.reminderSound] ?: defaults.reminderSound,
            reminderVolume = this[Keys.reminderVolume] ?: defaults.reminderVolume,
            reminderVibrate = this[Keys.reminderVibrate] ?: defaults.reminderVibrate,
            reminderVibrationMode = this[Keys.reminderVibrationMode] ?: defaults.reminderVibrationMode,
            reminderHeadsUp = this[Keys.reminderHeadsUp] ?: defaults.reminderHeadsUp,
            signReminderEnabled = this[Keys.signReminderEnabled] ?: defaults.signReminderEnabled,
            customThemeEnabled = this[Keys.customThemeEnabled] ?: defaults.customThemeEnabled,
            customThemeImage = this[Keys.customThemeImage] ?: defaults.customThemeImage,
            customThemeSeed = this[Keys.customThemeSeed] ?: defaults.customThemeSeed,
            customThemeFocus = this[Keys.customThemeFocus] ?: defaults.customThemeFocus,
            customThemePalette = this[Keys.customThemePalette] ?: defaults.customThemePalette,
            customThemeScale = this[Keys.customThemeScale] ?: defaults.customThemeScale,
            customThemeOffsetX = this[Keys.customThemeOffsetX] ?: defaults.customThemeOffsetX,
            customThemeOffsetY = this[Keys.customThemeOffsetY] ?: defaults.customThemeOffsetY,
            customThemeBlur = this[Keys.customThemeBlur] ?: defaults.customThemeBlur,
            termStartDate = this[Keys.termStartDate] ?: defaults.termStartDate,
            jwxtBaseUrl = this[Keys.jwxtBaseUrl] ?: defaults.jwxtBaseUrl,
            jwxtConnectedAt = this[Keys.jwxtConnectedAt] ?: defaults.jwxtConnectedAt
        )
    }
}
