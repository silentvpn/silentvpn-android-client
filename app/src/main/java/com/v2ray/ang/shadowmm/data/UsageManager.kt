// ✅ UPDATE UsageManager.kt

package com.v2ray.ang.shadowmm.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.v2ray.ang.BuildConfig // ✅ ADD THIS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UsageManager {
    private const val TAG = "UsageManager"

    // ✅ NEW (From BuildConfig):
    private val BASE_URL = BuildConfig.BASE_URL
    private val CLIENT_KEY = BuildConfig.API_KEY

    private val _usageDataFlow = MutableStateFlow<ServerData?>(null)
    val usageDataFlow = _usageDataFlow.asStateFlow()

    // ✅ Increased timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)  // ✅ Increased from 15s
        .readTimeout(30, TimeUnit.SECONDS)     // ✅ Increased from 20s
        .writeTimeout(30, TimeUnit.SECONDS)    // ✅ Increased from 20s
        .retryOnConnectionFailure(true)
        .build()

    data class ServerData(
        val usedMB: Int,
        val displayUsedMB: Int,
        val coins: Int,
        val extraMB: Int,
        val coinsExtraMB: Int,
        val adsExtraMB: Int,
        val totalLimitMB: Int,
        val adsWatched: Int,
        val dailyClaimed: Int,
        val blocked: Int,
        val lastLoginDate: String
    )

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)

        if (deviceId.isNullOrEmpty()) {
            deviceId = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "unknown"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get device ID: ${e.message}")
                "unknown"
            }

            prefs.edit().putString("device_id", deviceId).apply()
            Log.d(TAG, "Generated device ID: $deviceId")
        }

        return deviceId
    }

    fun sync(
        context: Context,
        actionType: String = "sync",
        addMB: Int = 0,
        exchangeMB: Int = 0,
        cost: Int = 0,
        onResult: ((ServerData) -> Unit)? = null
    ) {
        val deviceId = getDeviceId(context)

        Thread {
            try {
                Log.d(TAG, "sync: deviceId=$deviceId, action=$actionType, addMB=$addMB")

                val json = JSONObject().apply {
                    put("device_id", deviceId)
                    put("action_type", actionType)
                    put("add_mb", addMB)
                    put("exchange_mb", exchangeMB)
                    put("cost", cost)
                    put("last_login", getCurrentDate())
                }

                val body = json.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

                val url = "$BASE_URL/usage/ping?key=$CLIENT_KEY"
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    Log.d(TAG, "sync: SUCCESS - $responseBody")

                    val root = JSONObject(responseBody)

                    val result = ServerData(
                        usedMB = root.optInt("used_mb", 0),
                        displayUsedMB = root.optInt("display_used_mb", root.optInt("used_mb", 0)),
                        coins = root.optInt("coins", 0),
                        extraMB = root.optInt("extra_mb", 0),
                        coinsExtraMB = root.optInt("coins_extra_mb", 0),
                        adsExtraMB = root.optInt("ads_extra_mb", 0),
                        totalLimitMB = root.optInt("total_limit_mb", 3072),
                        adsWatched = root.optInt("ads_watched", 0),
                        dailyClaimed = root.optInt("daily_claimed", 0),
                        blocked = root.optInt("blocked", 0),
                        lastLoginDate = root.optString("last_login_date", getCurrentDate())
                    )

                    com.v2ray.ang.handler.MmkvManager.encodeSettings("cache_official_used", result.displayUsedMB)
                    com.v2ray.ang.handler.MmkvManager.encodeSettings("cache_official_limit", result.totalLimitMB)
                    com.v2ray.ang.handler.MmkvManager.encodeSettings("cache_official_extra", result.extraMB)

                    onResult?.let { callback ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback(result)
                        }
                    }
                } else {
                    Log.e(TAG, "sync: FAILED - HTTP ${response.code}: $responseBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "sync: EXCEPTION - ${e.message}", e)
            }
        }.start()
    }

    fun backgroundSync(context: Context) {
        sync(context, actionType = "sync", onResult = null)
        Log.d(TAG, "Background sync completed")
    }

    private fun getCurrentDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    suspend fun syncRequest(
        context: Context,
        actionType: String = "sync",
        addMB: Int = 0,
        exchangeMB: Int = 0,
        cost: Int = 0
    ): ServerData? = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId(context)

        try {
            val json = JSONObject().apply {
                put("device_id", deviceId)
                put("action_type", actionType)
                put("add_mb", addMB)
                put("exchange_mb", exchangeMB)
                put("cost", cost)
                put("last_login", getCurrentDate())
            }

            val body = json.toString().toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url("$BASE_URL/usage/ping?key=$CLIENT_KEY")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val root = JSONObject(responseBody)
                val data = ServerData(
                    usedMB = root.optInt("used_mb", 0),
                    displayUsedMB = root.optInt("display_used_mb", root.optInt("used_mb", 0)),
                    coins = root.optInt("coins", 0),
                    extraMB = root.optInt("extra_mb", 0),
                    coinsExtraMB = root.optInt("coins_extra_mb", 0),
                    adsExtraMB = root.optInt("ads_extra_mb", 0),
                    totalLimitMB = root.optInt("total_limit_mb", 3072),
                    adsWatched = root.optInt("ads_watched", 0),
                    dailyClaimed = root.optInt("daily_claimed", 0),
                    blocked = root.optInt("blocked", 0),
                    lastLoginDate = root.optString("last_login_date", getCurrentDate())
                )

                _usageDataFlow.emit(data)
                return@withContext data
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncRequest Failed: ${e.message}")
        }

        return@withContext null
    }
}