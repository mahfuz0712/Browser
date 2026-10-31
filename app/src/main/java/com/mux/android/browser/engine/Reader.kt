package com.mux.android.browser.engine

import com.mux.android.browser.data.AppSettings

/** "Reader mode": pulls the main article out of a page and re-renders it in HUD colors. */
object Reader {
    const val JS = """(function(){
try{
function score(el){var p=el.querySelectorAll('p'),s=0;for(var i=0;i<p.length;i++){s+=(p[i].innerText||'').length}return s}
var c=document.querySelectorAll('article,main,[role=main],section,div');
var best=document.body,bs=score(best);
for(var i=0;i<c.length;i++){var sc=score(c[i]);if(sc>bs*0.98&&c[i].contains(best)===false&&sc>=bs){best=c[i];bs=sc}}
var again=true;
while(again){again=false;var ch=best.children;for(var j=0;j<ch.length;j++){if(score(ch[j])>=bs*0.8&&ch[j].querySelectorAll('p').length>1){best=ch[j];bs=score(best);again=true;break}}}
var out=[];var els=best.querySelectorAll('h1,h2,h3,h4,p,blockquote,pre,ul,ol,img');
for(var k=0;k<els.length;k++){var e=els[k];var t=e.tagName.toLowerCase();
 if(t!=='blockquote'&&t!=='ul'&&t!=='ol'&&e.parentElement&&e.parentElement.closest('blockquote,ul,ol'))continue;
 if(t==='img'){if(e.src&&e.width>120)out.push('<img src="'+e.src+'">');continue}
 var h=e.innerHTML.replace(/<script[\s\S]*?<\/script>/gi,'').replace(/<style[\s\S]*?<\/style>/gi,'').replace(/\son\w+="[^"]*"/gi,'').replace(/javascript:/gi,'');
 if((e.innerText||'').trim().length<2)continue;
 out.push('<'+t+'>'+h+'</'+t+'>')}
var title=(document.querySelector('h1')||{}).innerText||document.title;
return JSON.stringify({ok:bs>250,title:title,html:out.join('')});
}catch(e){return JSON.stringify({ok:false})}
})()"""

    fun html(title: String, body: String, source: String): String {
        val size = AppSettings.readerSize
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;") }
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{background:#040A12;color:#D6F8FF;font-family:sans-serif;font-size:${size}px;line-height:1.65;margin:0;padding:18px}
.w{max-width:680px;margin:0 auto}
.tag{font-family:monospace;color:#3C8A99;font-size:11px;letter-spacing:1px}
h1{color:#19E3FF;font-size:1.5em;line-height:1.25}
h2,h3,h4{color:#FFB000;line-height:1.3}
a{color:#19E3FF}
img{max-width:100%;height:auto;border:1px solid #19E3FF55;margin:12px 0}
blockquote{border-left:3px solid #FFB000;margin-left:0;padding-left:14px;color:#9fd8e4}
pre{overflow:auto;background:#08141F;padding:10px;border:1px solid #19E3FF33}
</style></head><body><div class="w">
<div class="tag">◈ READER MODE · ${esc(source.removePrefix("https://").removePrefix("http://").take(60))}</div>
<h1>${esc(title)}</h1>
$body
</div></body></html>"""
    }
}
