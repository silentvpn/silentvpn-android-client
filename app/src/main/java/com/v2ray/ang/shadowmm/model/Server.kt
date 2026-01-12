package com.v2ray.ang.shadowmm.model

import com.v2ray.ang.dto.EConfigType

data class Server(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,

    val type: ServerType,
    val flag: String,

    val configType: EConfigType,
    var ping: Int = 0,

    // SHADOWSOCKS
    val method: String? = null,
    val password: String? = null,
    val passwordB64: String? = null,

    // VMESS / VLESS ONLY
    val path: String? = null,
    val sni: String? = null,
    val flow: String? = null,
    val network: String = "tcp",
    val tls: Boolean = false,

    // ✅ REALITY
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val fingerprint: String? = null,

    // ✅ TLS / REALITY
    val alpn: String? = null,
    val rawUri: String? = null
)

enum class ServerType {
    OFFICIAL, MANUAL
}

data class UserData(
    var dailyDataUsedMB: Int,
    val baseDailyLimitMB: Int,
    var bonusDataMB: Int,

    var coins: Int,
    var adsWatchedToday: Int,
    val lastAdWatchDate: String?
)

enum class Theme { LIGHT, DARK }

const val OFFICIAL_SERVER_ID = "official-sg-1"