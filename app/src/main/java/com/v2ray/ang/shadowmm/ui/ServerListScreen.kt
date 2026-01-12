package com.v2ray.ang.shadowmm.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.shadowmm.model.Server
import com.v2ray.ang.shadowmm.model.ServerType
import com.v2ray.ang.shadowmm.model.Strings
import com.v2ray.ang.shadowmm.vpn.parseShadowsocksUri
import com.v2ray.ang.shadowmm.vpn.buildShadowsocksUri
import com.v2ray.ang.shadowmm.vpn.parseVlessUri
import com.v2ray.ang.shadowmm.vpn.buildVlessUri
import com.v2ray.ang.shadowmm.vpn.parseVmessUri

@Composable
fun ServerListScreen(
    servers: List<Server>,
    currentServer: Server,
    darkTheme: Boolean,
    onSelect: (Server) -> Unit,
    onAddServer: (Server) -> Unit,
    onRenameServer: (Server, String) -> Unit,
    onDeleteServer: (Server) -> Unit,
    onBack: () -> Unit,
    strings: Strings,
) {
    BackHandler { onBack() }
    var filter by remember { mutableStateOf(ServerFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val filteredServers = when (filter) {
        ServerFilter.ALL -> servers
        ServerFilter.OFFICIAL -> servers.filter { it.type == ServerType.OFFICIAL }
        ServerFilter.MANUAL -> servers.filter { it.type == ServerType.MANUAL }
    }

    val bgColor = if (darkTheme) Color(0xFF131A2A) else Color(0xFFF2F4F9)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    strings.Back,
                    color = if (darkTheme) Color(0xFFBFC7FF) else Color(0xFF3F51B5)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings.ServerList,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (darkTheme) Color.White else Color(0xFF1A1A1A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAddDialog = true }) {
                Text(
                    strings.AddServer,
                    color = if (darkTheme) Color(0xFFBFC7FF) else Color(0xFF3F51B5)
                )
            }
        }

        FilterRow(
            current = filter,
            darkTheme = darkTheme,
            onChange = { filter = it },
            strings = strings
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredServers) { server ->
                ServerListItem(
                    server = server,
                    isCurrent = server.id == currentServer.id,
                    darkTheme = darkTheme,
                    onClick = { onSelect(server) },
                    onRename = onRenameServer,
                    onDelete = onDeleteServer,
                    strings = strings
                )
            }
        }
    }

    if (showAddDialog) {
        EnhancedAddServerDialog(
            darkTheme = darkTheme,
            onConfirm = { serverType, uri ->
                when (serverType) {
                    ServerProtocol.SHADOWSOCKS -> {
                        val cfg = parseShadowsocksUri(uri)
                        if (cfg == null) {
                            errorText = "Invalid ss:// link"
                        } else {
                            val name = cfg.name ?: "${cfg.host}:${cfg.port}"
                            val newServer = Server(
                                id = "manual-ss-${cfg.host}-${cfg.port}-${System.currentTimeMillis()}",
                                name = name,
                                host = cfg.host,
                                port = cfg.port,
                                type = ServerType.MANUAL,
                                flag = "🌐",
                                method = cfg.method,
                                password = cfg.password,
                                configType = EConfigType.SHADOWSOCKS,
                                ping = 0,
                                rawUri = uri
                            )
                            onAddServer(newServer)
                            errorText = null
                            showAddDialog = false
                        }
                    }
                    ServerProtocol.VLESS -> {
                        val cfg = parseVlessUri(uri)
                        if (cfg == null) {
                            errorText = "Invalid vless:// link"
                        } else {
                            // Validate Reality configuration
                            if (cfg.security == "reality") {
                                if (cfg.publicKey.isNullOrEmpty()) {
                                    errorText = "Reality requires Public Key (pbk parameter)"
                                    return@EnhancedAddServerDialog
                                }
                                if (cfg.sni.isNullOrEmpty()) {
                                    errorText = "Reality requires SNI parameter"
                                    return@EnhancedAddServerDialog
                                }
                            }

                            val name = cfg.name ?: "${cfg.host}:${cfg.port}"
                            val hasTls = cfg.security == "tls" || cfg.security == "reality"

                            val newServer = Server(
                                id = "manual-vless-${cfg.host}-${cfg.port}-${System.currentTimeMillis()}",
                                name = name,
                                host = cfg.host,
                                port = cfg.port,
                                type = ServerType.MANUAL,
                                flag = "🌐",
                                configType = EConfigType.VLESS,
                                password = cfg.uuid,
                                tls = hasTls,
                                network = cfg.network,
                                path = cfg.path,
                                sni = cfg.sni,
                                flow = cfg.flow,
                                publicKey = cfg.publicKey,
                                shortId = cfg.shortId,
                                spiderX = cfg.spiderX,
                                fingerprint = cfg.fingerprint,
                                alpn = cfg.alpn,
                                ping = 0,
                                rawUri = uri
                            )
                            onAddServer(newServer)
                            errorText = null
                            showAddDialog = false
                        }
                    }
                    ServerProtocol.VMESS -> {
                        val cfg = parseVmessUri(uri)
                        if (cfg == null) {
                            errorText = "Invalid vmess:// link"
                        } else {
                            val name = cfg.name ?: "${cfg.host}:${cfg.port}"
                            val hasTls = cfg.security == "tls"

                            val newServer = Server(
                                id = "manual-vmess-${cfg.host}-${cfg.port}-${System.currentTimeMillis()}",
                                name = name,
                                host = cfg.host,
                                port = cfg.port,
                                type = ServerType.MANUAL,
                                flag = "🌐",
                                configType = EConfigType.VMESS,
                                password = cfg.uuid,
                                tls = hasTls,
                                network = cfg.network,
                                path = cfg.path,
                                sni = cfg.sni,
                                ping = 0,
                                rawUri = uri // Original Key သိမ်းမယ်
                            )
                            onAddServer(newServer)
                            errorText = null
                            showAddDialog = false
                        }
                    }
                }
            },
            onDismiss = {
                showAddDialog = false
                errorText = null
            },
            errorText = errorText,
            strings = strings
        )
    }
}

