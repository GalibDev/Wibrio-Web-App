package com.wibrio.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wibrio.mesh.MeshStatus
import com.wibrio.mesh.TransportPreference
import com.wibrio.mesh.Peer

// Tokens inferred from the locally supplied .fig thumbnail, not connector exports.
internal val MeshBlue = Color(0xFF3464FF)
internal val MeshNavy = Color(0xFF10274E)
private val Mint = Color(0xFFE7F7F1)

@Composable internal fun SectionLabel(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable internal fun MeshArtwork() {
    val dark = MaterialTheme.colorScheme.background == Color(0xFF071226)
    Box(Modifier.fillMaxWidth().height(164.dp).background(if (dark) Color(0xFF102F3A) else Mint, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        // Decorative onboarding mark, never a representation of live network links.
        Canvas(Modifier.size(148.dp, 100.dp)) {
            val points = listOf(Offset(size.width * .5f, size.height * .18f), Offset(size.width * .2f, size.height * .57f), Offset(size.width * .72f, size.height * .64f), Offset(size.width * .48f, size.height * .9f))
            listOf(MeshBlue, Color(0xFF0BA9BC), Color(0xFF00AC94), Color(0xFF0CA57E)).forEachIndexed { index, color -> drawCircle(color, 14.dp.toPx(), points[index]) }
        }
    }
}

@Composable internal fun StartMeshScreen(preference: TransportPreference, status: MeshStatus, select: (TransportPreference) -> Unit, start: () -> Unit, stop: () -> Unit, searching: Boolean = false, relay: Boolean = true, setRelay: (Boolean) -> Unit = {}) {
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Welcome to", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Wibrio", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Connect with people. Stay close, offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MeshArtwork()
        SectionLabel("Connection preference", "Choose how you'd like to connect")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TransportPreference.entries.forEach { item ->
                val chosen = item == preference
                val label = when (item) { TransportPreference.AUTOMATIC -> "Automatic"; TransportPreference.WIFI_PRIORITY -> "Wi-Fi priority"; TransportPreference.BLUETOOTH_PRIORITY -> "Bluetooth priority" }
                val icon = when (item) { TransportPreference.AUTOMATIC -> Icons.Default.AutoAwesome; TransportPreference.WIFI_PRIORITY -> Icons.Default.Wifi; TransportPreference.BLUETOOTH_PRIORITY -> Icons.Default.Bluetooth }
                Surface(onClick = { select(item) }, modifier = Modifier.semantics { selected = chosen; role = Role.RadioButton }, shape = RoundedCornerShape(14.dp), color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(if (item == TransportPreference.AUTOMATIC) "Use available wireless connections" else "Preferred when supported by transport", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (chosen) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, if (chosen) "Selected" else "Not selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Text("Radio selection depends on device and transport support.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionSummary()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Relay nearby traffic", fontWeight = FontWeight.SemiBold); Text("Forward encrypted messages for other nodes", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = relay, onCheckedChange = setRelay)
        }
        if (status is MeshStatus.Unavailable) Info(status.reason)
        Primary(if (searching) "Discovery is running" else if (status is MeshStatus.Active) "Resume discovery" else "Start Mesh Network", start)
        TextButton(onClick = stop, modifier = Modifier.fillMaxWidth()) { Text("Stop / reset network") }
        Text(when (status) {
            MeshStatus.Discovering -> "Discovering nearby devices…"
            is MeshStatus.Active -> "${status.peers.size} reachable peers · ${if (searching) "Discovering" else "Discovery stopped"}"
            else -> "Discovery inactive · No connected devices"
        }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun DashboardScreen(name: String, status: MeshStatus, messages: List<DirectMessage> = emptyList(), openConversation: (String) -> Unit = {}, navigate: (String) -> Unit) {
    val peers = (status as? MeshStatus.Active)?.peers.orEmpty()
    Page {
        Surface(color = MeshNavy, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when (status) { is MeshStatus.Active -> "Mesh is active"; MeshStatus.Discovering -> "Discovering peers"; else -> "Mesh is inactive" }, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Hello, ${name.ifBlank { "neighbor" }}", color = Color(0xFFAFC3E5), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.Wifi, null, tint = Color(0xFF61E0D1))
                }
                Surface(color = Color(0xFF1D5360), shape = CircleShape) { Text(if (peers.isEmpty()) "No connections" else "${peers.size} reachable", color = Color(0xFF9CF0DE), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { navigate("Start Mesh") }, contentPadding = PaddingValues(0.dp)) { Text("Manage network  →", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Reachable", peers.size.toString().padStart(2, '0'), Modifier.weight(1f))
            Metric("Direct", peers.count { it.hops == 1 }.toString().padStart(2, '0'), Modifier.weight(1f))
            Metric("Relayed", peers.count { (it.hops ?: 0) > 1 }.toString().padStart(2, '0'), Modifier.weight(1f))
        }
        SectionLabel("Transport status")
        RadioStatus()
        SectionLabel("Quick actions")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("People", Icons.Default.People, Modifier.weight(1f)) { navigate("Nearby") }
            QuickTile("Share files", Icons.Default.FolderOpen, Modifier.weight(1f)) { navigate("Files") }
            QuickTile("Map", Icons.Default.Hub, Modifier.weight(1f)) { navigate("Map") }
        }
        SectionLabel("Recent conversations")
        val recent = messages.groupBy { it.peerId }.values.map { it.last() }.sortedByDescending { it.timestamp }.take(3)
        if (recent.isEmpty()) Empty("Your inbox starts here", "Connect with a verified peer to start a conversation.", Icons.AutoMirrored.Filled.Chat)
        recent.forEach { message ->
            val peerName = peers.firstOrNull { it.id.value == message.peerId }?.displayName ?: "Peer ${message.peerId.take(8)}"
            Action(peerName, message.text.take(80), Icons.AutoMirrored.Filled.Chat) { openConversation(message.peerId) }
        }
        Action("Network activity", "View diagnostics", Icons.Default.Insights) { navigate("Diagnostics") }
    }
}

@Composable private fun QuickTile(label: String, icon: ImageVector, modifier: Modifier, click: () -> Unit) {
    Surface(onClick = click, modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable internal fun PeopleScreen(messages: Boolean, navigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val filters = if (messages) listOf("All", "Unread", "Groups") else listOf("All", "Direct", "Relayed")
    Page {
        OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(if (messages) "Search your messages" else "Search nearby people") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { filters.forEachIndexed { index, text -> FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(text) }) } }
        SectionLabel(if (messages) "Your conversations" else "People around you", if (messages) "No conversations yet" else "0 reachable devices")
        Empty(if (query.isNotBlank()) "No results" else if (messages) "Say hello, offline" else "Your people are nearby", if (query.isNotBlank()) "Nothing matches “$query”." else if (messages) "Your messages will appear here once messaging is available." else "Start discovery when wireless connectivity is available.", if (messages) Icons.AutoMirrored.Filled.Chat else Icons.Default.PeopleOutline)
        if (messages) {
            Action("Private conversation", "View the conversation interface", Icons.AutoMirrored.Filled.Chat) { navigate("Conversation") }
            Action("Groups", "Group messaging availability", Icons.Default.Groups) { navigate("Groups") }
        } else Primary("Open mesh controls") { navigate("Start Mesh") }
    }
}

@Composable internal fun ConversationScreen(navigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Route, null, modifier = Modifier.size(18.dp)); Text("Temporarily unreachable · No peer selected", style = MaterialTheme.typography.labelMedium) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)); Text("A new conversation", style = MaterialTheme.typography.titleMedium); Text("Messages will appear here when a verified peer is connected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { navigate("Voice call") }) { Icon(Icons.Default.Call, "Voice call availability") }
            IconButton(onClick = { navigate("Video call") }) { Icon(Icons.Default.Videocam, "Video call availability") }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navigate("Files") }) { Icon(Icons.Default.Add, "Attachments") }
                Text("Messaging unavailable", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = {}, enabled = false) { Icon(Icons.Default.Mic, "Voice messages unavailable") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable internal fun FilesScreen(navigate: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Send to a nearby peer", "Share the things that matter.")
                Button(onClick = { category = "All files" }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Choose files") }
            }
        }
        SectionLabel("File categories")
        listOf(listOf("Photos" to Icons.Default.Image, "Documents" to Icons.Default.Description), listOf("Audio" to Icons.Default.MusicNote, "Videos" to Icons.Default.Videocam)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, icon) -> Surface(onClick = { category = label }, modifier = Modifier.weight(1f), color = if (label == "Audio") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                } }
            }
        }
        SectionLabel("Recent transfers")
        Empty("No transfers yet", "Files you share will appear here.", Icons.Default.FolderOpen)
        TextButton(onClick = { navigate("Transfers") }) { Text("View all transfers →") }
    }
    category?.let { selection -> AlertDialog(onDismissRequest = { category = null }, title = { Text(selection) }, text = { Text("File sharing isn't available yet. No file has been selected or sent.") }, confirmButton = { TextButton(onClick = { category = null }) { Text("Got it") } }) }
}

