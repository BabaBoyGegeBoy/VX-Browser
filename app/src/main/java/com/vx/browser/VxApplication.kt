package com.vx.browser

import android.app.Application
import com.vx.browser.browser.AdBlock
import com.vx.browser.browser.MediaSniffer
import com.vx.browser.data.AppDatabase
import com.vx.browser.util.Prefs

class VxApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        AdBlock.load(this)
        AdBlock.enabled = Prefs.isAdBlockEnabled(this)
        MediaSniffer.enabled = Prefs.isSnifferEnabled(this)
    }
}
