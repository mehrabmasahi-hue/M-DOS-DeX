package com.goldan.mdosdex

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import kotlin.concurrent.thread

/** Small, token-protected HTTP server for the local Wi-Fi Phone Link session. */
class PhoneLinkServer(
    context: Context,
    private val onInput: (JSONObject) -> Unit,
    private val onPackageUploaded: (File, String) -> Unit,
    private val onFileUploaded: (File, String) -> Unit,
    private val onPackageRemoved: (String) -> Unit
) {
    companion object { const val PORT = 8765; private const val MAX_UPLOAD = 100L * 1024L * 1024L }

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "phone-link").apply { mkdirs() }
    private val filesDir = File(root, "files").apply { mkdirs() }
    private val appsDir = File(root, "apps").apply { mkdirs() }
    private val token: String = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    @Volatile private var running = false
    private var socket: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        thread(name = "M-DOS-PhoneLink-Server", isDaemon = true) {
            try {
                val server = ServerSocket(PORT, 24, java.net.InetAddress.getByName("0.0.0.0"))
                socket = server
                while (running) {
                    try {
                        val client = server.accept()
                        thread(name = "M-DOS-PhoneLink-Client", isDaemon = true) { client.use { handle(it) } }
                    } catch (_: Exception) { if (running) continue }
                }
            } catch (_: Exception) { running = false }
        }
    }

    fun close() {
        running = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
    }

    fun pairInfo(): JSONObject {
        val ip = findLanIp() ?: "127.0.0.1"
        return JSONObject().put("url", "http://$ip:$PORT").put("token", token)
            .put("network", if (ip == "127.0.0.1") "اتصال Wi-Fi پیدا نشد" else "آماده اتصال در شبکه محلی")
    }

    fun qrPayload(): String = pairInfo().toString()

    fun localFileUrl(name: String): String = "http://127.0.0.1:$PORT/file?name=" +
        URLEncoder.encode(name, "UTF-8") + "&token=" + token

    fun filesJson(): String = buildFilesJson().toString()
    fun appsJson(): String = buildAppsJson().toString()

    fun readTextFile(name: String): String {
        val file = findFile(name) ?: return ""
        if (file.length() > 2L * 1024L * 1024L) return ""
        return try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
    }

    fun readFileBase64(name: String): String {
        val file = findFile(name) ?: return ""
        if (file.length() > 1_300_000L) return ""
        return try { android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP) }
        catch (_: Exception) { "" }
    }

    fun deleteFile(name: String): Boolean {
        val file = findFile(name) ?: return false
        val packageId = if (file.extension.equals("llmpk", true)) {
            try { JSONObject(file.readText()).optString("id") } catch (_: Exception) { "" }
        } else ""
        val removed = file.delete()
        if (removed && packageId.isNotBlank()) onPackageRemoved(packageId)
        return removed
    }

    fun deletePackage(id: String): Boolean {
        var removed = false
        appsDir.listFiles()?.filter { it.isFile && it.extension.equals("llmpk", true) }?.forEach { file ->
            try {
                if (JSONObject(file.readText()).optString("id") == id) {
                    removed = file.delete() || removed
                }
            } catch (_: Exception) { }
        }
        if (removed) onPackageRemoved(id)
        return removed
    }

    private fun handle(client: Socket) {
        client.soTimeout = 120_000
        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) { respond(output, 400, "Bad Request", "درخواست نامعتبر") ; return }
        val method = parts[0].uppercase()
        val target = parts[1]
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val uri = Uri.parse("http://localhost$target")
        val path = uri.path ?: "/"
        if (method == "OPTIONS") { respond(output, 204, "No Content", ""); return }
        if (!authorized(uri, headers)) {
            respondJson(output, 401, JSONObject().put("ok", false).put("error", "توکن اتصال معتبر نیست")); return
        }
        when {
            method == "GET" && path == "/ping" -> respondJson(output, 200, JSONObject().put("ok", true).put("name", "M-DOS DeX"))
            method == "GET" && path == "/files" -> respondJson(output, 200, buildFilesJson())
            method == "GET" && path == "/apps" -> respondJson(output, 200, buildAppsJson())
            method == "GET" && path == "/file" -> {
                val file = findFile(uri.getQueryParameter("name") ?: "")
                if (file == null) respond(output, 404, "Not Found", "فایل پیدا نشد") else respondFile(output, file)
            }
            method == "POST" && path == "/input" -> {
                val body = readBody(input, headers["content-length"]?.toLongOrNull() ?: 0L, 256 * 1024L)
                try { onInput(JSONObject(String(body, StandardCharsets.UTF_8))); respondJson(output, 200, JSONObject().put("ok", true)) }
                catch (_: Exception) { respondJson(output, 400, JSONObject().put("ok", false).put("error", "فرمان نامعتبر است")) }
            }
            method == "POST" && path == "/upload" -> handleUpload(input, output, headers, uri)
            method == "DELETE" && path == "/apps" -> {
                val id = uri.getQueryParameter("id") ?: ""
                val ok = deletePackage(id)
                respondJson(output, if (ok) 200 else 404, JSONObject().put("ok", ok))
            }
            method == "DELETE" && path == "/files" -> {
                val name = uri.getQueryParameter("name") ?: ""
                val ok = deleteFile(name)
                respondJson(output, if (ok) 200 else 404, JSONObject().put("ok", ok))
            }
            else -> respond(output, 404, "Not Found", "مسیر پیدا نشد")
        }
    }

    private fun handleUpload(input: InputStream, output: BufferedOutputStream, headers: Map<String, String>, uri: Uri) {
        val length = headers["content-length"]?.toLongOrNull() ?: -1L
        if (length < 0L || length > MAX_UPLOAD) {
            respondJson(output, 413, JSONObject().put("ok", false).put("error", "حجم فایل باید کمتر از ۱۰۰ مگابایت باشد")); return
        }
        val rawName = headers["x-file-name"]?.let { try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it } }
            ?: uri.getQueryParameter("name") ?: "received-file.bin"
        val name = safeName(rawName)
        val mime = headers["x-file-mime"]?.take(120) ?: guessMime(name)
        val targetDir = if (name.endsWith(".llmpk", true)) appsDir else filesDir
        val temp = File(targetDir, ".upload-${System.nanoTime()}.tmp")
        try {
            temp.outputStream().buffered().use { out -> copyExactly(input, out, length) }
            if (name.endsWith(".llmpk", true)) {
                if (length > 2L * 1024L * 1024L) throw IllegalArgumentException("بسته LLMPK بیش از ۲ مگابایت است")
                val text = temp.readText(Charsets.UTF_8)
                val pkg = JSONObject(text)
                val id = pkg.optString("id")
                if (pkg.optString("format") != "llmpk" || pkg.optInt("spec") != 1 || !id.matches(Regex("[a-z0-9][a-z0-9._-]{1,39}"))) {
                    throw IllegalArgumentException("ساختار بسته LLMPK معتبر نیست")
                }
                appsDir.listFiles()?.filter { it.isFile && it.extension.equals("llmpk", true) }?.forEach { old ->
                    try { if (JSONObject(old.readText()).optString("id") == id) old.delete() } catch (_: Exception) { }
                }
                val finalFile = uniqueFile(appsDir, name)
                if (!temp.renameTo(finalFile)) temp.copyTo(finalFile, overwrite = true).also { temp.delete() }
                onPackageUploaded(finalFile, text)
                respondJson(output, 200, JSONObject().put("ok", true).put("type", "llmpk").put("id", id).put("name", pkg.optString("name", name)))
            } else {
                val finalFile = uniqueFile(filesDir, name)
                if (!temp.renameTo(finalFile)) temp.copyTo(finalFile, overwrite = true).also { temp.delete() }
                onFileUploaded(finalFile, mime)
                respondJson(output, 200, JSONObject().put("ok", true).put("type", "file").put("name", finalFile.name).put("size", finalFile.length()))
            }
        } catch (e: Exception) {
            temp.delete()
            respondJson(output, 400, JSONObject().put("ok", false).put("error", e.message ?: "انتقال فایل ناموفق بود"))
        }
    }

    private fun buildFilesJson(): JSONArray {
        val arr = JSONArray()
        (filesDir.listFiles()?.toList() ?: emptyList()).filter { it.isFile && !it.name.startsWith(".") }
            .sortedByDescending { it.lastModified() }.forEach { file ->
                val mime = guessMime(file.name)
                arr.put(JSONObject().put("name", file.name).put("size", file.length()).put("mime", mime)
                    .put("url", localFileUrl(file.name)).put("modified", file.lastModified()))
            }
        (appsDir.listFiles()?.toList() ?: emptyList()).filter { it.isFile && it.extension.equals("llmpk", true) }
            .sortedByDescending { it.lastModified() }.forEach { file ->
                val mime = "application/vnd.mdos.llmpk"
                arr.put(JSONObject().put("name", file.name).put("size", file.length()).put("mime", mime)
                    .put("url", localFileUrl(file.name)).put("modified", file.lastModified()).put("type", "llmpk"))
            }
        return arr
    }

    private fun buildAppsJson(): JSONArray {
        val arr = JSONArray()
        (appsDir.listFiles() ?: emptyArray()).filter { it.isFile && it.extension.equals("llmpk", true) }
            .forEach { file -> try {
                val p = JSONObject(file.readText())
                arr.put(JSONObject().put("id", p.optString("id")).put("name", p.optString("name", file.name))
                    .put("version", p.optString("version", "?")).put("file", file.name))
            } catch (_: Exception) { } }
        return arr
    }

    private fun findFile(name: String): File? {
        val safe = safeName(name)
        val a = File(filesDir, safe).takeIf { it.isFile && it.canonicalFile.parentFile == filesDir.canonicalFile }
        if (a != null) return a
        val b = File(appsDir, safe).takeIf { it.isFile && it.canonicalFile.parentFile == appsDir.canonicalFile }
        return b
    }

    private fun authorized(uri: Uri, headers: Map<String, String>): Boolean =
        (headers["x-mdos-token"] ?: uri.getQueryParameter("token")) == token

    private fun safeName(value: String): String {
        val base = value.substringAfterLast('/').substringAfterLast('\\').trim()
            .replace(Regex("[^A-Za-z0-9._آ-ی ()-]"), "_").take(120)
        return base.ifBlank { "received-file.bin" }.takeUnless { it == "." || it == ".." } ?: "received-file.bin"
    }

    private fun uniqueFile(dir: File, name: String): File {
        var result = File(dir, name)
        if (!result.exists()) return result
        val stem = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 2
        while (result.exists()) { result = File(dir, "$stem ($i)$ext"); i++ }
        return result
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val b = input.read()
            if (b == -1) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (b == 10) break
            if (b != 13) bytes.write(b)
        }
        return bytes.toString("UTF-8")
    }

    private fun readBody(input: InputStream, length: Long, max: Long): ByteArray {
        require(length in 0..max) { "بدنه درخواست بیش از حد بزرگ است" }
        val out = ByteArrayOutputStream(length.toInt())
        copyExactly(input, out, length)
        return out.toByteArray()
    }

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, length: Long) {
        var remaining = length
        val buffer = ByteArray(32 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IllegalArgumentException("انتقال فایل ناقص است")
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun respondJson(out: BufferedOutputStream, status: Int, data: JSONObject) =
        respond(out, status, if (status == 200) "OK" else if (status == 401) "Unauthorized" else if (status == 404) "Not Found" else "Bad Request", data.toString(), "application/json; charset=utf-8")

    private fun respondJson(out: BufferedOutputStream, status: Int, data: JSONArray) =
        respond(out, status, "OK", data.toString(), "application/json; charset=utf-8")

    private fun respond(out: BufferedOutputStream, status: Int, reason: String, body: String, mime: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type, X-MDOS-Token, X-File-Name, X-File-Mime\r\nCache-Control: no-store\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8)); out.write(bytes); out.flush()
    }

    private fun respondFile(out: BufferedOutputStream, file: File) {
        val mime = guessMime(file.name)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${file.length()}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-store\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        FileInputStream(file).use { it.copyTo(out) }
        out.flush()
    }

    private fun findLanIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val net = interfaces.nextElement()
                if (!net.isUp || net.isLoopback) continue
                val addresses = net.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) return address.hostAddress
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
