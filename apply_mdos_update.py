#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit('Usage: python apply_mdos_update.py /path/to/M-DOS-DeX-repo')
repo = Path(sys.argv[1]).expanduser().resolve()
if not (repo / '.git').is_dir():
    raise SystemExit(f'Git repository not found: {repo}')

def read(rel):
    p = repo / rel
    if not p.is_file():
        raise SystemExit(f'Missing source file: {rel}')
    return p.read_text(encoding='utf-8')

def write(rel, value):
    p = repo / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(value, encoding='utf-8')

def replace_once(source, old, new, label):
    count = source.count(old)
    if count != 1:
        raise SystemExit(f'Patch stopped: expected one match for {label}, found {count}. No partial write was made.')
    return source.replace(old, new, 1)

# Load everything first. Only write after every patch validates.
paths = {
    'main': 'app/src/main/java/com/goldan/mdosdex/MainActivity.kt',
    'bridge': 'app/src/main/java/com/goldan/mdosdex/PhoneLinkBridge.kt',
    'phone': 'app/src/phone/java/com/goldan/mdosdex/WifiCompanionActivity.kt',
    'html': 'app/src/main/assets/index.html',
    'manifest': 'app/src/main/AndroidManifest.xml',
    'gradle': 'app/build.gradle.kts',
}
source = {key: read(rel) for key, rel in paths.items()}
main, phone, html, manifest, gradle = (source[k] for k in ('main','phone','html','manifest','gradle'))

# MainActivity: hand browsing to the native Android WebView and correct the cursor direction.
if 'import android.content.Intent' not in main:
    main = replace_once(main, 'import android.app.Activity\n', 'import android.app.Activity\nimport android.content.Intent\n', 'MainActivity Intent import')
main = replace_once(
    main,
    'addJavascriptInterface(PhoneLinkBridge(linkServer), "PhoneLinkNative")',
    '''addJavascriptInterface(PhoneLinkBridge(linkServer) { address ->
                mainHandler.post {
                    startActivity(Intent(this, MdosBrowserActivity::class.java)
                        .putExtra(MdosBrowserActivity.EXTRA_URL, address))
                }
            }, "PhoneLinkNative")''',
    'native browser bridge'
)
main = replace_once(
    main,
    "cursor.textContent='➤';cursor.style.cssText='position:fixed;left:0;top:0;z-index:2147483647;pointer-events:none;color:#ffca28;font-size:28px;font-weight:900;line-height:1;text-shadow:0 1px 4px #000;transform:translate('+window.__mdosRemotePointer.x+'px,'+window.__mdosRemotePointer.y+'px)'",
    "cursor.textContent='↖';cursor.style.cssText='position:fixed;left:0;top:0;z-index:2147483647;pointer-events:none;color:#ffca28;font-size:31px;font-weight:900;line-height:1;text-shadow:0 1px 4px #000;will-change:transform;transform:translate('+(window.__mdosRemotePointer.x-6)+'px,'+(window.__mdosRemotePointer.y-7)+'px)'",
    'mouse cursor arrow'
)
main = replace_once(
    main,
    "cursor.style.transform='translate('+p.x+'px,'+p.y+'px)'",
    "cursor.style.transform='translate('+(p.x-6)+'px,'+(p.y-7)+'px)'",
    'mouse cursor position'
)
main = replace_once(main, "if(c.type==='tap'||c.type==='right'){", "if(c.type==='tap'||c.type==='right'||c.type==='middle'||c.type==='double'){", 'mouse click types')
old_click = '''const e=eventTarget(),right=c.type==='right';
                    e.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:p.x,clientY:p.y,view:window}));
                    if(window.PointerEvent)e.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:right?2:0,buttons:right?2:1,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true}));
                    e.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,button:right?2:0,buttons:right?2:1,clientX:p.x,clientY:p.y,view:window}));
                    if(window.PointerEvent)e.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,cancelable:true,button:right?2:0,buttons:0,clientX:p.x,clientY:p.y,pointerId:1,pointerType:'mouse',isPrimary:true}));
                    e.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,button:right?2:0,buttons:0,clientX:p.x,clientY:p.y,view:window}));
                    if(!right)e.dispatchEvent(new MouseEvent('click',{bubbles:true,button:0,clientX:p.x,clientY:p.y,view:window}));
                    else e.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,button:2,clientX:p.x,clientY:p.y,view:window}));return true;'''
