package top.sky2333.rin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One pre-class item with its own check state. */
@Serializable
data class ChecklistItem(val text: String, val done: Boolean = false)

private val Context.checklistStore by preferencesDataStore(name = "course_checklists")

/**
 * Per-course pre-class checklist. New data is JSON; plain newline entries from
 * older builds are still read as unchecked items.
 */
class ChecklistStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun items(courseName: String): List<ChecklistItem> {
        val raw = context.checklistStore.data.first()[key(courseName)] ?: return emptyList()
        val decoded = runCatching { json.decodeFromString<List<ChecklistItem>>(raw) }.getOrNull()
        if (decoded != null) return decoded.filter { it.text.isNotBlank() }
        return raw.split("\n").filter { it.isNotBlank() }.map { ChecklistItem(text = it.trim()) }
    }

    suspend fun save(courseName: String, items: List<ChecklistItem>) {
        val key = key(courseName)
        val clean = items.filter { it.text.isNotBlank() }
        context.checklistStore.edit { prefs ->
            if (clean.isEmpty()) prefs.remove(key)
            else prefs[key] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ChecklistItem.serializer()),
                clean
            )
        }
    }

    private fun key(courseName: String) =
        stringPreferencesKey(courseName.take(40).replace("\n", ""))
}
