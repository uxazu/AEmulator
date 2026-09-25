package app.aemu.importer

import android.content.Context
import app.aemu.core.Engine
import app.aemu.core.GuestImage
import app.aemu.core.InitPlan
import app.aemu.core.PropArea
import app.aemu.core.VmPaths
import app.aemu.core.VmSettings
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Изучает разложенное дерево прошивки и выводит всё, что нужно для запуска:
 * версию, производителя и оболочку, движок, BOOTCLASSPATH, переменные и каталоги init,
 * план загрузки служб, путь карты памяти, разрешение экрана. Пишет шаблон области свойств.
 */
class Analyzer(private val ctx: Context, private val paths: VmPaths, private val ramdisk: List<BootImage.CpioEntry>?) {
    private val root = paths.root

    fun analyze(id: String, sourceName: String): GuestImage {
        val build = PropArea.parseProps(File(root, "system/build.prop").readText(Charsets.UTF_8))
        val deflt = File(root, "default.prop").takeIf { it.isFile }?.let { PropArea.parseProps(it.readText()) } ?: LinkedHashMap()
        val all = LinkedHashMap<String, String>().apply { putAll(deflt); build.forEach { (k, v) -> if (!(k.startsWith("ro.") && containsKey(k))) put(k, v) } }

        val api = all["ro.build.version.sdk"]?.toIntOrNull() ?: guessApi(all["ro.build.version.release"])
        val release = all["ro.build.version.release"] ?: "?"
        val brand = (all["ro.product.brand"] ?: all["ro.product.manufacturer"] ?: "").replaceFirstChar { it.uppercase() }
        val model = all["ro.product.model"] ?: all["ro.product.device"] ?: "Android"
        val skin = skin(all)
        val abi = all["ro.product.cpu.abi"] ?: "armeabi-v7a"

        // init.rc и его import’ы лежат в корне рамдиска
        val rcFiles = (root.listFiles()?.filter { it.isFile && it.name.endsWith(".rc") }?.sortedBy { if (it.name == "init.rc") 0 else 1 } ?: emptyList()) +
            (File(root, "system/etc/init").listFiles()?.filter { it.name.endsWith(".rc") } ?: emptyList())
        val rc = InitPlan.parse(rcFiles, all)
        // деревья из стендов HTC несут готовые dhd.bootclasspath / dhd.exports / dhd.dirs
        val dhdExports = File(root, "dhd.exports").takeIf { it.isFile }?.readLines()?.mapNotNull { l ->
            val i = l.indexOf('='); if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim() else null
        }?.toMap() ?: emptyMap()
        val dhdBcp = File(root, "dhd.bootclasspath").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val dhdDirs = File(root, "dhd.dirs").takeIf { it.isFile }?.readLines()?.map { it.trim() }?.filter { it.startsWith("/") } ?: emptyList()
        val bcp = rc.exports["BOOTCLASSPATH"]?.takeIf { it.isNotBlank() } ?: dhdBcp ?: dhdExports["BOOTCLASSPATH"]
            ?: bootclasspathFromOdex() ?: bootclasspathGuess(api)
        val exports = LinkedHashMap<String, String>(dhdExports).apply { putAll(rc.exports); remove("BOOTCLASSPATH") }
        val dirs = (rc.dirs + dhdDirs).filter { it.startsWith("/data") || it.startsWith("/cache") || it.startsWith("/mnt") || it.startsWith("/storage") }.distinct()
        if (api >= 17 && !exports.containsKey("EMULATED_STORAGE_TARGET") && exports["EXTERNAL_STORAGE"]?.startsWith("/storage/emulated") != false) {
            exports.putIfAbsent("EXTERNAL_STORAGE", "/storage/emulated/legacy")
            exports["EMULATED_STORAGE_SOURCE"] = "/mnt/shell/emulated"
            exports["EMULATED_STORAGE_TARGET"] = "/storage/emulated"
        }
        val plan = InitPlan.plan(rc, api, root)
        val vols = StorageList.read(root)
        val voldVols = vols.filter { !it.emulated }.map { it.mountPoint }
        val sdcard = vols.firstOrNull { it.primary && !it.emulated }?.mountPoint ?: exports["EXTERNAL_STORAGE"]?.takeIf { it.startsWith("/") }
            ?: when { api <= 13 -> "/mnt/sdcard"; api <= 16 -> "/storage/sdcard0"; else -> "/storage/emulated/legacy" }

        val warnings = ArrayList<String>()
        if (rcFiles.isEmpty() && dhdBcp == null) warnings += "В прошивке нет boot.img: план загрузки и BOOTCLASSPATH выведены эвристикой."
        if (abi.startsWith("arm64")) warnings += "64-битная прошивка: поддерживаются только 32-битные ARM."
        if (abi.startsWith("x86") || abi.startsWith("mips")) warnings += "Прошивка не для ARM ($abi) — не запустится."
        if (api >= 21) warnings += "Android ${release}: поддержка экспериментальная (ART, SELinux)."

        val density = all["ro.sf.lcd_density"]?.toIntOrNull() ?: 240
        val engine = Engine.forApi(api)
        val settings = when {
            engine == Engine.GB || api < 14 -> VmSettings(width = 480, height = 800, density = 240, fbHz = 60, touchHz = 60)
            // 540×960 — лучший баланс скорости и чёткости под эмуляцией; поменять можно в настройках
            density <= 160 -> VmSettings(width = 480, height = 800, density = 240)
            else -> VmSettings(width = 540, height = 960, density = 240)
        }

        writePropsTemplate(deflt, build, api, engine, rc.setprops)

        return GuestImage(
            id = id,
            name = (if (model.startsWith(brand, true)) model else "$brand $model").trim().ifBlank { "Android $release" } +
                if (skin != "AOSP" && !model.contains(skin, true)) " · $skin" else "",
            release = release,
            api = api,
            brand = brand,
            model = model,
            skin = skin,
            engine = engine,
            abi = abi,
            bootclasspath = bcp,
            exports = exports,
            dirs = dirs,
            services = plan,
            sdcardPath = sdcard,
            volumes = voldVols,
            settings = settings,
            createdAt = System.currentTimeMillis(),
            sourceName = sourceName,
            runtime = if (api >= 21) "art" else "dalvik",
            warnings = warnings,
            profileVersion = VERSION,
        )
    }

