package com.goldan.mdosdex

import android.graphics.Bitmap
import android.util.Base64
import android.webkit.JavascriptInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

class PhoneLinkBridge(private val server: PhoneLinkServer) {
    @JavascriptInterface fun getPairInfo(): String = server.pairInfo().toString()
    @JavascriptInterface fun getFilesJson(): String = server.filesJson()
    @JavascriptInterface fun getAppsJson(): String = server.appsJson()
    @JavascriptInterface fun getFileUrl(name: String): String = server.localFileUrl(name)
    @JavascriptInterface fun readTextFile(name: String): String = server.readTextFile(name)
    @JavascriptInterface fun readFileBase64(name: String): String = server.readFileBase64(name)
    @JavascriptInterface fun deleteFile(name: String): Boolean = server.deleteFile(name)

    @JavascriptInterface fun getQrDataUrl(): String {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
            val matrix = QRCodeWriter().encode(server.qrPayload(), BarcodeFormat.QR_CODE, 520, 520, hints)
            val bitmap = Bitmap.createBitmap(520, 520, Bitmap.Config.ARGB_8888)
            for (y in 0 until 520) for (x in 0 until 520) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            bitmap.recycle()
            "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }
}
