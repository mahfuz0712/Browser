package com.mux.android.browser.ui

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.browser.AppInfo
import com.mux.android.browser.data.AppSettings
import com.mux.android.browser.data.Nav
import com.mux.android.browser.data.Page
import com.mux.android.browser.engine.PrivateProfile
import com.mux.android.browser.engine.SearchEngines
import com.mux.android.browser.util.toast
import kotlinx.coroutines.launch

/** Settings + about, as a full-screen HUD page. */
@Composable
fun SystemScreen(tablet: Boolean, onUpdateFound: (UpdateInfo) -> Unit) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { link -> runCatching { uri.openUri(link) } }
    Overlay("SYSTEM", { Nav.page = Page.NONE }) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if (tablet) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        AppPanel(); Gap(); DefaultBrowserPanel(); Gap(); GeneralPanel(); Gap(); DisplayPanel(); Gap(); UpdatePanel(onUpdateFound)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        PrivacyPanel(); Gap(); DownloadPanel(); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
                    }
                }
            } else {
                AppPanel(); Gap(); DefaultBrowserPanel(); Gap(); GeneralPanel(); Gap(); PrivacyPanel(); Gap()
                DisplayPanel(); Gap(); DownloadPanel(); Gap(); UpdatePanel(onUpdateFound); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Gap() = Spacer(Modifier.height(8.dp))

@Composable
private fun SettingRow(label: String, hint: String?, value: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HudText(label, color = Hud.colors.text, size = 12.sp)
            if (hint != null) HudText(hint, color = Hud.colors.dim, size = 9.sp)
        }
        HudButton(value, Modifier.width(118.dp).height(38.dp), kind, fontSize = 11.sp, onClick = onClick)
    }
}

@Composable
private fun ToggleRow(label: String, hint: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label, hint, if (value) "ON" else "OFF", if (value) KeyKind.GOOD else KeyKind.FN) { onChange(!value) }
}

@Composable
private fun CycleRow(label: String, hint: String? = null, options: List<Pair<String, String>>, current: String, onChange: (String) -> Unit) {
    val i = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
    SettingRow(label, hint, options[i].second) { onChange(options[(i + 1) % options.size].first) }
}

@Composable
private fun AppPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "SYSTEM") {
        HudText("◈ ${AppInfo.NAME.uppercase()}", color = c.primary, size = 28.sp, bold = true)
        HudText("VERSION ${appVersion(LocalContext.current)}", color = c.accent, size = 12.sp)
        Spacer(Modifier.height(8.dp))
        HudText(
            "Tabs, private tabs, ad & tracker blocking, reader mode, downloads, bookmarks and history in a sci-fi HUD interface. Built on the Android System WebView (Chromium).",
            color = c.text, size = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        SettingRow("COLOR THEME", null, if (AppSettings.amber) "AMBER" else "CYAN", KeyKind.ACCENT) { AppSettings.amber = !AppSettings.amber }
    }
}

private fun isDefaultBrowser(ctx: android.content.Context): Boolean = try {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
    ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
} catch (e: Exception) {
    false
}

