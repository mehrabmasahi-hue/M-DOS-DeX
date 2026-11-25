package com.goldan.mdosdex

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var linkServer: PhoneLinkServer
    private lateinit var globalControlServer: MdosGlobalControlServer
    private lateinit var rootFrame: FrameLayout
    private lateinit var diagnosticView: TextView
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

        globalControlServer = MdosGlobalControlServer(this) { command ->
            mainHandler.post {
                val safeCommand = JSONObject.quote(command)
                webIfReady("window.MDOSConnect&&window.MDOSConnect.handleRemoteCommand($safeCommand);")
            }
        }
        globalControlServer.start()

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
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            addJavascriptInterface(PhoneLinkBridge(linkServer) { address ->
                mainHandler.post {
                    startActivity(Intent(this@MainActivity, MdosBrowserActivity::class.java)
                        .putExtra(MdosBrowserActivity.EXTRA_URL, address))
                }
            }, "PhoneLinkNative")
            addJavascriptInterface(MdosConnectBridge(globalControlServer, this@MainActivity), "MdosConnectNative")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    diagnosticView.visibility = View.GONE
                    applyTelevisionLayout()
                    installTelevisionNavigation()
                    injectRemoteControlApi()
                    injectPhoneLinkPairingUi()
                }
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) showDiagnostic("خطای بارگذاری M-DOS: " + (error?.description?.toString() ?: "نامشخص"))
                }
                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    showDiagnostic("موتور نمایش WebView متوقف شد. برنامه را ببندید و دوباره اجرا کنید.")
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        showDiagnostic("خطای JavaScript: " + message.message().take(220) + " · خط " + message.lineNumber())
                    }
                    return true
                }
            }
            isFocusable = true
            isFocusableInTouchMode = true
        }
        rootFrame = FrameLayout(this)
        diagnosticView = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xDD7A1F1F.toInt())
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 12, 18, 12)
            visibility = View.GONE
        }
        rootFrame.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootFrame.addView(diagnosticView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(rootFrame)
        web.requestFocus()
        web.post { web.loadUrl("file:///android_asset/index.html") }
    }

    private fun showDiagnostic(message: String) {
        if (!::diagnosticView.isInitialized) return
        runOnUiThread {
            diagnosticView.text = message
            diagnosticView.visibility = View.VISIBLE
        }
    }

    private fun applyImmersiveMode() {
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    /** TV sizing for the existing M-DOS desktop; original HTML shell and apps remain unchanged. */
    private fun applyTelevisionLayout() {
        val script = """
            (function(){
              if(document.getElementById('mdos-tv-layout')) return;
              const style=document.createElement('style');style.id='mdos-tv-layout';
              style.textContent=[
                'html,body{width:100%!important;height:100%!important;min-width:0!important;min-height:0!important;overflow:hidden!important;margin:0!important}',
                'body{font-size:clamp(14px,1.05vw,19px)!important}',
                '#desk{position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:76px!important;width:100%!important;height:auto!important;overflow:hidden!important;display:block!important}',
                '#tb{position:fixed!important;left:0!important;right:0!important;bottom:0!important;top:auto!important;width:100%!important;height:76px!important;min-height:76px!important;max-height:76px!important;display:grid!important;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr)!important;align-items:center!important;direction:ltr!important;padding:0 22px!important;box-sizing:border-box!important;z-index:99999!important}',
                '#weatherSlot{grid-column:1!important;justify-self:start!important;direction:rtl!important;min-width:0!important;max-width:25vw!important;overflow:hidden!important}',
                '#cen{grid-column:2!important;justify-self:center!important;min-width:0!important;max-width:48vw!important;display:flex!important;align-items:center!important;gap:clamp(3px,.5vw,9px)!important;overflow:hidden!important}',
                '#tray{grid-column:3!important;justify-self:end!important;direction:rtl!important;display:flex!important;align-items:center!important;gap:clamp(6px,1vw,16px)!important;min-width:0!important;max-width:30vw!important;overflow:hidden!important}',
                '#weatherBtn{max-width:100%!important;white-space:nowrap!important;font-size:clamp(11px,.85vw,15px)!important}',
                '#tray span,#clk{font-size:clamp(11px,.85vw,15px)!important}',
                '#st{width:clamp(42px,3.7vw,56px)!important;height:clamp(42px,5vh,58px)!important;flex:none!important}',
                '#st img{width:clamp(28px,2.5vw,38px)!important;height:clamp(28px,2.5vw,38px)!important}',
                '#tasks{max-width:34vw!important;overflow:hidden!important;display:flex!important;gap:clamp(2px,.35vw,7px)!important}',
                '.tk{width:clamp(34px,3vw,46px)!important;height:clamp(38px,4.5vh,52px)!important;font-size:clamp(17px,1.5vw,24px)!important;flex:none!important}',
                '#ic{display:grid!important;visibility:visible!important;opacity:1!important;position:absolute!important;z-index:2!important;top:clamp(12px,1.5vh,22px)!important;right:clamp(14px,1.8vw,30px)!important;left:auto!important;bottom:clamp(8px,1vh,14px)!important;height:auto!important;display:grid!important;grid-auto-flow:column!important;grid-template-rows:repeat(5,minmax(88px,10vh))!important;grid-auto-columns:clamp(90px,7.8vw,128px)!important;gap:clamp(4px,.5vw,9px)!important;align-content:start!important;justify-content:start!important}',
                '.ico{width:clamp(86px,7.3vw,118px)!important;height:clamp(86px,9.5vh,112px)!important;min-height:0!important;display:flex!important;flex-direction:column!important;align-items:center!important;justify-content:center!important;gap:4px!important;padding:5px!important;font-size:clamp(12px,.9vw,16px)!important;white-space:normal!important}',
                '.ico b{font-size:clamp(30px,2.8vw,46px)!important;line-height:1.2!important}',
                '.ico span{max-width:100%!important;white-space:normal!important;overflow-wrap:anywhere!important;text-align:center!important}',
                '.win{z-index:100!important}',
                '.win:not(.mx){width:min(72vw,1280px)!important;height:min(72vh,900px)!important;max-width:calc(100vw - 48px)!important;max-height:calc(100vh - 110px)!important}',
                '#sm,#qs,#searchPanel,#weatherPanel{bottom:calc(clamp(64px,7vh,82px) + 10px)!important;max-height:calc(100vh - 110px)!important}',
                '#tv{top:0!important;left:0!important;right:0!important;bottom:76px!important}',
                '#firstRun{padding:clamp(12px,3vh,28px)!important;overflow-y:auto!important;align-items:center!important;justify-content:center!important}',
                '.fr-card{width:min(760px,82vw)!important;max-height:calc(100vh - 36px)!important;min-height:0!important;overflow-y:auto!important;padding:clamp(20px,3vh,38px)!important}',
                '.fr-input{font-size:16px!important;min-height:46px!important}',
                '#bt,#lk{top:0!important;left:0!important;right:0!important;bottom:0!important;width:100%!important;height:100%!important}',
                '.win{min-width:min(320px,calc(100vw - 32px))!important;min-height:min(200px,calc(100vh - 100px))!important}',
                'button:focus-visible,input:focus-visible,select:focus-visible,[tabindex="0"]:focus-visible{outline:3px solid #ffc928!important;outline-offset:3px!important}'
              ].join('');
              document.head.appendChild(style);
              try{
                const pref=JSON.parse(localStorage.getItem('mdosPrefs')||'{}');
                pref.showIcons=true;
                localStorage.setItem('mdosPrefs',JSON.stringify(pref));
                document.documentElement.dataset.desktopIcons='on';
                if(typeof applyPrefs==='function')applyPrefs();
                document.documentElement.dataset.desktopIcons='on';
                if(typeof build==='function')build();
              }catch(e){console.error('M-DOS TV desktop recovery',e);}
              document.documentElement.dataset.taskbar='bottom';
              document.documentElement.dataset.scale='100';
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    /** DPAD support for the existing M-DOS setup wizard and desktop. */
    private fun installTelevisionNavigation() {
        val script = """
            (function(){
              if(window.__mdosTvNavigationReady)return;
              window.__mdosTvNavigationReady=true;
              const visible=e=>!!(e&&e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden'&&!e.disabled);
              const focusInitial=()=>{
                const first=document.querySelector('#firstRun.show');
                const scope=first||document.querySelector('#sm.open')||document.querySelector('#qs.open')||document.querySelector('#searchPanel.open')||document;
                const list=Array.from(scope.querySelectorAll('button,input,select,[tabindex="0"]')).filter(visible);
                if(list.length)list[0].focus();
                else {const icon=document.querySelector('#ic .ico');if(icon)icon.focus();else document.querySelector('#st')?.focus();}
              };
              const firstRun=document.getElementById('firstRun');
              if(firstRun){
                new MutationObserver(()=>setTimeout(focusInitial,80)).observe(firstRun,{attributes:true,attributeFilter:['class']});
                firstRun.addEventListener('click',()=>setTimeout(()=>{if(firstRun.classList.contains('show'))focusInitial()},100),true);
              }
              document.addEventListener('keydown',function(e){
                if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)){
                  const active=document.activeElement;
                  if(active&&(active.tagName==='INPUT'||active.tagName==='TEXTAREA')&&(e.key==='ArrowLeft'||e.key==='ArrowRight'))return;
                  const first=document.querySelector('#firstRun.show');
                  const scope=first||document.querySelector('#sm.open')||document.querySelector('#qs.open')||document.querySelector('#searchPanel.open')||document;
                  const list=Array.from(scope.querySelectorAll('button,input,select,[tabindex="0"]')).filter(visible);
                  if(!list.length)return;
                  if(!list.includes(active)){list[0].focus();e.preventDefault();return;}
                  const a=active.getBoundingClientRect(),ax=a.left+a.width/2,ay=a.top+a.height/2;
                  const opts=list.filter(x=>x!==active).map(x=>{
                    const r=x.getBoundingClientRect(),dx=r.left+r.width/2-ax,dy=r.top+r.height/2-ay;
                    const primary=e.key==='ArrowLeft'?-dx:e.key==='ArrowRight'?dx:e.key==='ArrowUp'?-dy:dy;
                    const secondary=(e.key==='ArrowLeft'||e.key==='ArrowRight')?Math.abs(dy):Math.abs(dx);
                    return {x,score:primary>0?primary+secondary*2:Infinity};
                  }).filter(x=>Number.isFinite(x.score)).sort((x,y)=>x.score-y.score);
                  (opts[0]?.x||list[(list.indexOf(active)+1)%list.length]).focus();
                  e.preventDefault();return;
                }
                if(e.key==='Enter'&&document.activeElement&&document.activeElement.tagName==='BUTTON'){document.activeElement.click();e.preventDefault();return;}
                if(e.key==='Escape'||e.keyCode===4){
                  if(firstRun?.classList.contains('show'))return;
                  const close=document.querySelector('.win.act .cb.x');
                  if(close){close.click();e.preventDefault();return;}
                  ['sm','qs','cm','searchPanel','tv'].forEach(id=>document.getElementById(id)?.classList.remove('open'));
                }
              },true);
              setTimeout(focusInitial,9000);
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
              cursor.textContent='↖';cursor.style.cssText='position:fixed;left:0;top:0;z-index:2147483647;pointer-events:none;color:#ffca28;font-size:31px;font-weight:900;line-height:1;text-shadow:0 1px 4px #000;will-change:transform;transform:translate('+(window.__mdosRemotePointer.x-6)+'px,'+(window.__mdosRemotePointer.y-7)+'px)';
              document.documentElement.appendChild(cursor);
              window.addEventListener('resize',()=>{const p=window.__mdosRemotePointer;p.x=Math.min(p.x,innerWidth-1);p.y=Math.min(p.y,innerHeight-1);cursor.style.transform='translate('+(p.x-6)+'px,'+(p.y-7)+'px)'});
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
                    cursor.style.transform='translate('+(p.x-6)+'px,'+(p.y-7)+'px)';
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
                  if(c.type==='tap'||c.type==='right'||c.type==='middle'||c.type==='double'){
                    const e=eventTarget(),button=c.type==='right'?2:c.type==='middle'?1:0;
                    const clickOnce=btn=>{
                      const buttons=btn===2?2:btn===1?4:1;
                      if(window.PointerEvent)e.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:btn,buttons,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true}));
                      e.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,button:btn,buttons,clientX:p.x,clientY:p.y,view:window}));
                      if(window.PointerEvent)e.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,cancelable:true,button:btn,buttons:0,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true}));
                      e.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,button:btn,buttons:0,clientX:p.x,clientY:p.y,view:window}));
                      if(btn===0)e.dispatchEvent(new MouseEvent('click',{bubbles:true,button:0,clientX:p.x,clientY:p.y,view:window}));
                      else if(btn===1)e.dispatchEvent(new MouseEvent('auxclick',{bubbles:true,button:1,clientX:p.x,clientY:p.y,view:window}));
                      else e.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,button:2,clientX:p.x,clientY:p.y,view:window}));
                    };
                    e.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:p.x,clientY:p.y,view:window}));
                    if(c.type==='double'){clickOnce(0);clickOnce(0);e.dispatchEvent(new MouseEvent('dblclick',{bubbles:true,button:0,clientX:p.x,clientY:p.y,view:window}));}
                    else clickOnce(button);
                    return true;
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
            "move", "tap", "right", "middle", "double", "down", "up", "scroll", "key", "text", "scroll-home" -> {
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
        if (::globalControlServer.isInitialized) globalControlServer.close()
        if (::linkServer.isInitialized) linkServer.close()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
