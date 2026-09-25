package app.aemu.ui

import app.aemu.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.content.Context
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
import kotlin.concurrent.thread

/** Процесс :vm держит ровно одну машину. */
object VmHost {
    @Volatile var vm: GuestVm? = null
}

class VmActivity : ComponentActivity() {
    private lateinit var vm: GuestVm
    private lateinit var surfaceView: SurfaceView
    private lateinit var guest: GuestScreen
    private lateinit var box: FrameLayout
    private var state by mutableStateOf(GuestVm.State.STOPPED)
    private val logLines = mutableStateListOf<String>()

    @SuppressLint("ClickableViewAccessibility")
    override fun attachBaseContext(base: Context) = super.attachBaseContext(app.aemu.AppPrefs.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        vm = if (cur != null && cur.img.id == img.id) cur else GuestVm(applicationContext, img).also { VmHost.vm = it }
        val s = vm.settings
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
        guest = GuestScreen(this, gw, gh).apply {
            input = vm.input
            fb = vm.paths.fb
        }
        val useBridge = vm.engine == Engine.KK && s.gpu
        surfaceView.visibility = if (useBridge) View.VISIBLE else View.GONE
        guest.passthrough = useBridge
        box.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        box.addView(guest, FrameLayout.LayoutParams(-1, -1))
        root.addView(box, FrameLayout.LayoutParams(0, 0))
        val overlay = ComposeView(this).apply { setContent { AemuTheme(forceDark = true) { Overlay() } } }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        // экран гостя вписываем с сохранением пропорций над панелью кнопок
        val navDp = if (s.showNavBar) 64 else 0
        root.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val navPx = (navDp * resources.displayMetrics.density).toInt()
            val aw = r - l
            val ah = b - t - navPx
            if (aw <= 0 || ah <= 0) return@addOnLayoutChangeListener
            val scale = minOf(aw.toFloat() / gw, ah.toFloat() / gh)
            val w = (gw * scale).toInt()
            val h = (gh * scale).toInt()
            val lp = box.layoutParams as FrameLayout.LayoutParams
            if (lp.width != w || lp.height != h) {
                lp.width = w; lp.height = h
                lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                lp.topMargin = (ah - h) / 2
                v.post { box.layoutParams = lp }
            }
        }

        vm.onFrame = { guest.rings++; guest.poke() }
        state = vm.state
        logLines.addAll(vm.lines().takeLast(200))
        vm.onState { st -> runOnUiThread { state = st } }
        vm.onLog { line -> runOnUiThread { logLines.add(line); if (logLines.size > 400) logLines.removeRange(0, logLines.size - 400) } }
        guest.start()

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
                val cmd = i.getStringExtra("cmd") ?: return
                thread {
                    val out = runCatching { vm.guestShell(cmd, 120_000) }.getOrElse { it.toString() }
                    java.io.File(vm.paths.bin, "shell.out").writeText(out)
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

    override fun onDestroy() {
        if (::guest.isInitialized) guest.stop()
        shellRx?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    private fun stopVm() {
        thread {
            vm.stop()
            VmHost.vm = null
            runOnUiThread { finishAndRemoveTask() }
            Thread.sleep(300)
            // GL-мост нельзя поднять повторно в том же процессе — процесс :vm уходит вместе с машиной
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun restartProcess(id: String) {
        val i = Intent(this, VmActivity::class.java).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = android.app.PendingIntent.getActivity(this, 1, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_CANCEL_CURRENT)
        (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 400, pi)
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // ------------------------------------------------------------------ интерфейс поверх экрана

    @Composable
    private fun Overlay() {
        var showLog by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }
        var seconds by remember { mutableStateOf(0L) }
        var frames by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { seconds = vm.bootSeconds(); frames = if (vm.glInApp) dev.lk.m7sense.GlBridge.frames() else guest.rings; delay(500) } }
        // как только гость начал рисовать — карточку убираем, остаётся маленький индикатор
        val drawing = frames > 30
        Box(Modifier.fillMaxSize()) {
            // карточка загрузки
            AnimatedVisibility(
                visible = state != GuestVm.State.RUNNING && !showLog && !(drawing && state == GuestVm.State.BOOTING),
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) { BootCard(seconds) }

            if (drawing && state == GuestVm.State.BOOTING && !showLog) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
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

            // верхняя кнопка меню
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                FilledTonalIconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.log)) }, leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
                        onClick = { menu = false; showLog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.vol_up)) }, leadingIcon = { Icon(Icons.Rounded.VolumeUp, null) },
                        onClick = { vm.input.press(InputService.KEY_VOLUMEUP) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.vol_down)) }, leadingIcon = { Icon(Icons.Rounded.VolumeDown, null) },
                        onClick = { vm.input.press(InputService.KEY_VOLUMEDOWN) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.power)) }, leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
                        onClick = { menu = false; vm.input.press(InputService.KEY_POWER) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.shutdown)) }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        onClick = { menu = false; stopVm() })
                }
            }

            // панель кнопок Android
            if (vm.settings.showNavBar) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
                ) {
                    IconButton(onClick = { vm.input.press(InputService.KEY_BACK) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    IconButton(onClick = { vm.input.press(InputService.KEY_HOME) }) { Icon(Icons.Rounded.Circle, stringResource(R.string.home)) }
                    if (vm.img.api >= 11) IconButton(onClick = { vm.input.press(InputService.KEY_APPSELECT) }) { Icon(Icons.Rounded.CropSquare, stringResource(R.string.recents)) }
                    IconButton(onClick = { vm.input.press(InputService.KEY_MENU) }) { Icon(Icons.Rounded.Menu, stringResource(R.string.menu)) }
                }
            }
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
        Surface(Modifier.fillMaxSize(), color = Color(0xF0101410)) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.log), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { guestTab = !guestTab }) { Text(if (guestTab) stringResource(R.string.host) else stringResource(R.string.guest_log)) }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                }
                Spacer(Modifier.height(8.dp))
                val list = if (guestTab) guestLines else logLines
                val st = rememberLazyListState()
                LaunchedEffect(list.size) { if (list.isNotEmpty()) st.scrollToItem(list.size - 1) }
                LazyColumn(state = st, modifier = Modifier.fillMaxSize()) {
                    items(list) { Text(it, style = Mono, color = Color(0xFFCFE8CF)) }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        fun start(ctx: Context, id: String) {
            ctx.startActivity(Intent(ctx, VmActivity::class.java).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
