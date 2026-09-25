package app.aemu.core

import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Замена property_service из init: принимает setprop от гостя и пишет в общую область,
 * которую все гостевые процессы отображают через fd 30 (ANDROID_PROPERTY_WORKSPACE).
 * persist.* сохраняются в /data/property, как у настоящего init.
 */
class PropService(private val paths: VmPaths, private val log: (String) -> Unit) {
    @Volatile var area: PropArea? = null
        private set
    @Volatile var writes = 0L
        private set
    @Volatile var rejects = 0L
        private set
    /** ctl.start / ctl.stop от гостя */
    var onCtl: ((start: Boolean, service: String) -> Unit)? = null
    /** наблюдатель за изменением свойств (например sys.boot_completed) */
    var onSet: ((String, String) -> Unit)? = null

    private val server = UnixServer(paths.socket("property_service"), "props") { c ->
        val din = DataInputStream(c.inputStream)
        val body = ByteArray(128)
        din.readFully(body)
        val b = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.getInt()
        val name = cstr(body, 4, 32)
        val value = cstr(body, 36, 92)
        if (cmd == 1) apply(name, value) else log("свойства: неизвестная команда $cmd")
    }

    /** Разворачивает область из шаблона образа и подмешивает сохранённые persist.*. */
    @Synchronized
    fun prepare(overrides: Map<String, String>): Boolean {
        close()
        val tpl = paths.propsTemplate
        val f = paths.props
        if (!tpl.isFile) {
            log("свойства: нет шаблона ${tpl.name}")
            return false
        }
        f.parentFile?.mkdirs()
        tpl.copyTo(f, overwrite = true)
        val a = PropArea.open(f)
        if (a == null) {
            log("свойства: область не распознана")
            return false
        }
        area = a
        val pd = File(paths.root, "data/property").apply { mkdirs() }
        var n = 0
        pd.listFiles()?.forEach { p ->
            if (p.isFile && p.name.startsWith("persist.")) {
                if (a.put(p.name, runCatching { p.readText().trim() }.getOrDefault(""))) n++
            }
        }
        // наши значения важнее сохранённых гостем
        for ((k, v) in overrides) a.put(k, v)
        writes = 0; rejects = 0
        log("свойства: записей ${a.count}, свободно ${a.room()}" + if (n > 0) ", сохранённых persist.* $n" else "")
        return true
    }

    fun serve(): Boolean = server.start(log)

    fun get(name: String): String? = area?.get(name)

    fun put(name: String, value: String): Boolean = area?.put(name, value) ?: false

    @Synchronized
    private fun apply(name: String, value: String) {
        if (name.isEmpty()) { rejects++; return }
        if (name.startsWith("ctl.")) {
            val what = name.removePrefix("ctl.")
            onCtl?.invoke(what == "start" || what == "restart", value) ?: log("свойства: ctl.$what=$value (служб нет)")
            return
        }
        if (name.startsWith("ro.") && get(name) != null) { rejects++; return }
        if (!put(name, value)) {
            rejects++
            log("свойства: не записал $name=$value (места нет?)")
            return
        }
        writes++
        if (name.startsWith("persist.")) {
            runCatching { File(paths.root, "data/property/$name").writeText(value) }
        }
        onSet?.invoke(name, value)
    }

    @Synchronized
    fun close() {
        area?.close()
        area = null
    }

    fun stop() {
        server.stop()
        close()
    }

    /** Отдельная копия области для mediaserver с ro.kernel.qemu=1 (эмуляторный звук). */
    fun privateCopy(name: String, extra: Map<String, String>): File? {
        val src = paths.props
        val dst = File(paths.bin, "props.$name")
        return runCatching {
            src.copyTo(dst, overwrite = true)
            PropArea.open(dst)?.use { a -> extra.forEach { (k, v) -> a.put(k, v) } }
            dst
        }.getOrNull()
    }

    private fun cstr(b: ByteArray, at: Int, max: Int): String {
        var n = 0
        while (n < max && b[at + n] != 0.toByte()) n++
        return String(b, at, n, Charsets.UTF_8)
    }
}
