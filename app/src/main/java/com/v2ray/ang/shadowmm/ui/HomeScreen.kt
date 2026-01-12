package com.v2ray.ang.shadowmm.ui

import android.content.Context
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.v2ray.ang.shadowmm.model.Server
import com.v2ray.ang.shadowmm.model.Strings
import com.v2ray.ang.shadowmm.model.UserData
import com.v2ray.ang.shadowmm.ui.theme.BackgroundBottom
import com.v2ray.ang.shadowmm.ui.theme.BackgroundTop
import com.v2ray.ang.shadowmm.ui.theme.ShadowAccent
import com.v2ray.ang.shadowmm.ui.theme.ShadowPrimary
import kotlinx.coroutines.delay


@Composable
fun HomeScreen(
    strings: Strings,
    currentServer: Server,
    isConnected: Boolean,
    isConnecting: Boolean,
    statusText: String,
    testStatus: String,
    testResult: String,
    userData: UserData,
    onChangeServerClick: () -> Unit,
    onStartStopClick: () -> Unit,
    onTestConnectionClick: () -> Unit,
    darkTheme: Boolean,
    showWarningBox: Boolean,
    onGetMoreDataClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE) }
    var isUsageReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isUsageReady = true
    }

    val backgroundModifier = if (darkTheme) {
        Modifier.background(Brush.verticalGradient(colors = listOf(BackgroundTop, BackgroundBottom)))
    } else {
        Modifier.background(Color(0xFFF2F4F9))
    }

    Column(
        modifier = Modifier.fillMaxSize().then(backgroundModifier).padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Content Wrapper
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Status Text
            Text(
                text = if (isConnecting) strings.connecting
                else if (isConnected) strings.connected
                else strings.notConnected,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (isConnected) Color(0xFF00C853)
                    else if (darkTheme) Color(0xFF98A0FF) else Color(0xFF6C63FF),
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(8.dp))

            // 2. Server Card
            ServerCard(strings, currentServer, onChangeServerClick, darkTheme)

            Spacer(Modifier.height(30.dp))

            // 3. Connect Button
            ConnectCircle(
                strings = strings,
                isConnected = isConnected,
                isConnecting = isConnecting,
                onClick = onStartStopClick,
                darkTheme = darkTheme
            )

            Spacer(Modifier.height(16.dp))

            // 4. 🔥 TESTING AREA (Between Button & Data Box)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp) // အမြင့် နည်းနည်း တိုးလိုက်တယ် နှိပ်ရလွယ်အောင်
                            .background(
                                color = if (darkTheme) Color.White.copy(alpha = 0.06f) else Color.White,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onTestConnectionClick() }, // ဘယ်အချိန်နှိပ်နှိပ် Test လုပ်မယ်
                        contentAlignment = Alignment.Center
                    ) {
                        if (testStatus == "IDLE" || testStatus == "TESTING") {
                            // စမ်းနေတုန်း (သို့) မစမ်းရသေးခင်
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (testStatus == "TESTING") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = if (darkTheme) Color.White else Color.Black
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (testStatus == "TESTING") "Testing connection..." else "Tap to Check Connection",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (darkTheme) Color(0xFFB9B9D9) else Color.Gray
                                    )
                                )
                            }
                        } else {
                            // Success (သို့) Fail ဖြစ်သွားတဲ့အခါ
                            val resultColor = when (testStatus) {
                                "SUCCESS" -> Color(0xFF00C853)
                                "FAIL" -> Color.Red
                                else -> if (darkTheme) Color.White else Color.Black
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = testResult, // "✅ Connection OK" or "❌ Connection Failed"
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = resultColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                // ပြန်စမ်းလို့ရကြောင်း သိသာအောင် Refresh icon လေး (Text နဲ့ပြလိုက်မယ်)
                                Text(
                                    text = "(Tap to retry)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (darkTheme) Color.Gray else Color.LightGray
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 5. Data Usage Card
            DataUsageCard(strings, userData, darkTheme, isUsageReady)

            // 🔥 WARNING BOX LOGIC UPDATE
            val isUnlimited = userData.baseDailyLimitMB == Int.MAX_VALUE
            val totalLimit = if (isUnlimited) {
                0
            } else {
                userData.baseDailyLimitMB + userData.bonusDataMB
            }
            val rawUsed = userData.dailyDataUsedMB
            val displayUsed = if (!isUnlimited && rawUsed > totalLimit) totalLimit else rawUsed

            // Percentage တွက်မယ်
            val percentage = if (totalLimit > 0) (displayUsed.toFloat() / totalLimit.toFloat()) * 100 else 0f

            // 🔥 FIX: 80% ကျော်တာနဲ့ Connected ဖြစ်ဖြစ်/မဖြစ်ဖြစ် အမြဲပြမယ်
            val shouldShowWarning = !isUnlimited && (percentage >= 80)

            if (shouldShowWarning) {
                Spacer(Modifier.height(16.dp))

                // ✅ Dynamic title based on percentage
                val titleText = when {
                    percentage >= 100 -> strings.limitReached
                    percentage >= 95 -> "⚠️ 95% Limit Near"
                    else -> strings.lowdatawarning  // 80-94%
                }

                val bodyText = when {
                    percentage >= 100 -> strings.fullpercent
                    percentage >= 95 -> "Usage at 95%. Auto-disconnect may occur."
                    else -> strings.runninglowdata
                }

                LimitWarningBox(
                    title = titleText,
                    message = bodyText,
                    strings = strings,
                    onGetMoreData = onGetMoreDataClick
                )
            }
        }

        // 6. Spacer to push Ads to bottom
        Spacer(Modifier.weight(1f))

        // 7. Ads Banner
        BannerAdView()

        Spacer(Modifier.height(8.dp))
    }
}



