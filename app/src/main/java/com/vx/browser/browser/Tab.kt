package com.vx.browser.browser

import android.webkit.WebView

data class Tab(
    val id: Int,
    var title: String = "",
    var url: String = "",
    val webView: WebView
)
