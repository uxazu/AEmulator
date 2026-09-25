package app.aemu.importer

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Тома хранилища прошивки из res/xml/storage_list.xml внутри framework-res.apk (бинарный XML).
 * MountService 4.x знает тома только отсюда и падает, если vold не сообщил о каком-то из них
 * (Samsung: /storage/extSdCard, /storage/UsbDrive…).
 */
object StorageList {
    data class Volume(val mountPoint: String, val emulated: Boolean, val removable: Boolean, val primary: Boolean)

    fun read(root: File): List<Volume> {
        val apk = File(root, "system/framework/framework-res.apk")
        if (!apk.isFile) return emptyList()
        return runCatching {
            ZipFile(apk).use { z ->
                val e = z.getEntry("res/xml/storage_list.xml") ?: return emptyList()
                parse(z.getInputStream(e).readBytes())
            }
        }.getOrDefault(emptyList())
    }

    /** Минимальный разбор AXML: пул строк и атрибуты элементов <storage>. */
    fun parse(d: ByteArray): List<Volume> {
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        var strings: List<String> = emptyList()
        val out = ArrayList<Volume>()
        var off = 8
        while (off + 8 <= d.size) {
            val type = b.getShort(off).toInt() and 0xffff
            val headerSize = b.getShort(off + 2).toInt() and 0xffff
            val size = b.getInt(off + 4)
            if (size <= 0) break
            when (type) {
                0x0001 -> strings = stringPool(b, off)
                0x0102 -> { // начало элемента
                    val nameIdx = b.getInt(off + 16 + 4)
                    val attrStart = b.getShort(off + 16 + 8).toInt() and 0xffff
                    val attrSize = b.getShort(off + 16 + 10).toInt() and 0xffff
                    val attrCount = b.getShort(off + 16 + 12).toInt() and 0xffff
                    if (strings.getOrNull(nameIdx) == "storage") {
                        val attrs = HashMap<String, Pair<String?, Int>>()
                        for (i in 0 until attrCount) {
                            val a = off + 16 + attrStart + i * attrSize
                            val an = strings.getOrNull(b.getInt(a + 4)) ?: continue
                            val raw = b.getInt(a + 8)
                            val data = b.getInt(a + 16)
                            attrs[an] = (if (raw >= 0) strings.getOrNull(raw) else null) to data
                        }
                        val mp = attrs["mountPoint"]?.first
                        if (mp != null && mp.startsWith("/")) {
                            fun flag(n: String) = attrs[n]?.let { (s, v) -> s?.equals("true", true) ?: (v != 0) } ?: false
                            out.add(Volume(mp, flag("emulated"), flag("removable"), flag("primary")))
                        }
                    }
                }
            }
            off += size
            if (headerSize <= 0) break
        }
        return out
    }

    private fun stringPool(b: ByteBuffer, off: Int): List<String> {
        val count = b.getInt(off + 8)
        val flags = b.getInt(off + 16)
        val stringsStart = b.getInt(off + 20)
        val utf8 = flags and 0x100 != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            var p = off + stringsStart + b.getInt(off + 28 + i * 4)
            if (utf8) {
                var n = b.get(p).toInt() and 0xff; p++
                if (n and 0x80 != 0) p++
                n = b.get(p).toInt() and 0xff; p++
                if (n and 0x80 != 0) { n = ((n and 0x7f) shl 8) or (b.get(p).toInt() and 0xff); p++ }
                val arr = ByteArray(n) { b.get(p + it) }
                out.add(String(arr, Charsets.UTF_8))
            } else {
                var n = b.getShort(p).toInt() and 0xffff; p += 2
                if (n and 0x8000 != 0) { n = ((n and 0x7fff) shl 16) or (b.getShort(p).toInt() and 0xffff); p += 2 }
                val sb = StringBuilder(n)
                for (k in 0 until n) sb.append(b.getShort(p + k * 2).toInt().toChar())
                out.add(sb.toString())
            }
        }
        return out
    }
}
