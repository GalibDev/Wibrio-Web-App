package com.wibrio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wibrio.mesh.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun DirectPeopleScreen(found: List<DiscoveredPeer>, status: MeshStatus, searching: Boolean, connect: (String) -> Unit, open: (String) -> Unit, controls: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val connected = (status as? MeshStatus.Active)?.peers.orEmpty()
    Page {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search nearby people") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        SectionLabel("Reachable · ${connected.size}", if (searching) "Discovering nearby devices…" else "Discovery stopped")
        connected.filter { it.displayName.contains(query, true) }.forEach { peer -> Action(peer.displayName, "${if (peer.hops == 1) "Direct" else "${peer.hops} hops"} · ${if (peer.verified) "Fingerprint verified" else "Verify fingerprint before chatting"}", Icons.AutoMirrored.Filled.Chat) { open(peer.id.value) } }
        SectionLabel("Nearby devices")
        found.filter { it.state != "Connected" && it.name.contains(query, true) }.forEach { peer ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(peer.name, fontWeight = FontWeight.SemiBold); Text(peer.state, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { connect(peer.endpoint) }, enabled = peer.state == "Nearby") { Text(when (peer.state) { "Connecting" -> "Connecting…"; "Verify code" -> "Verify code"; else -> "Connect" }) }
            } }
        }
        if (found.isEmpty() && connected.isEmpty()) Empty("No devices yet", "Start on both phones, keep Bluetooth and Wi-Fi on, then connect here.", Icons.Default.PeopleOutline)
        Primary("Mesh controls", controls)
    }
}

@Composable internal fun DirectInbox(messages: List<DirectMessage>, status: MeshStatus, open: (String) -> Unit, people: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val peers = (status as? MeshStatus.Active)?.peers.orEmpty()
    val nodes = (messages.map { it.peerId } + peers.map { it.id.value }).distinct()
    Page {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search conversations") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        SectionLabel("Your conversations")
        nodes.forEach { node ->
            val peer = peers.firstOrNull { it.id.value == node }
            val name = peer?.displayName ?: "Peer ${node.take(8)}"
            val last = messages.lastOrNull { it.peerId == node }
            if (name.contains(query, true) || last?.text?.contains(query, true) == true) Action(name, last?.text?.take(80) ?: "Direct · Ready to chat", Icons.AutoMirrored.Filled.Chat) { open(node) }
        }
        if (nodes.isEmpty()) Empty("Say hello, offline", "Connect and verify a device from People to begin.", Icons.AutoMirrored.Filled.Chat)
        Primary("Find people", people)
        Text("History and pending messages are stored encrypted on this device. Pending messages expire after 60 seconds; restart the mesh and verify links to resume.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun DirectConversation(node: String?, messages: List<DirectMessage>, status: MeshStatus, send: (String) -> Unit, disconnect: (String) -> Unit, approve: (String) -> Unit) {
    val peer = (status as? MeshStatus.Active)?.peers?.firstOrNull { it.id.value == node }
    val history = messages.filter { it.peerId == node }
    var draft by rememberSaveable(node) { mutableStateOf("") }
    var verifying by rememberSaveable(node) { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(history.size) { if (history.isNotEmpty()) scroll.animateScrollToItem(history.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(peer?.displayName ?: "Disconnected", fontWeight = FontWeight.Bold)
                Text(if (peer != null) "${if (peer.hops == 1) "Direct" else "${peer.hops} hops"} · ${if (peer.verified) "Fingerprint verified" else "Unverified remote identity"}" else "Temporarily unreachable · waiting for route recovery", style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(history, key = { "${it.id}:${it.outgoing}" }) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start) {
                    Surface(color = if (message.outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 300.dp)) {
                        Text(message.text, Modifier.padding(14.dp), color = if (message.outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    Text("${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))} · ${if (message.outgoing) message.state.name.lowercase() else "received"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (peer != null && !peer.verified) Button(onClick = { verifying = true }) { Text("Verify remote fingerprint") }
        if (peer?.hops == 1) TextButton(onClick = { disconnect(peer.id.value) }) { Text("Disconnect direct peer") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { if (it.toByteArray().size <= 3072) draft = it }, enabled = peer?.verified == true, modifier = Modifier.weight(1f), placeholder = { Text("Write a message") }, maxLines = 4, shape = RoundedCornerShape(22.dp))
            IconButton(onClick = { send(draft); draft = "" }, enabled = peer?.verified == true && draft.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send message") }
        }
        Spacer(Modifier.height(4.dp))
    }
    if (verifying && peer != null) AlertDialog(onDismissRequest = { verifying = false }, title = { Text("Verify ${peer.displayName}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Compare this fingerprint with the other person's Profile screen through a trusted channel. Both people must verify before relayed chat. A relay's display name does not establish trust.")
        Text(peer.id.value.chunked(4).joinToString(" "), style = MaterialTheme.typography.bodyMedium)
    } }, confirmButton = { TextButton(onClick = { approve(peer.id.value); verifying = false }) { Text("Fingerprint matches") } }, dismissButton = { TextButton(onClick = { verifying = false }) { Text("Cancel") } })
}

@Composable internal fun VerificationDialog(request: VerificationRequest, confirm: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = { confirm(false) }, title = { Text("Verify ${request.name}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Compare this code on both phones. Accept only if the codes match and you recognize the other device.")
        Text(request.digits, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("A display name alone does not verify who owns a phone.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { confirm(true) }) { Text("Codes match · Accept") } }, dismissButton = { TextButton(onClick = { confirm(false) }) { Text("Reject") } })
}
