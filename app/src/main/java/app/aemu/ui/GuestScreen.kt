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

    fun poke() { quiet = 0; synchronized(gate) { gate.notifyAll() } }

    private fun tick() {
        if (passthrough) return
        val f = fb ?: return
        val pixels = w.toLong() * h * 2
        if (buf != null) {
            val ino = runCatching { android.system.Os.stat(f.absolutePath).st_ino }.getOrDefault(0L)
            if (ino != mappedIno) { runCatching { raf?.close() }; raf = null; buf = null; bmp = null; lastHash = 0 }
        }
        if (buf == null) {
            if (!f.isFile || f.length() < pixels) return
            raf = RandomAccessFile(f, "r")
            buf = raf!!.channel.map(FileChannel.MapMode.READ_ONLY, 0, pixels)
            mappedIno = runCatching { android.system.Os.stat(f.absolutePath).st_ino }.getOrDefault(0L)
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        }
        val b = buf ?: return
        val bitmap = bmp ?: return
        var hash = 0L
        val last = pixels.toInt() - 4
        var i = 0
        while (i <= last) { hash = 31 * hash + b.getInt(i); i += 2048 }
        val rung = rings != ringSeen
        ringSeen = rings
        quiet++
        val force = quiet > 30
        if (rung || hash != lastHash || force) {
            if (force) quiet = 0
            b.rewind()
            bitmap.copyPixelsFromBuffer(b)
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
