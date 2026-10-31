package com.mux.android.browser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class Page { NONE, TABS, BOOKMARKS, HISTORY, DOWNLOADS, SYS }

/** Which overlay / dialog is open, find-bar and toolbar visibility. */
object Nav {
    var page by mutableStateOf(Page.NONE)
    var menu by mutableStateOf(false)
    var siteInfo by mutableStateOf(false)
    var clearData by mutableStateOf(false)
    var find by mutableStateOf(false)
    var chromeHidden by mutableStateOf(false)
    var resumeTick by mutableIntStateOf(0)
    var blurTick by mutableIntStateOf(0)       // bump = drop focus from the address bar
    var omniFocused by mutableStateOf(false)
}

/**
 * All user settings. Every property is Compose state AND persisted in SharedPreferences.
 */
object AppSettings {
    private var sp: SharedPreferences? = null
    private val registry = ArrayList<Pref<*>>()

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("browser", Context.MODE_PRIVATE)
        registry.forEach { it.load() }
    }

    fun prefs(): SharedPreferences? = sp

    class Pref<T : Any>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(def)

        init {
            registry.add(this)
        }

        @Suppress("UNCHECKED_CAST")
        fun load() {
            val s = sp ?: return
            val v: Any = when (def) {
                is Boolean -> s.getBoolean(key, def)
                is Int -> s.getInt(key, def)
                else -> s.getString(key, def as String) ?: def
            }
            state.value = v as T
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val e = sp?.edit() ?: return
            when (value) {
                is Boolean -> e.putBoolean(key, value)
                is Int -> e.putInt(key, value)
                else -> e.putString(key, value as String)
            }
            e.apply()
        }
    }

    // ---- general
    var amber by Pref("amber", false)
    var homepage by Pref("homepage", "")                 // "" = speed-dial start page
    var engine by Pref("engine", "google")
    var suggestions by Pref("suggestions", true)
    var restoreTabs by Pref("restoreTabs", true)
    var hideBars by Pref("hideBars", true)

    // ---- privacy & security
    var adBlock by Pref("adBlock", true)
    var httpsOnly by Pref("httpsOnly", false)
    var blockPopups by Pref("blockPopups", true)
    var dnt by Pref("dnt", true)
    var cookies by Pref("cookies", true)
    var blockThirdCookies by Pref("blockThirdCookies", true)
    var javascript by Pref("javascript", true)
    var safeBrowsing by Pref("safeBrowsing", true)
    var clearOnExit by Pref("clearOnExit", false)

    // ---- display
    var textZoom by Pref("textZoom", 100)
    var darkWeb by Pref("darkWeb", false)
    var desktopDefault by Pref("desktopDefault", false)
    var pinchZoom by Pref("pinchZoom", true)
    var blockImages by Pref("blockImages", false)
    var readerSize by Pref("readerSize", 18)

    // ---- downloads
    var confirmDownloads by Pref("confirmDownloads", true)

    // ---- session
    var session by Pref("session", "")
    var sessionActive by Pref("sessionActive", 0)
}
