package com.mux.android.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** WebView that reports vertical scrolling (used to hide / show the toolbars). */
class HudWebView(ctx: Context) : WebView(ctx) {
    var onScroll: ((y: Int, dy: Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScroll?.invoke(t, t - oldt)
    }
}

/** One browser tab. The WebView is created lazily, the first time the tab is shown. */
class BTab(val id: Long, val isPrivate: Boolean) {
    var title by mutableStateOf("")
    var url by mutableStateOf("")            // "" = start page
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var desktop by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var findIndex by mutableIntStateOf(0)
    var findCount by mutableIntStateOf(0)

    var parent: BTab? = null
    var web: HudWebView? = null
    var container: SwipeRefreshLayout? = null
    var pendingUrl: String? = null
    var upgradedFrom: String? = null
    var errorShown = false
    var viewGen by mutableIntStateOf(0)       // bumped when the WebView is re-created
    var isolated = false                     // true when the WebView uses the separate private profile

    val secure: Boolean get() = url.startsWith("https://")
    val displayTitle: String
        get() = title.ifBlank { if (url.isEmpty()) "NEW TAB" else com.mux.android.browser.util.prettyHost(url) }
}
