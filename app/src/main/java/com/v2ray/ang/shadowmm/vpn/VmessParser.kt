package com.v2ray.ang.shadowmm.vpn

import android.util.Base64
import android.util.Log
import org.json.JSONObject

data class VmessConfig(
    val uuid: String,
    val host: String,
    val port: Int,
    val name: String? = null,
    val network: String = "tcp",
    val security: String = "auto",
    val path: String? = null,
    val sni: String? = null,
    val type: String? = null, // header type
    val aid: Int = 0
)

/**
 * Parse vmess:// URI (Base64 encoded JSON)
 */
fun parseVmessUri(uri: String): VmessConfig? {
    try {
        if (!uri.startsWith("vmess://")) return null

        val base64 = uri.removePrefix("vmess://")
        val jsonString = try {
            String(Base64.decode(base64, Base64.NO_WRAP or Base64.URL_SAFE))
        } catch (e: Exception) {
            String(Base64.decode(base64, Base64.DEFAULT))
        }

        val json = JSONObject(jsonString)

        // v2rayN format
        return VmessConfig(
            uuid = json.optString("id"),
            host = json.optString("add"),
            port = json.optInt("port"),
            name = json.optString("ps"),
            network = json.optString("net", "tcp"),
            security = json.optString("tls", "none"), // "tls" or "none" usually in vmess json
            path = json.optString("path"),
            sni = json.optString("host"), // In vmess json, 'host' field often acts as SNI/Host
            type = json.optString("type"),
            aid = json.optInt("aid", 0)
        )

    } catch (e: Exception) {
        Log.e("VmessParser", "Failed to parse VMess URI", e)
        return null
    }
}