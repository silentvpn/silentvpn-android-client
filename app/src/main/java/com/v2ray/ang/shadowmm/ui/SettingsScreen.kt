package com.v2ray.ang.shadowmm.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.shadowmm.model.AppSettings
import com.v2ray.ang.shadowmm.model.Language
import com.v2ray.ang.shadowmm.model.Strings
import com.v2ray.ang.shadowmm.model.Theme

@Composable
fun SettingsScreen(
    strings: Strings,
    settings: AppSettings,
    onToggleTheme: () -> Unit,
    onChangeLanguage: (Language) -> Unit,
    onOpenSplitTunnel: () -> Unit
) {
    val context = LocalContext.current
    val bgColor = if (settings.theme == Theme.DARK) Color(0xFF111625) else Color(0xFFF4F5FB)
    val scrollState = rememberScrollState()

    // 🔥 Get Version Info
    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val version = pInfo.versionName
    val appName = context.applicationInfo.loadLabel(context.packageManager).toString()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = strings.settings,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            color = if (settings.theme == Theme.DARK) Color.White else Color(0xFF23244F)
        )

        Spacer(Modifier.height(20.dp))

        // SECTION 1: GENERAL
        SettingsGroup(dark = settings.theme == Theme.DARK) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dark mode", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (settings.theme == Theme.DARK) Color.White else Color(0xFF22243E))
                }
                Switch(
                    checked = settings.theme == Theme.DARK,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF5D4BFF))
                )
            }
            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(strings.language, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (settings.theme == Theme.DARK) Color.White else Color(0xFF22243E))
                Spacer(Modifier.height(8.dp))
                Row {
                    LanguageChip(strings.english, settings.language == Language.EN, settings.theme == Theme.DARK) { onChangeLanguage(Language.EN) }
                    Spacer(Modifier.width(8.dp))
                    LanguageChip(strings.myanmar, settings.language == Language.MM, settings.theme == Theme.DARK) { onChangeLanguage(Language.MM) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // SECTION 2: VPN SETTINGS
        SettingsGroup(dark = settings.theme == Theme.DARK) {
            SettingsRow(title = strings.splitTunneling, subtitle = strings.splitDesc, dark = settings.theme == Theme.DARK, onClick = onOpenSplitTunnel)
            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))
            SettingsRow(title = strings.notifications, subtitle = strings.notiDesc, dark = settings.theme == Theme.DARK, onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            })
            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            // 🔥 NEW: DEVICE ID ROW (Copy & Hide)
            DeviceIdRow(dark = settings.theme == Theme.DARK)
        }

        Spacer(Modifier.height(16.dp))

        // SECTION 3: SUPPORT & LEGAL (UPDATED FOR PLAY STORE)
        SettingsGroup(dark = settings.theme == Theme.DARK) {

            // ✅ Privacy Policy
            SettingsRow(
                title = "Privacy Policy",
                subtitle = "Read privacy policy",
                dark = settings.theme == Theme.DARK,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://silentvpn.github.io/Silent_Privacy")
                    )
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            // ✅ Terms & Conditions
            SettingsRow(
                title = "Terms & Conditions",
                subtitle = "Read terms of use",
                dark = settings.theme == Theme.DARK,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://silentvpn.github.io/Silent_Privacy/Terms_&_Conditions")
                    )
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            // ✅ Data Deletion Request
            /*SettingsRow(
                title = "Data Deletion Request",
                subtitle = "Request data removal",
                dark = settings.theme == Theme.DARK,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://silentvpn.github.io/Silent_Privacy/Data_Deletion_Request")
                    )
                    context.startActivity(intent)
                }
            )*/

            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            // ✅ Support via Telegram
            SettingsRow(
                title = "Support (Telegram)",
                subtitle = "Contact us on Telegram",
                dark = settings.theme == Theme.DARK,
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/silentvpnmm")
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            )

            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            // ✅ Support via Email (REQUIRED)
            SettingsRow(
                title = "Support (Email)",
                subtitle = "silentvpn.vpn@gmail.com",
                dark = settings.theme == Theme.DARK,
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("silentvpn.vpn@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "SilentVPN Support")
                    }
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            // ✅ About Silent VPN – Original V2RayNG
            SettingsRow(
                title = "About Silent VPN",
                subtitle = "Based on V2RayNG (GPLv3 License)",
                dark = settings.theme == Theme.DARK,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/2dust/v2rayNG")
                    )
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(color = if (settings.theme == Theme.DARK) Color(0xFF252C42) else Color(0xFFE3E5F3))

            // ✅ Silent VPN Modified Source Code (GPLv3 requirement)
            SettingsRow(
                title = "Open Source (Silent VPN)",
                subtitle = "View modified source code",
                dark = settings.theme == Theme.DARK,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/yourname/silent-vpn")
                    )
                    context.startActivity(intent)
                }
            )
        }

        Spacer(Modifier.height(30.dp))

        // 🔥 APP VERSION INFO (At the very bottom)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (settings.theme == Theme.DARK) Color(0xFF5D6679) else Color(0xFF9EA4C2)
            )
            Text(
                text = "Version $version",
                fontSize = 11.sp,
                color = if (settings.theme == Theme.DARK) Color(0xFF454B5E) else Color(0xFFB0B6D1)
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ... (SettingsGroup, SettingsRow, LanguageChip code stays same) ...
@Composable
private fun SettingsGroup(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (dark) Color(0xFF1B2235) else Color.White, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}
@Composable
private fun SettingsRow(title: String, subtitle: String, dark: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (dark) Color.White else Color(0xFF22243E))
            Text(text = subtitle, fontSize = 12.sp, color = if (dark) Color(0xFF9BA3C2) else Color(0xFF7A80A0))
        }
    }
}
@Composable
private fun LanguageChip(label: String, selected: Boolean, darkTheme: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFF5D4BFF) else if (darkTheme) Color(0xFF242A40) else Color(0xFFE0E3F5)
    val color = if (selected) Color.White else if (darkTheme) Color(0xFFB3B9D5) else Color(0xFF4B4F70)
    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

// 🔥 DEVICE ID ROW COMPONENT
@Composable
private fun DeviceIdRow(dark: Boolean) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Get Device ID
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown"
    }

    var isVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Device ID",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (dark) Color.White else Color(0xFF22243E)
            )
            Text(
                // Show ID or Stars based on isVisible
                text = if (isVisible) deviceId else "••••••••••••••••",
                fontSize = 12.sp,
                color = if (dark) Color(0xFF9BA3C2) else Color(0xFF7A80A0)
            )
        }

        // Show/Hide Button
        Text(
            text = if (isVisible) "Hide" else "Show",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (dark) Color(0xFFBFC7FF) else Color(0xFF3F51B5),
            modifier = Modifier
                .clickable { isVisible = !isVisible }
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Copy Button
        Text(
            text = "Copy",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (dark) Color(0xFFBFC7FF) else Color(0xFF3F51B5),
            modifier = Modifier
                .clickable {
                    clipboardManager.setText(AnnotatedString(deviceId))
                    Toast.makeText(context, "ID Copied!", Toast.LENGTH_SHORT).show()
                }
                .padding(8.dp)
        )
    }
}