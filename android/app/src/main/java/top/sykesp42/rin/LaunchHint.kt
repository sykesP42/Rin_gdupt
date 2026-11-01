package top.sykesp42.rin

import android.content.Context
import android.content.res.Configuration

/**
 * The last appearance the app actually resolved, kept in a synchronous store.
 *
 * The window background is chosen before Compose runs and long before DataStore
 * answers, so a cold start cannot wait for the real setting. Reading this hint in
 * `onCreate` costs one small file read and stops a dark theme from opening on a
 * paper-white flash (and the reverse). First launch ever has no hint and falls
 * back to the system's night qualifier.
 */
object LaunchHint {
    private const val FILE = "launch_hint"
    private const val KEY_DARK = "dark"

    fun isDark(context: Context): Boolean {
        val stored = context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_DARK, null)
        return when (stored) {
            "1" -> true
            "0" -> false
            else -> context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun remember(context: Context, dark: Boolean) {
        val previous = context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_DARK, null)
        // Cold start already paid for one read; only touch disk when it changed.
        if (previous == if (dark) "1" else "0") return
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK, if (dark) "1" else "0")
            .apply()
    }
}
