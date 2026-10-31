package com.mux.android.browser.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mux.android.browser.data.AppSettings
import com.mux.android.browser.data.Nav
import com.mux.android.browser.data.Page
import com.mux.android.browser.engine.BTab
import com.mux.android.browser.engine.Tabs

@Composable
fun BrowserRoot() {
    val ctx = LocalContext.current
    HudTheme(if (AppSettings.amber) AmberPalette else CyanPalette) {
        val tablet = LocalConfiguration.current.screenWidthDp >= 600
        val focus = LocalFocusManager.current

        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        // any change of a web-related setting is pushed into every open WebView
        val sig = listOf(
            AppSettings.javascript, AppSettings.blockPopups, AppSettings.pinchZoom, AppSettings.textZoom,
            AppSettings.blockImages, AppSettings.safeBrowsing, AppSettings.darkWeb, AppSettings.cookies,
            AppSettings.blockThirdCookies
        )
        LaunchedEffect(sig) { Tabs.applyAll() }
        LaunchedEffect(Nav.blurTick) { if (Nav.blurTick > 0) focus.clearFocus() }

        val tab = Tabs.active
        HudBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (tab != null) BrowserBody(tab, tablet)
                when (Nav.page) {
                    Page.TABS -> TabsScreen(tablet)
                    Page.BOOKMARKS -> BookmarksScreen()
                    Page.HISTORY -> HistoryScreen()
                    Page.DOWNLOADS -> DownloadsScreen()
                    Page.SYS -> SystemScreen(tablet) { update = it }
                    Page.NONE -> {}
                }
            }
        }

        if (Nav.menu && tab != null) MenuDialog(tab)
        if (Nav.siteInfo && tab != null) SiteInfoDialog(tab)
        if (Nav.clearData) ClearDataDialog()
        PromptHost()
        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { openUrl(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }
    }
}

@Composable
private fun BrowserBody(tab: BTab, tablet: Boolean) {
    val showChrome = !Nav.chromeHidden || Nav.omniFocused || tab.url.isEmpty()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 6.dp, vertical = 4.dp)) {
        if (tablet) TabStrip()
        if (showChrome) AddressBar(tab, Modifier.fillMaxWidth().height(46.dp))
        ProgressLine(tab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                Nav.page != Page.NONE -> {}
                Nav.omniFocused -> OmniDropdown(tab)
                tab.url.isEmpty() -> NewTabPage(tab, tablet)
                else -> WebHost(tab)
            }
        }
        if (Nav.find && tab.url.isNotEmpty()) FindBar(tab)
        if (showChrome) BottomBar(tab)
    }
}

@Composable
private fun WebHost(tab: BTab) {
    key(tab.id, tab.viewGen) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                Tabs.ensureWeb(tab)
                val box = tab.container
                if (box == null) {
                    View(c)
                } else {
                    (box.parent as? ViewGroup)?.removeView(box)
                    box
                }
            }
        )
    }
}

@Composable
private fun ProgressLine(tab: BTab) {
    val c = Hud.colors
    Box(Modifier.fillMaxWidth().height(3.dp)) {
        if (tab.loading && tab.progress < 100) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(tab.progress.coerceIn(3, 100) / 100f)
                    .background(c.accent)
            )
        }
    }
}

@Composable
private fun BottomBar(tab: BTab) {
    Row(Modifier.fillMaxWidth().height(50.dp)) {
        HudButton("◂", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, enabled = tab.canBack || tab.url.isNotEmpty() || tab.parent != null, fontSize = 22.sp) {
            Tabs.back()
        }
        HudButton("▸", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, enabled = tab.canForward, fontSize = 22.sp) {
            Tabs.forward(tab)
        }
        HudButton("⌂", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 20.sp) {
            Tabs.home(tab)
        }
        HudButton(Tabs.list.size.toString(), Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, fontSize = 16.sp, sub = "TABS") {
            Nav.page = Page.TABS
        }
        HudButton("⋮", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 22.sp) {
            Nav.menu = true
        }
    }
}

/** Tablet only: Chrome-like strip with every open tab. */
@Composable
private fun TabStrip() {
    Row(Modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState())) {
        for (t in Tabs.list.toList()) {
            HudButton(
                label = (if (t.isPrivate) "◐ " else "") + t.displayTitle.take(18),
                modifier = Modifier.height(34.dp).widthIn(min = 110.dp),
                kind = if (t.isPrivate) KeyKind.ACCENT else KeyKind.OP,
                selected = t.id == Tabs.activeId,
                fontSize = 11.sp,
                onLongClick = { Tabs.close(t) }
            ) { Tabs.select(t) }
        }
        HudButton("+", Modifier.height(34.dp).widthIn(min = 40.dp), KeyKind.GOOD, fontSize = 16.sp) { Tabs.newTab() }
    }
}
