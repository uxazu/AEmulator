package app.aemu.core

import android.net.LocalSocket
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.DataInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections

/**
 * Заглушка rild: телефония подключается к /dev/socket/rild, получает «радио выключено»
 * и на каждый запрос — RADIO_NOT_AVAILABLE. Этого хватает, чтобы phone-процесс не падал.
 */
class RilStub(paths: VmPaths, private val log: (String) -> Unit) {
    @Volatile var answered = 0L
        private set

    private val server = UnixServer(paths.socket("rild"), "rild") { c -> serveOne(c) }

    fun serve() = server.start(log)
    fun stop() = server.stop()

    private fun serveOne(c: LocalSocket) {
        val out = c.outputStream
        frame(out, ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(UNSOL_RADIO_STATE_CHANGED).putInt(0).array())
        log("радио: телефония подключилась, радио «выключено»")
        val din = DataInputStream(c.inputStream)
        while (true) {
            val len = try { din.readInt() } catch (e: Exception) { break }
            if (len <= 0 || len > 1 shl 20) break
            val body = ByteArray(len)
            din.readFully(body)
            if (len >= 8) {
                val b = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
                val request = b.getInt()
                val serial = b.getInt()
                // RADIO_POWER подтверждаем: на отказ телефония отвечает новым запросом без паузы
                // и забивает журнал radio десятками мегабайт; радио при этом остаётся «выключенным».
                // Идентификаторы устройства отдаём всегда: MIUI (поиск устройства, облако) и часть
                // приложений ждут IMEI бесконечно и зависают, пока его нет
                val data: List<String>? = when (request) {
                    RIL_REQUEST_GET_IMEI -> listOf(IMEI)
                    RIL_REQUEST_GET_IMEISV -> listOf(IMEISV)
                    RIL_REQUEST_BASEBAND_VERSION -> listOf(BASEBAND)
                    RIL_REQUEST_DEVICE_IDENTITY -> listOf(IMEI, IMEISV, "", "")
                    else -> null
                }
                val err = if (request == RIL_REQUEST_RADIO_POWER || data != null) 0 else E_RADIO_NOT_AVAILABLE
                val resp = ByteArrayOutputStream()
                resp.write(le(0)); resp.write(le(serial)); resp.write(le(err))
                if (data != null) {
                    if (request == RIL_REQUEST_DEVICE_IDENTITY) resp.write(le(data.size))
                    data.forEach { resp.write(string16(it)) }
                }
                frame(out, resp.toByteArray())
                answered++
            }
        }
    }

