package app.aemu.importer

import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sony SIN v3 (Xperia 2015–2017 .ftf / Flashtool / Emma: system.sin, kernel.sin…).
 *
 *   "\x03SIN" | header length (BE) | … block hash table …
 *   "MMCF" | body length (BE) | records: GPTP (partition uuid), LZ4A (LZ4 block → image offset), ADDR (raw → image offset)
 *   data area: the records' payloads, offsets counted from its start
 *
 * The payloads are written at their offsets in a sparse file (unlisted ranges stay holes = zeros), which is the
 * partition image (ext4 for system, an ELF kernel with the ramdisk for kernel.sin).
 */
object SinImage {
    fun probe(h: ByteArray) = h.size >= 8 && h[0].toInt() == 3 && h[1] == 'S'.code.toByte() && h[2] == 'I'.code.toByte() && h[3] == 'N'.code.toByte()

    private class Rec(val dataOff: Long, val dataLen: Long, val outLen: Long, val fileOff: Long, val lz4: Boolean)

    /** Streams the SIN into [out] (created or truncated); returns the image size. */
    fun unpack(input: InputStream, out: File, progress: (Long) -> Unit = {}): Long {
        val s = DataInputStream(input.buffered(1 shl 20))
        val h = ByteArray(8); s.readFully(h)
        if (!probe(h)) throw IOException("not a SIN v3 file")
        val hdrLen = ByteBuffer.wrap(h, 4, 4).int.toLong() and 0xffffffffL
        skipFully(s, hdrLen - 8)
        val mh = ByteArray(8); s.readFully(mh)
        if (String(mh, 0, 4, Charsets.ISO_8859_1) != "MMCF") throw IOException("SIN: no MMCF block (unsupported SIN layout)")
        val body = ByteArray(ByteBuffer.wrap(mh, 4, 4).int); s.readFully(body)
        val b = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        val recs = ArrayList<Rec>()
        var p = 0
        while (p + 8 <= body.size) {
            val magic = String(body, p, 4, Charsets.ISO_8859_1)
            val len = b.getInt(p + 4)
            if (len <= 0) break
            when (magic) {
                "LZ4A" -> recs += Rec(b.getLong(p + 8), b.getLong(p + 24), b.getLong(p + 16), b.getLong(p + 32), true)
                "ADDR" -> recs += Rec(b.getLong(p + 8), b.getLong(p + 16), b.getLong(p + 16), b.getLong(p + 24), false)
            }
            p += len
        }
        if (recs.isEmpty()) throw IOException("SIN: no data records")
        recs.sortBy { it.dataOff }
        var pos = 0L
        var size = 0L
        RandomAccessFile(out, "rw").use { raf ->
            raf.setLength(0)
            for (r in recs) {
                if (r.dataOff < pos) throw IOException("SIN: overlapping records")
                skipFully(s, r.dataOff - pos)
                val chunk = ByteArray(r.dataLen.toInt()); s.readFully(chunk)
                pos = r.dataOff + r.dataLen
                val data = if (r.lz4) BlockLZ4CompressorInputStream(ByteArrayInputStream(chunk)).use { it.readBytes() } else chunk
                raf.seek(r.fileOff); raf.write(data, 0, minOf(data.size.toLong(), r.outLen).toInt())
                size = maxOf(size, r.fileOff + r.outLen)
                progress(pos)
            }
            if (raf.length() < size) raf.setLength(size)
        }
        return size
    }

    private fun skipFully(s: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val k = s.skip(left)
            if (k <= 0) { if (s.read() < 0) throw IOException("SIN: unexpected end"); left-- } else left -= k
        }
    }
}

/**
 * Spreadtrum / Unisoc firmware package (.pac, ResearchDownload). Header: version (UTF-16, 22 chars), size,
 * product name and version (UTF-16, 256 chars each), file count at 1072, offset of the file table at 1076.
 * Each file entry starts with its own size; file id and name are UTF-16 (256 chars each), size at 1532,
 * data offset at 1544.
 */
object PacFile {
    data class Entry(val id: String, val name: String, val offset: Long, val size: Long)

    fun probe(h: ByteArray): Boolean = h.size >= 1100 && utf16(h, 0, 22).startsWith("BP_R")

