package com.mux.android.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.browser.data.AppSettings
import com.mux.android.browser.data.BookmarkStore
import com.mux.android.browser.data.HistoryStore
import com.mux.android.browser.data.Nav
import com.mux.android.browser.engine.BTab
import com.mux.android.browser.engine.SearchEngines
import com.mux.android.browser.engine.Tabs
import com.mux.android.browser.util.prettyHost
import kotlinx.coroutines.delay

object Omni {
    var text by mutableStateOf("")
}

private fun display(t: BTab): String = if (t.url.isEmpty() || t.url == "about:blank") "" else prettyHost(t.url)

@Composable
fun AddressBar(tab: BTab, modifier: Modifier = Modifier) {
    val c = Hud.colors
    val focus = LocalFocusManager.current
    var tfv by remember { mutableStateOf(TextFieldValue("")) }
    val focused = Nav.omniFocused
    val shown = if (focused) tfv else TextFieldValue(display(tab))
    val frame = if (tab.isPrivate) c.accent else c.primary
    val shape = HudShape(8.dp)
    val starred = BookmarkStore.has(tab.url)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val secureKind = when {
            tab.url.isEmpty() -> KeyKind.FN
            tab.secure -> KeyKind.GOOD
            else -> KeyKind.DANGER
        }
        HudButton(
            label = if (tab.isPrivate) "◐" else if (tab.url.isEmpty() || tab.secure) "◈" else "!",
            modifier = Modifier.width(42.dp).fillMaxHeight(),
            kind = if (tab.isPrivate) KeyKind.ACCENT else secureKind,
            enabled = tab.url.isNotEmpty(),
            fontSize = 16.sp
        ) { Nav.siteInfo = true }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(2.dp)
                .background(frame.copy(alpha = 0.07f), shape)
                .border(1.dp, frame.copy(alpha = if (focused) 0.9f else 0.5f), shape)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (shown.text.isEmpty()) {
                HudText(if (tab.isPrivate) "PRIVATE · SEARCH OR ENTER ADDRESS" else "SEARCH OR ENTER ADDRESS", color = c.dim, size = 12.sp, maxLines = 1)
            }
            BasicTextField(
                value = shown,
                onValueChange = {
                    if (focused) {
                        tfv = it
                        Omni.text = it.text
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    Tabs.load(tab, tfv.text)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { st ->
                        Nav.omniFocused = st.isFocused
                        if (st.isFocused) {
                            val u = if (tab.url == "about:blank") "" else tab.url
                            tfv = TextFieldValue(u, TextRange(0, u.length))
                            Omni.text = u
                        }
                    }
            )
        }

        if (focused) {
            HudButton("✕", Modifier.width(42.dp).fillMaxHeight(), KeyKind.FN, fontSize = 16.sp) {
                tfv = TextFieldValue("")
                Omni.text = ""
            }
        } else {
            HudButton(
                if (starred) "★" else "☆", Modifier.width(42.dp).fillMaxHeight(),
                if (starred) KeyKind.ACCENT else KeyKind.FN,
                enabled = tab.url.isNotEmpty(), fontSize = 18.sp
            ) {
                BookmarkStore.toggle(tab.url, tab.displayTitle)
            }
            HudButton(
                if (tab.loading) "✕" else "⟳", Modifier.width(42.dp).fillMaxHeight(), KeyKind.OP,
                enabled = tab.url.isNotEmpty(), fontSize = 18.sp
            ) {
                if (tab.loading) Tabs.stop(tab) else Tabs.reload(tab)
            }
        }
    }
}

private class Sugg(val text: String, val kind: String, val icon: String)

/** Dropdown shown instead of the page while the address bar has focus. */
@Composable
fun OmniDropdown(tab: BTab, modifier: Modifier = Modifier) {
    val c = Hud.colors
    val focus = LocalFocusManager.current
    val q = Omni.text.trim()
    var remote by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(q) {
        remote = emptyList()
        if (AppSettings.suggestions && q.length >= 2) {
            delay(250)
            remote = SearchEngines.suggest(q)
        }
    }

    val local = remember(q, BookmarkStore.items.size, HistoryStore.items.size) {
        val l = q.lowercase()
        val out = ArrayList<Sugg>()
        if (l.length >= 1) {
            for (b in BookmarkStore.items) {
                if (out.size >= 3) break
                if (b.url.lowercase().contains(l) || b.title.lowercase().contains(l)) out.add(Sugg(b.url, b.title, "★"))
            }
            for (h in HistoryStore.items) {
                if (out.size >= 6) break
                if (out.any { it.text == h.url }) continue
                if (h.url.lowercase().contains(l) || h.title.lowercase().contains(l)) out.add(Sugg(h.url, h.title.ifBlank { h.url }, "◷"))
            }
        } else {
            for (h in HistoryStore.mostVisited(6)) out.add(Sugg(h.url, h.title.ifBlank { h.url }, "◷"))
        }
        out
    }

    fun go(text: String) {
        focus.clearFocus()
        Tabs.load(tab, text)
    }

    Column(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (q.isNotEmpty()) {
                item {
                    HudItem(Modifier.fillMaxWidth(), onClick = { go(q) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HudText("▸", color = c.accent, size = 18.sp, bold = true, modifier = Modifier.width(26.dp))
                            HudText(q, color = c.text, size = 14.sp, bold = true, maxLines = 1, modifier = Modifier.weight(1f))
                            HudText(SearchEngines.current().name, color = c.dim, size = 10.sp)
                        }
                    }
                }
            }
            items(local) { s ->
                HudItem(Modifier.fillMaxWidth(), onClick = { go(s.text) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HudText(s.icon, color = c.primary, size = 16.sp, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(s.kind, color = c.text, size = 13.sp, maxLines = 1)
                            HudText(s.text, color = c.dim, size = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
            items(remote) { s ->
                HudItem(Modifier.fillMaxWidth(), onClick = { go(s) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HudText("⌕", color = c.dim, size = 16.sp, modifier = Modifier.width(26.dp))
                        HudText(s, color = c.text, size = 13.sp, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
