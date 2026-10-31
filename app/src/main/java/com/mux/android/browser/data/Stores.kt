package com.mux.android.browser.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Tiny JSON-file storage. Writes happen on a background thread. */
object Store {
    private var dir: File? = null
    private val io = Executors.newSingleThreadExecutor()

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        BookmarkStore.load()
        HistoryStore.load()
        DownloadStore.load()
        SiteStore.load()
    }

    fun read(name: String): String? = try {
        val f = File(dir, name)
        if (f.exists()) f.readText() else null
    } catch (e: Exception) {
        null
    }

    fun write(name: String, text: String) {
        val d = dir ?: return
        io.execute {
            try {
                File(d, name).writeText(text)
            } catch (e: Exception) {
            }
        }
    }
}

data class Bookmark(val url: String, val title: String, val time: Long)

object BookmarkStore {
    val items = mutableStateListOf<Bookmark>()

    fun load() {
        try {
            val a = JSONArray(Store.read("bookmarks.json") ?: return)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(Bookmark(o.getString("u"), o.optString("t"), o.optLong("d")))
            }
        } catch (e: Exception) {
        }
    }

    private fun save() {
        val a = JSONArray()
        for (b in items.toList()) a.put(JSONObject().put("u", b.url).put("t", b.title).put("d", b.time))
        Store.write("bookmarks.json", a.toString())
    }

    fun has(url: String) = url.isNotEmpty() && items.any { it.url == url }

    fun toggle(url: String, title: String): Boolean {
        if (url.isEmpty()) return false
        val i = items.indexOfFirst { it.url == url }
        val added = i < 0
        if (added) items.add(0, Bookmark(url, title.ifBlank { url }, System.currentTimeMillis())) else items.removeAt(i)
        save()
        return added
    }

    fun remove(url: String) {
        items.removeAll { it.url == url }
        save()
    }
}

data class HistItem(val url: String, val title: String, val time: Long, val count: Int)

object HistoryStore {
    val items = mutableStateListOf<HistItem>()
    private const val MAX = 3000

    fun load() {
        try {
            val a = JSONArray(Store.read("history.json") ?: return)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(HistItem(o.getString("u"), o.optString("t"), o.optLong("d"), o.optInt("c", 1)))
            }
        } catch (e: Exception) {
        }
    }

    private fun save() {
        val a = JSONArray()
        for (h in items.toList()) a.put(JSONObject().put("u", h.url).put("t", h.title).put("d", h.time).put("c", h.count))
        Store.write("history.json", a.toString())
    }

    fun add(url: String, title: String) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        val i = items.indexOfFirst { it.url == url }
        var count = 1
        var t = title
        if (i >= 0) {
            count = items[i].count + 1
            if (t.isBlank()) t = items[i].title
            items.removeAt(i)
        }
        items.add(0, HistItem(url, t, System.currentTimeMillis(), count))
        while (items.size > MAX) items.removeAt(items.size - 1)
        save()
    }

    fun remove(url: String) {
        items.removeAll { it.url == url }
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    fun mostVisited(max: Int): List<HistItem> =
        items.toList().sortedByDescending { it.count }.take(max)
}

data class Dl(val id: Long, val name: String, val url: String, val time: Long)

object DownloadStore {
    val items = mutableStateListOf<Dl>()

    fun load() {
        try {
            val a = JSONArray(Store.read("downloads.json") ?: return)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(Dl(o.getLong("i"), o.optString("n"), o.optString("u"), o.optLong("d")))
            }
        } catch (e: Exception) {
        }
    }

    private fun save() {
        val a = JSONArray()
        for (d in items.toList()) a.put(JSONObject().put("i", d.id).put("n", d.name).put("u", d.url).put("d", d.time))
        Store.write("downloads.json", a.toString())
    }

    fun add(d: Dl) {
        items.add(0, d)
        save()
    }

    fun remove(id: Long) {
        items.removeAll { it.id == id }
        save()
    }

    fun clear() {
        items.clear()
        save()
    }
}

/** Sites where the ad blocker is switched off by the user. */
object SiteStore {
    @Volatile
    var allowed: Set<String> = emptySet()
        private set

    fun load() {
        allowed = (Store.read("sites.txt") ?: "").lines().filter { it.isNotBlank() }.toSet()
    }

    fun isAllowed(host: String?): Boolean = host != null && allowed.contains(host)

    fun toggle(host: String): Boolean {
        val s = allowed.toMutableSet()
        val nowAllowed = if (s.contains(host)) {
            s.remove(host)
            false
        } else {
            s.add(host)
            true
        }
        allowed = s
        Store.write("sites.txt", s.joinToString("\n"))
        return nowAllowed
    }
}
