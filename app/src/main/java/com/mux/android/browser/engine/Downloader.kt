package com.mux.android.browser.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import com.mux.android.browser.data.Dl
import com.mux.android.browser.data.DownloadStore
import com.mux.android.browser.util.prettyHost
import com.mux.android.browser.util.toast

object Downloader {
    fun enqueue(ctx: Context, url: String, ua: String, mime: String?, name: String, referer: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            toast(ctx, "THIS KIND OF DOWNLOAD IS NOT SUPPORTED")
            return
        }
        Bridge.requestPerms(Bridge.storagePermsForDownload()) { ok ->
            if (!ok) {
                toast(ctx, "STORAGE PERMISSION REQUIRED")
                return@requestPerms
            }
            try {
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle(name)
                    .setDescription(prettyHost(url))
                    .setMimeType(if (mime.isNullOrBlank()) "application/octet-stream" else mime)
                    .addRequestHeader("User-Agent", ua)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                CookieManager.getInstance().getCookie(url)?.let { if (it.isNotEmpty()) req.addRequestHeader("Cookie", it) }
                if (!referer.isNullOrEmpty()) req.addRequestHeader("Referer", referer)
                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val id = dm.enqueue(req)
                DownloadStore.add(Dl(id, name, url, System.currentTimeMillis()))
                toast(ctx, "DOWNLOAD STARTED")
            } catch (e: Exception) {
                toast(ctx, "DOWNLOAD FAILED")
            }
        }
    }
}
