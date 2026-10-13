package com.wibrio.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import com.wibrio.mesh.CallWire
import androidx.core.graphics.scale

/** Small JPEG frames at up to 2 fps over the verified Nearby link; never a fake preview. */
internal class CallCamera(private val context: Context, private val outgoing: (Bitmap, ByteArray) -> Unit, private val failed: (String) -> Unit) {
    private val thread = HandlerThread("WibrioCallCamera").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var closed = false
    @Volatile var enabled = true
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start() {
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
            ?: manager.cameraIdList.firstOrNull() ?: error("No camera available")
        val info = manager.getCameraCharacteristics(cameraId)
        val front = info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val sensor = info.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val autofocus = info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val display = when (context.getSystemService(WindowManager::class.java).defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
        val rotation = if (front) (sensor + display) % 360 else (sensor - display + 360) % 360
        val sizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        val size = sizes.minByOrNull { it.width.toLong() * it.height } ?: error("JPEG camera capture unavailable")
        val images = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        reader = images
        images.setOnImageAvailableListener({ source ->
            try {
                source.acquireLatestImage()?.use { image ->
                    if (closed || !enabled) return@use
                    val buffer = image.planes[0].buffer
                    val jpeg = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, minOf(size.width, size.height) / 160) }
                    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: return@use
                    val scale = minOf(160f / bitmap.width, 160f / bitmap.height)
                    val small = bitmap.scale(maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()), true)
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()); if (front) postScale(-1f, 1f) }
                    val frame = Bitmap.createBitmap(small, 0, 0, small.width, small.height, matrix, true)
                    val output = ByteArrayOutputStream()
                    for (quality in listOf(35, 20, 10)) {
                        output.reset(); frame.compress(Bitmap.CompressFormat.JPEG, quality, output)
                        if (output.size() <= CallWire.MAX_DATA) break
                    }
                    if (!closed && enabled && output.size() <= CallWire.MAX_DATA) outgoing(frame, output.toByteArray())
                }
            } catch (failure: Exception) { if (!closed) failed("Camera frame failed") }
        }, handler)
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (closed) { camera.close(); return }
                device = camera
                camera.createCaptureSession(listOf(images.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        if (closed) { configured.close(); return }
                        session = configured
                        var capturing = false
                        val completed = object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) { capturing = false }
                            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                                capturing = false
                                if (!closed) failed("Camera capture failed")
                            }
                        }
                        val capture = object : Runnable {
                            override fun run() {
                                if (closed) return
                                try {
                                    if (enabled && !capturing) {
                                        capturing = true
                                        configured.capture(camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(images.surface); set(CaptureRequest.JPEG_QUALITY, 50.toByte())
                                        if (CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE in autofocus)
                                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        }.build(), completed, handler)
                                    }
                                    handler.postDelayed(this, 500)
                                } catch (failure: Exception) { if (!closed) failed("Camera capture stopped") }
                            }
                        }
                        handler.post(capture)
                    }
                    override fun onConfigureFailed(configured: CameraCaptureSession) { configured.close(); if (!closed) failed("Camera session unavailable") }
                }, handler)
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close(); if (!closed) failed("Camera disconnected") }
            override fun onError(camera: CameraDevice, error: Int) { camera.close(); if (!closed) failed("Camera unavailable ($error)") }
        }, handler)
    }
    fun close() {
        closed = true; handler.removeCallbacksAndMessages(null)
        runCatching { session?.close() }; runCatching { device?.close() }; runCatching { reader?.close() }
        thread.quitSafely()
    }
}
