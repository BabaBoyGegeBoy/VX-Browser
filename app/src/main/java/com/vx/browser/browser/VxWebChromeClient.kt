package com.vx.browser.browser

import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.vx.browser.util.Prefs

class VxWebChromeClient(
    private val onProgress: (Int) -> Unit
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgress(newProgress)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        val ctx = view?.context ?: return false
        if (!Prefs.isBlockPopup(ctx)) return false
        // 阻止弹窗：回收消息，不创建新窗口
        resultMsg.recycle()
        return true
    }
}
