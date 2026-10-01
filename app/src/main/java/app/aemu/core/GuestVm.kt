package app.aemu.core

import android.content.Context
import android.os.Process as AProcess
import android.os.SystemClock
import android.view.Surface
import dev.lk.dhd.BinderSlot
import dev.lk.dhd.GlSlot
import dev.lk.dhd.Slot
import dev.lk.m7sense.GlBridge
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.CopyOnWriteArrayList

/** Daemons system_server cannot live without: restarted even when init.rc marks them oneshot
 * (MIUI installd dies in the LBE security hook on start). */
private val ALWAYS_RESTART = setOf("mediaserver", "installd", "netd", "keystore")

/**
 * Одна запущенная виртуальная машина. Живёт в процессе :vm (одна на процесс: GL-мост нельзя
 * поднять дважды), заменяет собой init: готовит дерево, поднимает службы хоста (свойства, ввод,
 * звук, заглушки радио и накопителя), binderd, GL-мост и службы гостя в порядке загрузки Android.
 */
class GuestVm(val ctx: Context, val img: GuestImage) {
    enum class State { STOPPED, PREPARING, BOOTING, RUNNING, FAILED, STOPPING }

    val paths = VmPaths(ctx, img.id)
    val settings get() = img.settings
    val engine get() = img.engine

    @Volatile var state = State.STOPPED
        private set
    @Volatile var bootAt = 0L
        private set
    @Volatile var bootDoneAt = 0L
    /** the system reached the home screen at least once in this run: later crashes restart it instead of failing */
    @Volatile private var everBooted = false
    private var zygoteRestarts = 0
        private set
    @Volatile var failure: String? = null
        private set
    @Volatile var surface: Surface? = null
        set(v) { field = v; if (engine == Engine.KK) GlBridge.surface(v) }

    private val lines = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val stateListeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val logFile = File(paths.bin, "aemu.log")

    fun log(s: String) {
        val line = "${stamp()} $s"
        lines.add(line)
        while (lines.size > 2000) lines.removeAt(0)
        runCatching { logFile.appendText(line + "\n") }
        listeners.forEach { runCatching { it(line) } }
    }
    fun lines(): List<String> = lines.toList()
    fun onLog(l: (String) -> Unit) { listeners.add(l) }
    fun onState(l: (State) -> Unit) { stateListeners.add(l) }
    private fun setState(s: State) { state = s; stateListeners.forEach { runCatching { it(s) } } }

    val props = PropService(paths, ::log)
    /** ADB over the LAN, switched on from the VM menu */
    val adb = AdbServer(this)
    val input = InputService(paths, ::log)
    // 2.x пишет в /dev/eac через AudioHardwareGeneric на 44,1 кГц, HAL 4.x движка — на 48 кГц
    val audio = AudioOut(paths, ::log, if (img.api < 14) 44100 else AudioOut.RATE,
        if (TreeFixer.isMtkAudio(paths.root)) "dev/aemu_pcm" else "dev/eac")
    val ril = RilStub(paths, ::log, img.settings.imei.ifBlank { VmSettings.DEFAULT_IMEI }, img.api)
    val vold = VoldStub(paths, img.sdcardPath, ::log, others = img.volumes)
    var onFrame: (() -> Unit)? = null
    val frames = FrameBell(paths, ::log) { onFrame?.invoke() }
    private val events = EventsSink(paths, ::log)
    private val logd = LogdSink(paths, ::log)
    @Volatile private var lmk: GuestLmk? = null
    val net = NetProxy(ctx, paths, ::log)
    private val dns = DnsProxy(paths, img.api, ::log)
    val runner by lazy { GuestRunner(paths, img) }

    private val extraStubs = ArrayList<VoldStub>()
    private val procs = LinkedHashMap<String, Process>()
    private val pgids = LinkedHashSet<Int>()
    @Volatile private var stopping = false

    // ------------------------------------------------------------------ загрузка

    fun boot() {
        if (state == State.BOOTING || state == State.RUNNING || state == State.PREPARING) return
        stopping = false
        failure = null
        setState(State.PREPARING)
        try {
            if (recoveryMode) doRecovery() else doBoot()
        } catch (t: Throwable) {
            failure = t.message ?: t.toString()
            log("✖ boot aborted: $failure")
            setState(State.FAILED)
        }
    }

    /** Boot the firmware's recovery (RecoveryImage) instead of Android. */
    @Volatile var recoveryMode = false
    /** recovery framebuffer format for the screen view: 0 RGB565, 1 RGBA/RGBX_8888, 2 BGRA_8888 */
    @Volatile var recoveryFormat = 0