new_click = '''const e=eventTarget(),button=c.type==='right'?2:c.type==='middle'?1:0;
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
                    return true;'''
main = replace_once(main, old_click, new_click, 'left/right/middle/double click event dispatch')
main = replace_once(
    main,
    '"move", "tap", "right", "down", "up", "scroll", "key", "text", "scroll-home" -> {',
    '"move", "tap", "right", "middle", "double", "down", "up", "scroll", "key", "text", "scroll-home" -> {',
    'remote input allowlist'
)

bridge = '''package com.goldan.mdosdex

import android.webkit.JavascriptInterface

/** JavaScript bridge for Phone Link and the native M-DOS browser. */
class PhoneLinkBridge(
    private val server: PhoneLinkServer,
    private val openBrowserAction: (String) -> Unit
) {
    @JavascriptInterface fun getPairInfo(): String = server.pairInfo().toString()
    @JavascriptInterface fun getFilesJson(): String = server.filesJson()
    @JavascriptInterface fun getAppsJson(): String = server.appsJson()
    @JavascriptInterface fun getFileUrl(name: String): String = server.localFileUrl(name)
    @JavascriptInterface fun readTextFile(name: String): String = server.readTextFile(name)
    @JavascriptInterface fun readFileBase64(name: String): String = server.readFileBase64(name)
    @JavascriptInterface fun deleteFile(name: String): Boolean = server.deleteFile(name)
    @JavascriptInterface fun openBrowser(address: String): Boolean {
        val value = address.trim().take(2048)
        if (value.isBlank()) return false
        openBrowserAction(value)
        return true
    }
    @JavascriptInterface fun getQrDataUrl(): String = ""
}
'''

# Link to M-DOS: scrollable content, coalesced pointer requests and additional mouse controls.
phone = replace_once(phone, '    private var lastMoveAt = 0L\n', '''    private var lastMoveAt = 0L
    private var mouseSpeed = 1.85f
    private val moveLock = Any()
    private var queuedMoveX = 0.0
    private var queuedMoveY = 0.0
    private var movePumpActive = false
''', 'mouse coalescing state')
phone = replace_once(
    phone,
    '''        root!!.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6)); setBackgroundColor(panel) }''',
    '''        val contentScroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root!!.addView(contentScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6)); setBackgroundColor(panel) }''',
    'connected screen scroll container'
)
phone = replace_once(phone, 'content.addView(TouchPadView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))', 'content.addView(TouchPadView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))', 'touchpad scroll height')
phone = replace_once(
    phone,
    '''        content.addView(drag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(7) })
    }

    private fun showFullScreenPad()''',
    '''        content.addView(drag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(7) })
        val extras = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clickRow.addView(button("کلیک وسط", panel) { sendInput(JSONObject().put("type", "middle")) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        clickRow.addView(button("دوبار کلیک", panel) { sendInput(JSONObject().put("type", "double")) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(7) })
        extras.addView(clickRow)
        val scrollRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scrollRow.addView(button("▲ پیمایش بالا", panel) { sendInput(JSONObject().put("type", "scroll").put("dy", -420)) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        scrollRow.addView(button("▼ پیمایش پایین", panel) { sendInput(JSONObject().put("type", "scroll").put("dy", 420)) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(7) })
        extras.addView(scrollRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(7) })
        val speedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        speedRow.addView(label("سرعت نشانگر", 12f, muted), LinearLayout.LayoutParams(0, dp(42), 1f))
        speedRow.addView(button("−", panel) { mouseSpeed = (mouseSpeed - 0.25f).coerceAtLeast(0.75f); toastSpeed() }, LinearLayout.LayoutParams(dp(52), dp(40)))
        speedRow.addView(button("+", panel) { mouseSpeed = (mouseSpeed + 0.25f).coerceAtMost(3.5f); toastSpeed() }, LinearLayout.LayoutParams(dp(52), dp(40)).apply { leftMargin = dp(6) })
        extras.addView(speedRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(7) })
        content.addView(extras, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    private fun toastSpeed() = Toast.makeText(this, "سرعت ماوس: " + mouseSpeed + "×", Toast.LENGTH_SHORT).show()

    private fun showFullScreenPad()''',
    'mouse features'
)
phone = replace_once(
    phone,
    '''                        val now = System.currentTimeMillis()
                        if (now - lastMoveAt > 22) {
                            sendInput(JSONObject().put("type", "move").put("dx", dx * 1.85f).put("dy", dy * 1.85f))
                            lastMoveAt = now
                        }
                        downX = event.x; downY = event.y''',
    '''                        if (abs(dx) + abs(dy) > 0.25f) {
                            sendInput(JSONObject().put("type", "move").put("dx", dx * mouseSpeed).put("dy", dy * mouseSpeed))
                        }
                        downX = event.x; downY = event.y''',
    'drop fewer touch movements'
)
phone = replace_once(
    phone,
    '''        val scroll = ScrollView(this).apply { addView(list) }
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))''',
    '''        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))''',
    'apps list outer scrolling'
)
phone = replace_once(
    phone,
    '''        content.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))''',
    '''        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))''',
    'files list outer scrolling'
)
start = phone.find('    private fun renderController() {')
end = phone.find('    private fun pickFile(kind: String) {', start)
if start < 0 or end < 0:
    raise SystemExit('Could not locate phone controller method')
