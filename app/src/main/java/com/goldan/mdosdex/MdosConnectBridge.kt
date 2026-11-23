package com.goldan.mdosdex

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MdosConnectBridge(
    private val server: MdosGlobalControlServer,
    private val context: Context
) {
    @JavascriptInterface
    fun getInfoJson(): String = server.infoJson()

    @JavascriptInterface
    fun getPairingCode(): String = server.getPairingCode()

    @JavascriptInterface
    fun regeneratePairingCode(): String = server.regeneratePairingCode()

    /**
     * Writes a bounded settings-only JSON backup to the public Downloads/M-DOS folder
     * on Android 10+, without broad storage permissions.
     */
    @JavascriptInterface
    fun saveBackupJson(json: String): String {
        if (json.length > 1_500_000) return "ERROR:backup_too_large"
        return try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "M-DOS-settings-$stamp.json"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/M-DOS")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri: Uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return "ERROR:cannot_create_download"
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } ?: return "ERROR:cannot_open_download"
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                uri.toString()
            } else {
                val directory = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "M-DOS"
                )
                if (!directory.exists() && !directory.mkdirs()) return "ERROR:cannot_create_folder"
                val output = File(directory, fileName)
                output.writeText(json, Charsets.UTF_8)
                output.absolutePath
            }
        } catch (error: Exception) {
            "ERROR:" + (error.message ?: "backup_failed")
        }
    }
}
