package top.sykesp42.rin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoriteRoomsStore by preferencesDataStore(name = "favorite_rooms")

/** Room names the user pinned for quick access. */
class FavoriteRoomsStore(private val context: Context) {
    val flow: Flow<Set<String>> = context.favoriteRoomsStore.data
        .map { it[KEY] ?: emptySet() }

    suspend fun toggle(name: String): Set<String> {
        val normalized = name.trim()
        if (normalized.isEmpty()) return flow.let { emptySet() }
        var next = emptySet<String>()
        context.favoriteRoomsStore.edit { prefs ->
            val current = prefs[KEY].orEmpty().toMutableSet()
            if (!current.add(normalized)) current.remove(normalized)
            prefs[KEY] = current
            next = current.toSet()
        }
        return next
    }

    companion object {
        private val KEY = stringSetPreferencesKey("rooms")
    }
}
