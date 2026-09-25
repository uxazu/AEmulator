package app.aemu.core

/** Сведения о телефоне-хозяине, от которых зависят обходы в движке. */
object HostInfo {
    /** Драйвер GPU (adreno, mali, powervr…) из ro.hardware.egl. */
    fun gpu(): String = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, "ro.hardware.egl") as String).lowercase()
    }.getOrDefault("")
}
