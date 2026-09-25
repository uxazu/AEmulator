package app.aemu.core

import android.os.Process
import android.os.SystemClock
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Сенсорный экран и кнопки гостя. qemu при открытии гостем /dev/input/event0 подключается к
 * input.sock, а мы пишем туда сырые struct input_event (32-битные: sec, usec, type, code, value).
 * Мультитач — протокол A (SYN_MT_REPORT) с TRACKING_ID: его понимают и 2.3, и 4.x.
 */
class InputService(private val paths: VmPaths, private val log: (String) -> Unit) {

    data class P(val id: Int, val x: Int, val y: Int)

    private class Client(val out: OutputStream, val pid: Int)

    private val clients = CopyOnWriteArrayList<Client>()
    @Volatile var rateHz = 60
    @Volatile var mtMode = 0
    @Volatile var sent = 0L
        private set
    val connected: Int get() = clients.size

    private var lastIds: List<Int> = emptyList()
    private var lastSentAt = 0L
    private var pending: List<P>? = null
    private var pendingAt = 0L
    private var pumpStarted = false
    private var wasDown = false

    private val server = UnixServer(paths.inputSock, "input") { c ->
        runCatching { c.sendBufferSize = 64 * 1024 }
        val pid = runCatching { c.peerCredentials.pid }.getOrDefault(-1)
        clients.removeAll { old ->
            (old.pid == pid && pid > 0).also { if (it) runCatching { old.out.close() } }
        }
        val me = Client(c.outputStream, pid)
        clients.add(me)
        log("ввод: гость подключился (процесс $pid, клиентов ${clients.size})")
        // держим соединение, пока гость его не закроет
        val ins = c.inputStream
        val buf = ByteArray(64)
        while (true) {
            val n = try { ins.read(buf) } catch (e: Exception) { -1 }
            if (n < 0) break
        }
        clients.remove(me)
    }

    fun serve() = server.start(log)

    fun stop() {
        server.stop()
        clients.forEach { runCatching { it.out.close() } }
        clients.clear()
    }

    @Synchronized
    fun touch(now: List<P>, force: Boolean = false) {
        val gap = if (rateHz > 0) 1000L / rateHz else 0L
        val ids = now.map { it.id }
        val t = SystemClock.uptimeMillis()
        if (!force && gap > 0 && now.isNotEmpty() && ids == lastIds && t - lastSentAt < gap) {
            pending = now
            pendingAt = t
            startPump()
            return
        }
        val held = pending
        pending = null
        // отпускание: сначала дошлём последнее положение пальцев, иначе жест «короче» настоящего
        if (held != null && now.isEmpty() && held.isNotEmpty()) send(held)
        lastIds = ids
        lastSentAt = t
        send(now)
    }

    private fun startPump() {
        if (pumpStarted) return
        pumpStarted = true
        Thread({
            runCatching { Process.setThreadPriority(-8) }
            while (true) {
                try { Thread.sleep(if (pending != null) 2 else 25) } catch (_: InterruptedException) { break }
                synchronized(this) {
                    val list = pending
                    if (list != null) {
                        val gap = if (rateHz > 0) 1000L / rateHz else 0L
                        if (SystemClock.uptimeMillis() - lastSentAt >= gap) {
                            pending = null
                            lastSentAt = SystemClock.uptimeMillis()
                            send(list)
                        }
                    }
                }
            }
        }, "input-pump").apply { isDaemon = true; start() }
    }

