package app.aemu.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream

/**
 * Звук гостя. Гостевой HAL (audio.primary.default.so из набора движка) пишет PCM
 * 48 кГц/стерео/16 бит в FIFO /dev/eac, а мы отдаём его в AudioTrack телефона.
 */
class AudioOut(private val paths: VmPaths, private val log: (String) -> Unit, private val rate: Int = RATE) {
    @Volatile private var thread: Thread? = null
    @Volatile private var stop = false
    @Volatile var played = 0L
        private set
    @Volatile var muted = false

    val fifo: File get() = File(paths.root, "dev/eac")

    fun makeFifo() {
        val f = fifo
        val mode = runCatching { Os.stat(f.absolutePath).st_mode and OsConstants.S_IFMT }.getOrDefault(0)
        if (mode == OsConstants.S_IFIFO) return
        f.parentFile?.mkdirs()
        f.delete()
        runCatching { Os.mkfifo(f.absolutePath, "666".toInt(8)) }
            .onFailure { log("звук: канал не создался: ${it.message}") }
    }

    @Synchronized
    fun start() {
        if (thread?.isAlive == true) return
        if (!fifo.exists()) { log("звук: канала нет"); return }
        stop = false
        thread = Thread({ pump() }, "aemu-audio").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        stop = true
        thread?.interrupt()
        // разбудить read() на FIFO: откроем его на запись и сразу закроем
        runCatching {
            Thread { runCatching { java.io.FileOutputStream(fifo).close() } }.apply { isDaemon = true; start() }
        }
        thread = null
    }

    private fun pump() {
        val min = runCatching { AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) }.getOrDefault(0)
        val bufSize = maxOf(min * 4, 64 * 1024)
        val buf = ByteArray(4096)
        var track: AudioTrack? = null
        while (!stop) {
            val ins = runCatching { FileInputStream(fifo) }.getOrNull()
            if (ins == null) { Thread.sleep(500); continue }
            if (track == null) {
                track = runCatching {
                    AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(bufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build().also { it.play() }
                }.onFailure { log("звук: вывод не открылся: ${it.message}") }.getOrNull()
            }
            ins.use { s ->
                while (!stop) {
                    val n = try { s.read(buf) } catch (e: Exception) { -1 }
                    if (n < 0) break
                    if (n == 0) continue
                    if (played == 0L) log("звук пошёл")
                    val t = track
                    if (t != null && !muted) {
                        var off = 0
                        val until = SystemClock.uptimeMillis() + 1000
                        while (off < n && !stop) {
                            val w = t.write(buf, off, n - off, AudioTrack.WRITE_NON_BLOCKING)
                            if (w > 0) off += w
                            else if (w < 0) { runCatching { t.release() }; track = null; break }
                            else if (SystemClock.uptimeMillis() > until) break
                            else Thread.sleep(4)
                        }
                    }
                    played += n
                }
            }
        }
        runCatching { track?.stop(); track?.release() }
    }

    companion object { const val RATE = 48000 }
}
