package com.v2ray.ang.shadowmm.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.shadowmm.data.CoinStorage
import com.v2ray.ang.shadowmm.data.DailyLoginStorage
import com.v2ray.ang.shadowmm.data.RewardStorage
import com.v2ray.ang.shadowmm.data.UsageManager
import com.v2ray.ang.shadowmm.model.Strings
import com.v2ray.ang.shadowmm.model.UserData
import com.v2ray.ang.shadowmm.utils.PrefsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_ADS_PER_DAY = 4
private const val AD_REWARD_MB = 150

@Composable
fun RewardsScreen(
    strings: Strings,
    userData: UserData,
    darkTheme: Boolean,
    isConnected: Boolean,
    testStatus: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // States for Loading Logic
    var isLoading by remember { mutableStateOf(true) }
    var canAccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Try to sync with server (Direct Internet Check)
        // Give it 3 seconds timeout logic manually
        val startTime = System.currentTimeMillis()

        UsageManager.sync(context) { data ->
            // If sync succeeds (we got data), it means we have internet (VPN or Direct)
            if (data.totalLimitMB > 0) { // Just a check that data is valid
                canAccess = true
            }
            isLoading = false
        }

        // Safety timeout: If sync takes too long or fails silently, after 3s stop loading
        delay(3000)
        if (isLoading) {
            isLoading = false // If still loading after 3s, stop and assume failure if canAccess is false
        }
    }

    // 1. Show Loading Screen
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(if(darkTheme) Color(0xFF141D30) else Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF3F7DFF))
        }
        return
    }

    // 2. If Access Failed (No Internet / API Error) -> Show "Connect VPN" Msg
    if (!canAccess && !isConnected) {
        VpnRequiredMessage(
            strings = strings,
            darkTheme = darkTheme,
            isConnected = isConnected,
            testStatus = testStatus
        )
        return
    }

    // 3. Show Content
    RewardsContent(
        strings = strings,
        userData = userData,
        darkTheme = darkTheme,
        scope = scope
    )
}

@Composable
private fun VpnRequiredMessage(
    strings: Strings,
    darkTheme: Boolean,
    isConnected: Boolean,
    testStatus: String
) {
    val gradTop = if (darkTheme) Color(0xFF141D30) else Color(0xFFE9EDFF)
    val gradBottom = if (darkTheme) Color(0xFF0D1425) else Color(0xFFF4F6FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(gradTop, gradBottom))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF1B2235) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(if (darkTheme) Color(0xFF2E3650) else Color(0xFFFFE5E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isConnected) "⚠️" else "🔒", fontSize = 40.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = when {
                        !isConnected -> strings.titleNotConnected
                        testStatus == "TESTING" -> strings.titleTesting
                        testStatus == "FAIL" -> strings.titleFail
                        testStatus == "IDLE" -> strings.titleIdle
                        else -> strings.titleDefault
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = if (darkTheme) Color.White else Color(0xFF1A1A1A)),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when {
                        !isConnected -> strings.msgNotConnected
                        testStatus == "TESTING" -> strings.msgTesting
                        testStatus == "FAIL" -> strings.msgFail
                        testStatus == "IDLE" -> strings.msgIdle
                        else -> strings.msgDefault
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, color = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFF6D6F8A), lineHeight = 22.sp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (testStatus == "FAIL") Color(0xFFFFCDD2) else if (darkTheme) Color(0xFF2E3650) else Color(0xFFF5F5F5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Status:", style = MaterialTheme.typography.labelSmall.copy(color = if (darkTheme) Color(0xFF9BA3C2) else Color.Gray, fontSize = 11.sp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                !isConnected -> strings.statusDisconnected
                                testStatus == "TESTING" -> strings.statusTesting
                                testStatus == "FAIL" -> strings.statusFail
                                testStatus == "IDLE" -> strings.statusIdle
                                testStatus == "SUCCESS" -> strings.statusSuccess
                                else -> strings.statusReady
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = when {
                                !isConnected -> Color(0xFFE53935)
                                testStatus == "FAIL" -> Color(0xFFFF6F00)
                                testStatus == "IDLE" -> Color(0xFFFF9800)
                                testStatus == "SUCCESS" -> Color(0xFF00C853)
                                else -> if (darkTheme) Color.White else Color(0xFF1A1A1A)
                            })
                        )
                    }
                }
            }
        }
    }
}

