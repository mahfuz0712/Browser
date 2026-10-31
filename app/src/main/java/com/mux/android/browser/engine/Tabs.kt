package com.mux.android.browser.engine

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.mux.android.browser.MainActivity
import com.mux.android.browser.data.AppSettings
import com.mux.android.browser.data.HistoryStore
import com.mux.android.browser.data.Nav
import com.mux.android.browser.data.SiteStore
import com.mux.android.browser.util.hostOf
import com.mux.android.browser.util.prettyHost
import com.mux.android.browser.util.toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Real private tabs: when the installed WebView supports separate profiles, private tabs get their own
 * cookie / storage jar that is deleted when the last private tab closes. Looked up by reflection so the
 * app still works with older WebView versions.
 */
object PrivateProfile {
    private const val NAME = "hud_private"
    private var used = false

    private val supported: Boolean by lazy {
        try {
            WebViewFeature.isFeatureSupported("MULTI_PROFILE")
        } catch (e: Throwable) {
            false
        }
    }

    fun apply(web: WebView): Boolean {
        if (!supported) return false
        return try {
            val m = Class.forName("androidx.webkit.WebViewCompat")
                .getMethod("setProfile", WebView::class.java, String::class.java)
            m.invoke(null, web, NAME)
            used = true
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun deleteIfUsed() {
        if (!used) return
        try {
            val cls = Class.forName("androidx.webkit.ProfileStore")
            val inst = cls.getMethod("getInstance").invoke(null)
            cls.getMethod("deleteProfile", String::class.java).invoke(inst, NAME)
            used = false
        } catch (e: Throwable) {
        }
    }

    val isolatedSupported: Boolean get() = supported
}

/** Owns every tab and builds / configures their WebViews. */
object Tabs {
    val list = mutableStateListOf<BTab>()
    var activeId by mutableLongStateOf(-1L)

    private var nextId = 1L
    private val main = Handler(Looper.getMainLooper())
    private var baseUa = ""
    private var scrollAcc = 0
    private var lastToggle = 0L

    val active: BTab? get() = list.firstOrNull { it.id == activeId }

    private fun act(): MainActivity? = Bridge.activity

    private fun headers(): Map<String, String> =
        if (AppSettings.dnt) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

    private fun defaultUa(ctx: Context): String {
        if (baseUa.isEmpty()) {
            baseUa = WebSettings.getDefaultUserAgent(ctx)
                .replace("; wv", "")
                .replace(Regex(" Version/\\d+\\.\\d+"), "")
        }
        return baseUa
    }

    fun uaFor(ctx: Context, t: BTab): String {
        if (!t.desktop) return defaultUa(ctx)
        val v = Regex("Chrome/([\\d.]+)").find(defaultUa(ctx))?.groupValues?.get(1) ?: "120.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$v Safari/537.36"
    }

    // ---------------------------------------------------------------------------------------------
    // tab list
    // ---------------------------------------------------------------------------------------------

    fun newTab(url: String? = null, private: Boolean = false, select: Boolean = true, parent: BTab? = null): BTab {
        val t = BTab(nextId++, private)
        t.parent = parent
        t.desktop = AppSettings.desktopDefault
        list.add(t)
        if (select) {
            activeId = t.id
            Nav.chromeHidden = false
        }
        if (!url.isNullOrEmpty()) load(t, url)
        return t
    }

    fun select(t: BTab) {
        activeId = t.id
        Nav.chromeHidden = false
    }

    fun close(t: BTab) {
        val idx = list.indexOf(t)
        if (idx < 0) return
        val wasActive = t.id == activeId
        list.removeAt(idx)
        destroy(t)
        if (wasActive) {
            val next = t.parent?.takeIf { list.contains(it) } ?: list.getOrNull(minOf(idx, list.size - 1))
            if (next != null) activeId = next.id
        }
        if (list.isEmpty()) newTab(null, false)
        if (list.none { it.isPrivate }) PrivateProfile.deleteIfUsed()
        Nav.chromeHidden = false
    }

    fun closeAll(privateOnly: Boolean = false) {
        val victims = list.filter { !privateOnly || it.isPrivate }
        for (t in victims) {
            list.remove(t)
            destroy(t)
        }
        if (list.isEmpty()) newTab(null, false) else if (active == null) activeId = list.first().id
        if (list.none { it.isPrivate }) PrivateProfile.deleteIfUsed()
    }

    private fun destroy(t: BTab) {
        val w = t.web ?: return
        try {
            w.stopLoading()
            w.onScroll = null
            val c = t.container
            (c?.parent as? ViewGroup)?.removeView(c)
            c?.removeAllViews()
            w.destroy()
        } catch (e: Exception) {
        }
        t.web = null
        t.container = null
    }

    /** Frees all WebViews (activity going away). Tabs keep their URL and reload lazily. */
    fun releaseWebViews() {
        for (t in list) {
            if (t.web != null) {
                val u = t.url
                destroy(t)
                if (u.isNotEmpty() && u != "about:blank") t.pendingUrl = u
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // navigation
    // ---------------------------------------------------------------------------------------------

    fun load(t: BTab, input: String) {
        val u = SearchEngines.resolve(input)
        if (u.isEmpty()) return
        t.upgradedFrom = null
        t.errorShown = false
        t.pendingUrl = null
        t.url = u
        t.loading = true
        t.progress = 5
        val w = ensureWeb(t) ?: run {
            t.pendingUrl = u
            return
        }
        w.loadUrl(u, headers())
    }

    fun reload(t: BTab) {
        val w = t.web
        if (w == null) {
            if (t.url.isNotEmpty()) load(t, t.url)
        } else {
            w.reload()
        }
    }

    fun stop(t: BTab) {
        t.web?.stopLoading()
        t.loading = false
        t.progress = 100
    }

    fun forward(t: BTab) {
        t.web?.let { if (it.canGoForward()) it.goForward() }
    }

    /** @return true if the back press was consumed. */
    fun back(): Boolean {
        val t = active ?: return false
        val w = t.web
        if (w != null && w.canGoBack() && t.url.isNotEmpty()) {
            w.goBack()
            return true
        }
        val p = t.parent
        if (p != null && list.contains(p)) {
            close(t)
            select(p)
            return true
        }
        if (t.url.isNotEmpty()) {
            // no history left: show the start page again
            t.url = ""
            t.title = ""
            return true
        }
        return false
    }

    fun home(t: BTab) {
        val hp = AppSettings.homepage.trim()
        if (hp.isEmpty()) {
            t.url = ""
            t.title = ""
        } else {
            load(t, hp)
        }
    }

    private fun syncNav(t: BTab, w: WebView) {
        t.canBack = w.canGoBack()
        t.canForward = w.canGoForward()
    }

    // ---------------------------------------------------------------------------------------------
    // session
    // ---------------------------------------------------------------------------------------------

    fun restore(): Boolean {
        if (!AppSettings.restoreTabs) return false
        val s = AppSettings.session
        if (s.isBlank()) return false
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = BTab(nextId++, false)
                t.url = o.optString("u")
                t.title = o.optString("t")
                t.desktop = o.optBoolean("d")
                t.pendingUrl = t.url.ifEmpty { null }
                list.add(t)
            }
            if (list.isEmpty()) return false
            activeId = list[AppSettings.sessionActive.coerceIn(0, list.size - 1)].id
            return true
        } catch (e: Exception) {
            list.clear()
            return false
        }
    }

    fun saveSession() {
        val a = JSONArray()
        var activeIdx = 0
        var n = 0
        for (t in list) {
            if (t.isPrivate) continue
            if (t.id == activeId) activeIdx = n
            a.put(JSONObject().put("u", t.url).put("t", t.title).put("d", t.desktop))
            n++
        }
        AppSettings.session = a.toString()
        AppSettings.sessionActive = activeIdx
    }

    fun pauseAll() {
        for (t in list) t.web?.onPause()
    }

    fun resumeActive() {
        active?.web?.onResume()
    }

    // ---------------------------------------------------------------------------------------------
    // WebView creation + settings
    // ---------------------------------------------------------------------------------------------

    fun ensureWeb(t: BTab): HudWebView? {
        t.web?.let { return it }
        val act = act() ?: return null
        val w = HudWebView(act)
        if (t.isPrivate) t.isolated = PrivateProfile.apply(w)
        t.web = w

        val c = SwipeRefreshLayout(act)
        c.addView(w, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        c.setColorSchemeColors(Color.parseColor("#19E3FF"))
        c.setProgressBackgroundColorSchemeColor(Color.parseColor("#08141F"))
        c.setOnChildScrollUpCallback { _, _ -> w.scrollY > 0 }
        c.setOnRefreshListener {
            w.reload()
            main.postDelayed({ c.isRefreshing = false }, 800)
        }
        t.container = c

        configure(w, t)
        w.webViewClient = client(t)
        w.webChromeClient = chrome(t)
        w.setDownloadListener { url, ua, cd, mime, _ ->
            val name = URLUtil.guessFileName(url, cd, mime)
            val a = act()
            if (a != null) {
                if (AppSettings.confirmDownloads) Prompts.show(Prompt.Download(url, ua ?: "", cd, mime, name))
                else Downloader.enqueue(a, url, ua ?: "", mime, name, t.url)
            }
        }
        w.setFindListener { index, count, _ ->
            t.findIndex = index
            t.findCount = count
        }
        w.setOnLongClickListener {
            val r = w.hitTestResult
            val type = r.type
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                type == WebView.HitTestResult.IMAGE_TYPE
            ) {
                val extra = r.extra
                val msg = Handler(Looper.getMainLooper()) { m ->
                    val link = m.data.getString("url")
                    val src = m.data.getString("src")
                    val image = if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) null else (src ?: extra)
                    val linkUrl = link ?: if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) extra else null
                    if (linkUrl != null || image != null) Prompts.show(Prompt.Link(linkUrl, image, t.isPrivate))
                    true
                }.obtainMessage()
                w.requestFocusNodeHref(msg)
                true
            } else {
                false
            }
        }
        w.onScroll = { y, dy ->
            if (y <= 0) {
                if (t.id == activeId) Nav.chromeHidden = false
            } else if (AppSettings.hideBars && t.id == activeId) {
                val now = SystemClock.uptimeMillis()
                if (now - lastToggle > 350) {
                    scrollAcc = if ((dy > 0) == (scrollAcc > 0)) scrollAcc + dy else dy
                    if (scrollAcc > 60 && y > 200 && !Nav.chromeHidden) {
                        Nav.chromeHidden = true
                        lastToggle = now
                        scrollAcc = 0
                    } else if (scrollAcc < -40 && Nav.chromeHidden) {
                        Nav.chromeHidden = false
                        lastToggle = now
                        scrollAcc = 0
                    }
                }
            }
        }

        val p = t.pendingUrl
        if (p != null) {
            t.pendingUrl = null
            w.loadUrl(p, headers())
        }
        return w
    }

    fun applyAll() {
        for (t in list) {
            val w = t.web ?: continue
            configure(w, t)
        }
    }

    @Suppress("DEPRECATION")
    fun configure(w: WebView, t: BTab) {
        val s = w.settings
        s.javaScriptEnabled = AppSettings.javascript
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = !AppSettings.blockPopups
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(AppSettings.pinchZoom)
        s.builtInZoomControls = AppSettings.pinchZoom
        s.displayZoomControls = false
        s.textZoom = AppSettings.textZoom
        s.mediaPlaybackRequiresUserGesture = true
        s.allowFileAccess = false
        s.allowContentAccess = true
        s.blockNetworkImage = AppSettings.blockImages
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.cacheMode = if (t.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        s.saveFormData = !t.isPrivate
        s.userAgentString = uaFor(w.context, t)
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(AppSettings.safeBrowsing)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, AppSettings.darkWeb)
            }
        } catch (e: Throwable) {
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(AppSettings.cookies)
        try {
            cm.setAcceptThirdPartyCookies(w, !AppSettings.blockThirdCookies)
        } catch (e: Exception) {
        }
    }

