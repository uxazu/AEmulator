package app.aemu.core

import java.io.File

/**
 * Разбор init*.rc прошивки и построение плана загрузки: какие службы и в каком порядке запускать
 * вместо init, какие сокеты им создать, какие переменные окружения и каталоги нужны.
 * Порядок — как у настоящего Android: servicemanager → surfaceflinger → … → zygote.
 */
object InitPlan {

    data class RcService(
        val name: String,
        val argv: List<String>,
        val sockets: MutableMap<String, String> = LinkedHashMap(),
        var user: String = "root",
        var group: String = "root",
        var oneshot: Boolean = false,
        var disabled: Boolean = false,
        var cls: String = "default",
    )

    data class Rc(
        val services: Map<String, RcService>,
        val exports: Map<String, String>,
        val dirs: List<String>,
        /** setprop из секций init/boot (не из триггеров по свойствам) */
        val setprops: Map<String, String> = emptyMap(),
    )

    /** Разбирает набор .rc-файлов (корень рамдиска и /system/etc/init, если есть). */
    /**
     * @param props свойства прошивки: export внутри «on property:x=y» / «on early_property:x=y» учитывается,
     *   только если условие выполняется, и перекрывает безусловные (так init применяет их позже, чем on init) —
     *   MIUI/MTK так задают BOOTCLASSPATH для user-сборки.
     */
    fun parse(files: List<File>, props: Map<String, String> = emptyMap()): Rc {
        val services = LinkedHashMap<String, RcService>()
        val exports = LinkedHashMap<String, String>()
        val condExports = LinkedHashMap<String, String>()
        // режимы, в которые эмулятор не грузится: заводской, зарядка, восстановление
        val skip = Regex("""(?i)^(factory_init|meta_init|init[.]charging|init[.]recovery|lpm|FWUpgradeInit).*[.]rc$""")
        val dirs = ArrayList<String>()
        val setprops = LinkedHashMap<String, String>()
        for (f in files) {
            if (!f.isFile || skip.matches(f.name)) continue
            var cur: RcService? = null
            var propTrigger = false
            var condOk: Boolean? = null
            for (raw in f.readLines()) {
                val ln = raw.trim()
                if (ln.isEmpty() || ln.startsWith("#")) continue
                val t = ln.split(Regex("\\s+"))
                when (t[0]) {
                    "service" -> {
                        cur = if (t.size >= 3) RcService(t[1], t.drop(2)).also { services[t[1]] = it } else null
                    }
                    "on" -> {
                        cur = null
                        val conds = t.drop(1).filter { it.startsWith("property:") || it.startsWith("early_property:") }
                        propTrigger = conds.isNotEmpty()
                        condOk = if (conds.isEmpty()) null else conds.all { c ->
                            val kv = c.substringAfter(':'); val k = kv.substringBefore('='); val v = kv.substringAfter('=', "*")
                            props[k]?.let { v == "*" || it == v } ?: false
                        }
                    }
                    "import" -> cur = null
                    "setprop" -> if (cur == null && !propTrigger && t.size >= 3 && !t[2].startsWith("$")) setprops.putIfAbsent(t[1], t.drop(2).joinToString(" "))
                    "export" -> if (t.size >= 3) when (condOk) {
                        null -> exports[t[1]] = t.drop(2).joinToString(" ")
                        true -> condExports[t[1]] = t.drop(2).joinToString(" ")
                        false -> {}
                    }
                    "mkdir" -> if (t.size >= 2) dirs.add(t[1])
                    "socket" -> cur?.let { if (t.size >= 4) it.sockets[t[1]] = t[3] }
                    "user" -> cur?.let { if (t.size >= 2) it.user = t[1] }
                    "group" -> cur?.let { if (t.size >= 2) it.group = t[1] }
                    "oneshot" -> cur?.oneshot = true
                    "disabled" -> cur?.disabled = true
                    "class" -> cur?.let { if (t.size >= 2) it.cls = t[1] }
                }
            }
        }
        exports.putAll(condExports)
        return Rc(services, exports, dirs, setprops)
    }