    companion object {
        const val VERSION = 13

        private val LMK_DEFAULTS = listOf(
            "ro.FOREGROUND_APP_ADJ" to "0", "ro.VISIBLE_APP_ADJ" to "1", "ro.PERCEPTIBLE_APP_ADJ" to "2",
            "ro.HEAVY_WEIGHT_APP_ADJ" to "3", "ro.SECONDARY_SERVER_ADJ" to "4", "ro.BACKUP_APP_ADJ" to "5",
            "ro.HOME_APP_ADJ" to "6", "ro.HIDDEN_APP_MIN_ADJ" to "7", "ro.EMPTY_APP_ADJ" to "15",
            "ro.FOREGROUND_APP_MEM" to "2048", "ro.VISIBLE_APP_MEM" to "3072", "ro.PERCEPTIBLE_APP_MEM" to "4096",
            "ro.HEAVY_WEIGHT_APP_MEM" to "4096", "ro.SECONDARY_SERVER_MEM" to "6144", "ro.BACKUP_APP_MEM" to "6144",
            "ro.HOME_APP_MEM" to "6144", "ro.HIDDEN_APP_MEM" to "7168", "ro.EMPTY_APP_MEM" to "8192",
        )
        private val NET_DEFAULTS = listOf(
            "net.tcp.buffersize.default" to "4096,87380,110208,4096,16384,110208",
            "net.tcp.buffersize.wifi" to "524288,1048576,2097152,262144,524288,1048576",
            "net.tcp.buffersize.umts" to "4094,87380,110208,4096,16384,110208",
            "net.tcp.buffersize.hspa" to "4094,87380,262144,4096,16384,262144",
            "net.tcp.buffersize.lte" to "524288,1048576,2097152,262144,524288,1048576",
            "net.tcp.buffersize.edge" to "4093,26280,35040,4096,16384,35040",
            "net.tcp.buffersize.gprs" to "4092,8760,11680,4096,8760,11680",
            "net.bt.name" to "Android",
            "net.change" to "net.dns1",
        )

        /** Пересчитывает профиль образа новым анализатором, сохраняя настройки пользователя. */
        fun refresh(ctx: Context, img: GuestImage): GuestImage {
            if (img.profileVersion >= VERSION) return img
            val paths = VmPaths(ctx, img.id)
            val fresh = runCatching { Analyzer(ctx, paths, null).analyze(img.id, img.sourceName) }.getOrNull() ?: return img
            val out = fresh.copy(
                name = img.name, settings = img.settings, createdAt = img.createdAt, sizeBytes = img.sizeBytes,
                lastBootMs = img.lastBootMs, bootCount = img.bootCount,
            )
            app.aemu.core.ImageStore.save(ctx, out)
            return out
        }
    }

    private fun guessApi(rel: String?): Int = when {
        rel == null -> 19
        rel.startsWith("2.2") -> 8; rel.startsWith("2.3") -> 10; rel.startsWith("4.0") -> 15
        rel.startsWith("4.1") -> 16; rel.startsWith("4.2") -> 17; rel.startsWith("4.3") -> 18
        rel.startsWith("4.4") -> 19; rel.startsWith("5.0") -> 21; rel.startsWith("5.1") -> 22
        rel.startsWith("6") -> 23; else -> 19
    }

