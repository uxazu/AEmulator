package app.aemu.ui

import app.aemu.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aemu.core.Engine
import app.aemu.core.GuestLog
import app.aemu.core.GuestVm
import app.aemu.core.ImageStore
import app.aemu.core.InputService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread

/** Процесс :vm держит ровно одну машину. */
object VmHost {
    @Volatile var vm: GuestVm? = null
}

class VmActivity : ComponentActivity() {
    private lateinit var vm: GuestVm
    private lateinit var surfaceView: SurfaceView
    private lateinit var guest: GuestScreen
    /** the guest's screen size, for what takes frames off it (VNC, recording) */
    private var guestW = 0
    private var guestH = 0
    private var vnc: app.aemu.core.VncServer? = null
    private var recorder: app.aemu.core.ScreenRecorder? = null
    private val vncOn = androidx.compose.runtime.mutableStateOf(false)
    private val recOn = androidx.compose.runtime.mutableStateOf(false)
    private val grabThread by lazy { android.os.HandlerThread("frame-grab").apply { start() } }
    private var grabScratch: android.graphics.Bitmap? = null
    private lateinit var box: FrameLayout
    private var state by mutableStateOf(GuestVm.State.STOPPED)
    /** the guest's notification LED and where the guest screen starts (the LED sits just above it) */
    private var led by mutableStateOf<app.aemu.core.Lights.Led?>(null)
    /** this system's setting "Notification light" (the settings sheet changes it while the system runs) */
    private var showLed by mutableStateOf(true)
    private var guestTopPx by mutableStateOf(0)
    private val logLines = mutableStateListOf<String>()

