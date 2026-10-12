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

@Composable internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}
@Composable internal fun Logo(size: Int) { Image(painterResource(R.drawable.wibrio_logo), contentDescription = "Wibrio logo", modifier = Modifier.size(size.dp)) }
@Composable internal fun Hero(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable internal fun Primary(label: String, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text(label, fontWeight = FontWeight.SemiBold) } }
@Composable internal fun Info(text: String) { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
@Composable internal fun Metric(label: String, value: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium) } } }
@Composable internal fun Feature(icon: ImageVector, title: String, detail: String) { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } } }
@Composable internal fun Action(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Feature(icon, title, detail) }; Icon(Icons.Default.ChevronRight, null) } } }
@Composable internal fun Empty(title: String, detail: String, icon: ImageVector) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable internal fun ProfileEditor(initial: String, title: String, subtitle: String, save: (String) -> Unit) { var name by rememberSaveableName(initial); Page { Hero(title, subtitle); OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Display name") }, singleLine = true, supportingText = { Text("${name.length}/40 · Stored on this device") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { save(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Save and continue") } } }
@Composable internal fun rememberSaveableName(initial: String) = androidx.compose.runtime.saveable.rememberSaveable(initial) { mutableStateOf(initial) }
@Composable internal fun RadioStatus() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val radio = remember(revision) {
        val wifi = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
        val bluetooth = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        val bluetoothState = when {
            bluetooth == null -> "Not supported"
            Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED -> "Permission needed"
            else -> runCatching { if (bluetooth.isEnabled) "On" else "Off" }.getOrDefault("Unavailable")
        }
        (if (wifi == null) "Not supported" else if (wifi.isWifiEnabled) "On" else "Off") to bluetoothState
    }
    Feature(Icons.Default.Wifi, "Device radios", "Wi-Fi: ${radio.first} • Bluetooth: ${radio.second}")
}
@Composable internal fun PermissionSummary() { val context = LocalContext.current; var refresh by remember { mutableIntStateOf(0) }; val owner = LocalLifecycleOwner.current; DisposableEffect(owner) { val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }; owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) } }; val granted = remember(refresh) { permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } }; Info(if (granted) "Wireless permissions granted. Transport is not installed yet." else "Wireless permissions needed before nearby discovery.") }
@Composable internal fun PermissionPage(next: () -> Unit) { val context = LocalContext.current; var result by remember { mutableStateOf<String?>(null) }; val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { values -> result = if (values.values.all { it }) "Permissions granted" else "Some permissions were denied. You can retry or enable them in app settings." }; Page { Hero("Ready for nearby", "You're in control of device access."); Feature(Icons.Default.Bluetooth, "Nearby devices", "Bluetooth discovery, advertising and connections."); Feature(Icons.Default.Wifi, "Local Wi-Fi", "Discover compatible wireless connections."); if (Build.VERSION.SDK_INT <= 32) Feature(Icons.Default.LocationOn, "Location access", "Required by Android for nearby discovery on this version."); key(result) { PermissionSummary() }; result?.let { Info(it) }; Primary("Grant wireless permissions") { launcher.launch(permissions()) }; OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Open app settings") }; TextButton(onClick = next, modifier = Modifier.fillMaxWidth()) { Text("Continue") } } }