    private fun doRecovery() {
        paths.bin.mkdirs()
        runCatching { logFile.writeText("") }
        log("image \"${img.name}\": recovery mode")
        if (!RecoveryImage.installed(paths)) error("this firmware has no recovery installed")
        val qemu = paths.nativeBin(Engine.KK.qemu)
        if (!qemu.canExecute()) error("translator ${Engine.KK.qemu} is not executable")
        killLeftovers()
        HostNative.limitStackSafe()
        TreeFixer(ctx, paths, img, ::log).fixup()   // framebuffer, input node
        val sd = Sdcard.setup(ctx, paths, img, ::log)
        RecoveryImage.prepare(paths, sd)
        input.rateHz = settings.touchHz
        input.mtMode = settings.mtMode
        input.serve()
        // recovery's minui opens /dev/input/event0 through openat(), which qemu does not emulate:
        // a FIFO there gets the raw input_event stream straight from InputService
        runCatching {
            val ev = File(RecoveryImage.dir(paths), "dev/input/event0")
            ev.delete()
            android.system.Os.mkfifo(ev.path, "600".toInt(8))
            val fd = android.system.Os.open(ev.path, android.system.OsConstants.O_RDWR, 0)
            input.sink = java.io.FileOutputStream(fd)
        }.onFailure { log("recovery: no input channel: ${it.message}") }
        val (w, h) = runCatching { File(paths.root, "dhd.fbgeom").readText().trim().split(Regex("\\s+")).map { it.toInt() } }
            .getOrNull()?.takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: (settings.width to settings.height)
        val r = RecoveryImage.dir(paths)
        runCatching { File(r, "tmp/recovery.log").delete() }
        // debugging: run/recovery.strace traces the guest's system calls into run/recovery.trace
        val trace = if (File(paths.bin, "recovery.strace").exists()) listOf("-strace", "-D", File(paths.bin, "recovery.trace").absolutePath) else emptyList()
        val pb = ProcessBuilder(listOf(qemu.absolutePath, "-L", r.absolutePath, "-0", "/sbin/recovery") + trace + File(r, "sbin/recovery").absolutePath)
            .directory(r).redirectErrorStream(true).redirectOutput(File(paths.bin, "recovery.out"))
        pb.environment().clear()
        pb.environment().putAll(mapOf(
            "PATH" to "/sbin:/system/bin", "LD_LIBRARY_PATH" to ".:/sbin", "ANDROID_ROOT" to "/system", "ANDROID_DATA" to "/data",
            "EXTERNAL_STORAGE" to "/sdcard", "TZ" to "UTC",
            "LD_PRELOAD" to "/sbin/librecshim.so:/sbin/libaemushim.so", "AEMU_MOUNTS" to "/sdcard:/data:/system:/cache:/emmc:/external_sd:/usb-otg",
            "DHD_FB_W" to "$w", "DHD_FB_H" to "$h", "DHD_IN_W" to "$w", "DHD_IN_H" to "$h",
            "DHD_INPUT" to paths.inputSock.absolutePath,
        ))
        bootAt = System.currentTimeMillis()
        val p = pb.start()
        synchronized(procs) { procs["recovery"] = p }
        setState(State.RUNNING)
        log("recovery started (${w}x$h)")
        // pixel format the recovery draws in, from its own log ("Pixel format: BGRA_8888", TWRP)
        Thread {
            val rlog = File(r, "tmp/recovery.log")
            repeat(40) {
                if (!p.isAlive || stopping) return@Thread
                val t = runCatching { rlog.readText() }.getOrDefault("")
                val fmt = Regex("Pixel format: (BGRA_8888|RGBX_8888|RGBA_8888|RGB_565)").findAll(t).lastOrNull()?.groupValues?.get(1)
                if (fmt != null) {
                    recoveryFormat = when (fmt) { "BGRA_8888" -> 2; "RGB_565" -> 0; else -> 1 }
                    log("recovery draws $fmt")
                    return@Thread
                }
                Thread.sleep(250)
            }
        }.start()
        // reboot requests of the recovery (librecshim → /dev/aemu_power)
        val power = File(r, "dev/aemu_power")
        Thread {
            while (p.isAlive && !stopping) {
                if (power.length() > 0) {
                    val v = runCatching { power.readText().trim() }.getOrDefault("reboot"); power.writeText("")
                    powerRequest(v); break
                }
                Thread.sleep(500)
            }
        }.start()
        Thread {
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            if (stopping || powerHandled) return@Thread
            log("recovery exited (code $code), booting the system")
            onPower?.invoke(true, "")
        }.start()
    }