// ... imports

@Composable
private fun RewardsContent(
    strings: Strings,
    userData: UserData,
    darkTheme: Boolean,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE) }

    val gradTop = if (darkTheme) Color(0xFF141D30) else Color(0xFFE9EDFF)
    val gradBottom = if (darkTheme) Color(0xFF0D1425) else Color(0xFFF4F6FA)

    val savedRewardState = remember { RewardStorage.loadState(context) }

    var coins by remember { mutableStateOf(userData.coins) }

    // 🔥 FIX: Initial Value တွေကို Prefs ကနေယူမယ် (0 ဖြစ်ပြီး Glitch မဖြစ်အောင်)
    var totalExtraMB by remember { mutableStateOf(savedRewardState.extraDataTodayMB) }
    var coinsExtraMB by remember { mutableStateOf(prefs.getInt("saved_coins_extra", 0)) }
    var adsExtraMB by remember { mutableStateOf(prefs.getInt("saved_ads_extra", 0)) }

    var adsWatched by remember { mutableStateOf(savedRewardState.adWatchCountToday) }
    var usedMB by remember { mutableStateOf(userData.dailyDataUsedMB) }

    // ... (Unlimited Logic & Timer Logic remains same) ...
    val isUnlimitedInitial = userData.baseDailyLimitMB == Int.MAX_VALUE
    var isUnlimited by remember { mutableStateOf(isUnlimitedInitial) }
    val baseLimit = userData.baseDailyLimitMB
    val totalLimit = if (isUnlimited) {
        0
    } else {
        baseLimit + totalExtraMB
    }
    val displayUsedMB = if (!isUnlimited && usedMB > totalLimit) {
        totalLimit
    } else {
        usedMB
    }
    var dailyLimit by remember { mutableStateOf(prefs.getInt("saved_total_limit", userData.baseDailyLimitMB)) }

    val todayKey = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    var isClaimedLocally by remember(todayKey) { mutableStateOf(DailyLoginStorage.isClaimedToday(context)) }
    var mInterstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var pendingDailyClaim by remember { mutableStateOf(false) }
    var showExchangeConfirmDialog by remember { mutableStateOf(false) }
    var selectedExchangeOption by remember { mutableStateOf<Pair<Int, Int>?>(null) }


    // Helper Functions
    fun applyAdReward() {
        if (adsWatched >= MAX_ADS_PER_DAY) return
        adsWatched += 1

        adsExtraMB += AD_REWARD_MB
        totalExtraMB += AD_REWARD_MB
        dailyLimit += AD_REWARD_MB
        userData.adsWatchedToday = adsWatched
        userData.bonusDataMB = totalExtraMB

        RewardStorage.saveState(context, savedRewardState.copy(
            extraDataTodayMB = totalExtraMB,
            adWatchCountToday = adsWatched
        ))

        val newTotalLimit = userData.baseDailyLimitMB + totalExtraMB

        // ✅ CRITICAL: Reset ALL flags when purchasing data
        val prefs = context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("saved_ads_extra", adsExtraMB)
            .putInt("saved_total_limit", newTotalLimit)
            .putBoolean("warning_80_shown", false)
            .putBoolean("disconnected_95", false)
            .putBoolean("disconnected_100", false)  // ✅ New flag
            .apply()

        MmkvManager.encodeSettings("live_total_limit", newTotalLimit)
        MmkvManager.encodeSettings("live_used_mb", displayUsedMB)

        scope.launch {
            val data = UsageManager.syncRequest(context, actionType = "ad_watch", addMB = AD_REWARD_MB)
            if (data != null) {
                coins = data.coins
                totalExtraMB = data.extraMB
                adsExtraMB = data.adsExtraMB
                coinsExtraMB = data.coinsExtraMB

                val finalLimit = userData.baseDailyLimitMB + data.extraMB
                prefs.edit()
                    .putInt("saved_ads_extra", data.adsExtraMB)
                    .putInt("saved_coins_extra", data.coinsExtraMB)
                    .putInt("saved_total_limit", finalLimit)
                    .apply()

                MmkvManager.encodeSettings("live_total_limit", finalLimit)
            }
        }

        Toast.makeText(context, "+$AD_REWARD_MB MB Added!", Toast.LENGTH_SHORT).show()
    }

    // ... (applyDailyClaimReward remains same) ...
    fun applyDailyClaimReward() {
        if (isClaimedLocally) return
        coins += 20
        userData.coins = coins
        isClaimedLocally = true
        DailyLoginStorage.setClaimedToday(context)
        CoinStorage.saveCoins(context, coins)
        Toast.makeText(context, "+20 Coins Claimed!", Toast.LENGTH_SHORT).show()
        UsageManager.sync(context, actionType = "daily_claim") { data -> coins = data.coins }
    }

    fun performExchange() {
        val (mb, cost) = selectedExchangeOption ?: return
        coins -= cost

        coinsExtraMB += mb
        totalExtraMB += mb
        userData.coins = coins
        userData.bonusDataMB = totalExtraMB

        CoinStorage.saveCoins(context, coins)
        RewardStorage.saveState(context, savedRewardState.copy(extraDataTodayMB = totalExtraMB))

        val newTotalLimit = userData.baseDailyLimitMB + totalExtraMB

        // ✅ Reset flags
        val prefs = context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("saved_coins_extra", coinsExtraMB)
            .putInt("saved_total_limit", newTotalLimit)
            .putBoolean("warning_80_shown", false)
            .putBoolean("disconnected_95", false)
            .putBoolean("disconnected_100", false)
            .apply()

        MmkvManager.encodeSettings("live_total_limit", newTotalLimit)

        scope.launch {
            val data = UsageManager.syncRequest(context, actionType = "exchange", exchangeMB = mb, cost = cost)
            if (data != null) {
                coins = data.coins
                totalExtraMB = data.extraMB
                coinsExtraMB = data.coinsExtraMB
                adsExtraMB = data.adsExtraMB

                val finalLimit = userData.baseDailyLimitMB + data.extraMB
                prefs.edit()
                    .putInt("saved_ads_extra", data.adsExtraMB)
                    .putInt("saved_coins_extra", data.coinsExtraMB)
                    .putInt("saved_total_limit", finalLimit)
                    .apply()

                MmkvManager.encodeSettings("live_total_limit", finalLimit)
            }
        }

        Toast.makeText(context, "+$mb MB Exchanged!", Toast.LENGTH_SHORT).show()
        showExchangeConfirmDialog = false
        selectedExchangeOption = null
    }

    // ... (loadAd logic remains same) ...
    fun loadAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, "ca-app-pub-3940256099942544/1033173712", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                mInterstitialAd = null
                pendingDailyClaim = false
                showExchangeConfirmDialog = false
            }
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    var isRewardProcessing = false
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        if (isRewardProcessing) return
                        isRewardProcessing = true
                        if (pendingDailyClaim) {
                            pendingDailyClaim = false
                            applyDailyClaimReward()
                        } else {
                            applyAdReward()
                        }
                        loadAd()
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        mInterstitialAd = null
                        pendingDailyClaim = false
                        isRewardProcessing = false
                        loadAd()
                    }
                }
            }
        })
    }

    LaunchedEffect(todayKey) {
        loadAd()
        UsageManager.sync(context) { data ->
            coins = data.coins
            totalExtraMB = data.extraMB

            // 🔥 Update UI & Save to Prefs
            coinsExtraMB = data.coinsExtraMB
            adsExtraMB = data.adsExtraMB
            prefs.edit()
                .putInt("saved_coins_extra", data.coinsExtraMB)
                .putInt("saved_ads_extra", data.adsExtraMB)
                .putInt("saved_total_limit", data.totalLimitMB)
                .apply()

            adsWatched = data.adsWatched
            usedMB = data.displayUsedMB
            dailyLimit = data.totalLimitMB

            if (data.dailyClaimed == 0) {
                isClaimedLocally = false
                DailyLoginStorage.clearToday(context)
            } else {
                isClaimedLocally = true
                DailyLoginStorage.setClaimedToday(context)
            }
            // ... (Rest of UserData updates)
            userData.coins = data.coins
            userData.bonusDataMB = data.extraMB
            userData.adsWatchedToday = data.adsWatched
            RewardStorage.saveState(context, savedRewardState.copy(extraDataTodayMB = data.extraMB, adWatchCountToday = data.adsWatched))
            CoinStorage.saveCoins(context, data.coins)
        }
    }

    // ... (Rest of UI functions remain same: handleAdWatched, handleDailyLoginClaim, onExchangeClick) ...
    fun handleAdWatched() {
        if (adsWatched >= MAX_ADS_PER_DAY) {
            Toast.makeText(context, strings.limitReached, Toast.LENGTH_SHORT).show()
            return
        }
        pendingDailyClaim = false
        if (mInterstitialAd != null) mInterstitialAd?.show(context as Activity)
        else Toast.makeText(context, "Ad not ready", Toast.LENGTH_SHORT).show()
    }

    fun handleDailyLoginClaim() {
        if (isClaimedLocally) {
            Toast.makeText(context, "Already Claimed Today", Toast.LENGTH_SHORT).show()
            return
        }
        pendingDailyClaim = true
        if (mInterstitialAd != null) mInterstitialAd?.show(context as Activity)
        else Toast.makeText(context, "Ad not ready", Toast.LENGTH_SHORT).show()
    }

    fun onExchangeClick(mb: Int, cost: Int) {
        if (coins < cost) {
            Toast.makeText(context, strings.insufficientCoins, Toast.LENGTH_SHORT).show()
            return
        }
        selectedExchangeOption = mb to cost
        showExchangeConfirmDialog = true
    }


    // UI Structure
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(gradTop, gradBottom))), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Text(text = strings.rewards, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (darkTheme) Color(0xFFE5E8FF) else Color(0xFF1F2340))
            Spacer(Modifier.height(16.dp))

            // 🔥 Display Correct Variables
            BalanceCardSection(coins, displayUsedMB, adsExtraMB, coinsExtraMB, dailyLimit, isUnlimited, strings, darkTheme)

            Spacer(Modifier.height(20.dp))
            DailyAdRewardSection(adsWatched, strings, darkTheme) { handleAdWatched() }
            Spacer(Modifier.height(20.dp))
            DailyLoginSection(darkTheme, isClaimedLocally, strings) { handleDailyLoginClaim() }
            Spacer(Modifier.height(20.dp))
            ExchangeCoinSection(darkTheme, strings) { mb, cost -> onExchangeClick(mb, cost) }
            Spacer(Modifier.height(80.dp))
        }

        if (showExchangeConfirmDialog && selectedExchangeOption != null) {
            val (mb, cost) = selectedExchangeOption!!
            AlertDialog(
                onDismissRequest = { showExchangeConfirmDialog = false },
                title = { Text(text = strings.alertExchange) },
                text = {
                    Text(
                        text = String.format(
                            strings.exchangeConfirm,
                            cost,
                            mb
                        )
                    )
                },
                confirmButton = { TextButton(onClick = { performExchange() }) { Text(strings.confirm, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { showExchangeConfirmDialog = false }) { Text(strings.cancel, color = Color.Gray) } },
                containerColor = if (darkTheme) Color(0xFF1B2235) else Color.White,
                titleContentColor = if (darkTheme) Color.White else Color.Black,
                textContentColor = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFF6D6F8A)
            )
        }
    }
}

