package com.wibrio.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wibrio.mesh.*

@Composable internal fun rememberCallLauncher(calls: CallController): (String, Boolean) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.let { (node, video) ->
            if (callPermissions(video).all { permission -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED }) calls.start(node, video)
            else android.widget.Toast.makeText(context, "Allow microphone${if (video) " and camera" else ""} permission to call", android.widget.Toast.LENGTH_LONG).show()
        }
        pending = null
    }
    return { node, video ->
        if (callPermissions(video).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) calls.start(node, video)
        else { pending = node to video; permissions.launch(callPermissions(video)) }
    }
}
private fun callPermissions(video: Boolean) = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)

@Composable internal fun LiveCallHost(calls: CallController, peers: List<Peer>) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        calls.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) calls.foreground(true)
            if (event == Lifecycle.Event.ON_STOP) calls.foreground(false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); calls.foreground(false) }
    }
    val call by calls.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view, call?.phase) {
        val previous = view.keepScreenOn
        if (call?.phase == CallPhase.ACTIVE) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    val remote by calls.remoteVideo.collectAsStateWithLifecycle()
    val local by calls.localVideo.collectAsStateWithLifecycle()
    val muted by calls.muted.collectAsStateWithLifecycle()
    val camera by calls.cameraEnabled.collectAsStateWithLifecycle()
    val speaker by calls.speaker.collectAsStateWithLifecycle()
    val frames by calls.framesReceived.collectAsStateWithLifecycle()
    var permissionCall by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (call?.id == permissionCall && call?.phase == CallPhase.INCOMING) {
            if (callPermissions(call?.video == true).all { p -> ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }) calls.answer()
            else calls.end("Microphone/camera permission denied")
        }
        permissionCall = null
    }
    // Only an incoming invitation rings; capture starts after explicit Accept and peer READY.
    DisposableEffect(call?.id, call?.phase) {
        val ringtone = if (call?.phase == CallPhase.INCOMING) runCatching {
            android.media.RingtoneManager.getRingtone(context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))?.apply { play() }
        }.getOrNull() else null
        onDispose { ringtone?.stop() }
    }
    call?.let { current ->
        Dialog(onDismissRequest = { if (current.phase == CallPhase.ENDED) calls.dismiss() else calls.end() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            Surface(Modifier.fillMaxSize(), color = MeshNavy, contentColor = Color.White) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (current.video) "Offline video call" else "Offline voice call", style = MaterialTheme.typography.titleLarge)
                    Text(peers.firstOrNull { it.id.value == current.node }?.displayName ?: "Peer ${current.node.take(8)}", style = MaterialTheme.typography.headlineSmall)
                    Text(when (current.phase) { CallPhase.INCOMING -> "Incoming call"; CallPhase.OUTGOING -> "Calling…"; CallPhase.CONNECTING -> "Waiting for peer confirmation…"; CallPhase.ACTIVE -> "Connected · verified direct link"; CallPhase.ENDED -> current.reason })
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF081B34)), contentAlignment = Alignment.Center) {
                        if (current.video && remote != null) Image(remote!!.asImageBitmap(), "Live remote camera", Modifier.fillMaxSize())
                        else Icon(if (current.video) Icons.Default.Videocam else Icons.Default.Call, "Call", modifier = Modifier.size(72.dp))
                        if (current.video && local != null) Image(local!!.asImageBitmap(), "Your camera preview", Modifier.align(Alignment.BottomEnd).size(100.dp, 130.dp).padding(6.dp))
                    }
                    if (current.video) Text("Low bandwidth video · up to 2 fps · received $frames frames", style = MaterialTheme.typography.bodySmall)
                    if (current.phase == CallPhase.INCOMING) {
                        Button(onClick = {
                            permissionCall = current.id
                            if (callPermissions(current.video).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) calls.answer()
                            else permissions.launch(callPermissions(current.video))
                        }) { Icon(Icons.Default.Call, null); Text(" Accept") }
                    }
                    if (current.phase == CallPhase.ACTIVE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TextButton(onClick = calls::toggleMute) { Text(if (muted) "Unmute" else "Mute", color = Color.White) }
                            TextButton(onClick = calls::toggleSpeaker) { Text(if (speaker) "Earpiece" else "Speaker", color = Color.White) }
                            if (current.video) TextButton(onClick = calls::toggleCamera) { Text(if (camera) "Pause video" else "Resume video", color = Color.White) }
                        }
                    }
                    Button(onClick = { if (current.phase == CallPhase.ENDED) calls.dismiss() else calls.end(if (current.phase == CallPhase.INCOMING) "Call declined" else "Call ended") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD52C49))) {
                        Icon(Icons.Default.CallEnd, null); Text(if (current.phase == CallPhase.ENDED) " Close" else if (current.phase == CallPhase.INCOMING) " Decline" else " End call")
                    }
                    Text("Keep Wibrio open. Leaving the app or locking the screen ends this call.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
