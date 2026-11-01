package top.sky2333.rin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.courseOverridesStore by preferencesDataStore(name = "course_overrides")

/** One course's personal display tweaks: an alias and a color variant. */
data class CourseOverride(val alias: String = "", val colorIndex: Int = 0)

/**
 * Per-course display overrides, keyed by the course name. The alias is
 * what render sites show; the color variant reseeds the shared accent hash so
 * every surface (grid cells, timeline, term list) recolors together without
 * any render-site change.
 */
class CourseOverridesStore(private val context: Context) {

    suspend fun all(): Map<String, CourseOverride> {
        val prefs = context.courseOverridesStore.data.first()
        val result = mutableMapOf<String, CourseOverride>()
        prefs.asMap().forEach { (key, value) ->
            val raw = (value as? String) ?: return@forEach
            // "alias\u0001colorIndex"
            val separator = raw.indexOf('\u0001')
            val alias = if (separator >= 0) raw.take(separator) else raw
            val color = if (separator >= 0) raw.substring(separator + 1).toIntOrNull() ?: 0 else 0
            if (alias.isNotBlank() || color != 0) {
                result[key.name] = CourseOverride(alias, color)
            }
        }
        return result
    }

    suspend fun save(courseName: String, override: CourseOverride) {
        val key = stringPreferencesKey(courseName.take(60).replace("\n", ""))
        context.courseOverridesStore.edit { prefs ->
            if (override.alias.isBlank() && override.colorIndex == 0) {
                prefs.remove(key)
            } else {
                prefs[key] = override.alias + "\u0001" + override.colorIndex.coerceIn(0, 7)
            }
        }
    }
}
