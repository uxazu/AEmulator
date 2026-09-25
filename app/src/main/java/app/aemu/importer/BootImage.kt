package app.aemu.importer

import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * Достаёт рамдиск (init.rc, default.prop, sbin/…) из boot.img любого производителя:
 *  - стандартный заголовок "ANDROID!" (Google, HTC, Sony, MIUI, CM…), с MTK-заголовком рамдиска;
 *  - Samsung zImage со встроенным initramfs (2.3–4.x: в .tar из Odin лежит zImage/boot.img без "ANDROID!").
 */
object BootImage {
    data class CpioEntry(val name: String, val mode: Int, val data: ByteArray)

    fun ramdisk(img: ByteArray): List<CpioEntry>? {
        val bb = ByteBuffer.wrap(img).order(ByteOrder.LITTLE_ENDIAN)
        if (img.size > 64 && String(img, 0, 8, Charsets.ISO_8859_1) == "ANDROID!") {
            val kernelSize = bb.getInt(8).toLong() and 0xffffffffL
            val ramdiskSize = bb.getInt(16).toLong() and 0xffffffffL
            val pageSize = bb.getInt(36).let { if (it in 2048..131072) it else 2048 }
            val kPages = (kernelSize + pageSize - 1) / pageSize
            var off = (1 + kPages) * pageSize
            var len = ramdiskSize
            if (off + len > img.size) return null
            // MediaTek: перед сжатым рамдиском 512 байт своего заголовка
            if (bb.getInt(off.toInt()) == 0x58881688) { off += 512; len -= 512 }
            val rd = img.copyOfRange(off.toInt(), (off + len).toInt())
            return cpio(decompress(rd) ?: return null)
        }
        // иначе — zImage со встроенным initramfs
        return fromZImage(img)
    }

    private fun fromZImage(img: ByteArray): List<CpioEntry>? {
        // ищем сжатое ядро внутри zImage: gzip (большинство), LZMA/XZ (i9100 и др. кастомные ядра)
        for (i in 0 until img.size - 6) {
            val gz = img[i] == 0x1f.toByte() && img[i + 1] == 0x8b.toByte() && img[i + 2] == 0x08.toByte()
            val lz = img[i] == 0x5d.toByte() && img[i + 1] == 0x00.toByte() && img[i + 2] == 0x00.toByte() &&
                (img[i + 3] == 0x80.toByte() || img[i + 3] == 0x40.toByte() || img[i + 3] == 0x00.toByte() || img[i + 3] == 0x01.toByte())
            val xz = img[i] == 0xfd.toByte() && img[i + 1] == '7'.code.toByte() && img[i + 2] == 'z'.code.toByte() &&
                img[i + 3] == 'X'.code.toByte() && img[i + 4] == 'Z'.code.toByte()
            if (!gz && !lz && !xz) continue
            val kernel = runCatching {
                val tail = img.copyOfRange(i, img.size)
                when {
                    gz -> gunzipLenient(tail)
                    lz -> lzmaLenient(tail)
                    else -> XZInputStream(ByteArrayInputStream(tail)).readBytes()
                }
            }.getOrNull() ?: continue
            if (kernel.size < 1_000_000) continue
            findCpio(kernel)?.let { return it }
        }
        return null
    }

    /** LZMA без размера в заголовке может кончаться мусором — берём, сколько распаковалось. */
    private fun lzmaLenient(d: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        runCatching {
            LZMAInputStream(ByteArrayInputStream(d)).use { s ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            }
        }
        return out.toByteArray()
    }

    /**
     * Двухступенчатые рамдиски Samsung (i9100/i9300 на CM/Omni/MIUI): во встроенном initramfs лежит
     * только загрузчик, а настоящий рамдиск — вложенный архив stage1/boot.cpio, sbin/ramdisk.cpio…
     */
    private fun nestedBoot(entries: List<CpioEntry>): List<CpioEntry>? {
        val cand = entries.filter { e ->
            val n = e.name.substringAfterLast('/')
            (n.startsWith("boot") || n.startsWith("ramdisk")) && (n.endsWith(".cpio") || n.contains(".cpio."))
        }
        for (e in cand) {
            val inner = decompress(e.data) ?: continue
            val list = runCatching { cpio(inner) }.getOrNull() ?: continue
            if (list.any { it.name == "init.rc" }) return list
        }
        return null
    }

