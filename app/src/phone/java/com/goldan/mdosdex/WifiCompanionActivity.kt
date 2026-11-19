package com.goldan.mdosdex

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs

/** Phone companion: NSD discovery, one-time numeric pairing, then saved automatic reconnection. */
class WifiCompanionActivity : Activity() {
    private val yellow = 0xFFFFC928.toInt()
    private val navy = 0xFF101827.toInt()
    private val panel = 0xFF1A2638.toInt()
    private val textColor = 0xFFF5F7FA.toInt()
    private val muted = 0xFF9CAFC4.toInt()
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("phone_link", MODE_PRIVATE) }
    private lateinit var nsd: NsdManager
    private lateinit var wifiManager: WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var baseUrl = ""
    private var token = ""
    private var savedDeviceId = ""
    private var phoneId = ""
    private var root: LinearLayout? = null
    private var statusText: TextView? = null
    private var deviceList: LinearLayout? = null
    private lateinit var content: LinearLayout
    private var currentTab = "mouse"
    private var pendingPicker = "file"
    private var lastMoveAt = 0L
    private var mouseSpeed = 1.85f
    private val moveLock = Any()
    private var queuedMoveX = 0.0
    private var queuedMoveY = 0.0
    private var movePumpActive = false
    private var autoConnectTried = false
    private val found = linkedMapOf<String, Device>()
    private val resolving = mutableSetOf<String>()

    private data class Device(val id: String, val name: String, val url: String)

    companion object {
        private const val SERVICE_TYPE = "_mdoslink._tcp."
        private const val FILE_PICKER = 721
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(true)
        nsd = getSystemService(NSD_SERVICE) as NsdManager
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        phoneId = prefs.getString("phone_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("phone_id", it).apply()
        }
        savedDeviceId = prefs.getString("device_id", "") ?: ""
        token = prefs.getString("token", "") ?: ""
        baseUrl = prefs.getString("url", "") ?: ""
        showHome()
        discoverDevices()
    }

    private fun showHome(message: String? = null) {
        stopDiscovery()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
            setBackgroundColor(navy)
        }
        val logo = TextView(this).apply {
            text = "↔"; textSize = 31f; gravity = Gravity.CENTER
            setTextColor(navy); background = rounded(yellow, 22); typeface = Typeface.DEFAULT_BOLD
        }
        root!!.addView(logo, LinearLayout.LayoutParams(dp(62), dp(62)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root!!.addView(label("LINK TO M-DOS", 23f, textColor, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(3))
        })
        root!!.addView(label("اتصال بی‌سیم و کنترل M-DOS DeX", 13f, muted).apply { gravity = Gravity.CENTER })

        val card = card()
        statusText = label(message ?: "در حال پیدا کردن تلویزیون‌های M-DOS در همین شبکه Wi-Fi…", 13f, textColor)
        card.addView(statusText)
        card.addView(button("⌕  جستجوی تلویزیون‌ها", yellow) { discoverDevices() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(14) })
        card.addView(button("اتصال دستی با IP", panel) { manualConnect() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(8) })
        if (savedDeviceId.isNotBlank() && token.isNotBlank()) {
            card.addView(label("اتصال ذخیره‌شده: ${prefs.getString("device_name", "M-DOS DeX")}", 12f, muted)
                .apply { setPadding(0, dp(10), 0, 0) })
            card.addView(button("حذف اتصال ذخیره‌شده", 0xFF653D48.toInt()) {
                AlertDialog.Builder(this).setMessage("اتصال ذخیره‌شده از این گوشی حذف شود؟")
                    .setNegativeButton("لغو", null).setPositiveButton("حذف") { _, _ ->
                        prefs.edit().remove("device_id").remove("device_name").remove("url").remove("token").apply()
                        savedDeviceId = ""; token = ""; baseUrl = ""; showHome("اتصال ذخیره‌شده حذف شد.")
                    }.show()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })
        }
        root!!.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })
        root!!.addView(label("دستگاه‌های پیدا‌شده", 15f, yellow, true).apply { setPadding(dp(4), dp(16), dp(4), dp(8)) })
        val scroll = ScrollView(this)
        val listView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        deviceList = listView
        scroll.addView(listView)
        root!!.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root!!.addView(label("برای اتصال اولیه، کد شش‌رقمی روی تلویزیون را یک بار وارد کن. اتصال بعدی در همان شبکه به‌صورت خودکار انجام می‌شود.", 11f, muted)
            .apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
        setContentView(root!!)
        renderDevices()
    }

    private fun discoverDevices() {
        stopDiscovery()
        found.clear(); resolving.clear(); autoConnectTried = false
        renderDevices()
        statusText?.text = "در حال جستجوی دستگاه‌های M-DOS در شبکه محلی…"
        try {
            multicastLock = wifiManager.createMulticastLock("M-DOS-NSD").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                runOnUiThread { statusText?.text = "جستجوی Wi-Fi فعال است؛ دستگاه‌های پیدا‌شده پایین نمایش داده می‌شوند." }
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType?.contains("_mdoslink._tcp") != true) return
                val key = serviceInfo.serviceName ?: return
                synchronized(resolving) { if (!resolving.add(key)) return }
                @Suppress("DEPRECATION")
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        synchronized(resolving) { resolving.remove(key) }
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        synchronized(resolving) { resolving.remove(key) }
                        val host = info.host as? Inet4Address ?: return
                        val id = info.attributes["id"]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() } ?: info.serviceName
                        val device = Device(id, info.serviceName ?: "M-DOS DeX", "http://${host.hostAddress}:${info.port}")
                        runOnUiThread {
                            found[id] = device
                            renderDevices()
                            if (!autoConnectTried && id == savedDeviceId && token.isNotBlank()) {
                                autoConnectTried = true
                                baseUrl = device.url
                                statusText?.text = "تلویزیون ذخیره‌شده پیدا شد؛ اتصال خودکار…"
                                verifyConnection()
                            }
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                runOnUiThread {
                    val gone = found.values.firstOrNull { it.name == serviceInfo.serviceName }
                    if (gone != null) found.remove(gone.id)
                    renderDevices()
                }
            }
            override fun onDiscoveryStopped(serviceType: String) { }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) { }
                runOnUiThread { statusText?.text = "کشف خودکار Wi-Fi شروع نشد. اتصال دستی با IP را امتحان کن." }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) { }
            }
        }
        discoveryListener = listener
        try { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (_: Exception) { statusText?.text = "جستجوی شبکه در دسترس نیست؛ اتصال دستی با IP را امتحان کن." }
    }

    private fun stopDiscovery() {
        val listener = discoveryListener
        if (listener != null) try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) { }
        discoveryListener = null
        try { multicastLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
        multicastLock = null
    }

    private fun renderDevices() {
        val list = deviceList ?: return
        list.removeAllViews()
        if (found.isEmpty()) {
            list.addView(label("هنوز دستگاهی پیدا نشده است. تلویزیون و گوشی باید به یک Wi-Fi وصل باشند و برنامه M-DOS روی تلویزیون باز باشد.", 12f, muted)
                .apply { setPadding(dp(8), dp(10), dp(8), dp(10)) })
            return
        }
        found.values.forEach { device ->
            val row = card()
            row.addView(label("📺  ${device.name}", 15f, textColor, true))
            row.addView(label(device.url, 11f, muted).apply { setPadding(0, dp(4), 0, dp(8)) })
            val known = device.id == savedDeviceId && token.isNotBlank()
            row.addView(button(if (known) "اتصال خودکار" else "اتصال و جفت‌سازی", if (known) yellow else panel) {
                connectToDevice(device)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
    }

    private fun connectToDevice(device: Device) {
        if (device.id == savedDeviceId && token.isNotBlank()) {
            baseUrl = device.url
            verifyConnection()
        } else showPairCodeDialog(device)
    }

    private fun showPairCodeDialog(device: Device) {
        val input = EditText(this).apply {
            hint = "کد ۶ رقمی روی تلویزیون"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setHintTextColor(muted)
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
            addView(label("فقط برای اولین اتصال به این تلویزیون لازم است.", 13f, muted))
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) })
        }
        AlertDialog.Builder(this)
            .setTitle("جفت‌سازی با ${device.name}")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("اتصال دائمی") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length != 6) Toast.makeText(this, "کد باید ۶ رقم باشد.", Toast.LENGTH_LONG).show()
                else pairDevice(device, code)
            }.show()
    }

    private fun manualConnect() {
        val input = EditText(this).apply {
            hint = "192.168.1.100:8765"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(textColor); setHintTextColor(muted)
        }
        val box = LinearLayout(this).apply { setPadding(dp(22), dp(8), dp(22), dp(4)); addView(input) }
        AlertDialog.Builder(this).setTitle("آدرس تلویزیون")
            .setMessage("IP و پورت را از برنامه Phone Link روی تلویزیون بخوان.")
            .setView(box).setNegativeButton("لغو", null)
            .setPositiveButton("ادامه") { _, _ ->
                var address = input.text.toString().trim()
                if (address.isBlank()) return@setPositiveButton
                address = address.trimEnd('/')
                if (!address.startsWith("http://") && !address.startsWith("https://")) address = "http://$address"
                if (!address.contains(Regex(":\\d{2,5}$"))) address += ":8765"
                identifyManualDevice(address)
            }.show()
    }

    private fun identifyManualDevice(address: String) {
        statusText?.text = "در حال شناسایی دستگاه…"
        io.execute {
            try {
                val c = (URL(address.trimEnd('/') + "/identify").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 4500; readTimeout = 6000
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val result = JSONObject(stream.bufferedReader().use { it.readText() })
                c.disconnect()
                if (code !in 200..299 || !result.optBoolean("ok")) throw IllegalStateException("این آدرس، دستگاه M-DOS نیست.")
                val device = Device(result.optString("deviceId"), result.optString("deviceName", "M-DOS DeX"), address.trimEnd('/'))
                runOnUiThread {
                    if (device.id == savedDeviceId && token.isNotBlank()) {
                        baseUrl = device.url
                        verifyConnection()
                    } else showPairCodeDialog(device)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText?.text = e.message ?: "شناسایی دستگاه ناموفق بود."
                    Toast.makeText(this, e.message ?: "اتصال ناموفق بود", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pairDevice(device: Device, code: String) {
        statusText?.text = "در حال جفت‌سازی با ${device.name}…"
        io.execute {
            try {
                val c = (URL(device.url.trimEnd('/') + "/pair").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 7000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val payload = JSONObject().put("code", code).put("phoneId", phoneId)
                    .put("phoneName", "${Build.MANUFACTURER} ${Build.MODEL}").toString()
                c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                val result = JSONObject(stream.bufferedReader().use { it.readText() })
                val codeStatus = c.responseCode
                c.disconnect()
                if (codeStatus !in 200..299 || !result.optBoolean("ok"))
                    throw IllegalStateException(result.optString("error", "کد اتصال پذیرفته نشد"))
                val newToken = result.getString("token")
                val newDeviceId = result.getString("deviceId")
                val newName = result.optString("deviceName", device.name)
                prefs.edit().putString("device_id", newDeviceId).putString("device_name", newName)
                    .putString("token", newToken).putString("url", device.url.trimEnd('/')).apply()
                runOnUiThread {
                    savedDeviceId = newDeviceId; token = newToken; baseUrl = device.url.trimEnd('/')
                    statusText?.text = "جفت‌سازی ذخیره شد؛ اتصال در دفعات بعد بدون کد انجام می‌شود."
                    verifyConnection()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText?.text = e.message ?: "جفت‌سازی ناموفق بود."
                    Toast.makeText(this, e.message ?: "اتصال ناموفق بود", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun verifyConnection() {
        if (baseUrl.isBlank() || token.isBlank()) {
            showHome("هنوز دستگاهی جفت نشده است. ابتدا تلویزیون را جستجو کن.")
            return
        }
        statusText?.text = "در حال بررسی اتصال به M-DOS…"
        io.execute {
            val ok = try { request("GET", "/ping").optBoolean("ok") } catch (_: Exception) { false }
            runOnUiThread {
                if (ok) showConnected() else {
                    // Do not erase saved credentials: when the TV returns to the LAN it can reconnect automatically.
                    showHome("تلویزیون فعلاً در دسترس نیست. اتصال ذخیره شده؛ جستجو را دوباره بزن.")
                    discoverDevices()
                }
            }
        }
    }

    private fun showConnected() {
        stopDiscovery()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(navy) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(8)); setBackgroundColor(navy)
        }
        header.addView(label("LINK TO M-DOS", 19f, yellow, true))
        header.addView(label("متصل به ${prefs.getString("device_name", "M-DOS DeX")}", 12f, muted).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(button("بازگشت به دستگاه‌ها", panel) { showHome("اتصال ذخیره شد؛ برای اتصال دوباره همین تلویزیون را انتخاب کن."); discoverDevices() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(8) })
        root!!.addView(header)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8)) }
        val contentScroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root!!.addView(contentScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6)); setBackgroundColor(panel) }
        listOf("mouse" to "ماوس", "apps" to "برنامه‌ها", "files" to "فایل‌ها", "control" to "کنترل").forEach { (id, title) ->
            nav.addView(button(title, if (currentTab == id) yellow else panel) { currentTab = id; renderTab() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        root!!.addView(nav)
        setContentView(root!!)
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
        content.addView(label("ماوس لمسی", 18f, textColor, true))
        content.addView(label("انگشت را روی صفحه حرکت بده؛ لمس کوتاه کلیک چپ و نگه‌داشتن کلیک راست است.", 12f, muted)
            .apply { setPadding(0, dp(4), 0, dp(10)) })
        content.addView(button("⛶ تاچ‌پد تمام‌صفحه", panel) { showFullScreenPad() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(8) })
        content.addView(TouchPadView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        val clicks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clicks.addView(button("کلیک چپ", yellow) { sendInput(JSONObject().put("type", "tap")) }, LinearLayout.LayoutParams(0, dp(46), 1f))
        clicks.addView(button("کلیک راست", panel) { sendInput(JSONObject().put("type", "right")) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        content.addView(clicks, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(9) })
        val drag = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        drag.addView(button("شروع کشیدن", panel) { sendInput(JSONObject().put("type", "down")) }, LinearLayout.LayoutParams(0, dp(40), 1f))
        drag.addView(button("رها کردن", panel) { sendInput(JSONObject().put("type", "up")) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(8) })
        content.addView(drag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(7) })
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

    private fun showFullScreenPad() {
        val frame = FrameLayout(this).apply { setBackgroundColor(navy) }
        frame.addView(TouchPadView(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(button("✕ بستن", yellow) { showConnected() }, FrameLayout.LayoutParams(dp(110), dp(48), Gravity.TOP or Gravity.END).apply { topMargin = dp(14); rightMargin = dp(14) })
        frame.addView(button("کلیک چپ", panel) { sendInput(JSONObject().put("type", "tap")) }, FrameLayout.LayoutParams(dp(120), dp(48), Gravity.BOTTOM or Gravity.START).apply { bottomMargin = dp(18); leftMargin = dp(18) })
        frame.addView(button("کلیک راست", panel) { sendInput(JSONObject().put("type", "right")) }, FrameLayout.LayoutParams(dp(120), dp(48), Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dp(18); rightMargin = dp(18) })
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(frame)
    }

    private inner class TouchPadView : View(this@WifiCompanionActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var downAt = 0L
        init { isClickable = true; background = rounded(0xFF24344A.toInt(), 18) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = 0xFF7187A3.toInt(); paint.textSize = dp(15).toFloat(); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("صفحه کنترل ماوس", width / 2f, height / 2f - dp(5), paint)
            paint.color = 0xFF9CAFC4.toInt(); paint.textSize = dp(11).toFloat()
            canvas.drawText("TOUCH PAD", width / 2f, height / 2f + dp(20), paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; moved = false; downAt = System.currentTimeMillis(); return true }
                MotionEvent.ACTION_POINTER_DOWN -> { moved = true; return true }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val dy = (event.y - downY) * 5f; downY = event.y
                        sendInput(JSONObject().put("type", "scroll").put("dy", dy)); moved = true
                    } else {
                        val dx = event.x - downX; val dy = event.y - downY
                        if (abs(dx) + abs(dy) > 1f) moved = true
                        if (abs(dx) + abs(dy) > 0.25f) {
                            sendInput(JSONObject().put("type", "move").put("dx", dx * mouseSpeed).put("dy", dy * mouseSpeed))
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
        title.addView(label("بسته‌های برنامه M-DOS", 17f, textColor, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        title.addView(button("＋ انتخاب LLMPK", yellow) { pickFile("app") })
        content.addView(title)
        content.addView(label("فقط فایل‌های .llmpk منتقل می‌شوند؛ نصب و اجرا در خود M-DOS انجام می‌شود.", 11f, muted).apply { setPadding(0, dp(8), 0, dp(10)) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        io.execute {
            val apps = try { requestArray("GET", "/apps") } catch (_: Exception) { JSONArray() }
            runOnUiThread {
                if (!list.isAttachedToWindow) return@runOnUiThread
                if (apps.length() == 0) list.addView(label("هنوز بسته LLMPK منتقل نشده است.", 12f, muted).apply { setPadding(0, dp(14), 0, 0) })
                for (i in 0 until apps.length()) {
                    val app = apps.getJSONObject(i); val row = card()
                    row.addView(label("📦  ${app.optString("name")}", 14f, textColor, true))
                    row.addView(label("${app.optString("id")} · نسخه ${app.optString("version")}", 11f, muted).apply { setPadding(0, dp(4), 0, dp(7)) })
                    row.addView(button("حذف بسته", 0xFF653D48.toInt()) { removeApp(app.optString("id")) })
                    list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) })
                }
            }
        }
    }

    private fun renderFiles() {
        content.addView(label("فایل‌ها و رسانه‌ها", 18f, textColor, true))
        content.addView(label("فایل‌ها در حافظه خصوصی Phone Link روی تلویزیون ذخیره می‌شوند.", 11f, muted).apply { setPadding(0, dp(5), 0, dp(10)) })
        content.addView(button("＋ ارسال هر نوع فایل", yellow) { pickFile("file") }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        val rowButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowButtons.addView(button("ارسال عکس", panel) { pickFile("image") }, LinearLayout.LayoutParams(0, dp(42), 1f))
        rowButtons.addView(button("ارسال ویدئو", panel) { pickFile("video") }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(7) })
        content.addView(rowButtons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(7) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(label("فایل‌های منتقل‌شده", 14f, textColor, true).apply { setPadding(0, dp(14), 0, dp(7)) })
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        io.execute {
            val files = try { requestArray("GET", "/files") } catch (_: Exception) { JSONArray() }
            runOnUiThread {
                if (!list.isAttachedToWindow) return@runOnUiThread
                if (files.length() == 0) list.addView(label("هنوز فایلی ارسال نشده است.", 12f, muted))
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(panel, 12); setPadding(dp(10), dp(8), dp(10), dp(8)) }
                    row.addView(label("${f.optString("name")}\n${formatBytes(f.optLong("size"))}", 11f, textColor), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(button("حذف", 0xFF653D48.toInt()) { removeFile(f.optString("name")) })
                    list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
                }
            }
        }
    }

    private fun renderController() {
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

    private fun pickFile(kind: String) {
        pendingPicker = kind
        val mime = when (kind) { "image" -> "image/*"; "video" -> "video/*"; else -> "*/*" }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, FILE_PICKER)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER && resultCode == RESULT_OK) data?.data?.let { uploadSelectedFile(it, pendingPicker) }
    }

    private fun uploadSelectedFile(uri: Uri, kind: String) {
        Toast.makeText(this, "در حال آماده‌سازی فایل…", Toast.LENGTH_SHORT).show()
        io.execute {
            var temp: File? = null
            try {
                val name = displayName(uri)
                if (kind == "app" && !name.endsWith(".llmpk", true)) throw IllegalArgumentException("فقط فایل .llmpk قابل ارسال است")
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                temp = File(cacheDir, "upload-${System.currentTimeMillis()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                contentResolver.openInputStream(uri)?.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                    ?: throw IllegalArgumentException("خواندن فایل ممکن نشد")
                if (temp.length() > 100L * 1024L * 1024L) throw IllegalArgumentException("حجم فایل بیشتر از ۱۰۰ مگابایت است")
                val c = (URL("$baseUrl/upload").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 10_000; readTimeout = 60_000
                    setRequestProperty("X-MDOS-Token", token)
                    setRequestProperty("X-File-Name", URLEncoder.encode(name, "UTF-8"))
                    setRequestProperty("X-File-Mime", mime); setRequestProperty("Content-Type", "application/octet-stream")
                    setFixedLengthStreamingMode(temp.length())
                }
                temp.inputStream().use { source -> c.outputStream.use { target -> source.copyTo(target) } }
                val response = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
                val result = JSONObject(response)
                if (!result.optBoolean("ok")) throw IllegalArgumentException(result.optString("error", "انتقال ناموفق بود"))
                c.disconnect()
                runOnUiThread {
                    Toast.makeText(this, if (kind == "app") "بسته LLMPK منتقل شد؛ در M-DOS نصب و اجرا می‌شود." else "فایل به تلویزیون منتقل شد.", Toast.LENGTH_LONG).show()
                    renderTab()
                }
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
        io.execute { try { request("DELETE", "/apps?id=" + URLEncoder.encode(id, "UTF-8")); runOnUiThread { Toast.makeText(this, "بسته حذف شد.", Toast.LENGTH_SHORT).show(); renderTab() } } catch (e: Exception) { toastError(e) } }
    }
    private fun removeFile(name: String) {
        io.execute { try { request("DELETE", "/files?name=" + URLEncoder.encode(name, "UTF-8")); runOnUiThread { renderTab() } } catch (e: Exception) { toastError(e) } }
    }

    private fun sendInput(data: JSONObject) {
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

    private fun request(method: String, path: String, body: String? = null): JSONObject {
        val c = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 3500; readTimeout = 7000
            setRequestProperty("X-MDOS-Token", token)
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val result = JSONObject(stream.bufferedReader().use { it.readText() })
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException(result.optString("error", "خطای ارتباط با M-DOS ($code)"))
        return result
    }

    private fun requestArray(method: String, path: String): JSONArray {
        val c = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 5000; readTimeout = 9000; setRequestProperty("X-MDOS-Token", token)
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val result = JSONArray(stream.bufferedReader().use { it.readText() })
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("خطای دریافت اطلاعات ($code)")
        return result
    }

    private fun toastError(e: Exception) = runOnUiThread { Toast.makeText(this, e.message ?: "خطای ارتباط", Toast.LENGTH_LONG).show() }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = rounded(panel, 14); setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, color: Int, action: () -> Unit): Button = Button(this).apply {
        text = value; setTextColor(if (color == yellow) navy else textColor); textSize = 13f
        isAllCaps = false; background = rounded(color, 12); setOnClickListener { action() }
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun formatBytes(size: Long): String = when {
        size < 1024 -> "$size B"; size < 1024 * 1024 -> "${size / 1024} KB"; else -> "${size / (1024 * 1024)} MB"
    }

    override fun onDestroy() {
        stopDiscovery()
        io.shutdownNow()
        super.onDestroy()
    }
}
