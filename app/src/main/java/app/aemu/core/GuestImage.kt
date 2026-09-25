package app.aemu.core

import org.json.JSONArray
import org.json.JSONObject

/** Настройки конкретного образа, которые пользователь может менять. */
data class VmSettings(
    val width: Int = 540,
    val height: Int = 960,
    val density: Int = 240,
    /** аппаратная отрисовка через GPU телефона (иначе программный растеризатор прошивки) */
    val gpu: Boolean = true,
    /** hwui в гостевых приложениях (4.x) */
    val hwui: Boolean = true,
    /** JIT Dalvik; без него стабильнее, но медленнее */
    val jit: Boolean = true,
    /** частота развёртки гостя, Гц */
    val fbHz: Int = 60,
    /** касаний в секунду, которые шлём гостю */
    val touchHz: Int = 60,
    /** HTTP/HTTPS-прокси с современным TLS для старых браузеров */
    val netProxy: Boolean = true,
    val lowRam: Boolean = false,
    val showFrame: Boolean = false,
    val showNavBar: Boolean = true,
    val keepScreenOn: Boolean = true,
    val mtMode: Int = 0,
    /** старый движок для 2.x (GL через pbuffer, только GLES 1.x) — запасной вариант */
    val legacyEngine: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("width", width).put("height", height).put("density", density)
        .put("gpu", gpu).put("hwui", hwui).put("jit", jit).put("fbHz", fbHz)
        .put("touchHz", touchHz).put("netProxy", netProxy).put("lowRam", lowRam)
        .put("showFrame", showFrame).put("showNavBar", showNavBar)
        .put("keepScreenOn", keepScreenOn).put("mtMode", mtMode).put("legacyEngine", legacyEngine)

    companion object {
        fun fromJson(o: JSONObject?): VmSettings {
            if (o == null) return VmSettings()
            val d = VmSettings()
            return VmSettings(
                width = o.optInt("width", d.width),
                height = o.optInt("height", d.height),
                density = o.optInt("density", d.density),
                gpu = o.optBoolean("gpu", d.gpu),
                hwui = o.optBoolean("hwui", d.hwui),
                jit = o.optBoolean("jit", d.jit),
                fbHz = o.optInt("fbHz", d.fbHz),
                touchHz = o.optInt("touchHz", d.touchHz),
                netProxy = o.optBoolean("netProxy", d.netProxy),
                lowRam = o.optBoolean("lowRam", d.lowRam),
                showFrame = o.optBoolean("showFrame", d.showFrame),
                showNavBar = o.optBoolean("showNavBar", d.showNavBar),
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
                mtMode = o.optInt("mtMode", d.mtMode),
                legacyEngine = o.optBoolean("legacyEngine", false),
            )
        }
    }
}

/** Служба гостя, которую мы запускаем вместо init. */
data class GuestService(
    val name: String,
    val argv: List<String>,
    /** сокеты init: имя → права (например "zygote" → "0666") */
    val sockets: Map<String, String> = emptyMap(),
    val uid: Int = 0,
    val gid: Int = 0,
    /** ждать появления этого сокета перед следующей службой */
    val waitSocket: String? = null,
    val delayMs: Long = 0,
    val restart: Boolean = false,
    /** вендорский демон: если упадёт — не страшно */
    val optional: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("optional", optional)
        .put("name", name)
        .put("argv", JSONArray(argv))
        .put("sockets", JSONObject(sockets as Map<*, *>))
        .put("uid", uid).put("gid", gid)
        .put("waitSocket", waitSocket ?: JSONObject.NULL)
        .put("delayMs", delayMs).put("restart", restart)

    companion object {
        fun fromJson(o: JSONObject): GuestService {
            val argv = o.getJSONArray("argv").let { a -> List(a.length()) { a.getString(it) } }
            val socks = o.optJSONObject("sockets")?.let { s -> s.keys().asSequence().associateWith { s.getString(it) } } ?: emptyMap()
            return GuestService(
                name = o.getString("name"),
                argv = argv,
                sockets = socks,
                uid = o.optInt("uid", 0),
                gid = o.optInt("gid", 0),
                waitSocket = if (o.isNull("waitSocket")) null else o.optString("waitSocket"),
                delayMs = o.optLong("delayMs", 0),
                restart = o.optBoolean("restart", false),
                optional = o.optBoolean("optional", false),
            )
        }
    }
}

