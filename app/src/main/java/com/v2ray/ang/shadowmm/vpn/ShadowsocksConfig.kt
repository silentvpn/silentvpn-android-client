package com.v2ray.ang.shadowmm.vpn

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder

data class ShadowsocksConfig(
    val host: String,
    val port: Int,
    val method: String,
    val password: String,
    val name: String? = null,
    val plugin: String? = null,
    val pluginOpts: String? = null
)

/**
 * Parse ss:// URI into ShadowsocksConfig.
 *
 * Supports:
 * 1) ss://BASE64(method:password@host:port)#Name
 * 2) ss://BASE64(method:password)@host:port/?outline=1
 * 3) ss://method:password@host:port#Name
 */
fun parseShadowsocksUri(uri: String): ShadowsocksConfig? {
    if (!uri.startsWith("ss://")) return null
    val withoutScheme = uri.removePrefix("ss://")

    // --- separate name (#Name) ---
    val hashIndex = withoutScheme.indexOf('#')
    val name = if (hashIndex >= 0) {
        URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8")
    } else null
    val beforeName = if (hashIndex >= 0) {
        withoutScheme.substring(0, hashIndex)
    } else {
        withoutScheme
    }

    // --- remove query (?outline=1, ?type=tcp, etc.) ---
    val queryIndex = beforeName.indexOf('?')
    val mainPart = if (queryIndex >= 0) {
        beforeName.substring(0, queryIndex)
    } else {
        beforeName
    }

    var plugin: String? = null
    var pluginOpts: String? = null

    if (queryIndex >= 0) {
        val query = beforeName.substring(queryIndex + 1)
        val params = query.split('&').associate {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
        }

        // Plugin String ဥပမာ: "v2ray-plugin;server=...;path=/..."
        val rawPlugin = params["plugin"]
        if (rawPlugin != null) {
            val parts = rawPlugin.split(';', limit = 2)
            plugin = parts[0] // "v2ray-plugin"
            pluginOpts = if (parts.size > 1) parts[1] else null // "server=...;path=..."
        }
    }

    // If there's an '@' outside, it means creds + host:port are separated
    val atIndex = mainPart.lastIndexOf('@')
    return if (atIndex >= 0) {
        // Outline-like or plain style
        val left = mainPart.substring(0, atIndex)   // creds (maybe base64(method:pass) or method:pass)
        val right = mainPart.substring(atIndex + 1) // host:port[/]

        val (host, port) = parseHostPort(right) ?: return null

        // Try interpret left as BASE64(method:password)
        val decodedCreds = decodeBase64OrNull(left)
        if (decodedCreds != null && decodedCreds.contains(":")) {
            val colon = decodedCreds.indexOf(':')
            val method = decodedCreds.substring(0, colon)
            val password = decodedCreds.substring(colon + 1)

            ShadowsocksConfig(
                host = host,
                port = port,
                method = method,
                password = password,
                name = name,
                plugin = plugin,
                pluginOpts = pluginOpts
            )
        } else {
            // Fallback: plain "method:password"
            val colon = left.indexOf(':')
            if (colon <= 0) return null
            val method = left.substring(0, colon)
            val password = left.substring(colon + 1)

            ShadowsocksConfig(
                host = host,
                port = port,
                method = method,
                password = password,
                name = name,
                plugin = plugin,
                pluginOpts = pluginOpts
            )
        }
    } else {
        // Legacy style: everything is base64(method:password@host:port)
        val decoded = decodeBase64OrNull(mainPart) ?: return null
        // expected: method:password@host:port
        val methodEnd = decoded.indexOf(':')
        val midAt = decoded.lastIndexOf('@')
        val colonPort = decoded.lastIndexOf(':')

        if (methodEnd <= 0 || midAt <= methodEnd || colonPort <= midAt) return null

        val method = decoded.substring(0, methodEnd)
        val password = decoded.substring(methodEnd + 1, midAt)
        val host = decoded.substring(midAt + 1, colonPort)
        val port = decoded.substring(colonPort + 1).toIntOrNull() ?: return null

        ShadowsocksConfig(
            host = host,
            port = port,
            method = method,
            password = password,
            name = name,
            plugin = plugin,
            pluginOpts = pluginOpts
        )
    }
}

// Base64 decoder that supports URL-safe and missing padding
private fun decodeBase64OrNull(s: String): String? =
    try {
        val padded = when (s.length % 4) {
            2 -> s + "=="
            3 -> s + "="
            else -> s
        }
        val decodedBytes = Base64.decode(
            padded,
            Base64.NO_WRAP or Base64.URL_SAFE
        )
        String(decodedBytes)
    } catch (_: Exception) {
        null
    }

// host:port or host:port/  -> Pair(host, port)
private fun parseHostPort(hostPortRaw: String): Pair<String, Int>? {
    val hostPort = hostPortRaw.trim().trimEnd('/')   // handle trailing '/'
    val idx = hostPort.lastIndexOf(':')
    if (idx <= 0) return null
    val host = hostPort.substring(0, idx)
    val port = hostPort.substring(idx + 1).toIntOrNull() ?: return null
    return host to port
}

/**
 * Build an ss:// URI from your Server model.
 * You can use this to copy/share keys to Outline or other clients.
 *
 * Expected Server fields:
 *  - host, port, method, password, name (optional)
 */
fun buildShadowsocksUri(
    host: String,
    port: Int,
    method: String,
    password: String,
    name: String? = null
): String {
    val userInfo = "$method:$password"
    val base64 = Base64.encodeToString(userInfo.toByteArray(), Base64.NO_WRAP)
    val hostPort = "$host:$port"
    val encodedName = name?.let { URLEncoder.encode(it, "UTF-8") }

    return if (encodedName != null) {
        "ss://$base64@$hostPort#$encodedName"
    } else {
        "ss://$base64@$hostPort"
    }
}