private enum class ServerFilter { ALL, OFFICIAL, MANUAL }

@Composable
private fun FilterRow(
    current: ServerFilter,
    darkTheme: Boolean,
    onChange: (ServerFilter) -> Unit,
    strings: Strings,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChipTab(
            text = strings.All,
            selected = current == ServerFilter.ALL,
            darkTheme = darkTheme,
            onClick = { onChange(ServerFilter.ALL) }
        )
        Spacer(Modifier.width(8.dp))
        FilterChipTab(
            text = strings.Official,
            selected = current == ServerFilter.OFFICIAL,
            darkTheme = darkTheme,
            onClick = { onChange(ServerFilter.OFFICIAL) }
        )
        Spacer(Modifier.width(8.dp))
        FilterChipTab(
            text = strings.Manual,
            selected = current == ServerFilter.MANUAL,
            darkTheme = darkTheme,
            onClick = { onChange(ServerFilter.MANUAL) }
        )
    }
}

@Composable
private fun FilterChipTab(
    text: String,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val selectedColor = if (darkTheme) Color(0xFF3F4DFF) else Color(0xFF3F51B5)
    val unselectedColor = if (darkTheme) Color(0xFF1E2435) else Color(0xFFE0E3F5)
    val selectedText = Color.White
    val unselectedText = if (darkTheme) Color(0xFFB5BDD8) else Color(0xFF4B4F70)

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) selectedColor else unselectedColor,
        modifier = Modifier
            .height(32.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (selected) selectedText else unselectedText,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ServerListItem(
    server: Server,
    isCurrent: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
    onRename: (Server, String) -> Unit,
    onDelete: (Server) -> Unit,
    strings: Strings,
) {
    var showEditDialog by remember { mutableStateOf(false) }

    val bg = if (darkTheme) Color(0xFF1C2438) else Color.White
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (darkTheme) Color(0xFF101624) else Color(0xFFE0E3F5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = server.flag,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                // ✅ Show protocol ONLY for MANUAL servers
                if (server.type == ServerType.MANUAL) {
                    Text(
                        text = when (server.configType) {
                            EConfigType.SHADOWSOCKS -> "Shadowsocks"
                            EConfigType.VLESS -> "VLESS"
                            EConfigType.VMESS -> "VMess"
                            else -> "Manual"
                        },
                        color = if (darkTheme) Color(0xFF9AA3C0) else Color(0xFF757DA0),
                        fontSize = 12.sp
                    )
                }

                // Server name (always shown)
                Text(
                    text = server.name,
                    color = if (darkTheme) Color.White else Color(0xFF1A1A1A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TypeChip(server.type, darkTheme)

            if (isCurrent) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.current,
                    color = if (darkTheme) Color(0xFFBFC7FF) else Color(0xFF3F51B5),
                    fontSize = 11.sp
                )
            }

            if (server.type == ServerType.MANUAL) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.copy,
                    color = if (darkTheme) Color(0xFFBFC7FF) else Color(0xFF3F51B5),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {

                        val uriToCopy = server.rawUri

                        if (!uriToCopy.isNullOrBlank()) {
                            clipboard.setText(AnnotatedString(uriToCopy))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Original key not available", Toast.LENGTH_SHORT).show()
                        }
                    }
                )


                Spacer(Modifier.width(8.dp))

                // Edit action
                Text(
                    text = strings.edit,
                    color = if (darkTheme) Color(0xFFBFC7FF) else Color(0xFF3F51B5),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { showEditDialog = true }
                )
            }
        }
    }

    if (showEditDialog && server.type == ServerType.MANUAL) {
        EditManualServerDialog(
            server = server,
            onRename = onRename,
            onDelete = onDelete,
            onDismiss = { showEditDialog = false },
            strings = strings
        )
    }
}

