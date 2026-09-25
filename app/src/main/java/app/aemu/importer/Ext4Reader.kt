package app.aemu.importer

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

/** Источник с произвольным доступом (обычный файл, sparse-образ, кусок архива). */
interface RandomSource {
    val size: Long
    /** Читает ровно dst.remaining() байт с позиции pos (за концом — нули). */
    fun read(pos: Long, dst: ByteBuffer)
}

class ChannelSource(private val ch: SeekableByteChannel, private val base: Long = 0, override val size: Long = ch.size() - base) : RandomSource {
    override fun read(pos: Long, dst: ByteBuffer) {
        var p = pos
        synchronized(ch) {
            while (dst.hasRemaining()) {
                if (p >= size) { while (dst.hasRemaining()) dst.put(0); return }
                ch.position(base + p)
                val lim = dst.limit()
                val can = minOf(dst.remaining().toLong(), size - p).toInt()
                dst.limit(dst.position() + can)
                val n = ch.read(dst)
                dst.limit(lim)
                if (n <= 0) { while (dst.hasRemaining()) dst.put(0); return }
                p += n
            }
        }
    }
}

/**
 * Читатель ext2/3/4 только на чтение: суперблок, группы (32/64 бита), inode любых размеров,
 * extent-деревья и классические косвенные блоки, каталоги (линейные и htree), символьные ссылки,
 * inline data. Этого хватает для system.img любых Android 2.x–6.x.
 */
class Ext4Reader(private val src: RandomSource) {
    val blockSize: Int
    private val inodesPerGroup: Int
    private val inodeSize: Int
    private val descSize: Int
    private val groupCount: Int
    private val firstDataBlock: Long
    private val incompat: Int
    val volumeName: String

    data class Node(
        val ino: Int, val mode: Int, val uid: Int, val gid: Int, val size: Long,
        val flags: Int, val block: ByteArray, val mtime: Long,
    ) {
        val isDir get() = mode and 0xF000 == 0x4000
        val isFile get() = mode and 0xF000 == 0x8000
        val isLink get() = mode and 0xF000 == 0xA000
        val perm get() = mode and 0xFFF
    }

    init {
        val sb = buf(1024)
        src.read(1024, sb)
        sb.flip()
        if (sb.getShort(0x38).toInt() and 0xffff != 0xEF53) throw IOException("не ext2/3/4 (нет подписи 0xEF53)")
        val inodesCount = sb.getInt(0x0)
        val blocksLo = sb.getInt(0x4).toLong() and 0xffffffffL
        firstDataBlock = sb.getInt(0x14).toLong() and 0xffffffffL
        blockSize = 1024 shl sb.getInt(0x18)
        val blocksPerGroup = sb.getInt(0x20)
        inodesPerGroup = sb.getInt(0x28)
        val rev = sb.getInt(0x4C)
        inodeSize = if (rev == 0) 128 else sb.getShort(0x58).toInt() and 0xffff
        incompat = sb.getInt(0x60)
        val is64 = incompat and 0x80 != 0
        descSize = if (is64) maxOf(32, sb.getShort(0xFE).toInt() and 0xffff) else 32
        val blocksHi = if (is64) sb.getInt(0x150).toLong() and 0xffffffffL else 0
        val blocks = blocksLo or (blocksHi shl 32)
        groupCount = maxOf(((blocks - firstDataBlock + blocksPerGroup - 1) / blocksPerGroup).toInt(), (inodesCount + inodesPerGroup - 1) / inodesPerGroup)
        val nameBytes = ByteArray(16).also { for (i in 0 until 16) it[i] = sb.get(0x78 + i) }
        volumeName = String(nameBytes).trimEnd('\u0000')
    }

    private fun buf(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)

    private fun readBlock(b: Long, n: Int = blockSize): ByteBuffer {
        val bb = buf(n)
        src.read(b * blockSize, bb)
        bb.flip()
        return bb
    }

    private val inodeTableCache = HashMap<Int, Long>()

    private fun inodeTable(group: Int): Long = inodeTableCache.getOrPut(group) {
        val gdtBlock = firstDataBlock + 1
        val off = gdtBlock * blockSize + group.toLong() * descSize
        val d = buf(descSize)
        src.read(off, d); d.flip()
        val lo = d.getInt(0x8).toLong() and 0xffffffffL
        val hi = if (descSize >= 64) d.getInt(0x28).toLong() and 0xffffffffL else 0
        lo or (hi shl 32)
    }