@Composable
private fun DefaultBrowserPanel() {
    val c = Hud.colors
    val ctx = LocalContext.current
    var bump by remember { mutableStateOf(0) }
    val isDefault = remember(bump, Nav.resumeTick) { isDefaultBrowser(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { bump++ }
    HudPanel(Modifier.fillMaxWidth(), "DEFAULT BROWSER") {
        HudText(
            if (isDefault) "● BROWSER IS YOUR DEFAULT BROWSER" else "○ NOT THE DEFAULT BROWSER",
            color = if (isDefault) c.good else c.accent, size = 13.sp, bold = true
        )
        Spacer(Modifier.height(4.dp))
        HudText("Links from other apps open here when this is the default browser.", color = c.text, size = 12.sp)
        if (!isDefault) {
            Spacer(Modifier.height(8.dp))
            HudButton("SET AS DEFAULT", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 13.sp) {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val rm = ctx.getSystemService(RoleManager::class.java)
                        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                            launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                        } else {
                            launcher.launch(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        }
                    } else if (Build.VERSION.SDK_INT >= 24) {
                        launcher.launch(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    } else {
                        launcher.launch(Intent(AndroidSettings.ACTION_SETTINGS))
                    }
                } catch (e: Exception) {
                    toast(ctx, "OPEN ANDROID SETTINGS > APPS > DEFAULT APPS")
                }
            }
        }
    }
}

@Composable
private fun GeneralPanel() {
    HudPanel(Modifier.fillMaxWidth(), "GENERAL") {
        HudText("HOMEPAGE (EMPTY = SPEED DIAL START PAGE)", color = Hud.colors.dim, size = 10.sp)
        HudField(AppSettings.homepage, "https://", Modifier.fillMaxWidth().height(44.dp)) { AppSettings.homepage = it.trim() }
        Spacer(Modifier.height(6.dp))
        CycleRow("SEARCH ENGINE", null, SearchEngines.all.map { it.key to it.name }, AppSettings.engine) { AppSettings.engine = it }
        ToggleRow("SEARCH SUGGESTIONS", "SENDS WHAT YOU TYPE TO THE SEARCH ENGINE", AppSettings.suggestions) { AppSettings.suggestions = it }
        ToggleRow("RESTORE TABS ON START", null, AppSettings.restoreTabs) { AppSettings.restoreTabs = it }
        ToggleRow("HIDE TOOLBARS WHEN SCROLLING", null, AppSettings.hideBars) { AppSettings.hideBars = it }
    }
}

@Composable
private fun PrivacyPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "PRIVACY & SECURITY") {
        ToggleRow("AD & TRACKER BLOCKER", "BUILT-IN HOST LIST + HIDES AD BOXES", AppSettings.adBlock) { AppSettings.adBlock = it }
        ToggleRow("HTTPS-ONLY MODE", "UPGRADES http:// TO https:// WHEN POSSIBLE", AppSettings.httpsOnly) { AppSettings.httpsOnly = it }
        ToggleRow("BLOCK POP-UPS", null, AppSettings.blockPopups) { AppSettings.blockPopups = it }
        ToggleRow("SEND \"DO NOT TRACK\"", "DNT + GPC HEADERS ON PAGES YOU OPEN", AppSettings.dnt) { AppSettings.dnt = it }
        ToggleRow("COOKIES", null, AppSettings.cookies) { AppSettings.cookies = it }
        ToggleRow("BLOCK THIRD-PARTY COOKIES", null, AppSettings.blockThirdCookies) { AppSettings.blockThirdCookies = it }
        ToggleRow("JAVASCRIPT", "TURNING IT OFF BREAKS MANY SITES", AppSettings.javascript) { AppSettings.javascript = it }
        ToggleRow("SAFE BROWSING", "WARNS ABOUT DANGEROUS SITES (ANDROID 8+)", AppSettings.safeBrowsing) { AppSettings.safeBrowsing = it }
        ToggleRow("CLEAR DATA ON EXIT", "HISTORY, COOKIES AND CACHE", AppSettings.clearOnExit) { AppSettings.clearOnExit = it }
        Spacer(Modifier.height(6.dp))
        HudButton("CLEAR BROWSING DATA…", Modifier.fillMaxWidth().height(44.dp), KeyKind.DANGER, fontSize = 12.sp) { Nav.clearData = true }
        Spacer(Modifier.height(6.dp))
        HudText(
            if (PrivateProfile.isolatedSupported) "PRIVATE TABS: SEPARATE COOKIE JAR (SUPPORTED BY YOUR WEBVIEW)"
            else "PRIVATE TABS: NO HISTORY / CACHE ONLY. UPDATE ANDROID SYSTEM WEBVIEW FOR SEPARATE COOKIES.",
            color = c.dim, size = 9.sp
        )
    }
}