    @Synchronized
    private fun frame(out: OutputStream, payload: ByteArray) {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array())
        out.write(payload)
        out.flush()
    }

    private fun le(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** строка в формате Parcel.writeString: длина, UTF-16 с нулём, выравнивание до 4 байт */
    private fun string16(s: String): ByteArray {
        val chars = (s.length + 1) * 2
        val b = ByteBuffer.allocate(4 + (chars + 3) / 4 * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(s.length)
        s.forEach { b.putChar(it) }
        return b.array()
    }

    companion object {
        private const val UNSOL_RADIO_STATE_CHANGED = 1000
        private const val E_RADIO_NOT_AVAILABLE = 1
        private const val RIL_REQUEST_RADIO_POWER = 23
        private const val RIL_REQUEST_GET_IMEI = 38
        private const val RIL_REQUEST_GET_IMEISV = 39
        private const val RIL_REQUEST_BASEBAND_VERSION = 51
        private const val RIL_REQUEST_DEVICE_IDENTITY = 98
        /** правильный по Луну IMEI-образец из стандарта; настоящего модема у гостя нет */
        private const val IMEI = "490154203237518"
        private const val IMEISV = "01"
        private const val BASEBAND = "AEmulator"
    }
}

/**
 * Заглушка vold: MountService видит одну «вставленную» карту по пути из прошивки.
 * Сама карта — каталог на хосте, qemu подменяет гостевой путь (DHD_SDCARD).
 */
class VoldStub(
    paths: VmPaths,
    private val mountPoint: String,
    private val log: (String) -> Unit,
    sockName: String = "vold",
    /** остальные тома прошивки (съёмные карты, USB): сообщаем о них как о «нет носителя» */
    private val others: List<String> = emptyList(),
) {
    @Volatile var state = MOUNTED
    private val label = mountPoint.substringAfterLast('/').ifEmpty { "sdcard" }
    private val clients = Collections.synchronizedList(ArrayList<LocalSocket>())

    private val server = UnixServer(paths.socket(sockName), sockName) { c -> serveOne(c) }

    fun serve() = server.start(log)
    fun stop() { server.stop(); clients.clear() }

    private fun serveOne(c: LocalSocket) {
        clients.add(c)
        try {
            val out = c.outputStream
            val ins = c.inputStream
            val buf = ByteArray(4096)
            val acc = StringBuilder()
            while (true) {
                val n = try { ins.read(buf) } catch (e: Exception) { -1 }
                if (n <= 0) break
                for (i in 0 until n) {
                    val b = buf[i].toInt() and 0xff
                    if (b == 0) {
                        val cmd = acc.toString(); acc.setLength(0)
                        if (cmd.isNotEmpty()) answer(cmd, out)
                    } else acc.append(b.toChar())
                }
            }
        } finally {
            clients.remove(c)
        }
    }

    private fun answer(cmd: String, out: OutputStream) {
        val parts = cmd.trim().split(Regex("\\s+"))
        val seq = parts.getOrNull(0)?.toIntOrNull()
        val args = if (seq != null) parts.drop(1) else parts
        val head = args.getOrElse(0) { "" }
        val sub = args.getOrElse(1) { "" }
        var after: (() -> Unit)? = null
        val reply: List<String> = when {
            // с томами из storage_list (4.x) сообщаем только о них: основное хранилище там эмулируемое,
            // а незнакомый путь MountService Samsung считает ошибкой и обрывает список
            head == "volume" && sub == "list" -> (if (others.isEmpty()) listOf("110 $label $mountPoint $state")
                else others.mapIndexed { i, v -> "110 ${v.substringAfterLast('/')} $v ${if (i == 0 && v == mountPoint) state else 0}" }) +
                "200 Volumes listed."
            head == "volume" && sub == "mount" -> { state = MOUNTED; after = { announce(IDLE, MOUNTED) }; listOf("200 Volume mounted.") }
            head == "volume" && sub == "unmount" -> { state = IDLE; after = { announce(MOUNTED, IDLE) }; listOf("200 Volume unmounted.") }
            head == "volume" && sub == "format" -> listOf("200 Volume formatted.")
            head == "share" && sub == "status" -> listOf("210 ${args.getOrElse(2) { "ums" }} unavailable")
            head == "share" || head == "unshare" -> listOf("200 Share operation succeeded.")
            head == "storage" && sub == "users" -> listOf("200 Storage user list.")
            head == "asec" && sub == "list" -> listOf("200 Asec listed.")
            head == "asec" && sub == "path" -> listOf("211 /mnt/asec/${args.getOrElse(2) { "" }}")
            head == "obb" && sub == "list" -> listOf("200 Obb listed.")
            head == "cryptfs" -> listOf("200 0 0")
            else -> listOf("200 ok")
        }
        send(out, reply.map { withSeq(it, seq) })
        after?.invoke()
    }

    private fun withSeq(line: String, seq: Int?): String {
        if (seq == null) return line
        val sp = line.indexOf(' ')
        if (sp <= 0) return line
        val code = line.substring(0, sp).toIntOrNull() ?: return line
        if (code in 600..699) return line
        return "$code $seq${line.substring(sp)}"
    }

    fun announce(from: Int, to: Int) {
        val msg = "605 Volume $label $mountPoint state changed from $from (${name(from)}) to $to (${name(to)})"
        val list = synchronized(clients) { ArrayList(clients) }
        for (c in list) runCatching { send(c.outputStream, listOf(msg)) }
    }

    fun rescan() {
        state = MOUNTED
        announce(IDLE, MOUNTED)
    }

    private fun name(s: Int) = when (s) {
        0 -> "No-Media"; 1 -> "Idle-Unmounted"; 2 -> "Pending"; 3 -> "Checking"; 4 -> "Mounted"
        5 -> "Unmounting"; 6 -> "Formatting"; 7 -> "Shared-Unmounted"; else -> "Unknown"
    }

    @Synchronized
    private fun send(out: OutputStream, msgs: List<String>) {
        val b = ByteArrayOutputStream()
        for (s in msgs) { b.write(s.toByteArray()); b.write(0) }
        out.write(b.toByteArray())
        out.flush()
    }

    companion object {
        const val IDLE = 1
        const val MOUNTED = 4
    }
}

/** «Звонок кадра»: GL-сервер сообщает о новом кадре — будим отрисовку. */
class FrameBell(paths: VmPaths, private val log: (String) -> Unit, private val onFrame: () -> Unit) {
    @Volatile var rings = 0L
        private set
    private val server = UnixServer(paths.frameSock, "frame") { c ->
        val ins = c.inputStream
        val b = ByteArray(64)
        while (true) {
            val n = try { ins.read(b) } catch (e: Exception) { -1 }
            if (n <= 0) break
            rings += n
            onFrame()
        }
    }
    fun serve() = server.start(log)
    fun stop() = server.stop()
}

/**
 * /dev/log/events — канал (FIFO), который хост держит открытым и вычитывает.
 * Обычным файлом его делать нельзя: часть служб (MIUI Whetstone и др.) читает журнал событий как
 * устройство logger — на обычном файле чтение сразу упирается в конец, и служба крутит цикл
 * «read error», забивая процессор и журнал (миллионы строк за минуту). Из канала читатель ждёт данных.
 * Сами события эмулятору не нужны — выбрасываем.
 */
class EventsSink(paths: VmPaths, private val log: (String) -> Unit) {
    private val fifo = File(paths.root, "dev/log/events")
    @Volatile private var raf: java.io.RandomAccessFile? = null

    fun start() {
        runCatching {
            val mode = runCatching { android.system.Os.stat(fifo.absolutePath).st_mode and android.system.OsConstants.S_IFMT }.getOrDefault(0)
            if (mode != android.system.OsConstants.S_IFIFO) {
                fifo.parentFile?.mkdirs(); fifo.delete()
                android.system.Os.mkfifo(fifo.absolutePath, "666".toInt(8))
            }
            // O_RDWR: открытие не ждёт второй стороны, а писатели гостя никогда не упираются в «нет читателя»
            val r = java.io.RandomAccessFile(fifo, "rw").also { raf = it }
            Thread({
                val buf = ByteArray(1 shl 16)
                while (true) { if (runCatching { r.read(buf) }.getOrDefault(-1) < 0) break }
            }, "aemu-events").apply { isDaemon = true; start() }
        }.onFailure { log("журнал событий: канал не поднялся: ${it.message}") }
    }

    fun stop() { runCatching { raf?.close() }; raf = null }
}
