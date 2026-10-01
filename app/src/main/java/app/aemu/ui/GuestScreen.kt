package app.aemu.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

import android.view.MotionEvent
import android.view.View
import app.aemu.core.InputService
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Слой поверх экрана гостя: принимает касания и переводит их в координаты гостя.
 * В режиме GPU-моста (KK) прозрачен — кадр рисует мост в SurfaceView под ним.
 * В программном режиме (GB или без GPU) сам показывает fb0: отображает файл в память
 * и копирует в RGB565-битмап только когда кадр изменился.
 */
class GuestScreen(ctx: Context, private val w: Int, private val h: Int) : View(ctx) {
    var input: InputService? = null
    var fb: File? = null
    @Volatile var passthrough = false
        set(v) { field = v; postInvalidate() }
    @Volatile var fps = 0f
        private set
    @Volatile var rings = 0L
    /** 2 = the guest flips between two pages by panning (recovery's minui); show the one it wrote last */
    var pages = 1
    /** pixel format of fb0: 0 = RGB565, 1 = RGBA/RGBX_8888, 2 = BGRA_8888 (TWRP); a change remaps the file */
    @Volatile var format = 0
        set(v) {
            if (field == v) return
            field = v
            mappedIno = -1
            poke()
        }
    private val pageHash = LongArray(2)
    private var argb: IntArray? = null
    private var page = 0

    private var bmp: Bitmap? = null
    private var buf: MappedByteBuffer? = null
    private var raf: RandomAccessFile? = null
    private var mappedIno = 0L
    private var lastHash = 0L
    private var ringSeen = 0L
    private var changed = 0L
    private var changedAt = 0L
    private var quiet = 0
    @Volatile private var live = false
    private val gate = Object()
    private val paint = Paint().apply { isFilterBitmap = true }
    private val dst = Rect()

    fun start() {
        if (live) return
        live = true
        Thread({
            while (live) {
                runCatching { tick() }
                synchronized(gate) { runCatching { gate.wait(if (quiet > 8) 50L else 12L) } }
            }
        }, "guest-fb").apply { isDaemon = true; start() }
    }

    fun stop() { live = false }

    /** Copy of the last frame shown from fb0 (software path); null in GPU passthrough or before the first frame. */
    fun snapshot(): Bitmap? = if (passthrough) null else bmp?.copy(Bitmap.Config.ARGB_8888, false)

    fun poke() { quiet = 0; synchronized(gate) { gate.notifyAll() } }

    private fun tick() {
        if (passthrough) return
        val f = fb ?: return
        val pixels = w.toLong() * h * (if (format == 0) 2 else 4)
        if (buf != null) {
            val ino = runCatching { android.system.Os.stat(f.absolutePath).st_ino }.getOrDefault(0L)
            if (ino != mappedIno || mappedIno == -1L) { runCatching { raf?.close() }; raf = null; buf = null; bmp = null; lastHash = 0 }
        }
        if (buf == null) {
            if (!f.isFile || f.length() < pixels) return
            raf = RandomAccessFile(f, "r")
            val span = if (pages == 2 && f.length() >= pixels * 2) pixels * 2 else pixels
            buf = raf!!.channel.map(FileChannel.MapMode.READ_ONLY, 0, span)
            mappedIno = runCatching { android.system.Os.stat(f.absolutePath).st_ino }.getOrDefault(0L)
            bmp = Bitmap.createBitmap(w, h, if (format == 0) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
        }
        val b = buf ?: return
        val bitmap = bmp ?: return
        val two = b.capacity() >= pixels * 2
        if (two) {
            // two pages: follow the one whose content changed most recently
            for (pg in 0..1) {
                var hh = 0L
                var j = (pg * pixels).toInt()
                val end = j + pixels.toInt() - 4
                while (j <= end) { hh = 31 * hh + b.getInt(j); j += 2048 }
                if (hh != pageHash[pg]) { pageHash[pg] = hh; page = pg }
            }
        }
        val base = if (two) (page * pixels).toInt() else 0
        var hash = page.toLong()
        val last = base + pixels.toInt() - 4
        var i = base
        while (i <= last) { hash = 31 * hash + b.getInt(i); i += 2048 }
        val rung = rings != ringSeen
        ringSeen = rings
        quiet++
        val force = quiet > 30
        if (rung || hash != lastHash || force) {
            if (force) quiet = 0
            if (format == 0) {
                b.limit(base + pixels.toInt()); b.position(base)
                bitmap.copyPixelsFromBuffer(b)
                b.clear()
            } else {
                // 32-bit: the guest's alpha byte is padding (TWRP leaves 0), so force it opaque; RGBA swaps R and B
                val n = w * h
                val px = argb?.takeIf { it.size == n } ?: IntArray(n).also { argb = it }
                val ib = b.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN).position(base).let { (it as java.nio.ByteBuffer).asIntBuffer() }
                ib.get(px, 0, n)
                if (format == 2) for (k in 0 until n) px[k] = px[k] or -0x1000000
                else for (k in 0 until n) { val v = px[k]; px[k] = -0x1000000 or ((v and 0xff) shl 16) or (v and 0xff00) or ((v shr 16) and 0xff) }
                bitmap.setPixels(px, 0, w, 0, 0, w, h)
            }
            if (hash != lastHash || rung) {
                quiet = 0
                lastHash = hash
                changed++
                val now = System.currentTimeMillis()
                if (changedAt == 0L) changedAt = now
                if (now - changedAt > 1000) { fps = changed * 1000f / (now - changedAt); changed = 0; changedAt = now }
            }
            postInvalidate()
        }
    }

    override fun onDraw(c: Canvas) {
        dst.set(0, 0, width, height)
        if (passthrough) return
        val b = bmp
        if (b != null) c.drawBitmap(b, null, dst, paint) else c.drawColor(0xff000000.toInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val inp = input ?: return false
        poke()
        if (width <= 0 || height <= 0) return false
        val act = e.actionMasked
        val up = act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL
        val gone = if (act == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        // промежуточные точки движения — чтобы жесты и прокрутка были плавными
        val gap = if (inp.rateHz > 0) 1000L / inp.rateHz else 0L
        var prevAt = 0L
        for (hi in 0 until e.historySize) {
            val ht = e.getHistoricalEventTime(hi)
            if (gap > 0 && prevAt != 0L && ht - prevAt < gap) continue
            prevAt = ht
            val pts = ArrayList<InputService.P>(e.pointerCount)
            for (i in 0 until e.pointerCount) {
                if (up || i == gone) continue
                pts.add(InputService.P(e.getPointerId(i), gx(e.getHistoricalX(i, hi)), gy(e.getHistoricalY(i, hi))))
            }
            if (pts.isNotEmpty()) inp.touch(pts, true)
        }
        val pts = ArrayList<InputService.P>(e.pointerCount)
        for (i in 0 until e.pointerCount) {
            if (up || i == gone) continue
            pts.add(InputService.P(e.getPointerId(i), gx(e.getX(i)), gy(e.getY(i))))
        }
        inp.touch(pts, act != MotionEvent.ACTION_MOVE)
        return true
    }

    private fun gx(x: Float) = (x * w / width).toInt().coerceIn(0, w - 1)
    private fun gy(y: Float) = (y * h / height).toInt().coerceIn(0, h - 1)
}
