package com.mux.android.browser

import android.app.Application
import com.mux.android.browser.data.AppSettings
import com.mux.android.browser.data.Store

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Store.init(this)
    }
}