    fun inode(ino: Int): Node {
        val g = (ino - 1) / inodesPerGroup
        val idx = (ino - 1) % inodesPerGroup
        val off = inodeTable(g) * blockSize + idx.toLong() * inodeSize
        val b = buf(minOf(inodeSize, 256).coerceAtLeast(128))
        src.read(off, b); b.flip()
        val mode = b.getShort(0).toInt() and 0xffff
        val uidLo = b.getShort(2).toInt() and 0xffff
        val sizeLo = b.getInt(4).toLong() and 0xffffffffL
        val mtime = b.getInt(0x10).toLong() and 0xffffffffL
        val gidLo = b.getShort(0x18).toInt() and 0xffff
        val flags = b.getInt(0x20)
        val block = ByteArray(60).also { for (i in 0 until 60) it[i] = b.get(0x28 + i) }
        val sizeHi = b.getInt(0x6C).toLong() and 0xffffffffL
        val uidHi = b.getShort(0x78).toInt() and 0xffff
        val gidHi = b.getShort(0x7A).toInt() and 0xffff
        return Node(ino, mode, uidLo or (uidHi shl 16), gidLo or (gidHi shl 16), sizeLo or (sizeHi shl 32), flags, block, mtime)
    }

    /** Список физических отрезков файла: (логический блок, физический блок, длина). */
    private fun extents(n: Node): List<LongArray> {
        val out = ArrayList<LongArray>()
        if (n.flags and 0x80000 != 0) { // EXT4_EXTENTS_FL
            walkExtents(ByteBuffer.wrap(n.block).order(ByteOrder.LITTLE_ENDIAN), out, 0)
        } else {
            val bb = ByteBuffer.wrap(n.block).order(ByteOrder.LITTLE_ENDIAN)
            val nblocks = (n.size + blockSize - 1) / blockSize
            var logical = 0L
            val per = blockSize / 4
            fun add(phys: Long) {
                if (logical >= nblocks) return
                if (phys != 0L) {
                    val last = out.lastOrNull()
                    if (last != null && last[0] + last[2] == logical && last[1] + last[2] == phys) last[2]++
                    else out.add(longArrayOf(logical, phys, 1))
                }
                logical++
            }
            fun indirect(blk: Long, depth: Int) {
                if (logical >= nblocks) return
                if (blk == 0L) { // дыра: пропустить все блоки этого поддерева
                    var span = 1L; repeat(depth) { span *= per }
                    logical += span; return
                }
                val ib = readBlock(blk)
                for (i in 0 until per) {
                    if (logical >= nblocks) return
                    val p = ib.getInt(i * 4).toLong() and 0xffffffffL
                    if (depth == 1) add(p) else indirect(p, depth - 1)
                }
            }
            for (i in 0 until 12) add(bb.getInt(i * 4).toLong() and 0xffffffffL)
            indirect(bb.getInt(48).toLong() and 0xffffffffL, 1)
            indirect(bb.getInt(52).toLong() and 0xffffffffL, 2)
            indirect(bb.getInt(56).toLong() and 0xffffffffL, 3)
        }
        return out
    }

    private fun walkExtents(h: ByteBuffer, out: MutableList<LongArray>, level: Int) {
        if (level > 8) return
        val magic = h.getShort(0).toInt() and 0xffff
        if (magic != 0xF30A) return
        val entries = h.getShort(2).toInt() and 0xffff
        val depth = h.getShort(6).toInt() and 0xffff
        for (i in 0 until entries) {
            val e = 12 + i * 12
            if (depth == 0) {
                val lblk = h.getInt(e).toLong() and 0xffffffffL
                var len = h.getShort(e + 4).toInt() and 0xffff
                val uninit = len > 32768
                if (uninit) len -= 32768
                val hi = h.getShort(e + 6).toLong() and 0xffff
                val lo = h.getInt(e + 8).toLong() and 0xffffffffL
                if (!uninit) out.add(longArrayOf(lblk, (hi shl 32) or lo, len.toLong()))
            } else {
                val lo = h.getInt(e + 4).toLong() and 0xffffffffL
                val hi = h.getShort(e + 8).toLong() and 0xffff
                walkExtents(readBlock((hi shl 32) or lo), out, level + 1)
            }
        }
    }