@Composable
fun BannerAdView() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

// ... ConnectCircle, ServerCard, DataUsageCard (Same as before) ...
@Composable
private fun ConnectCircle(
    strings: Strings,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    darkTheme: Boolean
) {
    val outerColor = when {
        isConnected -> Color(0xFF69F0AE)
        else -> if (darkTheme) ShadowPrimary else Color(0xFFE0E3FF)
    }
    val innerColor = when {
        isConnected -> Color(0xFFB9F6CA)
        else -> if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.White
    }
    Box(
        modifier = Modifier.size(200.dp).shadow(24.dp, CircleShape, clip = false).clip(CircleShape).background(outerColor).clickable(enabled = true, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(150.dp).clip(CircleShape).background(innerColor), contentAlignment = Alignment.Center) {
            if (isConnecting) {
                CircularProgressIndicator(color = if(darkTheme) Color.White else ShadowPrimary, modifier = Modifier.size(60.dp), strokeWidth = 4.dp)
            } else {
                Text(text = if (isConnected) strings.disconnect else strings.connect, style = MaterialTheme.typography.bodyLarge.copy(color = if (isConnected) Color(0xFF1B5E20) else if (darkTheme) Color.White else Color(0xFF1A1A1A), fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun ServerCard(strings: Strings, currentServer: Server, onChangeServerClick: () -> Unit, darkTheme: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) Color.White.copy(alpha = 0.06f) else Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = currentServer.flag, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = strings.server, style = MaterialTheme.typography.labelSmall.copy(color = if (darkTheme) Color(0xFFB9B9D9) else Color(0xFF8C8FA6)))
                Text(text = currentServer.name, style = MaterialTheme.typography.bodyLarge.copy(color = if (darkTheme) Color.White else Color(0xFF1A1A1A), fontWeight = FontWeight.SemiBold))
            }
            Text(text = strings.change, style = MaterialTheme.typography.labelMedium.copy(color = ShadowAccent, fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable { onChangeServerClick() })
        }
    }
}

@Composable
private fun DataUsageCard(strings: Strings, userData: UserData, darkTheme: Boolean,isUsageReady: Boolean) {
    val isUnlimited = userData.baseDailyLimitMB == Int.MAX_VALUE
    val totalLimit = if (isUnlimited) 0 else userData.baseDailyLimitMB

    // Real Data
    val rawUsed = userData.dailyDataUsedMB
    val finalDisplayUsed = if (!isUnlimited && rawUsed > totalLimit) totalLimit else rawUsed

    // 🔥 LOGIC FIX: App စဖွင့်တာ ဟုတ်/မဟုတ် စစ်မယ်
    val isFirstRun = !AppSession.isFirstAnimPlayed

    // App စဖွင့်တာဆိုရင် 0 ကစမယ်၊ ဖွင့်ပြီးသားဆိုရင် ရှိပြီးသား Usage အတိုင်း တန်းပြမယ် (Glitch/Animation မဖြစ်အောင်)
    var usageTarget by remember { mutableIntStateOf(if (isFirstRun) 0 else finalDisplayUsed) }

    LaunchedEffect(finalDisplayUsed) {
        usageTarget = finalDisplayUsed
    }

    // 🔥 DURATION FIX: 1.5s -> 1s (1000ms) ပြောင်းလိုက်ပါပြီ
    val animSpecInt: AnimationSpec<Int> = if (isFirstRun) {
        tween(durationMillis = 1500, easing = LinearOutSlowInEasing)
    } else {
        snap()
    }

    val animatedUsed by animateIntAsState(
        targetValue = usageTarget,
        animationSpec = animSpecInt,
        label = "UsageCounter"
    )

    // Progress Bar အတွက်လည်း အတူတူပါပဲ
    val progress = if (!isUsageReady || isUnlimited || totalLimit == 0) {
        0f
    } else { animatedUsed.toFloat() / totalLimit.toFloat() }

    val animSpecFloat: AnimationSpec<Float> = if (isFirstRun) {
        tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    } else {
        snap()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = animSpecFloat,
        label = "progress"
    )

    // 🔥 State Update: Animation ပြီးသွားရင် (၁ စက္ကန့်ကျော်ရင်) Flag ကို True ပြောင်းမယ်
    LaunchedEffect(Unit) {
        if (isFirstRun) {
            delay(1050) // 1000ms + buffer
            AppSession.isFirstAnimPlayed = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) Color.White.copy(alpha = 0.06f) else Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = strings.dataUsed,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (darkTheme) Color(0xFFB9B9D9) else Color(0xFF8C8FA6)
                )
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = if (isUnlimited)
                    "Unlimited Data"
                else
                    "$animatedUsed MB / $totalLimit MB",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (darkTheme) Color.White else Color(0xFF1A1A1A),
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (darkTheme) ShadowAccent else ShadowPrimary,
                trackColor = if (darkTheme) Color.White.copy(alpha = 0.15f) else Color(0xFFE0E3FF)
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dataLeftText = when {
                    isUnlimited -> "Unlimited"
                    !isUsageReady -> "--"

                    // 🔥 Total Limit ထဲကနေ Animation ဂဏန်းကို နှုတ်မယ်
                    else -> maxOf(totalLimit - animatedUsed, 0).toString()
                }

                Text(
                    text = if (isUnlimited) "${strings.dataLeft}: ${strings.unlimited}"
                    else if (!isUsageReady) "${strings.dataLeft}: --"
                    else "${strings.dataLeft}: $dataLeftText MB",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (darkTheme) Color(0xFFB9B9D9) else Color(0xFF4C4F6A)
                    )
                )

                Text(
                    text = "Coins: ${userData.coins}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (darkTheme) Color(0xFFB9B9D9) else Color(0xFF4C4F6A)
                    )
                )
            }

        }
    }
}

@Composable
fun LimitWarningBox(
    title: String,
    message: String,
    strings: Strings,
    onGetMoreData: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        shape = RoundedCornerShape(12.dp),
        // 🔥 Margin ကို လျှော့လိုက်တယ် (Vertical 4.dp ပဲထားတော့မယ်)
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // 🔥 Padding ကို 16.dp ကနေ 12.dp သို့ လျှော့ချ
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp // 🔥 Font Size 16 -> 14 လျှော့
            )
            Spacer(modifier = Modifier.height(2.dp)) // Spacer လျှော့
            Text(
                text = message,
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 12.sp, // 🔥 Font Size 13 -> 12 လျှော့
                textAlign = TextAlign.Center,
                lineHeight = 16.sp // Line Height ထိန်း
            )
            Spacer(modifier = Modifier.height(8.dp)) // Spacer 12 -> 8 လျှော့
            Button(
                onClick = onGetMoreData,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                modifier = Modifier.fillMaxWidth().height(36.dp), // 🔥 Button Height 44 -> 36 လျှော့
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp) // Button Padding ဖြုတ်
            ) {
                Text(strings.getmoredata, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
object AppSession {
    var isFirstAnimPlayed = false
}
