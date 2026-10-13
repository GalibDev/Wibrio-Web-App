package com.wibrio.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/** 8 kHz mono PCM, 100 ms per packet. Bounded playback avoids accumulating old speech. */
internal class CallAudio(context: Context, private val outgoing: (ByteArray) -> Unit, private val failed: (String) -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val oldMode = manager.mode
    @Suppress("DEPRECATION") private val oldSpeaker = manager.isSpeakerphoneOn
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playback = Channel<ByteArray>(3, BufferOverflow.DROP_OLDEST)
    @Volatile var muted = false
    @Volatile private var closed = false
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change -> if (change < 0 && !closed) failed("Call interrupted by another audio session") }.build()
    @SuppressLint("MissingPermission")
    fun start(video: Boolean) {
        val minIn = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val minOut = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minIn > 0 && minOut > 0) { "8 kHz audio is not supported" }
        check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio is in use by another app" }
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        speaker(video)
        val input = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 6400))
        recorder = input
        check(input.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        val output = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minOut, 6400)).setTransferMode(AudioTrack.MODE_STREAM).build()
        track = output
        check(output.state == AudioTrack.STATE_INITIALIZED) { "Audio output unavailable" }
        if (AcousticEchoCanceler.isAvailable()) echo = AcousticEchoCanceler.create(input.audioSessionId)?.apply { enabled = true }
        if (NoiseSuppressor.isAvailable()) noise = NoiseSuppressor.create(input.audioSessionId)?.apply { enabled = true }
        input.startRecording(); check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }; output.play()
        scope.launch {
            try {
                val buffer = ByteArray(1600)
                while (isActive) {
                    val count = input.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    check(count > 0) { "Microphone read failed ($count)" }
                    if (!muted) outgoing(buffer.copyOf(count - count % 2))
                }
            } catch (failure: Exception) { if (!closed) failed("Microphone stopped: ${failure.message?.take(60)}") }
        }
        scope.launch {
            try { for (bytes in playback) check(output.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING) >= 0) { "Audio playback failed" } }
            catch (failure: Exception) { if (!closed) failed("Audio playback stopped") }
        }
    }
    @Suppress("DEPRECATION") fun speaker(enabled: Boolean) { manager.isSpeakerphoneOn = enabled }
    fun receive(bytes: ByteArray) { playback.trySend(bytes) }
    fun close() {
        if (closed) return
        closed = true; playback.close(); scope.cancel()
        runCatching { recorder?.stop() }; runCatching { track?.stop() }
        runCatching { echo?.release() }; runCatching { noise?.release() }
        runCatching { recorder?.release() }; runCatching { track?.release() }
        recorder = null; track = null
        runCatching { manager.abandonAudioFocusRequest(focus) }
        runCatching { manager.mode = oldMode }
        @Suppress("DEPRECATION") runCatching { manager.isSpeakerphoneOn = oldSpeaker }
    }
}
