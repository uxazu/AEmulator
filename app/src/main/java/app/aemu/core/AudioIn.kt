package app.aemu.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream

/**
 * The phone's microphone for the guest. The emulator's audio HAL asks for it on /dev/aemu_mic_ctl
 * ("start <rate> <channels>" / "stop") and reads 16-bit PCM from /dev/aemu_mic; here AudioRecord captures at exactly
 * that rate and channel count. Without the RECORD_AUDIO permission [onPermissionNeeded] is called once per request
 * and the guest hears silence.
 */
class AudioIn(private val ctx: Context, private val root: File, private val log: (String) -> Unit) {
    private val ctl = File(root, "dev/aemu_mic_ctl")
    private val pcm = File(root, "dev/aemu_mic")
    @Volatile private var running = false
    @Volatile private var rec: Thread? = null
    @Volatile private var want: Pair<Int, Int>? = null
    @Volatile var onPermissionNeeded: (() -> Unit)? = null

    fun start() {
        running = true
        runCatching {
            for (f in listOf(ctl, pcm)) { f.delete(); f.parentFile?.mkdirs(); Os.mkfifo(f.absolutePath, "666".toInt(8)); Os.chmod(f.absolutePath, "666".toInt(8)) }
            Thread({ control() }, "mic-ctl").apply { isDaemon = true; start() }
        }.onFailure { log("mic: channel failed: ${it.message}") }
    }

    fun stop() {
        running = false
        want = null
        runCatching { File(ctl.absolutePath).appendText("stop\n") }
    }

    /** Called after the user granted the permission: a waiting request starts now. */
    fun permissionGranted() { want?.let { (r, c) -> record(r, c) } }

    private fun control() {
        val fd = Os.open(ctl.absolutePath, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
        FileInputStream(fd).bufferedReader().use { r ->
            while (running) {
                val p = (runCatching { r.readLine() }.getOrNull() ?: break).trim().split(' ')
                when (p.firstOrNull()) {
                    "start" -> {
                        val rate = p.getOrNull(1)?.toIntOrNull() ?: 16000
                        val ch = p.getOrNull(2)?.toIntOrNull() ?: 1
                        want = rate to ch
                        record(rate, ch)
                    }
                    "stop" -> { want = null; rec?.interrupt(); rec = null }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun record(rate: Int, channels: Int) {
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            log("mic: the guest wants the microphone, asking for permission")
            onPermissionNeeded?.invoke()
            return
        }
        rec?.interrupt()
        rec = Thread({
            val mask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            val min = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
            val ar = runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, rate, mask, AudioFormat.ENCODING_PCM_16BIT, min * 2) }.getOrNull()
            if (ar == null || ar.state != AudioRecord.STATE_INITIALIZED) { log("mic: AudioRecord $rate Hz × $channels failed"); ar?.release(); return@Thread }
            var out: FileDescriptor? = null
            try {
                // O_NONBLOCK: when the guest stops reading the samples are dropped, never queued up as delay
                out = Os.open(pcm.absolutePath, OsConstants.O_RDWR or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC, 0)
                ar.startRecording()
                log("mic: recording $rate Hz × $channels for the guest")
                val buf = ByteArray(rate / 50 * channels * 2)
                while (running && !Thread.currentThread().isInterrupted) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n > 0) runCatching { Os.write(out, buf, 0, n) }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { ar.stop() }; ar.release()
                runCatching { out?.let { Os.close(it) } }
            }
        }, "mic").apply { isDaemon = true; start() }
    }
}