    @SuppressLint("ClickableViewAccessibility")
    override fun attachBaseContext(base: Context) = super.attachBaseContext(app.aemu.AppPrefs.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the overlay pads itself (status bar, navigation bar, keyboard): the terminal stays above the keyboard
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val id = intent.getStringExtra(EXTRA_ID)
        val cur = VmHost.vm
        val img = ((if (id != null) ImageStore.get(this, id) else null) ?: cur?.img)
            ?.let { if (cur?.img?.id == it.id) it else app.aemu.importer.Analyzer.refresh(this, it).effective() }
        if (img == null) { finish(); return }
        if (cur != null && cur.img.id != img.id && cur.state != GuestVm.State.STOPPED) {
            // в процессе уже живёт другая машина — её надо сначала остановить
            cur.stop()
            restartProcess(img.id)
            return
        }
        vm = if (cur != null && cur.img.id == img.id) cur
            else GuestVm(applicationContext, img).also { it.recoveryMode = intent.getBooleanExtra(EXTRA_RECOVERY, false); VmHost.vm = it }
        vm.onPower = { reboot, reason -> runOnUiThread { if (reboot) rebootVm(reason == "recovery") else stopVm() } }
        val s = vm.settings
        showLed = s.showLed
        if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val (gw, gh) = if (vm.engine == Engine.GB) 480 to 800 else s.width to s.height
        val root = FrameLayout(this).apply { setBackgroundColor(0xff000000.toInt()) }
        box = FrameLayout(this)
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { vm.surface = h.surface }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) { vm.surface = h.surface }
            override fun surfaceDestroyed(h: SurfaceHolder) { vm.surface = null }
        })
        guestW = gw; guestH = gh
        guest = GuestScreen(this, gw, gh).apply {
            input = vm.input
            fb = if (vm.recoveryMode) app.aemu.core.RecoveryImage.fb(vm.paths) else vm.paths.fb
            if (vm.recoveryMode) pages = 2
        }
        // 5.0+ renders through the standalone glserverd into fb0 (see GuestVm.glUp), shown like the software path
        val useBridge = vm.engine == Engine.KK && s.gpu && !vm.recoveryMode && vm.img.api < 21
        // the arm64 engine's GLES bridge shows SurfaceFlinger's frames in the same view, straight from the GPU; until
        // the first of them (and with the software renderer) the screen is fb0 as before
        val direct = vm.engine == Engine.N64 && vm.kernel.gpuBridge64 && s.gpu && !vm.recoveryMode
        if (direct) { surfaceView.holder.setFixedSize(gw, gh); guest.direct = { app.aemu.core.GlHost.presented() } }
        surfaceView.visibility = if (useBridge || direct) View.VISIBLE else View.GONE
        guest.passthrough = useBridge
        box.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        box.addView(guest, FrameLayout.LayoutParams(-1, -1))
        root.addView(box, FrameLayout.LayoutParams(0, 0))
        val overlay = ComposeView(this).apply { setContent { AemuTheme(forceDark = true) { Overlay() } } }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        // экран гостя вписываем с сохранением пропорций над панелью кнопок
        val navDp = if (!s.showNavBar) 0 else if (s.navSize == 0) 46 else 64
        // the window draws edge to edge (for the overlay's keyboard padding): the guest screen keeps out of the
        // camera cutout and system bars; the keyboard is not counted, so the screen does not jump when it opens
        var safeTop = 0
        var safeBottom = 0
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val bars = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            if (bars.top != safeTop || bars.bottom != safeBottom) { safeTop = bars.top; safeBottom = bars.bottom; v.requestLayout() }
            ins
        }
        root.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val full = gw >= r - l - 4 && gh >= b - t - 4
            val navPx = if (full) 0 else (navDp * resources.displayMetrics.density).toInt()
            val aw = r - l
            val ah = b - t - navPx - if (full) 0 else (safeTop + safeBottom)
            if (aw <= 0 || ah <= 0) return@addOnLayoutChangeListener
            val scale = minOf(aw.toFloat() / gw, ah.toFloat() / gh)
            val w = (gw * scale).toInt()
            val h = (gh * scale).toInt()
            val lp = box.layoutParams as FrameLayout.LayoutParams
            val top = if (full) 0 else safeTop + (ah - h) / 2
            if (guestTopPx != top) guestTopPx = top
            if (lp.width != w || lp.height != h || lp.topMargin != top) {
                lp.width = w; lp.height = h
                lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                lp.topMargin = top
                v.post { box.layoutParams = lp }
            }
        }

        vm.onFrame = { guest.rings++; guest.poke() }
        state = vm.state
        logLines.addAll(vm.lines().takeLast(200))
        vm.onState { st -> runOnUiThread { state = st } }
        vm.mic.onPermissionNeeded = { runOnUiThread { askMic.launch(android.Manifest.permission.RECORD_AUDIO) } }
        vm.camera.onPermissionNeeded = { runOnUiThread { askCamera.launch(android.Manifest.permission.CAMERA) } }
        led = vm.lights.current
        vm.lights.onChange { l -> runOnUiThread { led = l } }
        vm.onLog { line -> runOnUiThread { logLines.add(line); if (logLines.size > 400) logLines.removeRange(0, logLines.size - 400) } }
        guest.start()
        loadMenuOffset()
        loadNavPos()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { vm.input.press(InputService.KEY_BACK) }
        })

        registerShell()
        if (vm.state == GuestVm.State.STOPPED || vm.state == GuestVm.State.FAILED) {
            thread(name = "vm-boot") { vm.boot() }
        }
    }

    /** Отладка: adb shell am broadcast -a app.aemu.SHELL --es cmd "dumpsys power" → run/shell.out */
    private var shellRx: android.content.BroadcastReceiver? = null
    private fun registerShell() {
        if (!app.aemu.BuildConfig.DEBUG) return
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // --es keys "116d 115 116u": raw guest key codes, d/u = down/up only, bare = press
                i.getStringExtra("keys")?.let { ks ->
                    thread {
                        for (k in ks.split(' ').filter { it.isNotBlank() }) {
                            val code = k.trimEnd('d', 'u').toIntOrNull() ?: continue
                            when { k.endsWith("d") -> vm.input.key(code, true); k.endsWith("u") -> vm.input.key(code, false)
                                else -> { vm.input.key(code, true); Thread.sleep(80); vm.input.key(code, false) } }
                            Thread.sleep(150)
                        }
                    }
                    return
                }
                i.getStringExtra("sms")?.let { sendSms("+15550001234", it); return }
                if (i.getBooleanExtra("shot", false)) { runOnUiThread { screenshot() }; return }
                // --ez frames true --es out <name>: the count of frames shown and the clock, for the testers
                if (i.getBooleanExtra("frames", false)) {
                    val name = i.getStringExtra("out")?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]+")) } ?: "frames.out"
                    java.io.File(vm.paths.bin, name).writeText("shown=${guest.shown} direct=${guest.passthrough} ms=${android.os.SystemClock.uptimeMillis()}\n")
                    return
                }
                if (i.hasExtra("adb")) { if (i.getBooleanExtra("adb", false)) vm.adb.start() else vm.adb.stop(); return }
                val cmd = i.getStringExtra("cmd") ?: return
                // своё имя файла на каждую команду: медленная предыдущая команда не затрёт ответ
                val name = i.getStringExtra("out")?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]+")) } ?: "shell.out"
                thread {
                    val out = runCatching { vm.guestShell(cmd, 120_000) }.getOrElse { it.toString() }
                    val tmp = java.io.File(vm.paths.bin, "$name.tmp")
                    tmp.writeText(out + "\n<<done>>\n"); tmp.renameTo(java.io.File(vm.paths.bin, name))
                }
            }
        }
        shellRx = rx
        val f = android.content.IntentFilter("app.aemu.SHELL")
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_EXPORTED) else registerReceiver(rx, f)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val code = map(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) vm.input.key(code, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val code = map(keyCode) ?: return super.onKeyUp(keyCode, event)
        vm.input.key(code, false)
        return true
    }

    private fun map(k: Int): Int? = when (k) {
        KeyEvent.KEYCODE_VOLUME_UP -> InputService.KEY_VOLUMEUP
        KeyEvent.KEYCODE_VOLUME_DOWN -> InputService.KEY_VOLUMEDOWN
        KeyEvent.KEYCODE_MENU -> InputService.KEY_MENU
        KeyEvent.KEYCODE_SEARCH -> InputService.KEY_SEARCH
        else -> null
    }

    override fun onResume() {
        super.onResume()
        if (::vm.isInitialized) vm.sensors.resume(true)
    }

    override fun onPause() {
        if (::vm.isInitialized) vm.sensors.resume(false)
        super.onPause()
    }

    override fun onDestroy() {
        vnc?.stop(); vnc = null
        recorder?.let { r -> thread { r.stop() } }; recorder = null
        if (::guest.isInitialized) guest.stop()
        shellRx?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    private fun stopVm() {
        thread {
            vm.stop()
            VmHost.vm = null
            runCatching { app.aemu.core.Sdcard.expose(this@VmActivity, vm.paths, vm.img) { } }
            runOnUiThread { finishAndRemoveTask() }
            Thread.sleep(300)
            // GL-мост нельзя поднять повторно в том же процессе — процесс :vm уходит вместе с машиной
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private enum class Dlg { NONE, SMS, CALL, BATTERY, ADB, DEVICE }

    // position of the floating menu button, kept across launches
    private val uiPrefs by lazy { getSharedPreferences("vm_ui", MODE_PRIVATE) }
    private var menuOffset by mutableStateOf(Offset.Zero)
    private fun loadMenuOffset() { menuOffset = Offset(uiPrefs.getFloat("menu_x", 0f), uiPrefs.getFloat("menu_y", 0f)) }
    private fun saveMenuOffset() { uiPrefs.edit().putFloat("menu_x", menuOffset.x).putFloat("menu_y", menuOffset.y).apply() }

    private var navDelta by mutableStateOf(Offset.Zero)
    private var navIconPos by mutableStateOf(Offset.Zero)
    private var navCollapsed by mutableStateOf(false)
    private var navRect by mutableStateOf(Rect.Zero)
    private fun loadNavPos() {
        navCollapsed = uiPrefs.getBoolean("nav_collapsed", false)
        navDelta = Offset(uiPrefs.getFloat("nav_dx", 0f), uiPrefs.getFloat("nav_dy", 0f))
        navIconPos = Offset(uiPrefs.getFloat("nav_icon_x", 0f), uiPrefs.getFloat("nav_icon_y", 0f))
    }
    private fun saveNavPos() {
        uiPrefs.edit().putBoolean("nav_collapsed", navCollapsed)
            .putFloat("nav_dx", navDelta.x).putFloat("nav_dy", navDelta.y)
            .putFloat("nav_icon_x", navIconPos.x).putFloat("nav_icon_y", navIconPos.y).apply()
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.mic.permissionGranted() }

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    private fun guestAsync(cmd: String, done: ((String) -> Unit)? = null) {
        thread { val out = runCatching { vm.guestShell(cmd, 60_000) }.getOrElse { it.toString() }; done?.invoke(out) }
    }

    /** Quote for the guest's /system/bin/sh. */
    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun pasteToGuest() {
        val text = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isEmpty()) { toast(getString(R.string.m_clip_empty)); return }
        // Normalize line endings and truncate extreme paste payloads
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val safeLines = if (lines.size > 150) lines.take(150) else lines
        val script = buildString {
            for ((i, line) in safeLines.withIndex()) {
                if (line.isNotEmpty()) {
                    val escaped = line.replace(" ", "%s")
                    append("input text ").append(q(escaped)).append("; ")
                }
                if (i < safeLines.lastIndex) {
                    append("input keyevent 66; ")
                }
            }
        }
        guestAsync(script)
    }

    /**
     * The frame is taken on the host (the fb0 copy, or the GPU bridge's surface through PixelCopy): `screencap` does
     * not exist before 4.0 and cannot read the bridge's frames. The PNG lands in the guest's Pictures folder.
     */
    private fun screenshot() {
        val name = "aemu-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".png"
        val hostDir = app.aemu.core.Sdcard.hostDir(vm.paths)?.let { java.io.File(it, "Pictures") }
        if (hostDir == null) { toast(getString(R.string.m_failed)); return }
        fun save(b: android.graphics.Bitmap) = thread {
            val ok = runCatching {
                hostDir.mkdirs()
                java.io.File(hostDir, name).outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
            toast(if (ok) getString(R.string.m_screenshot_saved, vm.img.sdcardPath.trimEnd('/') + "/Pictures/$name") else getString(R.string.m_failed))
            // the guest's media scanner picks the file up for its gallery
            if (ok) guestAsync("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://${vm.img.sdcardPath.trimEnd('/')}/Pictures/$name")
        }
        guest.snapshot()?.let { save(it); return }
        if (surfaceView.width <= 0 || surfaceView.height <= 0) { toast(getString(R.string.m_failed)); return }
        val b = android.graphics.Bitmap.createBitmap(surfaceView.width, surfaceView.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.view.PixelCopy.request(surfaceView, b, { r ->
            if (r == android.view.PixelCopy.SUCCESS) save(b) else toast(getString(R.string.m_failed))
        }, android.os.Handler(mainLooper))
    }

    /**
     * The current frame at the guest's resolution: the fb0 copy, or the GPU bridge's surface through PixelCopy
     * (waited for here, so not to be called on the main thread).
     */
    private fun grabFrame(into: android.graphics.Bitmap): Boolean {
        val dst = android.graphics.Rect(0, 0, into.width, into.height)
        guest.snapshot()?.let { android.graphics.Canvas(into).drawBitmap(it, null, dst, null); it.recycle(); return true }
        val sw = surfaceView.width
        val sh = surfaceView.height
        if (sw <= 0 || sh <= 0 || !surfaceView.holder.surface.isValid) return false
        val tmp = grabScratch?.takeIf { it.width == sw && it.height == sh }
            ?: android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888).also { grabScratch = it }
        val done = java.util.concurrent.CountDownLatch(1)
        var ok = false
        runCatching {
            android.view.PixelCopy.request(surfaceView, tmp, { r -> ok = r == android.view.PixelCopy.SUCCESS; done.countDown() },
                android.os.Handler(grabThread.looper))
        }.onFailure { return false }
        if (!done.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) || !ok) return false
        android.graphics.Canvas(into).drawBitmap(tmp, null, dst, null)
        return true
    }

    /** VNC on/off. Each session gets a password of its own, shown here and in the log. */
    private fun toggleVnc() {
        vnc?.let { it.stop(); vnc = null; vncOn.value = false; return }
        if (guestW <= 0 || guestH <= 0) { toast(getString(R.string.m_failed)); return }
        val password = (1..8).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")
        val bmp = android.graphics.Bitmap.createBitmap(guestW, guestH, android.graphics.Bitmap.Config.ARGB_8888)
        val server = app.aemu.core.VncServer(app.aemu.core.VncServer.DEFAULT_PORT, password, guestW, guestH, vm.img.name,
            { px -> if (grabFrame(bmp)) { bmp.getPixels(px, 0, guestW, 0, 0, guestW, guestH); true } else false }, vm.input, vm::log)
        if (!server.start()) { toast(getString(R.string.m_failed)); return }
        vnc = server; vncOn.value = true
        val ip = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }.firstOrNull { it is java.net.Inet4Address }?.hostAddress
        }.getOrNull() ?: "127.0.0.1"
        val where = getString(R.string.m_vnc_started, "$ip:${app.aemu.core.VncServer.DEFAULT_PORT}", password)
        vm.log("vnc: $where")
        runOnUiThread { Toast.makeText(this, where, Toast.LENGTH_LONG).show() }
    }

    /** Screen recording on/off: an MP4 in the Movies folder of the guest's card (the phone's shared folder). */
    private fun toggleRecording() {
        recorder?.let { r ->
            recorder = null; recOn.value = false
            thread {
                r.stop()
                toast(getString(R.string.m_screenshot_saved, vm.img.sdcardPath.trimEnd('/') + "/Movies/" + r.file.name))
                guestAsync("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://${vm.img.sdcardPath.trimEnd('/')}/Movies/${r.file.name}")
            }
            return
        }
        val dir = app.aemu.core.Sdcard.hostDir(vm.paths)?.let { java.io.File(it, "Movies") }
        if (dir == null || guestW <= 0 || guestH <= 0) { toast(getString(R.string.m_failed)); return }
        val name = "aemu-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
        val r = app.aemu.core.ScreenRecorder(java.io.File(dir, name), guestW, guestH, ::grabFrame, vm::log)
        if (!r.start()) { toast(getString(R.string.m_failed)); return }
        recorder = r; recOn.value = true
    }

    // APK picked on the host: copied into the shared card folder, then installed by the guest's package manager
    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val dir = app.aemu.core.Sdcard.hostDir(vm.paths)
        if (dir == null) { toast(getString(R.string.m_failed)); return@registerForActivityResult }
        toast(getString(R.string.m_install_apk_start))
        thread {
            val ok = runCatching {
                contentResolver.openInputStream(uri)!!.use { i -> java.io.File(dir, "aemu-install.apk").outputStream().use { o -> i.copyTo(o) } }
            }.isSuccess
            if (!ok) { toast(getString(R.string.m_failed)); return@thread }
            val out = runCatching { vm.guestShell("pm install -r " + vm.img.sdcardPath.trimEnd('/') + "/aemu-install.apk", 300_000) }.getOrElse { it.toString() }
            runCatching { app.aemu.core.TreeFixer(this@VmActivity, vm.paths, vm.img, vm::log).seedOwners() }
            toast(out.trim().lines().lastOrNull { it.isNotBlank() }?.take(200) ?: getString(R.string.m_failed))
        }
    }

    private fun sendSms(from: String, body: String) {
        // through the emulated modem: stored, notified and rung like a real SMS on every version
        if (vm.ril.sms(from, body)) { toast(getString(R.string.m_sms_sent)); return }
        // radio emulation off: the message is written straight into the SMS provider inbox (4.1+)
        val cmd = "content insert --uri content://sms/inbox --bind address:s:${q(from)} --bind body:s:${q(body)} " +
            "--bind read:i:0 --bind date:l:${System.currentTimeMillis()}"
        guestAsync(cmd) { out -> toast(if (out.isBlank()) getString(R.string.m_sms_sent) else out.trim().take(200)) }
    }

    @Composable
    private fun SmsDialog(onDone: () -> Unit) {
        var from by remember { mutableStateOf("+10000000000") }
        var body by remember { mutableStateOf("Hello from AEmulator") }
        AlertDialog(onDismissRequest = onDone,
            title = { Text(stringResource(R.string.m_sms)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.m_sms_from)) }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.m_sms_body)) })
                }
            },
            confirmButton = { Button(onClick = { sendSms(from, body); onDone() }) { Text(stringResource(R.string.m_send)) } },
            dismissButton = { OutlinedButton(onClick = onDone) { Text(stringResource(R.string.close)) } })
    }

    @Composable
    private fun DeviceDialog(onDone: () -> Unit) {
        var airplane by remember { mutableStateOf(false) }
        var awake by remember { mutableStateOf(false) }
        val ns = if (vm.img.api >= 17) "global" else "system"
        // the switches show what the guest has now
        LaunchedEffect(Unit) {
            guestAsync("settings get $ns airplane_mode_on; settings get $ns stay_on_while_plugged_in") { out ->
                val v = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
                runOnUiThread { airplane = v.getOrNull(0) == "1"; awake = (v.getOrNull(1)?.toIntOrNull() ?: 0) != 0 }
            }
        }
        AlertDialog(onDismissRequest = onDone,
            title = { Text(stringResource(R.string.m_device)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.m_airplane), Modifier.weight(1f))
                        Switch(airplane, { v ->
                            airplane = v
                            guestAsync("settings put $ns airplane_mode_on ${if (v) 1 else 0}; " +
                                "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $v")
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.m_stay_awake), Modifier.weight(1f))
                        Switch(awake, { v ->
                            awake = v
                            guestAsync("settings put $ns stay_on_while_plugged_in ${if (v) 7 else 0}; svc power stayon ${if (v) "true" else "false"}")
                        })
                    }
                }
            },
            confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.close)) } })
    }

    @Composable
    private fun AdbDialog(onDone: () -> Unit) {
        var on by remember { mutableStateOf(vm.adb.running) }
        AlertDialog(onDismissRequest = onDone,
            title = { Text(stringResource(R.string.m_adb)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.m_adb_enable), Modifier.weight(1f))
                        Switch(on, { v -> if (v) vm.adb.start() else vm.adb.stop(); on = vm.adb.running })
                    }
                    if (on) {
                        val ips = app.aemu.core.AdbServer.addresses()
                        for (ip in ips.ifEmpty { listOf("127.0.0.1") })
                            Text("adb connect $ip:${vm.adb.port}", style = MaterialTheme.typography.bodyLarge,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Text(stringResource(R.string.m_adb_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.close)) } })
    }

    @Composable
    private fun CallDialog(onDone: () -> Unit) {
        var from by remember { mutableStateOf("+10000000000") }
        AlertDialog(onDismissRequest = onDone,
            title = { Text(stringResource(R.string.m_call)) },
            text = { OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.m_sms_from)) }, singleLine = true) },
            confirmButton = { Button(onClick = {
                if (!vm.ril.ring(from.trim())) toast(getString(R.string.m_failed))
                onDone()
            }) { Text(stringResource(R.string.m_call_ring)) } },
            dismissButton = { OutlinedButton(onClick = onDone) { Text(stringResource(R.string.close)) } })
    }

    @Composable
    private fun BatteryDialog(onDone: () -> Unit) {
        var level by remember { mutableStateOf(80f) }
        var charging by remember { mutableStateOf(true) }
        var follow by remember { mutableStateOf(vm.batteryFollowing) }
        // start from the guest's own battery state (sysfs or dumpsys)
        LaunchedEffect(Unit) {
            val cmd = "cat /sys/class/power_supply/battery/capacity 2>/dev/null; " +
                "cat /sys/class/power_supply/ac/online 2>/dev/null; " +
                "dumpsys battery 2>/dev/null"
            guestAsync(cmd) { out ->
                val lines = out.lines().map { it.trim() }
                val sysCap = lines.getOrNull(0)?.toFloatOrNull()
                val sysAc = lines.getOrNull(1)?.toIntOrNull()
                fun field(n: String) = out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("$n:") }?.substringAfter(':')?.trim()
                val l = sysCap ?: field("level")?.toFloatOrNull()
                val ac = if (sysAc != null) sysAc == 1 else (field("AC powered") == "true" || field("USB powered") == "true")
                runOnUiThread {
                    if (l != null) level = l.coerceIn(0f, 100f)
                    charging = ac
                }
            }
        }
        AlertDialog(onDismissRequest = onDone,
            title = { Text(stringResource(R.string.m_battery)) },
            text = {
                Column {
                    Text("${level.toInt()}%")
                    Slider(level, { level = it }, valueRange = 0f..100f, enabled = !follow)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.m_charging), Modifier.weight(1f)); Switch(charging, { charging = it }, enabled = !follow)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.m_battery_follow), Modifier.weight(1f))
                        Switch(follow, { v ->
                            follow = v
                            if (v) {
                                val hb = vm.hostBattery()
                                level = hb.first.toFloat().coerceIn(0f, 100f)
                                charging = hb.second
                                vm.startBatteryFollow()
                            } else vm.stopBatteryFollow()
                            thread { ImageStore.update(this@VmActivity, vm.img.id) { it.copy(settings = it.settings.copy(batteryFollow = v)) } }
                        })
                    }
                }
            },
            confirmButton = { Button(onClick = {
                if (follow) onDone() else {
                val lvl = level.toInt()
                val ac = if (charging) 1 else 0
                val cmd = "echo $lvl > /sys/class/power_supply/battery/capacity 2>/dev/null; " +
                    "echo $ac > /sys/class/power_supply/ac/online 2>/dev/null; " +
                    "dumpsys battery set level $lvl 2>/dev/null; " +
                    "dumpsys battery set ac $ac 2>/dev/null; " +
                    "dumpsys battery set usb 0 2>/dev/null"
                guestAsync(cmd)
                onDone()
                }
            }) { Text(stringResource(R.string.m_apply)) } },
            dismissButton = { OutlinedButton(onClick = {
                guestAsync("dumpsys battery reset 2>/dev/null")
                onDone()
            }) { Text(stringResource(R.string.m_reset)) } })
    }

    private fun rebootVm(recovery: Boolean = false) {
        val id = vm.img.id
        thread {
            vm.stop()
            VmHost.vm = null
            // the GL bridge cannot come up twice in one process, so a reboot restarts the :vm process
            runOnUiThread { restartProcess(id, recovery) }
        }
    }

    private fun restartProcess(id: String, recovery: Boolean = false) {
        val i = Intent(this, VmActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_RECOVERY, recovery)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = android.app.PendingIntent.getActivity(this, 1, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_CANCEL_CURRENT)
        (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 400, pi)
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // ------------------------------------------------------------------ интерфейс поверх экрана

    @Composable
    private fun Overlay() {
        var showLog by remember { mutableStateOf(false) }
        var showTerm by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf(Dlg.NONE) }
        // recovery is "running" too, but has no Android behind am/pm/settings
        val running = state == GuestVm.State.RUNNING && !vm.recoveryMode
        var seconds by remember { mutableStateOf(0L) }
        var frames by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { if (vm.recoveryMode) guest.format = vm.recoveryFormat else if (vm.engine == app.aemu.core.Engine.N64) guest.format = 1; seconds = vm.bootSeconds(); frames = if (vm.glInApp) dev.lk.m7sense.GlBridge.frames() else guest.rings; delay(500) } }
        // как только гость начал рисовать — карточку убираем, остаётся маленький индикатор
        val drawing = frames > 30
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // the menu button stays on screen: 40 dp button + 8 dp padding, anchored at the top end
            val keep = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
            val maxX = (constraints.maxWidth - keep).coerceAtLeast(0f)
            val maxY = (constraints.maxHeight - keep * 2).coerceAtLeast(0f)
            val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
            fun clamp(o: Offset) = Offset(if (rtl) o.x.coerceIn(0f, maxX) else o.x.coerceIn(-maxX, 0f), o.y.coerceIn(0f, maxY))
            LaunchedEffect(maxX, maxY) { menuOffset = clamp(menuOffset) }
            val navActive = remember { vm.settings.navKeys.split(',').any { it in app.aemu.core.VmSettings.NAV_ALL } } && vm.settings.showNavBar && !showLog && !showTerm
            if (navActive && !navCollapsed) {
                val dockPx = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) return@awaitEachGesture
                        val x = down.position.x
                        val y = down.position.y
                        if (x < navRect.left || x > navRect.right || y < navRect.top || y > navRect.bottom) {
                            down.consume()
                            navCollapsed = true
                            navIconPos = Offset((navRect.left + navRect.right - dockPx) / 2f,
                                (navRect.top + navRect.bottom - dockPx) / 2f)
                            saveNavPos()
                        }
                    }
                })
            }
            led?.takeIf { it.lit && showLed }?.let { LedIndicator(it, Modifier.align(Alignment.TopCenter)) }
            // карточка загрузки
            AnimatedVisibility(
                visible = state != GuestVm.State.RUNNING && !showLog && !(drawing && state == GuestVm.State.BOOTING),
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) { BootCard(seconds) }

            if (drawing && state == GuestVm.State.BOOTING && !showLog && vm.screenUpAt == 0L) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopStart).displayCutoutPadding().statusBarsPadding().padding(8.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.vm_booting_short, seconds.toInt()), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // журнал
            AnimatedVisibility(visible = showLog, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                LogPanel(onClose = { showLog = false })
            }
            AnimatedVisibility(visible = showTerm, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                TerminalPanel(onClose = { showTerm = false })
            }

            // top menu button (hidden while the log is open so it does not cover the log toolbar); drag to move it
            if (!showLog && !showTerm) Box(Modifier.align(Alignment.TopEnd).displayCutoutPadding().statusBarsPadding().padding(8.dp)
                .offset { IntOffset(menuOffset.x.roundToInt(), menuOffset.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { saveMenuOffset() }) { ch, d -> ch.consume(); menuOffset = clamp(menuOffset + d) }
                }) {
                FilledTonalIconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.log)) }, leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
                        onClick = { menu = false; showLog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.m_terminal)) }, leadingIcon = { Icon(Icons.Rounded.Code, null) },
                        enabled = running, onClick = { menu = false; showTerm = true })
                    // the machine keeps running behind its notification
                    DropdownMenuItem(text = { Text(stringResource(R.string.m_minimize)) }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        onClick = { menu = false; moveTaskToBack(true) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.vol_up)) }, leadingIcon = { Icon(Icons.Rounded.VolumeUp, null) },
                        onClick = { vm.input.press(InputService.KEY_VOLUMEUP) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.vol_down)) }, leadingIcon = { Icon(Icons.Rounded.VolumeDown, null) },
                        onClick = { vm.input.press(InputService.KEY_VOLUMEDOWN) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.power)) }, leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
                        onClick = { menu = false; vm.input.press(InputService.KEY_POWER) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.m_power_menu)) }, leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
                        onClick = { menu = false; vm.input.press(InputService.KEY_POWER, 1500) })
                    // long-press Menu makes 2.x–4.x call InputMethodManager.toggleSoftInput: the firmware's own keyboard
                    if (!vm.recoveryMode) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_keyboard)) }, leadingIcon = { Icon(Icons.Rounded.Keyboard, null) },
                            enabled = running, onClick = {
                                menu = false
                                // 5.0+ hides the soft keyboard while it sees hardware keys and has no long-Menu toggle
                                if (vm.img.api >= 21) guestAsync("settings put secure show_ime_with_hard_keyboard 1")
                                else vm.input.press(InputService.KEY_MENU, 1000)
                            })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_paste)) }, leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
                            enabled = running, onClick = { menu = false; pasteToGuest() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_sms)) }, leadingIcon = { Icon(Icons.Rounded.Sms, null) },
                            enabled = running, onClick = { menu = false; dialog = Dlg.SMS })
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_call)) }, leadingIcon = { Icon(Icons.Rounded.Call, null) },
                            enabled = running, onClick = { menu = false; dialog = Dlg.CALL })
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_battery)) }, leadingIcon = { Icon(Icons.Rounded.BatteryStd, null) },
                            enabled = running, onClick = { menu = false; dialog = Dlg.BATTERY })
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_install_apk)) }, leadingIcon = { Icon(Icons.Rounded.InstallMobile, null) },
                            enabled = running, onClick = { menu = false; pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_device)) }, leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                            enabled = running, onClick = { menu = false; dialog = Dlg.DEVICE })
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_adb)) }, leadingIcon = { Icon(Icons.Rounded.Lan, null) },
                            enabled = running, onClick = { menu = false; dialog = Dlg.ADB })
                        DropdownMenuItem(text = { Text(stringResource(R.string.m_settings)) }, leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                            enabled = running, onClick = { menu = false; guestAsync("am start -a android.settings.SETTINGS") })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.m_screenshot)) }, leadingIcon = { Icon(Icons.Rounded.Screenshot, null) },
                        enabled = state == GuestVm.State.RUNNING, onClick = { menu = false; screenshot() })
                    DropdownMenuItem(text = { Text(stringResource(if (recOn.value) R.string.m_record_stop else R.string.m_record)) },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                        enabled = state == GuestVm.State.RUNNING || recOn.value, onClick = { menu = false; toggleRecording() })
                    DropdownMenuItem(text = { Text(stringResource(if (vncOn.value) R.string.m_vnc_stop else R.string.m_vnc)) },
                        leadingIcon = { Icon(Icons.Rounded.Cast, null) },
                        onClick = { menu = false; toggleVnc() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.m_reset_menu_pos)) }, leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                        onClick = { menu = false; menuOffset = Offset.Zero; saveMenuOffset() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.m_reboot)) }, leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                        onClick = { menu = false; rebootVm() })
                    DropdownMenuItem(text = { Text(stringResource(if (vm.recoveryMode) R.string.m_reboot_system else R.string.m_reboot_recovery)) },
                        leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                        enabled = vm.recoveryMode || app.aemu.core.RecoveryImage.installed(vm.paths),
                        onClick = { menu = false; rebootVm(!vm.recoveryMode) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.shutdown)) }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        onClick = { menu = false; stopVm() })
                }
            }

            when (dialog) {
                Dlg.SMS -> SmsDialog { dialog = Dlg.NONE }
                Dlg.CALL -> CallDialog { dialog = Dlg.NONE }
                Dlg.BATTERY -> BatteryDialog { dialog = Dlg.NONE }
                Dlg.ADB -> AdbDialog { dialog = Dlg.NONE }
                Dlg.DEVICE -> DeviceDialog { dialog = Dlg.NONE }
                Dlg.NONE -> {}
            }

            // панель кнопок Android
            if (navActive) {
                FloatingNavBar(Modifier.align(Alignment.BottomCenter), constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            }
        }
    }

    /** The bar of guest buttons: the user's own set, each held down in the guest for as long as it is held here. */
    @Composable
    private fun FloatingNavBar(base: Modifier, maxW: Float, maxH: Float) {
        val keys = remember { vm.settings.navKeys.split(',').filter { it in app.aemu.core.VmSettings.NAV_ALL } }
        if (keys.isEmpty()) return
        val compact = vm.settings.navSize == 0
        var anchor by remember { mutableStateOf(Offset.Zero) }
        var pill by remember { mutableStateOf(IntSize.Zero) }
        val drawn = remember { arrayOf(Offset.Zero) }
        val d = androidx.compose.ui.platform.LocalDensity.current
        val margin = with(d) { 2.dp.toPx() }
        val dock = with(d) { 40.dp.toPx() }
        fun clampPill(p: Offset): Offset {
            val w = pill.width.toFloat().coerceAtLeast(dock)
            val h = pill.height.toFloat().coerceAtLeast(dock)
            return Offset(p.x.coerceIn(margin, (maxW - w - margin).coerceAtLeast(margin)),
                p.y.coerceIn(margin, (maxH - h - margin).coerceAtLeast(margin)))
        }
        LaunchedEffect(maxW, maxH) {
            if (navIconPos == Offset.Zero) {
                navIconPos = Offset((maxW - dock) / 2f, maxH - dock - margin * 6f)
                saveNavPos()
            }
        }
        if (navCollapsed) {
            val c = Offset(navIconPos.x.coerceIn(margin, (maxW - dock - margin).coerceAtLeast(margin)),
                navIconPos.y.coerceIn(margin, (maxH - dock - margin).coerceAtLeast(margin)))
            Box(Modifier.offset { IntOffset(c.x.roundToInt(), c.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { saveNavPos() }) { ch, dg ->
                        ch.consume()
                        navIconPos = Offset((navIconPos.x + dg.x).coerceIn(margin, (maxW - dock - margin).coerceAtLeast(margin)),
                            (navIconPos.y + dg.y).coerceIn(margin, (maxH - dock - margin).coerceAtLeast(margin)))
                    }
                }) {
                FilledTonalIconButton(onClick = {
                    navCollapsed = false
                    if (pill.width > 0) {
                        val tx = (c.x + dock / 2 - pill.width / 2).coerceIn(margin, (maxW - pill.width - margin).coerceAtLeast(margin))
                        val ty = (c.y + dock / 2 - pill.height / 2).coerceIn(margin, (maxH - pill.height - margin).coerceAtLeast(margin))
                        navDelta = Offset(tx - anchor.x, ty - anchor.y)
                    }
                    saveNavPos()
                }, modifier = Modifier.size(40.dp)) {
                    val near = listOf(c.x + dock / 2, maxW - c.x - dock / 2, c.y + dock / 2, maxH - c.y - dock / 2)
                    Icon(when (near.indexOf(near.min())) {
                        0 -> Icons.Rounded.KeyboardArrowRight
                        1 -> Icons.Rounded.KeyboardArrowLeft
                        2 -> Icons.Rounded.KeyboardArrowDown
                        else -> Icons.Rounded.KeyboardArrowUp
                    }, null)
                }
            }
            return
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
            modifier = base.navigationBarsPadding()
                .padding(bottom = if (compact) 3.dp else 6.dp)
                .offset {
                    val p = clampPill(Offset(anchor.x + navDelta.x, anchor.y + navDelta.y))
                    val o = Offset(p.x - anchor.x, p.y - anchor.y)
                    drawn[0] = o
                    IntOffset(o.x.roundToInt(), o.y.roundToInt())
                }
                .onGloballyPositioned {
                    val p = it.positionInRoot()
                    pill = it.size; anchor = p - drawn[0]
                    navRect = Rect(p.x, p.y, p.x + it.size.width, p.y + it.size.height)
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { saveNavPos() }, onDragCancel = { saveNavPos() }) { ch, d -> ch.consume(); navDelta += d }
                }) {
            Row(Modifier.padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 2.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 4.dp)) {
                for (k in keys) NavKey(k, if (compact) 36.dp else 48.dp)
            }
        }
    }

    @Composable
    private fun NavKey(k: String, size: androidx.compose.ui.unit.Dp) {
        var pressed by remember { mutableStateOf(false) }
        val bg by androidx.compose.animation.animateColorAsState(
            if (pressed) Color(0x26000000) else Color.Transparent, label = "key")
        val iconTint = MaterialTheme.colorScheme.onSecondaryContainer
        val code = when (k) {
            "back" -> InputService.KEY_BACK; "home" -> InputService.KEY_HOME; "menu" -> InputService.KEY_MENU
            "search" -> InputService.KEY_SEARCH; "volup" -> InputService.KEY_VOLUMEUP; "voldown" -> InputService.KEY_VOLUMEDOWN
            "power" -> InputService.KEY_POWER; else -> 0
        }
        val step = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
        Box(Modifier.size(size).background(bg, CircleShape).pointerInput(k) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                val t0 = android.os.SystemClock.uptimeMillis()
                var acc = Offset.Zero
                var moved = false
                var hold = k == "dpad"
                var tap = false
                while (!hold && !tap) {
                    val e = withTimeoutOrNull(120) { awaitPointerEvent() } ?: run { hold = true; break }
                    val c = e.changes.firstOrNull { it.id == down.id } ?: run { hold = true; break }
                    if (c.isConsumed) break
                    val dx = c.position.x - down.position.x
                    val dy = c.position.y - down.position.y
                    if (dx * dx + dy * dy > slop * slop) break
                    if (!c.pressed) tap = true
                }
                if (tap) {
                    when (k) {
                        "home" -> vm.input.press(InputService.KEY_HOME, 60)
                        "recents" -> vm.input.recents(vm.img.api, vm.img.skin)
                        else -> if (code != 0) vm.input.press(code, 60)
                    }
                    return@awaitEachGesture
                }
                if (!hold) return@awaitEachGesture
                pressed = true
                if (k == "home") vm.input.home(true)
                else if (code != 0) vm.input.key(code, true)
                // the D-pad button is a little trackball: sliding the finger sends arrows, a tap selects
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (k == "dpad") {
                        acc += c.position - c.previousPosition
                        while (kotlin.math.abs(acc.x) >= step || kotlin.math.abs(acc.y) >= step) {
                            moved = true
                            if (kotlin.math.abs(acc.x) >= kotlin.math.abs(acc.y)) {
                                vm.input.press(if (acc.x > 0) InputService.KEY_RIGHT else InputService.KEY_LEFT, 30)
                                acc = Offset(acc.x - step * kotlin.math.sign(acc.x), acc.y)
                            } else {
                                vm.input.press(if (acc.y > 0) InputService.KEY_DOWN else InputService.KEY_UP, 30)
                                acc = Offset(acc.x, acc.y - step * kotlin.math.sign(acc.y))
                            }
                        }
                    }
                    c.consume()
                    if (!c.pressed) break
                }
                pressed = false
                if (k == "home") vm.input.home(false)
                else if (code != 0) vm.input.key(code, false)
                else if (k == "recents") vm.input.recents(vm.img.api, vm.img.skin)
                else if (k == "dpad" && !moved) vm.input.press(InputService.KEY_CENTER, if (android.os.SystemClock.uptimeMillis() - t0 > 500) 700 else 80)
            }
        }, contentAlignment = Alignment.Center) {
            val d = navKeyLabel(k)
            val m = Modifier.size(if (size < 40.dp) 20.dp else 24.dp)
            when (k) {
                "back" -> Icon(Icons.AutoMirrored.Rounded.ArrowBack, d, m, tint = iconTint)
                "home" -> Icon(Icons.Rounded.Circle, d, m, tint = iconTint)
                "recents" -> Icon(Icons.Rounded.CropSquare, d, m, tint = iconTint)
                "menu" -> Icon(Icons.Rounded.Menu, d, m, tint = iconTint)
                "search" -> Icon(Icons.Rounded.Search, d, m, tint = iconTint)
                "volup" -> Icon(Icons.Rounded.VolumeUp, d, m, tint = iconTint)
                "voldown" -> Icon(Icons.Rounded.VolumeDown, d, m, tint = iconTint)
                "power" -> Icon(Icons.Rounded.PowerSettingsNew, d, m, tint = iconTint)
                else -> Icon(Icons.Rounded.Gamepad, d, m, tint = iconTint)
            }
        }
    }

    /**
     * The guest's LED above its screen: Sony's illumination bar as a glowing strip, other phones' notification LED as
     * a dot. Blinks with the guest's on/off times.
     */
    @Composable
    private fun LedIndicator(l: app.aemu.core.Lights.Led, modifier: Modifier) {
        var on by remember(l) { mutableStateOf(true) }
        LaunchedEffect(l) {
            if (l.onMs > 0 && l.offMs > 0) while (true) { on = true; delay(l.onMs.toLong()); on = false; delay(l.offMs.toLong()) }
        }
        val alpha by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.08f,
            androidx.compose.animation.core.tween(if (on) 120 else 260), label = "led")
        val c = Color(0xff000000.toInt() or (l.color and 0xffffff))
        val sony = vm.img.skin.contains("Xperia", true) || vm.img.brand.contains("Sony", true)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val y = with(density) { (guestTopPx.toDp() - 10.dp).coerceAtLeast(4.dp) }
        if (sony) {
            Box(
                modifier.offset(y = y)
                    .width(160.dp)
                    .height(6.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                c.copy(alpha = 0.4f),
                                c,
                                c.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier.offset(y = y).size(14.dp)
                    .alpha(alpha)
                    .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c, c.copy(alpha = 0.55f), Color.Transparent)), CircleShape)
            )
        }
    }

    @Composable
    private fun BootCard(seconds: Long) {
        Card(
            modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state == GuestVm.State.FAILED) {
                    Text(stringResource(R.string.failed_start), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(vm.failure ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { stopVm() }) { Text(stringResource(R.string.close)) }
                        Button(onClick = { thread { vm.stop(); vm.boot() } }) { Text(stringResource(R.string.retry)) }
                    }
                } else {
                    LoadingIndicator(Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(vm.img.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vm.img.displayVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    val phase = when (state) {
                        GuestVm.State.PREPARING -> stringResource(R.string.st_preparing)
                        GuestVm.State.BOOTING -> stringResource(R.string.st_booting, seconds.toInt())
                        GuestVm.State.STOPPING -> stringResource(R.string.st_stopping)
                        else -> "…"
                    }
                    Text(phase, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (vm.img.bootCount == 0 && state == GuestVm.State.BOOTING) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.first_boot_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(logLines.lastOrNull()?.substringAfter(' ') ?: "", style = Mono, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun LogPanel(onClose: () -> Unit) {
        var guestTab by remember { mutableStateOf(false) }
        val guestLines = remember { mutableStateListOf<String>() }
        LaunchedEffect(guestTab) {
            while (guestTab) {
                val recs = GuestLog.tail(vm.paths.root, 128 * 1024).takeLast(300)
                guestLines.clear(); guestLines.addAll(recs.map { "${it.prio} ${it.tag}: ${it.msg}" })
                delay(1500)
            }
        }
        val list = if (guestTab) guestLines else logLines
        Surface(Modifier.fillMaxSize(), color = Color(0xF0101410), contentColor = Color(0xFFE2E3DD)) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.log), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { copyLog(list.toList()) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.log_copy)) }
                    IconButton(onClick = { pendingExport = list.toList(); exportLog.launch("aemu-${vm.img.id}-${if (guestTab) "logcat" else "host"}.log") }) {
                        Icon(Icons.Rounded.Download, stringResource(R.string.log_export))
                    }
                    FilledTonalIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !guestTab, onClick = { guestTab = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.host), maxLines = 1)
                    }
                    SegmentedButton(selected = guestTab, onClick = { guestTab = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.guest_log), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val st = rememberLazyListState()
                LaunchedEffect(list.size) { if (list.isNotEmpty()) st.scrollToItem(list.size - 1) }
                SelectionContainer {
                    LazyColumn(state = st, modifier = Modifier.fillMaxSize()) {
                        items(list) { Text(it, style = Mono, color = Color(0xFFCFE8CF)) }
                    }
                }
            }
        }
    }

    // shell terminal into the guest: its lines survive closing and reopening the panel
    private val termLines = mutableStateListOf<String>()
    private val termHistory = ArrayList<String>()

    @Composable
    private fun TerminalPanel(onClose: () -> Unit) {
        var cmd by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var histPos by remember { mutableStateOf(-1) }
        fun run() {
            val c = cmd.trim()
            if (c.isEmpty() || busy) return
            cmd = ""; histPos = -1
            termHistory.remove(c); termHistory.add(c)
            if (c == "clear") { termLines.clear(); return }
            termLines.add("$ $c")
            busy = true
            thread {
                val out = runCatching { vm.guestShell(c, 180_000) }.getOrElse { it.toString() }
                runOnUiThread {
                    termLines.addAll(out.trimEnd('\n').lines())
                    while (termLines.size > 3000) termLines.removeAt(0)
                    busy = false
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = Color(0xF0101410), contentColor = Color(0xFFE2E3DD)) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.m_terminal), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (busy) LoadingIndicator(Modifier.size(32.dp))
                    IconButton(onClick = { copyLog(termLines.toList()) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.log_copy)) }
                    FilledTonalIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                }
                Spacer(Modifier.height(8.dp))
                val st = rememberLazyListState()
                LaunchedEffect(termLines.size) { if (termLines.isNotEmpty()) st.scrollToItem(termLines.size - 1) }
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(state = st, modifier = Modifier.fillMaxSize()) {
                        items(termLines) { Text(it, style = Mono, color = if (it.startsWith("$ ")) Color(0xFF9AD0FF) else Color(0xFFCFE8CF)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (termHistory.isNotEmpty()) {
                            histPos = if (histPos <= 0) termHistory.size - 1 else histPos - 1
                            cmd = termHistory[histPos]
                        }
                    }) { Icon(Icons.Rounded.History, null) }
                    OutlinedTextField(cmd, { cmd = it }, modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = Mono, placeholder = { Text("getprop ro.build.version.release", style = Mono) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                            autoCorrectEnabled = false, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { run() }))
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = { run() }, enabled = !busy && cmd.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.m_send))
                    }
                }
            }
        }
    }

    private var pendingExport: List<String> = emptyList()
    private val exportLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        val lines = pendingExport
        thread {
            val ok = runCatching { contentResolver.openOutputStream(uri)!!.bufferedWriter().use { w -> lines.forEach { w.write(it); w.write("\n") } } }.isSuccess
            runOnUiThread { Toast.makeText(this, if (ok) R.string.log_exported else R.string.log_export_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun copyLog(lines: List<String>) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("AEmulator log", lines.joinToString("\n")))
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_RECOVERY = "recovery"
        fun start(ctx: Context, id: String, recovery: Boolean = false) {
            ctx.startActivity(Intent(ctx, VmActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_RECOVERY, recovery)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