controller = '''    private fun renderController() {
        content.addView(label("کنترل صفحه", 18f, textColor, true))
        content.addView(label("کلیدها و متن را به محیط M-DOS بفرست.", 11f, muted).apply { setPadding(0, dp(5), 0, dp(10)) })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun keyRow(vararg keys: Pair<String, String>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEach { (title, key) -> row.addView(button(title, panel) {
                sendInput(JSONObject().put("type", "key").put("key", key))
            }, LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) }) }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)).apply { bottomMargin = dp(6) })
        }
        keyRow("Esc" to "Escape", "Tab" to "Tab", "Enter" to "Enter", "⌫" to "Backspace")
        keyRow("↑" to "ArrowUp", "↓" to "ArrowDown", "←" to "ArrowLeft", "→" to "ArrowRight")
        keyRow("Home" to "Home", "End" to "End", "PgUp" to "PageUp", "PgDn" to "PageDown")
        content.addView(grid)

        val entry = EditText(this).apply {
            hint = "متن یا فرمان را وارد کن"; setHintTextColor(muted); setTextColor(textColor); textSize = 14f
            minLines = 2; maxLines = 4; imeOptions = EditorInfo.IME_ACTION_SEND
            background = rounded(panel, 12); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        fun sendText() {
            val value = entry.text.toString()
            if (value.isNotEmpty()) { sendInput(JSONObject().put("type", "text").put("text", value)); entry.setText("") }
        }
        entry.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendText(); true } else false
        }
        content.addView(entry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)).apply { topMargin = dp(10) })

        var persian = true
        var buildKeyboard: (() -> Unit)? = null
        val keyboardHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        keyboardHeader.addView(label("صفحه‌کلید داخلی", 14f, textColor, true), LinearLayout.LayoutParams(0, dp(42), 1f))
        keyboardHeader.addView(button("فارسی  |  English", yellow) { persian = !persian; buildKeyboard?.invoke() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        content.addView(keyboardHeader, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })
        val keyboardRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(5), dp(3), dp(5)); background = rounded(0xFF172337.toInt(), 12)
        }
        content.addView(keyboardRows, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun insertKey(value: String) {
            val startPos = entry.selectionStart.coerceAtLeast(0)
            val endPos = entry.selectionEnd.coerceAtLeast(startPos)
            when (value) {
                "⌫" -> {
                    val from = if (startPos == endPos && startPos > 0) startPos - 1 else minOf(startPos, endPos)
                    val to = maxOf(startPos, endPos)
                    if (to > from) entry.text.delete(from, to)
                    entry.setSelection(from)
                }
                "↵" -> sendText()
                else -> {
                    entry.text.replace(minOf(startPos, endPos), maxOf(startPos, endPos), value)
                    entry.setSelection(minOf(startPos, endPos) + value.length)
                }
            }
        }
        buildKeyboard = {
            keyboardRows.removeAllViews()
            val chars = if (persian) listOf("۱۲۳۴۵۶۷۸۹۰", "ضصثقفغعهخحجچ", "شسیبلاتنمکگ", "ظطزرذدپو")
                else listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
            chars.forEach { line ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                line.forEach { ch ->
                    val key = TextView(this).apply {
                        text = ch.toString(); gravity = Gravity.CENTER; textSize = 13f; setTextColor(textColor)
                        background = rounded(panel, 7); minWidth = 0; setPadding(0, dp(4), 0, dp(4))
                        setOnClickListener { insertKey(ch.toString()) }
                    }
                    row.addView(key, LinearLayout.LayoutParams(0, dp(39), 1f).apply {
                        leftMargin = dp(2); rightMargin = dp(2); bottomMargin = dp(4)
                    })
                }
                keyboardRows.addView(row)
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("⌫" to "حذف", " " to "فاصله", "↵" to "ارسال").forEach { (keyValue, caption) ->
                actions.addView(button(caption, if (caption == "ارسال") yellow else panel) { insertKey(keyValue) },
                    LinearLayout.LayoutParams(0, dp(42), if (caption == "فاصله") 2f else 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
            }
            keyboardRows.addView(actions)
        }
        buildKeyboard?.invoke()
        content.addView(button("ارسال متن به M-DOS", yellow) { sendText() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        content.addView(button("بازگشت به ماوس", panel) { currentTab = "mouse"; renderTab() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(12) })
    }

'''
phone = phone[:start] + controller + phone[end:]
ss = phone.find('    private fun sendInput(data: JSONObject) {')
se = phone.find('    private fun request(method: String', ss)
if ss < 0 or se < 0:
    raise SystemExit('Could not locate phone sendInput method')
