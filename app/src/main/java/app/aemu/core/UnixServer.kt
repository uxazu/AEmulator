package app.aemu.core

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor

/**
 * Unix-сокет в файловой системе, который слушает приложение. Гость (через qemu) подключается
 * к нему по гостевому пути — qemu переписывает путь в дерево прошивки.
 * Все дескрипторы помечаются CLOEXEC, иначе они утекают в процессы гостя.
 */
class UnixServer(
    val file: File,
    private val name: String,
    private val onClient: (LocalSocket) -> Unit,
) {
    @Volatile private var server: LocalServerSocket? = null
    @Volatile private var bound: LocalSocket? = null
    @Volatile var clients = 0
        private set

    val running: Boolean get() = server != null && file.exists()

    @Synchronized
    fun start(log: (String) -> Unit): Boolean {
        if (running) return true
        stop()
        file.parentFile?.mkdirs()
        file.delete()
        return try {
            val ls = LocalSocket(LocalSocket.SOCKET_STREAM)
            ls.bind(LocalSocketAddress(file.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
            cloexec(ls.fileDescriptor)
            runCatching { Os.chmod(file.absolutePath, "666".toInt(8)) }
            bound = ls
            val s = LocalServerSocket(ls.fileDescriptor)
            server = s
            Thread({
                while (true) {
                    val c = try { s.accept() } catch (e: Exception) { break }
                    cloexec(c.fileDescriptor)
                    clients++
                    Thread({
                        try { onClient(c) } catch (_: Throwable) {
                        } finally {
                            clients--
                            runCatching { c.close() }
                        }
                    }, "$name-client").apply { isDaemon = true; start() }
                }
            }, "$name-accept").apply { isDaemon = true; start() }
            true
        } catch (e: Throwable) {
            log("$name: сокет не поднялся: $e")
            false
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        runCatching { bound?.close() }
        server = null
        bound = null
        clients = 0
    }

    companion object {
        fun cloexec(fd: FileDescriptor?) {
            if (fd == null) return
            runCatching { Os.fcntlInt(fd, OsConstants.F_SETFD, OsConstants.FD_CLOEXEC) }
        }
    }
}
