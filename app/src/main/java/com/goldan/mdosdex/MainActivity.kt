package com.goldan.mdosdex

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var linkServer: PhoneLinkServer
    private val mainHandler = Handler(Looper.getMainLooper())

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
                    injectPhoneLinkPairingUi()
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

    /** Uses viewport-relative sizing so the desktop scales correctly on 720p, 1080p and 4K TVs. */
    private fun applyTelevisionLayout() {
        val script = """
            (function(){
              if(document.getElementById('mdos-tv-layout')) return;
              const style=document.createElement('style');
              style.id='mdos-tv-layout';
              style.textContent = `
                :root{--mdos-tv-bar:clamp(56px,6.3vh,76px)}
                html,body{width:100%!important;height:100%!important;min-width:0!important;min-height:0!important;overflow:hidden!important;zoom:1!important}
                body{font-size:clamp(14px,1.05vw,18px)!important}
                #desk{position:fixed!important;inset:0 0 var(--mdos-tv-bar) 0!important;overflow:hidden!important}
                #tb{position:fixed!important;inset:auto 0 0 0!important;width:100%!important;height:var(--mdos-tv-bar)!important;min-height:var(--mdos-tv-bar)!important;max-height:var(--mdos-tv-bar)!important;display:grid!important;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr)!important;align-items:center!important;direction:ltr!important;padding:0 clamp(12px,2vw,30px)!important;z-index:99999!important;box-sizing:border-box!important}
                #weatherSlot{grid-column:1!important;justify-self:start!important;direction:rtl!important;min-width:0!important;max-width:30vw!important;overflow:hidden!important}
                #cen{grid-column:2!important;justify-self:center!important;min-width:0!important;max-width:45vw!important;display:flex!important;align-items:center!important;gap:clamp(3px,.45vw,8px)!important}
                #tray{grid-column:3!important;justify-self:end!important;direction:rtl!important;display:flex!important;align-items:center!important;gap:clamp(6px,1vw,16px)!important;min-width:0!important;max-width:36vw!important;overflow:hidden!important}
                #weatherBtn{max-width:100%!important;white-space:nowrap!important;font-size:clamp(11px,.9vw,15px)!important}
                #tray span,#clk{font-size:clamp(11px,.9vw,15px)!important}
                #st{width:clamp(42px,4vw,54px)!important;height:clamp(42px,4vw,54px)!important;flex:none!important}
                #st img{width:clamp(28px,2.6vw,36px)!important;height:clamp(28px,2.6vw,36px)!important}
                #tasks{max-width:36vw!important;overflow:hidden!important;display:flex!important;gap:clamp(2px,.35vw,7px)!important}
                .tk{width:clamp(34px,3.2vw,44px)!important;height:clamp(38px,4.5vh,48px)!important;font-size:clamp(17px,1.6vw,23px)!important;flex:none!important}
                #ic{position:absolute!important;top:clamp(12px,1.8vh,24px)!important;right:clamp(14px,2vw,32px)!important;left:auto!important;height:calc(100% - clamp(24px,3.6vh,48px))!important;grid-auto-columns:clamp(88px,7.8vw,120px)!important;grid-template-rows:repeat(auto-fill,minmax(clamp(94px,10.5vh,120px),auto))!important;gap:clamp(5px,.65vw,11px)!important;align-content:start!important}
                .ico{width:clamp(84px,7.5vw,114px)!important;height:clamp(88px,10vh,116px)!important;font-size:clamp(12px,.95vw,15px)!important;display:flex!important;flex-direction:column!important;align-items:center!important;justify-content:center!important;gap:3px!important}
                .ico b{font-size:clamp(32px,3vw,46px)!important;line-height:1.15!important}
                .ico span{max-width:100%!important;white-space:normal!important;overflow-wrap:anywhere!important;text-align:center!important}
                .win:not(.mx){width:min(72vw,1280px)!important;height:min(72vh,900px)!important;max-width:calc(100vw - 40px)!important;max-height:calc(100vh - var(--mdos-tv-bar) - 24px)!important}
                #sm,#qs,#searchPanel,#weatherPanel{bottom:calc(var(--mdos-tv-bar) + 12px)!important;max-height:calc(100vh - var(--mdos-tv-bar) - 28px)!important}
                #tv{inset:0 0 var(--mdos-tv-bar)!important}
                [data-taskbar="top"] #desk{top:0!important;bottom:var(--mdos-tv-bar)!important}
                [data-taskbar="hidden"] #tb{transform:none!important;opacity:1!important}
                button:focus-visible,input:focus-visible,select:focus-visible,[tabindex="0"]:focus-visible{outline:3px solid #ffc928!important;outline-offset:3px!important}
                .win{min-width:min(290px,calc(100vw - 24px))!important;min-height:min(190px,calc(100vh - 100px))!important}
              `;
              document.head.appendChild(style);
              document.documentElement.dataset.taskbar='bottom';
              document.documentElement.dataset.scale='100';
              const getVisible=()=>Array.from(document.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),[tabindex="0"]')).filter(e=>e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden');
              document.addEventListener('keydown',function(e){
                if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)) return;
                const items=getVisible();if(!items.length)return;
                let i=items.indexOf(document.activeElement);if(i<0)i=0;
                const step=(e.key==='ArrowLeft'||e.key==='ArrowUp')?-1:1;
                items[(i+step+items.length)%items.length].focus();e.preventDefault();
              },true);
              document.addEventListener('keydown',function(e){
                if(e.key==='Enter'&&document.activeElement&&document.activeElement.tagName==='BUTTON'){
                  document.activeElement.click();e.preventDefault();
                }
              },true);
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    /** Keeps the existing Phone Link files/apps UI, replacing QR with a one-time numeric code. */
    private fun injectPhoneLinkPairingUi() {
        val script = """
            (function(){
              if(window.__mdosWifiPhoneLinkInstalled)return;
              if(typeof window.phoneLinkApp!=='function'||!window.PhoneLinkNative)return;
              const original=window.phoneLinkApp;
              const replacement=function(el){
                original(el);
                const api=window.PhoneLinkNative;
                const qr=el.querySelector('img[alt="کد QR اتصال Phone Link"]');
                const card=qr&&qr.parentElement;
                if(!card||card.dataset.wifiPairing==='1')return;
                card.dataset.wifiPairing='1';
                card.innerHTML='';
                card.style.cssText+=';display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;gap:10px;padding:18px;min-height:210px';
                const heading=document.createElement('b');heading.textContent='اتصال Wi-Fi به Link to M-DOS';heading.style.fontSize='clamp(17px,2vw,23px)';
                const code=document.createElement('div');code.style.cssText='font:900 clamp(34px,5vw,52px)/1.3 ui-monospace,monospace;letter-spacing:8px;direction:ltr;color:#ffc928;background:#111827;border:1px solid #ffc92866;border-radius:16px;padding:8px 22px;min-width:220px';
                const address=document.createElement('div');address.style.cssText='font:500 12px/1.7 ui-monospace,monospace;direction:ltr;overflow-wrap:anywhere';
                const state=document.createElement('div');state.className='mu';state.style.cssText='font-size:12px;max-width:620px';
                const note=document.createElement('div');note.className='mu';note.style.cssText='font-size:12px;max-width:650px';note.textContent='در Link to M-DOS تلویزیون را جستجو و انتخاب کن؛ این کد فقط برای جفت‌سازی اولیه است. پس از آن اتصال ذخیره و خودکار می‌شود.';
                const refresh=document.createElement('button');refresh.className='btn s';refresh.textContent='به‌روزرسانی کد و آدرس';
                function update(){
                  try{const p=JSON.parse(api.getPairInfo()||'{}');code.textContent=p.pairingCode||'------';address.textContent=p.url||('Port '+(p.port||8765));state.textContent=(p.network||'شبکه محلی')+' · دستگاه‌های جفت‌شده: '+(p.trustedPhones||0);}
                  catch(e){code.textContent='------';state.textContent='خواندن وضعیت Phone Link ناموفق بود';}
                }
                refresh.onclick=update;
                card.append(heading,code,address,state,note,refresh);update();
                const timer=setInterval(function(){if(!document.body.contains(card)){clearInterval(timer);return;}update();},2500);
              };
              window.phoneLinkApp=replacement;
              try{if(typeof APPS!=='undefined'&&APPS.phonelink)APPS.phonelink.b=replacement;}catch(e){}
              window.__mdosWifiPhoneLinkInstalled=true;
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private fun injectRemoteControlApi() {
        val script = """
            (function(){
              if(window.MDOSRemoteInput)return;
              window.__mdosRemotePointer={x:Math.round(innerWidth/2),y:Math.round(innerHeight/2)};
              window.__mdosRemoteCapture=null;
              if(!Element.prototype.__mdosCapturePatched){
                const nativeSet=Element.prototype.setPointerCapture,nativeRelease=Element.prototype.releasePointerCapture;
                Element.prototype.setPointerCapture=function(id){if(id===1){window.__mdosRemoteCapture=this;return;}if(nativeSet)return nativeSet.call(this,id);};
                Element.prototype.releasePointerCapture=function(id){if(id===1&&window.__mdosRemoteCapture===this){window.__mdosRemoteCapture=null;return;}if(nativeRelease)return nativeRelease.call(this,id);};
                Element.prototype.__mdosCapturePatched=true;
              }
              const cursor=document.createElement('div');cursor.id='mdosRemoteCursor';
              cursor.textContent='➤';cursor.style.cssText='position:fixed;left:0;top:0;z-index:2147483647;pointer-events:none;color:#ffca28;font-size:28px;font-weight:900;line-height:1;text-shadow:0 1px 4px #000;transform:translate('+window.__mdosRemotePointer.x+'px,'+window.__mdosRemotePointer.y+'px)';
              document.documentElement.appendChild(cursor);
              window.addEventListener('resize',()=>{const p=window.__mdosRemotePointer;p.x=Math.min(p.x,innerWidth-1);p.y=Math.min(p.y,innerHeight-1);cursor.style.transform='translate('+p.x+'px,'+p.y+'px)'});
              window.MDOSRemoteInput=function(c){
                try{
                  const p=window.__mdosRemotePointer,d=document;
                  const eventTarget=()=>window.__mdosRemoteCapture||d.elementFromPoint(p.x,p.y)||d.body;
                  const pointer=(target,type,button,buttons)=>{
                    const init={bubbles:true,cancelable:true,button:button||0,buttons:buttons||0,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true};
                    if(window.PointerEvent)target.dispatchEvent(new PointerEvent(type,init));
                    target.dispatchEvent(new MouseEvent(type.replace('pointer','mouse'),init));
                  };
                  if(c.type==='move'){
                    p.x=Math.max(0,Math.min(innerWidth-1,p.x+(Number(c.dx)||0)));
                    p.y=Math.max(0,Math.min(innerHeight-1,p.y+(Number(c.dy)||0)));
                    cursor.style.transform='translate('+p.x+'px,'+p.y+'px)';
                    const e=eventTarget();
                    if(window.PointerEvent)e.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true,buttons:window.__mdosRemoteCapture?1:0}));
                    e.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:p.x,clientY:p.y,view:window,buttons:window.__mdosRemoteCapture?1:0}));return true;
                  }
                  if(c.type==='down'){
                    const e=eventTarget();window.__mdosRemoteCapture=e;pointer(e,'pointerdown',0,1);return true;
                  }
                  if(c.type==='up'){
                    const e=eventTarget();pointer(e,'pointerup',0,0);window.__mdosRemoteCapture=null;return true;
                  }
                  if(c.type==='tap'||c.type==='right'){
                    const e=eventTarget(),right=c.type==='right';
                    e.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:p.x,clientY:p.y,view:window}));
                    if(window.PointerEvent)e.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:right?2:0,buttons:right?2:1,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true}));
                    e.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,button:right?2:0,buttons:right?2:1,clientX:p.x,clientY:p.y,view:window}));
                    if(window.PointerEvent)e.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,cancelable:true,button:right?2:0,buttons:0,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true}));
                    e.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,button:right?2:0,buttons:0,clientX:p.x,clientY:p.y,view:window}));
                    if(!right)e.dispatchEvent(new MouseEvent('click',{bubbles:true,button:0,clientX:p.x,clientY:p.y,view:window}));
                    else e.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,button:2,clientX:p.x,clientY:p.y,view:window}));return true;
                  }
                  if(c.type==='scroll'){const e=d.elementFromPoint(p.x,p.y)||d.body;e.dispatchEvent(new WheelEvent('wheel',{bubbles:true,deltaY:Number(c.dy)||0,clientX:p.x,clientY:p.y}));return true;}
                  if(c.type==='key'){
                    const key=String(c.key||''),el=d.activeElement||d.body;
                    el.dispatchEvent(new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true}));
                    el.dispatchEvent(new KeyboardEvent('keyup',{key,bubbles:true,cancelable:true}));return true;
                  }
                  if(c.type==='text'){
                    const el=d.activeElement||d.querySelector('input:focus,textarea:focus,[contenteditable=true]');
                    if(el){if('value'in el){const start=el.selectionStart??el.value.length,end=el.selectionEnd??el.value.length;el.setRangeText(String(c.text||''),start,end,'end');}else el.textContent+=String(c.text||'');el.dispatchEvent(new Event('input',{bubbles:true}));return true;}return false;
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
            "move", "tap", "right", "down", "up", "scroll", "key", "text", "scroll-home" -> {
                val payload = JSONObject(command.toString()).toString()
                web.evaluateJavascript("window.MDOSRemoteInput&&window.MDOSRemoteInput($payload);", null)
            }
        }
    }

    private fun installRemotePackage(text: String) {
        webIfReady("window.MDOSLink&&window.MDOSLink.importPackageFromText(${JSONObject.quote(text)});")
    }

    private fun webIfReady(script: String) {
        if (::web.isInitialized) web.evaluateJavascript(script, null)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onDestroy() {
        if (::linkServer.isInitialized) linkServer.close()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
