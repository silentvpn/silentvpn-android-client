package com.v2ray.ang.shadowmm.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.v2ray.ang.shadowmm.model.*
import com.v2ray.ang.shadowmm.data.ServerStorage

@Composable
fun ShadowLinkApp(
    servers: List<Server>,
    currentServer: Server,
    isConnected: Boolean,
    isConnecting: Boolean,
    statusText: String,
    testStatus: String,
    testResult: String,
    userData: UserData,
    settings: AppSettings,
    onSelectServer: (Server) -> Unit,
    onToggleTheme: () -> Unit,
    onChangeLanguage: (Language) -> Unit,
    onAddServer: (Server) -> Unit,
    onRenameServer: (Server, String) -> Unit,
    onDeleteServer: (Server) -> Unit,
    onStartStopClick: () -> Unit,
    onTestConnectionClick: () -> Unit,
    showWarningBox: Boolean,
    onGetMoreDataClick: () -> Unit
) {
    val strings = translations[settings.language]!!
    val context = LocalContext.current

    var serversState by remember {
        mutableStateOf(ServerStorage.loadServers(context))
    }

    LaunchedEffect(Unit) {
        serversState = ServerStorage.loadServers(context)
    }
    var currentServerState by remember { mutableStateOf(currentServer) }
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val intent = activity?.intent
    val shouldGoToRewards = intent?.getBooleanExtra("GO_TO_REWARDS", false) == true

    // If notification clicked, start at "rewards", else "home"
    var currentScreen by remember { mutableStateOf(if (shouldGoToRewards) "rewards" else "home") }

    // Reset intent so rotation/recreation doesn't keep going to rewards
    LaunchedEffect(Unit) {
        if (shouldGoToRewards) {
            intent?.removeExtra("GO_TO_REWARDS")
        }
    }

    Scaffold(
        bottomBar = {
            // Only show bottom bar on main tabs
            if (currentScreen in listOf("home", "rewards", "settings")) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen == "home",
                        onClick = { currentScreen = "home" },
                        label = { Text(strings.home) },
                        icon = { }
                    )

                    // ✅ Rewards tab - Show but handle access in RewardsScreen
                    NavigationBarItem(
                        selected = currentScreen == "rewards",
                        onClick = { currentScreen = "rewards" },
                        label = { Text(strings.rewards) },
                        icon = { }
                    )

                    NavigationBarItem(
                        selected = currentScreen == "settings",
                        onClick = { currentScreen = "settings" },
                        label = { Text(strings.settings) },
                        icon = { }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (currentScreen) {
                "home" -> HomeScreen(
                    strings = strings,
                    currentServer = currentServerState,
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    statusText = statusText,
                    testStatus = testStatus,
                    testResult = testResult,
                    userData = userData,
                    onChangeServerClick = { currentScreen = "serverList" },
                    onStartStopClick = onStartStopClick,
                    onTestConnectionClick = onTestConnectionClick,
                    darkTheme = settings.theme == Theme.DARK,
                    showWarningBox = showWarningBox,
                    onGetMoreDataClick = {
                        onGetMoreDataClick() // MainActivity က Logic (VPN ချိတ်တာ) အရင်လုပ်မယ်
                        currentScreen = "rewards" // ပြီးရင် Rewards Screen ကို ပြောင်းမယ်
                    }

                )

                // ✅ CLEAN: Pass isConnected and testStatus to check VPN state
                "rewards" -> RewardsScreen(
                    strings = strings,
                    userData = userData,
                    darkTheme = settings.theme == Theme.DARK,
                    isConnected = isConnected,  // ✅ Pass VPN connection state
                    testStatus = testStatus,    // ✅ Pass test status to check if VPN working
                    onBack = { currentScreen = "home" }
                )

                "settings" -> SettingsScreen(
                    strings = strings,
                    settings = settings,
                    onToggleTheme = onToggleTheme,
                    onChangeLanguage = onChangeLanguage,
                    onOpenSplitTunnel = { currentScreen = "splitTunnel" }
                )

                "serverList" -> ServerListScreen(
                    strings = strings,
                    servers = serversState,
                    currentServer = currentServerState,
                    darkTheme = settings.theme == Theme.DARK,
                    onSelect = { server ->
                        currentServerState = server
                        onSelectServer(server)
                        currentScreen = "home"
                    },
                    onAddServer = { server ->
                        serversState = serversState + server
                        ServerStorage.saveServers(context, serversState)
                        onAddServer(server)
                    },
                    onRenameServer = { server, newName ->
                        serversState = serversState.map {
                            if (it.id == server.id) it.copy(name = newName) else it
                        }
                        ServerStorage.saveServers(context, serversState)
                        onRenameServer(server, newName)
                    },
                    onDeleteServer = { server ->
                        serversState = serversState.filterNot { it.id == server.id }
                        if (currentServerState.id == server.id && serversState.isNotEmpty()) {
                            currentServerState = serversState.first()
                            onSelectServer(currentServerState)
                        }
                        ServerStorage.saveServers(context, serversState)
                        onDeleteServer(server)
                    },
                    onBack = { currentScreen = "home" }
                )

                "splitTunnel" -> AppSplitScreen(
                    strings = strings,
                    darkTheme = settings.theme == Theme.DARK,
                    onBack = { currentScreen = "settings" },

                )
            }
        }
    }
}