    private fun doBoot() {
        paths.bin.mkdirs()
        runCatching { logFile.writeText("") }
        log("image \"${img.name}\": ${img.displayVersion}, ${img.skin}, engine ${engine.title}")
        if (!File(paths.root, "system/framework").isDirectory) error("firmware tree missing")
        val qemu = paths.nativeBin(engine.qemu)
        if (!qemu.canExecute()) error("translator ${engine.qemu} is not executable")
        killLeftovers()
        HostNative.limitStackSafe()

        // 1. дерево
        val fixer = TreeFixer(ctx, paths, img, ::log)
        fixer.fixup()
        // политика звука уже подменялась раньше — обновить её файлы (обёртка/AOSP) до текущей версии
        if (File(paths.root, "system/.aemu-parked/system#lib#hw#audio_policy.default.so").isFile) swapAudioPolicy()
        GuestLog.clear(paths.root)
        val sd = Sdcard.setup(ctx, paths, img, ::log)

        // 2. свойства
        val s = settings
        val overrides = LinkedHashMap<String, String>()
        // 2.3: ro.kernel.qemu=1 включает в фреймворке режим эмулятора — ставим его только mediaserver
        overrides["ro.kernel.qemu"] = if (engine == Engine.GB) "0" else "1"
        overrides["ro.kernel.qemu.gles"] = if (s.gpu && (s.hwui || engine == Engine.GB)) "1" else "0"
        overrides["qemu.gles"] = if (s.gpu) "1" else "0"
        // 7.0+ libEGL ignores egl.cfg and takes the first libGLES_*.so it finds; without the bridge: the software one
        if (img.api >= 24 && !s.gpu) {
            overrides["ro.hardware.egl"] = "android"
        }
        overrides["ro.sf.lcd_density"] = s.density.toString()
        overrides["qemu.sf.lcd_density"] = s.density.toString()
        overrides["ro.aemu.host"] = "qemu-user"
        overrides["dalvik.vm.execution-mode"] = if (s.jit) "int:jit" else "int:fast"
        if (s.lowRam || s.ramMb in 1..768) overrides["ro.config.low_ram"] = "true"
        if (s.ramMb > 0) {
            // Dalvik heap sized from the guest RAM budget (like a real device of that class)
            val heap = (s.ramMb / 4).coerceIn(32, 512)
            overrides["dalvik.vm.heapsize"] = "${heap}m"
            overrides["dalvik.vm.heapgrowthlimit"] = "${(heap / 2).coerceAtLeast(24)}m"
        }
        if (!s.radio) overrides["ro.radio.noril"] = "true"
        if (!s.hwui) overrides["debug.hwui.renderer"] = "skia"
        // PBO для текстур шрифтов (hwui 4.4+ на GLES3) ломают часть драйверов (Mali) — грузим текстуры напрямую
        if (HostInfo.gpu().contains("mali")) overrides["ro.hwui.use_gpu_pixel_buffers"] = "false"
        if (img.api in 19..20) overrides["persist.sys.dalvik.vm.lib"] = "libdvm.so"
        // зигота 4.4+ заранее открывает EGL; дети после fork наследуют соединение моста, и гостевая
        // библиотека моста уходит в бесконечную рекурсию (падение по стеку в каждом приложении)
        if (img.api >= 19) overrides["ro.zygote.disable_gl_preload"] = "1"
        // ART first boot compiles every app with dex2oat under qemu (minutes, single-threaded): by default only
        // skip it (verify-none: ART verifies classes lazily when an app runs) and use several threads; "full compilation" in the settings keeps machine code
        if (img.api >= 21) {
            if (!s.fullDexopt) overrides["dalvik.vm.dex2oat-filter"] = "verify-none"
            // 7.0+: PackageManager picks the filter per reason from pm.dexopt.*
            if (img.api >= 24 && !s.fullDexopt) for (r in listOf("first-boot", "boot", "install", "bg-dexopt", "ab-ota", "core-app", "forced-dexopt", "nsys-library"))
                overrides["pm.dexopt.$r"] = "verify-none"
            overrides["dalvik.vm.dex2oat-flags"] = "-j" + Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            // 6.0 relocates the boot image to a random address (patchoat), which fails under qemu: keep it in place
            if (img.api >= 23) {
                overrides["dalvik.vm.extra-opts"] = "-Xnorelocate"
                // installd's dex2oat starts its own runtime: without this it tries patchoat too and fails every app
                overrides["dalvik.vm.dex2oat-flags"] = overrides["dalvik.vm.dex2oat-flags"] + " --runtime-arg -Xnorelocate"
            }
        }
        // порты MIUI правят framework на smali так, что Dalvik-верификатор отвергает классы ядра
        // (зигота падает на VerifyError) — на телефонах они живут с выключенной проверкой байткода
        if (img.skin.contains("MIUI", true) && img.runtime == "dalvik") overrides["dalvik.vm.dexopt-flags"] = "v=n,o=a,m=y"
        // MediaTek: звук, камера, радио ждут «NVRAM готов» от nvram_daemon, которого у нас нет
        if (TreeFixer.isMtkAudio(paths.root) || File(paths.root, "system/bin/nvram_daemon").isFile) overrides["nvram_init"] = "Ready"
        // TouchWiz 4.1+: TvOutService and Samsung EDS look for HDMI/MHL hardware and keep crashing or blocking boot
        if (img.api >= 16 && (img.skin.contains("TouchWiz", true) || img.skin.contains("One UI", true))) {
            overrides["ro.tvout.enable"] = "false"
            overrides["ro.SecEDS.enable"] = "false"
        }
        // своя панель кнопок у нас — экранную панель гостя (4.x) прячем, чтобы не дублировать
        overrides["qemu.hw.mainkeys"] = if (s.showNavBar) "1" else "0"
        // отладка: run/props.extra — строки ключ=значение поверх всего остального
        File(paths.bin, "props.extra").takeIf { it.isFile }?.readLines()?.forEach { l ->
            val k = l.substringBefore('=').trim()
            if (k.isNotEmpty() && !k.startsWith("#") && l.contains('=')) overrides[k] = l.substringAfter('=').trim()
        }
        overrides["net.dns1"] = "8.8.8.8"
        overrides["net.dns2"] = "1.1.1.1"
        // в прошивках старая база часовых поясов — передаём текущее смещение, а не название зоны
        overrides["persist.sys.timezone"] = gmtZone()
        if (!props.prepare(overrides)) error("property area not ready")
        fixer.skipPreBoot(props)
        fixer.noScreenSleep()
        props.onSet = { k, v -> onProp(k, v) }
        props.onCtl = { start, svc -> onCtl(start, svc) }

        // 3. службы хоста
        props.serve()
        input.rateHz = s.touchHz
        input.mtMode = s.mtMode
        input.serve()
        frames.serve()
        if (s.radio) ril.serve() else log("radio: emulation disabled in settings")
        vold.serve()
        // у vold производителей бывают дополнительные сокеты (Samsung: usbstorage, enc_report) — отвечаем «ладно»
        runCatching {
            // 7.0+ keeps vold (with its cryptd socket) in /system/etc/init
            val rcFiles = listOf(paths.root, File(paths.root, "system/etc/init"))
                .flatMap { d -> d.listFiles()?.filter { it.isFile && it.name.endsWith(".rc") } ?: emptyList() }
            val rc = InitPlan.parse(rcFiles)
            rc.services["vold"]?.sockets?.keys?.filter { it != "vold" }?.forEach { name ->
                VoldStub(paths, img.sdcardPath, ::log, name).also { it.serve(); extraStubs.add(it) }
            }
        }
        // MediaTek: libaudioflinger сам работает с устройством /dev/eac (звуковой драйвер MTK) и читает из
        // него — наш канал звука под тем же именем его вешает, а за ним mediaserver и всю систему.
        // Для MTK /dev/eac ведёт в /dev/null: без звука, но без зависания.
        if (TreeFixer.isMtkAudio(paths.root)) {
            runCatching {
                val eac = audio.fifo
                if (!isLinkTo(eac, "/dev/null")) { eac.delete(); android.system.Os.symlink("/dev/null", eac.absolutePath) }
                // голосовой канал модема (CCCI): без него AudioMTKHardware бесконечно ждёт модем
                for (n in listOf("ccci_pcm_rx", "ccci_pcm_tx")) {
                    val f = File(paths.root, "dev/$n")
                    if (!isLinkTo(f, "/dev/null")) { f.delete(); android.system.Os.symlink("/dev/null", f.absolutePath) }
                }
            }
            // our HAL replaces the MTK one (TreeFixer) and plays through its own channel
            audio.makeFifo(); audio.start()
            log("audio: MediaTek, emulator HAL via /dev/aemu_pcm")
        } else { audio.makeFifo(); audio.start() }
        events.start()
        if (img.api >= 21) logd.start()
        lmk = GuestLmk(paths.root, s.lowRam, ::log, s.ramMb).also { it.start() }
        val netCfg = if (s.netProxy) net.start() else GuestRunner.NetConfig()
        runner.sdcardHost = sd
        val r = GuestRunner(paths, img, netCfg.copy(glPath = if (s.gpu) "/dev/socket/gl" else null)).also {
            it.sdcardHost = sd
            // отладка: файл run/binder.verbose включает полную трассировку binder в binder-warn.log
            it.binderVerbose = File(paths.bin, "binder.verbose").exists()
            it.userQemuArgs = s.qemuArgs
        }
        runnerRef = r
        Keeper.hold(ctx, img.name)

        bootAt = System.currentTimeMillis()
        bootDoneAt = 0
        everBooted = false; zygoteRestarts = 0
        setState(State.BOOTING)

        // 4. binder
        paths.binderSock.delete()
        paths.creds.let { d -> d.listFiles()?.forEach { it.delete() }; d.mkdirs() }
        startBinder()
        if (!waitFor("binder socket", 15_000) { paths.binderSock.exists() }) error("binderd failed to start")

        // 5. службы гостя по плану из init.rc прошивки
        val plan = img.services.ifEmpty { InitPlan.fallback(img, paths.root) }
        var glDone = false
        for (svc in plan) {
            if (stopping) return
            // GL-мост должен ждать гостя до SurfaceFlinger/zygote
            if (!glDone && (svc.name == "surfaceflinger" || svc.name == "zygote" || svc.name == "bootanim")) {
                glUp(); glDone = true
            }
            // 4.2+: заглушка bluetooth_manager до зиготы (system_server с ro.kernel.qemu=1 свою не поднимает)
            if (svc.name == "zygote" && img.api >= 9 && engine == Engine.KK && File(paths.root, "system/framework/aemu-stubs.jar").isFile) {
                // 2.3–4.1 look up "bluetooth" instead; without it BluetoothAdapter is null and the vendor's Bluetooth apps crash
                val btName = if (img.api >= 17) "bluetooth_manager" else "bluetooth"
                startService(GuestService("aemu-bt", listOf("/system/bin/app_process",
                    "-Djava.class.path=/system/framework/aemu-stubs.jar", "/system/bin", "app.aemu.stub.BtStub", btName), optional = true))
            }
            // 5.0–7.x: a registered network, otherwise ConnectivityService says "no active network" and browsers stay offline
            if (svc.name == "zygote" && img.api in 21..25 && engine == Engine.KK && File(paths.root, "system/framework/aemu-stubs.jar").isFile) {
                synchronized(procs) { procs.remove("aemu-net") }?.let { runCatching { it.destroyForcibly() } }
                startService(GuestService("aemu-net", listOf("/system/bin/app_process",
                    "-Djava.class.path=/system/framework/aemu-stubs.jar", "/system/bin", "app.aemu.stub.NetStub"), optional = true))
            }
            startService(svc)
            svc.waitSocket?.let { sock -> waitFor("socket $sock", 20_000) { paths.socket(sock).exists() } }
            if (svc.delayMs > 0) Thread.sleep(svc.delayMs)
        }
        if (!glDone) glUp()
        log("system started: ${alive().joinToString()}")
        watchdog()
    }

