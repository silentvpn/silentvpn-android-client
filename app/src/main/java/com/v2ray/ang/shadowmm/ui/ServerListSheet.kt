package com.v2ray.ang.shadowmm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.shadowmm.model.Server
import com.v2ray.ang.shadowmm.model.ServerType
import com.v2ray.ang.shadowmm.model.Strings
import com.v2ray.ang.shadowmm.vpn.parseShadowsocksUri
import com.v2ray.ang.shadowmm.vpn.parseVlessUri
import com.v2ray.ang.shadowmm.vpn.parseVmessUri

@Composable
fun ServerListSheet(
    servers: List<Server>,
    currentServer: Server,
    onSelect: (Server) -> Unit,
    onAddServer: (Server) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    strings: Strings,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    BackHandler { onBack() }

    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose server",
                    style = MaterialTheme.typography.titleMedium
                )

                TextButton(onClick = { showAddDialog = true }) {
                    Text("Add Server")
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(servers) { server ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(server) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = server.flag,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = server.name,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when (server.configType) {
                                        EConfigType.SHADOWSOCKS -> "Shadowsocks • ${server.host}:${server.port}"
                                        EConfigType.VLESS -> "VLESS • ${server.host}:${server.port}"
                                        EConfigType.VMESS -> "VMess • ${server.host}:${server.port}"
                                        else -> "${server.host}:${server.port}"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (server == currentServer) {
                                Text(
                                    text = strings.current,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close")
            }
        }
    }

    if (showAddDialog) {
        SimpleAddServerDialog(
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
                                ping = 0
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
                            val name = cfg.name ?: "${cfg.host}:${cfg.port}"

                            // Determine actual security status
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
                                ping = 0
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

@Composable
private fun SimpleAddServerDialog(
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
                // Protocol tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProtocolTab(
                        text = "Shadowsocks",
                        selected = selectedProtocol == ServerProtocol.SHADOWSOCKS,
                        onClick = { selectedProtocol = ServerProtocol.SHADOWSOCKS },
                        strings = strings
                    )
                    ProtocolTab(
                        text = "VLESS",
                        selected = selectedProtocol == ServerProtocol.VLESS,
                        onClick = { selectedProtocol = ServerProtocol.VLESS },
                        strings = strings
                    )
                    ProtocolTab(
                        text = "VMESS",
                        selected = selectedProtocol == ServerProtocol.VMESS,
                        onClick = { selectedProtocol = ServerProtocol.VMESS },
                        strings = strings
                    )
                }

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = {
                        Text(
                            when (selectedProtocol) {
                                ServerProtocol.SHADOWSOCKS -> "ss:// link"
                                ServerProtocol.VLESS -> "vless:// link"
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
            }
        }
    )
}

@Composable
private fun ProtocolTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    strings: Strings,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}