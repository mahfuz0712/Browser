package com.mux.android.browser.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.browser.data.Bookmark
import com.mux.android.browser.data.BookmarkStore
import com.mux.android.browser.data.DownloadStore
import com.mux.android.browser.data.HistItem
import com.mux.android.browser.data.HistoryStore
import com.mux.android.browser.data.Nav
import com.mux.android.browser.data.Page
import com.mux.android.browser.engine.BTab
import com.mux.android.browser.engine.SearchEngines
import com.mux.android.browser.engine.Tabs
import com.mux.android.browser.util.copyText
import com.mux.android.browser.util.prettyHost
import com.mux.android.browser.util.toast
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// -------------------------------------------------------------------------------------------------
// start page (speed dial)
// -------------------------------------------------------------------------------------------------

private class Tile(val url: String, val title: String)

@Composable
fun NewTabPage(tab: BTab, tablet: Boolean) {
    val c = Hud.colors
    var q by remember { mutableStateOf("") }
    val tiles = remember(BookmarkStore.items.size, HistoryStore.items.size) {
        val out = ArrayList<Tile>()
        val seen = HashSet<String>()
        for (b in BookmarkStore.items) if (out.size < 8 && seen.add(prettyHost(b.url))) out.add(Tile(b.url, b.title))
        for (h in HistoryStore.mostVisited(24)) if (out.size < 8 && seen.add(prettyHost(h.url))) out.add(Tile(h.url, h.title))
        out
    }
    val cols = if (tablet) 6 else 4

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (tablet) 40.dp else 18.dp))
            HudText("◈ BROWSER", color = if (tab.isPrivate) c.accent else c.primary, size = 32.sp, bold = true)
            HudText(
                if (tab.isPrivate) "PRIVATE TAB" else "SEARCH WITH ${SearchEngines.current().name}",
                color = c.dim, size = 11.sp
            )
            Spacer(Modifier.height(16.dp))
            HudField(q, "SEARCH OR ENTER ADDRESS", Modifier.fillMaxWidth().height(50.dp), ime = ImeAction.Go, onGo = {
                if (q.isNotBlank()) Tabs.load(tab, q)
            }) { q = it }
            Spacer(Modifier.height(14.dp))

            if (tab.isPrivate) {
                HudPanel(Modifier.fillMaxWidth(), "PRIVATE BROWSING") {
                    HudText("◐ Pages you visit here are not saved in history, and the cache is not kept.", color = c.text, size = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    HudText(
                        if (com.mux.android.browser.engine.PrivateProfile.isolatedSupported)
                            "Cookies live in a separate jar that is deleted when the last private tab closes."
                        else "This WebView cannot separate cookies, so they are shared with normal tabs.",
                        color = c.dim, size = 11.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    HudText("Downloads and bookmarks you make are kept. Your internet provider and the sites can still see your visit.", color = c.dim, size = 11.sp)
                }
            } else {
                HudPanel(Modifier.fillMaxWidth(), "SPEED DIAL") {
                    if (tiles.isEmpty()) {
                        HudText("Sites you bookmark or visit a lot appear here.", color = c.dim, size = 12.sp)
                    } else {
                        for (row in tiles.chunked(cols)) {
                            Row(Modifier.fillMaxWidth().height(84.dp)) {
                                for (t in row) {
                                    HudButton(
                                        label = prettyHost(t.url).firstOrNull()?.uppercase() ?: "#",
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        kind = KeyKind.OP,
                                        fontSize = 26.sp,
                                        sub = prettyHost(t.url).take(11)
                                    ) { Tabs.load(tab, t.url) }
                                }
                                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                HudButton("BOOKMARKS", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 11.sp) { Nav.page = Page.BOOKMARKS }
                HudButton("HISTORY", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 11.sp) { Nav.page = Page.HISTORY }
                HudButton("DOWNLOADS", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 11.sp) { Nav.page = Page.DOWNLOADS }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------
// tabs
// -------------------------------------------------------------------------------------------------

@Composable
fun TabsScreen(tablet: Boolean) {
    val c = Hud.colors
    val close = { Nav.page = Page.NONE }
    Overlay("TABS (${Tabs.list.size})", close, actions = {
        HudButton("+ TAB", Modifier.width(78.dp).height(38.dp), KeyKind.GOOD, fontSize = 11.sp) { close(); Tabs.newTab() }
        HudButton("+ PRIVATE", Modifier.width(92.dp).height(38.dp), KeyKind.ACCENT, fontSize = 11.sp) { close(); Tabs.newTab(null, true) }
    }) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(Tabs.list.toList(), key = { it.id }) { t ->
                HudItem(Modifier.fillMaxWidth(), selected = t.id == Tabs.activeId, onClick = { Tabs.select(t); close() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val fav = t.favicon
                        if (fav != null) {
                            Image(fav.asImageBitmap(), null, Modifier.size(34.dp))
                        } else {
                            Avatar(t.displayTitle, Modifier.size(34.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(t.displayTitle, color = c.text, size = 14.sp, bold = true, maxLines = 1)
                            HudText(
                                (if (t.isPrivate) "PRIVATE · " else "") + (if (t.url.isEmpty()) "START PAGE" else t.url),
                                color = if (t.isPrivate) c.accent else c.dim, size = 10.sp, maxLines = 1
                            )
                        }
                        HudButton("✕", Modifier.width(44.dp).height(40.dp), KeyKind.DANGER, fontSize = 14.sp) {
                            Tabs.close(t)
                            if (Tabs.list.isEmpty()) close()
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(46.dp)) {
            HudButton("CLOSE PRIVATE TABS", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, enabled = Tabs.list.any { it.isPrivate }, fontSize = 11.sp) {
                Tabs.closeAll(privateOnly = true)
            }
            HudButton("CLOSE ALL", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 11.sp) {
                Tabs.closeAll()
                close()
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// bookmarks
// -------------------------------------------------------------------------------------------------

@Composable
fun BookmarksScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf("") }
    val close = { Nav.page = Page.NONE }
    val list = remember(query, BookmarkStore.items.size) {
        val l = query.trim().lowercase()
        BookmarkStore.items.toList().filter { l.isEmpty() || it.title.lowercase().contains(l) || it.url.lowercase().contains(l) }
    }
    Overlay("BOOKMARKS", close) {
        HudField(query, "SEARCH BOOKMARKS", Modifier.fillMaxWidth().height(44.dp)) { query = it }
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) EmptyNote("NO BOOKMARKS.\nUSE ☆ IN THE ADDRESS BAR.")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(list, key = { it.url }) { b: Bookmark ->
                val open = expanded == b.url
                HudItem(Modifier.fillMaxWidth(), selected = open, onClick = { expanded = if (open) "" else b.url }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(prettyHost(b.url), Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(b.title, color = c.text, size = 14.sp, bold = true, maxLines = 1)
                            HudText(b.url, color = c.dim, size = 10.sp, maxLines = 1)
                        }
                    }
                    if (open) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HudButton("OPEN", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, fontSize = 11.sp) {
                                Tabs.active?.let { Tabs.load(it, b.url) }
                                close()
                            }
                            HudButton("NEW TAB", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp) { close(); Tabs.newTab(b.url) }
                            HudButton("COPY", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 11.sp) { copyText(ctx, b.url) }
                            HudButton("DEL", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 11.sp) { BookmarkStore.remove(b.url) }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// history
// -------------------------------------------------------------------------------------------------

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf("") }
    var sure by remember { mutableStateOf(false) }
    val close = { Nav.page = Page.NONE }
    val dayFmt = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }

    val rows: List<Any> = remember(query, HistoryStore.items.size) {
        val l = query.trim().lowercase()
        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        fun dayKey(t: Long) = (t + tz.getOffset(t)) / 86_400_000L
        val today = dayKey(now)
        val out = ArrayList<Any>()
        var last = Long.MIN_VALUE
        for (h in HistoryStore.items.toList()) {
            if (l.isNotEmpty() && !h.title.lowercase().contains(l) && !h.url.lowercase().contains(l)) continue
            val k = dayKey(h.time)
            if (k != last) {
                last = k
                out.add(
                    when (k) {
                        today -> "TODAY"
                        today - 1 -> "YESTERDAY"
                        else -> dayFmt.format(Date(h.time)).uppercase()
                    }
                )
            }
            out.add(h)
        }
        out
    }

    Overlay("HISTORY", close, actions = {
        HudButton(if (sure) "SURE?" else "CLEAR ALL", Modifier.width(92.dp).height(38.dp), KeyKind.DANGER, selected = sure, enabled = HistoryStore.items.isNotEmpty(), fontSize = 11.sp) {
            if (sure) {
                HistoryStore.clear()
                sure = false
            } else {
                sure = true
            }
        }
    }) {
        HudField(query, "SEARCH HISTORY", Modifier.fillMaxWidth().height(44.dp)) { query = it }
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) EmptyNote("NO HISTORY")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { if (it is HistItem) "u${it.url}" else "h$it" }) { r ->
                if (r is String) {
                    HudText(r, color = c.accent, size = 12.sp, bold = true, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp))
                } else if (r is HistItem) {
                    val open = expanded == r.url
                    HudItem(Modifier.fillMaxWidth(), selected = open, onClick = { expanded = if (open) "" else r.url }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(prettyHost(r.url), Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                HudText(r.title.ifBlank { prettyHost(r.url) }, color = c.text, size = 14.sp, bold = true, maxLines = 1)
                                HudText(r.url, color = c.dim, size = 10.sp, maxLines = 1)
                            }
                            HudText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(r.time)), color = c.dim, size = 10.sp)
                        }
                        if (open) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                HudButton("OPEN", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, fontSize = 11.sp) {
                                    Tabs.active?.let { Tabs.load(it, r.url) }
                                    close()
                                }
                                HudButton("NEW TAB", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp) { close(); Tabs.newTab(r.url) }
                                HudButton("COPY", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 11.sp) { copyText(ctx, r.url) }
                                HudButton("DEL", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 11.sp) { HistoryStore.remove(r.url) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// downloads
// -------------------------------------------------------------------------------------------------

private class DlInfo(val status: Int, val total: Long, val sofar: Long, val mime: String?)

private fun queryDownloads(ctx: Context): Map<Long, DlInfo> {
    val out = HashMap<Long, DlInfo>()
    val ids = DownloadStore.items.map { it.id }.toLongArray()
    if (ids.isEmpty()) return out
    try {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(*ids))?.use { cur ->
            val iId = cur.getColumnIndex(DownloadManager.COLUMN_ID)
            val iSt = cur.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val iTo = cur.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val iSo = cur.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val iMi = cur.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
            while (cur.moveToNext()) {
                out[cur.getLong(iId)] = DlInfo(cur.getInt(iSt), cur.getLong(iTo), cur.getLong(iSo), cur.getString(iMi))
            }
        }
    } catch (e: Exception) {
    }
    return out
}

private fun fmtBytes(b: Long): String = when {
    b >= 1_073_741_824L -> "%.2f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576L -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024L -> "%d KB".format(b / 1024)
    else -> "$b B"
}

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    var info by remember { mutableStateOf<Map<Long, DlInfo>>(emptyMap()) }
    val close = { Nav.page = Page.NONE }
    LaunchedEffect(DownloadStore.items.size) {
        while (true) {
            info = queryDownloads(ctx)
            delay(1000)
        }
    }
    Overlay("DOWNLOADS", close, actions = {
        HudButton("FOLDER", Modifier.width(78.dp).height(38.dp), KeyKind.OP, fontSize = 11.sp) {
            try {
                ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toast(ctx, "NO FILE MANAGER")
            }
        }
    }) {
        if (DownloadStore.items.isEmpty()) EmptyNote("NO DOWNLOADS YET")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(DownloadStore.items.toList(), key = { it.id }) { d ->
                val i = info[d.id]
                val status = when (i?.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "DONE"
                    DownloadManager.STATUS_FAILED -> "FAILED"
                    DownloadManager.STATUS_RUNNING -> "DOWNLOADING"
                    DownloadManager.STATUS_PAUSED -> "PAUSED"
                    DownloadManager.STATUS_PENDING -> "WAITING"
                    else -> "MISSING"
                }
                val pct = if (i != null && i.total > 0) (i.sofar * 100 / i.total).toInt() else 0
                HudItem(Modifier.fillMaxWidth()) {
                    HudText(d.name, color = c.text, size = 14.sp, bold = true, maxLines = 2)
                    HudText(prettyHost(d.url), color = c.dim, size = 10.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        HudText(
                            status + if (i != null && i.total > 0) "  ·  ${fmtBytes(i.sofar)} / ${fmtBytes(i.total)}" else if (i != null && i.sofar > 0) "  ·  ${fmtBytes(i.sofar)}" else "",
                            color = when (status) { "DONE" -> c.good; "FAILED", "MISSING" -> c.danger; else -> c.accent },
                            size = 11.sp, modifier = Modifier.weight(1f)
                        )
                        if (status == "DOWNLOADING") HudText("$pct%", color = c.accent, size = 11.sp)
                    }
                    if (status == "DOWNLOADING" || status == "PAUSED") {
                        val filled = (pct / 5).coerceIn(0, 20)
                        HudText("█".repeat(filled) + "░".repeat(20 - filled), color = c.primary, size = 11.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HudButton("OPEN", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, enabled = status == "DONE", fontSize = 11.sp) {
                            try {
                                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                val uri: Uri? = dm.getUriForDownloadedFile(d.id)
                                if (uri != null) {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, i?.mime ?: "*/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            } catch (e: Exception) {
                                toast(ctx, "NO APP TO OPEN THIS FILE")
                            }
                        }
                        HudButton("RE-GET", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, enabled = status == "FAILED" || status == "MISSING", fontSize = 11.sp) {
                            Tabs.newTab(d.url)
                            close()
                        }
                        HudButton("DEL", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 11.sp) {
                            try {
                                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.remove(d.id)
                            } catch (e: Exception) {
                            }
                            DownloadStore.remove(d.id)
                        }
                    }
                }
            }
        }
    }
}