    @Volatile private var runnerRef: GuestRunner? = null
    val guestRunner: GuestRunner get() = runnerRef ?: runner

    private fun startBinder() {
        val sock = paths.binderSock.absolutePath
        when (engine) {
            Engine.KK -> spawn("binderd", listOf(paths.nativeBin(engine.binderd).absolutePath, "-s", sock), emptyMap())
            Engine.GB -> if (File(paths.bin, "binderd.kk").exists()) {
                // эксперимент: более новый binderd движка KK
                spawn("binderd", listOf(paths.nativeBin(Engine.KK.binderd).absolutePath, "-s", sock), emptyMap())
            } else {
                Slot.start(ctx, BinderSlot::class.java, listOf("binderd", "-s", sock), emptyMap(), paths.log("binderd"))
                log("· binderd started (:binder service)")
            }
        }
    }

    private fun glUp() {
        if (!settings.gpu) { log("GPU bridge off, software rendering"); return }
        val (w, h) = if (engine == Engine.GB) 480 to 800 else settings.width to settings.height
        paths.glSock.parentFile?.mkdirs()
        paths.glSock.delete()
        when (engine) {
            Engine.GB -> {
                Slot.start(ctx, GlSlot::class.java,
                    listOf("glserverd", "-s", paths.glSock.absolutePath, "-fb", paths.fb.absolutePath, "-w", "$w", "-h", "$h"),
                    emptyMap(), paths.log("glserverd"))
                waitFor("GL bridge socket", 10_000) { paths.glSock.exists() }
            }
            Engine.KK -> {
                if (surface == null) waitFor("screen surface", 10_000) { surface != null }
                val sf = surface
                HostNative.relaxFdsanSafe()
                // 5.0+: hwui's GL stream has crashed the in-app bridge (SIGSEGV in serve_one_raw took the whole app down);
                // the standalone glserverd keeps such a crash inside its own process
                if (img.api < 21 && sf != null && GlBridge.start(sf, paths.glSock.absolutePath, paths.log("glbridge").absolutePath, w, h, false)) {
                    log("★ GPU bridge running in-app: frames render straight to the surface")
                    waitFor("GL bridge socket", 5_000) { paths.glSock.exists() }
                    return
                }
                log(if (img.api >= 21) "GPU bridge: standalone glserverd" else "in-app GPU bridge failed, falling back to standalone glserverd")
                spawn("glserverd", listOf(paths.nativeBin(engine.glserverd).absolutePath,
                    "-s", paths.glSock.absolutePath, "-fb", paths.fb.absolutePath,
                    "-notify", paths.frameSock.absolutePath, "-w", "$w", "-h", "$h"), emptyMap())
                waitFor("GL bridge socket", 10_000) { paths.glSock.exists() }
            }
        }
    }

