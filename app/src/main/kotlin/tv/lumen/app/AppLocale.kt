package tv.lumen.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Язык интерфейса, выбранный в настройках. Дублируется в SharedPreferences, потому что
 * применить его нужно в attachBaseContext — до первой отрисовки, без ожидания DataStore.
 */
object AppLocale {
    private const val PREFS = "ui_locale"
    private const val KEY = "language"

    fun saved(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun save(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY) else putString(KEY, tag)
        }.commit() // сразу на диск: следом activity пересоздаётся
    }

    /** Контекст с выбранным языком; без выбора — системный. */
    fun wrap(base: Context): Context {
        val tag = saved(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale) // даты «2 дня назад» и числа — тоже на этом языке
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }
}