    /** initramfs внутри распакованного ядра: несжатый cpio или gzip/lzma с cpio. */
    private fun findCpio(k: ByteArray): List<CpioEntry>? {
        val magic = "070701".toByteArray()
        var i = 0
        while (i < k.size - 6) {
            if (k[i] == magic[0] && k[i + 1] == magic[1] && k[i + 2] == magic[2] && k[i + 3] == magic[3] && k[i + 4] == magic[4] && k[i + 5] == magic[5]) {
                val entries = runCatching { cpio(k.copyOfRange(i, k.size)) }.getOrNull()
                if (entries != null && entries.any { it.name == "init.rc" }) return entries
                if (entries != null) nestedBoot(entries)?.let { return it }
            }
            if (k[i] == 0x1f.toByte() && k[i + 1] == 0x8b.toByte() && k[i + 2] == 0x08.toByte()) {
                val inner = runCatching { gunzipLenient(k.copyOfRange(i, minOf(k.size, i + 64 * 1024 * 1024))) }.getOrNull()
                if (inner != null && inner.size > 6 && String(inner, 0, 6) == "070701") {
                    val entries = runCatching { cpio(inner) }.getOrNull()
                    if (entries != null && entries.any { it.name == "init.rc" }) return entries
                    if (entries != null) nestedBoot(entries)?.let { return it }
                }
            }
            i++
        }
        return null
    }

    fun decompress(d: ByteArray): ByteArray? {
        if (d.size < 6) return null
        return runCatching {
            when {
                d[0] == 0x1f.toByte() && d[1] == 0x8b.toByte() -> gunzipLenient(d)
                d[0] == 0x5d.toByte() && d[1] == 0x00.toByte() -> LZMAInputStream(ByteArrayInputStream(d)).readBytes()
                d[0] == 0xfd.toByte() && String(d, 1, 4) == "7zXZ" -> XZInputStream(ByteArrayInputStream(d)).readBytes()
                d[0] == 0x02.toByte() && d[1] == 0x21.toByte() && d[2] == 0x4c.toByte() -> Lz4Legacy.decode(d)
                String(d, 0, 6) == "070701" -> d
                else -> null
            }
        }.getOrNull()
    }

    /** gzip, который не падает на мусоре после конца потока (частый случай в boot.img). */
    fun gunzipLenient(d: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        try {
            GZIPInputStream(ByteArrayInputStream(d), 65536).use { g ->
                val b = ByteArray(65536)
                while (true) { val n = g.read(b); if (n < 0) break; out.write(b, 0, n) }
            }
        } catch (e: Exception) {
            if (out.size() == 0) {
                // сырой deflate после 10-байтного заголовка
                val inf = Inflater(true)
                inf.setInput(d, 10, d.size - 10)
                val b = ByteArray(65536)
                while (!inf.finished()) { val n = inf.inflate(b); if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break; out.write(b, 0, n) }
            }
        }
        return out.toByteArray()
    }

    fun cpio(d: ByteArray): List<CpioEntry> {
        val out = ArrayList<CpioEntry>()
        var off = 0
        while (off + 110 <= d.size) {
            val m = String(d, off, 6, Charsets.ISO_8859_1)
            if (m != "070701" && m != "070702") break
            fun hex(i: Int) = String(d, off + 6 + i * 8, 8, Charsets.ISO_8859_1).toLong(16)
            val mode = hex(1).toInt()
            val fileSize = hex(6).toInt()
            val nameSize = hex(11).toInt()
            val nameStart = off + 110
            val name = String(d, nameStart, maxOf(0, nameSize - 1), Charsets.UTF_8)
            var dataStart = nameStart + nameSize
            dataStart = (dataStart + 3) and 3.inv()
            if (name == "TRAILER!!!") break
            val data = d.copyOfRange(dataStart, minOf(d.size, dataStart + fileSize))
            out.add(CpioEntry(name.removePrefix("./").trimStart('/'), mode, data))
            off = (dataStart + fileSize + 3) and 3.inv()
        }
        return out
    }

    fun readAll(i: InputStream): ByteArray = i.readBytes()
}

/** Минимальный распаковщик LZ4 legacy (рамдиски некоторых ядер 4.x). */
object Lz4Legacy {
    fun decode(d: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var p = 4
        val bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        while (p + 4 <= d.size) {
            val clen = bb.getInt(p); p += 4
            if (clen == 0x184C2102 || clen <= 0 || p + clen > d.size) break
            out.write(block(d, p, clen))
            p += clen
        }
        return out.toByteArray()
    }

    private fun block(src: ByteArray, start: Int, len: Int): ByteArray {
        val out = ByteArrayOutputStream(len * 3)
        var buf = ByteArray(len * 4)
        var o = 0
        var i = start
        val end = start + len
        fun ensure(n: Int) { if (o + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, o + n)) }
        while (i < end) {
            val token = src[i++].toInt() and 0xff
            var lit = token ushr 4
            if (lit == 15) { while (true) { val b = src[i++].toInt() and 0xff; lit += b; if (b != 255) break } }
            ensure(lit); System.arraycopy(src, i, buf, o, lit); i += lit; o += lit
            if (i >= end) break
            val offset = (src[i].toInt() and 0xff) or ((src[i + 1].toInt() and 0xff) shl 8); i += 2
            var ml = token and 15
            if (ml == 15) { while (true) { val b = src[i++].toInt() and 0xff; ml += b; if (b != 255) break } }
            ml += 4
            ensure(ml)
            for (k in 0 until ml) { buf[o] = buf[o - offset]; o++ }
        }
        out.write(buf, 0, o)
        return out.toByteArray()
    }
}
