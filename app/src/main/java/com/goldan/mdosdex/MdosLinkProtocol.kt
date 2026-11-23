package com.goldan.mdosdex

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned contract shared by the TV host and the phone companion.
 * Feature discovery belongs here so clients do not assume every build
 * exposes every Phone Link feature.
 */
internal object MdosLinkProtocol {
    const val ID = "mdos-phone-link"
    const val VERSION = 3
    const val MIN_COMPATIBLE_VERSION = 2

    private val supportedCapabilities = listOf(
        "device-discovery",
        "one-time-pairing",
        "remote-pointer",
        "keyboard-input",
        "file-transfer",
        "llmpk-management",
        "connection-health"
    )

    fun capabilitiesJson(): JSONArray = JSONArray().apply {
        supportedCapabilities.forEach { put(it) }
    }

    fun hello(
        deviceId: String,
        deviceName: String,
        uptimeMs: Long,
        trustedPhoneCount: Int
    ): JSONObject = JSONObject()
        .put("ok", true)
        .put("protocol", ID)
        .put("protocolVersion", VERSION)
        .put("minCompatibleVersion", MIN_COMPATIBLE_VERSION)
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("name", deviceName)
        .put("serverState", "running")
        .put("uptimeMs", uptimeMs)
        .put("trustedPhones", trustedPhoneCount)
        .put("capabilities", capabilitiesJson())
}
