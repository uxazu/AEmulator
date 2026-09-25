package app.aemu.core

/**
 * Набор движка: хостовые исполняемые файлы (лежат в nativeLibraryDir как lib*.so)
 * и гостевые файлы (32-битный ARM, копируются в дерево прошивки из assets/engines/<id>).
 *
 * GB — Android 2.x (Gingerbread/Froyo): GL рисуется в pbuffer и читается в fb0, binder и GL-сервер
 *      живут в отдельных процессах-«слотах» приложения.
 * KK — Android 4.x и новее: GL-мост работает прямо в процессе приложения и рисует в Surface.
 */
enum class Engine(
    val id: String,
    val title: String,
    val qemu: String,
    val binderd: String,
    val glserverd: String,
    val runner: String,
    /** гостевая реализация EGL/GLES: имя в assets → имя в /system/lib/egl */
    val guestGles: String,
) {
    GB(
        id = "gb",
        title = "Запасной для 2.x",
        qemu = "libqemu_gb.so",
        binderd = "libbinderd_gb.so",
        glserverd = "libglserverd_gb.so",
        runner = "libdhdrun_gb.so",
        guestGles = "libGLES_dhd.so",
    ),
    KK(
        id = "kk",
        title = "Универсальный (2.3–6.x)",
        qemu = "libqemu_kk.so",
        binderd = "libbinderd_kk.so",
        glserverd = "libglserverd_kk.so",
        runner = "libdhdrun_kk.so",
        guestGles = "libGLES.so",
    );

    companion object {
        /**
         * KK — универсальный: GL-мост в приложении (быстрее) и полный GLES 1/2/3, работает и с 2.3.
         * GB остаётся запасным (выбирается в настройках образа).
         */
        fun forApi(api: Int): Engine = KK
        fun byId(id: String?): Engine = entries.firstOrNull { it.id == id } ?: KK
    }
}