    fun entries(src: RandomSource): List<Entry> {
        val h = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        src.read(0, h); h.flip()
        val count = h.getInt(1072)
        var off = h.getInt(1076).toLong() and 0xffffffffL
        if (count !in 1..512 || off <= 0) throw IOException("PAC: bad file table")
        val out = ArrayList<Entry>()
        repeat(count) {
            val e = ByteBuffer.allocate(2600).order(ByteOrder.LITTLE_ENDIAN)
            src.read(off, e); e.flip()
            val entSize = e.getInt(0).takeIf { it in 1600..4096 } ?: 2580
            val id = utf16(e.array(), 4, 256)
            val name = utf16(e.array(), 516, 256)
            val size = e.getInt(1532).toLong() and 0xffffffffL
            val data = e.getInt(1544).toLong() and 0xffffffffL
            if (size > 0 && data > 0 && name.isNotEmpty()) out += Entry(id, name, data, size)
            off += entSize
        }
        return out
    }

    private fun utf16(b: ByteArray, off: Int, chars: Int): String {
        val sb = StringBuilder()
        for (i in 0 until chars) {
            val p = off + i * 2
            if (p + 1 >= b.size) break
            val c = ((b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)).toChar()
            if (c == '\u0000') break
            sb.append(c)
        }
        return sb.toString()
    }
}

/** A file tree of a partition image, delivered object by object (directories before their contents). */
interface TreeSource {
    /** [link] is set for symlinks; [data] opens the contents of a regular file */
    fun walk(visit: (path: String, isDir: Boolean, link: String?, mode: Int, size: Long, data: (() -> InputStream)?) -> Unit)
}

/**
 * YAFFS2 images (mkyaffs2image: system.img of Android 1.x–2.3 phones on raw NAND — Nexus One, HTC Dream/Magic/Hero,
 * Desire, early Samsung/LG). Chunks of page + spare; the packed tags at the start of the spare give object id, chunk id
 * and byte count. Chunk 0 of an object is its header (type, parent, name, mode, size, symlink alias).
 * Page/spare sizes are probed (2048+64, 4096+128, 512+16).
 */
class Yaffs2Image(private val src: RandomSource) : TreeSource {
    private class Obj(val id: Int, val type: Int, val parent: Int, val name: String, val mode: Int, val size: Long, val alias: String, val equiv: Int)

    private var page = 2048
    private var spare = 64
    private val objs = HashMap<Int, Obj>()
    private val chunks = HashMap<Int, HashMap<Int, Pair<Long, Int>>>()   // obj → chunk → (file offset, bytes)

    companion object {
        private val GEOMETRIES = listOf(2048 to 64, 4096 to 128, 512 to 16, 8192 to 256, 16384 to 512)

        fun probe(src: RandomSource): Boolean = GEOMETRIES.any { (p, s) -> headerAt(src, 0, p, s) != null }

        /** an object header at [off] with a plausible type, parent and name */
        private fun headerAt(src: RandomSource, off: Long, page: Int, spare: Int): Pair<Int, Int>? {
            if (src.size < (page + spare).toLong()) return null
            val d = ByteBuffer.allocate(page + spare).order(ByteOrder.LITTLE_ENDIAN)
            src.read(off, d); d.flip()
            val type = d.getInt(0)
            val parent = d.getInt(4)
            if (type !in 1..5 || parent !in 1..0xfffff) return null
            val nameOk = (0 until 8).all { val c = d.get(10 + it).toInt() and 0xff; c == 0 || c in 0x20..0x7e } && d.get(10) != 0.toByte()
            if (!nameOk) return null
            val objId = d.getInt(page + 4) and 0x0fffffff
            return if (objId in 1..0xfffff) type to objId else null
        }
    }

