package com.vx.browser.ui.home

import android.net.Uri
import com.vx.browser.data.entity.Bookmark

object HomePage {

    fun build(bookmarks: List<Bookmark>, isNight: Boolean): String {
        val theme = if (isNight) "dark" else "light"
        val items = bookmarks.joinToString("") { b ->
            val initial = (b.title.firstOrNull() ?: b.url.firstOrNull())?.toString() ?: "·"
            val url = Uri.encode(b.url)
            val title = escapeHtml(b.title.ifBlank { b.url })
            """
            <div class="item" onclick="location.href='vx://open?url=$url'">
              <div class="ic">$initial</div>
              <div class="t">$title</div>
            </div>
            """.trimIndent()
        }
        val empty = if (bookmarks.isEmpty()) {
            "<div class=\"empty\">暂无书签，去添加几个吧</div>"
        } else ""
        return TEMPLATE
            .replace("%%THEME%%", theme)
            .replace("%%ITEMS%%", items)
            .replace("%%EMPTY%%", empty)
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private val TEMPLATE = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<title>VX浏览器</title>
<style>
:root{
  --bg:#f3f4f6; --card:#ffffff; --text:#222222; --sub:#8a8a8a;
  --accent:#1565C0; --accent-text:#ffffff; --border:#e3e5e8;
}
body.dark{
  --bg:#121212; --card:#1e1e1e; --text:#e8e8e8; --sub:#9a9a9a;
  --accent:#42a5f5; --accent-text:#06121f; --border:#2c2c2c;
}
*{box-sizing:border-box; -webkit-tap-highlight-color:transparent;}
body{
  margin:0; min-height:100vh; display:flex; flex-direction:column; align-items:center;
  padding:48px 16px 40px; background:var(--bg); color:var(--text);
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif;
}
.logo{
  font-size:46px; font-weight:800; letter-spacing:3px; color:var(--accent);
  margin:8px 0 6px;
}
.sub{color:var(--sub); font-size:13px; margin-bottom:26px;}
.search{
  display:flex; width:100%; max-width:560px; align-items:center;
  background:var(--card); border:1px solid var(--border); border-radius:26px; overflow:hidden;
}
.search input{
  flex:1; border:none; outline:none; background:transparent; color:var(--text);
  font-size:16px; padding:14px 18px;
}
.search .qr{
  border:none; background:transparent; color:var(--accent);
  padding:0 10px; cursor:pointer; display:flex; align-items:center; line-height:1;
}
.search .go{
  border:none; background:var(--accent); color:var(--accent-text);
  padding:0 16px; display:flex; align-items:center; justify-content:center;
  align-self:stretch; cursor:pointer;
}
.grid{
  display:grid; grid-template-columns:repeat(auto-fill,minmax(72px,1fr));
  gap:14px; width:100%; max-width:560px; margin-top:30px;
}
.item{
  background:var(--card); border:1px solid var(--border); border-radius:14px;
  padding:16px 6px; display:flex; flex-direction:column; align-items:center; gap:8px; cursor:pointer;
}
.item .ic{
  width:38px; height:38px; border-radius:50%; background:var(--accent); color:var(--accent-text);
  display:flex; align-items:center; justify-content:center; font-weight:700; font-size:17px;
}
.item .t{
  font-size:12px; color:var(--text); text-align:center; max-width:74px;
  overflow:hidden; text-overflow:ellipsis; white-space:nowrap;
}
.empty{color:var(--sub); margin-top:30px; font-size:14px;}
</style>
</head>
<body class="%%THEME%%">
  <div class="logo">VX</div>
  <div class="sub">VX浏览器 · 轻量极速</div>
  <form class="search" onsubmit="return doSearch()">
    <input id="q" type="search" placeholder="搜索或输入网址" />
    <button type="button" class="qr" onclick="location.href='vx://qr'" aria-label="扫码">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2"/>
        <line x1="3" y1="12" x2="21" y2="12"/>
        <line x1="12" y1="3" x2="12" y2="21"/>
      </svg>
    </button>
    <button type="submit" class="go" aria-label="搜索">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="11" cy="11" r="7"/>
        <line x1="21" y1="21" x2="16.65" y2="16.65"/>
      </svg>
    </button>
  </form>
  <div class="grid">
%%ITEMS%%
  </div>
%%EMPTY%%
  <script>
    function doSearch(){
      var v=document.getElementById('q').value.trim();
      if(v){ location.href='vx://search?q='+encodeURIComponent(v); }
      return false;
    }
  </script>
</body>
</html>
""".trimIndent()
}