    // ---------------------------------------------------------------------------------------------
    // clients
    // ---------------------------------------------------------------------------------------------

    private fun isLocal(host: String?): Boolean {
        if (host == null) return false
        return host == "localhost" || host.endsWith(".local") ||
            Regex("^(10\\.|192\\.168\\.|127\\.|172\\.(1[6-9]|2\\d|3[01])\\.)").containsMatchIn(host)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun errorHtml(url: String, desc: String): String =
        """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
body{background:#040A12;color:#D6F8FF;font-family:monospace;padding:24px}
h1{color:#19E3FF;font-size:20px}.b{border:1px solid #19E3FF66;padding:14px;margin-top:16px;word-break:break-all}
a{color:#FFB000;font-size:16px}</style></head><body>
<h1>◈ CONNECTION FAILED</h1>
<div class="b">${esc(desc)}<br><br>${esc(url)}</div>
<p><a href="${esc(url)}">▸ RETRY</a></p></body></html>"""

    private fun mainFrameError(t: BTab, view: WebView, url: String, desc: String) {
        val up = t.upgradedFrom
        if (up != null && url.startsWith("https://") && url.removePrefix("https://") == up.removePrefix("http://")) {
            t.upgradedFrom = null
            view.loadUrl(up, headers())     // site has no https: fall back to the original http address
            return
        }
        t.errorShown = true
        view.loadDataWithBaseURL(url, errorHtml(url, desc), "text/html", "utf-8", url)
    }

    private fun external(t: BTab, url: String, gesture: Boolean) {
        if (!gesture) return
        val a = act() ?: return
        try {
            val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            val label = (intent.data?.scheme ?: "APP").uppercase() + "  ·  " + url.take(60)
            Prompts.show(Prompt.ExternalApp(label) {
                try {
                    a.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    val fb = intent.getStringExtra("browser_fallback_url")
                    if (fb != null) load(t, fb) else toast(a, "NO APP FOUND")
                }
            })
        } catch (e: Exception) {
        }
    }

    private fun client(t: BTab) = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val u = request.url
            val scheme = (u.scheme ?: "").lowercase()
            if (scheme == "http" || scheme == "https") {
                if (request.isForMainFrame && scheme == "http" && AppSettings.httpsOnly && !isLocal(u.host)) {
                    t.upgradedFrom = u.toString()
                    view.loadUrl("https://" + u.toString().removePrefix("http://"), headers())
                    return true
                }
                return false
            }
            if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript" || scheme == "file") return false
            external(t, u.toString(), if (Build.VERSION.SDK_INT >= 24) request.hasGesture() else true)
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (AppSettings.adBlock && !request.isForMainFrame) {
                val host = request.url.host
                if (host != null && AdBlock.isBlocked(host) && !SiteStore.isAllowed(hostOf(t.url))) {
                    main.post { t.blocked++ }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            t.loading = true
            if (t.progress >= 100) t.progress = 5
            if (url != null && url != "about:blank") {
                if (hostOf(url) != hostOf(t.url)) {
                    t.blocked = 0
                    t.favicon = null
                }
                t.url = url
            }
            if (t.id == activeId) Nav.chromeHidden = false
            syncNav(t, view)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            t.loading = false
            t.progress = 100
            syncNav(t, view)
            if (url != null && url != "about:blank") t.url = url
            val title = view.title ?: ""
            if (title.isNotBlank() && !t.errorShown) t.title = title
            if (!t.isPrivate && !t.errorShown && url != null) HistoryStore.add(url, title)
            t.errorShown = false
            if (AppSettings.adBlock && !SiteStore.isAllowed(hostOf(t.url))) view.evaluateJavascript(AdBlock.COSMETIC_JS, null)
            if (AppSettings.darkWeb) {
                val native = try {
                    WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
                } catch (e: Throwable) {
                    false
                }
                if (!native) view.evaluateJavascript(AdBlock.DARK_JS, null)
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (url != null && url != "about:blank" && !url.startsWith("data:")) t.url = url
            syncNav(t, view)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val desc = if (Build.VERSION.SDK_INT >= 23) error.description.toString() else "ERROR"
            mainFrameError(t, view, request.url.toString(), desc)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
            if (Build.VERSION.SDK_INT >= 23) return
            if (failingUrl != null && failingUrl == t.url) mainFrameError(t, view, failingUrl, description ?: "ERROR")
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val u = error.url ?: ""
            if (u == t.url || u == view.url || t.url.isEmpty()) {
                val what = when (error.primaryError) {
                    SslError.SSL_UNTRUSTED -> "THE CERTIFICATE IS NOT TRUSTED"
                    SslError.SSL_EXPIRED -> "THE CERTIFICATE HAS EXPIRED"
                    SslError.SSL_IDMISMATCH -> "THE CERTIFICATE IS FOR A DIFFERENT SITE"
                    SslError.SSL_NOTYETVALID -> "THE CERTIFICATE IS NOT VALID YET"
                    else -> "THE CERTIFICATE IS INVALID"
                }
                Prompts.show(Prompt.Ssl(handler, u, what))
            } else {
                handler.cancel()
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String?, realm: String?) {
            Prompts.show(Prompt.Auth(handler, host ?: "", realm ?: ""))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            main.post {
                val u = t.url
                destroy(t)
                t.pendingUrl = u.ifEmpty { null }
                t.viewGen++
                if (t.id == activeId) ensureWeb(t)
            }
            return true
        }
    }

    private fun chrome(t: BTab) = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            t.progress = newProgress
            if (newProgress >= 100) t.loading = false
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) t.title = title
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            t.favicon = icon
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
            if (!isUserGesture && AppSettings.blockPopups) return false
            val nt = newTab(null, t.isPrivate, select = true, parent = t)
            nt.url = "about:blank"
            val w = ensureWeb(nt) ?: return false
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = w
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            main.post { close(t) }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val res = request.resources
            val origin = request.origin.toString()
            val need = ArrayList<String>()
            val labels = ArrayList<String>()
            val ok = ArrayList<String>()
            for (r in res) {
                when (r) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                        need.add(Manifest.permission.CAMERA); labels.add("CAMERA"); ok.add(r)
                    }
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                        need.add(Manifest.permission.RECORD_AUDIO); labels.add("MICROPHONE"); ok.add(r)
                    }
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                        labels.add("PROTECTED MEDIA"); ok.add(r)
                    }
                }
            }
            if (ok.isEmpty()) {
                request.deny()
                return
            }
            Prompts.show(Prompt.Perm(origin, labels.joinToString(" + ")) { allow ->
                if (!allow) {
                    request.deny()
                } else {
                    Bridge.requestPerms(need) { granted ->
                        if (granted) request.grant(ok.toTypedArray()) else request.deny()
                    }
                }
            })
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            if (origin == null || callback == null) return
            Prompts.show(Prompt.Perm(origin, "LOCATION") { allow ->
                if (!allow) {
                    callback.invoke(origin, false, false)
                } else {
                    Bridge.requestPerms(listOf(Manifest.permission.ACCESS_FINE_LOCATION)) { g ->
                        callback.invoke(origin, g, false)
                    }
                }
            })
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams
        ): Boolean {
            Bridge.fileCb?.onReceiveValue(null)
            val a = act()
            if (a == null) {
                filePathCallback.onReceiveValue(null)
                return false
            }
            Bridge.fileCb = filePathCallback
            return try {
                a.launchFile(fileChooserParams.createIntent())
                true
            } catch (e: Exception) {
                Bridge.fileCb = null
                filePathCallback.onReceiveValue(null)
                false
            }
        }

        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
            val a = act()
            if (a == null) callback.onCustomViewHidden() else a.showCustomView(view, callback)
        }

        override fun onHideCustomView() {
            act()?.hideCustomView()
        }

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            Prompts.show(Prompt.Js(0, prettyHost(url), message, "", result))
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            Prompts.show(Prompt.Js(1, prettyHost(url), message, "", result))
            return true
        }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
            Prompts.show(Prompt.Js(2, prettyHost(url), message, defaultValue ?: "", result))
            return true
        }
    }
}
