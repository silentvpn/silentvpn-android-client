package com.v2ray.ang.shadowmm.data

import android.content.Context
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.shadowmm.model.Server
import com.v2ray.ang.shadowmm.model.ServerType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple persistence for servers using SharedPreferences + JSON.
 * We mainly care about MANUAL servers, but this can store all servers.
 *
 * ✅ FIXED: Now saves ALL server properties (configType, tls, path, sni, flow)
 */
object ServerStorage {

    private const val PREFS_NAME = "shadowlink_servers"
    private const val KEY_SERVERS = "servers_json"

    var plugin: String? = null
    var pluginOpts: String? = null

    fun loadServers(context: Context): List<Server> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SERVERS, null) ?: return emptyList()

        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<Server>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(obj.toServer())
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveServers(context: Context, servers: List<Server>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        servers.forEach { server ->
            arr.put(server.toJson())
        }
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    // --- helpers --------------------------------------------------------

    private fun Server.toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("host", host)
        obj.put("port", port)
        obj.put("type", type.name)
        obj.put("flag", flag)
        obj.put("method", method ?: JSONObject.NULL)
        obj.put("password", password ?: JSONObject.NULL)
        obj.put("ping", ping ?: JSONObject.NULL)
        // ✅ FIXED: Save all properties to preserve manual servers completely
        obj.put("configType", configType.name)
        obj.put("tls", tls)
        obj.put("path", path ?: JSONObject.NULL)
        obj.put("sni", sni ?: JSONObject.NULL)
        obj.put("flow", flow ?: JSONObject.NULL)
        obj.put("publicKey", publicKey)
        obj.put("shortId", shortId)
        obj.put("fingerprint", fingerprint)
        obj.put("alpn", alpn)
        obj.put("spiderX", spiderX ?: JSONObject.NULL)
        obj.put("network", network)
        obj.put("rawUri", rawUri ?: JSONObject.NULL)
        obj.put("plugin", plugin ?: JSONObject.NULL)
        obj.put("pluginOpts", pluginOpts ?: JSONObject.NULL)

        return obj
    }

    private fun JSONObject.toServer(): Server {
        return Server(
            id = getString("id"),
            name = getString("name"),
            host = getString("host"),
            port = getInt("port"),
            type = ServerType.valueOf(getString("type")),
            flag = optString("flag", "🌐"),
            method = optStringOrNull("method"),
            password = optStringOrNull("password"),
            // ✅ FIXED: Load configType from saved data instead of hardcoding
            configType = try {
                EConfigType.valueOf(optString("configType", EConfigType.SHADOWSOCKS.name))
            } catch (e: Exception) {
                EConfigType.SHADOWSOCKS
            },
            ping = optInt("ping", 0),
            // ✅ FIXED: Load tls, path, sni, flow to restore manual servers completely
            tls = optBoolean("tls", false),
            network = optString("network", "tcp"),
            path = optStringOrNull("path"),
            sni = optStringOrNull("sni"),
            flow = optStringOrNull("flow"),
            publicKey = optStringOrNull("publicKey"),
            shortId = optStringOrNull("shortId"),
            fingerprint = optStringOrNull("fingerprint"),
            spiderX = optStringOrNull("spiderX"),
            rawUri = optStringOrNull("rawUri"),
            alpn = optStringOrNull("alpn"),


        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }
}