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
    val input = InputService(paths, ::log)
    // 2.x пишет в /dev/eac через AudioHardwareGeneric на 44,1 кГц, HAL 4.x движка — на 48 кГц
    val audio = AudioOut(paths, ::log, if (img.api < 14) 44100 else AudioOut.RATE)
    val ril = RilStub(paths, ::log)
    val vold = VoldStub(paths, img.sdcardPath, ::log, others = img.volumes)
    var onFrame: (() -> Unit)? = null
    val frames = FrameBell(paths, ::log) { onFrame?.invoke() }
    private val events = EventsSink(paths, ::log)
    val net = NetProxy(ctx, paths, ::log)
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
            doBoot()
        } catch (t: Throwable) {
            failure = t.message ?: t.toString()
            log("✖ запуск прерван: $failure")
            setState(State.FAILED)
        }
    }

    private fun doBoot() {
        paths.bin.mkdirs()
        runCatching { logFile.writeText("") }
        log("образ «${img.name}»: ${img.displayVersion}, ${img.skin}, движок ${engine.title}")
        if (!File(paths.root, "system/framework").isDirectory) error("нет дерева прошивки")
        val qemu = paths.nativeBin(engine.qemu)
        if (!qemu.canExecute()) error("транслятор ${engine.qemu} не исполняемый")
        killLeftovers()

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
        overrides["ro.sf.lcd_density"] = s.density.toString()
        overrides["qemu.sf.lcd_density"] = s.density.toString()
        overrides["ro.aemu.host"] = "qemu-user"
        overrides["dalvik.vm.execution-mode"] = if (s.jit) "int:jit" else "int:fast"
        if (s.lowRam) overrides["ro.config.low_ram"] = "true"
        if (!s.hwui) overrides["debug.hwui.renderer"] = "skia"
        // PBO для текстур шрифтов (hwui 4.4+ на GLES3) ломают часть драйверов (Mali) — грузим текстуры напрямую
        if (HostInfo.gpu().contains("mali")) overrides["ro.hwui.use_gpu_pixel_buffers"] = "false"
        if (img.api in 19..20) overrides["persist.sys.dalvik.vm.lib"] = "libdvm.so"
        // зигота 4.4+ заранее открывает EGL; дети после fork наследуют соединение моста, и гостевая
        // библиотека моста уходит в бесконечную рекурсию (падение по стеку в каждом приложении)
        if (img.api >= 19) overrides["ro.zygote.disable_gl_preload"] = "1"
        // порты MIUI правят framework на smali так, что Dalvik-верификатор отвергает классы ядра
        // (зигота падает на VerifyError) — на телефонах они живут с выключенной проверкой байткода
        if (img.skin.contains("MIUI", true) && img.runtime == "dalvik") overrides["dalvik.vm.dexopt-flags"] = "v=n,o=a,m=y"
        // MediaTek: звук, камера, радио ждут «NVRAM готов» от nvram_daemon, которого у нас нет
        if (TreeFixer.isMtkAudio(paths.root) || File(paths.root, "system/bin/nvram_daemon").isFile) overrides["nvram_init"] = "Ready"
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
        if (!props.prepare(overrides)) error("область свойств не готова")
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
        ril.serve()
        vold.serve()
        // у vold производителей бывают дополнительные сокеты (Samsung: usbstorage, enc_report) — отвечаем «ладно»
        runCatching {
            val rc = InitPlan.parse(paths.root.listFiles()?.filter { it.isFile && it.name.endsWith(".rc") } ?: emptyList())
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
            log("звук: MediaTek — свой звуковой драйвер, звук эмулятора отключён")
        } else { audio.makeFifo(); audio.start() }
        events.start()
        val netCfg = if (s.netProxy) net.start() else GuestRunner.NetConfig()
        runner.sdcardHost = sd
        val r = GuestRunner(paths, img, netCfg.copy(glPath = if (s.gpu) "/dev/socket/gl" else null)).also {
            it.sdcardHost = sd
            // отладка: файл run/binder.verbose включает полную трассировку binder в binder-warn.log
            it.binderVerbose = File(paths.bin, "binder.verbose").exists()
        }
        runnerRef = r
        Keeper.hold(ctx, img.name)

        bootAt = System.currentTimeMillis()
        bootDoneAt = 0
        setState(State.BOOTING)

        // 4. binder
        paths.binderSock.delete()
        paths.creds.let { d -> d.listFiles()?.forEach { it.delete() }; d.mkdirs() }
        startBinder()
        if (!waitFor("сокет binder", 15_000) { paths.binderSock.exists() }) error("binderd не поднялся")

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
            if (svc.name == "zygote" && img.api >= 17 && engine == Engine.KK && File(paths.root, "system/framework/aemu-stubs.jar").isFile) {
                startService(GuestService("aemu-bt", listOf("/system/bin/app_process",
                    "-Djava.class.path=/system/framework/aemu-stubs.jar", "/system/bin", "app.aemu.stub.BtStub"), optional = true))
            }
            startService(svc)
            svc.waitSocket?.let { sock -> waitFor("сокет $sock", 20_000) { paths.socket(sock).exists() } }
            if (svc.delayMs > 0) Thread.sleep(svc.delayMs)
        }
        if (!glDone) glUp()
        log("система пошла: ${alive().joinToString()}")
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
                log("· binderd пошёл (служба :binder)")
            }
        }
    }

    private fun glUp() {
        if (!settings.gpu) { log("GPU-мост выключен — графика программная"); return }
        val (w, h) = if (engine == Engine.GB) 480 to 800 else settings.width to settings.height
        paths.glSock.parentFile?.mkdirs()
        paths.glSock.delete()
        when (engine) {
            Engine.GB -> {
                Slot.start(ctx, GlSlot::class.java,
                    listOf("glserverd", "-s", paths.glSock.absolutePath, "-fb", paths.fb.absolutePath, "-w", "$w", "-h", "$h"),
                    emptyMap(), paths.log("glserverd"))
                waitFor("сокет GL-моста", 10_000) { paths.glSock.exists() }
            }
            Engine.KK -> {
                if (surface == null) waitFor("поверхность экрана", 10_000) { surface != null }
                val sf = surface
                if (sf != null && GlBridge.start(sf, paths.glSock.absolutePath, paths.log("glbridge").absolutePath, w, h, false)) {
                    log("★ GPU-мост работает в приложении: кадр рисуется прямо в поверхность")
                    waitFor("сокет GL-моста", 5_000) { paths.glSock.exists() }
                    return
                }
                log("GPU-мост в приложении не поднялся — беру отдельный glserverd")
                spawn("glserverd", listOf(paths.nativeBin(engine.glserverd).absolutePath,
                    "-s", paths.glSock.absolutePath, "-fb", paths.fb.absolutePath,
                    "-notify", paths.frameSock.absolutePath, "-w", "$w", "-h", "$h"), emptyMap())
                waitFor("сокет GL-моста", 10_000) { paths.glSock.exists() }
            }
        }
    }

    val glInApp: Boolean get() = engine == Engine.KK && GlBridge.running()

    private fun startService(svc: GuestService, propsFile: File? = null) {
        if (!File(paths.root, svc.argv.first().removePrefix("/")).isFile) {
            log("· ${svc.name}: нет ${svc.argv.first()} — пропускаю")
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
        val p = pb.start()
        synchronized(procs) { procs[name] = p }
        pidOf(p)?.let { pgids.add(it) }
        log("· $name пошёл")
        Thread({
            runCatching {
                OutputStreamWriter(FileOutputStream(log, true)).use { w ->
                    w.write("\n=== $name ${stamp()} ===\n")
                    p.inputStream.bufferedReader().forEachLine { w.write(it); w.write("\n"); w.flush() }
                }
            }
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            if (!stopping) log("· $name завершился, код $code")
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
        if (!ok) log("⚠ не дождался: $what")
        return ok
    }

    // ------------------------------------------------------------------ присмотр

    private fun onProp(k: String, v: String) {
        if ((k == "sys.boot_completed" || k == "dev.bootcomplete") && v == "1" && bootDoneAt == 0L) {
            bootDoneAt = System.currentTimeMillis()
            log("★ система загружена за ${(bootDoneAt - bootAt) / 1000} с")
            setState(State.RUNNING)
            Thread { afterBoot() }.start()
        }
    }

    private fun onCtl(start: Boolean, svc: String) {
        val plan = img.services.ifEmpty { InitPlan.fallback(img, paths.root) }
        if (svc == "bootanim" || svc == "bootanimation") {
            if (!start) synchronized(procs) { procs.remove("bootanim")?.destroyForcibly() }
            return
        }
        val def = plan.firstOrNull { it.name == svc } ?: InitPlan.optional(svc, img, paths.root)
        if (def == null) { log("ctl.${if (start) "start" else "stop"} $svc — такой службы нет"); return }
        synchronized(procs) { procs.remove(svc) }?.destroyForcibly()
        if (start) Thread { runCatching { startService(def) } }.start()
    }

    private fun afterBoot() {
        val r = guestRunner
        r.run(listOf("/system/bin/svc", "power", "stayon", "true"), 60_000)
        TreeFixer(ctx, paths, img, ::log).noScreenSleep()
        val img2 = img.copy(lastBootMs = bootDoneAt - bootAt, bootCount = img.bootCount + 1)
        ImageStore.save(ctx, img2)
    }

    private fun watchdog() {
        Thread({
            var restarts = HashMap<String, Int>()
            while (!stopping) {
                Thread.sleep(3000)
                val dead = synchronized(procs) { procs.filter { !it.value.isAlive }.keys.toList() }
                for (name in dead) {
                    if (stopping) break
                    val p = synchronized(procs) { procs[name] } ?: continue
                    val code = runCatching { p.exitValue() }.getOrDefault(-1)
                    if (name == "zygote") {
                        if (state != State.FAILED && bootDoneAt == 0L) {
                            failure = "зигота завершилась (код $code)" + if (code == 137) " — система убита по памяти" else ""
                            log("✖ $failure"); setState(State.FAILED)
                        } else if (bootDoneAt > 0) {
                            log("✖ зигота упала (код $code) — перезапуск системы")
                            restartZygote()
                        }
                        synchronized(procs) { procs.remove(name) }
                        continue
                    }
                    val svc = img.services.firstOrNull { it.name == name }
                    val n = restarts.getOrDefault(name, 0)
                    // родной audio_policy производителя может падать с нашим HAL — подменяем на AOSP
                    if (name == "mediaserver" && code == 139 && n == 1) swapAudioPolicy()
                    if (svc != null && (svc.restart || name == "mediaserver") && code != 137 && code != 143 && n < 12) {
                        restarts[name] = n + 1
                        log("служба $name упала (код $code) — поднимаю заново (${n + 1}/12)")
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
                log("звук: MediaTek — вернул родную audio_policy")
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
            log("звук: audio_policy прошивки падает — поставил AOSP-версию (родная сохранена)")
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
        if (n > 0) log("добиты приложения прошлой зиготы: $n")
        runCatching { startService(z) }
    }

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
        log("останавливаю систему")
        runCatching { guestRunner.run(listOf("/system/bin/sync"), 5_000) }
        killAll()
        props.stop(); input.stop(); frames.stop(); ril.stop(); vold.stop(); audio.stop(); net.stop(); events.stop()
        extraStubs.forEach { it.stop() }; extraStubs.clear()
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
        if (n > 0) log("прибрано процессов от прошлого запуска: $n")
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
