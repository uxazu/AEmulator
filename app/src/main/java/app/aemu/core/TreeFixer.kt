package app.aemu.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import android.system.OsConstants
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Подгонка дерева прошивки под запуск без ядра и железа.
 *  - [sanitize] выполняется один раз после импорта: убирает вендорские драйверы, которые полезли бы
 *    в несуществующее железо (GPU, композитор, OMX, датчики, камера…). Файлы не удаляются —
 *    переносятся в /system/.aemu-parked, чтобы можно было вернуть.
 *  - [fixup] выполняется перед каждым запуском: кладёт файлы движка, создаёт узлы /dev и /sys,
 *    каталоги, которые создаёт init, восстанавливает владельцев и т.п.
 */
class TreeFixer(
    private val ctx: Context,
    private val paths: VmPaths,
    private val img: GuestImage,
    private val log: (String) -> Unit,
) {
    private val root get() = paths.root
    private val engine get() = img.engine

    // ---------------------------------------------------------------- однократно после импорта

    fun sanitize() {
        val parked = File(root, "system/.aemu-parked").apply { mkdirs() }
        var n = 0
        fun park(f: File) {
            if (!f.exists() && !isLink(f)) return
            val rel = f.relativeTo(root).path.replace('/', '#')
            val dst = File(parked, rel)
            dst.delete()
            if (f.renameTo(dst)) n++
        }
        // 1. HAL-модули: оставляем только нейтральные *.default.so и безвредные классы
        val keepClasses = setOf("audio_policy", "local_time", "power", "keystore", "consumerir", "vibrator", "memtrack")
        val parkClasses = setOf(
            "gralloc", "hwcomposer", "copybit", "overlay", "sensors", "camera", "gps", "nfc", "nfc_nci",
            "lights", "audio", "fm", "bluetooth", "hdmi", "tv", "display", "ir", "wlan", "gpu", "fingerprint",
        )
        for (dir in listOf("system/lib/hw", "vendor/lib/hw", "system/vendor/lib/hw")) {
            File(root, dir).listFiles()?.forEach { f ->
                val name = f.name
                if (!name.endsWith(".so")) return@forEach
                val cls = name.substringBefore('.')
                val variant = name.removeSuffix(".so").substringAfter('.', "")
                val isDefault = variant == "default" || variant == "goldfish"
                when {
                    // родной gralloc.default прошивки работает с fb0, который эмулирует qemu, — оставляем
                    cls == "gralloc" && isDefault -> {}
                    // звук даёт движок
                    cls == "gralloc" || (cls == "audio" && variant == "primary.default") -> park(f)
                    cls == "audio" && (variant.startsWith("a2dp") || variant.startsWith("usb") || variant.startsWith("r_submix")) -> {}
                    cls in keepClasses && isDefault -> {}
                    cls == "audio_policy" -> if (!isDefault) park(f)
                    cls in parkClasses -> park(f)
                    !isDefault && cls !in keepClasses -> park(f)
                    !isDefault -> park(f)
                }
            }
        }
        // 2. вендорские драйверы OpenGL: их место займёт GL-мост
        for (dir in listOf("system/lib/egl", "vendor/lib/egl", "system/vendor/lib/egl")) {
            File(root, dir).listFiles()?.forEach { f ->
                val nm = f.name
                val vendorGl = (nm.startsWith("libEGL_") || nm.startsWith("libGLESv1_CM_") || nm.startsWith("libGLESv2_") ||
                    nm.startsWith("libGLESv3_") || (nm.startsWith("libGLES_") && nm != "libGLES_android.so") ||
                    nm.startsWith("libq3dtools") || nm.startsWith("eglsubAndroid") || nm.startsWith("libRBEGL") || nm.startsWith("libRBGLES"))
                if (vendorGl) park(f)
            }
        }
        // 3. аппаратные кодеки OMX переполняют стек OMXMaster и роняют mediaserver
        for (rel in listOf("system/lib/libstagefrighthw.so", "vendor/lib/libstagefrighthw.so", "system/vendor/lib/libstagefrighthw.so")) park(File(root, rel))
        // 4. заслонки ядра: /dev/binder как каталог, группы планировщика
        File(root, "dev/binder").takeIf { it.isDirectory }?.let { wipe(it) }
        File(root, "dev/cpuctl").takeIf { it.exists() }?.let { wipe(it) }
        log("подготовка прошивки: убрано в сторону вендорских модулей: $n")
    }

    // ---------------------------------------------------------------- перед каждым запуском

    fun fixup(owners: Boolean = true) {
        if (owners) seedOwners()
        installEngineFiles()
        eglConfig(img.settings.gpu)
        makeDataDirs()
        makeUserZeroLink()
        makeDevNodes()
        makeSysNodes()
        makeProcMounts()
        audioPolicy()
        qtaguid()
        vendorChecks()
        for (n in LOGS) File(root, "dev/log/$n").let { if (!it.isFile) { it.parentFile?.mkdirs(); it.createNewFile() } }
        makeFb()
    }

    private fun installEngineFiles() {
        val copies = when (engine) {
            Engine.KK -> listOf(
                "libashmemshim.so" to "system/lib/libashmemshim.so",
                "libGLES.so" to "system/lib/egl/libGLES_bridge.so",
                "audio.primary.default.so" to "system/lib/hw/audio.primary.default.so",
                "gralloc.default.so" to "system/lib/hw/gralloc.default.so",
                "fbpaint" to "system/bin/fbpaint",
                "netprobe" to "system/bin/netprobe",
                "aemu-stubs.jar" to "system/framework/aemu-stubs.jar",
            )
            Engine.GB -> listOf(
                "libashmemshim.so" to "system/lib/libashmemshim.so",
                "libGLES_dhd.so" to "system/lib/egl/libGLES_dhd.so",
                "gralloc.default.so" to "system/lib/hw/gralloc.default.so",
                "fbpaint" to "system/bin/fbpaint",
            )
        }
        // образы, подготовленные старой версией: вернуть родной gralloc.default из «парковки»
        val parkedGralloc = File(root, "system/.aemu-parked/system#lib#hw#gralloc.default.so")
        if (parkedGralloc.isFile) {
            val g = File(root, "system/lib/hw/gralloc.default.so")
            g.delete(); parkedGralloc.renameTo(g)
        }
        // gralloc.default, который выделяет память через ION (MediaTek и др.), без ядра не работает:
        // устройство выделения получается пустым и SurfaceFlinger падает — ставим gralloc движка
        val romGralloc = File(root, "system/lib/hw/gralloc.default.so")
        if (engine == Engine.KK && romGralloc.isFile && romGralloc.length() < 2_000_000) {
            val text = runCatching { String(romGralloc.readBytes(), Charsets.ISO_8859_1) }.getOrDefault("")
            if (text.contains("libion.so") || text.contains("ion_alloc")) {
                val parked = File(root, "system/.aemu-parked/system#lib#hw#gralloc.default.so.ion")
                parked.parentFile?.mkdirs()
                if (romGralloc.renameTo(parked)) log("gralloc прошивки работает через ION — заменён на gralloc движка")
            }
        }
        var copied = 0
        for ((from, to) in copies + listOf("@libaemushim.so" to "system/lib/libaemushim.so")) {
            val dst = File(root, to)
            // gralloc движка — только если в прошивке своего нет
            if (from == "gralloc.default.so" && dst.isFile) continue
            // «@» — общий для всех движков файл
            // звуковой HAL стенда разложен под AudioFlinger HTC; остальным 4.2+ — вариант с раскладкой AOSP.
            // У Samsung свой audio_stream_out (лишние слоты) — с ним AOSP-вариант роняет mediaserver,
            // поэтому там остаётся исходный: выход не открывается, система работает без звука.
            val htcLike = img.skin.contains("HTC", true) || img.skin.contains("TouchWiz", true) || img.skin.contains("Samsung", true)
            val name = if (from == "audio.primary.default.so" && img.api >= 17 && !htcLike)
                "audio.primary.aosp.so" else from
            val asset = if (name.startsWith("@")) "engines/common/${name.drop(1)}" else "engines/${engine.id}/$name"
            val size = runCatching { ctx.assets.openFd(asset).use { it.length } }.getOrDefault(-1L)
            // одинаковый размер ещё не значит тот же файл (правки движка на месте, варианты HAL) — сверяем CRC
            if (dst.isFile && size >= 0 && dst.length() == size && sameContent(asset, dst)) continue
            runCatching {
                dst.parentFile?.mkdirs()
                if (isLink(dst)) Os.remove(dst.absolutePath)
                ctx.assets.open(asset).use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
                dst.setReadable(true, false)
                dst.setExecutable(true, false)
                copied++
            }.onFailure { log("нет ${from.removePrefix("@")} в наборе движка: ${it.message}") }
        }
        // вендорские драйверы RenderScript (Adreno, Mali…) лезут в GPU; без них libRS берёт процессорный
        for (dir in listOf("system/lib", "vendor/lib", "system/vendor/lib")) {
            File(root, dir).listFiles()?.filter { it.name.startsWith("libRSDriver_") }?.forEach { f ->
                val parked = File(root, "system/.aemu-parked/${f.relativeTo(root).path.replace('/', '#')}")
                parked.parentFile?.mkdirs()
                if (f.renameTo(parked)) log("RenderScript: убран вендорский драйвер ${f.name}")
            }
        }
        // netfilter в эмуляторе нет: iptables всегда падает, а netd 4.x (Samsung) считает это фатальным
        // для NetworkManagementService/ConnectivityService — ставим пустую программу, родную прячем
        val trueSize = runCatching { ctx.assets.openFd("engines/common/aemu_true.so").use { it.length } }.getOrDefault(-1L)
        for (n in listOf("iptables", "ip6tables")) {
            val f = File(root, "system/bin/$n")
            if (!f.exists() && !isLink(f) || trueSize < 0 || (!isLink(f) && f.length() == trueSize)) continue
            runCatching {
                val parked = File(root, "system/.aemu-parked/system#bin#$n")
                parked.parentFile?.mkdirs()
                if (isLink(f)) Os.remove(f.absolutePath) else if (!parked.exists()) f.renameTo(parked) else f.delete()
                ctx.assets.open("engines/common/aemu_true.so").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                f.setReadable(true, false); f.setExecutable(true, false)
                copied++
            }.onFailure { log("$n не заменился: ${it.message}") }
        }
        // камера 2.3: заглушка вместо вендорской libcamera.so, которая лезет в /dev/msm_camera
        val cam = File(root, "system/lib/libcamera.so")
        val stamp = File(root, "system/lib/libcamera.so.aemu")
        val parkedCam = File(root, "system/.aemu-parked/system#lib#libcamera.so")
        val htc = img.skin.contains("HTC", true)
        // заглушка собрана под интерфейс камеры HTC — у других производителей оставляем родную
        if (!htc && stamp.isFile && parkedCam.isFile) { cam.delete(); parkedCam.renameTo(cam); stamp.delete() }
        if (engine == Engine.GB && htc) {
            if (cam.isFile && !stamp.isFile) {
                runCatching {
                    File(root, "system/.aemu-parked").mkdirs()
                    cam.copyTo(File(root, "system/.aemu-parked/system#lib#libcamera.so"), overwrite = true)
                    ctx.assets.open("engines/gb/libcamera.so").use { i -> cam.outputStream().use { o -> i.copyTo(o) } }
                    stamp.writeText("1")
                }
            }
        }
        if (copied > 0) log("файлы движка ${engine.id}: обновлено $copied")
    }

    /** Направляет загрузчик EGL прошивки на GL-мост (или на программный растеризатор). */
    private fun eglConfig(gpu: Boolean) {
        val dir = File(root, "system/lib/egl").apply { mkdirs() }
        val cfg = File(dir, "egl.cfg")
        val cfgRom = File(dir, "egl.cfg.rom")
        if (!cfgRom.isFile && cfg.isFile) cfg.copyTo(cfgRom, overwrite = true)
        when (engine) {
            Engine.GB -> cfg.writeText(if (gpu) "0 0 dhd\n" else "0 0 android\n")
            Engine.KK -> {
                // сам мост лежит как libGLES_bridge.so; все имена, которые ищут загрузчики EGL (libGLES.so у 4.4,
                // libGLES_<тег>.so по egl.cfg, раздельные libEGL_/libGLESv*_), занимает переходник libGLES_split:
                // он чинит загрузку текстур, сводит раздельные библиотеки в один мост и даёт SurfaceFlinger ES2
                val bridge = File(dir, "libGLES_bridge.so")
                val sw = File(dir, "libGLES_android.so")
                val swOff = File(dir, "libGLES_android.so.sw")
                val names = listOf("libGLES.so", "libGLES_aemu.so", "libGLES_android.so", "libEGL_aemu.so", "libGLESv1_CM_aemu.so", "libGLESv2_aemu.so")
                if (gpu && bridge.isFile) {
                    if (!swOff.isFile && sw.isFile && sw.length() != splitSize()) sw.renameTo(swOff)
                    runCatching {
                        for (n in names) {
                            val f = File(dir, n)
                            if (f.isFile && f.length() == splitSize()) continue
                            ctx.assets.open("engines/kk/libGLES_split.so").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                            f.setReadable(true, false); f.setExecutable(true, false)
                        }
                    }.onFailure { log("GL: переходник не разложился: ${it.message}") }
                    // часть загрузчиков (Samsung 4.3) берут последнюю строку, остальные — строку с impl=1
                    cfg.writeText("0 0 android\n0 1 aemu\n")
                } else {
                    // программная отрисовка: убираем все наши имена, иначе загрузчик 4.4 всё равно найдёт libGLES.so
                    for (n in names) if (n != "libGLES_android.so") File(dir, n).delete()
                    if (swOff.isFile) { sw.delete(); swOff.renameTo(sw) }
                    cfg.writeText("0 0 android\n")
                }
            }
        }
    }

    private fun sameContent(asset: String, f: File): Boolean = runCatching {
        fun crc(i: java.io.InputStream): Long {
            val c = java.util.zip.CRC32(); val b = ByteArray(1 shl 16)
            while (true) { val n = i.read(b); if (n < 0) break; c.update(b, 0, n) }
            return c.value
        }
        ctx.assets.open(asset).use { crc(it) } == f.inputStream().use { crc(it) }
    }.getOrDefault(false)

    /** Пустые таблицы xt_qtaguid, на которые гостевая прослойка подменяет /proc/net/xt_qtaguid/… */
    /**
     * MediaTek DRVB: кодеки, DRM, камера и даже debuggerd сверяют «платформу» через демон drvbd, а тот
     * читает efuse через /dev/devmap. На эмуляторе проверка проваливается, каждый клиент 10 с ждёт демон,
     * а модуль DRM затем нарочно прыгает на 0xddeeaadd (drmserver падает, MediaPlayer зависает намертво).
     * Проверка отвечает на вызов подписанным ответом, который считает сама библиотека, — поэтому достаточно,
     * чтобы её внутренние проверки вернули «успех»: ответ получится честный.
     */
    private fun vendorChecks() = runCatching {
        val drvb = File(root, "system/lib/libmtk_drvb.so")
        if (drvb.isFile) {
            val n = ElfPatch.returnZero(drvb, setOf("mtk_drvb_basechk", "platform_init", "platform_advchk", "drvb_ext_input"))
            if (n > 0) log("MediaTek DRVB: проверка платформы отключена ($n функц.)")
        }
    }.onFailure { log("MediaTek DRVB: не вышло — ${it.message}") }

    private fun qtaguid() = runCatching {
        val d = File(root, "data/.aemu_qtaguid").apply { mkdirs() }
        d.setReadable(true, false); d.setExecutable(true, false)
        val files = mapOf(
            "stats" to "idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets " +
                "rx_tcp_bytes rx_tcp_packets rx_udp_bytes rx_udp_packets rx_other_bytes rx_other_packets " +
                "tx_tcp_bytes tx_tcp_packets tx_udp_bytes tx_udp_packets tx_other_bytes tx_other_packets\n",
            "iface_stat_fmt" to "ifname total_skb_rx_bytes total_skb_rx_packets total_skb_tx_bytes total_skb_tx_packets\n",
            "iface_stat_all" to "",
        )
        for ((n, text) in files) File(d, n).apply { writeText(text); setReadable(true, false) }
    }

    private fun splitSize(): Long = runCatching { ctx.assets.openFd("engines/kk/libGLES_split.so").use { it.length } }.getOrDefault(-1L)

    private fun makeDataDirs() {
        var made = 0
        val want = (DATA_DIRS + img.dirs.map { it.trim().removePrefix("/") }).filter { it.isNotEmpty() && !it.startsWith("#") }
        for (d in want.distinct()) {
            // каталоги init в /dev, /sys, /proc, /acct не создаём — это подменяет qemu
            if (d.startsWith("proc") || d.startsWith("sys/") || d == "sys" || d.startsWith("acct")) continue
            val f = File(root, d)
            if (!f.isDirectory && f.mkdirs()) made++
        }
        if (made > 0) log("созданы каталоги, которые делает init: $made")
    }

    private fun makeUserZeroLink() {
        if (img.api < 17) return
        val user = File(root, "data/user").apply { mkdirs() }
        val zero = File(user, "0")
        val cur = runCatching { Os.readlink(zero.absolutePath) }.getOrNull()
        if (cur == "../data") return
        runCatching {
            if (cur != null || zero.exists()) { if (zero.isDirectory && !isLink(zero)) wipe(zero) else zero.delete() }
            Os.symlink("../data", zero.absolutePath)
        }
    }

    private fun makeDevNodes() {
        File(root, "dev/input/event0").let { if (!it.isFile) { it.parentFile?.mkdirs(); it.createNewFile() } }
        File(root, "dev/socket").mkdirs()
        File(root, "dev/graphics").mkdirs()
        AudioOut(paths, log).makeFifo()
        File(root, "dev/binder").takeIf { it.isDirectory }?.let { wipe(it) }
        File(root, "dev/cpuctl").takeIf { it.exists() }?.let { wipe(it) }
    }

    private fun makeSysNodes() {
        val freq = File(root, "sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state")
        if (!freq.isFile) {
            freq.parentFile?.mkdirs()
            freq.writeText(listOf(384000, 594000, 810000, 1026000, 1242000, 1512000).joinToString("\n") { "$it 0" } + "\n")
        }
        val cpus = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val cpu = File(root, "sys/devices/system/cpu").apply { mkdirs() }
        for ((n, v) in listOf("present" to "0-${cpus - 1}", "possible" to "0-${cpus - 1}", "online" to "0-${cpus - 1}", "offline" to "")) {
            File(cpu, n).let { if (!it.isFile) it.writeText(v + "\n") }
        }
        File(root, "sys/class/net/lo").mkdirs()
        val base = File(root, "sys/class/power_supply")
        val bat = mapOf(
            "battery/type" to "Battery", "battery/status" to "Full", "battery/health" to "Good",
            "battery/present" to "1", "battery/capacity" to "100", "battery/batt_vol" to "4200",
            "battery/voltage_now" to "4200000", "battery/batt_temp" to "250", "battery/temp" to "250",
            "battery/technology" to "Li-ion", "ac/type" to "Mains", "ac/online" to "1",
            "usb/type" to "USB", "usb/online" to "0",
        )
        for ((rel, v) in bat) File(base, rel).let { if (!it.isFile) { it.parentFile?.mkdirs(); it.writeText(v + "\n") } }
        val power = File(root, "sys/power").apply { mkdirs() }
        for (n in listOf("state", "wake_lock", "wake_unlock", "autosleep")) File(power, n).let { if (!it.isFile) it.createNewFile(); it.setWritable(true, false) }
        // узлы питания, которые открывает libhardware_legacy именно этой прошивки (Samsung: dvfslock_ctrl…):
        // если хоть один не откроется, библиотека считает экран выключенным и система не принимает касания
        runCatching {
            val lib = File(root, "system/lib/libhardware_legacy.so")
            if (lib.isFile) {
                val text = String(lib.readBytes(), Charsets.ISO_8859_1)
                for (m in Regex("/sys/(power|android_power)/[a-z_0-9]+").findAll(text)) {
                    val rel = m.value.removePrefix("/")
                    if (rel.contains("wait_for_fb")) continue // блокирующее чтение — обычный файл вызвал бы холостой цикл
                    File(root, rel).let { f -> if (!f.exists()) { f.parentFile?.mkdirs(); f.createNewFile(); f.setWritable(true, false) } }
                }
            }
        }
        // подсветка экрана: LightsService пишет сюда яркость
        val bl = File(root, "sys/class/leds/lcd-backlight").apply { mkdirs() }
        File(bl, "brightness").let { if (!it.isFile) it.writeText("255\n") }
        File(bl, "max_brightness").let { if (!it.isFile) it.writeText("255\n") }
    }

    /**
     * Своя таблица монтирования: прошивки (Samsung /efs, /preload…) читают /proc/mounts и, не найдя
     * раздел, пытаются смонтировать его сами — а seccomp телефона убивает за mount(). qemu отдаёт
     * гостю файлы из дерева поверх хостовых, поэтому root/proc/mounts видится как настоящий.
     */
    private fun makeProcMounts() {
        val mounts = LinkedHashMap<String, String>() // точка → строка
        fun add(dev: String, point: String, type: String, opts: String) { mounts.putIfAbsent(point, "$dev $point $type $opts 0 0") }
        add("rootfs", "/", "rootfs", "ro,relatime")
        add("tmpfs", "/dev", "tmpfs", "rw,nosuid,relatime,mode=755")
        add("devpts", "/dev/pts", "devpts", "rw,relatime,mode=600")
        add("proc", "/proc", "proc", "rw,relatime")
        add("sysfs", "/sys", "sysfs", "rw,relatime")
        add("none", "/acct", "cgroup", "rw,relatime,cpuacct")
        add("tmpfs", "/mnt/asec", "tmpfs", "rw,relatime,mode=755,gid=1000")
        add("tmpfs", "/mnt/obb", "tmpfs", "rw,relatime,mode=755,gid=1000")
        add("/dev/block/platform/aemu/by-name/system", "/system", "ext4", "ro,relatime")
        add("/dev/block/platform/aemu/by-name/userdata", "/data", "ext4", "rw,nosuid,nodev,noatime")
        add("/dev/block/platform/aemu/by-name/cache", "/cache", "ext4", "rw,nosuid,nodev,noatime")
        // точки монтирования из init*.rc и fstab прошивки
        val rcs = (root.listFiles()?.filter { it.isFile && (it.name.endsWith(".rc") || it.name.startsWith("fstab")) } ?: emptyList()) +
            listOfNotNull(File(root, "system/etc/vold.fstab").takeIf { it.isFile })
        for (f in rcs) runCatching {
            for (raw in f.readLines()) {
                val t = raw.trim().split(Regex("\\s+"))
                if (t.isEmpty() || t[0].startsWith("#")) continue
                when {
                    t[0] == "mount" && t.size >= 4 -> {
                        val point = t[3]
                        if (point.startsWith("/") && point != "/" && !point.startsWith("/proc") && !point.startsWith("/sys") && !point.startsWith("/dev"))
                            add(t[2].substringBefore('@').ifEmpty { "none" }, point, t[1], if (point == "/system") "ro,relatime" else "rw,relatime")
                    }
                    f.name.startsWith("fstab") && t.size >= 3 && t[0].startsWith("/dev") && t[1].startsWith("/") ->
                        add(t[0], t[1], t[2], "rw,relatime")
                }
            }
        }
        // карта памяти видна «смонтированной»
        add("/dev/block/vold/179:1", img.sdcardPath, "vfat", "rw,dirsync,nosuid,nodev,noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702")
        for (p in mounts.keys) if (p != "/" && !p.startsWith("/proc") && !p.startsWith("/sys") && !p.startsWith("/dev")) File(root, p.trimStart('/')).mkdirs()
        val f = File(root, "proc/mounts")
        f.parentFile?.mkdirs()
        val text = mounts.values.joinToString("\n", postfix = "\n")
        if (f.takeIf { it.isFile }?.readText() != text) f.writeText(text)
    }

    /**
     * Звук обслуживает наш HAL (один выход на динамик, 48 кГц), поэтому audio_policy.conf пишем
     * канонический: лишние выходы прошивок (hdmi с dynamic-параметрами, deep_buffer, a2dp, usb)
     * заставляют audio_policy спрашивать у HAL то, чего он не знает, и mediaserver падает.
     */
    private fun audioPolicy() {
        val cur = File(root, "system/etc/audio_policy.conf")
        val rom = File(root, "system/etc/audio_policy.conf.rom")
        if (!cur.isFile && !rom.isFile && img.api < 16) return
        runCatching {
            if (cur.isFile && !rom.isFile) cur.copyTo(rom)
            val text = """
                |# создано AEmulator: единственный модуль — звуковой HAL эмулятора
                |global_configuration {
                |  attached_output_devices AUDIO_DEVICE_OUT_SPEAKER
                |  default_output_device AUDIO_DEVICE_OUT_SPEAKER
                |  attached_input_devices AUDIO_DEVICE_IN_BUILTIN_MIC
                |}
                |
                |audio_hw_modules {
                |  primary {
                |    outputs {
                |      primary {
                |        sampling_rates 48000
                |        channel_masks AUDIO_CHANNEL_OUT_STEREO
                |        formats AUDIO_FORMAT_PCM_16_BIT
                |        devices AUDIO_DEVICE_OUT_SPEAKER
                |        flags AUDIO_OUTPUT_FLAG_PRIMARY
                |      }
                |    }
                |    inputs {
                |      primary {
                |        sampling_rates 8000|16000|44100|48000
                |        channel_masks AUDIO_CHANNEL_IN_MONO|AUDIO_CHANNEL_IN_STEREO
                |        formats AUDIO_FORMAT_PCM_16_BIT
                |        devices AUDIO_DEVICE_IN_BUILTIN_MIC
                |      }
                |    }
                |  }
                |}
                |""".trimMargin()
            if (cur.takeIf { it.isFile }?.readText() != text) cur.writeText(text)
        }.onFailure { log("звук: audio_policy.conf не записался: ${it.message}") }
    }

    /** Кадровый буфер — обычный файл, qemu отвечает на ioctl FBIOGET_* от гостя. */
    private fun makeFb() {
        val s = img.settings
        val (w, h) = if (engine == Engine.GB) 480 to 800 else s.width to s.height
        // двойная буферизация: гость листает страницы через FBIOPAN_DISPLAY
        val need = w.toLong() * h * 2 * 2
        val f = paths.fb
        if (f.isFile && f.length() == need) return
        f.parentFile?.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(need) }
        runCatching { File(root, "dhd.fbgeom").writeText("$w $h\n") }
        log("кадровый буфер: ${w}x$h")
    }

    /**
     * Владельцы файлов приложений в /data/data: ядро телефона не даёт менять uid, поэтому qemu
     * подставляет их по таблице dhd.owners (dev, ino → uid, gid). Берём uid из packages.xml.
     */
    private fun seedOwners() {
        val stamp = File(root, "dhd.owners.seeded")
        val done = if (stamp.isFile) stamp.readLines().filter { it.isNotBlank() }.toMutableSet() else mutableSetOf()
        val pkgXml = File(root, "data/system/packages.xml")
        val dataDir = File(root, "data/data")
        if (!pkgXml.isFile || !dataDir.isDirectory) return
        runCatching {
            val text = pkgXml.readText()
            val re = Regex("<package name=\"([^\"]+)\"[^>]*?\\s(?:shared)?[Uu]serId=\"(\\d+)\"")
            val sb = StringBuilder()
            var rows = 0
            var pkgs = 0
            for (m in re.findAll(text)) {
                val name = m.groupValues[1]
                val uid = m.groupValues[2].toIntOrNull() ?: continue
                val d = File(dataDir, name)
                if (!d.isDirectory) continue
                val key = "$name $uid"
                if (key in done) continue
                done.add(key)
                pkgs++
                val todo = ArrayDeque<File>().apply { add(d) }
                while (todo.isNotEmpty()) {
                    val f = todo.removeFirst()
                    val st = runCatching { Os.lstat(f.absolutePath) }.getOrNull() ?: continue
                    sb.append(String.format("%016x %016x %08x %08x\n", st.st_dev, st.st_ino, uid, uid))
                    rows++
                    if (!OsConstants.S_ISLNK(st.st_mode) && f.isDirectory) f.listFiles()?.forEach { todo.add(it) }
                }
            }
            if (rows > 0) {
                paths.owners.appendText(sb.toString())
                log("владельцы файлов восстановлены: пакетов $pkgs, записей $rows")
            }
            stamp.writeText(done.joinToString("\n", postfix = "\n"))
        }.onFailure { log("владельцы: ${it.message}") }
    }

    /** Пропустить миграцию PRE_BOOT_COMPLETED (4.x): под эмуляцией она занимает минуты. */
    fun skipPreBoot(props: PropService) {
        if (img.api < 14) return
        val f = File(root, "data/system/called_pre_boots.dat")
        val rel = props.get("ro.build.version.release") ?: img.release
        val code = props.get("ro.build.version.codename") ?: "REL"
        val incr = props.get("ro.build.version.incremental") ?: ""
        val fresh = runCatching {
            DataInputStream(f.inputStream().buffered()).use { i ->
                i.readInt() == 10000 && i.readUTF() == rel && i.readUTF() == code && i.readUTF() == incr
            }
        }.getOrDefault(false)
        if (fresh) return
        runCatching {
            f.parentFile?.mkdirs()
            DataOutputStream(f.outputStream().buffered()).use { o ->
                o.writeInt(10000); o.writeUTF(rel); o.writeUTF(code); o.writeUTF(incr); o.writeInt(0)
            }
        }
    }

    /** Экран не гаснет, данные «включены»: прямо в базе настроек (есть после первой загрузки). */
    fun noScreenSleep() {
        val db = File(root, "data/data/com.android.providers.settings/databases/settings.db")
        if (!db.isFile) return
        runCatching {
            SQLiteDatabase.openDatabase(db.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
                fun put(table: String, name: String, value: String) {
                    val exists = runCatching {
                        d.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.count > 0 }
                    }.getOrDefault(false)
                    if (!exists) return
                    val cv = android.content.ContentValues().apply { put("name", name); put("value", value) }
                    if (d.update(table, cv, "name=?", arrayOf(name)) == 0) d.insert(table, null, cv)
                }
                put("system", "screen_off_timeout", "2147483647")
                val t = if (img.api >= 17) "global" else "system"
                put(t, "stay_on_while_plugged_in", "7")
                put(if (img.api >= 17) "global" else "secure", "mobile_data", "1")
                put(if (img.api >= 17) "global" else "secure", "package_verifier_enable", "0")
                put(if (img.api >= 17) "global" else "secure", "install_non_market_apps", "1")
                put("secure", "install_non_market_apps", "1")
                if (img.api >= 17) put("global", "verifier_verify_adb_installs", "0")
            }
        }.onFailure { log("настройки: база не открылась: ${it.message}") }
    }

    private fun isLink(f: File) = runCatching { OsConstants.S_ISLNK(Os.lstat(f.absolutePath).st_mode) }.getOrDefault(false)
    private fun wipe(f: File) = ImageStore.wipe(f)

    companion object {
        /** Прошивка MediaTek, где AudioFlinger связан с собственной звуковой библиотекой MTK (/dev/eac). */
        fun isMtkAudio(root: File): Boolean {
            val f = File(root, "system/lib/libaudio.primary.default.so")
            if (!f.isFile || f.length() > 20_000_000) return false
            return runCatching { String(f.readBytes(), Charsets.ISO_8859_1).contains("AudioMTKHardware") }.getOrDefault(false)
        }

        private val LOGS = listOf("main", "system", "radio") // events — канал, его делает EventsSink
        private val DATA_DIRS = listOf(
            "data", "data/app", "data/app-private", "data/app-lib", "data/app-asec", "data/data", "data/dalvik-cache",
            "data/local", "data/local/tmp", "data/misc", "data/misc/keystore", "data/misc/wifi", "data/misc/zoneinfo",
            "data/property", "data/system", "data/anr", "data/backup", "data/lost+found", "data/drm", "data/media",
            "data/resource-cache", "data/security", "data/tombstones", "cache", "cache/download", "cache/lost+found",
            "mnt", "mnt/asec", "mnt/obb", "mnt/secure", "mnt/secure/asec", "mnt/shell", "mnt/media_rw", "storage",
        )
    }
}