    private fun send(now: List<P>) {
        if (clients.isEmpty()) return
        val b = ByteBuffer.allocate((now.size * 7 + 3) * EV).order(ByteOrder.LITTLE_ENDIAN)
        if (mtMode == 4) { // одиночное касание
            now.firstOrNull()?.let {
                put(b, EV_ABS, ABS_X, it.x); put(b, EV_ABS, ABS_Y, it.y); put(b, EV_ABS, ABS_PRESSURE, 64)
            }
        } else {
            for (p in now) {
                if (mtMode == 0 || mtMode == 1) put(b, EV_ABS, ABS_MT_TRACKING_ID, p.id.coerceIn(0, 31))
                put(b, EV_ABS, ABS_MT_POSITION_X, p.x)
                put(b, EV_ABS, ABS_MT_POSITION_Y, p.y)
                if (mtMode != 3) put(b, EV_ABS, ABS_MT_TOUCH_MAJOR, 40)
                if (mtMode == 2) put(b, EV_ABS, ABS_MT_WIDTH_MAJOR, 8)
                if (mtMode == 0 || mtMode == 1) put(b, EV_ABS, ABS_MT_PRESSURE, 64)
                put(b, EV_SYN, SYN_MT_REPORT, 0)
            }
        }
        val down = now.isNotEmpty()
        if (down != wasDown) {
            put(b, EV_KEY, BTN_TOUCH, if (down) 1 else 0)
            wasDown = down
        }
        if (!down && mtMode != 4) put(b, EV_SYN, SYN_MT_REPORT, 0)
        put(b, EV_SYN, SYN_REPORT, 0)
        flush(b)
    }

    fun key(code: Int, down: Boolean) {
        val b = ByteBuffer.allocate(2 * EV).order(ByteOrder.LITTLE_ENDIAN)
        put(b, EV_KEY, code, if (down) 1 else 0)
        put(b, EV_SYN, SYN_REPORT, 0)
        synchronized(this) { flush(b) }
    }

    fun press(code: Int, holdMs: Long = 80) {
        Thread {
            key(code, true)
            Thread.sleep(holdMs)
            key(code, false)
        }.start()
    }

    fun tap(x: Int, y: Int) {
        touch(listOf(P(1, x, y)), true)
        Thread.sleep(60)
        touch(emptyList(), true)
    }

    fun swipe(x0: Int, y0: Int, x1: Int, y1: Int, ms: Int = 300) {
        val steps = (ms / 16).coerceAtLeast(2)
        for (i in 0..steps) {
            val x = x0 + (x1 - x0) * i / steps
            val y = y0 + (y1 - y0) * i / steps
            touch(listOf(P(1, x, y)), true)
            Thread.sleep(16)
        }
        touch(emptyList(), true)
    }

    private fun put(b: ByteBuffer, type: Int, code: Int, value: Int) {
        val t = System.nanoTime()
        b.putInt((t / 1_000_000_000).toInt())
        b.putInt(((t % 1_000_000_000) / 1000).toInt())
        b.putShort(type.toShort())
        b.putShort(code.toShort())
        b.putInt(value)
    }

    private fun flush(b: ByteBuffer) {
        if (b.position() == 0) return
        val a = ByteArray(b.position())
        b.flip(); b.get(a); b.clear()
        val dead = ArrayList<Client>()
        for (c in clients) {
            try { c.out.write(a); c.out.flush() } catch (e: Exception) { dead.add(c) }
        }
        if (dead.isNotEmpty()) clients.removeAll(dead.toSet())
        sent += a.size / EV
    }

    companion object {
        private const val EV = 16
        private const val EV_SYN = 0
        private const val EV_KEY = 1
        private const val EV_ABS = 3
        private const val SYN_REPORT = 0
        private const val SYN_MT_REPORT = 2
        private const val ABS_X = 0
        private const val ABS_Y = 1
        private const val ABS_PRESSURE = 24
        private const val ABS_MT_TOUCH_MAJOR = 48
        private const val ABS_MT_WIDTH_MAJOR = 50
        private const val ABS_MT_POSITION_X = 53
        private const val ABS_MT_POSITION_Y = 54
        private const val ABS_MT_TRACKING_ID = 57
        private const val ABS_MT_PRESSURE = 58
        private const val BTN_TOUCH = 330

        const val KEY_HOME = 102
        const val KEY_BACK = 158
        const val KEY_MENU = 139
        const val KEY_POWER = 116
        const val KEY_SEARCH = 217
        const val KEY_VOLUMEDOWN = 114
        const val KEY_VOLUMEUP = 115
        const val KEY_APPSELECT = 580
    }
}
