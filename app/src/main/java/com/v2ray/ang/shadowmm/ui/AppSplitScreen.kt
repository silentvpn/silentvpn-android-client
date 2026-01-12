package com.v2ray.ang.shadowmm.ui

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.shadowmm.model.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.Bitmap
)

@Composable
fun AppSplitScreen(
    darkTheme: Boolean,
    strings: Strings,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    // Keys
    val keyEnabled = "pref_per_app_proxy"
    val keySet = "pref_per_app_proxy_set"
    val keyBypass = "pref_bypass_apps"

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(MmkvManager.decodeSettingsStringSet(keySet) ?: mutableSetOf()) }
    var isBypassEnabled by remember { mutableStateOf(MmkvManager.decodeSettingsBool(keyEnabled, false)) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // Always force bypass mode internal flag
    LaunchedEffect(Unit) {
        MmkvManager.encodeSettings(keyBypass, true)
    }

    // Load Apps
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val appList = installed.mapNotNull { pkg ->
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                if (pm.getLaunchIntentForPackage(pkg.packageName) != null) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo).toBitmap()
                    AppInfo(label, pkg.packageName, icon)
                } else null
            }.sortedBy { it.name }
            apps = appList
            isLoading = false
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // ✅ COLORS: Matched with SettingsScreen
    val bgColor = if (darkTheme) Color(0xFF111625) else Color(0xFFF4F5FB)
    val cardColor = if (darkTheme) Color(0xFF1B2235) else Color.White
    val textColor = if (darkTheme) Color.White else Color(0xFF22243E)
    val subTextColor = if (darkTheme) Color(0xFF9BA3C2) else Color(0xFF7A80A0)
    val dividerColor = if (darkTheme) Color(0xFF252C42) else Color(0xFFE3E5F3)

    Column(modifier = Modifier.fillMaxSize().background(bgColor)) {

        // ✅ COMBINED HEADER & SWITCH SECTION
        Surface(
            color = cardColor,
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {

                // 1. Header (Back + Title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp, color = textColor)
                    }
                    Text(
                        text = strings.splitTunneling,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // 2. Bypass Switch Row (Now inside the same box)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.bypassModeTitle, // ✅ "Bypass Mode"
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.bypassModeDesc, // ✅ "Selected apps will NOT use VPN"
                            fontSize = 13.sp,
                            color = subTextColor
                        )
                    }
                    Switch(
                        checked = isBypassEnabled,
                        onCheckedChange = { enabled ->
                            isBypassEnabled = enabled
                            MmkvManager.encodeSettings(keyEnabled, enabled)
                            MmkvManager.encodeSettings(keyBypass, true)
                            Toast.makeText(context, strings.restartAppToApply, Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF5D4BFF)
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Content Area
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            // 📌 Reminder Text
            Text(
                text = strings.restartAppToApply,
                color = if (darkTheme) Color(0xFFFFAB40) else Color(0xFFE65100),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            // 🔎 Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF5D4BFF),
                    unfocusedBorderColor = dividerColor,
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor,
                    cursorColor = textColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                ),
                placeholder = {
                    Text(
                        text = strings.searchApps, // ✅ "Search apps by name"
                        fontSize = 14.sp,
                        color = subTextColor
                    )
                }
            )

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF5D4BFF))
                }
            } else {
                // Apps List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredApps) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        val rowAlpha = if (isBypassEnabled) 1f else 0.5f

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardColor.copy(alpha = rowAlpha),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = isBypassEnabled) {
                                    val newSet = selectedPackages.toMutableSet()
                                    if (isSelected) newSet.remove(app.packageName) else newSet.add(app.packageName)
                                    selectedPackages = newSet
                                    MmkvManager.encodeSettings(keySet, newSet)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp),
                                    alpha = rowAlpha
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor.copy(alpha = rowAlpha)
                                    )
                                    Text(
                                        app.packageName,
                                        fontSize = 11.sp,
                                        color = subTextColor.copy(alpha = rowAlpha)
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        val newSet = selectedPackages.toMutableSet()
                                        if (!checked) newSet.remove(app.packageName) else newSet.add(app.packageName)
                                        selectedPackages = newSet
                                        MmkvManager.encodeSettings(keySet, newSet)
                                    },
                                    enabled = isBypassEnabled,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF5D4BFF),
                                        uncheckedColor = subTextColor
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}