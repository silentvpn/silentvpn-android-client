package com.v2ray.ang.shadowmm.vpn

import android.util.Base64
import android.util.Log
import java.net.URLDecoder

data class VlessConfig(
    val uuid: String,
    val host: String,
    val port: Int,
    val name: String? = null,
    val network: String = "tcp",
    val security: String = "none",
    val path: String? = null,
    val sni: String? = null,
    val flow: String? = null,
    val headerType: String? = null,
    val host_header: String? = null,

    // ✅ REALITY
    val publicKey: String? = null,   // pbk
    val shortId: String? = null,     // sid
    val spiderX: String? = null,     // spx
    val fingerprint: String? = null,  // fp
    // ✅ ALPN
    val alpn: String? = null
)

/**
 * Parse vless:// URI into VlessConfig
 * Format: vless://uuid@host:port?param1=value1&param2=value2#name
 */
fun parseVlessUri(uri: String): VlessConfig? {
    try {
        if (!uri.startsWith("vless://")) return null

        val withoutScheme = uri.removePrefix("vless://")

        // Extract name (after #)
        val hashIndex = withoutScheme.indexOf('#')
        val name = if (hashIndex >= 0) {
            URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8")
        } else null

        val beforeName = if (hashIndex >= 0) {
            withoutScheme.substring(0, hashIndex)
        } else {
            withoutScheme
        }

        // Extract query params (after ?)
        val queryIndex = beforeName.indexOf('?')
        val mainPart = if (queryIndex >= 0) {
            beforeName.substring(0, queryIndex)
        } else {
            beforeName
        }

        val queryString = if (queryIndex >= 0) {
            beforeName.substring(queryIndex + 1)
        } else {
            ""
        }

        // Parse main part: uuid@host:port
        val atIndex = mainPart.indexOf('@')
        if (atIndex < 0) return null

        val uuid = mainPart.substring(0, atIndex)
        val hostPort = mainPart.substring(atIndex + 1)

        val colonIndex = hostPort.lastIndexOf(':')
        if (colonIndex < 0) return null

        val host = hostPort.substring(0, colonIndex)
        val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: return null

        // Parse query params
        val params = parseQueryParams(queryString)

        return VlessConfig(
            uuid = uuid,
            host = host,
            port = port,
            name = name,
            network = params["type"] ?: "tcp",
            security = params["security"] ?: "none",
            path = params["path"],
            sni = params["sni"],
            flow = params["flow"],
            headerType = params["headerType"],
            host_header = params["host"],

            // ✅ REALITY
            publicKey = params["pbk"],
            shortId = params["sid"],
            spiderX = params["spx"],
            fingerprint = params["fp"],
            alpn = params["alpn"]

        )

    } catch (e: Exception) {
        Log.e("VlessParser", "Failed to parse VLESS URI", e)
        return null
    }
}

private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isEmpty()) return emptyMap()

    return query.split('&')
        .mapNotNull { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2) {
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            } else null
        }
        .toMap()
}

/**
 * Build vless:// URI from config
 */
fun buildVlessUri(
    uuid: String,
    host: String,
    port: Int,
    name: String? = null,
    network: String = "tcp",
    security: String = "none",
    path: String? = null,
    sni: String? = null,
    flow: String? = null
): String {
    val params = mutableListOf<String>()

    if (network != "tcp") params.add("type=$network")
    if (security != "none") params.add("security=$security")
    if (!path.isNullOrEmpty()) params.add("path=${java.net.URLEncoder.encode(path, "UTF-8")}")
    if (!sni.isNullOrEmpty()) params.add("sni=$sni")
    if (!flow.isNullOrEmpty()) params.add("flow=$flow")

    val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    val nameString = if (!name.isNullOrEmpty()) "#${java.net.URLEncoder.encode(name, "UTF-8")}" else ""

    return "vless://$uuid@$host:$port$queryString$nameString"
}