@Composable
private fun DisplayPanel() {
    HudPanel(Modifier.fillMaxWidth(), "DISPLAY") {
        CycleRow(
            "PAGE TEXT SIZE", null,
            listOf(80, 90, 100, 110, 125, 150, 175, 200).map { it.toString() to "$it%" },
            AppSettings.textZoom.toString()
        ) { AppSettings.textZoom = it.toInt() }
        ToggleRow("DARK PAGES", "FORCES DARK MODE ON WEBSITES", AppSettings.darkWeb) { AppSettings.darkWeb = it }
        ToggleRow("DESKTOP SITE BY DEFAULT", "FOR NEW TABS", AppSettings.desktopDefault) { AppSettings.desktopDefault = it }
        ToggleRow("PINCH TO ZOOM", null, AppSettings.pinchZoom) { AppSettings.pinchZoom = it }
        ToggleRow("DATA SAVER (NO IMAGES)", null, AppSettings.blockImages) { AppSettings.blockImages = it }
        CycleRow(
            "READER MODE TEXT", null,
            listOf(15, 17, 18, 20, 22, 26).map { it.toString() to "$it PX" },
            AppSettings.readerSize.toString()
        ) { AppSettings.readerSize = it.toInt() }
    }
}

@Composable
private fun DownloadPanel() {
    HudPanel(Modifier.fillMaxWidth(), "DOWNLOADS") {
        ToggleRow("ASK BEFORE DOWNLOADING", null, AppSettings.confirmDownloads) { AppSettings.confirmDownloads = it }
        HudText("FILES GO TO THE ANDROID DOWNLOADS FOLDER.", color = Hud.colors.dim, size = 9.sp)
    }
}

@Composable
private fun UpdatePanel(onUpdateFound: (UpdateInfo) -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    HudPanel(Modifier.fillMaxWidth(), "UPDATES") {
        HudButton("CHECK FOR UPDATES", Modifier.fillMaxWidth().height(46.dp), KeyKind.OP, fontSize = 13.sp) {
            scope.launch {
                status = "CHECKING..."
                if (!isOnline(ctx)) {
                    status = "NO INTERNET CONNECTION"
                    return@launch
                }
                val (ok, info) = checkUpdate(ctx)
                status = when {
                    !ok -> "CHECK FAILED"
                    info == null -> "UP TO DATE"
                    else -> "NEW VERSION AVAILABLE: v${info.version}"
                }
                if (info != null) onUpdateFound(info)
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HudText(status, color = c.dim, size = 12.sp)
        }
    }
}

@Composable
private fun AuthorPanel(open: (String) -> Unit) {
    HudPanel(Modifier.fillMaxWidth(), "AUTHOR") {
        LinkRow("NAME", AppInfo.AUTHOR, null, open)
        LinkRow("EMAIL", AppInfo.EMAIL, "mailto:${AppInfo.EMAIL}", open)
        LinkRow("GITHUB", "github.com/mahfuz0712", AppInfo.GITHUB, open)
        LinkRow("PORTFOLIO", "portfolio-mahfuz0712.vercel.app", AppInfo.PORTFOLIO, open)
    }
}

@Composable
private fun MoreAppsPanel(open: (String) -> Unit) {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "MORE APPS") {
        HudText("Discover more apps by the developer on zApps Store.", color = c.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        HudButton("OPEN zApps STORE ▸", Modifier.fillMaxWidth().height(48.dp), KeyKind.ACCENT, fontSize = 14.sp) { open(AppInfo.MORE_APPS) }
    }
}

@Composable
private fun LinkRow(label: String, value: String, link: String?, open: (String) -> Unit) {
    val c = Hud.colors
    Column(
        Modifier.fillMaxWidth().then(if (link != null) Modifier.clickable { open(link) } else Modifier).padding(vertical = 6.dp)
    ) {
        HudText(label, color = c.dim, size = 10.sp)
        HudText(value, color = if (link != null) c.primary else c.text, size = 16.sp)
    }
}