    init {
        val (p, s) = GEOMETRIES.firstOrNull { (p, s) -> headerAt(src, 0, p, s) != null } ?: throw IOException("not a YAFFS2 image")
        page = p; spare = s
        val step = (page + spare).toLong()
        val seqOf = HashMap<Long, Int>()
        val buf = ByteBuffer.allocate(page + spare).order(ByteOrder.LITTLE_ENDIAN)
        var off = 0L
        while (off + step <= src.size) {
            buf.clear(); src.read(off, buf); buf.flip()
            val seq = buf.getInt(page)
            var objId = buf.getInt(page + 4)
            val chunkRaw = buf.getInt(page + 8)
            val bytes = buf.getInt(page + 12)
            val erased = seq == -1 || seq == 0
            if (!erased) {
                val header = chunkRaw == 0 || chunkRaw and 0x80000000.toInt() != 0
                objId = objId and 0x0fffffff
                if (header) {
                    val type = buf.getInt(0)
                    if (type in 1..5) {
                        val nb = ByteArray(256); buf.position(10); buf.get(nb)
                        val name = String(nb, 0, nb.indexOf(0).let { if (it < 0) 256 else it }, Charsets.UTF_8)
                        val mode = buf.getInt(268)
                        val size = buf.getInt(292).toLong() and 0xffffffffL
                        val equiv = buf.getInt(296)
                        val ab = ByteArray(160); buf.position(300); buf.get(ab)
                        val alias = String(ab, 0, ab.indexOf(0).let { if (it < 0) 160 else it }, Charsets.UTF_8)
                        val prev = seqOf[objId.toLong()]
                        if (prev == null || seq >= prev) {
                            objs[objId] = Obj(objId, type, buf.getInt(4), name, mode, size, alias, equiv)
                            seqOf[objId.toLong()] = seq
                        }
                    }
                } else if (chunkRaw > 0 && bytes in 0..page) {
                    chunks.getOrPut(objId) { HashMap() }[chunkRaw] = off to bytes
                }
            }
            off += step
        }
        if (objs.isEmpty()) throw IOException("YAFFS2: no objects")
    }

    override fun walk(visit: (String, Boolean, String?, Int, Long, (() -> InputStream)?) -> Unit) {
        val kids = objs.values.groupBy { it.parent }
        fun go(parent: Int, prefix: String, depth: Int) {
            if (depth > 64) return
            for (o in kids[parent].orEmpty()) {
                if (o.id == parent || o.name.isEmpty() || o.name == "lost+found" && prefix.isEmpty()) continue
                val path = if (prefix.isEmpty()) o.name else "$prefix/${o.name}"
                when (o.type) {
                    3 -> { visit(path, true, null, o.mode, 0, null); go(o.id, path, depth + 1) }
                    2 -> visit(path, false, o.alias, o.mode, 0, null)
                    1 -> visit(path, false, null, o.mode, o.size) { contents(o.id, o.size) }
                    4 -> objs[o.equiv]?.takeIf { it.type == 1 }?.let { t -> visit(path, false, null, t.mode, t.size) { contents(t.id, t.size) } }
                }
            }
        }
        go(1, "", 0)
    }

    private fun contents(id: Int, size: Long): InputStream {
        val map = chunks[id].orEmpty()
        val out = ByteArray(size.toInt())
        for ((chunk, loc) in map) {
            val at = (chunk - 1).toLong() * page
            if (at >= size) continue
            val n = minOf(loc.second.toLong(), size - at).toInt()
            val bb = ByteBuffer.wrap(out, at.toInt(), n)
            src.read(loc.first, bb)
        }
        return ByteArrayInputStream(out)
    }
}

/**
 * FAT12/16/32 with long names: Samsung RFS (factoryfs.rfs / dbdata.rfs of Galaxy S, Galaxy Tab, Wave… on 2.x) is FAT
 * underneath. RFS keeps symlinks as small files with the SYSTEM attribute whose contents are the target path.
 */
class FatImage(private val src: RandomSource) : TreeSource {
    private val bps: Int
    private val spc: Int
    private val firstData: Long
    private val fat: ByteArray
    private val fat32: Boolean
    private val fat12: Boolean
    private val rootCluster: Int
    private val rootDirOff: Long
    private val rootEntries: Int

    companion object {
        fun probe(src: RandomSource): Boolean {
            if (src.size < 4096) return false
            val b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
            src.read(0, b); b.flip()
            val sig = (b.get(510).toInt() and 0xff) == 0x55 && (b.get(511).toInt() and 0xff) == 0xaa
            val bps = b.getShort(11).toInt() and 0xffff
            val spc = b.get(13).toInt() and 0xff
            val fats = b.get(16).toInt() and 0xff
            return sig && bps in listOf(512, 1024, 2048, 4096) && spc > 0 && spc and (spc - 1) == 0 && fats in 1..2
        }
    }

