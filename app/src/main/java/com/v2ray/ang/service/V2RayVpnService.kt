package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils
import java.lang.ref.SoftReference
import android.net.TrafficStats
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.v2ray.ang.shadowmm.data.UsageManager
import com.v2ray.ang.shadowmm.utils.PrefsHelper // ✅ ADD THIS

private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
@Volatile private var usageSyncRunning = false

private var lastRxBytes = 0L
private var lastTxBytes = 0L

class V2RayVpnService : VpnService(), ServiceControl {
    private lateinit var mInterface: ParcelFileDescriptor
    private var isRunning = false
    private var tun2SocksService: Tun2SocksControl? = null

    private var lastKnownLimit = 0
    private var lastKnownUsed = 0

    // ✅ ADDED: Mutex for thread-safe operations
    private val syncMutex = Mutex()

    private val usagePrefs by lazy {
        getSharedPreferences("vpn_state", MODE_PRIVATE)
    }

    private fun isOfficialCurrentServer(): Boolean {
        return MmkvManager.decodeSettingsBool("current_is_official", false)
    }

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val connectivity by lazy {
        getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        V2RayServiceManager.serviceControl = SoftReference(this)
    }

    override fun onRevoke() {
        stopV2Ray()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUsageSyncLoop()
        stopV2Ray(true)

        // ✅ FIX: Unregister network callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (ignored: Exception) {
                Log.w(AppConfig.TAG, "Failed to unregister network callback")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        NotificationManager.cancelNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val usagePrefs = getSharedPreferences("vpn_state", MODE_PRIVATE)

        // 🔥 FIX 1: နေ့ရက်အသစ် ဖြစ်/မဖြစ် အရင်ဆုံး စစ်မယ် (Data မဖတ်ခင် လုပ်ရမယ်)
        if (isNewDay(usagePrefs)) {
            Log.d("V2RayService", "📅 NEW DAY DETECTED - Force Resetting Everything")

            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val today = dateFormat.format(java.util.Date())

            // 🔥 commit() ကိုသုံးပါ (apply မသုံးပါနဲ့) - ချက်ချင်း သိမ်းဖို့လိုလို့ပါ
            usagePrefs.edit()
                .putString("last_service_date", today)
                .putBoolean("warning_80_shown", false)
                .putBoolean("disconnected_95", false)
                .putBoolean("disconnected_100", false)
                .putInt("saved_used_mb", 0) // Usage ကို 0 ချက်ချင်းလုပ်မယ်
                .commit()

            lastKnownUsed = 0 // Memory ထဲမှာလည်း 0 လုပ်မယ်
        }
        // 1. Load latest limit from both sources
        val prefsLimit = usagePrefs.getInt("saved_total_limit", 0)
        val mmkvLimit = MmkvManager.decodeSettingsInt("live_total_limit", 0)
        val savedTotal = maxOf(prefsLimit, mmkvLimit)
        val savedUsed = usagePrefs.getInt("saved_used_mb", 0)

        Log.d("V2RayService", "📊 Service Start: Used=$savedUsed, Limit=$savedTotal")

        if (savedTotal > 0) {
            lastKnownLimit = savedTotal
            lastKnownUsed = savedUsed

            checkAndResetDailyFlags(usagePrefs)
        }

        if (intent == null) {
            val guid = MmkvManager.getSelectServer()
            if (!guid.isNullOrEmpty()) {
                V2RayServiceManager.startCoreLoop()
                setupService()
                val config = MmkvManager.decodeServerConfig(guid)
                NotificationManager.showNotification(config)
            } else {
                stopSelf()
                NotificationManager.cancelNotification()
                return START_NOT_STICKY
            }
        } else {
            if (V2RayServiceManager.startCoreLoop()) {
                startService()
            }
        }

        return START_STICKY
    }

    private fun isNewDay(prefs: SharedPreferences): Boolean {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = dateFormat.format(java.util.Date())
        val lastDate = prefs.getString("last_service_date", "")
        return lastDate != today
    }

    private fun checkAndResetDailyFlags(prefs: SharedPreferences) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = dateFormat.format(java.util.Date())
        val lastResetDate = prefs.getString("last_flag_reset_date", "")