    val glInApp: Boolean get() = engine == Engine.KK && GlBridge.running()

    private fun startService(svc: GuestService, propsFile: File? = null) {
        if (!File(paths.root, svc.argv.first().removePrefix("/")).isFile) {
            log("· ${svc.name}: ${svc.argv.first()} missing, skipping")
            return
        }
        val extra = LinkedHashMap<String, String>()
        for ((sock, mode) in svc.sockets) extra["DHD_SOCK_$sock"] = "${paths.socket(sock).absolutePath},$mode"
        if (svc.uid != 0) { extra["DHD_UID"] = svc.uid.toString(); extra["DHD_GID"] = svc.gid.toString() }
        var props = propsFile
        // звук: с ro.kernel.qemu=1 mediaserver берёт эмуляторный вывод в /dev/eac
        if (svc.name == "mediaserver") props = this.props.privateCopy("media", mapOf("ro.kernel.qemu" to "1"))
        val argv = if (svc.name == "zygote" && !settings.jit && img.runtime == "dalvik")
            svc.argv.take(1) + "-Xint:fast" + svc.argv.drop(1) else svc.argv
        // отладка: файл run/strace.<служба> включает трассировку системных вызовов qemu
        if (File(paths.bin, "strace.${svc.name}").exists()) extra["QEMU_STRACE"] = "1"
        spawn(svc.name, guestRunner.cmdline(argv, props), guestRunner.env(extra))
    }

