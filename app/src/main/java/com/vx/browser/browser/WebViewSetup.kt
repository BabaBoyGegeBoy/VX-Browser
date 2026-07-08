package com.vx.browser.browser

import android.webkit.WebSettings
import android.webkit.WebView
import com.vx.browser.util.Prefs

object WebViewSetup {
    fun configure(wv: WebView) {
        val s = wv.settings
        s.javaScriptEnabled = Prefs.isJavaScriptEnabled(wv.context)
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.setSupportZoom(true)
        wv.fitsSystemWindows = true
    }
}