// ... (Sub Composables: BalanceCardSection, DailyAdRewardSection, etc. remain the same)
@Composable
private fun BalanceCardSection(coins: Int, used: Int, adData: Int, exchangedData: Int, totalLimit: Int, isUnlimited: Boolean, strings: Strings, darkTheme: Boolean) {
    val cardColor = if (darkTheme) Color(0xFF3F7DFF) else Color(0xFF3F7DFF)
    val accent = Color(0xFFFFF176)
    val textColor = Color.White
    val subTextColor = Color(0xFFE5ECFF)

    Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Coins & Rewards", fontSize = 14.sp, color = subTextColor)
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text("$", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = cardColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(coins.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text("Coins", fontSize = 14.sp, color = subTextColor)
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(text = "• ${strings.extraData}: $adData MB", fontSize = 14.sp, color = subTextColor, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(text = "• ${strings.exchangedData}: $exchangedData MB ${strings.noResetNote}", fontSize = 14.sp, color = subTextColor, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isUnlimited)
                        "• ${strings.dataUsed}: Unlimited"
                    else
                        "• ${strings.dataUsed}: $used MB",
                    fontSize = 14.sp, color = subTextColor, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isUnlimited) "${strings.todayLimit}: Unlimited" else "${strings.todayLimit}: $totalLimit MB",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor
            )
        }
    }
}

