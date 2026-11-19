package com.goldan.mdosdex

import android.webkit.JavascriptInterface

/** JavaScript bridge for the built-in Phone Link app. Pairing is numeric, not QR-based. */
class PhoneLinkBridge(private val server: PhoneLinkServer) {
    @JavascriptInterface fun getPairInfo(): String = server.pairInfo().toString()
    @JavascriptInterface fun getFilesJson(): String = server.filesJson()
    @JavascriptInterface fun getAppsJson(): String = server.appsJson()
    @JavascriptInterface fun getFileUrl(name: String): String = server.localFileUrl(name)
    @JavascriptInterface fun readTextFile(name: String): String = server.readTextFile(name)
    @JavascriptInterface fun readFileBase64(name: String): String = server.readFileBase64(name)
    @JavascriptInterface fun deleteFile(name: String): Boolean = server.deleteFile(name)

    // Kept for compatibility with the existing HTML; the QR element is replaced by the Wi-Fi pairing code.
    @JavascriptInterface fun getQrDataUrl(): String = ""
}
