package app.aemu.core

import android.content.Context
import android.system.Os
import org.json.JSONObject
import java.io.File

/** Расположение файлов одного образа внутри песочницы приложения. */
class VmPaths(val ctx: Context, val id: String) {
    val dir: File = File(ImageStore.imagesDir(ctx), id)
    /** дерево гостя: сюда смотрит qemu через -L */
    val root: File = File(dir, "root")
    /** рабочий каталог: сокеты хоста, журналы, копии свойств */
    val bin: File = File(dir, "run")
    val meta: File = File(dir, "image.json")
    val props: File get() = File(root, "dev/__properties__")
    val propsTemplate: File get() = File(dir, "props.base")
    val fb: File get() = File(root, "dev/graphics/fb0")
    val binderSock: File get() = File(bin, "binder.sock")
    val hwbinderSock: File get() = File(bin, "hwbinder.sock")
    val vndbinderSock: File get() = File(bin, "vndbinder.sock")
    val inputSock: File get() = File(bin, "input.sock")
    val frameSock: File get() = File(bin, "frame.sock")
    val glSock: File get() = File(root, "dev/socket/gl")
    val creds: File get() = File(bin, "creds")
    val owners: File get() = File(root, "dhd.owners")
    val snapshots: File get() = File(dir, "snapshots")
    fun socket(name: String) = File(root, "dev/socket/$name")
    fun log(name: String) = File(bin, "$name.log")

    fun nativeBin(name: String): File = File(ctx.applicationInfo.nativeLibraryDir, name)
}

object ImageStore {
    fun imagesDir(ctx: Context): File = File(ctx.filesDir, "images").apply { mkdirs() }

    fun list(ctx: Context): List<GuestImage> =
        imagesDir(ctx).listFiles()?.mapNotNull { d ->
            val f = File(d, "image.json")
            if (!f.isFile) null else runCatching { GuestImage.fromJson(JSONObject(f.readText())) }.getOrNull()
        }?.sortedByDescending { it.createdAt } ?: emptyList()

    fun get(ctx: Context, id: String): GuestImage? {
        val f = VmPaths(ctx, id).meta
        return if (f.isFile) runCatching { GuestImage.fromJson(JSONObject(f.readText())) }.getOrNull() else null
    }

    fun save(ctx: Context, img: GuestImage) {
        val p = VmPaths(ctx, img.id)
        p.dir.mkdirs()
        val tmp = File(p.dir, "image.json.tmp")
        tmp.writeText(img.toJson().toString(2))
        tmp.renameTo(p.meta)
    }

    fun newId(ctx: Context): String {
        val abc = "abcdefghijkmnpqrstuvwxyz23456789"
        while (true) {
            val id = (1..6).map { abc.random() }.joinToString("")
            if (!File(imagesDir(ctx), id).exists()) return id
        }
    }

    /** Удаляет каталог образа, не проходя по символьным ссылкам наружу. */
    fun delete(ctx: Context, id: String) {
        wipe(VmPaths(ctx, id).dir)
    }

    fun dirBase(img: GuestImage): String = if (img.baseId.isNotEmpty()) img.name else img.skin.ifBlank { "AOSP" }