    private val AID = mapOf(
        "root" to 0, "system" to 1000, "radio" to 1001, "bluetooth" to 1002, "graphics" to 1003,
        "input" to 1004, "audio" to 1005, "camera" to 1006, "log" to 1007, "compass" to 1008,
        "mount" to 1009, "wifi" to 1010, "adb" to 1011, "install" to 1012, "media" to 1013,
        "dhcp" to 1014, "sdcard_rw" to 1015, "vpn" to 1016, "keystore" to 1017, "usb" to 1018,
        "drm" to 1019, "mdnsr" to 1020, "gps" to 1021, "media_rw" to 1023, "shell" to 2000, "net_bt_admin" to 3001,
        "inet" to 3003, "nobody" to 9999,
    )

    /**
     * Порядок и правила запуска известных служб. Остальные службы init (вендорские демоны,
     * rild, vold, adbd, debuggerd…) не запускаем: их роль играет хост или они трогают железо.
     */
    private data class Rule(val name: String, val alt: List<String> = emptyList(), val waitSocket: String? = null,
                            val delayMs: Long = 0, val restart: Boolean = false, val minApi: Int = 0)

    private val ORDER = listOf(
        Rule("servicemanager", delayMs = 1500),
        Rule("surfaceflinger", delayMs = 2000, minApi = 14),
        Rule("healthd", delayMs = 500, minApi = 19),
        Rule("keystore", delayMs = 300),
        Rule("installd", waitSocket = "installd"),
        Rule("netd", waitSocket = "netd"),
        Rule("drm", alt = listOf("drmserver"), delayMs = 500),
        Rule("media", alt = listOf("mediaserver"), delayMs = 1000, restart = true),
        Rule("zygote"),
    )

    /** Строит план загрузки из разобранных rc-файлов; для отсутствующих служб берёт умолчания. */
    fun plan(rc: Rc, api: Int, root: File): List<GuestService> {
        val out = ArrayList<GuestService>()
        for (r in ORDER) {
            if (api in 1 until r.minApi) continue
            val found = (listOf(r.name) + r.alt).firstNotNullOfOrNull { rc.services[it] }
            val def = found?.let { toService(it, r) } ?: defaults(r.name, api, root)?.let {
                it.copy(waitSocket = r.waitSocket, delayMs = r.delayMs, restart = r.restart)
            } ?: continue
            if (!File(root, def.argv.first().removePrefix("/")).isFile) continue
            out.add(def)
        }
        // вендорские демоны: system_server прошивок Samsung/HTC/LG ждёт их службы (tvout и т.п.)
        val known = ORDER.flatMap { listOf(it.name) + it.alt }.toSet() + setOf("surfaceflinger")
        val extras = rc.services.values.filter { sv ->
            val bin = sv.argv.first()
            val base = bin.substringAfterLast('/')
            !sv.disabled && !sv.oneshot && sv.name !in known && sv.name !in SKIP && base !in SKIP_BIN &&
                // префиксом совпадают только явные «xxx_» и длинные имена: иначе «sh» отсекал бы shelld (MIUI) и т.п.
                SKIP_BIN.none { base == it || ((it.endsWith("_") || it.length >= 5) && base.startsWith(it)) } && !base.endsWith(".sh") &&
                (bin.startsWith("/system/bin/") || bin.startsWith("/system/xbin/") || bin.startsWith("/vendor/bin/")) &&
                File(root, bin.removePrefix("/")).isFile
        }.map { sv -> GuestService(sv.name, sv.argv, sv.sockets.toMap(), delayMs = 200, optional = true) }
        val z = out.indexOfFirst { it.name == "zygote" }.let { if (it < 0) out.size else it }
        out.addAll(z, extras)
        return out
    }