    private fun skin(p: Map<String, String>): String {
        val man = (p["ro.product.manufacturer"] ?: p["ro.product.brand"] ?: "").lowercase()
        fun has(path: String) = File(root, path).exists()
        return when {
            p.containsKey("ro.miui.ui.version.name") || p.containsKey("ro.miui.ui.version.code") || has("system/app/MiuiHome.apk") || has("system/app/MiuiHome") -> "MIUI " + (p["ro.miui.ui.version.name"] ?: "")
            p.containsKey("ro.cm.version") -> "CyanogenMod ${p["ro.cm.version"]?.substringBefore('-')}"
            p.containsKey("ro.lineage.version") -> "LineageOS"
            p["ro.build.display.id"]?.contains("Flyme", true) == true -> "Flyme"
            p.containsKey("ro.build.version.emui") -> "EMUI"
            p.containsKey("ro.build.version.opporom") -> "ColorOS"
            (p["ro.product.brand"] ?: "").lowercase() == "google" || p["ro.build.tags"]?.contains("release-keys") == true && man.contains("samsung") && !has("system/framework/twframework.jar") && !has("system/app/TwLauncher.apk") && !has("system/app/SecLauncher2.apk") -> "AOSP"
            has("system/framework/twframework.jar") || has("system/app/TwLauncher.apk") || has("system/app/TwLauncher") || has("system/app/SecLauncher2.apk") || man.contains("samsung") -> "TouchWiz"
            man.contains("htc") || has("system/framework/com.htc.framework.jar") || has("system/framework/HTCExtension.jar") -> "HTC Sense"
            man.contains("motorola") -> "MOTOBLUR"
            man.contains("lge") || man.contains("lg") -> "LG Optimus UI"
            man.contains("sony") || man.contains("semc") -> "Xperia UI"
            man.contains("asus") -> "ZenUI"
            man.contains("huawei") -> "EMUI"
            man.contains("lenovo") -> "Vibe UI"
            else -> "AOSP"
        }.trim()
    }

    /** Порядок BOOTCLASSPATH из зависимостей odex (стоковые прошивки «одексированы»). */
    private fun bootclasspathFromOdex(): String? {
        // odex приложения зависит от всего BOOTCLASSPATH, а odex jar фреймворка — только от предшественников
        val apps = listOf("system/app", "system/priv-app").flatMap { d ->
            File(root, d).walkTopDown().maxDepth(2).filter { it.isFile && it.name.endsWith(".odex") }.take(6).toList()
        }
        var best: List<String> = emptyList()
        for (f in apps) {
            val deps = runCatching { odexDeps(f.readBytes()) }.getOrNull() ?: continue
            val jars = deps.filter { it.endsWith(".jar") }
            if (jars.size > best.size) best = jars
        }
        if (best.size >= 3) return best.joinToString(":")
        // иначе — самая длинная цепочка среди jar фреймворка
        File(root, "system/framework").listFiles()?.filter { it.name.endsWith(".odex") }?.forEach { f ->
            val jars = runCatching { odexDeps(f.readBytes()) }.getOrNull()?.filter { it.endsWith(".jar") } ?: return@forEach
            val self = "/system/framework/" + f.name.removeSuffix(".odex") + ".jar"
            val chain = jars + self
            if (chain.size > best.size) best = chain
        }
        return if (best.size >= 3) best.joinToString(":") else null
    }

    /** Разбор секции зависимостей odex (формат dey 036). */
    private fun odexDeps(b: ByteArray): List<String> {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        if (String(b, 0, 4) != "dey\n") return emptyList()
        val depsOff = bb.getInt(16)
        val depsLen = bb.getInt(20)
        if (depsOff <= 0 || depsOff + depsLen > b.size) return emptyList()
        var p = depsOff + 12 // modWhen, crc, dalvikBuild
        val count = bb.getInt(p); p += 4
        val out = ArrayList<String>()
        repeat(count) {
            val len = bb.getInt(p); p += 4
            out.add(depToJar(String(b, p, len - 1))); p += len
            p += 20 // SHA-1
        }
        return out
    }

    /** /system/framework/core.odex или /data/dalvik-cache/system@framework@core.jar@classes.dex → …/core.jar */
    private fun depToJar(d: String): String = when {
        d.contains("@classes.dex") -> "/" + d.substringAfterLast('/').removeSuffix("@classes.dex").replace('@', '/')
        d.endsWith(".odex") -> d.removeSuffix(".odex") + ".jar"
        else -> d
    }

