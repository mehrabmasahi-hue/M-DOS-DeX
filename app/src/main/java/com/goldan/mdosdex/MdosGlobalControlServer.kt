package com.goldan.mdosdex

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Local-network control endpoint for M-DOS.
 *
 * Commands are HMAC-authenticated with a unique 256-bit device pairing key.
 * The pairing key is never sent over the network. Traffic is limited to M-DOS
 * UI commands and does not execute shell commands or shut down Android itself.
 */
class MdosGlobalControlServer(
    private val context: Context,
    private val onCommand: (String) -> Unit
) {
    private val prefs = context.getSharedPreferences("mdos_global_control", Context.MODE_PRIVATE)
    private val vault = MdosSecureStore(
        context,
        "mdos_global_secrets",
        "mdos_global_control_aes_v1"
    )
    private val main = Handler(Looper.getMainLooper())
    private val clients: ExecutorService = Executors.newCachedThreadPool()
    private val seenNonces = ConcurrentHashMap<String, Long>()
    private val random = SecureRandom()
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var serverPort = 0
    @Volatile private var registration: NsdManager.RegistrationListener? = null
    private var acceptThread: Thread? = null

    val deviceId: String
        get() {
            val existing = prefs.getString("device_id", null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", created).apply()
            return created
        }

    @Synchronized
    fun getPairingCode(): String {
        vault.get("pairing_code")?.let { return it }
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val code = bytes.joinToString("") { "%02x".format(it) }
        vault.put("pairing_code", code)
        return code
    }

    @Synchronized
    fun regeneratePairingCode(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val code = bytes.joinToString("") { "%02x".format(it) }
        vault.put("pairing_code", code)
        return code
    }

    fun infoJson(): String = JSONObject()
        .put("deviceId", deviceId)
        .put("deviceName", android.os.Build.MODEL ?: "M-DOS Device")
        .put("port", serverPort)
        .put("running", running)
        .put("networkMode", "LAN / Wi-Fi")
        .put("protocol", MdosProtocol.PROTOCOL_VERSION)
        .toString()

    @Synchronized
    fun start() {
        if (running) return
        running = true
        getPairingCode()
        acceptThread = Thread({
            try {
                val socketServer = ServerSocket(0, 32, InetAddress.getByName("0.0.0.0"))
                serverSocket = socketServer
                serverPort = socketServer.localPort
                main.post { registerService(serverPort) }

                while (running) {
                    val client = socketServer.accept()
                    clients.execute { handleClient(client) }
                }
            } catch (_: Exception) {
                if (running) {
                    running = false
                    main.post { unregisterService() }
                }
            }
        }, "M-DOS-Global-Control-Accept").apply {
            isDaemon = true
            start()
        }
    }

    private fun registerService(port: Int) {
        if (!running || registration != null) return
        val info = NsdServiceInfo().apply {
            serviceName = "M-DOS-${deviceId.take(8)}"
            serviceType = MdosProtocol.SERVICE_TYPE
            setPort(port)
            setAttribute("id", deviceId)
            setAttribute("v", MdosProtocol.PROTOCOL_VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registration = null
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                registration = null
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registration = null
            }
        }
        registration = listener
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            registration = null
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val line = reader.readLine() ?: return
                if (line.length > 12_000) {
                    writeResponse(client, JSONObject().put("ok", false).put("error", "request_too_large"))
                    return
                }

                val request = JSONObject(line)
                val requestedDevice = request.optString("deviceId")
                val command = request.optString("command")
                val timestamp = request.optLong("timestamp", 0L)
                val nonce = request.optString("nonce")
                val signature = request.optString("signature")

                if (requestedDevice != deviceId || command !in MdosProtocol.COMMANDS) {
                    writeResponse(client, JSONObject().put("ok", false).put("error", "invalid_target_or_command"))
                    return
                }
                if (abs(System.currentTimeMillis() - timestamp) > 90_000L ||
                    nonce.length !in 16..80 ||
                    !nonce.matches(Regex("^[A-Za-z0-9_-]+$"))) {
                    writeResponse(client, JSONObject().put("ok", false).put("error", "expired_or_invalid_request"))
                    return
                }

                val now = System.currentTimeMillis()
                seenNonces.entries.toList().forEach { entry -> if (now - entry.value > 120_000L) seenNonces.remove(entry.key, entry.value) }
                if (seenNonces.putIfAbsent(nonce, now) != null) {
                    writeResponse(client, JSONObject().put("ok", false).put("error", "replayed_request"))
                    return
                }

                val key = getPairingCode()
                val expected = MdosProtocol.sign(deviceId, command, timestamp, nonce, key)
                val valid = MessageDigest.isEqual(
                    expected.lowercase().toByteArray(Charsets.US_ASCII),
                    signature.lowercase().toByteArray(Charsets.US_ASCII)
                )
                if (!valid) {
                    writeResponse(client, JSONObject().put("ok", false).put("error", "authentication_failed"))
                    return
                }

                if (command != "status") main.post { onCommand(command) }
                val response = JSONObject()
                    .put("ok", true)
                    .put("deviceId", deviceId)
                    .put("command", command)
                    .put("deviceName", android.os.Build.MODEL ?: "M-DOS Device")
                    .put("timestamp", System.currentTimeMillis())
                writeResponse(client, response)
            } catch (_: Exception) {
                try {
                    writeResponse(client, JSONObject().put("ok", false).put("error", "bad_request"))
                } catch (_: Exception) {}
            }
        }
    }

    private fun writeResponse(socket: Socket, value: JSONObject) {
        socket.getOutputStream().write((value.toString() + "\n").toByteArray(Charsets.UTF_8))
        socket.getOutputStream().flush()
    }

    @Synchronized
    fun close() {
        running = false
        unregisterService()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverPort = 0
        clients.shutdownNow()
        seenNonces.clear()
    }

    private fun unregisterService() {
        val listener = registration ?: return
        try { nsd.unregisterService(listener) } catch (_: Exception) {}
        registration = null
    }
}