@Composable
private fun TypeChip(type: ServerType, darkTheme: Boolean) {
    val (bg, text) = when (type) {
        ServerType.OFFICIAL -> Color(0xFF27AE60) to "Official"
        ServerType.MANUAL -> Color(0xFF7F8C8D) to "Manual"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EnhancedAddServerDialog(
    darkTheme: Boolean,
    onConfirm: (ServerProtocol, String) -> Unit,
    onDismiss: () -> Unit,
    errorText: String?,
    strings: Strings,
) {
    var selectedProtocol by remember { mutableStateOf(ServerProtocol.SHADOWSOCKS) }
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedProtocol, textState.text.trim()) }) {
                Text(strings.Add)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
        title = { Text(strings.addmanualserver) },
        text = {
            Column {
                // Protocol Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipTab(
                        text = "Shadowsocks",
                        selected = selectedProtocol == ServerProtocol.SHADOWSOCKS,
                        darkTheme = darkTheme,
                        onClick = { selectedProtocol = ServerProtocol.SHADOWSOCKS }
                    )
                    FilterChipTab(
                        text = "VLESS",
                        selected = selectedProtocol == ServerProtocol.VLESS,
                        darkTheme = darkTheme,
                        onClick = { selectedProtocol = ServerProtocol.VLESS }
                    )
                    FilterChipTab(
                        text = "VMess",
                        selected = selectedProtocol == ServerProtocol.VMESS,
                        darkTheme = darkTheme,
                        onClick = { selectedProtocol = ServerProtocol.VMESS }
                    )
                }

                // URI Input
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = {
                        Text(
                            when (selectedProtocol) {
                                ServerProtocol.SHADOWSOCKS -> "ss:// link (Outline supported)"
                                ServerProtocol.VLESS -> "vless:// link"
                                ServerProtocol.VMESS -> "vmess:// link"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (selectedProtocol) {
                                ServerProtocol.SHADOWSOCKS -> "ss://..."
                                ServerProtocol.VLESS -> "vless://uuid@host:port..."
                                ServerProtocol.VMESS -> "vmess://eyJ..."
                            }
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Help text
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (selectedProtocol) {
                        ServerProtocol.SHADOWSOCKS -> "Paste your ss:// link from Outline or Shadowsocks app"
                        ServerProtocol.VLESS -> "Paste your vless:// configuration link"
                        ServerProtocol.VMESS -> "Paste your vmess:// configuration link"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (darkTheme) Color(0xFFB5BDD8) else Color(0xFF757DA0)
                )
            }
        }
    )
}

@Composable
private fun EditManualServerDialog(
    server: Server,
    onRename: (Server, String) -> Unit,
    onDelete: (Server) -> Unit,
    onDismiss: () -> Unit,
    strings: Strings,
) {
    var nameState by remember { mutableStateOf(TextFieldValue(server.name)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onRename(server, nameState.text.trim().ifEmpty { server.name })
                onDismiss()
            }) {
                Text(strings.save)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDelete(server)
                    onDismiss()
                }) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.cancel)
                }
            }
        },
        title = { Text(strings.editserver) },
        text = {
            Column {
                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}