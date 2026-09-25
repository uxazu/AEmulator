package app.aemu.core

import android.content.Context
import android.os.Environment
import android.system.Os
import java.io.File
import java.nio.file.Files

/**
 * Общая папка «карты памяти»: лежит в памяти телефона (Внутренний накопитель/AEmulator/<образ>),
 * гость видит её по своим путям (/mnt/sdcard, /sdcard, /storage/…) — их подменяет qemu (DHD_SDCARD).
 */
object Sdcard {
    private val LAYOUT = listOf("DCIM", "Download", "Music", "Movies", "Pictures", "Android/data", "Android/obb", "LOST.DIR")
    private val GUEST_PATHS = listOf(
        "mnt/sdcard", "sdcard", "storage/emulated/legacy", "storage/emulated/0",
        "storage/sdcard0", "storage/sdcard", "mnt/shell/emulated/0", "mnt/emmc", "storage/extSdCard",
    )

    fun setup(ctx: Context, paths: VmPaths, img: GuestImage, log: (String) -> Unit): File? {
        val d = choose(ctx, img, log) ?: return null
        LAYOUT.forEach { File(d, it).mkdirs() }
        val readme = File(d, "README-AEmulator.txt")
        if (!readme.isFile) runCatching {
            readme.writeText(
                "Общая папка образа «${img.name}».\n" +
                    "На телефоне: ${d.absolutePath}\n" +
                    "Внутри прошивки: ${img.sdcardPath} (и /sdcard)\n\n" +
                    "Кладите сюда APK, музыку, фото — гость увидит их сразу.\n"
            )
        }
        val all = (GUEST_PATHS + img.sdcardPath.trimStart('/')).distinct()
        for (rel in all) placeholder(File(paths.root, rel))
        for (rel in listOf("mnt/asec", "mnt/obb", "mnt/secure/asec")) File(paths.root, rel).mkdirs()
        runCatching { File(paths.bin, "sdcard.path").writeText(d.absolutePath + "\n") }
        log("карта памяти: ${d.absolutePath} → гость видит ${img.sdcardPath}")
        return d
    }

    fun hostDir(paths: VmPaths): File? =
        runCatching { File(File(paths.bin, "sdcard.path").readText().trim()) }.getOrNull()?.takeIf { it.isDirectory }

    private fun choose(ctx: Context, img: GuestImage, log: (String) -> Unit): File? {
        val safe = img.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(40).ifBlank { img.id }
        val tries = ArrayList<File>()
        runCatching {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                tries.add(File(File(Environment.getExternalStorageDirectory(), "AEmulator"), "$safe (${img.id})"))
            }
        }
        ctx.getExternalFilesDir(null)?.let { tries.add(File(it, "sdcard-${img.id}")) }
        tries.add(File(ctx.filesDir, "sdcard-${img.id}"))
        for (t in tries) if (writable(t)) return t else log("карта памяти: ${t.absolutePath} не пишется, пробую дальше")
        log("карта памяти: ни одно место не пишется")
        return null
    }

    private fun writable(d: File): Boolean = runCatching {
        d.mkdirs()
        if (!d.isDirectory) return false
        val p = File(d, ".aemu-probe")
        p.writeText("ok")
        val ok = p.length() > 0
        p.delete()
        ok
    }.getOrDefault(false)

    /** В дереве гостя на месте точки монтирования должен быть обычный каталог (не ссылка). */
    private fun placeholder(f: File) {
        if (runCatching { Files.isSymbolicLink(f.toPath()) }.getOrDefault(false)) runCatching { Os.remove(f.absolutePath) }
        else if (f.isFile) f.delete()
        f.mkdirs()
    }
}
