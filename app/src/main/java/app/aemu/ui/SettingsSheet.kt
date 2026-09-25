package app.aemu.ui

import app.aemu.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aemu.core.Engine
import app.aemu.core.GuestImage
import app.aemu.core.VmSettings

private data class Res(val label: String, val w: Int, val h: Int, val dpi: Int)

private val RESOLUTIONS = listOf(
    Res("480×800", 480, 800, 240),
    Res("540×960", 540, 960, 240),
    Res("720×1280", 720, 1280, 320),
)

@Composable
fun SettingsSheet(img: GuestImage, onDismiss: () -> Unit, onSave: (VmSettings) -> Unit) {
    var s by remember { mutableStateOf(img.settings) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
            Text(img.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.vs_screen), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (s.legacyEngine && img.api < 14) {
                Text(stringResource(R.string.vs_fixed_2x), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                    RESOLUTIONS.forEachIndexed { i, r ->
                        ToggleButton(
                            checked = s.width == r.w && s.height == r.h,
                            onCheckedChange = { s = s.copy(width = r.w, height = r.h, density = r.dpi) },
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                RESOLUTIONS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        ) { Text(r.label) }
                    }
                }
                Text(stringResource(R.string.vs_screen_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.vs_perf), style = MaterialTheme.typography.titleMedium)
            Toggle(stringResource(R.string.vs_gpu), stringResource(R.string.vs_gpu_sub), s.gpu) { s = s.copy(gpu = it) }
            if (img.api >= 14) Toggle(stringResource(R.string.vs_hwui), stringResource(R.string.vs_hwui_sub), s.hwui) { s = s.copy(hwui = it) }
            Toggle(stringResource(R.string.vs_jit), stringResource(R.string.vs_jit_sub), s.jit) { s = s.copy(jit = it) }
            if (img.api < 14) Toggle(stringResource(R.string.vs_legacy), stringResource(R.string.vs_legacy_sub), s.legacyEngine) { s = s.copy(legacyEngine = it) }
            Toggle(stringResource(R.string.vs_lowram), stringResource(R.string.vs_lowram_sub), s.lowRam) { s = s.copy(lowRam = it) }
            Toggle(stringResource(R.string.vs_proxy), stringResource(R.string.vs_proxy_sub), s.netProxy) { s = s.copy(netProxy = it) }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.vs_controls), style = MaterialTheme.typography.titleMedium)
            Toggle(stringResource(R.string.vs_nav), stringResource(R.string.vs_nav_sub), s.showNavBar) { s = s.copy(showNavBar = it) }
            Toggle(stringResource(R.string.vs_awake), stringResource(R.string.vs_awake_sub), s.keepScreenOn) { s = s.copy(keepScreenOn = it) }
            Toggle(stringResource(R.string.vs_single), stringResource(R.string.vs_single_sub), s.mtMode == 4) { s = s.copy(mtMode = if (it) 4 else 0) }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(s) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Switch(checked = on, onCheckedChange = set) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    )
}
