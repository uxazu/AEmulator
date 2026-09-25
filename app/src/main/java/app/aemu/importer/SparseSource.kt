package app.aemu.importer

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android sparse-образ (system.img из fastboot/Odin) как источник с произвольным доступом —
 * без распаковки во временный файл: читаем нужные куски по таблице чанков.
 * Поддерживаются и «склеенные» образы (system.img_sparsechunk.* у Motorola) — несколько частей подряд.
 */
class SparseSource(private val parts: List<RandomSource>) : RandomSource {
    private class Chunk(val outStart: Long, val outLen: Long, val type: Int, val part: Int, val srcOff: Long, val fill: Int)

    private val chunks = ArrayList<Chunk>()
    override val size: Long

    init {
        var out = 0L
        var maxOut = 0L
        for ((pi, src) in parts.withIndex()) {
            val h = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
            src.read(0, h); h.flip()
            if (h.getInt(0) != MAGIC) throw IOException("не sparse-образ")
            val fileHdr = h.getShort(8).toInt() and 0xffff
            val chunkHdr = h.getShort(10).toInt() and 0xffff
            val blk = h.getInt(12).toLong()
            val totalBlks = h.getInt(16).toLong() and 0xffffffffL
            val totalChunks = h.getInt(20)
            var off = fileHdr.toLong()
            // каждая часть описывает весь образ целиком: чужие куски у неё DONT_CARE
            out = 0
            for (i in 0 until totalChunks) {
                val c = ByteBuffer.allocate(chunkHdr).order(ByteOrder.LITTLE_ENDIAN)
                src.read(off, c); c.flip()
                val type = c.getShort(0).toInt() and 0xffff
                val sz = (c.getInt(4).toLong() and 0xffffffffL) * blk
                val total = c.getInt(8).toLong() and 0xffffffffL
                val data = off + chunkHdr
                when (type) {
                    RAW -> chunks.add(Chunk(out, sz, RAW, pi, data, 0))
                    FILL -> {
                        val f = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN); src.read(data, f); f.flip()
                        chunks.add(Chunk(out, sz, FILL, pi, 0, f.getInt(0)))
                    }
                    DONT_CARE, CRC -> {}
                    else -> throw IOException("неизвестный чанк 0x${type.toString(16)}")
                }
                out += sz
                off += total
            }
            maxOut = maxOf(maxOut, totalBlks * blk, out)
        }
        chunks.sortBy { it.outStart }
        size = maxOut
    }

    override fun read(pos: Long, dst: ByteBuffer) {
        var p = pos
        while (dst.hasRemaining()) {
            val c = find(p)
            if (c == null) {
                // дыра до следующего чанка
                val next = chunks.firstOrNull { it.outStart > p }?.outStart ?: Long.MAX_VALUE
                val k = minOf(dst.remaining().toLong(), next - p).toInt()
                repeat(k) { dst.put(0) }
                p += k
                if (next == Long.MAX_VALUE) { while (dst.hasRemaining()) dst.put(0); return }
                continue
            }
            val inChunk = p - c.outStart
            val k = minOf(dst.remaining().toLong(), c.outLen - inChunk).toInt()
            if (c.type == RAW) {
                val lim = dst.limit()
                dst.limit(dst.position() + k)
                parts[c.part].read(c.srcOff + inChunk, dst)
                dst.limit(lim)
            } else {
                val fb = byteArrayOf((c.fill).toByte(), (c.fill shr 8).toByte(), (c.fill shr 16).toByte(), (c.fill shr 24).toByte())
                for (i in 0 until k) dst.put(fb[((inChunk + i) and 3).toInt()])
            }
            p += k
        }
    }

    private fun find(p: Long): Chunk? {
        var lo = 0; var hi = chunks.size - 1
        var best: Chunk? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = chunks[mid]
            if (c.outStart <= p) { best = c; lo = mid + 1 } else hi = mid - 1
        }
        return best?.takeIf { p < it.outStart + it.outLen }
    }

    companion object {
        const val MAGIC = 0xED26FF3A.toInt()
        const val RAW = 0xCAC1
        const val FILL = 0xCAC2
        const val DONT_CARE = 0xCAC3
        const val CRC = 0xCAC4

        fun probe(src: RandomSource): Boolean = runCatching {
            val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN); src.read(0, b); b.flip(); b.getInt(0) == MAGIC
        }.getOrDefault(false)
    }
}
