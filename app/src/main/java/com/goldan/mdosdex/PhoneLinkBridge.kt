package com.goldan.mdosdex

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