    /** Службы init, которые не запускаем: их роль играет хост, или они лезут в железо. */
    private val SKIP = setOf(
        "ueventd", "vold", "rild", "ril-daemon", "adbd", "debuggerd", "debuggerd64", "console", "dbus", "bluetoothd",
        "btld", "wpa_supplicant", "p2p_supplicant", "dhcpcd", "racoon", "mtpd", "qemud", "goldfish-setup", "goldfish-logcat",
        "dumpstate", "flash_recovery", "recovery", "redbend_ua", "sreadaheadd", "lpmkey", "playlpm", "charger", "macloader",
        "mfgloader", "wlandutservice", "bt_dut_cmd", "bootanim", "samsungani", "playsound", "sdcard", "fuse_sdcard0",
        "healthd", "logd", "lmkd", "watchdogd", "ril-daemon1", "ril-daemon2", "fota", "gpsd", "sensors", "akmd",
        "rmt_storage", "qmuxd", "netmgrd", "thermald", "mpdecision", "thermal-engine", "time_daemon", "diag",
    )
    private val SKIP_BIN = setOf(
        "logwrapper", "sh", "rild", "vold", "wpa_supplicant", "dhcpcd", "bluetoothd", "hciattach", "btld", "adbd",
        "sdcard", "gpsd", "gps", "akmd", "orientationd", "geomagneticd", "sensor", "charging", "playlpm", "bootanimation",
        "rmt_storage", "qmuxd", "netmgrd", "thermald", "mpdecision", "thermal-engine", "fm_", "wifi", "wifi_", "gps_", "hostapd", "mediaserver",
        "drexe", "npsmobex", "immvibed", "cbd", "smdexe", "ddexe", "mdm_helper", "rilproxy",
        // Qualcomm: камера, TrustZone, модем — лезут в /dev своего железа и сыплют ошибками
        "mm-qcamera", "qcamerasvr", "qseecomd", "bridgemgrd", "irsc_util", "mm-pp-daemon", "ks", "qcks", "efsks",
        "sensors.qcom", "location-mq", "xtwifi", "quipc", "usf_", "adsprpcd", "subsystem_ramdump",
    )

    private fun toService(s: RcService, r: Rule): GuestService {
        val name = when (r.name) { "media" -> "mediaserver"; "drm" -> "drmserver"; else -> r.name }
        return GuestService(
            name = name,
            argv = s.argv,
            sockets = s.sockets.toMap(),
            // службы идут от «root» гостя, как в эталонном стенде: qemu подделывает uid только по запросу
            uid = 0,
            gid = 0,
            waitSocket = r.waitSocket,
            delayMs = r.delayMs,
            restart = r.restart,
        )
    }

    /** Умолчания для образов без init.rc (например, только /system из прошивки). */
    fun defaults(name: String, api: Int, root: File): GuestService? = when (name) {
        "servicemanager" -> GuestService("servicemanager", listOf("/system/bin/servicemanager"))
        "surfaceflinger" -> if (api >= 14) GuestService("surfaceflinger", listOf("/system/bin/surfaceflinger")) else null
        "healthd" -> if (File(root, "sbin/healthd").isFile) GuestService("healthd", listOf("/sbin/healthd")) else null
        "keystore" -> GuestService("keystore", listOf("/system/bin/keystore", "/data/misc/keystore"))
        "installd" -> GuestService("installd", listOf("/system/bin/installd"), mapOf("installd" to "0600"))
        "netd" -> GuestService("netd", listOf("/system/bin/netd"),
            if (api >= 14) mapOf("netd" to "0660", "dnsproxyd" to "0660", "mdns" to "0660") else mapOf("netd" to "0660"))
        "media", "mediaserver" -> GuestService("mediaserver", listOf("/system/bin/mediaserver"))
        "drm", "drmserver" -> if (api >= 11) GuestService("drmserver", listOf("/system/bin/drmserver")) else null
        "zygote" -> {
            val app = when {
                File(root, "system/bin/app_process32").isFile && !File(root, "system/bin/app_process").isFile -> "/system/bin/app_process32"
                else -> "/system/bin/app_process"
            }
            GuestService("zygote", listOf(app, "-Xzygote", "/system/bin", "--zygote", "--start-system-server"), mapOf("zygote" to "0666"))
        }
        else -> null
    }

    /** Служба, которую гость может попросить через ctl.start (например bootanim). */
    fun optional(name: String, img: GuestImage, root: File): GuestService? = when (name) {
        "bootanim", "bootanimation" -> if (File(root, "system/bin/bootanimation").isFile)
            GuestService("bootanim", listOf("/system/bin/bootanimation"), uid = 1003, gid = 1003) else null
        else -> null
    }

    fun fallback(img: GuestImage, root: File): List<GuestService> =
        plan(Rc(emptyMap(), emptyMap(), emptyList()), img.api, root)
}
