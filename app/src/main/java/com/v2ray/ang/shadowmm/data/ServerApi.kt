package com.v2ray.ang.shadowmm.data

import android.util.Log
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.shadowmm.model.Server
import com.v2ray.ang.shadowmm.model.ServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ServerApi {

    private const val TAG = "ServerApi"

    // 🔥 PRIORITY 1: GitHub servers (rarely blocked)
    private val GITHUB_URL = BuildConfig.GITHUB_URL
    private val VPS_URL = BuildConfig.BASE_URL
    private val CLIENT_KEY = BuildConfig.API_KEY

    // Build OkHttpClient with reasonable timeouts and retry
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    /**
     * 🔥 Fetch servers with fallback strategy:
     * 1. Try GitHub (rarely blocked)
     * 2. If fail, try VPS
     * 3. If both fail, return empty list
     */
    suspend fun fetchOfficialServers(): List<Server> = withContext(Dispatchers.IO) {
        // 1️⃣ Try GitHub first
        try {
            Log.d(TAG, "Fetching from GitHub...")
            val githubServers = fetchFromGitHub()
            if (githubServers.isNotEmpty()) {
                Log.d(TAG, "✅ GitHub fetch successful: ${githubServers.size} servers")
                return@withContext githubServers
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub fetch failed: ${e.message}")
        }

        // 2️⃣ Fallback to VPS
        try {
            Log.d(TAG, "Falling back to VPS...")
            val vpsServers = fetchFromVPS()
            if (vpsServers.isNotEmpty()) {
                Log.d(TAG, "✅ VPS fetch successful: ${vpsServers.size} servers")
                return@withContext vpsServers
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPS fetch failed: ${e.message}")
        }

        // 3️⃣ Both failed
        Log.e(TAG, "❌ All server sources failed")
        return@withContext emptyList()
    }

    /**
     * Fetch from GitHub (JSON Array format)
     */
    @Throws(IOException::class)
    private fun fetchFromGitHub(): List<Server> {
        val request = Request.Builder()
            .url(GITHUB_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val bodyString = response.body?.string()
                ?: throw IOException("Empty response body")

            // GitHub format: JSON Array directly
            val jsonArray = JSONArray(bodyString)
            return parseServerArray(jsonArray)
        }
    }

    /**
     * Fetch from VPS (JSON Object with "servers" array)
     */
    @Throws(IOException::class)
    private fun fetchFromVPS(): List<Server> {
        val url = "$VPS_URL/servers?key=$CLIENT_KEY"

        // Try normal fetch
        try {
            return doFetchVPS(url, addCloseHeader = false)
        } catch (e: Exception) {
            Log.w(TAG, "VPS first attempt failed, retrying with Connection: close")
            // Retry with Connection: close
            return doFetchVPS(url, addCloseHeader = true)
        }
    }

    @Throws(IOException::class)
    private fun doFetchVPS(url: String, addCloseHeader: Boolean): List<Server> {
        val requestBuilder = Request.Builder().url(url)
        if (addCloseHeader) {
            requestBuilder.header("Connection", "close")
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val bodyString = response.body?.string()
                ?: throw IOException("Empty response body")

            // VPS format: { "servers": [...] }
            val json = JSONObject(bodyString)
            val arr = json.optJSONArray("servers") ?: return emptyList()

            return parseServerArray(arr)
        }
    }

    /**
     * Parse server array (works for both GitHub and VPS formats)
     */
    private fun parseServerArray(jsonArray: JSONArray): List<Server> {
        val list = mutableListOf<Server>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)

            val typeStr = obj.optString("type", "MANUAL")
            val configTypeStr = obj.optString("configType", "VMESS")

            val server = Server(
                id = obj.optString("id", "server-$i"),
                name = obj.optString("name", "Server $i"),
                host = obj.optString("host", ""),
                port = obj.optInt("port", 0),
                type = if (typeStr == "OFFICIAL") ServerType.OFFICIAL else ServerType.MANUAL,
                flag = obj.optString("flag", "🌐"),
                configType = when (configTypeStr) {
                    "SHADOWSOCKS" -> EConfigType.SHADOWSOCKS
                    "VLESS" -> EConfigType.VLESS
                    "TROJAN" -> EConfigType.TROJAN
                    else -> EConfigType.VMESS

                },

                method = obj.optString("method", ""),
                password = obj.optString("password", obj.optString("uuid", obj.optString("id", ""))),
                tls = obj.optBoolean("tls", false),

                // 🔥 FIX: API ကလာတဲ့ network ကို ယူမယ်၊ မပါရင် tcp ထားမယ်
                network = obj.optString("network", "tcp"),

                path = obj.optString("path", null),
                sni = obj.optString("sni", null),
                flow = obj.optString("flow", null),
                publicKey = obj.optString("publicKey", null),
                shortId = obj.optString("shortId", null),
                fingerprint = obj.optString("fingerprint", null),
                alpn = obj.optString("alpn", null),
                spiderX = obj.optString("spiderX", null)
            )

            list.add(server)
        }

        Log.d(TAG, "Parsed ${list.size} servers")
        return list
    }
}