    private fun spawn(name: String, cmd: List<String>, env: Map<String, String>) {
        val log = paths.log(name)
        val pb = ProcessBuilder(cmd).directory(paths.bin).redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(if (env.isEmpty()) mapOf("PATH" to "/system/bin") else env)
        // 7.0+ zygote refuses to fork with pipes among its descriptors (it only reopens files and devices):
        // its stdio go straight to the log file and /dev/null, which the guest shim shows as /dev/null
        val direct = name == "zygote" && img.api >= 24
        if (direct) {
            runCatching { log.appendText("\n=== $name ${stamp()} ===\n") }
            pb.redirectInput(File("/dev/null")).redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        }
        val p = pb.start()
        synchronized(procs) { procs[name] = p }
        pidOf(p)?.let { pgids.add(it) }
        log("· $name started")
        Thread({
            if (!direct) runCatching {
                OutputStreamWriter(FileOutputStream(log, true)).use { w ->
                    w.write("\n=== $name ${stamp()} ===\n")
                    p.inputStream.bufferedReader().forEachLine { w.write(it); w.write("\n"); w.flush() }
                }
            }
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            if (!stopping) log("· $name exited, code $code")
        }, "log-$name").apply { isDaemon = true; start() }
    }

    private fun alive(): List<String> = synchronized(procs) { procs.filterValues { it.isAlive }.keys.toList() }

