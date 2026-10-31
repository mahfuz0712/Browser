package com.mux.android.browser.engine

import com.mux.android.browser.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Engine(val key: String, val name: String, val template: String, val suggest: String?)

object SearchEngines {
    val all = listOf(
        Engine("google", "GOOGLE", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
        Engine("ddg", "DUCKDUCKGO", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?type=list&q=%s"),
        Engine("bing", "BING", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
        Engine("brave", "BRAVE", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"),
        Engine("ecosia", "ECOSIA", "https://www.ecosia.org/search?q=%s", "https://ac.ecosia.org/autocomplete?type=list&q=%s"),
        Engine("startpage", "STARTPAGE", "https://www.startpage.com/do/search?q=%s", null),
        Engine("yahoo", "YAHOO", "https://search.yahoo.com/search?p=%s", null)
    )

    fun current(): Engine = all.firstOrNull { it.key == AppSettings.engine } ?: all[0]

    fun searchUrl(q: String): String =
        current().template.replace("%s", URLEncoder.encode(q, "UTF-8"))

    private val schemeRe = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostRe = Regex("^([^\\s/?#@]+\\.)+[a-zA-Z]{2,}(:\\d+)?([/?#].*)?$")
    private val ipRe = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?([/?#].*)?$")
    private val localRe = Regex("^localhost(:\\d+)?([/?#].*)?$")

    /** Turns whatever was typed into a URL (or a search for it). Empty = nothing to do. */
    fun resolve(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        val lower = t.lowercase()
        if (lower.startsWith("javascript:")) return ""
        if (lower == "about:blank") return "about:blank"
        if (lower.startsWith("http://") || lower.startsWith("https://")) return t
        if (lower.startsWith("file:") || lower.startsWith("content:")) return ""
        if (schemeRe.containsMatchIn(t) && t.contains("://")) return t
        if (!t.contains(' ')) {
            if (localRe.matches(t) || ipRe.matches(t)) return "http://$t"
            if (hostRe.matches(t)) return "https://$t"
        }
        return searchUrl(t)
    }

    /** Online suggestions from the selected engine (empty on any failure). */
    suspend fun suggest(q: String): List<String> = withContext(Dispatchers.IO) {
        val e = current()
        val tpl = e.suggest ?: return@withContext emptyList()
        try {
            val c = URL(tpl.replace("%s", URLEncoder.encode(q, "UTF-8"))).openConnection() as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            if (c.responseCode != 200) return@withContext emptyList()
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            val list = arr.optJSONArray(1) ?: return@withContext emptyList()
            val out = ArrayList<String>()
            for (i in 0 until minOf(list.length(), 6)) out.add(list.optString(i))
            out
        } catch (ex: Exception) {
            emptyList()
        }
    }
}