new_send = '''    private fun sendInput(data: JSONObject) {
        if (data.optString("type") == "move") {
            var startWorker = false
            synchronized(moveLock) {
                queuedMoveX += data.optDouble("dx", 0.0)
                queuedMoveY += data.optDouble("dy", 0.0)
                if (!movePumpActive) { movePumpActive = true; startWorker = true }
            }
            if (startWorker) io.execute {
                while (true) {
                    val payload = synchronized(moveLock) {
                        if (kotlin.math.abs(queuedMoveX) + kotlin.math.abs(queuedMoveY) < 0.01) {
                            movePumpActive = false
                            null
                        } else {
                            val x = queuedMoveX; val y = queuedMoveY
                            queuedMoveX = 0.0; queuedMoveY = 0.0
                            JSONObject().put("type", "move").put("dx", x).put("dy", y)
                        }
                    } ?: break
                    try { request("POST", "/input", payload.toString()) } catch (_: Exception) { }
                    try { Thread.sleep(24) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        synchronized(moveLock) { movePumpActive = false }
                        break
                    }
                }
            }
            return
        }
        io.execute { try { request("POST", "/input", data.toString()) } catch (_: Exception) { } }
    }

'''
phone = phone[:ss] + new_send + phone[se:]

# Browser window now opens pages in a real WebView instead of an iframe that websites can block.
w0 = html.find('function web(el,k,arg){')
w1 = html.find('\nasync function sec(el){', w0)
if w0 < 0 or w1 < 0:
    raise SystemExit('Could not locate M-DOS browser function')