        if (lastResetDate != today) {
            Log.d("V2RayService", "📅 Daily Reset: Clearing flags")
            prefs.edit()
                .putString("last_flag_reset_date", today)
                .putBoolean("warning_80_shown", false)
                .putBoolean("disconnected_95", false)
                .apply()
        }
    }

    override fun getService(): Service = this

    override fun startService() {
        setupService()
    }

    override fun stopService() {
        stopV2Ray(true)
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    private fun setupService() {
        val prepare = prepare(this)
        if (prepare != null) {
            return
        }

        if (configureVpnService() != true) {
            return
        }

        runTun2socks()
    }

    private fun configureVpnService(): Boolean {
        val builder = Builder()
        configureNetworkSettings(builder)
        configurePerAppProxy(builder)

        try {
            mInterface.close()
        } catch (ignored: Exception) {
        }

        configurePlatformFeatures(builder)

        try {
            mInterface = builder.establish()!!
            isRunning = true
            startUsageSyncLoop()
            return true
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to establish VPN interface", e)
            stopV2Ray()
        }
        return false
    }

    private fun configureNetworkSettings(builder: Builder) {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val bypassLan = SettingsManager.routingRulesetsBypassLan()

        builder.setMtu(SettingsManager.getVpnMtu())
        builder.addAddress(vpnConfig.ipv4Client, 30)

        if (bypassLan) {
            AppConfig.ROUTED_IP_LIST.forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true) {
            builder.addAddress(vpnConfig.ipv6Client, 126)
            if (bypassLan) {
                builder.addRoute("2000::", 3)
                builder.addRoute("fc00::", 18)
            } else {
                builder.addRoute("::", 0)
            }
        }

        SettingsManager.getVpnDnsServers().forEach {
            if (Utils.isPureIpAddress(it)) {
                builder.addDnsServer(it)
            }
        }

        builder.setSession(V2RayServiceManager.getRunningServerName())
    }

    private fun configurePlatformFeatures(builder: Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to request default network", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                builder.setHttpProxy(
                    ProxyInfo.buildDirectProxy(LOOPBACK, SettingsManager.getHttpPort())
                )
            }
        }
    }

    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        if (bypassApps) apps.add(selfPackageName) else apps.remove(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    builder.addDisallowedApplication(it)
                } else {
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(AppConfig.TAG, "Failed to configure app in VPN: ${e.localizedMessage}", e)
            }
        }
    }

    // ✅ UPDATE startUsageSyncLoop() - Fixed warning logic
    private fun startUsageSyncLoop() {
        if (usageSyncRunning) return
        usageSyncRunning = true

        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        var accumulatedBytes = 0L

        val liveLimit = MmkvManager.decodeSettingsInt("live_total_limit", 0)
        if (liveLimit > 0) {
            lastKnownLimit = liveLimit
        } else {
            lastKnownLimit = usagePrefs.getInt("saved_total_limit", 3072)
        }

        lastKnownUsed = usagePrefs.getInt("saved_used_mb", 0)

        Log.d(AppConfig.TAG, "✅ Sync Loop Init: Used=$lastKnownUsed, Limit=$lastKnownLimit")

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        var lastCheckedDate = dateFormat.format(java.util.Date())

        syncScope.launch {
            Log.d(AppConfig.TAG, "✅ Usage Sync Loop Started")

            while (usageSyncRunning) {
                try {
                    delay(5_000) // 5 second interval

                    // 0. ✅ Check for date change during runtime
                    val currentDate = dateFormat.format(java.util.Date())
                    if (currentDate != lastCheckedDate) {
                        Log.d(AppConfig.TAG, "📅 DATE CHANGED DURING RUNTIME!")

                        usagePrefs.edit()
                            .putString("last_service_date", currentDate)
                            .putBoolean("warning_80_shown", false)
                            .putBoolean("disconnected_95", false)
                            .putBoolean("disconnected_100", false)
                            .putInt("saved_used_mb", 0)
                            .apply()

                        // ✅ CRITICAL: Reset memory
                        lastKnownUsed = 0
                        accumulatedBytes = 0L
                        lastCheckedDate = currentDate

                        Log.d(AppConfig.TAG, "✅ Memory reset: lastKnownUsed=0")
                        showToast("📅 Daily Reset Complete")
                        stopV2Ray()  // Disconnect and let user reconnect
                        break
                    }

                    val freshLimit = usagePrefs.getInt("saved_total_limit", 0)
                    if (freshLimit > lastKnownLimit) {
                        Log.d(AppConfig.TAG, "🎁 Data Purchase! Limit: $lastKnownLimit → $freshLimit")
                        lastKnownLimit = freshLimit

                        // Reset flags
                        usagePrefs.edit()
                            .putBoolean("warning_80_shown", false)
                            .putBoolean("disconnected_95", false)
                            .putBoolean("disconnected_100", false)
                            .apply()

                        Log.d(AppConfig.TAG, "✅ Flags reset after data purchase")
                    }

                    // 2. Day Reset Detection (Server-side)
                    val savedUsed = usagePrefs.getInt("saved_used_mb", lastKnownUsed)
                    if (lastKnownUsed > 100 && savedUsed < 10) {
                        // Server reset usage (probably new day on server)
                        Log.d(AppConfig.TAG, "🔄 Server-side Reset Detected!")
                        showToast("🔄 Server Daily Reset")

                        lastKnownUsed = 0
                        accumulatedBytes = 0L

                        usagePrefs.edit()
                            .putInt("saved_used_mb", 0)
                            .putBoolean("warning_80_shown", false)
                            .putBoolean("disconnected_95", false)
                            .putBoolean("disconnected_100", false)
                            .apply()

                        stopV2Ray()
                        break
                    }

                    // 3. Manual Server Check
                    val isOfficial = isOfficialCurrentServer()
                    if (!isOfficial) {
                        lastRxBytes = TrafficStats.getTotalRxBytes()
                        lastTxBytes = TrafficStats.getTotalTxBytes()
                        accumulatedBytes = 0L
                        continue
                    }

                    // 4. Traffic Measurement
                    val newRx = TrafficStats.getTotalRxBytes()
                    val newTx = TrafficStats.getTotalTxBytes()
                    val diffRx = if (newRx >= lastRxBytes) newRx - lastRxBytes else 0L
                    val diffTx = if (newTx >= lastTxBytes) newTx - lastTxBytes else 0L
                    lastRxBytes = newRx
                    lastTxBytes = newTx
                    val deltaBytes = (diffRx + diffTx).coerceAtLeast(0L)
                    accumulatedBytes += deltaBytes

                    val currentUsedMB = lastKnownUsed + (accumulatedBytes / (1024 * 1024)).toInt()

                    // 5. ✅ WARNING & DISCONNECT LOGIC (FIXED)
                    if (lastKnownLimit > 0 && currentUsedMB > 0) {
                        val percent = if (lastKnownLimit > 0) {
                            (currentUsedMB.toFloat() / lastKnownLimit.toFloat()) * 100
                        } else {
                            0f
                        }

                        // Read flags
                        val warning80Shown = usagePrefs.getBoolean("warning_80_shown", false)
                        val disconnected95 = usagePrefs.getBoolean("disconnected_95", false)
                        val disconnected100 = usagePrefs.getBoolean("disconnected_100", false)

                        // Debug log
                        if (currentUsedMB % 50 == 0) { // Log every 50MB
                            Log.d(AppConfig.TAG, "📊 ${currentUsedMB}MB/$lastKnownLimit MB (${"%.1f".format(percent)}%) | Flags: 80=$warning80Shown, 95=$disconnected95, 100=$disconnected100")
                        }

                        when {
                            // ✅ 100% - FINAL DISCONNECT
                            currentUsedMB >= lastKnownLimit -> {
                                Log.w(AppConfig.TAG, "🚫 100% LIMIT!")
                                showToast("⚠️ Daily Data Limit Reached!")

                                if (!disconnected100) {
                                    NotificationManager.showLimitReachedAlert()
                                    usagePrefs.edit()
                                        .putBoolean("disconnected_100", true)
                                        .apply()
                                }

                                stopV2Ray()
                                break
                            }

                            percent >= 95 -> {
                                if (disconnected95) {
                                    // 🔥 FIX: Flag က True ဖြစ်နေပြီဆိုရင် ထပ်မဖြတ်တော့ဘူး။
                                    // User က ပြန်ချိတ်ချင်လို့ ချိတ်ထားတာဖြစ်မယ်။ 100% ထိ ဆက်သုံးခွင့်ပြုမယ်။
                                    Log.d(AppConfig.TAG, "✅ 95% Flag is TRUE. Ignoring disconnect.")
                                } else {
                                    // Flag က False (ဒီနေ့အတွက် တခါမှ မဖြတ်ရသေး) ဆိုမှ ဖြတ်မယ်
                                    Log.w(AppConfig.TAG, "⚠️ 95% Limit! Auto-disconnecting (First time today)")
                                    showToast("⚠️ 95% Limit! Auto-disconnecting...")
                                    NotificationManager.showUsageWarning(95)

                                    usagePrefs.edit()
                                        .putBoolean("disconnected_95", true) // Flag မှတ်လိုက်ပြီ
                                        .putBoolean("warning_80_shown", true)
                                        .apply()

                                    stopV2Ray() // VPN ပိတ်မယ်
                                    break
                                }
                            }

                            // ✅ 80% - WARNING ONLY (Once per day, NO disconnect)
                            percent >= 80 -> {
                                val warning80 = usagePrefs.getBoolean("warning_80_shown", false)
                                if (!warning80) {
                                    showToast("⚠️ Warning: Data limit near (80%)")
                                    usagePrefs.edit().putBoolean("warning_80_shown", true).apply()
                                }
                            }
                        }
                    }

                    // 6. Server Sync (if accumulated data)
                    val usedMb = (accumulatedBytes / (1024 * 1024)).toInt()
                    if (usedMb > 0) {
                        var mbToSend = usedMb
                        if (lastKnownLimit > 0) {
                            val remaining = lastKnownLimit - lastKnownUsed
                            if (remaining <= 0) mbToSend = 0
                            else if (mbToSend > remaining) mbToSend = remaining
                        }

                        if (mbToSend > 0) {
                            lastKnownUsed += mbToSend
                            val serverData = UsageManager.syncRequest(applicationContext, addMB = mbToSend)
                            if (serverData != null) {
                                lastKnownLimit = serverData.totalLimitMB
                                lastKnownUsed = serverData.displayUsedMB
                                usagePrefs.edit()
                                    .putInt("saved_total_limit", lastKnownLimit)
                                    .putInt("saved_used_mb", lastKnownUsed)
                                    .apply()
                                accumulatedBytes -= (mbToSend.toLong() * 1024 * 1024)
                            } else {
                                lastKnownUsed -= mbToSend
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Sync Loop Error: ${e.message}")
                }
            }

            Log.d(AppConfig.TAG, "❌ Usage Sync Loop Stopped")
        }
    }

    private fun stopUsageSyncLoop() {
        usageSyncRunning = false
        syncScope.coroutineContext.cancelChildren()
    }

    private fun runTun2socks() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true) == true) {
            tun2SocksService = TProxyService(
                context = applicationContext,
                vpnInterface = mInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        } else {
            tun2SocksService = Tun2SocksService(
                context = applicationContext,
                vpnInterface = mInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        }
        tun2SocksService?.startTun2Socks()
    }

    private fun stopV2Ray(isForced: Boolean = true) {
        stopUsageSyncLoop()

        val intent = Intent("com.v2ray.ang.STOP_VPN_ACTION")
        intent.setPackage(packageName)
        sendBroadcast(intent)

        isRunning = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (ignored: Exception) {
            }
        }

        tun2SocksService?.stopTun2Socks()
        tun2SocksService = null

        V2RayServiceManager.stopCoreLoop()

        if (isForced) {
            stopSelf()
            try {
                mInterface.close()
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to close VPN interface", e)
            }
        }
    }

    private fun showToast(message: String) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to show toast", e)
        }
    }
}