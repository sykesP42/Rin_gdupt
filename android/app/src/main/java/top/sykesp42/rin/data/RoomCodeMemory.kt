package top.sykesp42.rin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.roomCodeStore by preferencesDataStore(name = "room_codes")

/**
 * Learned mapping between classroom display names (from the JWXT schedule,
 * e.g. 二教A-205) and the four-digit attendance codes used by the school's
 * attendance endpoints. Grows every time the user queries a room code while
 * a course is running there; feeds local auto classroom recognition.
 */
class RoomCodeMemory(private val context: Context) {

    /** location-name → code, decoded from "name=code" entries. */
    suspend fun snapshot(): Map<String, String> {
        val prefs = context.roomCodeStore.data.first()
        return prefs[KEY_ENTRIES].orEmpty()
            .mapNotNull { entry ->
                val sep = entry.lastIndexOf('=')
                if (sep <= 0) null else entry.substring(0, sep) to entry.substring(sep + 1)
            }
            .toMap()
    }

    suspend fun codeFor(location: String): String? =
        snapshot()[normalize(location)]

    suspend fun remember(location: String, code: String) {
        if (location.isBlank() || !Regex("^\\d{4}$").matches(code)) return
        val key = normalize(location)
        context.roomCodeStore.edit { prefs ->
            val current = prefs[KEY_ENTRIES].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("$key=") }
            current.add("$key=$code")
            prefs[KEY_ENTRIES] = current
        }
    }

    private fun normalize(location: String): String =
        location.trim().replace(Regex("\\s+"), "")

    companion object {
        private val KEY_ENTRIES = stringSetPreferencesKey("entries")
    }
}