new_web = r'''function web(el,k,arg){
 el.style.cssText+=';display:flex;flex-direction:column;gap:10px;padding:12px;overflow:auto';
 const head=h('div','card','<b class="big">🌐</b><div><b>مرورگر M-DOS</b><br><span class="mu">مرورگر داخلی Android WebView · موتور جستجوی پیش‌فرض: Google</span></div>');
 const note=h('p','mu','نشانی سایت یا عبارت جستجو را وارد کن. صفحات در WebView داخلی Android باز می‌شوند و به iframe محدود نیستند.');
 const bar=h('div','row');bar.style.cssText='display:flex;gap:7px;align-items:center';
 const input=h('input','in');input.type='text';input.dir='ltr';input.autocomplete='url';input.placeholder='https://example.com یا جستجو در Google';input.style.cssText='flex:1;min-width:0;width:auto';
 const open=s=>{
  const value=String(s||'').trim();if(!value)return;
  if(window.PhoneLinkNative&&typeof window.PhoneLinkNative.openBrowser==='function')window.PhoneLinkNative.openBrowser(value);
  else{
   const isUrl=/^(https?:\/\/)/i.test(value)||(/^[\w.-]+\.[a-z]{2,}(\/[^\s]*)?$/i.test(value));
   window.open(isUrl?(/^https?:\/\//i.test(value)?value:'https://'+value):'https://www.google.com/search?q='+encodeURIComponent(value),'_blank');
  }
 };
 const go=h('button','btn','برو');go.onclick=()=>open(input.value);
 input.onkeydown=e=>{if(e.key==='Enter'){open(input.value);e.preventDefault()}};
 bar.append(input,go);
 const home=h('div','card');home.style.cssText+=';display:block';
 const quick=h('div','row');
 [['Google','https://www.google.com'],['YouTube','https://www.youtube.com'],['Wikipedia','https://www.wikipedia.org'],['GitHub','https://github.com'],['MDN','https://developer.mozilla.org'],['OWASP','https://owasp.org']].forEach(([name,url])=>{
  const b=h('button','btn s',name);b.onclick=()=>open(url);quick.append(b);
 });
 const google=h('button','btn','🔎 جستجوی Google');google.onclick=()=>open(input.value||'https://www.google.com');
 const googleHome=h('button','btn s','صفحه اصلی Google');googleHome.onclick=()=>open('https://www.google.com');
 home.append(h('h3','','شروع وب‌گردی'),h('p','mu','جستجوها به‌صورت پیش‌فرض در Google انجام می‌شوند.'),quick,google,googleHome);
 el.append(head,note,bar,home);
 if(arg){input.value=String(arg);setTimeout(()=>open(arg),120)}else setTimeout(()=>input.focus(),50);
}'''
html = html[:w0] + new_web + html[w1:]

manifest = replace_once(
    manifest,
    '    </application>\n</manifest>',
    '''        <activity android:name=".MdosBrowserActivity" android:exported="false"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize" />
    </application>
</manifest>''',
    'register native browser activity'
)