    fun sanitizeDir(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), " ")
        .replace(Regex("\\s+"), " ").trim().trim('.', ' ').take(48).trim('.', ' ')

    fun adopt(ctx: Context, img: GuestImage): GuestImage {
        val base = sanitizeDir(dirBase(img))
        if (base.isEmpty() || base == img.id) return img
        val from = VmPaths(ctx, img.id)
        if (!from.dir.isDirectory) return img
        val images = imagesDir(ctx)
        var desired = ""
        if (!File(images, base).exists()) desired = base
        else for (n in 2..99) {
            val c = File(images, "$base $n")
            if (!c.exists() || c == from.dir) { desired = "$base $n"; break }
        }
        if (desired.isEmpty() || desired == img.id) return img
        val target = File(images, desired)
        if (!from.dir.renameTo(target)) return img
        moveSdcard(ctx, img, desired, target)
        val renamed = img.copy(id = desired)
        save(ctx, renamed)
        return renamed
    }

    private fun moveSdcard(ctx: Context, img: GuestImage, newId: String, dir: File) {
        val safe = img.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(40).ifBlank { img.id }
        val moves = ArrayList<Pair<File, File>>()
        runCatching {
            if (android.os.Environment.getExternalStorageState() == android.os.Environment.MEDIA_MOUNTED) {
                val share = File(android.os.Environment.getExternalStorageDirectory(), "AEmulator")
                moves.add(File(share, "$safe (${img.id})") to File(share, "$safe ($newId)"))
            }
        }
        ctx.getExternalFilesDir(null)?.let { moves.add(File(it, "sdcard-${img.id}") to File(it, "sdcard-$newId")) }
        moves.add(File(ctx.filesDir, "sdcard-${img.id}") to File(ctx.filesDir, "sdcard-$newId"))
        var moved = ""
        for ((a, b) in moves) if (a.isDirectory && !b.exists() && a.renameTo(b)) moved = b.path
        if (moved.isNotEmpty()) {
            val sp = File(File(dir, "run"), "sdcard.path")
            if (sp.isFile) runCatching { sp.writeText(moved + "\n") }
        }
    }

    private fun fixLink(ctx: Context, f: File, map: Map<String, String>) {
        if (!runCatching { android.system.OsConstants.S_ISLNK(android.system.Os.lstat(f.path).st_mode) }.getOrDefault(false)) return
        val t = runCatching { android.system.Os.readlink(f.path) }.getOrNull() ?: return
        var nt = t
        for ((o, n) in map) {
            nt = nt.replace(File(imagesDir(ctx), o).path, File(imagesDir(ctx), n).path)
            nt = nt.replace("sdcard-$o", "sdcard-$n")
            nt = nt.replace(" ($o)", " ($n)")
        }
        if (nt != t) runCatching { android.system.Os.remove(f.path); android.system.Os.symlink(nt, f.path) }
    }

    fun vmProcessAlive(pkg: String): Boolean {
        val procs = java.io.File("/proc").listFiles() ?: return true
        for (p in procs) {
            if (p.name.toIntOrNull() == null) continue
            val cl = runCatching { java.io.File(p, "cmdline").readText() }.getOrNull() ?: continue
            if (cl.split('\u0000').any { it == "$pkg:vm" }) return true
        }
        return false
    }

    fun migrateLegacy(ctx: Context) {
        if (vmProcessAlive(ctx.packageName)) return
        runCatching {
            val imgs = list(ctx)
            val map = HashMap<String, String>()
            for (img in imgs) {
                val moved = adopt(ctx, img)
                if (moved.id != img.id) map[img.id] = moved.id
            }
            for (img in imgs) {
                var changed = false
                var cur = img
                map[img.id]?.let { cur = cur.copy(id = it); changed = true }
                if (img.baseId.isNotEmpty()) map[img.baseId]?.let { if (cur.baseId != it) { cur = cur.copy(baseId = it); changed = true } }
                val p = VmPaths(ctx, cur.id)
                fixLink(ctx, File(p.root, "system"), map)
                fixLink(ctx, File(File(p.dir, "recovery"), "system"), map)
                fixLink(ctx, File(File(p.dir, "recovery"), "data"), map)
                fixLink(ctx, File(File(p.dir, "recovery"), "sdcard"), map)
                Sdcard.expose(ctx, p, cur) { }
                if (changed) save(ctx, cur)
            }
        }
    }

    /**
     * New container of [src]'s firmware: a full own copy of the root including /system (flashing in one container
     * never touches another), a fresh /data (factory reset: apps, settings and accounts start empty; dalvik-cache is kept
     * so the first boot is quick) or a full copy of it, its own memory-card folder and settings.
     */
    fun clone(ctx: Context, src: GuestImage, name: String, copyData: Boolean, customSettings: VmSettings? = null, cancelled: () -> Boolean = { false }, progress: (String) -> Unit): GuestImage {
        val from = VmPaths(ctx, src.id)
        val need = du(from.root) + du(File(from.root, "system").canonicalFile).takeIf { File(from.root, "system").canonicalPath != File(from.root, "system").path } .let { it ?: 0 }
        val free = imagesDir(ctx).usableSpace
        if (need + (200L shl 20) > free) error("not enough space: need ${need shr 20} MB, free ${free shr 20} MB")
        val id = newId(ctx)
        val to = VmPaths(ctx, id)
        try {
            to.root.mkdirs()
            val skipData = setOf("data", "system", "user", "property", "anr", "tombstones", "backup", "misc/wifi", "misc/keystore",
                "local/tmp", "app", "app-private", "app-asec")
            fun copy(a: File, b: File, rel: String) {
                if (cancelled()) error("cancelled")
                val st = runCatching { Os.lstat(a.path) }.getOrNull() ?: return
                when {
                    android.system.OsConstants.S_ISLNK(st.st_mode) -> runCatching { Os.symlink(Os.readlink(a.path), b.path) }
                    android.system.OsConstants.S_ISDIR(st.st_mode) -> {
                        b.mkdirs()
                        for (c in a.list().orEmpty()) {
                            val r = if (rel.isEmpty()) c else "$rel/$c"
                            if (rel.isEmpty() && (c == "dev" || c == "proc")) continue
                            if (rel.isEmpty() && c == "system") progress("system")
                            if (!copyData && r.startsWith("data/") && r.removePrefix("data/") in skipData) { File(b, c).mkdirs(); continue }
                            if (rel.isEmpty() && c == "data") progress("data")
                            copy(File(a, c), File(b, c), r)
                        }
                    }
                    android.system.OsConstants.S_ISREG(st.st_mode) -> {
                        a.inputStream().use { i -> b.outputStream().use { i.copyTo(it, 1 shl 16) } }
                        runCatching { Os.chmod(b.path, st.st_mode and 0xfff) }
                    }
                }
            }
            progress("root")
            // an older container's /system may be a link to its original: copy what it points to
            val sys = File(from.root, "system").canonicalFile
            copy(from.root, to.root, "")
            if (runCatching { android.system.OsConstants.S_ISLNK(Os.lstat(File(to.root, "system").path).st_mode) }.getOrDefault(false)) {
                File(to.root, "system").delete(); copy(sys, File(to.root, "system"), "system")
            }
            File(to.root, "dev").mkdirs()
            // ownership table is keyed by inode: the fresh copy gets its own on first boot
            if (!copyData) { File(to.root, "dhd.owners").delete(); File(to.root, "dhd.owners.seeded").delete() }
            listOf("props.base").forEach { n -> File(from.dir, n).takeIf { it.isFile }?.copyTo(File(to.dir, n), true) }
            val img = src.copy(id = id, name = name, baseId = src.baseId.ifEmpty { src.id },
                settings = customSettings ?: src.settings,
                createdAt = System.currentTimeMillis(),
                lastBootMs = 0, bootCount = 0, sizeBytes = du(to.dir))
            save(ctx, img)
            return adopt(ctx, img)
        } catch (t: Throwable) {
            wipe(to.dir)
            throw t
        }
    }

    /**
     * Factory reset without a recovery: the user's part of /data (apps, settings, accounts) and /cache are emptied,
     * the rest of /data (dalvik-cache, what the import prepared) stays, like in a fresh container.
     */
    fun resetData(ctx: Context, id: String) {
        val root = VmPaths(ctx, id).root
        for (n in RESET_DIRS) {
            val d = File(root, "data/$n")
            if (!d.exists()) continue
            d.listFiles()?.forEach { wipe(it) }
        }
        File(root, "cache").listFiles()?.forEach { wipe(it) }
        get(ctx, id)?.let { save(ctx, it.copy(sizeBytes = du(VmPaths(ctx, id).dir))) }
    }

    private val RESET_DIRS = listOf("data", "system", "user", "property", "anr", "tombstones", "backup", "misc/wifi",
        "misc/keystore", "local/tmp", "app", "app-private", "app-asec", "media", "user_de", "system_de", "system_ce", "misc_de", "misc_ce")

    /** Changes the stored image from its current state (not from a copy read earlier: the machine updates boot counters). */
    @Synchronized
    fun update(ctx: Context, id: String, f: (GuestImage) -> GuestImage) {
        get(ctx, id)?.let { save(ctx, f(it)) }
    }

    fun wipe(f: File) {
        val isLink = runCatching { android.system.OsConstants.S_ISLNK(Os.lstat(f.path).st_mode) }.getOrDefault(false)
        if (!isLink && f.isDirectory) { f.setWritable(true, true); f.listFiles()?.forEach { wipe(it) } }
        f.delete()
    }

    fun du(f: File): Long {
        var n = 0L
        val st = ArrayDeque<File>().apply { add(f) }
        while (st.isNotEmpty()) {
            val x = st.removeLast()
            val isLink = runCatching { android.system.OsConstants.S_ISLNK(Os.lstat(x.path).st_mode) }.getOrDefault(false)
            if (isLink) continue
            if (x.isDirectory) x.listFiles()?.forEach { st.add(it) } else n += x.length()
        }
        return n
    }
}