    /** Эвристика: AOSP-порядок + вендорские jar, которых нет в списке приложений. */
    private fun bootclasspathGuess(api: Int): String {
        val fw = File(root, "system/framework")
        val names = fw.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val aosp = when {
            api <= 10 -> listOf("core.jar", "bouncycastle.jar", "ext.jar", "framework.jar", "android.policy.jar", "services.jar", "core-junit.jar")
            api <= 18 -> listOf("core.jar", "core-junit.jar", "bouncycastle.jar", "ext.jar", "framework.jar", "framework2.jar", "telephony-common.jar", "voip-common.jar", "mms-common.jar", "android.policy.jar", "services.jar", "apache-xml.jar", "webviewchromium.jar")
            api <= 20 -> listOf("core.jar", "conscrypt.jar", "okhttp.jar", "core-junit.jar", "bouncycastle.jar", "ext.jar", "framework.jar", "framework2.jar", "telephony-common.jar", "voip-common.jar", "mms-common.jar", "android.policy.jar", "services.jar", "apache-xml.jar", "webviewchromium.jar")
            else -> listOf("core-libart.jar", "conscrypt.jar", "okhttp.jar", "core-junit.jar", "bouncycastle.jar", "ext.jar", "framework.jar", "telephony-common.jar", "voip-common.jar", "ims-common.jar", "mms-common.jar", "android.policy.jar", "apache-xml.jar")
        }.filter { it in names }
        val vendorHints = listOf("twframework", "secframework", "sec_", "com.htc.", "HTC", "com.motorola", "com.lge", "lge", "semc", "com.sonyericsson", "miui", "com.qualcomm.qcrilhook", "qcom.fmradio", "framework-miui", "framework-ext", "cm.jar")
        val vendor = names.filter { n -> n.endsWith(".jar") && n !in aosp && vendorHints.any { n.startsWith(it) || n.contains(it) } && n != "services.jar" }
        return (aosp + vendor).joinToString(":") { "/system/framework/$it" }
    }

    /** Шаблон области свойств: default.prop → build.prop → наши умолчания (ro.* — «первый побеждает»). */
    private fun writePropsTemplate(deflt: Map<String, String>, build: Map<String, String>, api: Int, engine: Engine, rcProps: Map<String, String>) {
        val base = LinkedHashMap<String, String>()
        fun add(k: String, v: String) { if (!(k.startsWith("ro.") && base.containsKey(k))) base[k] = v }
        // то, что обычно выставляет init из командной строки ядра
        add("ro.secure", "0")
        add("ro.adb.secure", "0")
        add("ro.debuggable", "0")
        add("ro.serialno", "0123456789abcdef")
        add("ro.bootmode", "unknown")
        add("ro.baseband", "unknown")
        add("ro.bootloader", "unknown")
        add("ro.carrier", "unknown")
        add("ro.revision", "0")
        add("ro.factorytest", "0")
        add("ro.setupwizard.mode", "DISABLED")
        add("ro.kernel.android.checkjni", "0")
        add("ro.opengles.version", if (engine == Engine.KK) "131072" else "131072")
        add("keyguard.no_require_sim", "1")
        add("ro.radio.noril", "false")
        add("dalvik.vm.stack-trace-file", "/data/anr/traces.txt")
        for ((k, v) in deflt) add(k, v)
        for ((k, v) in build) add(k, v)
        for ((k, v) in rcProps) add(k, v)
        // то, что init.rc 2.x–4.0 выставляет для ActivityManager и сети: без этого system_server падает
        if (api <= 15) for ((k, v) in LMK_DEFAULTS) add(k, v)
        for ((k, v) in NET_DEFAULTS) add(k, v)
        val sysDefault = File(root, "system/default.prop")
        if (sysDefault.isFile) PropArea.parseProps(sysDefault.readText()).forEach { (k, v) -> add(k, v) }
        // ro.sf.lcd_density и ro.kernel.qemu выставляются при каждом запуске из настроек образа
        base.remove("ro.sf.lcd_density")
        base.remove("ro.kernel.qemu")
        base.remove("ro.kernel.qemu.gles")
        base["persist.sys.usb.config"] = "none"
        base["DEVICE_PROVISIONED"] = "1"
        if (!base.containsKey("dalvik.vm.heapsize")) base["dalvik.vm.heapsize"] = if (api >= 14) "256m" else "64m"
        PropArea.create(paths.propsTemplate).use { a ->
            var dropped = 0
            for ((k, v) in base) if (!a.put(k, v)) dropped++
            if (dropped > 0) android.util.Log.w("aemu", "свойств не вошло: $dropped")
        }
    }
}
