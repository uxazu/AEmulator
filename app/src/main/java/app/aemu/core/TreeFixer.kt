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
        log("firmware prep: vendor modules set aside: $n")
    }

    // ---------------------------------------------------------------- перед каждым запуском

    /**
     * Precompiled code of 5.0+ system apps and of the framework (the odex files in oat/arm and framework/arm) is the only copy: the jars
     * and apks are stripped. installd's dexopt on a fresh /data replaced or unlinked those files inside /system
     * (a real /system is read-only), and the next boot died in system_server with "No original dex files found".
     * Taking the write bit away makes such writes fail harmlessly; ImageStore.wipe gives it back before deleting.
     */
    private fun protectOat() {
        val sys = File(root, "system")
        var n = 0
        fun lock(d: File) {
            d.listFiles()?.forEach { f -> if (f.isFile) { if (f.canWrite()) { f.setWritable(false, false); n++ } } else if (f.isDirectory) lock(f) }
            d.setWritable(false, false)
        }
        File(sys, "framework").listFiles()?.filter { it.isDirectory && (it.name == "oat" || it.name == "arm" || it.name == "arm64") }?.forEach { lock(it) }
        for (top in listOf("app", "priv-app")) File(sys, top).listFiles()?.forEach { app ->
            File(app, "oat").takeIf { it.isDirectory }?.let { lock(it) }
        }
        if (n > 0) log("oat files write-protected: $n")
    }

    fun fixup(owners: Boolean = true) {
        if (owners) seedOwners()
        installEngineFiles()
        protectOat()
        fixThemeXml()
        parkWhetstone()
        parkQcomServices()
        swapMtkAudioHal()
        eglConfig(img.settings.gpu)
        makeDataDirs()
        makeUserZeroLink()
        makeDevNodes()
        makeSysNodes()
        makeProcMounts()
        audioPolicy()
        qtaguid()
        vendorChecks()
        scriptShebangs()
        samsungEfs()
        keyLayout()
        for (n in LOGS) File(root, "dev/log/$n").let { if (!it.isFile) { it.parentFile?.mkdirs(); it.createNewFile() } }
        makeFb()
    }

    /**
     * Скрипты am/pm/input/monkey… в старых прошивках начинаются с «# Script…» без #!. Ядро на такой
     * execve отвечает ENOEXEC и mksh запускает файл сам, а qemu вместо этого падает «Exec format error».
     */
    private fun scriptShebangs() {
        for (dir in listOf("system/bin", "system/xbin")) {
            val files = File(root, dir).listFiles() ?: continue
            for (f in files) {
                if (!f.isFile || f.length() > 64_000 || java.nio.file.Files.isSymbolicLink(f.toPath())) continue
                val head = runCatching { f.inputStream().use { s -> ByteArray(2).also { s.read(it) } } }.getOrNull() ?: continue
                if (head[0] != '#'.code.toByte() || head[1] == '!'.code.toByte()) continue
                runCatching { f.writeBytes("#!/system/bin/sh\n".toByteArray() + f.readBytes()) }
            }
        }
    }

    /**
     * Samsung: без /efs/FactoryApp/factorymode = ON прошивка считает себя заводской и поверх всего
     * рисует таблицу «PDA / CSC / RF Cal Date…», а часть служб работает в режиме заводского теста.
     */
    private fun samsungEfs() {
        val efs = File(root, "efs")
        if (!efs.isDirectory) return
        val dir = File(efs, "FactoryApp").apply { mkdirs() }
        for (n in listOf("factorymode", "keystr")) {
            val f = File(dir, n)
            if (runCatching { f.readText().trim() }.getOrNull() != "ON") runCatching { f.writeText("ON") }
        }
    }

    private fun qcomAudioFlinger(): Boolean = runCatching {
        val af = File(root, "system/lib/libaudioflinger.so")
        af.isFile && String(af.readBytes(), Charsets.ISO_8859_1).contains("setFmVolume")
    }.getOrDefault(false)

    /**
     * MediaTek 4.x: libaudioflinger loads the primary HAL from /system/lib/libaudio.primary.default.so
     * (AudioMTKHardware, which needs the MTK sound driver — silent here). Park it as libaudio.mtk.so and put
     * our HAL there; it forwards the DcRemove filter AudioFlinger links from that library to the original.
     */
    private fun swapMtkAudioHal() {
        if (engine != Engine.KK || img.api < 17) return
        val lib = File(root, "system/lib/libaudio.primary.default.so")
        val orig = File(root, "system/lib/libaudio.mtk.so")
        if (!orig.isFile) {
            if (!lib.isFile || !isMtkAudio(root)) return
            if (!lib.renameTo(orig)) return
        }
        val asset = "engines/kk/audio.primary.mtk.so"
        val size = runCatching { ctx.assets.openFd(asset).use { it.length } }.getOrDefault(-1L)
        if (lib.isFile && lib.length() == size && sameContent(asset, lib)) return
        runCatching {
            ctx.assets.open(asset).use { i -> lib.outputStream().use { o -> i.copyTo(o) } }
            lib.setReadable(true, false)
            log("audio: MediaTek HAL replaced with the emulator HAL")
        }.onFailure { log("audio: MediaTek HAL swap failed: ${it.message}") }
    }

    /**
     * MIUI 8 reads system/media/theme/theme_compatibility.xml while zygote preloads classes. Some builds ship it with
     * nested comments ("<!-- <!-- ...") that Android's strict XML parser rejects; ThemeCompatibility and MiuiResources
     * then fail to initialise in every process and the system never finishes booting (black screen). Double hyphens
     * inside comments are separated so the file parses.
     */
    private fun fixThemeXml() {
        val f = File(root, "system/media/theme/theme_compatibility.xml")
        if (!f.isFile || f.length() > 4_000_000) return
        runCatching {
            val s = f.readText()
            if (!s.contains("<!-- <!--")) return
            val out = StringBuilder(s.length)
            var i = 0
            while (true) {
                val a = s.indexOf("<!--", i)
                if (a < 0) { out.append(s, i, s.length); break }
                out.append(s, i, a + 4)
                val e = s.indexOf("-->", a + 4)
                if (e < 0) { out.append(s, a + 4, s.length); break }
                out.append(s.substring(a + 4, e).replace("--", "- -")).append("-->")
                i = e + 3
            }
            f.writeText(out.toString())
            log("theme_compatibility.xml: nested comments fixed")
        }
    }

    /**
     * MIUI 8: ActivityManagerService calls MIUI's Whetstone service (an app process) while holding its own lock, and that
     * process in turn waits for the same lock. The watchdog then restarts system_server over and over and the boot never
     * finishes. Whetstone only does background-power bookkeeping, so its package is set aside.
     */
    private fun parkWhetstone() {
        val apk = File(root, "system/app/Whetstone.apk")
        if (!apk.isFile || !File(root, "system/app/miuisystem.apk").isFile) return
        runCatching {
            val dir = File(root, "system/.aemu-parked").apply { mkdirs() }
            if (apk.renameTo(File(dir, "Whetstone.apk"))) {
                File(root, "data/dalvik-cache/system@app@Whetstone.apk@classes.dex").delete()
                log("MIUI: Whetstone package set aside (deadlock with the activity manager)")
            }
        }
    }

    /**
     * qemu names the input device "dhd-touch"; with no layout of that name the guest falls back to the firmware's
     * Generic.kl / qwerty.kl, where vendors map the codes differently (4.x Generic.kl: 102 = MOVE_HOME, 580 often
     * unmapped), so Home and Recents did nothing on many firmwares. A layout under the device's own name is looked up
     * first by every version from 2.3 on and gives the navigation bar the same codes everywhere.
     */
    private fun keyLayout() {
        val f = File(root, "system/usr/keylayout/dhd-touch.kl")
        val text = buildString {
            append("# AEmulator: navigation bar and hardware keys of the emulator\n")
            append("key ${InputService.KEY_HOME} HOME WAKE\n")
            append("key 172 HOME WAKE\n")
            append("key ${InputService.KEY_BACK} BACK WAKE_DROPPED\n")
            append("key ${InputService.KEY_MENU} MENU WAKE_DROPPED\n")
            append("key ${InputService.KEY_SEARCH} SEARCH WAKE_DROPPED\n")
            append("key ${InputService.KEY_POWER} POWER WAKE\n")
            append("key ${InputService.KEY_VOLUMEDOWN} VOLUME_DOWN WAKE\n")
            append("key ${InputService.KEY_VOLUMEUP} VOLUME_UP WAKE\n")
            append("key 113 VOLUME_MUTE\n")
            append("key 212 CAMERA\n")
            // APP_SWITCH exists from 3.0; older layouts reject unknown labels and drop the whole file
            if (img.api >= 11) append("key ${InputService.KEY_APPSELECT} APP_SWITCH\n")
        }.let {
            // 5.0 dropped the WAKE flags from the layout syntax: an unknown flag makes the guest reject the whole file
            if (img.api >= 21) it.replace(" WAKE_DROPPED", "").replace(" WAKE", "") else it
        }
        if (runCatching { f.readText() }.getOrNull() == text) return
        runCatching { f.parentFile?.mkdirs(); f.writeText(text); f.setReadable(true, false) }
            .onFailure { log("key layout: ${it.message}") }
    }

    /**
     * Qualcomm builds: QTI permissions, DPM, the location stack, the RIL message tunnel and Dirac audio bind to modem,
     * DSP and data-path daemons that do not exist here; their apps crash in a loop and drag system_server with them.
     */
    private fun parkQcomServices() {
        val parked = File(root, "system/.aemu-parked")
        var n = 0
        // 4.x keeps apps as bare app/Name.apk (+ .odex) instead of app/Name/
        for (rel in QCOM_PARK.flatMap { if (it.endsWith(".xml")) listOf(it) else listOf(it, "$it.apk", "$it.odex") }) {
            val f = File(root, rel)
            if (!f.exists() && !isLink(f)) continue
            parked.mkdirs()
            val dst = File(parked, rel.replace('/', '#'))
            if (dst.exists()) ImageStore.wipe(dst)
            if (f.renameTo(dst)) n++
        }
        if (n > 0) log("Qualcomm: vendor services set aside: $n")
    }

    /** Native arm64 engine: its HALs and libraries are arm64 files from assets/engines/n64, named "a#b#c" for a/b/c. */
    private fun installNative64() {
        // engines/n64-<api>: AOSP's own SurfaceFlinger for that release. The vendors' ones (MIUI's blur, Qualcomm's
        // ExSurfaceFlinger) need the GPU's GLES 2 and show no windows on the software renderer.
        var copied = 0
        for (dir in listOf("engines/n64", "engines/n64-${img.api}")) for (n in runCatching { ctx.assets.list(dir) }.getOrNull().orEmpty()) {
            val dst = File(root, n.replace('#', '/'))
            val asset = "$dir/$n"
            val size = runCatching { ctx.assets.openFd(asset).use { it.length } }.getOrDefault(-1L)
            if (dst.isFile && size >= 0 && dst.length() == size && sameContent(asset, dst)) continue
            runCatching {
                dst.parentFile?.mkdirs()
                if (isLink(dst)) Os.remove(dst.absolutePath)
                else if (dst.isFile) {          // the firmware's own file is kept aside
                    val parked = File(root, "system/.aemu-parked/$n")
                    parked.parentFile?.mkdirs()
                    if (!parked.exists()) dst.renameTo(parked)
                }
                ctx.assets.open(asset).use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
                dst.setReadable(true, false); dst.setExecutable(true, false)
                copied++
            }.onFailure { log("$n: ${it.message}") }
        }
        // mediaserver of a 6.0 arm64 firmware is a 32-bit program (it runs through qemu-arm): camera and sound
        // reach the phone through the same 32-bit HALs as on the other engines
        if (File(root, "system/bin/mediaserver").let { it.isFile && !isElf64(it) }) {
            for ((asset, to) in listOf("engines/common/camera.default.so" to "system/lib/hw/camera.default.so",
                "engines/kk/audio.primary.aosp.so" to "system/lib/hw/audio.primary.default.so")) {
                val dst = File(root, to)
                val size = runCatching { ctx.assets.openFd(asset).use { it.length } }.getOrDefault(-1L)
                if (dst.isFile && size >= 0 && dst.length() == size && sameContent(asset, dst)) continue
                runCatching {
                    dst.parentFile?.mkdirs()
                    val parked = File(root, "system/.aemu-parked/${to.replace('/', '#')}")
                    parked.parentFile?.mkdirs()
                    if (dst.isFile && !parked.exists()) dst.renameTo(parked)
                    ctx.assets.open(asset).use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
                    dst.setReadable(true, false); copied++
                }.onFailure { log("$to: ${it.message}") }
            }
        }
        // the Java stubs (bluetooth manager, network, tool runner) are dex code: the same jar as for 32-bit guests
        runCatching {
            val jar = File(root, "system/framework/aemu-stubs.jar")
            val size = ctx.assets.openFd("engines/kk/aemu-stubs.jar").use { it.length }
            if (!jar.isFile || jar.length() != size) {
                ctx.assets.open("engines/kk/aemu-stubs.jar").use { i -> jar.outputStream().use { o -> i.copyTo(o) } }
                jar.setReadable(true, false); copied++
            }
        }
        if (copied > 0) log("engine n64 files updated: $copied")
        // qemu-arm (the firmware's 32-bit programs) asks for the lowest mappable address; the phone hides the real file
        runCatching { File(root, "proc/sys/vm").mkdirs(); File(root, "proc/sys/vm/mmap_min_addr").writeText("65536") }
        runCatching { File(root, "proc/.one/1").mkdirs(); File(root, "proc/.task1").mkdirs(); File(root, "proc/.task1/none").writeText("") }     // what a 32-bit program sees as /proc/self/task
        // the vendor's HALs drive hardware this phone does not have (ION, MDP, the SoC's audio DSP): with them
        // aside, the loader falls back to the *.default ones
        var parked = 0
        for (dir in listOf("system/lib64/hw", "system/vendor/lib64/hw", "system/lib/hw", "system/vendor/lib/hw")) {
            File(root, dir).listFiles()?.forEach { f ->
                val id = f.name.substringBefore('.')
                // (Qualcomm ships its location HAL as gps.default.so: no GPS at all is better than its modem worker)
                if (!f.isFile || (f.name.endsWith(".default.so") && id != "gps") || id !in VENDOR_HALS) return@forEach
                val dst = File(root, "system/.aemu-parked/${f.relativeTo(root).path.replace('/', '#')}")
                dst.parentFile?.mkdirs()
                if (dst.exists()) dst.delete()
                if (f.renameTo(dst)) parked++
            }
        }
        // Qualcomm location core loads its modem (QMI) back end by name and crashes inside it without a modem;
        // with the back end gone it keeps its built-in do-nothing one
        for (rel in listOf("system/lib64/libloc_api_v02.so", "system/vendor/lib64/libloc_api_v02.so", "system/lib/libloc_api_v02.so", "system/vendor/lib/libloc_api_v02.so")) {
            val f = File(root, rel)
            if (!f.isFile) continue
            val dst = File(root, "system/.aemu-parked/${rel.replace('/', '#')}")
            dst.parentFile?.mkdirs()
            if (f.renameTo(dst)) parked++
        }
        // the vendor's RenderScript driver goes straight to its GPU; without it libRS computes on the CPU
        for (dir in listOf("system/lib64", "system/vendor/lib64", "system/lib", "system/vendor/lib")) {
            File(root, dir).listFiles()?.filter { it.name.startsWith("libRSDriver_") }?.forEach { f ->
                val dst = File(root, "system/.aemu-parked/${f.relativeTo(root).path.replace('/', '#')}")
                dst.parentFile?.mkdirs()
                if (f.renameTo(dst)) parked++
            }
        }
        if (parked > 0) log("vendor HALs set aside: $parked")
    }

    private val VENDOR_HALS = setOf("gralloc", "hwcomposer", "copybit", "lights", "sensors", "audio", "power", "memtrack",
        "camera", "gps", "fingerprint", "gxfingerprint", "consumerir", "bluetooth", "nfc", "vibrator", "keystore", "gatekeeper")

    private fun installEngineFiles() {
        val copies = when (engine) {
            Engine.KK -> listOf(
                "libashmemshim.so" to "system/lib/libashmemshim.so",
                "libGLES.so" to "system/lib/egl/libGLES_bridge.so",
                "audio.primary.default.so" to "system/lib/hw/audio.primary.default.so",
                "gralloc.default.so" to "system/lib/hw/gralloc.default.so",
                "fbpaint" to "system/bin/fbpaint",
                "aemu-stubs.jar" to "system/framework/aemu-stubs.jar",
            )
            Engine.GB -> listOf(
                "libashmemshim.so" to "system/lib/libashmemshim.so",
                "libGLES_dhd.so" to "system/lib/egl/libGLES_dhd.so",
                "gralloc.default.so" to "system/lib/hw/gralloc.default.so",
                "fbpaint" to "system/bin/fbpaint",
            )
            Engine.N64 -> return installNative64()
        }
        // a static network debugging tool shipped by older versions; nothing runs it
        File(root, "system/bin/netprobe").delete()
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
                if (romGralloc.renameTo(parked)) log("firmware gralloc uses ION, replaced with engine gralloc")
            }
        }
        var copied = 0
        // 7.0+ libEGL on a qemu kernel (qemu.gles=1) only tries the "emulation" driver name
        val nougat = if (img.api >= 24 && copies.any { it.second == "system/lib/egl/libGLES_bridge.so" })
            listOf("libGLES_split.so" to "system/lib/egl/libGLES_emulation.so") else emptyList()
        // 7.0+ without the GPU bridge: libEGL would still find the bridge by scanning egl/, so it is not there at all
        val noBridge = img.api >= 24 && !img.settings.gpu
        if (noBridge) {
            for (n in listOf("libGLES_bridge.so", "libGLES_emulation.so")) File(root, "system/lib/egl/$n").delete()
            // a scan skips libGLES_android.so; in "vendor software renderer" mode (qemu.gles=2) libEGL asks for
            // libGLES_swiftshader.so, which here is the stock software renderer
            val sw = File(root, "system/lib/egl/libGLES_swiftshader.so")
            if (!sw.exists() && !isLink(sw) && File(root, "system/lib/egl/libGLES_android.so").isFile)
                runCatching { Os.symlink("libGLES_android.so", sw.path) }
        }
        for ((from, to) in (copies + nougat).filter { !noBridge || !it.second.startsWith("system/lib/egl/libGLES_") } +
            listOf("@libaemushim.so" to "system/lib/libaemushim.so", "@lights.default.so" to "system/lib/hw/lights.default.so",
                "@sensors.default.so" to "system/lib/hw/sensors.default.so",
                "@camera.default.so" to "system/lib/hw/camera.default.so")) {
            val dst = File(root, to)
            // gralloc движка — только если в прошивке своего нет
            if (from == "gralloc.default.so" && dst.isFile) continue
            // «@» — общий для всех движков файл
            // звуковой HAL стенда разложен под AudioFlinger HTC; остальным 4.2+ — вариант с раскладкой AOSP.
            // У Samsung свой audio_stream_out (лишние слоты) — с ним AOSP-вариант роняет mediaserver,
            // поэтому там остаётся исходный: выход не открывается, система работает без звука.
            // Samsung 4.3 AudioFlinger uses the KitKat slots (verified on I9300 XXUGNJ2: init_check 0x44,
            // open_output_stream 0x6c, stream write 0x40), so only 4.1–4.2 TouchWiz keeps the stand HAL
            val samsung = img.skin.contains("TouchWiz", true) || img.skin.contains("Samsung", true)
            val htcLike = img.skin.contains("HTC", true) || (samsung && img.api < 18)
            val name = when {
                from != "audio.primary.default.so" || htcLike -> from
                // 4.0 has its own audio_hw_device layout; Qualcomm CAF builds add set_fm_volume/open_output_session
                img.api in 14..15 -> if (qcomAudioFlinger()) "audio.primary.ics-qcom.so" else "audio.primary.ics.so"
                img.api >= 16 -> "audio.primary.aosp.so"
                else -> from
            }
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
            }.onFailure { log("${from.removePrefix("@")} missing from engine set: ${it.message}") }
        }
        // вендорские драйверы RenderScript (Adreno, Mali…) лезут в GPU; без них libRS берёт процессорный
        for (dir in listOf("system/lib", "vendor/lib", "system/vendor/lib")) {
            File(root, dir).listFiles()?.filter { it.name.startsWith("libRSDriver_") }?.forEach { f ->
                val parked = File(root, "system/.aemu-parked/${f.relativeTo(root).path.replace('/', '#')}")
                parked.parentFile?.mkdirs()
                if (f.renameTo(parked)) log("RenderScript: removed vendor driver ${f.name}")
            }
        }
        // netfilter в эмуляторе нет: iptables всегда падает, а netd 4.x (Samsung) считает это фатальным
        // для NetworkManagementService/ConnectivityService — ставим пустую программу, родную прячем
        val trueSize = runCatching { ctx.assets.openFd("engines/common/aemu_true.so").use { it.length } }.getOrDefault(-1L)
        // MIUI: invoke-as runs FirewallService's iptables loop as root; under qemu that shell never sees its
        // children exit, so ConnectivityService (and the whole boot) hangs in CommandLineUtils.waitFor
        val fakes = listOf("bin/iptables", "bin/ip6tables") + if (img.skin.contains("MIUI", true)) listOf("xbin/invoke-as") else emptyList()
        for (rel in fakes) {
            val n = rel.substringAfter('/')
            val f = File(root, "system/$rel")
            if (!f.exists() && !isLink(f) || trueSize < 0 || (!isLink(f) && f.length() == trueSize)) continue
            runCatching {
                val parked = File(root, "system/.aemu-parked/system#${rel.replace('/', '#')}")
                parked.parentFile?.mkdirs()
                if (isLink(f)) Os.remove(f.absolutePath) else if (!parked.exists()) f.renameTo(parked) else f.delete()
                ctx.assets.open("engines/common/aemu_true.so").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                f.setReadable(true, false); f.setExecutable(true, false)
                copied++
            }.onFailure { log("$n not replaced: ${it.message}") }
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
        if (copied > 0) log("engine ${engine.id} files updated: $copied")
    }

    /** Направляет загрузчик EGL прошивки на GL-мост (или на программный растеризатор). */
    private fun eglConfig(gpu: Boolean) {
        val dir = File(root, "system/lib/egl").apply { mkdirs() }
        val cfg = File(dir, "egl.cfg")
        val cfgRom = File(dir, "egl.cfg.rom")
        if (!cfgRom.isFile && cfg.isFile) cfg.copyTo(cfgRom, overwrite = true)
        when (engine) {
            Engine.N64 -> {}
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
                    }.onFailure { log("GL: shim failed to install: ${it.message}") }
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

    private fun isElf64(f: File): Boolean = runCatching {
        f.inputStream().use { i -> val b = ByteArray(5); i.read(b) == 5 && b[4] == 2.toByte() }
    }.getOrDefault(false)

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
            if (n > 0) log("MediaTek DRVB: platform check disabled ($n func.)")
        }
    }.onFailure { log("MediaTek DRVB: failed: ${it.message}") }

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
        if (made > 0) log("created init directories: $made")
    }

    private fun makeUserZeroLink() {
        if (img.api < 14) return // ICS installd creates /data/user/0 too; an absolute link points at the host /data
        // 7.0+: vold prepares the device-encrypted per-user dirs; without it installd cannot create app DE storage
        if (img.api >= 24) for (d in listOf("data", "user_de/0", "misc_ce/0", "misc_de/0", "system_ce/0", "system_de/0", "media/0", "misc/profiles/cur/0", "misc/profiles/ref"))
            File(root, "data/$d").mkdirs()
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
        healthdRelativeSysfs(base)
        personalityNoop()
        parkNfc()
        mainStackMaps()
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
                    if (rel.contains("wait_for_fb")) continue // handled below
                    File(root, rel).let { f -> if (!f.exists()) { f.parentFile?.mkdirs(); f.createNewFile(); f.setWritable(true, false) } }
                }
            }
        }
        // SurfaceFlinger's DisplayEventThread loops sleep→wake: missing nodes make it spin at 100% CPU (EBADF).
        // wake answers at once; sleep is a FIFO with no writer, so the read blocks forever and the screen stays on.
        runCatching {
            File(power, "wait_for_fb_wake").let { if (!it.isFile) it.writeText("awake") }
            val sleep = File(power, "wait_for_fb_sleep")
            if (!sleep.exists()) android.system.Os.mkfifo(sleep.absolutePath, "666".toInt(8))
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
        }.onFailure { log("audio: failed to write audio_policy.conf: ${it.message}") }
    }

    /** Кадровый буфер — обычный файл, qemu отвечает на ioctl FBIOGET_* от гостя. */
    private fun makeFb() {
        val s = img.settings
        val (w, h) = if (engine == Engine.GB) 480 to 800 else s.width to s.height
        // двойная буферизация: гость листает страницы через FBIOPAN_DISPLAY
        // qemu reports smem_len = two 32-bit pages (TWRP draws BGRA_8888); mmap past the file end would SIGBUS
        val need = w.toLong() * h * 4 * 2
        val f = paths.fb
        if (f.isFile && f.length() == need) return
        f.parentFile?.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(need) }
        runCatching { File(root, "dhd.fbgeom").writeText("$w $h\n") }
        log("framebuffer: ${w}x$h")
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
                log("file owners restored: $pkgs packages, $rows entries")
            }
            stamp.writeText(done.joinToString("\n", postfix = "\n"))
        }.onFailure { log("owners: ${it.message}") }
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
                // мастер первого запуска считаем пройденным: иначе он остаётся «домашним экраном»,
                // и кнопка «Домой» открывает его (у Samsung — чёрный экран на минуты)
                put(if (img.api >= 17) "global" else "secure", "device_provisioned", "1")
                put("secure", "user_setup_complete", "1")
            }
        }.onFailure { log("settings: database failed to open: ${it.message}") }
        disableSetupWizards()
    }

    /** Отключает пакеты мастеров первого запуска в package-restrictions.xml (enabled="2" = выключен). */
    private fun disableSetupWizards() {
        val f = File(root, "data/system/users/0/package-restrictions.xml")
        if (!f.isFile) return
        // packages.xml, а не packages.list: в списке нет пакетов с общим системным uid (мастер Samsung)
        val pkgs = runCatching { File(root, "data/system/packages.xml").readText() }.getOrNull() ?: return
        val targets = SETUP_WIZARDS.filter { pkgs.contains("<package name=\"$it\"") }
        if (targets.isEmpty()) return
        var xml = runCatching { f.readText() }.getOrNull() ?: return
        var changed = false
        for (p in targets) {
            val open = Regex("<pkg name=\"${Regex.escape(p)}\"([^>]*?)(/?)>")
            val m = open.find(xml)
            if (m == null) {
                xml = xml.replace("</package-restrictions>", "    <pkg name=\"$p\" enabled=\"2\" />\n</package-restrictions>")
                changed = true
            } else if (!m.groupValues[1].contains("enabled=\"2\"")) {
                val attrs = m.groupValues[1].replace(Regex(" enabled=\"\\d\""), "")
                xml = xml.replaceRange(m.range, "<pkg name=\"$p\"$attrs enabled=\"2\"${m.groupValues[2]}>")
                changed = true
            }
        }
        if (changed) runCatching { f.writeText(xml); log("setup wizard disabled: ${targets.joinToString()}") }
    }

    /**
     * Static healthd (5.0+) checks the battery files with faccessat, which the stand's qemu does not map into the
     * guest root: it tests the phone's real /sys and finds no battery. Its path constant becomes the relative
     * "sys/class/power_supply" (resolved from the services' working directory, run/, where sys links to root/sys).
     */
    private fun healthdRelativeSysfs(base: File) {
        if (img.api < 21) return
        runCatching {
            val link = File(paths.bin, "sys")
            paths.bin.mkdirs()
            if (!isLink(link)) { wipe(link); Os.symlink(File(root, "sys").absolutePath, link.path) }
            val hd = File(root, "sbin/healthd").takeIf { it.isFile } ?: return
            val d = hd.readBytes()
            val from = "/sys/class/power_supply".toByteArray()
            val to = "sys/class/power_supply".toByteArray() + 0
            var n = 0
            var i = 0
            while (i <= d.size - from.size) {
                if (d[i] == from[0] && (0 until from.size).all { d[i + it] == from[it] }) { System.arraycopy(to, 0, d, i, to.size); n++; i += from.size } else i++
            }
            if (n > 0) { hd.writeBytes(d); log("healthd: battery sysfs path made relative ($n)") }
        }.onFailure { log("healthd patch failed: ${it.message}") }
        if (!base.isDirectory) log("no fake power_supply")
    }

    /**
     * bionic 6.0+ starts every 32-bit process with personality(PER_LINUX32) and aborts ("error setting PER_LINUX32
     * personality") when it fails — and it does on phones without AArch32 (Snapdragon 8 Elite…), qemu passes the
     * call to the host kernel. The personality syscall stub (mov ip,r7; mov r7,#136; swi 0) gets "mov r0,#0"
     * instead of the swi, in libc.so and in static binaries (healthd, ueventd…). Done once per image.
     */
    private fun personalityNoop() {
        if (img.api < 23) return
        val stamp = File(root, ".aemu-personality")
        if (stamp.isFile && runCatching { stamp.readText().trim().toInt() }.getOrDefault(0) > 0) return
        val files = listOf(File(root, "system/lib/libc.so")) +
            listOf("sbin", "system/bin", "system/xbin").flatMap { File(root, it).listFiles().orEmpty().toList() }
        var n = 0
        for (f in files) {
            if (!f.isFile || isLink(f) || f.length() > 8_000_000 || f.length() < 1024) continue
            runCatching {
                val d = f.readBytes()
                if (d[0] != 0x7f.toByte() || d[1] != 'E'.code.toByte()) return@runCatching
                val b = java.nio.ByteBuffer.wrap(d).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                var hit = false
                var i = 0
                while (i + 12 <= d.size) {
                    // mov ip, r7 ; mov r7, #136 (6.0) or ldr r7, [pc, #x] holding 136 (7.x) ; swi #0
                    if (b.getInt(i) == 0xe1a0c007.toInt() && b.getInt(i + 8) == 0xef000000.toInt()) {
                        val w = b.getInt(i + 4)
                        val nr = when {
                            w == 0xe3a07088.toInt() -> 136
                            w and 0xfffff000.toInt() == 0xe59f7000.toInt() ->
                                (i + 4 + 8 + (w and 0xfff)).takeIf { it + 4 <= d.size }?.let { b.getInt(it) } ?: -1
                            else -> -1
                        }
                        if (nr == 136) { b.putInt(i + 8, 0xe3a00000.toInt()); hit = true }   // swi #0 → mov r0, #0
                    }
                    i += 4
                }
                if (hit) { f.writeBytes(d); n++ }
            }
        }
        log("personality(PER_LINUX32) made a no-op in $n files")
        runCatching { stamp.writeText("$n\n") }
    }

    /**
     * bionic 6.0 finds the main thread's stack in /proc/self/task/<pid>/maps; qemu only emulates /proc/self/maps,
     * so the guest parses the host's 64-bit map and ART gets random stack bounds ("Check failed: &stack_variable >
     * stack_end", then SIGSEGV in zygote). The format string in libc.so becomes "/proc/self/maps".
     */
    /**
     * 5.0+: the NFC app's own watchdog aborts it while the vendor NFC stack initialises under qemu; that
     * persistent crash loops the whole system. There is no NFC to offer, so the app is moved out of the way.
     */
    private fun parkNfc() {
        // 7.0+ zygote: the shim shows qemu's /dhd.owners descriptor under this whitelisted name; after fork
        // zygote reopens it by that path, which has to lead back to the same table
        if (img.api >= 24) runCatching {
            val link = File(root, "system/framework/aemu-owners.jar")
            if (!isLink(link)) { link.delete(); Os.symlink(paths.owners.absolutePath, link.path) }
        }
        if (img.api < 21) return
        val parked = File(root, "system/.aemu-parked").apply { mkdirs() }
        for (dir in listOf("system/app", "system/priv-app")) File(root, dir).listFiles()?.filter { it.name.startsWith("Nfc") }?.forEach { f ->
            val dst = File(parked, f.relativeTo(root).path.replace('/', '#'))
            if (f.renameTo(dst)) log("parked ${f.name}")
        }
    }

    private fun mainStackMaps() {
        if (img.api < 23) return
        val libc = File(root, "system/lib/libc.so")
        runCatching {
            val d = libc.readBytes()
            val from = "/proc/self/task/%d/maps".toByteArray()
            val i = indexOf(d, from)
            if (i < 0) return
            val to = "/proc/self/maps".toByteArray()
            for (k in from.indices) d[i + k] = if (k < to.size) to[k] else 0
            libc.writeBytes(d)
            log("libc: main thread stack read from /proc/self/maps")
        }.onFailure { log("libc maps patch failed: ${it.message}") }
    }

    private fun indexOf(d: ByteArray, p: ByteArray): Int {
        var i = 0
        while (i <= d.size - p.size) {
            if (d[i] == p[0] && (1 until p.size).all { d[i + it] == p[it] }) return i
            i++
        }
        return -1
    }

    private fun isLink(f: File) = runCatching { OsConstants.S_ISLNK(Os.lstat(f.absolutePath).st_mode) }.getOrDefault(false)
    private fun wipe(f: File) = ImageStore.wipe(f)

    companion object {
        /** Мастера первого запуска: Samsung, Google, AOSP, MIUI, HTC, Sony */
        val SETUP_WIZARDS = listOf(
            "com.sec.android.app.SecSetupWizard", "com.google.android.setupwizard", "com.android.provision",
            "com.miui.provision", "com.htc.setupwizard", "com.sonyericsson.setupwizard", "com.sonymobile.setupwizard",
            // вход в Google-аккаунт на 2.x–4.x больше не работает (старые протоколы входа Google закрыл):
            // его экран не рисуется, запрещает «Домой», и «Контакты» при первом запуске запирают в нём систему
            "com.google.android.gsf.login",
        )
        /** Прошивка MediaTek, где AudioFlinger связан с собственной звуковой библиотекой MTK (/dev/eac). */
        fun isMtkAudio(root: File): Boolean {
            if (File(root, "system/lib/libaudio.mtk.so").isFile) return true   // already swapped for our HAL
            val f = File(root, "system/lib/libaudio.primary.default.so")
            if (!f.isFile || f.length() > 20_000_000) return false
            return runCatching { String(f.readBytes(), Charsets.ISO_8859_1).contains("AudioMTKHardware") }.getOrDefault(false)
        }

        private val QCOM_PARK = listOf(
            "system/etc/permissions/qti_permissions.xml",
            "system/etc/permissions/com.qti.dpmframework.xml",
            "system/priv-app/com.qualcomm.location",
            "system/priv-app/qcrilmsgtunnel",
            "system/priv-app/dpmserviceapp",
            "system/priv-app/DiracAudioControlService",
            "system/app/DiracManager",
        )
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
