package com.goldan.mdosdex

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MdosProtocol {
    const val SERVICE_TYPE = "_mdosctl._tcp."
    const val PROTOCOL_VERSION = 1

    val COMMANDS = setOf(
        "maintenance_on", "maintenance_off", "lock_desktop", "refresh_shell",
        "restart_shell", "hide_icons", "show_icons", "dark_mode", "light_mode",
        "focus_mode_on", "focus_mode_off", "phonelink_disable", "phonelink_enable",
        "mstore_disable", "mstore_enable", "backup_export", "clear_temp",
        "status", "notify_user", "wallpaper_reset"
    )

    fun hexToBytes(value: String): ByteArray? {
        if (value.length != 64 || !value.matches(Regex("^[0-9a-fA-F]{64}$"))) return null
        return try {
            ByteArray(32) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sign(deviceId: String, command: String, timestamp: Long, nonce: String, secretHex: String): String {
        val key = hexToBytes(secretHex) ?: throw IllegalArgumentException("Invalid pairing key")
        val message = "$deviceId\n$command\n$timestamp\n$nonce".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message).joinToString("") { "%02x".format(it) }
    }
}
