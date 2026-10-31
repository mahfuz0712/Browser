package com.mux.android.browser.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mux.android.browser.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.0.0"
} catch (e: Exception) {
    "0.0.0"
}

/** Works on every Android version from 5.0 (activeNetworkInfo is deprecated but universal). */
@Suppress("DEPRECATION")
fun isOnline(ctx: Context): Boolean = try {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.activeNetworkInfo?.isConnected == true
} catch (e: Exception) {
    false
}

private fun parts(v: String): List<Int> =
    v.trim().removePrefix("v").removePrefix("V").split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

fun isNewer(remote: String, local: String): Boolean {
    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Returns (requestWorked, newerReleaseOrNull). Reads the latest GitHub release of the repo. */
suspend fun checkUpdate(ctx: Context): Pair<Boolean, UpdateInfo?> = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.github.com/repos/${AppInfo.GITHUB_USER}/${AppInfo.REPO}/releases/latest"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) return@withContext false to null
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val tag = j.optString("tag_name")
        if (tag.isEmpty() || !isNewer(tag, appVersion(ctx))) return@withContext true to null
        var apk = j.optString("html_url")
        val assets = j.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) {
                    apk = a.optString("browser_download_url")
                    break
                }
            }
        }
        true to UpdateInfo(tag.removePrefix("v"), apk, j.optString("body").take(300))
    } catch (e: Exception) {
        false to null
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

@Composable
fun UpdateDialog(u: UpdateInfo, current: String, onUpdate: () -> Unit, onCancel: () -> Unit) {
    val c = Hud.colors
    Dialog(onDismissRequest = onCancel) {
        Box(Modifier.fillMaxWidth().background(c.bg, HudShape(12.dp))) {
            HudPanel(Modifier.fillMaxWidth(), "SYSTEM UPDATE DETECTED") {
                HudText("UPDATE AVAILABLE", color = c.primary, size = 18.sp, bold = true)
                Spacer(Modifier.height(10.dp))
                HudText("v$current  >>  v${u.version}", color = c.text, size = 14.sp)
                if (u.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    HudText(u.notes, color = c.dim, size = 11.sp, maxLines = 6)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp).height(46.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HudButton("CANCEL", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 13.sp) { onCancel() }
                    HudButton("UPDATE", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, fontSize = 13.sp) { onUpdate() }
                }
            }
        }
    }
}
