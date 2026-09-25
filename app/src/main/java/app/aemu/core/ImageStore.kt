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

    fun wipe(f: File) {
        val isLink = runCatching { android.system.OsConstants.S_ISLNK(Os.lstat(f.path).st_mode) }.getOrDefault(false)
        if (!isLink && f.isDirectory) f.listFiles()?.forEach { wipe(it) }
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
