package com.mux.android.browser

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mux.android.browser.data.AppSettings
import com.mux.android.browser.data.Nav
import com.mux.android.browser.data.Page
import com.mux.android.browser.data.Store
import com.mux.android.browser.engine.Actions
import com.mux.android.browser.engine.Bridge
import com.mux.android.browser.engine.Tabs
import com.mux.android.browser.ui.BrowserRoot

class MainActivity : ComponentActivity() {
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val cb = Bridge.permCb
        Bridge.permCb = null
        cb?.invoke(res)
    }

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = Bridge.fileCb
        Bridge.fileCb = null
        cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
    }

    fun launchPerms(p: Array<String>) = permLauncher.launch(p)
    fun launchFile(i: Intent) = fileLauncher.launch(i)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.activity = this
        AppSettings.init(this)
        Store.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        if (Tabs.list.isEmpty()) {
            if (!Tabs.restore()) {
                val hp = AppSettings.homepage.trim()
                Tabs.newTab(if (hp.isEmpty()) null else hp)
            }
        }
        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    Nav.menu -> Nav.menu = false
                    Nav.siteInfo -> Nav.siteInfo = false
                    Nav.clearData -> Nav.clearData = false
                    Nav.page != Page.NONE -> Nav.page = Page.NONE
                    Nav.omniFocused -> Nav.blurTick++
                    Nav.find -> {
                        Nav.find = false
                        Tabs.active?.web?.clearMatches()
                    }
                    else -> if (!Tabs.back()) moveTaskToBack(true)
                }
            }
        })

        setContent { BrowserRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Links from other apps, "Share > Browser" and web-search intents. */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val text: String? = when (i.action) {
            Intent.ACTION_VIEW -> i.data?.toString()
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { s ->
                Regex("https?://\\S+").find(s)?.value ?: s
            }
            Intent.ACTION_WEB_SEARCH -> i.getStringExtra("query")
            else -> null
        }
        if (text.isNullOrBlank()) return
        i.action = null           // handle it only once
        Nav.page = Page.NONE
        Nav.menu = false
        val cur = Tabs.active
        if (cur != null && cur.url.isEmpty() && !cur.isPrivate) Tabs.load(cur, text) else Tabs.newTab(text)
    }

    override fun onPause() {
        super.onPause()
        Tabs.saveSession()
        Tabs.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        Tabs.resumeActive()
        Nav.resumeTick++
    }

    override fun onDestroy() {
        if (isFinishing) {
            if (AppSettings.clearOnExit) Actions.clearOnExit(this)
            Tabs.closeAll(privateOnly = true)
        }
        hideCustomView()
        Tabs.releaseWebViews()
        if (Bridge.activity === this) Bridge.activity = null
        super.onDestroy()
    }

    // ---- fullscreen video -------------------------------------------------------------------

    fun showCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            cb.onCustomViewHidden()
            return
        }
        customView = v
        customCb = cb
        (window.decorView as FrameLayout).addView(
            v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun hideCustomView() {
        val v = customView ?: return
        (v.parent as? ViewGroup)?.removeView(v)
        customView = null
        try {
            customCb?.onCustomViewHidden()
        } catch (e: Exception) {
        }
        customCb = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }
}
