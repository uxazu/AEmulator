package app.aemu.core

import android.content.Context

/**
 * TLS-мост для старых браузеров (TLS 1.0 без SNI/современных шифров): qemu перенаправляет
 * соединения гостя на :443 сюда. Пока выключен — гость ходит в сеть напрямую.
 */
class TlsBridge(private val ctx: Context, private val paths: VmPaths, private val log: (String) -> Unit) {
    fun start(): Int = 0
    fun stop() {}
}
