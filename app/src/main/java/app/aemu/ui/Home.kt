package app.aemu.ui

import android.text.format.Formatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aemu.R
import app.aemu.core.GuestImage

/**
 * The vendor's look for a machine card: a "screen" gradient that tells TouchWiz, MIUI, Sense, Xperia… apart at a
 * glance (artwork of the card, not UI chrome — the chrome around it stays on theme tokens).
 */
private fun skinColors(img: GuestImage): List<Color> {
    val s = (img.skin + " " + img.brand).lowercase()
    return when {
        "miui" in s || "hyperos" in s -> listOf(Color(0xFFFF8A3D), Color(0xFFE2462E))
        "touchwiz" in s || "one ui" in s || "samsung" in s -> listOf(Color(0xFF3D7BFF), Color(0xFF1B2F9E))
        "sense" in s || "htc" in s -> listOf(Color(0xFF7BC74D), Color(0xFF2E7D32))
        "xperia" in s || "sony" in s -> listOf(Color(0xFF8E7CFF), Color(0xFF3B2A8F))
        "coloros" in s || "realme" in s || "oppo" in s -> listOf(Color(0xFF2FD6A7), Color(0xFF0E7C68))
        "xos" in s || "xui" in s || "hios" in s || "infinix" in s || "tecno" in s -> listOf(Color(0xFF39C8F0), Color(0xFF1765B5))
        "motoblur" in s || "motorola" in s -> listOf(Color(0xFF5C6BC0), Color(0xFF263238))
        "lg" in s -> listOf(Color(0xFFE5486B), Color(0xFF8E1F3E))
        else -> listOf(Color(0xFF34C08E), Color(0xFF0B6E66))   // AOSP and the rest: Android green-teal
    }
}

/** A machine (a firmware with its own data and settings) as a little phone: tap to start, menu for the rest. */
@Composable
fun MachineCard(
    img: GuestImage,
    baseName: String?,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onRename: () -> Unit,
    onNewMachine: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // expressive press: the card gives a little under the finger and springs back
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        onClick = onStart,
        interactionSource = press,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(
            Modifier.padding(8.dp).fillMaxWidth().aspectRatio(0.9f).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(skinColors(img))),
        ) {
            Text(img.skin, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.9f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp, end = 48.dp))
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.menu), tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                        onClick = { menu = false; onSettings() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.new_machine)) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; onNewMachine() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
            Column(Modifier.align(Alignment.Center).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(img.release.substringBefore(' ').take(5), style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text("API ${img.api}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            }
            FilledIconButton(
                onClick = onStart,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = skinColors(img).last()),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(44.dp),
            ) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.start)) }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            Text(img.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    img.settings.let { "${it.width}×${it.height}" },
                    Formatter.formatShortFileSize(ctx, img.sizeBytes),
                    if (img.lastBootMs > 0) "${img.lastBootMs / 1000} s" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            if (baseName != null) Text(stringResource(R.string.container_of, baseName), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "+": an M3 Expressive FAB menu — import a firmware file, or open the firmware library. */
@Composable
fun HomeFab(expanded: Boolean, onToggle: (Boolean) -> Unit, onImport: () -> Unit, onLibrary: () -> Unit) {
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(checked = expanded, onCheckedChange = onToggle) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add } }
                Icon(rememberVectorPainter(icon), stringResource(R.string.add_firmware), Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        FloatingActionButtonMenuItem(onClick = { onToggle(false); onImport() }, icon = { Icon(Icons.Rounded.FileOpen, null) },
            text = { Text(stringResource(R.string.add_import)) })
        FloatingActionButtonMenuItem(onClick = { onToggle(false); onLibrary() }, icon = { Icon(Icons.Rounded.Inventory2, null) },
            text = { Text(stringResource(R.string.lib_title)) })
    }
}

/**
 * The firmware library (VMOS-style): every imported firmware with "new machine", plus firmware files waiting in
 * Internal storage/aemulator/firmware and the import button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheet(
    firmwares: List<GuestImage>,
    onDismiss: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: (java.io.File) -> Unit,
    onNewMachine: (GuestImage) -> Unit,
) {
    var files by remember { mutableStateOf(emptyList<java.io.File>()) }
    DisposableEffect(Unit) {
        val t = Thread { files = app.aemu.core.FirmwareFolder.list() }.apply { start() }
        onDispose { t.interrupt() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lib_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(onClick = onImportFile) {
                    Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_import))
                }
            }
            if (firmwares.isEmpty()) Text(stringResource(R.string.lib_empty), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (fw in firmwares) {
                ListItem(
                    headlineContent = { Text(fw.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${fw.displayVersion} · ${fw.skin}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(skinColors(fw))), contentAlignment = Alignment.Center) {
                            Text(fw.release.take(3), style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                    },
                    trailingContent = { FilledTonalButton(onClick = { onNewMachine(fw) }) { Text(stringResource(R.string.new_machine)) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.clip(MaterialTheme.shapes.large),
                )
            }
            if (files.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.fw_folder_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                for (f in files.take(12)) {
                    ListItem(
                        headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${f.length() / 1_048_576} MB") },
                        trailingContent = { FilledTonalButton(onClick = { onImportFolder(f) }) { Text(stringResource(R.string.fw_folder_import)) } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.clip(MaterialTheme.shapes.large),
                    )
                }
            }
        }
    }
}