    init {
        val b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        src.read(0, b); b.flip()
        bps = b.getShort(11).toInt() and 0xffff
        spc = b.get(13).toInt() and 0xff
        val reserved = b.getShort(14).toInt() and 0xffff
        val fats = b.get(16).toInt() and 0xff
        rootEntries = b.getShort(17).toInt() and 0xffff
        val total16 = b.getShort(19).toInt() and 0xffff
        val fatSz16 = b.getShort(22).toInt() and 0xffff
        val total = if (total16 != 0) total16.toLong() else b.getInt(32).toLong() and 0xffffffffL
        val fatSz = if (fatSz16 != 0) fatSz16.toLong() else b.getInt(36).toLong() and 0xffffffffL
        val rootSectors = ((rootEntries * 32) + bps - 1) / bps
        val dataSectors = total - reserved - fats * fatSz - rootSectors
        val clusters = dataSectors / spc
        fat12 = clusters < 4085
        fat32 = clusters >= 65525
        rootCluster = if (fat32) b.getInt(44) else 0
        rootDirOff = (reserved + fats * fatSz) * bps
        firstData = (reserved + fats * fatSz + rootSectors) * bps
        fat = ByteArray((fatSz * bps).toInt())
        src.read(reserved.toLong() * bps, ByteBuffer.wrap(fat))
    }

    private fun next(c: Int): Int = when {
        fat32 -> ByteBuffer.wrap(fat, c * 4, 4).order(ByteOrder.LITTLE_ENDIAN).int and 0x0fffffff
        fat12 -> { val o = c * 3 / 2; val v = (fat[o].toInt() and 0xff) or ((fat[o + 1].toInt() and 0xff) shl 8); if (c and 1 == 1) v shr 4 else v and 0xfff }
        else -> ByteBuffer.wrap(fat, c * 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
    }
    private fun isEnd(c: Int) = c < 2 || (if (fat32) c >= 0x0ffffff8 else if (fat12) c >= 0xff8 else c >= 0xfff8)
    private val clusterBytes get() = bps * spc

    private fun chain(start: Int): List<Int> {
        val out = ArrayList<Int>()
        var c = start
        while (!isEnd(c) && out.size < 4_000_000) { out += c; c = next(c) }
        return out
    }

    private fun readChain(start: Int, size: Long): ByteArray {
        val out = ByteArray(size.toInt())
        var o = 0
        for (c in chain(start)) {
            if (o >= out.size) break
            val n = minOf(clusterBytes, out.size - o)
            src.read(firstData + (c - 2).toLong() * clusterBytes, ByteBuffer.wrap(out, o, n))
            o += n
        }
        return out
    }

    private fun dirBytes(cluster: Int): ByteArray =
        if (cluster == 0) ByteArray(rootEntries * 32).also { src.read(rootDirOff, ByteBuffer.wrap(it)) }
        else chain(cluster).let { cs -> readChain(cluster, cs.size.toLong() * clusterBytes) }

    override fun walk(visit: (String, Boolean, String?, Int, Long, (() -> InputStream)?) -> Unit) {
        fun go(cluster: Int, prefix: String, depth: Int) {
            if (depth > 64) return
            val d = dirBytes(cluster)
            val lfn = ArrayList<String>()
            var i = 0
            while (i + 32 <= d.size) {
                val first = d[i].toInt() and 0xff
                if (first == 0) break
                val attr = d[i + 11].toInt() and 0xff
                if (first == 0xe5) { lfn.clear(); i += 32; continue }
                if (attr == 0x0f) {
                    val sb = StringBuilder()
                    for (o in listOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)) {
                        val ch = ((d[i + o].toInt() and 0xff) or ((d[i + o + 1].toInt() and 0xff) shl 8))
                        if (ch == 0 || ch == 0xffff) break
                        sb.append(ch.toChar())
                    }
                    lfn.add(0, sb.toString()); i += 32; continue
                }
                if (attr and 0x08 != 0) { lfn.clear(); i += 32; continue }   // volume label
                val short = String(d, i, 8, Charsets.ISO_8859_1).trimEnd() +
                    String(d, i + 8, 3, Charsets.ISO_8859_1).trimEnd().let { if (it.isEmpty()) "" else ".$it" }
                val name = if (lfn.isNotEmpty()) lfn.joinToString("") else short.lowercase()
                lfn.clear()
                val bb = ByteBuffer.wrap(d, i, 32).order(ByteOrder.LITTLE_ENDIAN)
                val cl = ((bb.getShort(i + 20).toInt() and 0xffff) shl 16) or (bb.getShort(i + 26).toInt() and 0xffff)
                val size = bb.getInt(i + 28).toLong() and 0xffffffffL
                i += 32
                if (name == "." || name == ".." || name.isEmpty()) continue
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (attr and 0x10 != 0) {
                    visit(path, true, null, 0x1ed, 0, null)
                    if (cl != 0 && cl != cluster) go(cl, path, depth + 1)
                } else if (attr and 0x04 != 0 && size in 1..255) {
                    val t = String(readChain(cl, size), Charsets.UTF_8).trimEnd('\u0000')
                    if (t.isNotEmpty() && t.none { it < ' ' }) visit(path, false, t, 0x1ff, 0, null)
                    else visit(path, false, null, 0x1a4, size) { ByteArrayInputStream(readChain(cl, size)) }
                } else {
                    visit(path, false, null, 0x1a4, size) { ByteArrayInputStream(readChain(cl, size)) }
                }
            }
        }
        go(if (fat32) rootCluster else 0, "", 0)
    }
}

