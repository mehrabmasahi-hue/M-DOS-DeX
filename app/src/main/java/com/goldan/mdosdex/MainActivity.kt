package com.goldan.mdosdex

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var linkServer: PhoneLinkServer
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var remoteX = 600.0
    private var remoteY = 350.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        linkServer = PhoneLinkServer(
            this,
            onInput = { command -> mainHandler.post { handleRemoteInput(command) } },
            onPackageUploaded = { _, text -> mainHandler.post { installRemotePackage(text) } },
            onFileUploaded = { _, _ -> mainHandler.post { webIfReady("window.dispatchEvent(new CustomEvent('mdos-link-files-changed'));") } },
            onPackageRemoved = { id -> mainHandler.post { webIfReady("window.MDOSLink&&window.MDOSLink.uninstallPackage(${JSONObject.quote(id)});") } }
        )
        linkServer.start()

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.textZoom = 100
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            addJavascriptInterface(PhoneLinkBridge(linkServer), "PhoneLinkNative")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    applyTelevisionLayout()
                    injectRemoteControlApi()
                }
            }
            webChromeClient = WebChromeClient()
            isFocusable = true
            isFocusableInTouchMode = true
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(web)
        web.requestFocus()
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }


    private fun applyTelevisionLayout() {
        val script = \"\"\"
            (function(){
              if(document.getElementById('mdos-tv-layout')) return;
              const style=document.createElement('style');
              style.id='mdos-tv-layout';
              style.textContent = `
                html,body{width:100%!important;height:100%!important;min-width:0!important;overflow:hidden!important;zoom:1!important}
                #desk{inset:0 0 64px 0!important}
                #tb{position:fixed!important;top:auto!important;right:0!important;bottom:0!important;left:0!important;height:64px!important;min-height:64px!important;display:grid!important;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr)!important;direction:ltr!important;padding:0 22px!important;z-index:99999!important}
                #weatherSlot{grid-column:1!important;justify-self:start!important;direction:rtl!important}
                #cen{grid-column:2!important;justify-self:center!important}
                #tray{grid-column:3!important;justify-self:end!important;direction:rtl!important;display:flex!important;align-items:center!important;gap:16px!important}
                #tray span,#clk{font-size:15px!important}
                #st{width:48px!important;height:48px!important}
                #st img{width:34px!important;height:34px!important}
                #ic{top:16px!important;right:20px!important;height:calc(100% - 32px)!important;grid-auto-columns:112px!important;grid-template-rows:repeat(auto-fill,104px)!important;gap:8px!important}
                .ico{width:108px!important;height:100px!important;font-size:14px!important}
                .ico b{font-size:42px!important}
                .ico span{max-width:104px!important;font-size:14px!important}
                .win{max-width:calc(100vw - 40px)!important;max-height:calc(100vh - 100px)!important}
                #sm{bottom:76px!important;max-height:calc(100vh - 100px)!important}
                #qs,#searchPanel,#weatherPanel{bottom:76px!important}
                #tv{inset:0 0 64px!important}
                [data-taskbar="top"] #desk{top:0!important;bottom:64px!important}
                [data-taskbar="hidden"] #tb{transform:none!important}
                [data-scale="90"],[data-scale="110"],[data-scale="125"]{zoom:1!important}
                button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #ffc928!important;outline-offset:3px!important}
              `;
              document.head.appendChild(style);
              document.documentElement.dataset.taskbar='bottom';
              document.documentElement.dataset.scale='100';
              const visible=()=>Array.from(document.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),[tabindex="0"]')).filter(e=>e.getClientRects().length && getComputedStyle(e).visibility!=='hidden');
              document.addEventListener('keydown',function(e){
                if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)) return;
                const items=visible(); if(!items.length) return;
                let i=items.indexOf(document.activeElement);
                if(i<0) i=0;
                const step=(e.key==='ArrowLeft'||e.key==='ArrowUp')?-1:1;
                items[(i+step+items.length)%items.length].focus();
                e.preventDefault();
              },true);
              document.addEventListener('keydown',function(e){
                if(e.key==='Enter' && document.activeElement && document.activeElement.tagName==='BUTTON'){
                  document.activeElement.click(); e.preventDefault();
                }
              },true);
            })();
        \"\"\".trimIndent()
        web.evaluateJavascript(script, null)
    }

    private fun injectRemoteControlApi() {
        val script = """
            (function(){
              if(window.MDOSRemoteInput)return;
              window.__mdosRemotePointer={x:Math.round(innerWidth/2),y:Math.round(innerHeight/2)};
              const cursor=document.createElement('div');cursor.id='mdosRemoteCursor';
              cursor.textContent='➤';cursor.style.cssText='position:fixed;left:0;top:0;z-index:2147483647;pointer-events:none;color:#ffca28;font-size:28px;font-weight:900;line-height:1;text-shadow:0 1px 4px #000;transform:translate('+window.__mdosRemotePointer.x+'px,'+window.__mdosRemotePointer.y+'px)';
              document.documentElement.appendChild(cursor);
              window.addEventListener('resize',()=>{const p=window.__mdosRemotePointer;p.x=Math.min(p.x,innerWidth-1);p.y=Math.min(p.y,innerHeight-1);cursor.style.transform='translate('+p.x+'px,'+p.y+'px)'});
              window.MDOSRemoteInput=function(c){
                try{
                  const p=window.__mdosRemotePointer, d=document;
                  if(c.type==='move'){
                    p.x=Math.max(0,Math.min(innerWidth-1,p.x+(Number(c.dx)||0)));
                    p.y=Math.max(0,Math.min(innerHeight-1,p.y+(Number(c.dy)||0)));
                    cursor.style.transform='translate('+p.x+'px,'+p.y+'px)';
                    const e=d.elementFromPoint(p.x,p.y)||d.body;
                    e.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:p.x,clientY:p.y,view:window}));
                    return true;
                  }
                  if(c.type==='down'||c.type==='up'){
                    const e=d.elementFromPoint(p.x,p.y)||d.body, down=c.type==='down';
                    e.dispatchEvent(new MouseEvent(down?'mousedown':'mouseup',{bubbles:true,button:0,buttons:down?1:0,clientX:p.x,clientY:p.y,view:window}));return true;
                  }
                  if(c.type==='tap'||c.type==='right'){
                    const e=d.elementFromPoint(p.x,p.y)||d.body, right=c.type==='right';
                    e.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:p.x,clientY:p.y,view:window}));
                    e.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,button:right?2:0,buttons:right?2:1,clientX:p.x,clientY:p.y,view:window}));
                    e.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,button:right?2:0,buttons:0,clientX:p.x,clientY:p.y,view:window}));
                    if(!right)e.dispatchEvent(new MouseEvent('click',{bubbles:true,button:0,clientX:p.x,clientY:p.y,view:window}));
                    else e.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,button:2,clientX:p.x,clientY:p.y,view:window}));
                    return true;
                  }
                  if(c.type==='scroll'){const e=d.elementFromPoint(p.x,p.y)||d.body;e.dispatchEvent(new WheelEvent('wheel',{bubbles:true,deltaY:Number(c.dy)||0,clientX:p.x,clientY:p.y}));return true;}
                  if(c.type==='key'){
                    const key=String(c.key||'');const el=d.activeElement||d.body;
                    el.dispatchEvent(new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true}));
                    el.dispatchEvent(new KeyboardEvent('keyup',{key,bubbles:true,cancelable:true}));return true;
                  }
                  if(c.type==='text'){
                    const el=d.activeElement||d.querySelector('input:focus,textarea:focus, [contenteditable=true]');
                    if(el){if('value' in el){const start=el.selectionStart??el.value.length,end=el.selectionEnd??el.value.length;el.setRangeText(String(c.text||''),start,end,'end');}else el.textContent+=String(c.text||'');el.dispatchEvent(new Event('input',{bubbles:true}));return true;}return false;
                  }
                  if(c.type==='scroll-home'){window.scrollTo(0,0);return true;}
                }catch(e){console.warn('M-DOS remote input',e)}return false;
              };
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private fun handleRemoteInput(command: JSONObject) {
        if (!::web.isInitialized) return
        when (command.optString("type")) {
            "move" -> {
                remoteX += command.optDouble("dx", 0.0)
                remoteY += command.optDouble("dy", 0.0)
                web.evaluateJavascript("window.MDOSRemoteInput&&window.MDOSRemoteInput({type:'move',dx:${command.optDouble("dx", 0.0)},dy:${command.optDouble("dy", 0.0)}});", null)
            }
            "tap", "right", "down", "up", "scroll", "key", "text", "scroll-home" -> {
                val payload = JSONObject(command.toString()).toString()
                web.evaluateJavascript("window.MDOSRemoteInput&&window.MDOSRemoteInput($payload);", null)
            }
        }
    }

    private fun installRemotePackage(text: String) {
        if (!::web.isInitialized) return
        web.evaluateJavascript("window.MDOSLink&&window.MDOSLink.importPackageFromText(${JSONObject.quote(text)});", null)
    }

    private fun webIfReady(script: String) { if (::web.isInitialized) web.evaluateJavascript(script, null) }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onDestroy() {
        linkServer.close()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
