package app.aemu.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Область системных свойств в формате bionic v1 ("PROP"/0x45434f76).
 * Этот формат понимают bionic 2.x напрямую и 4.x–5.x в режиме совместимости,
 * когда область передана через ANDROID_PROPERTY_WORKSPACE.
 *
 *  0: count, 4: serial, 8: magic, 12: version, 16..31: резерв
 * 32: оглавление — по 4 байта: (длина имени << 24) | смещение prop_info
 * prop_info (128 байт): name[32], serial(4) = (длина значения << 24) | счётчик, value[92]
 */
class PropArea private constructor(private val b: ByteBuffer, private val chan: FileChannel?) : AutoCloseable {

    val count: Int get() = b.getInt(0)

    fun get(name: String): String? {
        val off = find(name) ?: return null
        val serial = b.getInt(off + NAME_MAX)
        val len = (serial ushr 24).coerceIn(0, VALUE_MAX - 1)
        val v = ByteArray(len)
        for (i in 0 until len) v[i] = b.get(off + NAME_MAX + 4 + i)
        return String(v, Charsets.UTF_8)
    }

    fun all(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until count) {
            val e = b.getInt(TOC + i * 4)
            val off = e and 0xffffff
            val nl = e ushr 24
            val nb = ByteArray(nl) { b.get(off + it) }
            val n = String(nb, Charsets.UTF_8)
            out.add(n to (get(n) ?: ""))
        }
        return out
    }

    @Synchronized
    fun put(name: String, value: String): Boolean {
        val nb = name.toByteArray(Charsets.UTF_8)
        var vb = value.toByteArray(Charsets.UTF_8)
        if (nb.isEmpty() || nb.size >= NAME_MAX) return false
        if (vb.size >= VALUE_MAX) vb = vb.copyOf(VALUE_MAX - 1)
        val off = find(name)
        if (off != null) {
            val s0 = b.getInt(off + NAME_MAX)
            b.putInt(off + NAME_MAX, s0 or 1) // нечётный — «пишу»
            for (i in vb.indices) b.put(off + NAME_MAX + 4 + i, vb[i])
            b.put(off + NAME_MAX + 4 + vb.size, 0)
            b.putInt(off + NAME_MAX, (vb.size shl 24) or (((s0 or 1) + 1) and 0xffffff))
            b.putInt(4, b.getInt(4) + 1)
            return true
        }
        val n = count
        val at = nextInfo(n)
        if ((n + 1) * 4 + TOC > firstInfo(n)) return false
        if (at + INFO > b.capacity()) return false
        for (i in 0 until INFO) b.put(at + i, 0)
        for (i in nb.indices) b.put(at + i, nb[i])
        for (i in vb.indices) b.put(at + NAME_MAX + 4 + i, vb[i])
        b.putInt(at + NAME_MAX, vb.size shl 24)
        b.putInt(TOC + n * 4, (nb.size shl 24) or at)
        b.putInt(0, n + 1)
        b.putInt(4, b.getInt(4) + 1)
        return true
    }

    fun room(): Int {
        val n = count
        val byToc = (firstInfo(n) - TOC) / 4 - n
        val byTail = (b.capacity() - nextInfo(n)) / INFO
        return maxOf(0, minOf(byToc, byTail))
    }

    private fun find(name: String): Int? {
        val nb = name.toByteArray(Charsets.UTF_8)
        for (i in 0 until count) {
            val e = b.getInt(TOC + i * 4)
            if ((e ushr 24) != nb.size) continue
            val off = e and 0xffffff
            var same = true
            for (j in nb.indices) if (b.get(off + j) != nb[j]) { same = false; break }
            if (same && b.get(off + nb.size) == 0.toByte()) return off
        }
        return null
    }

    private fun firstInfo(n: Int): Int {
        if (n == 0) return INFO_START
        var m = Int.MAX_VALUE
        for (i in 0 until n) m = minOf(m, b.getInt(TOC + i * 4) and 0xffffff)
        return m
    }

    private fun nextInfo(n: Int): Int {
        if (n == 0) return firstInfo(0)
        var m = 0
        for (i in 0 until n) m = maxOf(m, b.getInt(TOC + i * 4) and 0xffffff)
        return m + INFO
    }

    override fun close() {
        (b as? MappedByteBuffer)?.force()
        runCatching { chan?.close() }
    }

    companion object {
        const val SIZE = 131072
        const val MAGIC = 0x504f5250
        const val VERSION = 0x45434f76
        private const val TOC = 32
        private const val INFO = 128
        private const val NAME_MAX = 32
        private const val VALUE_MAX = 92
        private const val INFO_START = 4096

        /** Создаёт пустую область в файле. */
        fun create(f: File): PropArea {
            f.parentFile?.mkdirs()
            RandomAccessFile(f, "rw").use { it.setLength(0); it.setLength(SIZE.toLong()) }
            val a = open(f) ?: error("не открылась область свойств")
            a.b.putInt(0, 0); a.b.putInt(4, 0); a.b.putInt(8, MAGIC); a.b.putInt(12, VERSION)
            return a
        }

        /** Отображает существующую область; null, если это не v1. */
        fun open(f: File): PropArea? {
            val ch = RandomAccessFile(f, "rw").channel
            val m = ch.map(FileChannel.MapMode.READ_WRITE, 0, f.length())
            m.order(ByteOrder.LITTLE_ENDIAN)
            val a = PropArea(m, ch)
            if (m.getInt(8) != MAGIC && !(m.getInt(8) == 0 && m.getInt(0) == 0)) { a.close(); return null }
            return a
        }

        /** Разбор build.prop/default.prop: ключ=значение, комментарии, import не поддерживаем. */
        fun parseProps(text: String): LinkedHashMap<String, String> {
            val out = LinkedHashMap<String, String>()
            for (raw in text.lineSequence()) {
                val ln = raw.trim()
                if (ln.isEmpty() || ln.startsWith("#") || ln.startsWith("import ")) continue
                val i = ln.indexOf('=')
                if (i <= 0) continue
                out[ln.substring(0, i).trim()] = ln.substring(i + 1).trim()
            }
            return out
        }
    }
}
