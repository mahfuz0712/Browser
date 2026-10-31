package com.mux.android.browser.engine

import android.webkit.HttpAuthHandler
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Things a page asks the user (shown as HUD dialogs by the UI). */
sealed class Prompt {
    class Js(val kind: Int, val origin: String, val message: String, val default: String, val result: JsResult) : Prompt()
    class Ssl(val handler: SslErrorHandler, val url: String, val error: String) : Prompt()
    class Auth(val handler: HttpAuthHandler, val host: String, val realm: String) : Prompt()
    class Perm(val origin: String, val what: String, val decide: (Boolean) -> Unit) : Prompt()
    class Download(val url: String, val ua: String, val cd: String?, val mime: String?, val name: String) : Prompt()
    class Link(val link: String?, val image: String?, val priv: Boolean) : Prompt()
    class ExternalApp(val label: String, val open: () -> Unit) : Prompt()
}

object Prompts {
    var current by mutableStateOf<Prompt?>(null)
        private set
    private val queue = ArrayDeque<Prompt>()

    fun show(p: Prompt) {
        if (current == null) current = p else queue.addLast(p)
    }

    fun done() {
        current = queue.removeFirstOrNull()
    }
}
