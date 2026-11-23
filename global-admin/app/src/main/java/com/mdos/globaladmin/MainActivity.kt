package com.mdos.globaladmin

import android.app.Activity
import android.app.AlertDialog
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private data class Device(
        val id: String,
        val name: String,
        val host: String,
        val port: Int
    )

    private val devices = linkedMapOf<String, Device>()
    private val selected = linkedSetOf<String>()
    private val rowChecks = linkedMapOf<String, CheckBox>()
    private val executor = Executors.newCachedThreadPool()
    private lateinit var nsd: NsdManager
    private lateinit var vault: MdosSecureStore
    private lateinit var status: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var commandGrid: LinearLayout
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolving = 0

    private val pad = 18

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsd = getSystemService(NSD_SERVICE) as NsdManager
        vault = MdosSecureStore(this)
        buildUi()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF111318.toInt())
        }
        val header = TextView(this).apply {
            text = "M-DOS GLOBAL ADMIN"
            textSize = 22f
            setTextColor(0xFFFFC928.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        page.addView(header)
        page.addView(TextView(this).apply {
            text = "مدیریت مستقیم دستگاه‌های M-DOS در شبکه محلی"
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 4, 0, 12)
        })
        status = TextView(this).apply {
            text = "برای پیدا کردن دستگاه‌ها، جست‌وجو را بزنید."
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF242833.toInt())
        }
        page.addView(status)
        val actionBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionBar.addView(makeButton("جست‌وجوی دستگاه‌ها") { discover() }, LinearLayout.LayoutParams(0, -2, 1f))
        actionBar.addView(makeButton("انتخاب همه") {
            selected.clear()
            selected.addAll(devices.keys)
            renderDevices()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(actionBar)

        page.addView(sectionTitle("دستگاه‌های پیدا شده"))
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(deviceList)

        page.addView(sectionTitle("۲۰ فرمان مدیریتی"))
        commandGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(commandGrid)

        val scroll = ScrollView(this)
        scroll.addView(page)
        setContentView(scroll)
        renderCommands()
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(0xFFFFC928.toInt())
        setPadding(0, 18, 0, 8)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun makeButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 12f
        setOnClickListener { action() }
    }

    private fun setStatus(message: String) {
        runOnUiThread { status.text = message }
    }

    private fun discover() {
        stopDiscovery()
        devices.clear()
        selected.clear()
        rowChecks.clear()
        renderDevices()
        setStatus("در حال جست‌وجوی M-DOS در Wi-Fi…")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.startsWith("M-DOS-")) return
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val attrs = serviceInfo.attributes
                            val id = attrs["id"]?.toString(Charsets.UTF_8)
                                ?: serviceInfo.serviceName.removePrefix("M-DOS-")
                            val host = serviceInfo.host?.hostAddress ?: return
                            val device = Device(
                                id = id,
                                name = serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port
                            )
                            runOnUiThread {
                                devices[id] = device
                                renderDevices()
                                setStatus("دستگاه‌های پیدا شده: ${devices.size}")
                            }
                        }
                    })
                } catch (_: Exception) {}
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                setStatus("شروع جست‌وجو ناموفق بود: $errorCode")
                stopDiscovery()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(MdosProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            setStatus("جست‌وجوی شبکه در این دستگاه پشتیبانی نشد.")
        }
    }

    private fun stopDiscovery() {
        val listener = discoveryListener ?: return
        try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
        discoveryListener = null
    }

    private fun renderDevices() {
        if (!::deviceList.isInitialized) return
        deviceList.removeAllViews()
        if (devices.isEmpty()) {
            deviceList.addView(TextView(this).apply {
                text = "هنوز دستگاهی پیدا نشده است."
                setTextColor(0xFFBBBBBB.toInt())
                textSize = 12f
            })
            return
        }
        devices.values.forEach { device ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 10, 12, 10)
                setBackgroundColor(0xFF242833.toInt())
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val check = CheckBox(this).apply {
                text = device.name
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = selected.contains(device.id)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(device.id) else selected.remove(device.id)
                }
            }
            rowChecks[device.id] = check
            top.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            val paired = vault.get(device.id) != null
            top.addView(TextView(this).apply {
                text = if (paired) "● جفت‌شده" else "○ بدون کلید"
                textSize = 11f
                setTextColor(if (paired) 0xFF71DB9B.toInt() else 0xFFFFC16B.toInt())
            })
            card.addView(top)
            card.addView(TextView(this).apply {
                text = "${device.host}:${device.port}\nID: ${device.id}"
                textSize = 10f
                setTextColor(0xFFB8BECA.toInt())
            })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(makeButton(if (paired) "تغییر کلید" else "جفت‌سازی") {
                pairDevice(device)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (paired) buttons.addView(makeButton("حذف دسترسی") {
                vault.remove(device.id)
                renderDevices()
                setStatus("کلید ذخیره‌شده برای ${device.name} حذف شد.")
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(buttons)
            val spacer = LinearLayout(this).apply { minimumHeight = 8 }
            deviceList.addView(card)
            deviceList.addView(spacer)
        }
    }

    private fun pairDevice(device: Device) {
        val input = EditText(this).apply {
            hint = "کلید ۶۴ رقمی را از M-DOS Connect وارد کنید"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("جفت‌سازی ${device.name}")
            .setMessage("در M-DOS Connect کلید جفت‌سازی را نمایش دهید و اینجا وارد کنید. کلید در Android Keystore رمزگذاری می‌شود.")
            .setView(input)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("بررسی و ذخیره", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val secret = input.text.toString().trim().lowercase()
                        if (MdosProtocol.hexToBytes(secret) == null) {
                            input.error = "کلید باید دقیقاً ۶۴ رقم هگزادسیمال باشد."
                            return@setOnClickListener
                        }
                        setStatus("در حال بررسی کلید و اتصال به ${device.name}…")
                        executor.execute {
                            val result = send(device, "status", secret)
                            runOnUiThread {
                                if (result.first) {
                                    vault.put(device.id, secret)
                                    renderDevices()
                                    setStatus("جفت‌سازی ${device.name} موفق بود.")
                                    dialog.dismiss()
                                } else {
                                    setStatus("کلید یا اتصال معتبر نیست: ${result.second}")
                                }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun renderCommands() {
        commandGrid.removeAllViews()
        val rows = MdosProtocol.COMMANDS.chunked(2)
        rows.forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { command ->
                val label = MdosProtocol.LABELS[command] ?: command
                row.addView(makeButton(label) { sendToSelected(command) },
                    LinearLayout.LayoutParams(0, -2, 1f))
            }
            if (pair.size == 1) row.addView(TextView(this), LinearLayout.LayoutParams(0, -2, 1f))
            commandGrid.addView(row)
        }
    }

    private fun sendToSelected(command: String) {
        val targets = selected.mapNotNull { devices[it] }
        if (targets.isEmpty()) {
            setStatus("ابتدا یک یا چند دستگاه را انتخاب کنید.")
            return
        }
        val label = MdosProtocol.LABELS[command] ?: command
        setStatus("در حال ارسال «$label» به ${targets.size} دستگاه…")
        executor.execute {
            var ok = 0
            val errors = ArrayList<String>()
            targets.forEach { device ->
                val secret = vault.get(device.id)
                if (secret == null) {
                    errors.add("${device.name}: جفت نشده")
                } else {
                    val result = send(device, command, secret)
                    if (result.first) ok++ else errors.add("${device.name}: ${result.second}")
                }
            }
            runOnUiThread {
                val errorText = if (errors.isEmpty()) "" else "\n" + errors.joinToString("؛ ")
                setStatus("فرمان «$label»: موفق برای $ok از ${targets.size} دستگاه.$errorText")
            }
        }
    }

    private fun send(device: Device, command: String, secret: String): Pair<Boolean, String> {
        return try {
            val timestamp = System.currentTimeMillis()
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val signature = MdosProtocol.sign(device.id, command, timestamp, nonce, secret)
            val payload = JSONObject()
                .put("deviceId", device.id)
                .put("command", command)
                .put("timestamp", timestamp)
                .put("nonce", nonce)
                .put("signature", signature)
                .toString() + "\n"
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(device.host, device.port), 4500)
                socket.soTimeout = 4500
                socket.getOutputStream().write(payload.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val line = reader.readLine() ?: return Pair(false, "پاسخی از دستگاه دریافت نشد")
                val response = JSONObject(line)
                if (response.optBoolean("ok", false)) Pair(true, "OK")
                else Pair(false, response.optString("error", "رد فرمان"))
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "خطای اتصال")
        }
    }

    override fun onDestroy() {
        stopDiscovery()
        executor.shutdownNow()
        super.onDestroy()
    }
}
