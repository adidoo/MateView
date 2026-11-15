// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language of the interface: the system's by default, or one forced in the
 * settings. On Android 13+ the choice is handed to the system (it also shows up
 * in the system's per-app language settings); on older versions it is kept in
 * the app's preferences and applied when the activity is created.
 */
object AppLanguage {
    /** Follow the language of the system. */
    const val SYSTEM = ""
    val SUPPORTED = listOf("en", "fr")

    private const val PREFS = "ui"
    private const val KEY = "language"

    fun current(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language.orEmpty()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM).orEmpty()
        }
        return tag.takeIf { it in SUPPORTED } ?: SYSTEM
    }

    /** Applies [tag] ([SYSTEM], "en" or "fr"); the activity is recreated with the new language. */
    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    /** Before Android 13: wraps the activity's base context so resources use the forced language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = current(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }
}
