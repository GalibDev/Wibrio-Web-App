package com.wibrio.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.wibrio.mesh.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private val model: WibrioViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WibrioApp(model) }
    }
}
private val Blue = MeshBlue
private val Navy = Color(0xFF081B46)
private val Cyan = Color(0xFF00D5E8)
private val destinations = listOf("Home", "Nearby", "Chats", "Files", "Map")
internal fun permissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT <= 32) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= 31) addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
}.toTypedArray()

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WibrioApp(model: WibrioViewModel) {
    val profile by model.profile.collectAsStateWithLifecycle()
    val status by model.meshStatus.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val scheme = if (profile.dark) darkColorScheme(primary = Color(0xFF7CB9FF), secondary = Cyan, background = Color(0xFF071226), surface = Color(0xFF101F36))
        else lightColorScheme(primary = Blue, secondary = Color(0xFF007D8C), background = Color.White, surface = Color.White, primaryContainer = Color(0xFFEDF2FF), onPrimaryContainer = Color(0xFF183565), surfaceVariant = Color(0xFFF0F4FA), onSurface = Color(0xFF10213B))
    MaterialTheme(colorScheme = scheme, shapes = Shapes(medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp))) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "Splash"
        Scaffold(
            topBar = { if (route !in listOf("Splash", "Welcome", "Voice call", "Video call")) TopAppBar(title = { Text(if (route == "Home") "Wibrio" else when (route) { "Nearby" -> "People"; "Chats" -> "Messages"; "Files" -> "Share files"; "Map" -> "Network map"; "Conversation" -> "Conversation"; else -> route }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }, navigationIcon = { if (route !in destinations) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { if (route in destinations) IconButton(onClick = { nav.navigate("Settings") { launchSingleTop = true } }) { Icon(Icons.Default.Settings, "Settings") }; if (route in destinations) IconButton(onClick = { nav.navigate("Profile") { launchSingleTop = true } }) { Icon(Icons.Default.AccountCircle, "Your profile") } }) },
            bottomBar = { if (route in destinations) NavigationBar { destinations.forEachIndexed { index, destination -> NavigationBarItem(selected = route == destination, onClick = { nav.navigate(destination) { popUpTo("Home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(listOf(Icons.Default.Home, Icons.Default.People, Icons.AutoMirrored.Filled.Chat, Icons.Default.FolderOpen, Icons.Default.Hub)[index], destination) }, label = { Text(when (destination) { "Nearby" -> "People"; "Chats" -> "Messages"; else -> destination }) }) } } }
        ) { padding ->
            NavHost(nav, startDestination = "Splash", modifier = Modifier.padding(padding)) {
                composable("Splash") {
                    LaunchedEffect(profile.ready) { if (profile.ready) { delay(700); nav.navigate(if (profile.name.isBlank()) "Welcome" else "Home") { popUpTo("Splash") { inclusive = true } } } }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Logo(112); Text("Wibrio", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Closer, even offline.") } }
                }
                composable("Welcome") { Page { Logo(96); Hero("Connection without boundaries", "A nearby community. No account. No phone number. Built for the moments the internet can't reach."); Feature(Icons.Default.Wifi, "Offline by design", "Connect with nearby devices using local wireless links."); Feature(Icons.Default.Hub, "A network of people", "Mesh relaying is planned for an upcoming milestone."); Feature(Icons.Default.Lock, "Trust comes first", "Peer verification and encryption are required before messaging ships."); Primary("Set up this device") { nav.navigate("Setup") } } }
                composable("Setup") { ProfileEditor(profile.name, "Make it yours", "Choose the name nearby people will see.") { model.saveName(it) { nav.navigate("Permissions") { popUpTo("Welcome") { inclusive = true } } } } }
                composable("Permissions") { PermissionPage { nav.navigate("Home") { popUpTo("Permissions") { inclusive = true } } } }
                composable("Home") { DashboardScreen(profile.name, status) { nav.navigate(it) } }
                composable("Start Mesh") { val preference by model.preference.collectAsStateWithLifecycle(); StartMeshScreen(preference, status, { model.preference.value = it }, { model.start() }, { model.stop() }) }
                composable("Nearby") { PeopleScreen(false) { nav.navigate(it) } }
                composable("Chats") { PeopleScreen(true) { nav.navigate(it) } }
                composable("Conversation") { ConversationScreen { nav.navigate(it) } }
                composable("Map") { NetworkMapScreen(profile.name) }
                composable("Profile") { Page { Logo(80); Hero(profile.name, "This device belongs to you."); Action("Edit display name", "Saved locally on this device", Icons.Default.Edit) { nav.navigate("Edit profile") }; Info("Cryptographic identity, fingerprint and verification QR are pending Phase 2–4. No shareable identity has been generated.") } }
                composable("Edit profile") { ProfileEditor(profile.name, "Device profile", "Update your nearby display name.") { model.saveName(it) { nav.popBackStack() } } }
                composable("Settings") { Page { Hero("Your preferences", "A quieter, more personal network."); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Dark appearance", modifier = Modifier.weight(1f)); Switch(checked = profile.dark, onCheckedChange = model::dark) }; Action("Device profile", "Name and identity", Icons.Default.AccountCircle) { nav.navigate("Profile") }; Action("Permissions", "Manage wireless access", Icons.Default.Tune) { nav.navigate("Permissions") }; Action("Security & privacy", "Trust and local data", Icons.Default.Lock) { nav.navigate("Security") }; Action("Network diagnostics", "Transport and routing status", Icons.Default.Insights) { nav.navigate("Diagnostics") }; Text("Wibrio 0.1.0 • Foundation", style = MaterialTheme.typography.labelMedium) } }
                composable("Security") { Page { Hero("Trust, by design", "You decide who gets close."); Info("This foundation does not send messages or claim end-to-end encryption. Android Keystore identity, authenticated handshakes and reviewed encryption are upcoming milestones."); Feature(Icons.Default.Storage, "Local preferences", "Display name and theme stay on this device. Android backup is disabled."); Empty("No trusted devices", "Peers will require explicit verification before connection.", Icons.Default.VerifiedUser) } }
                composable("Diagnostics") { Page { Hero("Network diagnostics", "Live foundation state"); listOf("Mesh" to if (status is MeshStatus.Unavailable) "Unavailable" else "Inactive", "Transport" to "Not installed", "Node identity" to "Not generated", "Endpoints / verified peers" to "0 / 0", "Routes / hops" to "0 / —", "Packets sent / received" to "0 / 0", "ACKs / retries / failures" to "0 / 0 / 0").forEach { (key, value) -> Feature(Icons.Default.Info, key, value) }; PermissionSummary() } }
                composable("Files") { FilesScreen { nav.navigate(it) } }
                composable("Transfers") { Page { Hero("Transfers", "Keep an eye on every byte."); Empty("No active transfers", "Progress appears only after a real transfer starts.", Icons.Default.Folder) } }
                composable("Groups") { Page { Hero("Better together", "Private spaces for nearby communities."); Empty("Groups arrive in Phase 5", "Authenticated membership and group encryption must be implemented first.", Icons.Default.Groups) } }
                listOf("Voice call", "Video call").forEach { title -> composable(title) { CallScreen(title == "Video call") { nav.popBackStack() } } }
            }
            if (error != null) AlertDialog(onDismissRequest = { model.error.value = null }, title = { Text("Couldn't save") }, text = { Text(error.orEmpty()) }, confirmButton = { TextButton(onClick = { model.error.value = null }) { Text("OK") } })
        }
    }
}
