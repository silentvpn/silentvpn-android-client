package com.v2ray.ang.shadowmm

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.v2ray.ang.AppConfig.TAG
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.shadowmm.data.CoinStorage
import com.v2ray.ang.shadowmm.data.DailyLoginStorage
import com.v2ray.ang.shadowmm.data.RewardStorage
import com.v2ray.ang.shadowmm.data.ServerApi
import com.v2ray.ang.shadowmm.data.ServerStorage
import com.v2ray.ang.shadowmm.data.UsageManager
import com.v2ray.ang.shadowmm.model.*
import com.v2ray.ang.shadowmm.ui.ShadowLinkApp
import com.v2ray.ang.shadowmm.ui.theme.ShadowLinkTheme
import com.v2ray.ang.service.V2RayVpnService
import com.v2ray.ang.shadowmm.utils.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    // VPN running state
    private val vpnPrefs by lazy {
        getSharedPreferences("vpn_state", MODE_PRIVATE)
    }

    // Server selection persistence
    private val serverPrefs by lazy {
        getSharedPreferences("server_prefs", MODE_PRIVATE)
    }

    // Settings persistence (Language & Theme)
    private val settingsPrefs by lazy {
        getSharedPreferences("app_settings", MODE_PRIVATE)
    }

    private fun setVpnRunning(running: Boolean) {
        PrefsHelper.saveMultiple(this, "vpn_state", mapOf("running" to running))
    }

    private fun isVpnMarkedRunning(): Boolean {
        return vpnPrefs.getBoolean("running", false)
    }

    private fun saveLastSelectedServer(serverId: String) {
        serverPrefs.edit().putString("last_server_id", serverId).apply()
    }

    private fun getLastSelectedServerId(): String? {
        val serverId = serverPrefs.getString("last_server_id", null)
        return serverId
    }

    private fun bytesToMB(bytes: Int): Int {
        return if (bytes > 10000) {
            bytes / (1024 * 1024)
        } else {
            bytes
        }
    }

    private var fallbackServer: Server? = null

    private val _isConnected = mutableStateOf(false)
    private val _isConnecting = mutableStateOf(false)
    private val _statusText = mutableStateOf("")

    // Testing States
    private val _testStatus = mutableStateOf("IDLE")
    private val _testResult = mutableStateOf("")

    // Ads
    private var mInterstitialAd: InterstitialAd? = null

    // Data Tracking
    private var sessionStartTime: Long = 0

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) performConnect() else _isConnecting.value = false
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun cleanupOldPrefs() {
        val prefs = getSharedPreferences("vpn_state", MODE_PRIVATE)
        prefs.edit()
            .remove("grace_period_end")
            .remove("grace_period_active")
            .apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cleanupOldPrefs()

        MobileAds.initialize(this) {}
        loadInterstitialAd()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val filter = android.content.IntentFilter("com.v2ray.ang.STOP_VPN_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vpnStopReceiver, filter)
        }

        // 🔥 Check VPN Status strictly on startup (Recent Clear Fix)
        checkVpnStatusAndUpdateUI()

        setContent {
            var isLoading by remember { mutableStateOf(true) }

            // 🔥 FIX: 0 အစား သိမ်းထားတဲ့ Cache ကို ချက်ချင်းဆွဲထုတ်မယ်
            // ဒါဆို Server ကမလာခင်မှာ 0 မပြတော့ဘဲ လက်ရှိရောက်နေတဲ့အတိုင်း ပြနေမယ်
            val cachedUsed = remember {
                getSharedPreferences("vpn_state", Context.MODE_PRIVATE).getInt("saved_used_mb", 0)
            }
            val cachedExtra = 0
            // Coins ကိုတော့ CoinStorage ကနေ ယူမယ် (သို့) 0
            val cachedCoins = remember {
                try {
                    getSharedPreferences("coin_prefs", Context.MODE_PRIVATE).getInt("coins", 0)
                } catch (e: Exception) { 0 }
            }

            // State တွေကို Cache နဲ့ စမယ်
            var serverSyncedDataUsage by remember { mutableIntStateOf(cachedUsed) }
            var extraDataMB by remember { mutableIntStateOf(cachedExtra) }
            var coins by remember { mutableIntStateOf(cachedCoins) }
            var adsWatched by remember { mutableIntStateOf(0) }

            // ✅ 4-SECOND SYNC: GitHub + API
            LaunchedEffect(Unit) {
                var callbackCalled = false

                // Fetch servers in parallel
                launch {
                    try { ServerApi.fetchOfficialServers() } catch (_: Exception) {}
                }

                // Sync User Data
                launch {
                    delay(100)
                    val extraUsedMb = consumeBackgroundUsedMb()
                    val extraUsedMbConverted = bytesToMB(extraUsedMb)

                    UsageManager.sync(this@MainActivity, addMB = extraUsedMbConverted) { serverData ->
                        if (!isLoading) return@sync
                        callbackCalled = true

                        serverSyncedDataUsage = bytesToMB(serverData.usedMB)
                        extraDataMB = serverData.extraMB
                        coins = serverData.coins
                        adsWatched = serverData.adsWatched

                        CoinStorage.saveCoins(this@MainActivity, serverData.coins)

                        val currentRewards = RewardStorage.loadState(this@MainActivity)
                        RewardStorage.saveState(
                            this@MainActivity,
                            currentRewards.copy(
                                extraDataTodayMB = serverData.extraMB,
                                adWatchCountToday = serverData.adsWatched
                            )
                        )

                        // Check dailyClaimed to solve FLICKER
                        if (serverData.dailyClaimed == 1) {
                            DailyLoginStorage.setClaimedToday(this@MainActivity)
                        } else {
                            DailyLoginStorage.clearToday(this@MainActivity)
                        }

                        isLoading = false
                    }

                    // Timeout 4 seconds
                    delay(4000)
                    if (!callbackCalled && isLoading) {
                        isLoading = false
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF3F51B5))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Syncing with Server...", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                MainAppContent(
                    initialUsed = serverSyncedDataUsage,
                    initialExtra = extraDataMB,
                    initialCoins = coins,
                    initialAds = adsWatched
                )
            }
        }
    }

    // Helper function to check VPN status
    private fun isVpnServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (V2RayVpnService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    // Helper function to enforce UI state if VPN is running
    private fun checkVpnStatusAndUpdateUI() {
        runOnUiThread {
            if (isVpnServiceRunning()) {
                if (!_isConnected.value) {
                    Log.d("MainActivity", "✅ VPN Service detected! Restoring UI state...")
                    _isConnected.value = true
                    _statusText.value = "Connected"
                    setVpnRunning(true)

                    // Restore test status
                    val savedTestStatus = vpnPrefs.getString("test_status", "IDLE")
                    _testStatus.value = savedTestStatus ?: "IDLE"
                }
            } else {
                if (_isConnected.value) {
                    Log.d("MainActivity", "❌ VPN Service died. Updating UI...")
                    setVpnRunning(false)
                    _isConnected.value = false
                    _statusText.value = "Not Connected"
                }
            }

        }

    }

    // Detect when App is re-opened
    override fun onResume() {
        super.onResume()
        checkVpnStatusAndUpdateUI()
        if (_isConnected.value) {
            lifecycleScope.launch {
                try {
                    UsageManager.sync(this@MainActivity) { }
                } catch (e: Exception) {}
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // 🔥 FIX: Unregister Receiver to prevent leaks
        try {
            unregisterReceiver(vpnStopReceiver)
        } catch (e: Exception) {
            // Already unregistered or not registered
        }
    }

    @Composable
    private fun MainAppContent(
        initialUsed: Int,
        initialExtra: Int,
        initialCoins: Int,
        initialAds: Int
    ) {
        // 1. Context & Prefs (Declare ONCE at top)
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)

        // 2. State Definitions (Declare variables first)
        var activeServerId by remember { mutableStateOf("loading") }
        var activeServerName by remember { mutableStateOf("Loading...") }

        // Load last known server type preference
        val isLastKnownOfficial = remember {
            val guid = MmkvManager.getSelectServer()
            if (!guid.isNullOrEmpty()) {
                val config = MmkvManager.decodeServerConfig(guid)
                config?.subscriptionId == "OFFICIAL" // Config ရှိရင် အဲ့ဒါကို အတည်ယူမယ်
            } else {
                prefs.getBoolean("current_is_official", false) // မရှိမှ Prefs ကြည့်မယ်
            }
        }

        // Define activeServerType state (Use MANUAL instead of CUSTOM)
        var activeServerType by remember { mutableStateOf(if (isLastKnownOfficial) ServerType.OFFICIAL else ServerType.MANUAL) }

        // Derived state for easy check
        val isOfficialServer = activeServerType == ServerType.OFFICIAL

        // Other States
        var activeServerHost by remember { mutableStateOf("127.0.0.1") }
        var activeServerPort by remember { mutableStateOf(10808) }
        var activeServerMethod by remember { mutableStateOf("chacha20-ietf-poly1305") }
        var activeServerPassword by remember { mutableStateOf("") }
        var activeServerConfigType by remember { mutableStateOf(EConfigType.SHADOWSOCKS) }
        var activeServerFlag by remember { mutableStateOf("⏳") }

        var liveServerUsed by remember { mutableStateOf(initialUsed) }
        var liveDisplayUsed by remember { mutableStateOf(initialUsed) }
        var liveExtraMB by remember { mutableStateOf(initialExtra) }
        var liveCoins by remember { mutableStateOf(initialCoins) }
        var liveAds by remember { mutableStateOf(initialAds) }

        // 🔥 FIXED: Load saved limit
        val savedLimit = prefs.getInt("saved_total_limit", 3072)
        var liveTotalLimit by remember { mutableIntStateOf(savedLimit) }

        // 3. LaunchedEffects & Logic
        LaunchedEffect(Unit) {
            val savedRewards = RewardStorage.loadState(context) // Use 'context' variable
            liveExtraMB = savedRewards.extraDataTodayMB
            liveAds = savedRewards.adWatchCountToday
        }

        var showLimitDialog by remember { mutableStateOf(false) }
        val activity = context as? androidx.activity.ComponentActivity
        val intent = activity?.intent

        LaunchedEffect(Unit) {
            if (intent?.getBooleanExtra("SHOW_LIMIT_DIALOG", false) == true) {
                showLimitDialog = true
                intent.removeExtra("SHOW_LIMIT_DIALOG")
            }
        }

        // Connection States
        var isConnectedState by remember { mutableStateOf(_isConnected.value) }
        var isConnectingState by remember { mutableStateOf(_isConnecting.value) }

        LaunchedEffect(Unit) {
            snapshotFlow { _isConnected.value }.collect { isConnectedState = it }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { _isConnecting.value }.collect { isConnectingState = it }
        }
        // 🔥 FIX: VPN ချိတ်လိုက်တာနဲ့ Data Usage ကို ချက်ချင်းဆွဲတင်မယ် (Delay မဖြစ်အောင်)
        LaunchedEffect(isConnectedState) {
            if (isConnectedState) {
                delay(500) // Connection ငြိမ်သွားအောင် ခဏစောင့်
                try {
                    UsageManager.sync(context) { serverData ->
                        liveServerUsed = bytesToMB(serverData.usedMB)
                        liveDisplayUsed = bytesToMB(serverData.displayUsedMB)
                        liveExtraMB = serverData.extraMB
                        liveCoins = serverData.coins
                        liveAds = serverData.adsWatched
                        liveTotalLimit = serverData.totalLimitMB

                        // Save latest limit
                        prefs.edit()
                            .putInt("saved_total_limit", serverData.totalLimitMB)
                            .apply()
                    }
                } catch (e: Exception) { Log.e("Sync", "Immediate sync failed", e) }
            }
        }

        // Server Loading Logic
        var officialServers by remember { mutableStateOf<List<Server>>(emptyList()) }
        var serversLoaded by remember { mutableStateOf(false) }

        val activeServer = remember(
            activeServerId, activeServerName, activeServerType,
            activeServerHost, activeServerPort
        ) {
            Server(
                id = activeServerId,
                name = activeServerName,
                host = activeServerHost,
                port = activeServerPort,
                type = activeServerType,
                flag = activeServerFlag,
                configType = activeServerConfigType,
                method = activeServerMethod,
                password = activeServerPassword,
                tls = false
            )
        }

        // Restore Connected Server UI
        LaunchedEffect(isConnectedState, serversLoaded) {
            if (isConnectedState) {
                val connectedGuid = MmkvManager.getSelectServer()
                if (!connectedGuid.isNullOrEmpty()) {
                    val config = MmkvManager.decodeServerConfig(connectedGuid)
                    if (config != null) {
                        if (activeServerName == "Loading..." || activeServerId == "loading") {
                            activeServerId = "restored_${config.remarks.hashCode()}"
                            activeServerName = config.remarks ?: "Connected Server"
                            activeServerHost = config.server ?: ""
                            activeServerPort = config.serverPort?.toIntOrNull() ?: 10808
                            activeServerMethod = config.method ?: "chacha20-ietf-poly1305"
                            activeServerPassword = config.password ?: ""
                            activeServerConfigType = config.configType

                            // 🔥 GLITCH FIX: Type ကိုပါ Config ကနေ ချက်ချင်းဆွဲတင်မယ် (API စောင့်စရာမလိုတော့ဘူး)
                            // subscriptionId ထဲမှာ "OFFICIAL" (သို့) "MANUAL" သိမ်းထားတာကို စစ်မယ်
                            if (config.subscriptionId == "OFFICIAL") {
                                activeServerType = ServerType.OFFICIAL

                                // Cache ကိုပါ ချက်ချင်းပြ (Unlimited မပြတော့အောင်)
                                val prefs = context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
                                val cachedUsed = prefs.getInt("saved_used_mb", 0)
                                val cachedLimit = prefs.getInt("saved_total_limit", 3072)
                                liveDisplayUsed = cachedUsed
                                liveTotalLimit = cachedLimit
                            } else {
                                activeServerType = ServerType.MANUAL
                                liveTotalLimit = Int.MAX_VALUE
                            }
                            // Don't overwrite activeServerType here, logic below handles it
                            if (!serversLoaded) serversLoaded = true
                        }
                    }
                }
            }
        }

        // Fetch & Load Servers
        LaunchedEffect(Unit) {
            if (!serversLoaded || officialServers.isEmpty()) {
                try {
                    val fetched = ServerApi.fetchOfficialServers()

                    if (fetched.isNotEmpty()) {
                        val existingServers = ServerStorage.loadServers(context)

                        // Clean duplicates
                        val storedGuids = MmkvManager.decodeServerList()
                        storedGuids.forEach { guid ->
                            val config = MmkvManager.decodeServerConfig(guid)
                            if (config != null && fetched.any { it.name == config.remarks }) {
                                MmkvManager.removeServer(guid)
                            }
                        }

                        val manualServers = existingServers.filter { it.type == ServerType.MANUAL }
                        val allServers = fetched + manualServers
                        ServerStorage.saveServers(context, allServers)

                        officialServers = fetched
                        val lastServerId = getLastSelectedServerId()

                        val isVpnConnected = _isConnected.value
                        val connectedGuid = if (isVpnConnected) MmkvManager.getSelectServer() else null
                        val connectedConfig = if (!connectedGuid.isNullOrEmpty()) {
                            MmkvManager.decodeServerConfig(connectedGuid)
                        } else {
                            null
                        }

                        val serverToSelect = when {
                            connectedConfig != null -> {
                                val connectedServerId = "server-${connectedConfig.remarks.hashCode()}"
                                val foundServer = allServers.find { it.id == connectedServerId }
                                    ?: allServers.find { it.name == connectedConfig.remarks }

                                foundServer ?: Server(
                                    id = connectedServerId,
                                    name = connectedConfig.remarks ?: "Connected Server",
                                    host = connectedConfig.server ?: "unknown",
                                    port = connectedConfig.serverPort?.toIntOrNull() ?: 0,
                                    type = if (fetched.any { it.name == connectedConfig.remarks }) ServerType.OFFICIAL else ServerType.MANUAL,
                                    flag = "🌐",
                                    configType = connectedConfig.configType,
                                    method = connectedConfig.method,
                                    password = connectedConfig.password,
                                    tls = connectedConfig.security == "tls",
                                    path = connectedConfig.path,
                                    sni = connectedConfig.sni,
                                    flow = connectedConfig.flow
                                )
                            }
                            lastServerId != null -> {
                                allServers.find { it.id == lastServerId } ?: fetched.first()
                            }
                            else -> fetched.first()
                        }

                        activeServerId = serverToSelect.id
                        activeServerName = serverToSelect.name
                        activeServerType = serverToSelect.type
                        activeServerHost = serverToSelect.host
                        activeServerPort = serverToSelect.port
                        activeServerMethod = serverToSelect.method ?: "chacha20-ietf-poly1305"
                        activeServerPassword = serverToSelect.password ?: ""
                        activeServerConfigType = serverToSelect.configType
                        activeServerFlag = serverToSelect.flag

                        serversLoaded = true

                        if (!_isConnected.value) {
                            setupV2RayConfig(serverToSelect)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Error: ${e.message}", e)
                }
            }
        }
        LaunchedEffect(liveExtraMB) {
            if (activeServerType == ServerType.OFFICIAL) {
                // အကယ်၍ liveTotalLimit က 3072 ဖြစ်နေပြီး Extra က 150 ဆိုရင် -> 3222 ဖြစ်အောင် ပေါင်းထည့်မယ်
                val baseLimit = 3072 // သို့မဟုတ် Server ကလာတဲ့ Base
                if (liveTotalLimit < baseLimit + liveExtraMB) {
                    liveTotalLimit = baseLimit + liveExtraMB
                }
            }
        }

        // 4. Background Sync & Real-time Listener (LAG & SYNC FIX)
        LaunchedEffect(Unit) {
            // (က) Real-time Listener: Rewards Screen က Data ပြောင်းရင် ဒီမှာချက်ချင်းသိမယ်
            launch {
                UsageManager.usageDataFlow.collect { data ->
                    if (data != null) {
                        // UI Variables Update
                        liveServerUsed = bytesToMB(data.usedMB)

                        // Glitch Fix: 0 ဖြစ်မသွားအောင် စစ်ဆေးခြင်း
                        if (data.displayUsedMB > 0 || data.totalLimitMB > 0) {
                            liveDisplayUsed = bytesToMB(data.displayUsedMB)
                            liveTotalLimit = data.totalLimitMB
                            liveExtraMB = data.extraMB
                            liveCoins = data.coins
                            liveAds = data.adsWatched
                            PrefsHelper.saveMultiple(context, "vpn_state", mapOf(
                                "saved_used_mb" to liveDisplayUsed,
                                "saved_total_limit" to liveTotalLimit,
                                "usage_ready" to true
                            ))
                        }

                        liveTotalLimit = data.totalLimitMB

                        // Manual Server Override
                        if (activeServerType == ServerType.MANUAL) {
                            liveTotalLimit = Int.MAX_VALUE
                            liveDisplayUsed = 0
                        }

                        // Save State
                        CoinStorage.saveCoins(context, data.coins)

                        // Auto-Disconnect Logic
                        /*if (activeServerType == ServerType.OFFICIAL) {
                            val totalLimit = liveTotalLimit
                            if (isConnectedState && liveDisplayUsed >= totalLimit) {
                                val graceEndTime = prefs.getLong("grace_period_end", 0L)
                                val currentTime = System.currentTimeMillis()

                                if (currentTime < graceEndTime) {
                                    Log.d("MainActivity", "In Grace Period...")
                                } else {
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        try {
                                            Toast.makeText(context, "⚠️ Daily Limit Reached!", Toast.LENGTH_LONG).show()
                                            V2RayServiceManager.stopVService(context)
                                            resetUI()
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        }*/
                    }
                }
            }

            // (ခ) Periodic Sync: ၁၀ စက္ကန့်တခါ Server ကို လှမ်းဆွဲမယ် (Thread Safe)
            launch {
                while (isActive) {
                    try {
                        // syncRequest (Suspend function) ကိုသုံးလို့ Main Thread မပိတ်တော့ပါ
                        UsageManager.syncRequest(context)
                    } catch (e: Exception) {
                        Log.e("Sync", "Loop error: ${e.message}")
                    }
                    delay(10000) // 10 Seconds Delay
                }
            }
        }

        val savedLang = settingsPrefs.getString("language", "en")
        val savedTheme = settingsPrefs.getString("theme", "light")

        var settings by remember {
            mutableStateOf(
                AppSettings(
                    theme = if (savedTheme == "dark") Theme.DARK else Theme.LIGHT,
                    language = if (savedLang == "mm") Language.MM else Language.EN
                )
            )
        }

        //val baseDailyLimit = 3072
        val totalLimit = liveTotalLimit
        // Using isOfficialServer (derived state) for logic
        val isLimitReached =
            isOfficialServer &&
                    (liveDisplayUsed >= liveTotalLimit)

        val todayKey = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }

        var warningCount by remember { mutableIntStateOf(prefs.getInt("warning_count", 0)) }
        val savedDate = remember { prefs.getString("warning_date", "") }

        LaunchedEffect(Unit) {
            if (savedDate != todayKey) {
                warningCount = 0
                prefs.edit().putString("warning_date", todayKey).putInt("warning_count", 0).apply()
            }
        }
        val usagePercent = if (liveTotalLimit > 0) (liveDisplayUsed.toFloat() / liveTotalLimit.toFloat()) * 100 else 0f

        val showWarningBox = isOfficialServer && (usagePercent >= 80)

        val onGetMoreDataClick: () -> Unit = {
            // ၁။ အကြိမ်အရေအတွက် တိုးမယ် (၃ ခါပြည့်ရင် button မပေါ်တော့ဘူး)
            val newCount = warningCount + 1
            warningCount = newCount
            prefs.edit().putInt("warning_count", newCount).apply()

            // ၂။ VPN ချိတ်တဲ့ ကုဒ်တွေ (Grace Time, Stop Service, Initiate) အကုန်ဖြုတ်လိုက်ပါ
            // Screen ပြောင်းတဲ့အလုပ်ကို ShadowMMApp.kt က လုပ်ပေးပါလိမ့်မယ်။

            // Optional: User သိအောင် Toast လေး ပြချင်ရင် ထားလို့ရပါတယ် (မထားလဲရပါတယ်)
            Toast.makeText(context, "Redirecting to Rewards...", Toast.LENGTH_SHORT).show()
        }

        /*if (showLimitDialog) {
            AlertDialog(
                onDismissRequest = { showLimitDialog = false },
                title = { Text("⚠️ Limit Reached") },
                text = { Text("Your daily data limit has been reached. VPN is disconnected.\n\nGet more data now to reconnect?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                        onClick = {
                            showLimitDialog = false
                            // 🔥 15s မပေးတော့ဘူး၊ Rewards Screen ကိုပဲ ညွှန်ပေးမယ်
                            onGetMoreDataClick()
                        }
                    ) { Text("Get More Data") }
                },
                dismissButton = {
                    TextButton(onClick = { showLimitDialog = false }) { Text("Close") }
                }
            )
        }*/

        ShadowLinkTheme(darkTheme = settings.theme == Theme.DARK) {
            key(activeServerId, activeServerName, activeServerType) {
                ShadowLinkApp(
                    servers = officialServers,
                    currentServer = activeServer,
                    isConnected = isConnectedState,
                    isConnecting = isConnectingState,
                    statusText = _statusText.value,
                    testStatus = _testStatus.value,
                    testResult = _testResult.value,
                    userData = UserData(
                        dailyDataUsedMB = if (isOfficialServer) {
                            if (liveDisplayUsed > liveTotalLimit) liveTotalLimit else liveDisplayUsed
                        } else {
                            0
                        },

                        baseDailyLimitMB = if (isOfficialServer) {
                            liveTotalLimit   // base only (3072)
                        } else {
                            Int.MAX_VALUE
                        },
                        bonusDataMB = 0,
                        coins = liveCoins,
                        adsWatchedToday = liveAds,
                        lastAdWatchDate = ""
                    ),
                    settings = settings,
                    onSelectServer = { newServer ->
                        if (_isConnected.value) {
                            try { V2RayServiceManager.stopVService(context) } catch (_: Exception) {}
                            resetUI()
                        }

                        activeServerId = newServer.id
                        activeServerName = newServer.name
                        activeServerType = newServer.type
                        activeServerHost = newServer.host
                        activeServerPort = newServer.port
                        activeServerMethod = newServer.method ?: "chacha20-ietf-poly1305"
                        activeServerPassword = newServer.password ?: ""
                        activeServerConfigType = newServer.configType
                        activeServerFlag = newServer.flag

                        // 🔥 FIX: UI Glitch ပျောက်အောင် State ချက်ချင်းပြောင်းမယ်
                        if (newServer.type == ServerType.MANUAL) {
                            // Manual ဆိုရင် Unlimited ပြောင်း
                            liveTotalLimit = Int.MAX_VALUE
                            liveDisplayUsed = 0
                            liveExtraMB = 0
                        } else {
                            // Official ဆိုရင် သိမ်းထားတဲ့ Usage အဟောင်းကို ချက်ချင်းပြ (API မလာခင်)
                            val prefs = context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
                            val cachedUsed = prefs.getInt("saved_used_mb", 0)
                            val cachedLimit = prefs.getInt("saved_total_limit", 3072)
                            val cachedExtra = MmkvManager.decodeSettingsInt("cache_official_extra", 0)

                            liveDisplayUsed = cachedUsed
                            liveTotalLimit = cachedLimit
                            liveExtraMB = 0


                            // Save Official State for Service
                            MmkvManager.encodeSettings("current_is_official", true)
                            vpnPrefs.edit().putBoolean("current_is_official", true).commit()
                        }

                        saveLastSelectedServer(newServer.id)
                        setupV2RayConfig(newServer)
                    },
                    onToggleTheme = {
                        val newTheme = if (settings.theme == Theme.LIGHT) Theme.DARK else Theme.LIGHT
                        settings = settings.copy(theme = newTheme)
                        settingsPrefs.edit()
                            .putString("theme", if (newTheme == Theme.DARK) "dark" else "light")
                            .apply()
                    },
                    onChangeLanguage = { lang ->
                        settings = settings.copy(language = lang)
                        settingsPrefs.edit()
                            .putString("language", if (lang == Language.MM) "mm" else "en")
                            .apply()
                    },
                    onAddServer = { server ->
                        if (activeServerId == "loading") {
                            activeServerId = server.id
                            activeServerName = server.name
                            activeServerType = server.type
                            activeServerHost = server.host
                            activeServerPort = server.port
                            activeServerMethod = server.method ?: ""
                            activeServerPassword = server.password ?: ""
                            activeServerConfigType = server.configType
                            activeServerFlag = server.flag
                            saveLastSelectedServer(server.id)
                            setupV2RayConfig(server)
                        }
                    },
                    onRenameServer = { server, newName ->
                        if (activeServerId == server.id) {
                            activeServerName = newName
                        }
                    },
                    onDeleteServer = { server ->
                        if (activeServerId == server.id && officialServers.isNotEmpty()) {
                            val firstServer = officialServers.firstOrNull() ?: return@ShadowLinkApp
                            activeServerId = firstServer.id
                            activeServerName = firstServer.name
                            activeServerType = firstServer.type
                            activeServerHost = firstServer.host
                            activeServerPort = firstServer.port
                            activeServerMethod = firstServer.method ?: "chacha20-ietf-poly1305"
                            activeServerPassword = firstServer.password ?: ""
                            activeServerConfigType = firstServer.configType
                            activeServerFlag = firstServer.flag
                            saveLastSelectedServer(firstServer.id)
                            setupV2RayConfig(firstServer)
                        }
                    },
                    onStartStopClick = {
                        if (isOfficialServer) {
                            val prefs = getSharedPreferences("vpn_state", MODE_PRIVATE)

                            // ၁။ ရက်စွဲ စစ်ဆေးခြင်း (Date Check)
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val today = dateFormat.format(java.util.Date())
                            val lastServiceDate = prefs.getString("last_service_date", "")

                            // 🔥 FIX: ရက်မတူတော့ရင် (ရက်ကူးသွားရင်) Data အဟောင်းကို မသုံးဘဲ 0 လုပ်မယ်
                            if (today != lastServiceDate) {
                                // UI မှာ 0 ပြောင်းမယ်
                                liveDisplayUsed = 0
                                // Service ကိုလည်း 0 လို့ ပြောလိုက်မယ် (ဒါမှ Service က Limit Reached လို့ မထင်တော့မှာ)
                                MmkvManager.encodeSettings("live_used_mb", 0)

                                // Limit ကိုတော့ ပုံမှန်အတိုင်း Update လုပ်မယ်
                                val freshLimit = prefs.getInt("saved_total_limit", liveTotalLimit)
                                liveTotalLimit = freshLimit
                                MmkvManager.encodeSettings("live_total_limit", freshLimit)

                            } else {
                                // ၂။ ရက်တူနေရင်တော့ ရှိပြီးသား Data အတိုင်း ပုံမှန် Sync လုပ်မယ်
                                val freshLimit = prefs.getInt("saved_total_limit", liveTotalLimit)
                                val freshUsed = prefs.getInt("saved_used_mb", liveDisplayUsed)

                                liveTotalLimit = freshLimit
                                liveDisplayUsed = freshUsed

                                // Data ဝယ်ထားရင် ပြန်ချိတ်ခွင့်ပြုတဲ့ Logic
                                val was95Disconnected = prefs.getBoolean("disconnected_95", false)
                                if (was95Disconnected && freshUsed < freshLimit) {
                                    // User bought data, allow reconnect
                                }

                                // Update MMKV
                                MmkvManager.encodeSettings("live_total_limit", freshLimit)
                                MmkvManager.encodeSettings("live_used_mb", freshUsed)
                            }
                        }

                        fallbackServer = activeServer

                        // ၃။ Connection အဖွင့်/အပိတ် လုပ်မယ်
                        toggleConnection(isOfficialServer,
                            isOfficialServer && (liveDisplayUsed >= liveTotalLimit))
                    },
                    onTestConnectionClick = { performTestWithAd() },
                    showWarningBox = showWarningBox,
                    onGetMoreDataClick = onGetMoreDataClick
                )
            }
        }
    }

    private fun resetUI() {
        _isConnected.value = false
        _isConnecting.value = false
        _statusText.value = ""
        setVpnRunning(false)
        _testStatus.value = "IDLE"
        _testResult.value = ""
    }

    // 🔥 FIXED: Button Logic
    private fun toggleConnection(isOfficialServer: Boolean, isLimitReached: Boolean) {
        if (_isConnected.value) { performDisconnect(); return }

        if (isOfficialServer && isLimitReached) {
            Toast.makeText(this, "Daily limit reached!", Toast.LENGTH_SHORT).show();
            return
        }

        MmkvManager.encodeSettings("current_is_official", isOfficialServer)

        initiateConnection()
    }

    private fun initiateConnection() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            performConnect()
        }
    }

    private fun setupV2RayConfig(server: Server): Boolean {
        return try {
            val guid = UUID.nameUUIDFromBytes(server.id.toByteArray()).toString()

            // 1. Security Logic (VLESS/VMess)
            val security = when (server.configType) {
                EConfigType.SHADOWSOCKS -> "none"

                EConfigType.VLESS -> when {
                    !server.publicKey.isNullOrEmpty() -> "reality"
                    server.tls -> "tls"
                    else -> "none"
                }

                EConfigType.VMESS -> if (server.tls) "tls" else "auto"
                EConfigType.TROJAN -> "tls" // 🔥 Trojan is always TLS

                else -> "none"
            }

            val officialMarker = if (server.type == ServerType.OFFICIAL) "OFFICIAL" else "MANUAL"

            val finalMethod = when (server.configType) {
                EConfigType.SHADOWSOCKS -> if (server.method.isNullOrEmpty()) "chacha20-ietf-poly1305" else server.method
                EConfigType.TROJAN -> "none"
                EConfigType.VLESS -> "none"  // 🔥 VLESS ဆိုရင် "none" အသေပေးမယ်
                EConfigType.VMESS -> "auto"  // VMess ဆိုရင် "auto"
                else -> "none"
            }

            val profile = ProfileItem(
                configType = server.configType,
                remarks = server.name,
                server = server.host,
                serverPort = server.port.toString(),

                method = finalMethod,

                password = server.password ?: "",
                subscriptionId = officialMarker,

                // 🔥 FIX A: Network ကို Server ကလာတဲ့အတိုင်း သုံးမယ် (TCP အသေမထားနဲ့)
                network = server.network,

                headerType = "none",
                security = security,
                path = server.path,
                sni = server.sni,
                flow = server.flow,
                // ✅ REALITY
                publicKey = server.publicKey,
                shortId = server.shortId,
                spiderX = server.spiderX,
                fingerprint = server.fingerprint
            )

            MmkvManager.encodeServerConfig(guid, profile)
            MmkvManager.setSelectServer(guid)

            // 🔥 FIX B: Data Usage တက်ဖို့ ဒီကောင် လိုကိုလိုပါတယ် (Service က MMKV ကိုဖတ်လို့ပါ)
            val isOfficial = (server.type == ServerType.OFFICIAL)
            MmkvManager.encodeSettings("current_is_official", isOfficial)

            // Backup အနေနဲ့ Prefs ထဲလည်း ထည့်မယ်
            vpnPrefs.edit().putBoolean("current_is_official", isOfficial).commit()

            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Config Setup Failed", e)
            false
        }
    }

    private fun performConnect() {
        _isConnecting.value = true
        _isConnected.value = false
        _statusText.value = "Connecting..."
        _testStatus.value = "IDLE"
        sessionStartTime = System.currentTimeMillis()

        val prefs = getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
        var guid = MmkvManager.getSelectServer()

        // 🔥 ROBUST FIX: Recent Clear လုပ်ပြီးရင် GUID ပျောက်သွားတတ်တယ်
        // ဒါကြောင့် GUID မရှိရင်၊ နောက်ဆုံးရွေးခဲ့တဲ့ Server ID (Storage) ကိုသုံးပြီး Config ပြန်ဆောက်မယ်
        if (guid.isNullOrEmpty()) {
            try {
                // 1. နောက်ဆုံးရွေးခဲ့တဲ့ ID ကို Disk ထဲကနေ ယူမယ်
                val lastId = getLastSelectedServerId()

                if (lastId != null) {
                    // 2. Server List အပြည့်အစုံကို Disk ထဲကနေ ဆွဲထုတ်မယ် (API စောင့်စရာမလို)
                    val allServers = ServerStorage.loadServers(this)

                    // 3. ID တူတဲ့ Server ကို ရှာမယ်
                    val targetServer = allServers.find { it.id == lastId }

                    if (targetServer != null) {
                        Log.d("MainActivity", "♻️ Restoring config for: ${targetServer.name}")

                        // 4. Config ပြန်ဆောက်မယ် (ဒါဆိုရင် GUID အသစ်ရပြီ)
                        setupV2RayConfig(targetServer)
                        guid = MmkvManager.getSelectServer()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Auto-restore failed", e)
            }
        }

        // Config ရှိ/မရှိ စစ်ဆေးပြီး ချိတ်မယ်
        if (!guid.isNullOrEmpty()) {
            V2RayServiceManager.startVService(this, guid)
        } else {
            _isConnecting.value = false
            Toast.makeText(this, "Please select a server first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            delay(2000)
            if (isVpnServiceRunning()) {
                _isConnecting.value = false
                _isConnected.value = true
                _statusText.value = "Connected"
                setVpnRunning(true)
                Toast.makeText(this@MainActivity, "Connected!", Toast.LENGTH_SHORT).show()
                performActualTest(showAd = true)
            } else {
                _isConnecting.value = false
                _isConnected.value = false
                _statusText.value = "Failed"
                _testStatus.value = "FAIL"
                setVpnRunning(false)
                Toast.makeText(this@MainActivity, "Connection failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performDisconnect() {
        // 🔥 FIX: UI ကို ချက်ချင်း Reset လုပ်မယ် (Sync ကို မစောင့်တော့ဘူး)
        // ဒါမှ Button Freeze မဖြစ်မှာ
        lifecycleScope.launch {
            try {
                // 1. Stop Service Immediately
                withTimeoutOrNull(3000) {
                    V2RayServiceManager.stopVService(this@MainActivity)
                } ?: Log.w(TAG, "Stop service timeout")

                // 2. Stop Service Intent explicitly
                val intent = android.content.Intent(this@MainActivity, V2RayVpnService::class.java)
                stopService(intent)

                // 3. Reset UI Instantly
                resetUI()
                vpnPrefs.edit().putString("test_status", "IDLE").apply()
                setVpnRunning(false)
                Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()

                // 4. Sync က နောက်ကွယ်မှာ အေးဆေးလုပ်ပါစေ
                withContext(Dispatchers.IO) {
                    try { UsageManager.syncRequest(this@MainActivity) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                // Error တက်ရင်လည်း UI ကို အတင်း Reset ချမယ်
                resetUI()
            }
        }
    }

    private fun performTestWithAd() {
        if (!_isConnected.value) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }
        // ✅ FIXED: Using correct parameter
        performActualTest(showAd = true)
    }

    // ✅ FIXED: Renamed parameter to 'showAd' and handled both Success/Fail cases
    private fun performActualTest(showAd: Boolean = false) {
        lifecycleScope.launch {
            try {
                _testStatus.value = "TESTING"
                _testResult.value = "Testing connection..."

                val result = withContext(Dispatchers.IO) {
                    testProxyConnection()
                }

                if (result) {
                    _testStatus.value = "SUCCESS"
                    _testResult.value = "✅ Connection OK"
                    vpnPrefs.edit().putString("test_status", "SUCCESS").apply()
                } else {
                    _testStatus.value = "FAIL"
                    _testResult.value = "❌ Connection Failed"
                    vpnPrefs.edit().putString("test_status", "FAIL").apply()
                    _isConnected.value = false
                    setVpnRunning(false)
                }

                // ✅ Ad will show for BOTH Success and Fail if showAd is true
                if (showAd) {
                    delay(1500)
                    loadAndShowInterstitialAd { }
                }

            } catch (e: Exception) {
                _testStatus.value = "FAIL"
                _testResult.value = "❌ Test Error"
                _isConnected.value = false
                setVpnRunning(false)
                vpnPrefs.edit().putString("test_status", "FAIL").apply()
            }
        }
    }

    private val vpnStopReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.v2ray.ang.STOP_VPN_ACTION") {
                Log.d("MainActivity", "Received VPN STOP Signal")
                resetUI() // UI ကို Disconnect အနေအထား ပြောင်းမယ်
            }
        }
    }

    private suspend fun testProxyConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
            val url = URL("https://www.google.com")
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) { false }
    }

    private fun loadAndShowInterstitialAd(onDismiss: () -> Unit) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    loadInterstitialAd()
                    onDismiss()
                }
            }
        } else {
            onDismiss()
            loadInterstitialAd()
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) { mInterstitialAd = interstitialAd }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) { mInterstitialAd = null }
            }
        )
    }

    private fun consumeBackgroundUsedMb(): Int {
        val prefs = getSharedPreferences("background_usage", MODE_PRIVATE)
        val pending = prefs.getInt("pending_mb", 0)
        prefs.edit().putInt("pending_mb", 0).apply()
        return pending
    }
    @Composable
    fun LimitWarningBox(onGetMoreData: () -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), // အနီရောင်ဖျော့ဖျော့
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp) // Orange Area နေရာစာယူမယ်
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠️ Daily Limit Reached",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "VPN is disconnected. Get 15s connection to buy data?",
                    color = Color.Black.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onGetMoreData,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)), // အစိမ်းရောင် ခလုတ်
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Get More Data", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}