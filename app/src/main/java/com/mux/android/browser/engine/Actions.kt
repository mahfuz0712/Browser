package com.mux.android.browser.engine

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mux.android.browser.MainActivity
import com.mux.android.browser.R
import com.mux.android.browser.data.BookmarkStore
import com.mux.android.browser.data.DownloadStore
import com.mux.android.browser.data.HistoryStore
import com.mux.android.browser.util.toast
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/** One-shot page actions used by the menu. */
object Actions {
    fun toggleBookmark(ctx: Context, t: BTab) {
        if (t.url.isEmpty()) return
        val added = BookmarkStore.toggle(t.url, t.displayTitle)
        toast(ctx, if (added) "BOOKMARK SAVED" else "BOOKMARK REMOVED")
    }

    fun toggleDesktop(t: BTab) {
        t.desktop = !t.desktop
        val w = t.web
        if (w != null) {
            Tabs.configure(w, t)
            w.reload()
        }
    }

    fun translate(t: BTab) {
        if (t.url.isEmpty()) return
        val lang = Locale.getDefault().language.ifEmpty { "en" }
        Tabs.load(t, "https://translate.google.com/translate?sl=auto&tl=$lang&u=" + Uri.encode(t.url))
    }

    fun print(ctx: Context, t: BTab) {
        val w = t.web
        if (w == null || t.url.isEmpty()) return
        try {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val name = t.displayTitle.take(40)
            pm.print(name, w.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
        } catch (e: Exception) {
            toast(ctx, "PRINT NOT AVAILABLE")
        }
    }

    fun addToHome(ctx: Context, t: BTab) {
        if (t.url.isEmpty()) return
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            toast(ctx, "HOME SCREEN SHORTCUTS NOT SUPPORTED")
            return
        }
        try {
            val icon = t.favicon?.let { IconCompat.createWithBitmap(it) }
                ?: IconCompat.createWithResource(ctx, R.mipmap.ic_launcher)
            val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(t.url))
            val info = ShortcutInfoCompat.Builder(ctx, "site_" + t.url.hashCode())
                .setShortLabel(t.displayTitle.take(24))
                .setIcon(icon)
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
        } catch (e: Exception) {
            toast(ctx, "COULD NOT ADD SHORTCUT")
        }
    }

    fun reader(ctx: Context, t: BTab) {
        val w = t.web
        if (w == null || t.url.isEmpty()) return
        w.evaluateJavascript(Reader.JS) { res ->
            try {
                val s = JSONTokener(res).nextValue() as? String ?: return@evaluateJavascript
                val o = JSONObject(s)
                if (!o.optBoolean("ok")) {
                    toast(ctx, "NO ARTICLE FOUND ON THIS PAGE")
                } else {
                    val base = t.url
                    w.loadDataWithBaseURL(base, Reader.html(o.optString("title"), o.optString("html"), base), "text/html", "utf-8", base)
                }
            } catch (e: Exception) {
                toast(ctx, "READER MODE FAILED")
            }
        }
    }

    /** Best effort: expires every cookie that the page can see. */
    fun clearSiteData(ctx: Context, t: BTab) {
        val url = t.url
        if (url.isEmpty()) return
        val cm = CookieManager.getInstance()
        val cookies = cm.getCookie(url)
        if (!cookies.isNullOrEmpty()) {
            val host = Uri.parse(url).host ?: ""
            for (part in cookies.split(";")) {
                val name = part.substringBefore("=").trim()
                if (name.isEmpty()) continue
                cm.setCookie(url, "$name=; Max-Age=0; Path=/")
                cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$host")
            }
            cm.flush()
        }
        t.web?.clearCache(true)
        toast(ctx, "SITE DATA CLEARED")
    }

    fun clearData(ctx: Context, history: Boolean, cookies: Boolean, cache: Boolean, downloads: Boolean) {
        if (history) HistoryStore.clear()
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
        if (cache) for (t in Tabs.list) t.web?.clearCache(true)
        if (downloads) {
            try {
                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val ids = DownloadStore.items.map { it.id }.toLongArray()
                if (ids.isNotEmpty()) dm.remove(*ids)
            } catch (e: Exception) {
            }
            DownloadStore.clear()
        }
        toast(ctx, "DATA CLEARED")
    }

    /** Called when the app closes and "clear on exit" is on. */
    fun clearOnExit(ctx: Context) {
        clearData(ctx, history = true, cookies = true, cache = true, downloads = false)
    }
}
