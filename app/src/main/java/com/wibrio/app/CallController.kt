package com.wibrio.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import com.wibrio.mesh.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class CallController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mesh: DirectMeshRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val machine = CallMachine { android.os.SystemClock.elapsedRealtime() }
    val state = MutableStateFlow<CallView?>(null)
    val remoteVideo = MutableStateFlow<Bitmap?>(null)
    val localVideo = MutableStateFlow<Bitmap?>(null)
    val muted = MutableStateFlow(false)
    val cameraEnabled = MutableStateFlow(true)
    val speaker = MutableStateFlow(false)
    val framesReceived = MutableStateFlow(0L)
    private var visible = false
    private var audio: CallAudio? = null
    private var camera: CallCamera? = null
    private var mediaJob: Job? = null
    private var mediaQueue: Channel<CallPacket>? = null
    private var audioSequence = 0L
    private var videoSequence = 0L
    private var decodeJob: Job? = null
    init {
        scope.launch {
            mesh.callPackets.collect { (node, packet) ->
                if (!visible && packet.kind == CallKind.INVITE) {
                    runCatching { mesh.sendCall(node, packet.copy(kind = CallKind.BUSY)) }
                } else perform(machine.receive(node, packet))
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                val call = machine.view
                if (call != null && call.phase != CallPhase.ENDED && call.node !in mesh.callPeers.value)
                    perform(machine.end("Direct peer disconnected", false))
                else perform(machine.tick())
            }
        }
    }
    fun foreground(value: Boolean) {
        visible = value
        if (!value) end("Call ended when Wibrio left the foreground")
    }
    fun start(node: String, video: Boolean) = scope.launch {
        try {
            require(visible && node in mesh.callPeers.value) { "Reconnect and verify a direct peer using Wibrio 0.5.0 or newer" }
            requirePermissions(video)
            perform(machine.start(node, video))
        } catch (failure: Exception) { mesh.error.value = failure.message }
    }
    fun answer() = scope.launch {
        try { requirePermissions(machine.view?.video == true); perform(machine.answer()) }
        catch (failure: Exception) { mesh.error.value = failure.message; perform(machine.end("Microphone/camera access unavailable")) }
    }
    private fun requirePermissions(video: Boolean) {
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Allow microphone access to call" }
        if (video) require(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { "Allow camera access for video calling" }
    }
    fun end(reason: String = "Call ended") = scope.launch { perform(machine.end(reason)) }
    private fun endFor(id: String, reason: String) = scope.launch {
        if (machine.view?.id == id) perform(machine.end(reason))
    }
    fun dismiss() { if (machine.view?.phase == CallPhase.ENDED) { machine.dismiss(); state.value = null } }
    fun toggleMute() { muted.value = !muted.value; audio?.muted = muted.value }
    fun toggleSpeaker() { speaker.value = !speaker.value; audio?.speaker(speaker.value) }
    fun toggleCamera() {
        cameraEnabled.value = !cameraEnabled.value
        camera?.enabled = cameraEnabled.value
        if (!cameraEnabled.value) localVideo.value = null
    }
    private suspend fun perform(actions: List<CallAction>) {
        val expected = machine.view?.id
        state.value = machine.view
        for (action in actions) when (action) {
            is CallAction.Send -> try { mesh.sendCall(action.node, action.packet) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (machine.view?.id == expected) perform(machine.end("Call signalling failed", false)); return }
            CallAction.StartMedia -> startMedia()
            CallAction.StopMedia -> stopMedia()
            is CallAction.Media -> {
                if (action.packet.kind == CallKind.AUDIO) audio?.receive(action.packet.data)
                else if (decodeJob?.isActive != true) {
                    val expected = machine.view?.id
                    decodeJob = scope.launch {
                        val bitmap = withContext(Dispatchers.Default) {
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(action.packet.data, 0, action.packet.data.size, options)
                            if (options.outWidth !in 1..160 || options.outHeight !in 1..160) null
                            else BitmapFactory.decodeByteArray(action.packet.data, 0, action.packet.data.size)
                        }
                        if (bitmap != null && machine.view?.id == expected && machine.view?.phase == CallPhase.ACTIVE) {
                            remoteVideo.value = bitmap; framesReceived.value++
                        }
                    }
                }
            }
        }
        state.value = machine.view
    }
    private fun startMedia() {
        if (audio != null) return
        val call = machine.view ?: return
        if (call.phase != CallPhase.ACTIVE || !visible) return
        muted.value = false; cameraEnabled.value = true; speaker.value = call.video
        framesReceived.value = 0; audioSequence = 0; videoSequence = 0
        val queue = Channel<CallPacket>(3, BufferOverflow.DROP_OLDEST)
        mediaQueue = queue
        mediaJob = scope.launch {
            for (packet in queue) {
                if (machine.view?.id != call.id || machine.view?.phase != CallPhase.ACTIVE) break
                try { mesh.sendCall(call.node, packet) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (machine.view?.id == call.id) perform(machine.end("Media transport stalled", false)); break }
            }
        }
        try {
            audio = CallAudio(context, { bytes -> scope.launch {
                if (machine.view?.id == call.id && machine.view?.phase == CallPhase.ACTIVE)
                    queue.trySend(CallPacket(call.id, call.video, CallKind.AUDIO, ++audioSequence, bytes))
            } }, { message -> endFor(call.id, message) })
            audio!!.start(call.video)
            if (call.video) {
                camera = CallCamera(context, { bitmap, bytes -> scope.launch {
                if (machine.view?.id == call.id && machine.view?.phase == CallPhase.ACTIVE && cameraEnabled.value) {
                    localVideo.value = bitmap
                    queue.trySend(CallPacket(call.id, true, CallKind.VIDEO, ++videoSequence, bytes))
                }
                } }, { message -> endFor(call.id, message) })
                camera!!.start()
            }
        } catch (failure: Exception) { endFor(call.id, "Could not start media: ${failure.message?.take(80)}") }
    }
    private fun stopMedia() {
        mediaQueue?.close(); mediaQueue = null
        mediaJob?.cancel(); mediaJob = null
        runCatching { audio?.close() }; audio = null
        runCatching { camera?.close() }; camera = null
        decodeJob?.cancel(); decodeJob = null
        remoteVideo.value = null; localVideo.value = null
    }
}
