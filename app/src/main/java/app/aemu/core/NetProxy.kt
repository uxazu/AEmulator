package app.aemu.core

import android.content.Context

/**
 * Сетевые помощники гостя. Сеть у гостя работает напрямую (qemu передаёт сокеты в ядро телефона),
 * а прокси нужны для старых браузеров: HTTP-прокси и TLS-мост с современными шифрами.
 */
class NetProxy(private val ctx: Context, private val paths: VmPaths, private val log: (String) -> Unit) {
    private var tls: TlsBridge? = null

    fun start(): GuestRunner.NetConfig {
        val t = TlsBridge(ctx, paths, log)
        val port = t.start()
        tls = t
        return GuestRunner.NetConfig(https443 = port)
    }

    fun stop() {
        tls?.stop()
        tls = null
    }
}
