package com.mdos.globaladmin

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MdosProtocol {
    const val SERVICE_TYPE = "_mdosctl._tcp."
    val COMMANDS = listOf(
        "maintenance_on","maintenance_off","lock_desktop","refresh_shell","restart_shell",
        "hide_icons","show_icons","dark_mode","light_mode","focus_mode_on","focus_mode_off",
        "phonelink_disable","phonelink_enable","mstore_disable","mstore_enable",
        "backup_export","clear_temp","status","notify_user","wallpaper_reset"
    )
    val LABELS = mapOf(
        "maintenance_on" to "حالت تعمیرات",
        "maintenance_off" to "خروج از تعمیرات",
        "lock_desktop" to "قفل دسکتاپ",
        "refresh_shell" to "بازآوری دسکتاپ",
        "restart_shell" to "راه‌اندازی مجدد پوسته",
        "hide_icons" to "مخفی‌کردن آیکون‌ها",
        "show_icons" to "نمایش آیکون‌ها",
        "dark_mode" to "تم تاریک",
        "light_mode" to "تم روشن",
        "focus_mode_on" to "حالت تمرکز",
        "focus_mode_off" to "خروج از تمرکز",
        "phonelink_disable" to "محدودکردن Phone Link",
        "phonelink_enable" to "فعال‌کردن Phone Link",
        "mstore_disable" to "محدودکردن M Store",
        "mstore_enable" to "فعال‌کردن M Store",
        "backup_export" to "پشتیبان تنظیمات",
        "clear_temp" to "پاک‌سازی موقت",
        "status" to "گزارش وضعیت",
        "notify_user" to "پیام مدیریتی",
        "wallpaper_reset" to "بازنشانی پس‌زمینه"
    )

    fun hexToBytes(value: String): ByteArray? {
        if (value.length != 64 || !value.matches(Regex("^[0-9a-fA-F]{64}$"))) return null
        return try {
            ByteArray(32) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (_: Exception) { null }
    }

    fun sign(deviceId: String, command: String, timestamp: Long, nonce: String, secretHex: String): String {
        val key = hexToBytes(secretHex) ?: throw IllegalArgumentException("کلید باید ۶۴ رقم هگزادسیمال باشد.")
        val payload = "$deviceId\n$command\n$timestamp\n$nonce".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(payload).joinToString("") { "%02x".format(it) }
    }
}
