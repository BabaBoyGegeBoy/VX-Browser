package com.vx.browser.browser

import android.content.Context
import android.webkit.WebView

class TabManager(private val context: Context) {
    private val tabs = mutableListOf<Tab>()
    var currentIndex = -1
        private set

    fun newTab(): Tab {
        val wv = WebView(context)
        WebViewSetup.configure(wv)
        val tab = Tab(id = tabs.size + 1, webView = wv)
        tabs.add(tab)
        currentIndex = tabs.lastIndex
        return tab
    }

    fun getCurrent(): Tab? =
        if (currentIndex in tabs.indices) tabs[currentIndex] else null

    fun list(): List<Tab> = tabs.toList()

    fun select(index: Int) {
        if (index in tabs.indices) currentIndex = index
    }

    fun close(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        tab.webView.destroy()
        currentIndex = if (tabs.isEmpty()) -1 else tabs.lastIndex.coerceAtMost(currentIndex)
    }
}
