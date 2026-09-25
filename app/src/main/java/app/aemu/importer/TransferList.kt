package app.aemu.importer

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Сборка system.img из блочной OTA (system.new.dat + system.transfer.list, Android 5.0+).
 * В полных OTA встречаются только команды new/zero/erase — их и поддерживаем.
 * Результат — разреженный файл (дыры не занимают место на диске).
 */
object TransferList {
    private const val BLOCK = 4096

    fun build(list: String, data: InputStream, out: File) {
        val lines = list.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val version = lines[0].toIntOrNull() ?: throw IOException("transfer.list: нет версии")
        val totalBlocks = lines[1].toLong()
        val cmdStart = if (version >= 2) 4 else 2
        RandomAccessFile(out, "rw").use { raf ->
            raf.setLength(totalBlocks * BLOCK)
            val buf = ByteArray(BLOCK * 256)
            for (ln in lines.drop(cmdStart)) {
                val parts = ln.split(' ')
                when (parts[0]) {
                    "new" -> for ((a, b) in ranges(parts[1])) {
                        var blk = a
                        while (blk < b) {
                            val n = minOf(256L, b - blk).toInt()
                            readFully(data, buf, n * BLOCK)
                            raf.seek(blk * BLOCK)
                            raf.write(buf, 0, n * BLOCK)
                            blk += n
                        }
                    }
                    "zero", "erase" -> {} // дыры и так нулевые
                    "move", "bsdiff", "imgdiff", "stash", "free" -> throw IOException("это инкрементальная OTA — нужна полная прошивка")
                }
            }
        }
    }

    private fun ranges(s: String): List<Pair<Long, Long>> {
        val v = s.split(',').map { it.toLong() }
        val out = ArrayList<Pair<Long, Long>>()
        var i = 1
        while (i + 1 < v.size) { out.add(v[i] to v[i + 1]); i += 2 }
        return out
    }

    private fun readFully(i: InputStream, b: ByteArray, n: Int) {
        var off = 0
        while (off < n) {
            val k = i.read(b, off, n - off)
            if (k <= 0) { b.fill(0, off, n); return }
            off += k
        }
    }
}