/**
 * super.img of Android 10+ (dynamic partitions): LP metadata at 4096 + 4096 (geometry) → header, partition table and
 * extents. Returns the byte ranges (in the image) of the named partition, e.g. "system" or "system_a".
 */
object SuperImage {
    private const val GEOMETRY_MAGIC = 0x616c4467
    private const val HEADER_MAGIC = 0x414c5030

    fun probe(src: RandomSource): Boolean {
        if (src.size < 16384) return false
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        src.read(4096, b); b.flip()
        return b.int == GEOMETRY_MAGIC
    }

    /** name → list of (offset in super, length) */
    fun partitions(src: RandomSource): Map<String, List<Pair<Long, Long>>> {
        val g = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        src.read(4096, g); g.flip()
        if (g.getInt(0) != GEOMETRY_MAGIC) throw IOException("super: no LP geometry")
        val metaMax = g.getInt(40).toLong()
        // primary metadata slot 0 sits after the reserved area (4096) and both geometry copies (2 × 4096)
        val hdrOff = 4096L + 2 * 4096
        val h = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        src.read(hdrOff, h); h.flip()
        if (h.getInt(0) != HEADER_MAGIC) throw IOException("super: no LP header")
        val headerSize = h.getInt(8)
        val tablesSize = h.getInt(44)
        fun desc(at: Int) = Triple(h.getInt(at), h.getInt(at + 4), h.getInt(at + 8))   // offset, count, entry size
        val parts = desc(80)
        val extents = desc(92)
        val t = ByteBuffer.allocate(tablesSize).order(ByteOrder.LITTLE_ENDIAN)
        src.read(hdrOff + headerSize, t); t.flip()
        val ext = (0 until extents.second).map { i ->
            val o = extents.first + i * extents.third
            Triple(t.getLong(o), t.getInt(o + 8), t.getLong(o + 12))   // sectors, target type (0 = linear), target data
        }
        val out = LinkedHashMap<String, List<Pair<Long, Long>>>()
        for (i in 0 until parts.second) {
            val o = parts.first + i * parts.third
            val nb = ByteArray(36); t.position(o); t.get(nb)
            val name = String(nb, 0, nb.indexOf(0).let { if (it < 0) 36 else it }, Charsets.US_ASCII)
            val first = t.getInt(o + 40)
            val num = t.getInt(o + 44)
            out[name] = (first until first + num).mapNotNull { e ->
                ext.getOrNull(e)?.takeIf { it.second == 0 }?.let { (sectors, _, start) -> start * 512 to sectors * 512 }
            }
        }
        if (metaMax <= 0) throw IOException("super: bad geometry")
        return out
    }
}

/** A partition assembled from extents of another source (super.img). */
class ExtentSource(private val src: RandomSource, private val extents: List<Pair<Long, Long>>) : RandomSource {
    override val size: Long = extents.sumOf { it.second }
    override fun read(pos: Long, dst: ByteBuffer) {
        var p = pos
        while (dst.hasRemaining()) {
            if (p >= size) { while (dst.hasRemaining()) dst.put(0); return }
            var base = 0L
            val (off, len) = extents.first { e -> (p < base + e.second).also { if (!it) base += e.second } }
            val inExt = p - base
            val n = minOf(dst.remaining().toLong(), len - inExt).toInt()
            val lim = dst.limit()
            dst.limit(dst.position() + n)
            src.read(off + inExt, dst)
            dst.limit(lim)
            p += n
        }
    }
}
