package app.aemu.core

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The guest's LEDs: the emulator's lights HAL (native/lightshal) writes one line per LightsService call into the
 * FIFO /dev/aemu_lights: "<light> <argb hex> <flash mode> <on ms> <off ms>". The notification/attention/battery LED
 * is what the VM screen shows (an illumination bar on Sony firmwares, a dot elsewhere).
 *
 * Samsung (TouchWiz 4.x–6.x) drives its "SVC LED" through sysfs instead of the HAL: /sys/class/sec/led/led_pattern
 * (1 charging, 2 charging error, 3 missed notification, 4 low battery, 5 fully charged, 6 powering) and led_blink
 * ("0xAARRGGBB <on ms> <off ms>", apps' own notification colours). Both are FIFOs read here.
 */
class Lights(private val root: File, private val log: (String) -> Unit) {
    data class Led(val color: Int, val onMs: Int, val offMs: Int) {
        val lit get() = color and 0xffffff != 0
    }

    private val fifo = File(root, "dev/aemu_lights")
    private val samsung = listOf("led_pattern", "led_blink").map { File(root, "sys/class/sec/led/$it") }
    @Volatile private var running = false
    private val state = HashMap<String, Led>()
    private val listeners = CopyOnWriteArrayList<(Led?) -> Unit>()
    @Volatile var current: Led? = null
        private set

    fun onChange(l: (Led?) -> Unit) { listeners.add(l) }

    fun start() {
        running = true
        runCatching {
            mkfifo(fifo)
            Thread({ readHal() }, "lights").apply { isDaemon = true; start() }
        }.onFailure { log("lights: FIFO not created: $it") }
        for (f in samsung) runCatching {
            mkfifo(f)
            Thread({ readSamsung(f) }, "lights-${f.name}").apply { isDaemon = true; start() }
        }
        runCatching { File(root, "sys/class/sec/led/led_lowpower").let { if (!it.exists()) it.writeText("0") } }
    }

    fun stop() {
        running = false
        // wake the readers
        for (f in samsung + fifo) runCatching { File(f.absolutePath).appendText("0\n") }
    }

    private fun mkfifo(f: File) {
        f.delete(); f.parentFile?.mkdirs()
        Os.mkfifo(f.absolutePath, "666".toInt(8))
        Os.chmod(f.absolutePath, "666".toInt(8))
    }

    private fun readHal() {
        // O_RDWR: opening does not wait for a writer and the reader gets no EOF between the HAL's short writes
        val fd = Os.open(fifo.absolutePath, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
        FileInputStream(fd).bufferedReader().use { r ->
            while (running) {
                val line = runCatching { r.readLine() }.getOrNull() ?: break
                val p = line.trim().split(' ')
                if (p.size < 5) continue
                publish(p[0], Led(p[1].toLongOrNull(16)?.toInt() ?: continue, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0))
            }
        }
    }

    private fun readSamsung(f: File) {
        val fd = Os.open(f.absolutePath, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
        val buf = ByteArray(128)
        FileInputStream(fd).use { s ->
            while (running) {
                val n = runCatching { s.read(buf) }.getOrDefault(-1)
                if (n <= 0) break
                val t = String(buf, 0, n).trim().lines().lastOrNull()?.trim() ?: continue
                if (f.name == "led_pattern") {
                    val id = t.toIntOrNull() ?: 0
                    val led = when (id) {
                        1 -> Led(0xff0000, 0, 0)
                        2 -> Led(0xff0000, 500, 500)
                        3 -> Led(0x0000ff, 500, 5000)
                        4 -> Led(0xff0000, 500, 5000)
                        5 -> Led(0x00ff00, 0, 0)
                        6 -> Led(0x00ffff, 1000, 1000)
                        else -> Led(0, 0, 0)
                    }
                    // pattern 0 switches the LED off whatever lit it
                    if (id == 0) { publish("battery", led); publish("notifications", led) }
                    else publish(if (id == 3 || id == 6) "notifications" else "battery", led)
                } else {
                    val p = t.split(' ').filter { it.isNotEmpty() }
                    publish("notifications", Led(p[0].removePrefix("0x").toLongOrNull(16)?.toInt() ?: 0,
                        p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2)?.toIntOrNull() ?: 0))
                }
            }
        }
    }

    /** The LED shows the most urgent request, like LightsService/NotificationManager on a phone. */
    private fun publish(name: String, led: Led) {
        val shown = synchronized(state) {
            state[name] = led
            listOf("attention", "notifications", "battery").firstNotNullOfOrNull { n -> state[n]?.takeIf { it.lit } }
        }
        if (shown != current) {
            current = shown
            listeners.forEach { runCatching { it(shown) } }
        }
    }
}