    private fun waitFor(what: String, ms: Long, cond: () -> Boolean): Boolean {
        val until = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < until && !stopping) {
            if (cond()) return true
            Thread.sleep(100)
        }
        val ok = cond()
        if (!ok) log("⚠ timed out waiting for: $what")
        return ok
    }

    // ------------------------------------------------------------------ присмотр

    /** Guest asked to reboot / power off ("reboot", "reboot,recovery", "shutdown"); the UI restarts or stops the VM. */
    @Volatile var onPower: ((reboot: Boolean, reason: String) -> Unit)? = null
    @Volatile private var powerHandled = false

    private fun powerRequest(v: String) {
        if (powerHandled || v.isEmpty()) return
        powerHandled = true
        val reboot = v.startsWith("reboot")
        val reason = v.substringAfter(',', "")
        log("guest requested ${if (reboot) "reboot" else "power off"}${if (reason.isNotEmpty()) " ($reason)" else ""}")
        if (reason == "recovery" && !RecoveryImage.installed(paths)) log("no recovery installed, booting the system again")
        onPower?.invoke(reboot, reason)
    }

    private fun onProp(k: String, v: String) {
        if (k == "sys.powerctl") { powerRequest(v); return }
        if ((k == "sys.boot_completed" || k == "dev.bootcomplete") && v == "1" && bootDoneAt == 0L) {
            bootDoneAt = System.currentTimeMillis()
            everBooted = true
            log("★ system booted in ${(bootDoneAt - bootAt) / 1000} s")
            setState(State.RUNNING)
            Thread { afterBoot() }.start()
        }
    }

    private fun onCtl(start: Boolean, svc: String) {
        val plan = img.services.ifEmpty { InitPlan.fallback(img, paths.root) }
        if (svc == "bootanim" || svc == "bootanimation") {
            if (!start) {
                // 4.x sets service.bootanim.exit first and the animation quits on its own, closing its audio.
                // Killing it outright left Samsung's boot sound track half-open: mediaserver hung and every
                // app creating a sound (the phone process, ToneGenerator) got an ANR.
                val p = synchronized(procs) { procs.remove("bootanim") } ?: return
                Thread {
                    runCatching { if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
                }.start()
                return
            }
            // below 4.1 (2.3, MIUI ICS) the animation does not quit by itself: killed, it leaves the GL bridge on its last frame
            if (img.api < 16 || bootDoneAt != 0L || synchronized(procs) { procs.containsKey("bootanim") }) return
            InitPlan.optional("bootanim", img, paths.root)?.let { def ->
                Thread { runCatching { startService(def) } }.start()
                log("boot animation started")
            }
            return
        }
        val def = plan.firstOrNull { it.name == svc } ?: InitPlan.optional(svc, img, paths.root)
        if (def == null) { log("ctl.${if (start) "start" else "stop"} $svc: no such service"); return }
        synchronized(procs) { procs.remove(svc) }?.destroyForcibly()
        if (start) Thread { runCatching { startService(def) } }.start()
    }

    private fun afterBoot() {
        val r = guestRunner
        r.run(listOf("/system/bin/sh", "-c", "export PATH=/system/bin:/system/xbin:\$PATH; svc power stayon true"), 60_000)
        TreeFixer(ctx, paths, img, ::log).noScreenSleep()
        disableBrokenComponents()
        val img2 = img.copy(lastBootMs = bootDoneAt - bootAt, bootCount = img.bootCount + 1)
        ImageStore.save(ctx, img2)
    }

    /**
     * 2.3–4.1 Google network location (in Play services and in Maps) calls TelephonyRegistry.listen with notifyNow at
     * start: the reply of that call is lost between the nested oneway callback and the reply, and the service dies with
     * "Unknown exception code" and a "has stopped" dialog on every boot. Nothing here needs network location.
     */
    private fun disableBrokenComponents() {
        if (img.api !in 9..16) return
        val marker = File(paths.root, "data/.aemu-components-disabled2")
        if (marker.isFile) return
        val list = listOf(
            "com.google.android.location/com.google.android.location.NetworkLocationService",
            "com.google.android.location/com.google.android.location.internal.server.NetworkLocationService",
            "com.google.android.apps.maps/com.google.android.location.internal.server.NetworkLocationService",
            "com.google.android.apps.maps/com.google.android.location.NetworkLocationService",
        )
        // PATH of 2.3–4.x starts with /sbin: the pm script's "app_process" is then looked up there and qemu gives up
        for (c in list) runCatching { guestRunner.run(listOf("/system/bin/sh", "-c", "export PATH=/system/bin:/system/xbin:\$PATH; pm disable $c"), 120_000) }
        runCatching { marker.writeText("1") }
    }

    /** qemu maps a guest path into the tree only if the file exists there, so the shim's request file is pre-made */
    private val power: File get() = File(paths.root, "dev/aemu_power")

    private fun watchdog() {
        runCatching { power.writeText(""); power.setWritable(true, false) }
        Thread({
            var restarts = HashMap<String, Int>()
            while (!stopping) {
                Thread.sleep(3000)
                if (power.length() > 0) { val v = runCatching { power.readText().trim() }.getOrDefault("reboot"); power.writeText(""); powerRequest(v) }
                if (img.api >= 21) runCatching { dns.ensure() }
                val dead = synchronized(procs) { procs.filter { !it.value.isAlive }.keys.toList() }
                for (name in dead) {
                    if (stopping) break
                    val p = synchronized(procs) { procs[name] } ?: continue
                    val code = runCatching { p.exitValue() }.getOrDefault(-1)
                    if (name == "zygote") {
                        if (state != State.FAILED && bootDoneAt == 0L && !(everBooted && zygoteRestarts < 6)) {
                            failure = "zygote exited (code $code)" + if (code == 137) " — system killed by low memory" else ""
                            if (code == 137 && guestLogHas("No original dex files found"))
                                failure = "system_server cannot start: the precompiled code of the framework (system/framework/oat) is missing from this container — import the firmware again"
                            log("✖ $failure"); setState(State.FAILED)
                        } else if (bootDoneAt > 0 || everBooted) {
                            zygoteRestarts++
                            log("✖ zygote crashed (code $code), restarting system ($zygoteRestarts)")
                            restartZygote()
                        }
                        synchronized(procs) { procs.remove(name) }
                        continue
                    }
                    val svc = img.services.firstOrNull { it.name == name }
                    val n = restarts.getOrDefault(name, 0)
                    // init restarts surfaceflinger and then zygote ("onrestart restart zygote"); without that a
                    // crashed compositor leaves the last frame on screen forever
                    if (name == "surfaceflinger" && svc != null && bootDoneAt > 0 && n < 12) {
                        restarts[name] = n + 1
                        log("✖ surfaceflinger crashed (code $code), restarting it and the system (${n + 1}/12)")
                        synchronized(procs) { procs.remove(name) }
                        runCatching { startService(svc) }
                        synchronized(procs) { procs["zygote"] }?.let { z -> pidOf(z)?.let { runCatching { AProcess.sendSignal(it, 9) } } }
                        continue
                    }
                    // родной audio_policy производителя может падать с нашим HAL — подменяем на AOSP
                    if (name == "mediaserver" && code == 139 && n == 1) swapAudioPolicy()
                    if (svc != null && (svc.restart || name in ALWAYS_RESTART) && code != 137 && code != 143 && n < 12) {
                        restarts[name] = n + 1
                        log("service $name crashed (code $code), restarting (${n + 1}/12)")
                        synchronized(procs) { procs.remove(name) }
                        runCatching { startService(svc) }
                    } else synchronized(procs) { procs.remove(name) }
                }
            }
        }, "vm-watchdog").apply { isDaemon = true; start() }
    }

    /**
     * Ставит политику AOSP 4.3 за обёрткой: родная сохраняется, лишние потоки производителя
     * (Samsung: 0..14 против 0..9 в AOSP) обёртка сводит к MUSIC, чтобы AOSP-код не писал за массив.
     */
    private fun swapAudioPolicy() {
        if (img.api < 16) return
        val f = File(paths.root, "system/lib/hw/audio_policy.default.so")
        val aosp = File(paths.root, "system/lib/libaemu_apaosp.so")
        val parked = File(paths.root, "system/.aemu-parked/system#lib#hw#audio_policy.default.so")
        // MediaTek: AudioPolicyService переделан (другие ops и слоты), политика AOSP в нём падает.
        // Родная политика MTK падала только из-за проверки DRVB, которую теперь снимает TreeFixer
        if (TreeFixer.isMtkAudio(paths.root)) {
            if (parked.isFile) runCatching {
                parked.copyTo(f, overwrite = true); parked.delete(); aosp.delete()
                log("audio: MediaTek, restored stock audio_policy")
            }
            return
        }
        fun assetSize(n: String) = runCatching { ctx.assets.openFd("engines/kk/$n").use { it.length } }.getOrDefault(-1L)
        if (aosp.length() == assetSize("audio_policy.default.so") && f.length() == assetSize("audio_policy.wrap.so")) return
        runCatching {
            parked.parentFile?.mkdirs()
            if (!parked.isFile && f.isFile) f.copyTo(parked, overwrite = true)
            ctx.assets.open("engines/kk/audio_policy.default.so").use { i -> aosp.outputStream().use { o -> i.copyTo(o) } }
            ctx.assets.open("engines/kk/audio_policy.wrap.so").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            log("audio: firmware audio_policy crashes, installed AOSP one (stock kept)")
        }
    }

    private fun restartZygote() {
        val z = img.services.firstOrNull { it.name == "zygote" } ?: return
        bootDoneAt = 0
        setState(State.BOOTING)
        paths.socket("zygote").delete()
        // на настоящем ядре дети зиготы гибнут вместе с system_server; здесь они остаются жить и
        // держат ссылки на мёртвые службы (телефония отказывает новому system_server в правах)
        val n = killZygoteChildren()
        if (n > 0) log("killed apps of previous zygote: $n")
        // healthd keeps the battery listener of the dead system_server and stops answering the new one:
        // BatteryService.onStart then blocks in registerListener forever
        img.services.firstOrNull { it.name == "healthd" }?.let { h ->
            synchronized(procs) { procs.remove("healthd") }?.let { runCatching { it.destroyForcibly() } }
            runCatching { startService(h) }
        }
        runCatching { startService(z) }
    }

    private fun guestLogHas(text: String): Boolean = runCatching {
        val f = File(paths.root, "dev/log/main")
        val len = f.length()
        java.io.RandomAccessFile(f, "r").use { r ->
            val n = minOf(len, 400_000L).toInt(); r.seek(len - n)
            val b = ByteArray(n); r.readFully(b)
            String(b, Charsets.ISO_8859_1).contains(text)
        }
    }.getOrDefault(false)

    private fun killZygoteChildren(): Int {
        val marker = "/images/${img.id}/"
        var n = 0
        File("/proc").listFiles()?.forEach { d ->
            val pid = d.name.toIntOrNull() ?: return@forEach
            val cmd = runCatching { String(File(d, "cmdline").readBytes(), Charsets.ISO_8859_1) }.getOrNull() ?: return@forEach
            // app_process с aemu-stubs.jar — наша заглушка bluetooth_manager, не приложение зиготы
            if (cmd.contains(marker) && cmd.contains("/system/bin/app_process") && !cmd.contains("aemu-stubs.jar")) {
                runCatching { AProcess.sendSignal(pid, 9) }; n++
            }
        }
        return n
    }

    // ------------------------------------------------------------------ остановка

    fun stop() {
        if (state == State.STOPPED) return
        stopping = true
        setState(State.STOPPING)
        log("stopping system")
        runCatching { guestRunner.run(listOf("/system/bin/sync"), 5_000) }
        killAll()
        adb.stop(); props.stop(); input.stop(); frames.stop(); ril.stop(); vold.stop(); audio.stop(); net.stop(); dns.stop(); events.stop(); logd.stop()
        extraStubs.forEach { it.stop() }; extraStubs.clear()
        lmk?.stop(); lmk = null
        Keeper.release(ctx)
        setState(State.STOPPED)
    }

    private fun killAll() {
        val list = synchronized(procs) { procs.values.toList() }
        for (g in pgids) runCatching { android.system.Os.kill(-g, android.system.OsConstants.SIGKILL) }
        list.forEach { runCatching { it.destroyForcibly() } }
        synchronized(procs) { procs.clear() }
        pgids.clear()
        killLeftovers()
    }

    /** Добивает процессы гостя, оставшиеся от прошлых запусков (qemu, binderd, dhdrun). */
    fun killLeftovers(): Int {
        val mine = AProcess.myPid()
        var n = 0
        File("/proc").listFiles()?.forEach { d ->
            val pid = d.name.toIntOrNull() ?: return@forEach
            if (pid == mine) return@forEach
            val cmd = runCatching { String(File(d, "cmdline").readBytes(), Charsets.ISO_8859_1).replace('\u0000', ' ') }.getOrNull() ?: return@forEach
            val ours = (cmd.contains("/images/") && (cmd.contains("libqemu") || cmd.contains("libbinderd") || cmd.contains("libdhdrun") || cmd.contains("libglserverd"))) ||
                cmd.startsWith("${ctx.packageName}:binder") || cmd.startsWith("${ctx.packageName}:gl")
            if (ours) { runCatching { AProcess.sendSignal(pid, 9) }; n++ }
        }
        if (n > 0) log("cleaned up processes from previous run: $n")
        return n
    }

    // ------------------------------------------------------------------ служебное

    fun bootSeconds(): Long = if (bootAt == 0L) 0 else ((if (bootDoneAt > 0) bootDoneAt else System.currentTimeMillis()) - bootAt) / 1000

    fun guestShell(cmd: String, timeoutMs: Long = 60_000): String =
        guestRunner.run(listOf("/system/bin/sh", "-c", cmd), timeoutMs).second

    fun installApk(apkOnSdcard: String): String =
        guestRunner.run(listOf("/system/bin/pm", "install", "-r", apkOnSdcard), 600_000).second

    private fun pidOf(p: Process): Int? {
        var c: Class<*>? = p.javaClass
        while (c != null) {
            val f = c.declaredFields.firstOrNull { it.name == "pid" }
            if (f != null) { f.isAccessible = true; return f.getInt(p) }
            c = c.superclass
        }
        return null
    }

    private fun gmtZone(): String {
        val h = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000
        return if (h == 0) "Etc/GMT" else "Etc/GMT" + (if (h > 0) "-$h" else "+${-h}")
    }

    private fun isLinkTo(f: File, target: String) = runCatching { android.system.Os.readlink(f.absolutePath) == target }.getOrDefault(false)

    private fun stamp() = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
}
