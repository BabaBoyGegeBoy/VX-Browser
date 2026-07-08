package com.vx.browser.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vx.browser.data.repository.ElementRuleRepository
import com.vx.browser.data.repository.HistoryRepository
import com.vx.browser.util.Prefs
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.regex.Pattern

class VxWebViewClient(
    private val onTitle: (String) -> Unit,
    private val onUrl: (String) -> Unit,
    private val historyRepo: HistoryRepository,
    private val elementRepo: ElementRuleRepository? = null,
    private val onCustomScheme: (String) -> Unit
) : WebViewClient() {

    var lastTouchTime = 0L

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // 导航一开始就更新地址栏，避免 A→B 要等 B 加载完才显示
        if (!url.isNullOrEmpty()) onUrl(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val finalUrl = url ?: return
        view?.apply {
            settings.javaScriptCanOpenWindowsAutomatically = !Prefs.isBlockPopup(context)
            setOnTouchListener { _, _ -> lastTouchTime = System.currentTimeMillis(); false }
        }
        val title = view?.title ?: ""
        onTitle(title)
        onUrl(finalUrl)
        if (!finalUrl.startsWith("vx://")) {
            CoroutineScope(Dispatchers.IO).launch { historyRepo.add(title, finalUrl) }
        }
        if (finalUrl.startsWith("http")) {
            val host = Uri.parse(finalUrl).host
            val css = AdBlock.elementCssFor(host)
            if (css.isNotEmpty()) {
                val js = "(function(){" +
                        "var id='vx_adblock_style';" +
                        "var s=document.getElementById(id);" +
                        "if(!s){s=document.createElement('style');s.id=id;document.documentElement.appendChild(s);}" +
                        "s.textContent=" + JSONObject.quote(css) + ";" +
                        "})();"
                view?.evaluateJavascript(js, null)
            }
            injectElementRules(view, host)
            view?.evaluateJavascript(MEDIA_HOOK_JS, null)
        }
    }

    private fun injectElementRules(view: WebView?, host: String?) {
        val repo = elementRepo ?: return
        if (host == null) return
        val wv = view ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val rules = runCatching { repo.getByHost(host) }.getOrDefault(emptyList())
            if (rules.isEmpty()) return@launch
            val hideSels = rules.filter { it.action == "hide" }.map { it.selector }
            val delSels = rules.filter { it.action == "delete" }.map { it.selector }
            val sb = StringBuilder()
            if (hideSels.isNotEmpty()) {
                sb.append(hideSels.joinToString(",")).append("{display:none!important}")
            }
            val hideCss = sb.toString()
            val delJs = if (delSels.isNotEmpty()) {
                "document.querySelectorAll(" + JSONObject.quote(delSels.joinToString(",")) +
                        ").forEach(function(n){if(n.parentNode)n.parentNode.removeChild(n);});"
            } else ""
            val js = "(function(){" +
                    (if (hideCss.isNotEmpty()) {
                        "var s=document.getElementById('vx_elem_hide');" +
                                "if(!s){s=document.createElement('style');s.id='vx_elem_hide';document.documentElement.appendChild(s);}" +
                                "s.textContent=" + JSONObject.quote(hideCss) + ";"
                    } else "") +
                    delJs +
                    "})();"
            // WebView 方法必须在主线程调用
            wv.post { wv.evaluateJavascript(js, null) }
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val url = uri.toString()
        if (uri.scheme == "vx") {
            onCustomScheme(url)
            return true
        }
        val ctx = view?.context ?: return false
        val targetHost = uri.host
        val curHost = Uri.parse(view.url ?: "").host
        // 单页“跳转确认”：当前页已开启且目标是异站跳转 → 弹窗确认后才加载
        if (Prefs.isJumpConfirm(ctx, curHost) && targetHost != null && targetHost != curHost) {
            showJumpConfirm(view, url)
            return true
        }
        // 阻止自动跳转：非用户手势触发的跳转直接拦截
        if (Prefs.isBlockAutoRedirect(ctx) && !isUserInitiated()) {
            return true
        }
        return false
    }

    private fun isUserInitiated(): Boolean = System.currentTimeMillis() - lastTouchTime < 1000

    private fun showJumpConfirm(view: WebView, url: String) {
        android.app.AlertDialog.Builder(view.context)
            .setTitle("跳转确认")
            .setMessage("即将跳转到：\n$url")
            .setPositiveButton("继续") { _, _ -> view.loadUrl(url) }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        return intercept(request?.url?.toString() ?: return null)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        return intercept(url ?: return null)
    }

    private fun intercept(url: String): WebResourceResponse? {
        if (AdBlock.isBlocked(url)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        if (isSkipped(url)) return null
        if (MediaSniffer.maybeMedia(url)) {
            MediaSniffer.add(url)
            if (url.lowercase().contains(".m3u8")) probeM3u8(url)
            return null
        }
        if (MediaSniffer.isCandidate(url)) {
            probeAsync(url)
        }
        return null
    }

    private fun isSkipped(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return true
        return SKIP_PATTERN.matcher(lower).find()
    }

    private fun probeAsync(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            when (MediaProbe.probe(url)) {
                MediaProbe.MediaType.VIDEO, MediaProbe.MediaType.AUDIO -> MediaSniffer.add(url)
                MediaProbe.MediaType.M3U8 -> {
                    MediaSniffer.add(url)
                    MediaSniffer.addAll(MediaProbe.fetchAndParseM3u8(url))
                }
                MediaProbe.MediaType.MPD -> MediaSniffer.add(url)
                else -> {}
            }
        }
    }

    private fun probeM3u8(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            MediaSniffer.addAll(MediaProbe.fetchAndParseM3u8(url))
        }
    }

    companion object {
        private val SKIP_PATTERN = Pattern.compile(
            """\.(js|css|m4s|ts|woff2?|ttf|png|jpe?g|gif|svg|webp|ico|json)(\?|$|#)"""
        )

        private const val MEDIA_HOOK_JS = """
(function(){
  function report(u){
    try{
      var f = document.createElement('iframe');
      f.style.display = 'none';
      f.src = 'vx://media?url=' + encodeURIComponent(u);
      document.body.appendChild(f);
      setTimeout(function(){ if(f.parentNode) f.parentNode.removeChild(f); }, 1000);
    }catch(e){}
  }
  function collect(){
    var set = {};
    function add(u){ if(u && !u.startsWith('blob:') && !u.startsWith('data:')) set[u]=1; }
    var els = document.querySelectorAll('video, audio');
    for(var i=0;i<els.length;i++){
      add(els[i].src); add(els[i].currentSrc);
      var ss = els[i].querySelectorAll('source');
      for(var j=0;j<ss.length;j++) add(ss[j].src);
    }
    var alls = document.querySelectorAll('source');
    for(var k=0;k<alls.length;k++) add(alls[k].src);
    for(var key in set){ report(key); }
  }
  var pending=false;
  function schedule(){ if(pending) return; pending=true; setTimeout(function(){ pending=false; collect(); }, 800); }
  collect();
  var obs = new MutationObserver(schedule);
  obs.observe(document, {childList:true, subtree:true});
})();
"""

        /** 进入“标记广告”选择模式：捕获阶段吞掉 click 防跳转，elementFromPoint 反查真实元素 */
        const val ELEMENT_SELECTOR_JS = """
(function(){
  if(window.vxSelector) return;
  var hl=document.createElement('div');
  hl.style.cssText='position:fixed;pointer-events:none;z-index:2147483647;border:2px solid #ff3b30;background:rgba(255,59,48,0.18);box-shadow:0 0 0 9999px rgba(0,0,0,0.25);transition:all .1s;display:none;';
  document.documentElement.appendChild(hl);
  var cur=null;
  var selected=[];
  function rectOf(el){ if(!el) return; var r=el.getBoundingClientRect(); hl.style.left=r.left+'px'; hl.style.top=r.top+'px'; hl.style.width=r.width+'px'; hl.style.height=r.height+'px'; hl.style.display='block'; }
  function clearHl(){ hl.style.display='none'; }
  function mark(el){
    if(!el||el===document.documentElement||el===document.body) return;
    var i=selected.indexOf(el);
    if(i>=0){ selected.splice(i,1); } else { selected.push(el); }
    cur=el; rectOf(el);
  }
  function onPick(e){
    e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
    var el=document.elementFromPoint(e.clientX,e.clientY);
    if(!el) return;
    mark(el);
  }
  document.addEventListener('click', onPick, true);
  function onScroll(){ if(cur) rectOf(cur); }
  window.addEventListener('scroll', onScroll, true);
  window.addEventListener('resize', onScroll, true);
  function cssPath(el){
    if(!el||el===document.documentElement||el===document.body) return '';
    if(el.id) return '#'+el.id;
    var parts=[];
    var node=el;
    while(node&&node.nodeType===1&&node!==document.documentElement&&node!==document.body){
      var s=node.nodeName.toLowerCase();
      if(node.id){ return '#'+node.id + (parts.length? ' '+parts.join(' '):''); }
      if(node.className&&typeof node.className==='string'&&node.className.trim()){
        s+=node.className.trim().split(/[ \t\n\r]+/).filter(Boolean).map(function(c){return '.'+c;}).join('');
      }
      var parent=node.parentNode;
      if(parent&&parent.children){
        var same=Array.prototype.filter.call(parent.children,function(c){return c.nodeName===node.nodeName;});
        if(same.length>1){ var idx=Array.prototype.indexOf.call(parent.children,node)+1; s+=':nth-of-type('+idx+')'; }
      }
      parts.unshift(s);
      node=parent;
    }
    return parts.join(' ');
  }
  window.vxSelector={
    parent:function(){ if(cur&&cur.parentNode&&cur.parentNode.nodeType===1&&cur.parentNode!==document.body){ cur=cur.parentNode; rectOf(cur); } },
    child:function(){ if(cur){ var c=cur.firstElementChild; if(c){ cur=c; rectOf(cur); } } },
    clearAll:function(){ selected=[]; clearHl(); cur=null; },
    apply:function(action){
      var arr=selected.map(function(el){ return cssPath(el); });
      if(action==='hide'){ arr.forEach(function(sel){ if(!sel) return; var els=document.querySelectorAll(sel); for(var i=0;i<els.length;i++){ els[i].style.display='none'; } }); }
      else if(action==='delete'){ arr.forEach(function(sel){ if(!sel) return; var els=document.querySelectorAll(sel); for(var i=0;i<els.length;i++){ if(els[i].parentNode) els[i].parentNode.removeChild(els[i]); } }); }
      clearHl(); selected=[]; cur=null;
      return arr;
    },
    exit:function(){ document.removeEventListener('click',onPick,true); window.removeEventListener('scroll',onScroll,true); window.removeEventListener('resize',onScroll,true); if(hl.parentNode) hl.parentNode.removeChild(hl); window.vxSelector=null; }
  };
})();
"""
    }
}
