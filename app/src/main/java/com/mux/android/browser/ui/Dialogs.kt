package com.mux.android.browser.ui

import android.webkit.JsPromptResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.browser.MainActivity
import com.mux.android.browser.data.AppSettings
import com.mux.android.browser.data.BookmarkStore
import com.mux.android.browser.data.Nav
import com.mux.android.browser.data.Page
import com.mux.android.browser.data.SiteStore
import com.mux.android.browser.engine.Actions
import com.mux.android.browser.engine.BTab
import com.mux.android.browser.engine.Downloader
import com.mux.android.browser.engine.Prompt
import com.mux.android.browser.engine.Prompts
import com.mux.android.browser.engine.Tabs
import com.mux.android.browser.util.copyText
import com.mux.android.browser.util.hostOf
import com.mux.android.browser.util.prettyHost
import com.mux.android.browser.util.shareText

// -------------------------------------------------------------------------------------------------
// page prompts (JS dialogs, SSL, permissions, downloads, link menu...)
// -------------------------------------------------------------------------------------------------

@Composable
fun PromptHost() {
    val p = Prompts.current ?: return
    val ctx = LocalContext.current
    val c = Hud.colors
    when (p) {
        is Prompt.Js -> {
            var text by remember(p) { mutableStateOf(p.default) }
            val cancel = { p.result.cancel(); Prompts.done() }
            HudDialog(prettyHost(p.origin).uppercase() + " SAYS", onDismiss = cancel) {
                HudText(p.message, color = c.text, size = 14.sp)
                if (p.kind == 2) {
                    Spacer(Modifier.height(10.dp))
                    HudField(text, "", Modifier.fillMaxWidth().height(44.dp)) { text = it }
                }
                if (p.kind == 0) {
                    DialogButtons(Triple("OK", KeyKind.ACCENT) { p.result.confirm(); Prompts.done() })
                } else {
                    DialogButtons(
                        Triple("CANCEL", KeyKind.FN, cancel),
                        Triple("OK", KeyKind.ACCENT) {
                            if (p.kind == 2) (p.result as JsPromptResult).confirm(text) else p.result.confirm()
                            Prompts.done()
                        }
                    )
                }
            }
        }
        is Prompt.Ssl -> {
            val back = { p.handler.cancel(); Prompts.done() }
            HudDialog("CERTIFICATE WARNING", onDismiss = back) {
                HudText("YOUR CONNECTION IS NOT PRIVATE", color = c.danger, size = 15.sp, bold = true)
                Spacer(Modifier.height(8.dp))
                HudText(p.error, color = c.text, size = 13.sp)
                HudText(p.url, color = c.dim, size = 10.sp, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                HudText("Attackers might be able to read what you send to this site.", color = c.dim, size = 11.sp)
                DialogButtons(
                    Triple("GO BACK", KeyKind.GOOD, back),
                    Triple("PROCEED ANYWAY", KeyKind.DANGER) { p.handler.proceed(); Prompts.done() }
                )
            }
        }
        is Prompt.Auth -> {
            var user by remember(p) { mutableStateOf("") }
            var pass by remember(p) { mutableStateOf("") }
            val cancel = { p.handler.cancel(); Prompts.done() }
            HudDialog("SIGN IN", onDismiss = cancel) {
                HudText(p.host, color = c.primary, size = 15.sp, bold = true)
                if (p.realm.isNotBlank()) HudText(p.realm, color = c.dim, size = 11.sp)
                Spacer(Modifier.height(10.dp))
                HudField(user, "USERNAME", Modifier.fillMaxWidth().height(44.dp)) { user = it }
                Spacer(Modifier.height(6.dp))
                HudField(pass, "PASSWORD", Modifier.fillMaxWidth().height(44.dp), password = true) { pass = it }
                DialogButtons(
                    Triple("CANCEL", KeyKind.FN, cancel),
                    Triple("SIGN IN", KeyKind.ACCENT) { p.handler.proceed(user, pass); Prompts.done() }
                )
            }
        }
        is Prompt.Perm -> {
            HudDialog("PERMISSION REQUEST", onDismiss = { p.decide(false); Prompts.done() }) {
                HudText(prettyHost(p.origin), color = c.primary, size = 15.sp, bold = true)
                Spacer(Modifier.height(6.dp))
                HudText("WANTS TO USE YOUR ${p.what}", color = c.text, size = 14.sp)
                DialogButtons(
                    Triple("BLOCK", KeyKind.DANGER) { p.decide(false); Prompts.done() },
                    Triple("ALLOW", KeyKind.GOOD) { p.decide(true); Prompts.done() }
                )
            }
        }
        is Prompt.Download -> {
            HudDialog("DOWNLOAD FILE", onDismiss = { Prompts.done() }) {
                HudText(p.name, color = c.primary, size = 16.sp, bold = true)
                HudText(prettyHost(p.url) + (p.mime?.let { "  ·  $it" } ?: ""), color = c.dim, size = 11.sp)
                Spacer(Modifier.height(4.dp))
                HudText("SAVED TO YOUR DOWNLOADS FOLDER", color = c.dim, size = 10.sp)
                DialogButtons(
                    Triple("CANCEL", KeyKind.FN) { Prompts.done() },
                    Triple("DOWNLOAD", KeyKind.ACCENT) {
                        Downloader.enqueue(ctx, p.url, p.ua, p.mime, p.name, Tabs.active?.url)
                        Prompts.done()
                    }
                )
            }
        }
        is Prompt.ExternalApp -> {
            HudDialog("OPEN IN ANOTHER APP?", onDismiss = { Prompts.done() }) {
                HudText(p.label, color = c.text, size = 12.sp, maxLines = 4)
                DialogButtons(
                    Triple("CANCEL", KeyKind.FN) { Prompts.done() },
                    Triple("OPEN", KeyKind.ACCENT) { p.open(); Prompts.done() }
                )
            }
        }
        is Prompt.Link -> LinkMenu(p)
    }
}

@Composable
private fun LinkMenu(p: Prompt.Link) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val cur = Tabs.active
    val done = { Prompts.done() }
    HudDialog(if (p.link != null) "LINK" else "IMAGE", onDismiss = done) {
        HudText((p.link ?: p.image ?: "").take(120), color = c.dim, size = 10.sp, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        val rows = ArrayList<Triple<String, KeyKind, () -> Unit>>()
        if (p.link != null) {
            rows.add(Triple("OPEN IN NEW TAB", KeyKind.GOOD) { Tabs.newTab(p.link, p.priv, select = true, parent = cur); done() })
            rows.add(Triple("OPEN IN BACKGROUND", KeyKind.OP) { Tabs.newTab(p.link, p.priv, select = false, parent = cur); done() })
            if (!p.priv) rows.add(Triple("OPEN IN PRIVATE TAB", KeyKind.ACCENT) { Tabs.newTab(p.link, true, select = true, parent = cur); done() })
            rows.add(Triple("COPY LINK", KeyKind.FN) { copyText(ctx, p.link); done() })
            rows.add(Triple("SHARE LINK", KeyKind.FN) { shareText(ctx, p.link); done() })
        }
        if (p.image != null) {
            rows.add(Triple("DOWNLOAD IMAGE", KeyKind.ACCENT) {
                val name = android.webkit.URLUtil.guessFileName(p.image, null, null)
                if (cur != null) Downloader.enqueue(ctx, p.image, Tabs.uaFor(ctx, cur), null, name, cur.url)
                done()
            })
            rows.add(Triple("OPEN IMAGE IN NEW TAB", KeyKind.OP) { Tabs.newTab(p.image, p.priv, select = true, parent = cur); done() })
            rows.add(Triple("COPY IMAGE ADDRESS", KeyKind.FN) { copyText(ctx, p.image); done() })
        }
        for ((label, kind, action) in rows) {
            HudButton(label, Modifier.fillMaxWidth().height(44.dp), kind, fontSize = 12.sp, onClick = action)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// main menu
// -------------------------------------------------------------------------------------------------

@Composable
fun MenuDialog(tab: BTab) {
    val ctx = LocalContext.current
    val act = ctx as? MainActivity
    val hasPage = tab.url.isNotEmpty()
    val close = { Nav.menu = false }
    val adsOff = SiteStore.allowed.contains(hostOf(tab.url))

    class Item(val label: String, val kind: KeyKind = KeyKind.OP, val sel: Boolean = false, val on: Boolean = true, val run: () -> Unit)

    val items = listOf(
        Item("NEW TAB", KeyKind.GOOD) { close(); Tabs.newTab() },
        Item("PRIVATE TAB", KeyKind.ACCENT) { close(); Tabs.newTab(null, true) },
        Item("BOOKMARKS") { close(); Nav.page = Page.BOOKMARKS },
        Item("HISTORY") { close(); Nav.page = Page.HISTORY },
        Item("DOWNLOADS") { close(); Nav.page = Page.DOWNLOADS },
        Item("SETTINGS") { close(); Nav.page = Page.SYS },
        Item(if (BookmarkStore.has(tab.url)) "★ SAVED" else "☆ BOOKMARK", KeyKind.ACCENT, BookmarkStore.has(tab.url), hasPage) { close(); Actions.toggleBookmark(ctx, tab) },
        Item("SHARE", on = hasPage) { close(); shareText(ctx, tab.url) },
        Item("COPY LINK", on = hasPage) { close(); copyText(ctx, tab.url) },
        Item("FIND IN PAGE", on = hasPage) { close(); Nav.find = true },
        Item("READER MODE", on = hasPage) { close(); Actions.reader(ctx, tab) },
        Item("DESKTOP SITE", KeyKind.OP, tab.desktop, hasPage) { close(); Actions.toggleDesktop(tab) },
        Item("DARK PAGES", KeyKind.OP, AppSettings.darkWeb) { close(); AppSettings.darkWeb = !AppSettings.darkWeb; Tabs.applyAll(); Tabs.active?.web?.reload() },
        Item(if (adsOff) "ADS: ALLOWED" else "ADS: BLOCKED", KeyKind.GOOD, !adsOff, hasPage) { close(); SiteStore.toggle(hostOf(tab.url)); Tabs.reload(tab) },
        Item("TRANSLATE", on = hasPage) { close(); Actions.translate(tab) },
        Item("PRINT / PDF", on = hasPage) { close(); Actions.print(ctx, tab) },
        Item("ADD TO HOME", on = hasPage) { close(); Actions.addToHome(ctx, tab) },
        Item("SITE INFO", on = hasPage) { close(); Nav.siteInfo = true },
        Item("CLEAR DATA", KeyKind.DANGER) { close(); Nav.clearData = true },
        Item("CLOSE TAB", KeyKind.DANGER) { close(); Tabs.close(tab) },
        Item("EXIT", KeyKind.DANGER) { close(); act?.finish() }
    )

    HudDialog("MENU", onDismiss = { Nav.menu = false }) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            for (row in items.chunked(3)) {
                Row(Modifier.fillMaxWidth().height(54.dp)) {
                    for (m in row) {
                        HudButton(m.label, Modifier.weight(1f).fillMaxHeight(), m.kind, enabled = m.on, selected = m.sel, fontSize = 11.sp, onClick = m.run)
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// site info, clear data, find bar
// -------------------------------------------------------------------------------------------------

@Composable
fun SiteInfoDialog(tab: BTab) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val host = hostOf(tab.url)
    val allowed = SiteStore.allowed.contains(host)
    HudDialog("SITE INFO", onDismiss = { Nav.siteInfo = false }) {
        HudText(host.ifEmpty { tab.url }, color = c.primary, size = 18.sp, bold = true)
        Spacer(Modifier.height(6.dp))
        HudText(
            if (tab.secure) "● SECURE CONNECTION (HTTPS)" else "● NOT SECURE (HTTP)",
            color = if (tab.secure) c.good else c.danger, size = 12.sp, bold = true
        )
        HudText("TRACKERS / ADS BLOCKED: ${tab.blocked}", color = c.text, size = 12.sp)
        if (tab.isPrivate) {
            HudText(
                if (tab.isolated) "PRIVATE TAB: SEPARATE COOKIES, DELETED WHEN THE LAST PRIVATE TAB CLOSES"
                else "PRIVATE TAB: NO HISTORY / CACHE. COOKIES ARE SHARED (OLD WEBVIEW)",
                color = c.accent, size = 10.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        HudButton(
            if (allowed) "AD BLOCKER: OFF FOR THIS SITE" else "AD BLOCKER: ON FOR THIS SITE",
            Modifier.fillMaxWidth().height(44.dp), if (allowed) KeyKind.FN else KeyKind.GOOD, fontSize = 12.sp
        ) {
            SiteStore.toggle(host)
            Tabs.reload(tab)
        }
        HudButton(
            if (tab.desktop) "DESKTOP SITE: ON" else "DESKTOP SITE: OFF",
            Modifier.fillMaxWidth().height(44.dp), if (tab.desktop) KeyKind.ACCENT else KeyKind.FN, fontSize = 12.sp
        ) { Actions.toggleDesktop(tab) }
        HudButton("CLEAR COOKIES & CACHE OF THIS SITE", Modifier.fillMaxWidth().height(44.dp), KeyKind.DANGER, fontSize = 12.sp) {
            Actions.clearSiteData(ctx, tab)
        }
        HudButton("CLOSE", Modifier.fillMaxWidth().height(44.dp), KeyKind.FN, fontSize = 12.sp) { Nav.siteInfo = false }
    }
}

@Composable
fun ClearDataDialog() {
    val ctx = LocalContext.current
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var downloads by remember { mutableStateOf(false) }
    val c = Hud.colors
    HudDialog("CLEAR BROWSING DATA", onDismiss = { Nav.clearData = false }) {
        @Composable
        fun row(label: String, v: Boolean, set: (Boolean) -> Unit) {
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                HudText(label, color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
                HudButton(if (v) "ON" else "OFF", Modifier.width(80.dp).height(36.dp), if (v) KeyKind.GOOD else KeyKind.FN, fontSize = 11.sp) { set(!v) }
            }
        }
        row("BROWSING HISTORY", history) { history = it }
        row("COOKIES & SITE DATA", cookies) { cookies = it }
        row("CACHED FILES", cache) { cache = it }
        row("DOWNLOAD LIST + FILES", downloads) { downloads = it }
        DialogButtons(
            Triple("CANCEL", KeyKind.FN) { Nav.clearData = false },
            Triple("CLEAR", KeyKind.DANGER) {
                Actions.clearData(ctx, history, cookies, cache, downloads)
                Nav.clearData = false
            }
        )
    }
}

@Composable
fun FindBar(tab: BTab) {
    var q by remember { mutableStateOf("") }
    val c = Hud.colors
    LaunchedEffect(q) {
        val w = tab.web ?: return@LaunchedEffect
        if (q.isEmpty()) w.clearMatches() else w.findAllAsync(q)
    }
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        HudField(q, "FIND IN PAGE", Modifier.weight(1f).fillMaxHeight(), ime = androidx.compose.ui.text.input.ImeAction.Search) { q = it }
        HudText(
            if (q.isEmpty()) "" else if (tab.findCount == 0) " 0/0 " else " ${tab.findIndex + 1}/${tab.findCount} ",
            color = c.dim, size = 11.sp
        )
        HudButton("▴", Modifier.width(40.dp).fillMaxHeight(), KeyKind.OP, fontSize = 16.sp) { tab.web?.findNext(false) }
        HudButton("▾", Modifier.width(40.dp).fillMaxHeight(), KeyKind.OP, fontSize = 16.sp) { tab.web?.findNext(true) }
        HudButton("✕", Modifier.width(40.dp).fillMaxHeight(), KeyKind.DANGER, fontSize = 14.sp) {
            Nav.find = false
            tab.web?.clearMatches()
        }
    }
}
