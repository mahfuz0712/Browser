package com.mux.android.browser.engine

import android.Manifest
import android.net.Uri
import android.webkit.ValueCallback
import com.mux.android.browser.MainActivity
import com.mux.android.browser.util.hasPerm

/** Glue between WebView callbacks and the Activity (permission dialogs, file picker, fullscreen video). */
object Bridge {
    var activity: MainActivity? = null
    var permCb: ((Map<String, Boolean>) -> Unit)? = null
    var fileCb: ValueCallback<Array<Uri>>? = null

    /** Asks for the runtime permissions that are not granted yet. [done] gets true when all are granted. */
    fun requestPerms(perms: List<String>, done: (Boolean) -> Unit) {
        val act = activity
        if (act == null) {
            done(false)
            return
        }
        val need = perms.filter { !hasPerm(act, it) }
        if (need.isEmpty()) {
            done(true)
            return
        }
        permCb = { res -> done(need.all { res[it] == true }) }
        try {
            act.launchPerms(need.toTypedArray())
        } catch (e: Exception) {
            permCb = null
            done(false)
        }
    }

    fun storagePermsForDownload(): List<String> =
        if (android.os.Build.VERSION.SDK_INT <= 28) listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) else emptyList()
}
