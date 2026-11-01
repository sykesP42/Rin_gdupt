package top.sky2333.rin.ui

import top.sky2333.rin.data.AppSettings
import top.sky2333.rin.ui.schedule.ShareStyle

/**
 * One place that decides what the app is allowed to call the user.
 *
 * With 隐私模式 on, the personal name must not surface anywhere — including
 * share cards — so an unnamed profile gets a stable pseudonym.
 */
object Identity {
    private val PSEUDONYMS = listOf("青柠", "白露", "南风", "夏至", "远山", "星野", "木槿", "拾光")

    fun displayName(settings: AppSettings?, realName: String): String {
        val configured = settings?.profileName.orEmpty().trim()
        if (configured.isNotEmpty()) return configured
        if (settings?.hidePersonal != true) return realName
        return pseudonym(realName)
    }

    /** A fresh pseudonym, so each privacy session gets a different name. */
    fun randomName(): String = PSEUDONYMS.random() + "同学"

    /** Stable fallback for a single session: composition must not flicker names. */
    private val sessionName: String by lazy { PSEUDONYMS.random() + "同学" }

    private fun pseudonym(realName: String): String = sessionName

    fun shareStyle(settings: AppSettings?): ShareStyle =
        ShareStyle.entries.firstOrNull { it.name.equals(settings?.shareStyle, ignoreCase = true) }
            ?: ShareStyle.PLAIN

    /** The owner line on share cards; blank disables name-based styles. */
    fun shareOwner(settings: AppSettings?, realName: String): String =
        if (settings?.hidePersonal == true || settings?.profileName?.isNotBlank() == true) {
            displayName(settings, realName)
        } else {
            displayName(settings, realName)
        }
}