@Composable
private fun DailyAdRewardSection(watched: Int, strings: Strings, darkTheme: Boolean, onWatchAd: () -> Unit) {
    val canWatchMore = watched < MAX_ADS_PER_DAY
    Surface(shape = RoundedCornerShape(16.dp), color = if (darkTheme) Color(0xFF1B2235) else Color.White, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(strings.dailyRewardTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (darkTheme) Color.White else Color(0xFF22243E))
            Spacer(Modifier.height(4.dp))
            Text(strings.dailyRewardDesc, fontSize = 13.sp, color = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFF6D6F8A))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onWatchAd, enabled = canWatchMore, shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = if (!canWatchMore) Color.Gray else if (darkTheme) Color(0xFF5D4BFF) else Color(0xFF3F7DFF)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                val btnText = if (!canWatchMore) strings.limitReached else strings.watchAd
                Text("$btnText ($watched/$MAX_ADS_PER_DAY)", color = Color.White)
            }
        }
    }
}

@Composable
private fun DailyLoginSection(darkTheme: Boolean, claimed: Boolean, strings: Strings, onClaim: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (darkTheme) Color(0xFF1B2235) else Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(strings.dailyLoginTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (darkTheme) Color.White else Color(0xFF22243E))
            Spacer(Modifier.height(8.dp))
            Text(strings.dailyLoginDesc, fontSize = 13.sp, color = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFF6D6F8A))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClaim, enabled = !claimed, shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = if (claimed) Color.Gray else if (darkTheme) Color(0xFF5D4BFF) else Color(0xFF3F7DFF)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (claimed) strings.claimed else strings.claimCoins, color = Color.White)
            }
        }
    }
}

