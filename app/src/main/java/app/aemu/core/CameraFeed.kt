package app.aemu.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.net.LocalSocket
import android.os.Handler
import android.os.HandlerThread
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The phone's cameras for the guest. The emulator's camera HAL (native/camerahal) connects to /dev/socket/aemu_cam
 * and sends "open <0 back | 1 front>", "start", "stop", "shot", "close"; frames go back as
 *     int32 type, width, height, length + data — type 1: RGBA_8888 then NV21 (preview), type 2: JPEG (picture).
 * Frames are 640×480 straight from the sensor (the HAL reports the sensor orientation, the guest rotates).
 */
class CameraFeed(private val ctx: Context, private val paths: VmPaths, private val log: (String) -> Unit) {
    private val server = UnixServer(paths.socket("aemu_cam"), "camera") { c -> Session(c).run() }
    @Volatile var onPermissionNeeded: (() -> Unit)? = null

    fun start() = server.start(log)
    fun stop() = server.stop()

    private inner class Session(private val sock: LocalSocket) {
        private val out: OutputStream = sock.outputStream
        private val thread = HandlerThread("guest-camera").apply { start() }
        private val handler = Handler(thread.looper)
        @Volatile private var device: CameraDevice? = null
        @Volatile private var reader: ImageReader? = null
        @Volatile private var streaming = false
        @Volatile private var shot = false
        private val nv21 = ByteArray(W * H * 3 / 2)
        private val rgba = ByteArray(W * H * 4)

        fun run() {
            try {
                val r = sock.inputStream.bufferedReader()
                while (true) {
                    val line = r.readLine() ?: break
                    val p = line.trim().split(' ')
                    when (p[0]) {
                        "open" -> open(p.getOrNull(1) == "1")
                        "start" -> streaming = true
                        "stop" -> streaming = false
                        "shot" -> shot = true
                        "close" -> break
                    }
                }
            } finally {
                streaming = false
                runCatching { device?.close() }; runCatching { reader?.close() }
                thread.quitSafely()
                runCatching { sock.close() }
            }
        }

        @SuppressLint("MissingPermission")
        private fun open(front: Boolean) {
            if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                log("camera: the guest wants the camera, asking for permission")
                onPermissionNeeded?.invoke()
                // the guest's open already succeeded: wait for the answer, the preview starts when it is granted
                repeat(120) {
                    if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return@repeat
                    Thread.sleep(500)
                }
                if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
            }
            val cm = ctx.getSystemService(CameraManager::class.java)
            val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want }
                ?: cm.cameraIdList.firstOrNull() ?: run { log("camera: the phone has no camera"); return }
            val ir = ImageReader.newInstance(W, H, ImageFormat.YUV_420_888, 2)
            reader = ir
            ir.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { frame(it) } }, handler)
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    // the guest may have closed the camera already (mediaserver only probes it at start)
                    runCatching {
                        val req = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(ir.surface) }
                        @Suppress("DEPRECATION")
                        d.createCaptureSession(listOf(ir.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) { runCatching { s.setRepeatingRequest(req.build(), null, handler) } }
                            override fun onConfigureFailed(s: CameraCaptureSession) { log("camera: session not configured") }
                        }, handler)
                    }.onFailure { runCatching { d.close() }; return }
                    log("camera: ${if (front) "front" else "back"} camera opened for the guest")
                }
                override fun onDisconnected(d: CameraDevice) { d.close() }
                override fun onError(d: CameraDevice, e: Int) { log("camera: error $e"); d.close() }
            }, handler)
        }

        private fun frame(img: Image) {
            if (!streaming && !shot) return
            toNv21(img)
            runCatching {
                if (shot) {
                    shot = false
                    val jpg = ByteArrayOutputStream(200_000)
                    YuvImage(nv21, ImageFormat.NV21, W, H, null).compressToJpeg(Rect(0, 0, W, H), 92, jpg)
                    send(2, jpg.toByteArray(), jpg.size())
                    streaming = false   // a picture stops the preview until the guest starts it again
                    return
                }
                toRgba()
                send(1, rgba, rgba.size, nv21)
            }.onFailure { streaming = false }
        }

        private fun send(type: Int, a: ByteArray, n: Int, b: ByteArray? = null) {
            val h = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            h.putInt(type).putInt(W).putInt(H).putInt(n + (b?.size ?: 0))
            synchronized(out) { out.write(h.array()); out.write(a, 0, n); if (b != null) out.write(b); out.flush() }
        }

        /** YUV_420_888 planes (any row/pixel stride) → NV21 */
        private fun toNv21(img: Image) {
            val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
            val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
            for (row in 0 until H) {
                yb.position(row * y.rowStride)
                yb.get(nv21, row * W, minOf(W, yb.remaining()))
            }
            var o = W * H
            for (row in 0 until H / 2) {
                val up = row * u.rowStride; val vp = row * v.rowStride
                for (col in 0 until W / 2) {
                    nv21[o++] = vb.get(vp + col * v.pixelStride)
                    nv21[o++] = ub.get(up + col * u.pixelStride)
                }
            }
        }

        private fun toRgba() {
            var o = 0
            for (row in 0 until H) {
                val uv = W * H + (row shr 1) * W
                val yr = row * W
                for (col in 0 until W) {
                    val yy = (nv21[yr + col].toInt() and 0xff) - 16
                    val p = uv + (col and 1.inv())
                    val v = (nv21[p].toInt() and 0xff) - 128
                    val u = (nv21[p + 1].toInt() and 0xff) - 128
                    val c = 1192 * (if (yy < 0) 0 else yy)
                    var r = (c + 1634 * v) shr 10
                    var g = (c - 833 * v - 400 * u) shr 10
                    var b = (c + 2066 * u) shr 10
                    if (r < 0) r = 0 else if (r > 255) r = 255
                    if (g < 0) g = 0 else if (g > 255) g = 255
                    if (b < 0) b = 0 else if (b > 255) b = 255
                    rgba[o++] = r.toByte(); rgba[o++] = g.toByte(); rgba[o++] = b.toByte(); rgba[o++] = -1
                }
            }
        }
    }

    companion object {
        const val W = 640
        const val H = 480
    }
}
