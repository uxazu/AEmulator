package app.aemu.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import app.aemu.BuildConfig
import app.aemu.update.AppUpdateManager
import app.aemu.update.ReleaseInfo
import app.aemu.update.UpdateState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aemu.core.GuestImage
import app.aemu.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.Settings
import android.content.Context

const val ACTION_BOOT = "app.aemu.BOOT"

class MainActivity : ComponentActivity() {
    private val model: LibraryModel by viewModels()

    override fun attachBaseContext(base: Context) = super.attachBaseContext(app.aemu.AppPrefs.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askPermissions()
        handleView(intent)
        setContent { AemuTheme { Library(model) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleView(intent)
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    private fun handleView(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { model.import(it) }
        // ярлыки и автоматизация: am start -a app.aemu.BOOT --es id <образ>
        if (i?.action == ACTION_BOOT) i.getStringExtra("id")?.let { VmActivity.start(this, it, i.getBooleanExtra("recovery", false)) }
    }

    private fun askPermissions() {
        val want = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) want += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 32) want += Manifest.permission.WRITE_EXTERNAL_STORAGE
        val need = want.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }
}

@Composable
fun Library(model: LibraryModel) {
    val images by model.images.collectAsState()
    val imp by model.import.collectAsState()
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.import(it) } }
    var settingsFor by remember { mutableStateOf<GuestImage?>(null) }
    var deleteFor by remember { mutableStateOf<GuestImage?>(null) }
    var renameFor by remember { mutableStateOf<GuestImage?>(null) }
    var help by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(false) }
    var containerFrom by remember { mutableStateOf<GuestImage?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        if (app.aemu.AppPrefs.autoCheckUpdates(ctx)) {
            val now = System.currentTimeMillis()
            if (now - app.aemu.AppPrefs.lastUpdateCheck(ctx) > 3600_000L) {
                app.aemu.AppPrefs.setLastUpdateCheck(ctx, now)
                val res = AppUpdateManager.checkLatestRelease()
                res.getOrNull()?.let { rel ->
                    if (AppUpdateManager.isNewerVersion(rel.versionName, BuildConfig.VERSION_NAME)) {
                        updateState = UpdateState.Available(rel)
                    }
                }
            }
        }
    }

    fun startDownload(rel: ReleaseInfo) {
        updateState = UpdateState.Downloading(rel, 0f, 0L, rel.sizeBytes)
        downloadJob = scope.launch {
            try {
                val file = AppUpdateManager.downloadApk(ctx, rel) { progress, downloaded, total ->
                    updateState = UpdateState.Downloading(rel, progress, downloaded, total)
                }
                updateState = UpdateState.ReadyToInstall(rel, file)
                AppUpdateManager.installApk(ctx, file)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    updateState = UpdateState.Error(e.localizedMessage ?: "Ошибка загрузки")
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("AEmulator") },
                subtitle = { Text(stringResource(R.string.lib_subtitle)) },
                actions = { IconButton(onClick = { help = true }) { Icon(Icons.Rounded.Info, stringResource(R.string.help)) }
                    IconButton(onClick = { ctx.startActivity(Intent(ctx, AppSettingsActivity::class.java)) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) } },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            HomeFab(expanded = addMenu, onToggle = { addMenu = it },
                onImport = { pick.launch(arrayOf("*/*")) }, onLibrary = { library = true })
        },
    ) { pad ->
        val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan =
            { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(148.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = full) { StorageAccessCard() }
            item(span = full) {
                AnimatedVisibility(imp.active || imp.error != null || imp.done != null) {
                    ImportCard(imp, onCancel = model::cancelImport, onDismiss = model::dismissImport)
                }
            }
            if (images.isEmpty() && !imp.active) item(span = full) { EmptyState(onHelp = { help = true }) }
            items(images.size, key = { images[it].id }) { i ->
                val img = images[i]
                MachineCard(
                    img,
                    baseName = images.firstOrNull { it.id == img.baseId }?.name,
                    modifier = Modifier.animateItem(),
                    onStart = { VmActivity.start(ctx, img.id) },
                    onSettings = { settingsFor = img },
                    onRename = { renameFor = img },
                    onNewMachine = { containerFrom = img },
                    onDelete = { deleteFor = img },
                )
            }
        }
    }

    settingsFor?.let { img ->
        SettingsSheet(img, onDismiss = { settingsFor = null }, onSave = { s -> model.updateSettings(img, s); settingsFor = null })
    }
    deleteFor?.let { img ->
AlertDialog(
            onDismissRequest = { deleteFor = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.delete_title, img.name)) },
            text = { Text(stringResource(R.string.delete_text, Formatter.formatShortFileSize(ctx, img.sizeBytes))) },
            confirmButton = { Button(onClick = { model.delete(img); deleteFor = null },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    renameFor?.let { img ->
        var name by remember { mutableStateOf(img.name) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text(stringResource(R.string.name)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { Button(onClick = { model.rename(img, name.trim().ifEmpty { img.name }); renameFor = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (help) HelpDialog(onDismiss = { help = false })
    if (library) LibrarySheet(images.filter { it.baseId.isEmpty() }, onDismiss = { library = false },
        onImportFile = { library = false; pick.launch(arrayOf("*/*")) },
        onImportFolder = { f -> library = false; model.import(android.net.Uri.fromFile(f)) },
        onNewMachine = { library = false; containerFrom = it })
    containerFrom?.let { src -> ContainerDialog(src, images, onDismiss = { containerFrom = null },
        onCreate = { from, name, copy -> containerFrom = null; model.clone(from, name, copy) }) }

    UpdateDialog(
        state = updateState,
        onDismiss = { updateState = UpdateState.Idle },
        onStartDownload = ::startDownload,
        onCancelDownload = {
            downloadJob?.cancel()
            updateState = UpdateState.Idle
        },
        onInstall = { file -> AppUpdateManager.installApk(ctx, file) },
        onOpenUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
    )
}

@Composable
private fun ContainerDialog(src: GuestImage, images: List<GuestImage>, onDismiss: () -> Unit, onCreate: (GuestImage, String, Boolean) -> Unit) {
    var from by remember { mutableStateOf(src) }
    var name by remember(from) { mutableStateOf("${from.name} (${images.count { it.baseId == from.id || it.baseId == from.baseId.ifEmpty { "-" } } + 2})") }
    var copy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ContentCopy, null) },
        title = { Text(stringResource(R.string.add_container)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { img ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { from = img }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = from.id == img.id, onClick = { from = img })
                        Text("${img.name} · ${img.release}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Row(Modifier.fillMaxWidth().clickable { copy = !copy }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(copy, { copy = it })
                    Text(stringResource(R.string.container_copy_data))
                }
                Text(stringResource(R.string.container_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onCreate(from, name.trim().ifEmpty { from.name }, copy) }) { Text(stringResource(R.string.container_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Доступ ко всем файлам: общая папка гостя в «Внутренний накопитель/AEmulator». */
@Composable
private fun StorageAccessCard() {
    if (Build.VERSION.SDK_INT < 30) return
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
    val life = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = android.os.Environment.isExternalStorageManager()
        }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    if (granted) return
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.storage_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                runCatching {
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}")))
                }.onFailure { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }) { Text(stringResource(R.string.allow)) }
        }
    }
}

@Composable
private fun EmptyState(onHelp: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Android, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_text),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onHelp) { Text(stringResource(R.string.empty_help)) }
    }
}

@Composable
private fun ImportCard(s: ImportState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (s.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.active) LoadingIndicator(Modifier.size(40.dp)) else Icon(if (s.error != null) Icons.Rounded.Warning else Icons.Rounded.Android, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(when { s.error != null -> stringResource(R.string.import_failed); s.active -> stringResource(R.string.import_running); else -> stringResource(R.string.import_done) },
                        style = MaterialTheme.typography.titleMedium)
                    Text(s.file, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                s.error != null -> Text(s.error, style = MaterialTheme.typography.bodyMedium)
                s.active -> {
                    if (s.progress in 0f..1f) LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(s.step, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                s.done != null -> {
                    Text("${s.done.name} · ${s.done.displayVersion} · ${s.done.skin}", style = MaterialTheme.typography.bodyMedium)
                    s.done.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (s.active) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } else TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.help_text))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