@Composable
private fun ExchangeCoinSection(darkTheme: Boolean, strings: Strings, onExchange: (Int, Int) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (darkTheme) Color(0xFF1B2235) else Color.White, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(strings.exchangeTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (darkTheme) Color.White else Color(0xFF22243E))
            Spacer(Modifier.height(4.dp))
            Text(strings.exchangeDesc, fontSize = 13.sp, color = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFF6D6F8A))
            Spacer(Modifier.height(8.dp))
            ExchangeRow("450 MB", "60 Coins", darkTheme) { onExchange(450, 60) }
            HorizontalDivider(color = if (darkTheme) Color(0xFF252C42) else Color(0xFFE3E5F3))
            ExchangeRow("1050 MB", "100 Coins", darkTheme) { onExchange(1050, 100) }
            HorizontalDivider(color = if (darkTheme) Color(0xFF252C42) else Color(0xFFE3E5F3))
            ExchangeRow("1650 MB", "160 Coins", darkTheme) { onExchange(1650, 160) }
            HorizontalDivider(color = if (darkTheme) Color(0xFF252C42) else Color(0xFFE3E5F3))
            ExchangeRow("2350 MB", "200 Coins", darkTheme) { onExchange(2350, 200) }
        }
    }
}

@Composable
private fun ExchangeRow(label: String, cost: String, darkTheme: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (darkTheme) Color(0xFF2E3650) else Color(0xFFFFF3CD)), contentAlignment = Alignment.Center) {
            Text("$", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = if (darkTheme) Color(0xFFFFD54F) else Color(0xFFFFC107))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (darkTheme) Color.White else Color(0xFF22243E))
            Text(cost, fontSize = 12.sp, color = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFF6D6F8A))
        }
        Text("›", fontSize = 24.sp, color = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFFB0B3C8))
    }
}