/**
 * Импортированный образ прошивки. Всё, что нужно для запуска, выводится из самой прошивки
 * при импорте (build.prop, init*.rc, состав /system) — никаких захардкоженных профилей устройств.
 */
data class GuestImage(
    val id: String,
    val name: String,
    val release: String,
    val api: Int,
    val brand: String,
    val model: String,
    val skin: String,
    val engine: Engine,
    val abi: String = "armeabi-v7a",
    val bootclasspath: String,
    val exports: Map<String, String>,
    val dirs: List<String>,
    val services: List<GuestService>,
    val sdcardPath: String,
    /** тома из storage_list.xml, которыми управляет vold (не эмулируемые): о них должна знать заглушка vold */
    val volumes: List<String> = emptyList(),
    val settings: VmSettings,
    val createdAt: Long,
    val sizeBytes: Long = 0,
    val sourceName: String = "",
    val runtime: String = "dalvik",
    val warnings: List<String> = emptyList(),
    val lastBootMs: Long = 0,
    val bootCount: Int = 0,
    /** версия анализатора, которым построен профиль; устаревший профиль пересчитывается перед запуском */
    val profileVersion: Int = 0,
) {
    val displayVersion: String get() = "Android $release (API $api)"

    /** Движок с учётом выбора пользователя. */
    fun effective(): GuestImage = copy(engine = if (settings.legacyEngine && api < 14) Engine.GB else Engine.KK)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("release", release).put("api", api)
        .put("brand", brand).put("model", model).put("skin", skin)
        .put("engine", engine.id).put("abi", abi)
        .put("bootclasspath", bootclasspath)
        .put("exports", JSONObject(exports as Map<*, *>))
        .put("dirs", JSONArray(dirs))
        .put("services", JSONArray(services.map { it.toJson() }))
        .put("sdcardPath", sdcardPath)
        .put("volumes", JSONArray(volumes))
        .put("settings", settings.toJson())
        .put("createdAt", createdAt).put("sizeBytes", sizeBytes)
        .put("sourceName", sourceName).put("runtime", runtime)
        .put("warnings", JSONArray(warnings))
        .put("lastBootMs", lastBootMs).put("bootCount", bootCount)
        .put("profileVersion", profileVersion)

    companion object {
        fun fromJson(o: JSONObject): GuestImage {
            val ex = o.optJSONObject("exports")
            val exports = ex?.keys()?.asSequence()?.associateWith { ex.getString(it) } ?: emptyMap()
            val dirs = o.optJSONArray("dirs")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val svcs = o.optJSONArray("services")?.let { a -> List(a.length()) { GuestService.fromJson(a.getJSONObject(it)) } } ?: emptyList()
            val warn = o.optJSONArray("warnings")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            return GuestImage(
                id = o.getString("id"),
                name = o.optString("name", "Android"),
                release = o.optString("release", "?"),
                api = o.optInt("api", 19),
                brand = o.optString("brand", ""),
                model = o.optString("model", ""),
                skin = o.optString("skin", "AOSP"),
                engine = Engine.byId(o.optString("engine")),
                abi = o.optString("abi", "armeabi-v7a"),
                bootclasspath = o.optString("bootclasspath", ""),
                exports = exports,
                dirs = dirs,
                services = svcs,
                sdcardPath = o.optString("sdcardPath", "/mnt/sdcard"),
                volumes = o.optJSONArray("volumes")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                settings = VmSettings.fromJson(o.optJSONObject("settings")),
                createdAt = o.optLong("createdAt", 0),
                sizeBytes = o.optLong("sizeBytes", 0),
                sourceName = o.optString("sourceName", ""),
                runtime = o.optString("runtime", "dalvik"),
                warnings = warn,
                lastBootMs = o.optLong("lastBootMs", 0),
                bootCount = o.optInt("bootCount", 0),
                profileVersion = o.optInt("profileVersion", 0),
            )
        }
    }
}
