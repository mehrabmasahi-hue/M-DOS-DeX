package com.goldan.mdosdex

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.FrameLayout
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.abs

class CompanionActivity : Activity() {
    private val yellow = 0xFFFFC928.toInt()
    private val navy = 0xFF101827.toInt()
    private val panel = 0xFF1A2638.toInt()
    private val text = 0xFFF5F7FA.toInt()
    private val muted = 0xFF9CAFC4.toInt()
    private val io = Executors.newSingleThreadExecutor()
    private var baseUrl: String = ""
    private var token: String = ""
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var content: LinearLayout
    private var currentTab = "mouse"
    private var pendingPicker = "file"
    private var lastMoveAt = 0L
    private var scanAfterPermission = false

    companion object {
        private const val CAMERA_PERMISSION = 720
        private const val FILE_PICKER = 721
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        val prefs = getSharedPreferences("phone_link", MODE_PRIVATE)
        baseUrl = prefs.getString("url", "") ?: ""
        token = prefs.getString("token", "") ?: ""
        showHome()
        if (baseUrl.isNotBlank() && token.isNotBlank()) verifyConnection()
    }

    private fun showHome(message: String? = null) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(22))
            setBackgroundColor(navy)
        }
        val logo = TextView(this).apply {
            text = "↔"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(navy)
            background = rounded(yellow, 22)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(logo, LinearLayout.LayoutParams(dp(66), dp(66)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(label("LINK TO M-DOS", 24f, text, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4)) })
        root.addView(label("کنترل و اتصال بی‌سیم M-DOS", 14f, muted).apply { gravity = Gravity.CENTER })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 20)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        statusText = label(message ?: "برای اتصال، گوشی و دستگاه M-DOS را به یک شبکه Wi-Fi وصل کنید.", 14f, text)
        card.addView(statusText)
        card.addView(button("اسکن کد QR", yellow) { requestScan() }.apply { textSize = 17f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(18) })
        card.addView(button("اتصال دوباره", panel) { verifyConnection() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(30) })
        root.addView(label("در صفحه M-DOS برنامه «Phone Link» را باز کنید و QR نمایش‌داده‌شده را اسکن کنید.", 12f, muted)
            .apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) })
        setContentView(root)
    }

    private fun requestScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            scanAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
        } else startQrScanner()
    }

    private fun startQrScanner() {
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("کد QR برنامه Phone Link را اسکن کنید")
            setBeepEnabled(false)
            setOrientationLocked(false)
            initiateScan()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && scanAfterPermission) startQrScanner()
            else Toast.makeText(this, "برای اسکن QR مجوز دوربین لازم است.", Toast.LENGTH_LONG).show()
            scanAfterPermission = false
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scan != null) {
            val raw = scan.contents ?: return
            try {
                val obj = JSONObject(raw)
                val url = obj.getString("url").trimEnd('/')
                val newToken = obj.getString("token")
                val parsed = Uri.parse(url)
                require(parsed.scheme == "http" && parsed.host != null && parsed.port == 8765) { "کد QR مربوط به Phone Link نیست" }
                baseUrl = url
                token = newToken
                getSharedPreferences("phone_link", MODE_PRIVATE).edit().putString("url", baseUrl).putString("token", token).apply()
                verifyConnection()
            } catch (e: Exception) {
                Toast.makeText(this, "کد QR نامعتبر است: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == FILE_PICKER && resultCode == RESULT_OK) data?.data?.let { uploadSelectedFile(it, pendingPicker) }
    }

    private fun verifyConnection() {
        if (baseUrl.isBlank() || token.isBlank()) {
            showHome("هنوز دستگاهی متصل نشده است. ابتدا QR را اسکن کنید.")
            return
        }
        statusTextSafe("در حال بررسی اتصال به M-DOS...")
        io.execute {
            val ok = try { request("GET", "/ping").optBoolean("ok") } catch (_: Exception) { false }
            runOnUiThread {
                if (ok) showConnected() else {
                    getSharedPreferences("phone_link", MODE_PRIVATE).edit().clear().apply()
                    baseUrl = ""; token = ""
                    showHome("اتصال برقرار نشد. هر دو دستگاه باید روی یک Wi-Fi باشند و صفحه M-DOS باز باشد.")
                }
            }
        }
    }

    private fun statusTextSafe(value: String) {
        runOnUiThread { if (::statusText.isInitialized) statusText.text = value }
    }

    private fun showConnected() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(navy) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(10))
            setBackgroundColor(navy)
        }
        header.addView(label("LINK TO M-DOS", 19f, yellow, true))
        header.addView(label("متصل به ${Uri.parse(baseUrl).host}", 12f, muted).apply { setPadding(0, dp(4), 0, 0) })
        root.addView(header)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(panel)
        }
        listOf("mouse" to "ماوس", "apps" to "برنامه‌ها", "files" to "فایل‌ها", "control" to "کنترل").forEach { (id, title) ->
            val b = button(title, if (currentTab == id) yellow else panel) { currentTab = id; renderTab() }
            b.textSize = 12f
            nav.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        root.addView(nav)
        setContentView(root)
        renderTab()
    }

    private fun renderTab() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        when (currentTab) {
            "mouse" -> renderMouse()
            "apps" -> renderApps()
            "files" -> renderFiles()
            else -> renderController()
        }
    }

    private fun renderMouse() {
        content.addView(label("ماوس لمسی", 18f, text, true))
        content.addView(label("انگشت را روی صفحه حرکت دهید؛ لمس کوتاه کلیک چپ و نگه‌داشتن، کلیک راست است.", 12f, muted)
            .apply { setPadding(0, dp(4), 0, dp(10)) })
        content.addView(button("⛶ تاچ‌پد تمام‌صفحه", panel) { showFullScreenPad() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { bottomMargin = dp(8) })
        val pad = TouchPadView()
        content.addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val clicks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clicks.addView(button("کلیک چپ", yellow) { sendInput(JSONObject().put("type", "tap")) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        clicks.addView(button("کلیک راست", panel) { sendInput(JSONObject().put("type", "right")) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        content.addView(clicks, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })
        val drag = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        drag.addView(button("شروع کشیدن", panel) { sendInput(JSONObject().put("type", "down")) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        drag.addView(button("رها کردن", panel) { sendInput(JSONObject().put("type", "up")) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(8) })
        content.addView(drag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(7) })
        content.addView(button("اسکرول به بالا", panel) { sendInput(JSONObject().put("type", "scroll").put("dy", -460)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })
        content.addView(button("اسکرول به پایین", panel) { sendInput(JSONObject().put("type", "scroll").put("dy", 460)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })
    }

    private fun showFullScreenPad() {
        val frame = FrameLayout(this).apply { setBackgroundColor(navy) }
        frame.addView(TouchPadView(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val close = button("✕ بستن", yellow) { showConnected() }
        frame.addView(close, FrameLayout.LayoutParams(dp(110), dp(48), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(14); rightMargin = dp(14)
        })
        val left = button("کلیک چپ", panel) { sendInput(JSONObject().put("type", "tap")) }
        frame.addView(left, FrameLayout.LayoutParams(dp(120), dp(48), Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = dp(18); leftMargin = dp(18)
        })
        val right = button("کلیک راست", panel) { sendInput(JSONObject().put("type", "right")) }
        frame.addView(right, FrameLayout.LayoutParams(dp(120), dp(48), Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = dp(18); rightMargin = dp(18)
        })
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        setContentView(frame)
    }

    private inner class TouchPadView : View(this@CompanionActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var downAt = 0L
        init { isClickable = true; background = rounded(0xFF24344A.toInt(), 18) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            p.color = 0xFF7187A3.toInt(); p.textSize = dp(15).toFloat(); p.textAlign = Paint.Align.CENTER
            canvas.drawText("صفحه کنترل ماوس", width / 2f, height / 2f - dp(5), p)
            p.color = 0xFF9CAFC4.toInt(); p.textSize = dp(11).toFloat()
            canvas.drawText("TOUCH PAD", width / 2f, height / 2f + dp(20), p)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; moved = false; downAt = System.currentTimeMillis(); return true }
                MotionEvent.ACTION_POINTER_DOWN -> { moved = true; return true }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val dy = (event.y - downY) * 5f
                        downY = event.y
                        sendInput(JSONObject().put("type", "scroll").put("dy", dy))
                        moved = true
                    } else {
                        val dx = event.x - downX; val dy = event.y - downY
                        if (abs(dx) + abs(dy) > 1f) moved = true
                        val now = System.currentTimeMillis()
                        if (now - lastMoveAt > 12) {
                            sendInput(JSONObject().put("type", "move").put("dx", dx * 1.85f).put("dy", dy * 1.85f))
                            lastMoveAt = now
                        }
                        downX = event.x; downY = event.y
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) sendInput(JSONObject().put("type", if (System.currentTimeMillis() - downAt > 550) "right" else "tap"))
                    performClick(); return true
                }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    private fun renderApps() {
        val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title.addView(label("برنامه‌های M-DOS", 18f, text, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        title.addView(button("＋ برنامه جدید", yellow) { pickFile("app") })
        content.addView(title)
        content.addView(label("فایل LLMPK را انتخاب کنید؛ پس از انتقال، در M-DOS بررسی، نصب و اجرا می‌شود.", 12f, muted)
            .apply { setPadding(0, dp(8), 0, dp(10)) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this); scroll.addView(list)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        io.execute {
            val apps = try { requestArray("GET", "/apps") } catch (_: Exception) { JSONArray() }
            runOnUiThread {
                if (!list.isAttachedToWindow) return@runOnUiThread
                if (apps.length() == 0) list.addView(label("هنوز بسته LLMPK منتقل نشده است.", 13f, muted).apply { setPadding(0, dp(18), 0, 0) })
                for (i in 0 until apps.length()) {
                    val app = apps.getJSONObject(i)
                    val row = card()
                    row.addView(label("📦  ${app.optString("name")}", 15f, text, true))
                    row.addView(label("${app.optString("id")}  ·  نسخه ${app.optString("version")}", 11f, muted).apply { setPadding(0, dp(5), 0, dp(8)) })
                    row.addView(button("حذف برنامه", 0xFF653D48.toInt()) { removeApp(app.optString("id")) })
                    list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
                }
            }
        }
    }

    private fun renderFiles() {
        content.addView(label("فایل‌ها و رسانه‌ها", 18f, text, true))
        content.addView(label("هر فایل پس از انتقال در حافظه داخلی M-DOS ذخیره می‌شود و از برنامه Phone Link قابل مشاهده و دریافت است.", 12f, muted)
            .apply { setPadding(0, dp(5), 0, dp(12)) })
        content.addView(button("＋ ارسال هر نوع فایل", yellow) { pickFile("file") }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(button("ارسال عکس‌ها", panel) { pickFile("image") }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(7) })
        content.addView(button("ارسال ویدئوها", panel) { pickFile("video") }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(7) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this); scroll.addView(list)
        content.addView(label("فایل‌های منتقل‌شده", 15f, text, true).apply { setPadding(0, dp(18), 0, dp(8)) })
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        io.execute {
            val files = try { requestArray("GET", "/files") } catch (_: Exception) { JSONArray() }
            runOnUiThread {
                if (!list.isAttachedToWindow) return@runOnUiThread
                if (files.length() == 0) list.addView(label("هنوز فایلی انتقال داده نشده است.", 12f, muted))
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(panel, 12); setPadding(dp(10), dp(8), dp(10), dp(8)) }
                    row.addView(label("${f.optString("name")}\n${formatBytes(f.optLong("size"))}", 12f, text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(button("حذف", 0xFF653D48.toInt()) { removeFile(f.optString("name")) })
                    list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
                }
            }
        }
    }

    private fun renderController() {
        content.addView(label("کنترل کامل", 18f, text, true))
        content.addView(label("کلیدها و فرمان‌های پرکاربرد را مستقیماً به M-DOS بفرستید.", 12f, muted).apply { setPadding(0, dp(5), 0, dp(12)) })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun keyRow(vararg keys: Pair<String, String>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEach { (title, key) -> row.addView(button(title, panel) { sendInput(JSONObject().put("type", "key").put("key", key)) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) }) }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(7) })
        }
        keyRow("Esc" to "Escape", "Tab" to "Tab", "Enter" to "Enter", "⌫" to "Backspace")
        keyRow("↑" to "ArrowUp", "↓" to "ArrowDown", "←" to "ArrowLeft", "→" to "ArrowRight")
        keyRow("Home" to "Home", "End" to "End", "PgUp" to "PageUp", "PgDn" to "PageDown")
        content.addView(grid)
        val entry = EditText(this).apply {
            hint = "متن یا فرمان را وارد کنید"
            setHintTextColor(muted); setTextColor(this@CompanionActivity.text); textSize = 15f
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_SEND
            background = rounded(panel, 12); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        fun sendText() { val value = entry.text.toString(); if (value.isNotEmpty()) { sendInput(JSONObject().put("type", "text").put("text", value)); entry.setText("") } }
        entry.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) { sendText(); true } else false }
        content.addView(entry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(14) })
        content.addView(button("ارسال متن", yellow) { sendText() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(7) })
        content.addView(button("بازگشت به اتصال", panel) { currentTab = "mouse"; renderTab() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(16) })
    }

    private fun pickFile(kind: String) {
        pendingPicker = kind
        val mime = when (kind) { "image" -> "image/*"; "video" -> "video/*"; else -> "*/*" }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = mime; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivityForResult(intent, FILE_PICKER)
    }

    private fun uploadSelectedFile(uri: Uri, kind: String) {
        Toast.makeText(this, "در حال آماده‌سازی فایل...", Toast.LENGTH_SHORT).show()
        io.execute {
            var temp: File? = null
            try {
                val name = displayName(uri)
                if (kind == "app" && !name.endsWith(".llmpk", true)) throw IllegalArgumentException("فقط فایل با پسوند .llmpk قابل نصب است")
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                temp = File(cacheDir, "upload-${System.currentTimeMillis()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                contentResolver.openInputStream(uri)?.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                    ?: throw IllegalArgumentException("خواندن فایل ممکن نشد")
                if (temp.length() > 100L * 1024L * 1024L) throw IllegalArgumentException("حجم فایل بیشتر از ۱۰۰ مگابایت است")
                val url = URL("$baseUrl/upload")
                val c = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 10_000; readTimeout = 60_000
                    setRequestProperty("X-MDOS-Token", token)
                    setRequestProperty("X-File-Name", URLEncoder.encode(name, "UTF-8"))
                    setRequestProperty("X-File-Mime", mime)
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setFixedLengthStreamingMode(temp.length())
                }
                temp.inputStream().use { source -> c.outputStream.use { target -> source.copyTo(target) } }
                val response = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
                val result = JSONObject(response)
                if (!result.optBoolean("ok")) throw IllegalArgumentException(result.optString("error", "انتقال ناموفق بود"))
                runOnUiThread { Toast.makeText(this, if (kind == "app") "بسته ارسال شد؛ نصب و اجرا در M-DOS انجام می‌شود." else "فایل به M-DOS منتقل شد.", Toast.LENGTH_LONG).show(); renderTab() }
                c.disconnect()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, e.message ?: "انتقال فایل ناموفق بود", Toast.LENGTH_LONG).show() }
            } finally { temp?.delete() }
        }
    }

    private fun displayName(uri: Uri): String {
        var result = "file-${System.currentTimeMillis()}"
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) result = it.getString(0) ?: result }
        return result
    }

    private fun removeApp(id: String) {
        io.execute { try { request("DELETE", "/apps?id=" + URLEncoder.encode(id, "UTF-8")); runOnUiThread { Toast.makeText(this, "برنامه حذف شد.", Toast.LENGTH_SHORT).show(); renderTab() } } catch (e: Exception) { toastError(e) } }
    }
    private fun removeFile(name: String) {
        io.execute { try { request("DELETE", "/files?name=" + URLEncoder.encode(name, "UTF-8")); runOnUiThread { renderTab() } } catch (e: Exception) { toastError(e) } }
    }

    private fun sendInput(data: JSONObject) {
        io.execute { try { request("POST", "/input", data.toString()) } catch (_: Exception) { } }
    }

    private fun request(method: String, path: String, body: String? = null): JSONObject {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 4_000; readTimeout = 7_000
            setRequestProperty("X-MDOS-Token", token)
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val result = stream.bufferedReader().use { JSONObject(it.readText()) }
        val code = c.responseCode
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException(result.optString("error", "خطای ارتباط با M-DOS ($code)"))
        return result
    }

    private fun requestArray(method: String, path: String): JSONArray {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 5_000; readTimeout = 8_000; setRequestProperty("X-MDOS-Token", token)
        }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val result = JSONArray(stream.bufferedReader().use { it.readText() })
        c.disconnect(); return result
    }

    private fun toastError(e: Exception) = runOnUiThread { Toast.makeText(this, e.message ?: "خطای ارتباط", Toast.LENGTH_LONG).show() }
    private fun card(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(panel, 14); setPadding(dp(12), dp(12), dp(12), dp(12)) }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, color: Int, action: () -> Unit): Button = Button(this).apply {
        text = value; setTextColor(if (color == yellow) navy else this@CompanionActivity.text); textSize = 14f
        isAllCaps = false; background = rounded(color, 12); setOnClickListener { action() }
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun formatBytes(size: Long): String = when { size < 1024 -> "$size B"; size < 1024 * 1024 -> "${size / 1024} KB"; else -> "${size / (1024 * 1024)} MB" }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }
}