    /** Пишет содержимое файла в поток. */
    fun copy(n: Node, out: OutputStream) {
        if (n.flags and 0x10000000 != 0) { // INLINE_DATA
            out.write(n.block, 0, minOf(n.size, 60).toInt())
            return
        }
        val ex = extents(n).sortedBy { it[0] }
        var pos = 0L
        val chunk = ByteArray(1 shl 20)
        val zeros = ByteArray(64 * 1024)
        fun zero(len: Long) {
            var left = len
            while (left > 0) { val k = minOf(left, zeros.size.toLong()).toInt(); out.write(zeros, 0, k); left -= k }
        }
        for (e in ex) {
            val start = e[0] * blockSize
            if (start >= n.size) break
            if (start > pos) { zero(start - pos); pos = start }
            var left = minOf(e[2] * blockSize, n.size - start)
            var phys = e[1] * blockSize
            while (left > 0) {
                val k = minOf(left, chunk.size.toLong()).toInt()
                val bb = ByteBuffer.wrap(chunk, 0, k)
                src.read(phys, bb)
                out.write(chunk, 0, k)
                phys += k; left -= k; pos += k
            }
        }
        if (pos < n.size) zero(n.size - pos)
    }

    fun readAll(n: Node): ByteArray {
        val bo = java.io.ByteArrayOutputStream(n.size.toInt().coerceAtLeast(0))
        copy(n, bo)
        return bo.toByteArray()
    }

    fun linkTarget(n: Node): String {
        // быстрые ссылки (<60 байт) живут прямо в i_block
        return if (n.size < 60 && n.flags and 0x80000 == 0 && n.flags and 0x10000000 == 0) String(n.block, 0, n.size.toInt())
        else String(readAll(n))
    }

    data class Entry(val name: String, val ino: Int, val type: Int)

    fun list(dir: Node): List<Entry> {
        val data = readAll(dir)
        val out = ArrayList<Entry>()
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var off = 0
        val filetype = incompat and 0x2 != 0
        while (off + 8 <= data.size) {
            val ino = bb.getInt(off)
            val recLen = bb.getShort(off + 4).toInt() and 0xffff
            val nameLen = if (filetype) bb.get(off + 6).toInt() and 0xff else bb.getShort(off + 6).toInt() and 0xffff
            val type = if (filetype) bb.get(off + 7).toInt() and 0xff else 0
            if (recLen < 8) break
            if (ino != 0 && nameLen > 0 && off + 8 + nameLen <= data.size) {
                val name = String(data, off + 8, nameLen, Charsets.UTF_8)
                if (name != "." && name != ".." && type != 0xDE) out.add(Entry(name, ino, type))
            }
            off += recLen
        }
        return out
    }

    /** Обход дерева: visitor получает путь относительно корня образа и inode. */
    fun walk(visitor: (path: String, node: Node) -> Unit) {
        val stack = ArrayDeque<Pair<String, Int>>()
        stack.add("" to 2)
        val seen = HashSet<Int>()
        while (stack.isNotEmpty()) {
            val (path, ino) = stack.removeLast()
            val n = inode(ino)
            if (path.isNotEmpty()) visitor(path, n)
            if (n.isDir && seen.add(ino)) {
                for (e in list(n)) {
                    if (path.isEmpty() && e.name == "lost+found") continue
                    stack.add((if (path.isEmpty()) e.name else "$path/${e.name}") to e.ino)
                }
            }
        }
    }

    fun lookup(path: String): Node? {
        var n = inode(2)
        for (part in path.trim('/').split('/').filter { it.isNotEmpty() }) {
            if (!n.isDir) return null
            val e = list(n).firstOrNull { it.name == part } ?: return null
            n = inode(e.ino)
        }
        return n
    }

    companion object {
        fun probe(src: RandomSource): Boolean = runCatching {
            val b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            src.read(1024 + 0x38, b); b.flip()
            b.getShort(0).toInt() and 0xffff == 0xEF53
        }.getOrDefault(false)
    }
}
