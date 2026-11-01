package top.sykesp42.rin.ui.faces

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.sykesp42.rin.data.AppSettings

/**
 * The user's face customisation, mirrored out of DataStore so notification
 * builders, which run outside any composition, can read it synchronously.
 * The process owns it; everyone else observes.
 */
data class NoticePrefs(
    val outfit: NoticeOutfit = NoticeOutfit.ACADEMIC,
    val plate: NoticePlate = NoticePlate.AUTO,
    val inShade: Boolean = true,
    val inApp: Boolean = true,
    val overrides: Map<String, NoticeIcon> = emptyMap()
)

object NoticeFaces {
    private const val INK = 0xFF16232F.toInt()
    private const val SLATE = 0xFF3A5068.toInt()
    private const val FROST = 0xFFE8F1F6.toInt()
    private const val LINEN = 0xFFD6C9BA.toInt()

    private val _prefs = MutableStateFlow(NoticePrefs())
    val prefs: StateFlow<NoticePrefs> = _prefs.asStateFlow()

    fun publish(settings: AppSettings) {
        _prefs.value = NoticePrefs(
            outfit = NoticeOutfit.from(settings.noticeOutfit),
            plate = NoticePlate.from(settings.noticePlate),
            inShade = settings.noticeInShade,
            inApp = settings.noticeInApp,
            overrides = parse(settings.noticeOverrides)
        )
    }

    fun face(slot: NoticeSlot): NoticeIcon = _prefs.value.overrides[slot.key] ?: slot.default

    /**
     * Null means the face is drawn straight onto whatever ground the shade provides.
     * [previewing] lets an outfit picker show each set on its own plate rather than
     * the plate of whichever set happens to be selected.
     */
    fun plateArgb(context: Context, previewing: NoticeOutfit? = null): Int? {
        val prefs = _prefs.value
        return when (prefs.plate) {
            NoticePlate.NONE -> null
            NoticePlate.AUTO -> when {
                (previewing ?: prefs.outfit).lightPlateOnly -> FROST
                isNight(context) -> SLATE
                else -> INK
            }
            else -> argb(prefs.plate)
        }
    }

    private fun argb(plate: NoticePlate): Int? = when (plate) {
        NoticePlate.INK -> INK
        NoticePlate.SLATE -> SLATE
        NoticePlate.FROST -> FROST
        NoticePlate.LINEN -> LINEN
        else -> null
    }

    private fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    fun parse(raw: String): Map<String, NoticeIcon> =
        raw.split(';')
            .mapNotNull { entry ->
                val slotKey = entry.substringBefore('=').trim()
                val face = entry.substringAfter('=', "").trim().let { name ->
                    NoticeIcon.entries.firstOrNull { it.name == name }
                } ?: return@mapNotNull null
                NoticeSlot.from(slotKey)?.let { it.key to face }
            }
            .toMap()

    /** Drops unknown slots, unknown faces and any slot still pointing at its default. */
    fun sanitize(raw: String): String =
        parse(raw)
            .filter { (slotKey, face) -> NoticeSlot.from(slotKey)?.default != face }
            .entries
            .joinToString(";") { (slotKey, face) -> "$slotKey=${face.name}" }
}