browser_activity = r'''package com.goldan.mdosdex

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

class MdosBrowserActivity : Activity() {
    companion object { const val EXTRA_URL = "mdos.browser.url" }
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var pageState: TextView
    private val navy = Color.rgb(16, 24, 39)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6)); setBackgroundColor(navy)
        }
        fun toolButton(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
            background = rounded(Color.rgb(36, 52, 74), 9); minWidth = dp(42)
            setPadding(dp(5), 0, dp(5), 0); setOnClickListener { action() }
        }
        val back = toolButton("‹") { if (browser.canGoBack()) browser.goBack() }
        val forward = toolButton("›") { if (browser.canGoForward()) browser.goForward() }
        val reload = toolButton("⟳") { browser.reload() }
        address = EditText(this).apply {
            hint = "نشانی سایت یا جستجو در Google"; textSize = 14f; singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO; setTextColor(Color.WHITE); setHintTextColor(0xFFBAC5D3.toInt())
            background = rounded(Color.rgb(36, 52, 74), 10); setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        val go = toolButton("برو") { navigate(address.text.toString()) }
        val home = toolButton("⌂") { browser.loadUrl("https://www.google.com") }
        listOf(back, forward, reload).forEach { toolbar.addView(it, LinearLayout.LayoutParams(dp(42), dp(44)).apply { rightMargin = dp(4) }) }
        toolbar.addView(address, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        toolbar.addView(go, LinearLayout.LayoutParams(dp(54), dp(44)).apply { rightMargin = dp(4) })
        toolbar.addView(home, LinearLayout.LayoutParams(dp(42), dp(44)))
        root.addView(toolbar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        pageState = TextView(this).apply {
            text = "مرورگر داخلی M-DOS · جستجو با Google"; textSize = 11f
            setTextColor(0xFF526174.toInt()); setPadding(dp(10), dp(3), dp(10), dp(3)); maxLines = 1
        }
        root.addView(pageState, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)))
        browser = WebView(this)
        browser.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            useWideViewPort = true; loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true; setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false; allowContentAccess = true; allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT; builtInZoomControls = true; displayZoomControls = false
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(browser, true) }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
                catch (_: Exception) {
                    Toast.makeText(this@MdosBrowserActivity, "این پیوند در دستگاه پشتیبانی نمی‌شود.", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageState.text = "در حال بارگذاری صفحه…"
                if (!address.hasFocus()) address.setText(url.orEmpty())
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!address.hasFocus()) address.setText(url.orEmpty())
                pageState.text = view?.title?.takeIf { it.isNotBlank() } ?: url.orEmpty()
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) pageState.text = "بارگذاری ناموفق: " + (error?.description ?: "خطای شبکه")
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) pageState.text = title
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(address.text.toString()); true
            } else false
        }
        navigate(intent.getStringExtra(EXTRA_URL).orEmpty().ifBlank { "https://www.google.com" })
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        val target = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            !value.contains(Regex("\\\\s")) && value.contains('.') &&
                value.matches(Regex("[\\\\p{L}0-9.-]+(:[0-9]+)?(/[^\\\\s]*)?")) -> "https://$value"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
        }
        address.setText(target); browser.loadUrl(target)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() { if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else finish() }
    override fun onDestroy() {
        if (::browser.isInitialized) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading(); browser.loadUrl("about:blank"); browser.destroy()
        }
        super.onDestroy()
    }
}
'''

# Version increment for the new paired APK build.
gradle = replace_once(gradle, 'versionCode = 4', 'versionCode = 5', 'versionCode')
gradle = replace_once(gradle, 'versionName = "1.3.0"', 'versionName = "1.4.0"', 'versionName')

updated = {
    paths['main']: main,
    paths['bridge']: bridge,
    paths['phone']: phone,
    paths['html']: html,
    paths['manifest']: manifest,
    paths['gradle']: gradle,
    'app/src/main/java/com/goldan/mdosdex/MdosBrowserActivity.kt': browser_activity,
}
# Final sanity guards before writing anything.
assert "MdosBrowserActivity" in main and "cursor.textContent='↖'" in main
assert "صفحه‌کلید داخلی" in phone and "queuedMoveX" in phone and "contentScroll" in phone
assert "function web(el,k,arg)" in html and "جستجوی Google" in html
assert "MdosBrowserActivity" in manifest
assert "versionCode = 5" in gradle

backup_dir = Path.home() / ('M-DOS-DeX-backup-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
backup_dir.mkdir(parents=True, exist_ok=True)
for rel, content in updated.items():
    dest = repo / rel
    if dest.exists():
        b = backup_dir / rel
        b.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(dest, b)
for rel, content in updated.items():
    write(rel, content)
print('Updated files:')
for rel in updated:
    print(' - ' + rel)
print('Backups saved outside repository: ' + str(backup_dir))
