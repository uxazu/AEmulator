package app.aemu.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The phone's accelerometer and vibration motor for the guest.
 *  - accelerometer: 16-byte records (type, x, y, z) into the FIFO /dev/aemu_sensors, read by the emulator's sensors
 *    HAL (native/sensorshal); only while the VM screen is shown;
 *  - vibration: /sys/class/timed_output/vibrator/enable is a FIFO; libhardware_legacy / the vibrator HAL of 2.3–7.x
 *    write the duration in ms there, and the phone vibrates for that long.
 */
class Sensors(private val ctx: Context, private val root: File, private val log: (String) -> Unit) : SensorEventListener {
    private val accelFifo = File(root, "dev/aemu_sensors")
    private val vibFifo = File(root, "sys/class/timed_output/vibrator/enable")
    @Volatile private var out: FileDescriptor? = null
    @Volatile private var running = false
    private val rec = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)

    fun start() {
        running = true
        runCatching {
            mkfifo(accelFifo)
            // O_RDWR | O_NONBLOCK: opening needs no reader, and a full pipe (guest not polling) drops samples
            out = Os.open(accelFifo.absolutePath, OsConstants.O_RDWR or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC, 0)
            apply()
        }.onFailure { log("sensors: accelerometer channel failed: ${it.message}") }
        runCatching {
            vibFifo.parentFile?.mkdirs()
            mkfifo(vibFifo)
            Thread({ vibrations() }, "vibrator").apply { isDaemon = true; start() }
        }.onFailure { log("sensors: vibrator channel failed: ${it.message}") }
    }

    fun stop() {
        running = false
        apply()
        runCatching { out?.let { Os.close(it) } }; out = null
        runCatching { File(vibFifo.absolutePath).appendText("0\n") }
    }

    @Volatile private var visible = false

    /** The VM screen is visible: feed the accelerometer (about 50 Hz). Remembered until the channel is up. */
    fun resume(on: Boolean) {
        visible = on
        apply()
    }

    private fun apply() {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        sm.unregisterListener(this)
        if (visible && running && out != null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val fd = out ?: return
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        synchronized(rec) {
            rec.clear()
            rec.putInt(1).putFloat(e.values[0]).putFloat(e.values[1]).putFloat(e.values[2])
            runCatching { Os.write(fd, rec.array(), 0, 16) }   // EAGAIN when the guest is not reading: dropped
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun vibrations() {
        val fd = Os.open(vibFifo.absolutePath, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
        val vib = ctx.getSystemService(Vibrator::class.java)
        val buf = ByteArray(64)
        FileInputStream(fd).use { s ->
            while (running) {
                val n = runCatching { s.read(buf) }.getOrDefault(-1)
                if (n <= 0) break
                val ms = String(buf, 0, n).trim().lines().lastOrNull()?.trim()?.toLongOrNull() ?: continue
                runCatching {
                    if (ms > 0) vib?.vibrate(VibrationEffect.createOneShot(ms.coerceAtMost(5_000), VibrationEffect.DEFAULT_AMPLITUDE)) else vib?.cancel()
                }
            }
        }
    }

    private fun mkfifo(f: File) {
        val isFifo = runCatching { OsConstants.S_ISFIFO(Os.lstat(f.absolutePath).st_mode) }.getOrDefault(false)
        if (isFifo) return
        f.delete(); f.parentFile?.mkdirs()
        Os.mkfifo(f.absolutePath, "666".toInt(8))
        Os.chmod(f.absolutePath, "666".toInt(8))
    }
}