@Composable internal fun NetworkMapScreen(name: String, peers: List<Peer> = emptyList(), routes: List<com.wibrio.mesh.MeshRoute> = emptyList()) {
    Page {
        SectionLabel("${peers.size} devices reachable", "Direct links and observed multi-hop routes")
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .07f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(64.dp).background(MeshBlue, CircleShape), contentAlignment = Alignment.Center) { Text("You", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Text(name.ifBlank { "This device" }, style = MaterialTheme.typography.titleSmall)
                Text("${peers.count { it.hops == 1 }} direct links", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        peers.forEach { peer ->
            val route = routes.firstOrNull { it.destination == peer.id.value }
            Feature(Icons.Default.Hub, peer.displayName, if (peer.hops == 1) "Confirmed direct link · 1 hop" else "${peer.hops} observed hops · via ${route?.nextHop?.take(12) ?: "unknown"}")
        }
        if (peers.isEmpty()) Info("No active links. Verified direct peers appear after connection.")
        Text("Remote route paths are relay reports, not independently measured physical links.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun CallScreen(video: Boolean, back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF091A30)).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(32.dp))
        Text(if (video) "VIDEO CALL" else "VOICE CALL", color = Color(0xFFA5BAD9), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(120.dp).background(Color(0xFF123F57), CircleShape), contentAlignment = Alignment.Center) { Box(Modifier.size(88.dp).background(Color(0xFFB2F1ED), CircleShape), contentAlignment = Alignment.Center) { Icon(if (video) Icons.Default.Videocam else Icons.Default.Person, null, tint = MeshNavy, modifier = Modifier.size(40.dp)) } }
        Text("No peer selected", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Calling is unavailable", color = Color(0xFFAFBDD1), style = MaterialTheme.typography.bodyMedium)
        Text("No call has started. Direct calling will be available in a later milestone.", color = Color(0xFFAFBDD1), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Icons.Default.MicOff to "Mute", Icons.AutoMirrored.Filled.VolumeUp to "Speaker", Icons.Default.Videocam to "Camera").forEach { (icon, label) -> Column(horizontalAlignment = Alignment.CenterHorizontally) { FilledTonalIconButton(onClick = {}, enabled = false) { Icon(icon, "$label unavailable") }; Text(label, color = Color(0xFFA5BAD9), style = MaterialTheme.typography.labelSmall) } }
        }
        Button(onClick = back, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5167)), shape = CircleShape, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Close, "Close call screen", tint = Color.White) }
    }
}
