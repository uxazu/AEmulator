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
import androidx.compose.runtime.setValue
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
        if (i?.action == ACTION_BOOT) i.getStringExtra("id")?.let { VmActivity.start(this, it) }
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
            ExtendedFloatingActionButton(
                onClick = { pick.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.add_firmware)) },
                expanded = images.isEmpty() || !scroll.state.collapsedFraction.let { it > 0.5f },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (images.isEmpty() && !imp.active) EmptyState(onHelp = { help = true })
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StorageAccessCard() }
                item {
                    AnimatedVisibility(imp.active || imp.error != null || imp.done != null) {
                        ImportCard(imp, onCancel = model::cancelImport, onDismiss = model::dismissImport)
                    }
                }
                items(images, key = { it.id }) { img ->
                    ImageCard(
                        img,
                        onStart = { VmActivity.start(ctx, img.id) },
                        onSettings = { settingsFor = img },
                        onRename = { renameFor = img },
                        onDelete = { deleteFor = img },
                    )
                }
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
private fun ImageCard(img: GuestImage, onStart: () -> Unit, onSettings: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(img.release.take(3), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(img.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(img.displayVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(img.skin) })
                AssistChip(onClick = {}, label = { Text("${img.settings.width}×${img.settings.height}") })
                AssistChip(onClick = {}, label = { Text(Formatter.formatShortFileSize(ctx, img.sizeBytes)) })
            }
            if (img.lastBootMs > 0) Text(stringResource(R.string.last_boot, (img.lastBootMs / 1000).toInt()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            img.warnings.firstOrNull()?.let { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStart, contentPadding = ButtonDefaults.ButtonWithIconContentPadding, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start))
                }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, stringResource(R.string.settings)) }
                FilledTonalIconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, stringResource(R.string.rename)) }
                FilledTonalIconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
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
