package dev.fadalias.app

import android.content.Context
import android.content.res.Configuration
import dev.fadalias.data.ActivityEntry
import dev.fadalias.data.ActivityKind
import dev.fadalias.data.AppSort
import dev.fadalias.data.IconSize
import dev.fadalias.data.LanguageMode
import dev.fadalias.data.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Purely local UI preferences (nothing here reaches the hook). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumOf(key: String, def: E): E =
        enumValues<E>().getOrElse(sp.getInt(key, def.ordinal)) { def }

    var themeMode: ThemeMode
        get() = enumOf("theme", ThemeMode.SYSTEM)
        set(v) = sp.edit().putInt("theme", v.ordinal).apply()
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic", true)
        set(v) = sp.edit().putBoolean("dynamic", v).apply()
    var amoled: Boolean
        get() = sp.getBoolean("amoled", true)
        set(v) = sp.edit().putBoolean("amoled", v).apply()
    var uiScale: Float
        get() = sp.getFloat("scale", 1.0f)
        set(v) = sp.edit().putFloat("scale", v).apply()
    var language: LanguageMode
        get() = enumOf("lang", LanguageMode.SYSTEM)
        set(v) = sp.edit().putInt("lang", v.ordinal).apply()
    var iconSize: IconSize
        get() = enumOf("iconSize", IconSize.PX_192)
        set(v) = sp.edit().putInt("iconSize", v.ordinal).apply()
    var hideProtectedWarning: Boolean
        get() = sp.getBoolean("hideProtectedWarning", false)
        set(v) = sp.edit().putBoolean("hideProtectedWarning", v).apply()
    var showSystemApps: Boolean
        get() = sp.getBoolean("showSystemApps", false)
        set(v) = sp.edit().putBoolean("showSystemApps", v).apply()
    var systemWarningAccepted: Boolean
        get() = sp.getBoolean("systemWarningAccepted", false)
        set(v) = sp.edit().putBoolean("systemWarningAccepted", v).apply()
    var hideSelfWarning: Boolean
        get() = sp.getBoolean("hideSelfWarning", false)
        set(v) = sp.edit().putBoolean("hideSelfWarning", v).apply()
    var sort: AppSort
        get() = enumOf("sort", AppSort.CHANGED_FIRST)
        set(v) = sp.edit().putInt("sort", v.ordinal).apply()

    fun loadActivity(): List<ActivityEntry> = try {
        val a = JSONArray(sp.getString("activity", "[]"))
        (0 until a.length()).mapNotNull {
            val o = a.getJSONObject(it)
            val kind = ActivityKind.entries.getOrNull(o.getInt("k")) ?: return@mapNotNull null
            ActivityEntry(kind, o.getString("p"), o.optString("l"), o.getLong("t"))
        }
    } catch (_: Throwable) {
        emptyList()
    }

    fun saveActivity(list: List<ActivityEntry>) {
        val a = JSONArray()
        list.take(20).forEach { a.put(JSONObject().put("k", it.kind.ordinal).put("p", it.pkg).put("l", it.label).put("t", it.at)) }
        sp.edit().putString("activity", a.toString()).apply()
    }

    /** Applies the chosen language to a context (used from attachBaseContext). */
    fun wrap(base: Context): Context {
        val tag = when (language) {
            LanguageMode.SYSTEM -> return base
            LanguageMode.TURKISH -> "tr"
            LanguageMode.ENGLISH -> "en"
        }
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(cfg)
    }
}
