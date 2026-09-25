package app.aemu.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.aemu.AppPrefs
import app.aemu.BuildConfig
import app.aemu.R

/** Ссылки проекта — одни и те же в приложении, README и на сайте. */
object Links {
    const val SITE = "https://aemulator.gt.tc"
    const val GITHUB = "https://github.com/uxazu/aemulator"
    const val CHANNEL = "https://t.me/aemulatorofficial"
    const val AUTHOR = "https://github.com/uxazu"
    const val ORIGINAL = "https://t.me/istratii_tech"
    const val DONATE = "https://dalink.to/uxazu"
    const val USDT = "TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF"
    const val TON = "UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V"
}

class AppSettingsActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppPrefs.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AemuTheme { AppSettings(onBack = ::finish, onRestyle = ::restart) } }
    }

    /** Язык и тема применяются пересозданием активностей (главный экран — через onResume). */
    private fun restart() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        startActivity(Intent(this, AppSettingsActivity::class.java))
        overridePendingTransition(0, 0)
        finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun AppSettings(onBack: () -> Unit, onRestyle: () -> Unit) {
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var langDialog by remember { mutableStateOf(false) }
    var defaults by remember { mutableStateOf(AppPrefs.defaults(ctx)) }
    var theme by remember { mutableStateOf(AppPrefs.theme(ctx)) }
    var dynamic by remember { mutableStateOf(AppPrefs.dynamicColor(ctx)) }
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    fun copy(text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("address", text))
        Toast.makeText(ctx, ctx.getString(R.string.as_copied), Toast.LENGTH_SHORT).show()
    }
    val langTag = AppPrefs.language(ctx)
    val langName = AppPrefs.LANGUAGES.firstOrNull { it.first == langTag }?.second?.ifEmpty { null } ?: stringResource(R.string.as_lang_system)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AboutCard() }

            item {
                Section(stringResource(R.string.as_appearance)) {
                    Row_(Icons.Rounded.Translate, stringResource(R.string.as_language), langName) { langDialog = true }
                    Text(stringResource(R.string.as_theme), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
                    val options = listOf(R.string.as_theme_system, R.string.as_theme_light, R.string.as_theme_dark)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        options.forEachIndexed { i, res ->
                            ToggleButton(
                                checked = theme == i,
                                onCheckedChange = { theme = i; AppPrefs.setTheme(ctx, i); onRestyle() },
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                // одна строка: длинные названия («Системная») не переносятся по буквам
                                Text(stringResource(res), maxLines = 1, softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Toggle(stringResource(R.string.as_dynamic), stringResource(R.string.as_dynamic_sub), dynamic) {
                            dynamic = it; AppPrefs.setDynamicColor(ctx, it); onRestyle()
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.as_defaults), stringResource(R.string.as_defaults_sub)) {
                    fun set(s: app.aemu.core.VmSettings) { defaults = s; AppPrefs.setDefaults(ctx, s) }
                    Toggle(stringResource(R.string.vs_gpu), stringResource(R.string.vs_gpu_sub), defaults.gpu) { set(defaults.copy(gpu = it)) }
                    Toggle(stringResource(R.string.vs_jit), stringResource(R.string.vs_jit_sub), defaults.jit) { set(defaults.copy(jit = it)) }
                    Toggle(stringResource(R.string.vs_proxy), stringResource(R.string.vs_proxy_sub), defaults.netProxy) { set(defaults.copy(netProxy = it)) }
                    Toggle(stringResource(R.string.vs_nav), stringResource(R.string.vs_nav_sub), defaults.showNavBar) { set(defaults.copy(showNavBar = it)) }
                    Toggle(stringResource(R.string.vs_awake), stringResource(R.string.vs_awake_sub), defaults.keepScreenOn) { set(defaults.copy(keepScreenOn = it)) }
                }
            }

            item {
                Section(stringResource(R.string.as_links)) {
                    Row_(Icons.Rounded.Public, stringResource(R.string.as_site), "aemulator.gt.tc") { open(Links.SITE) }
                    Row_(Icons.Rounded.Send, stringResource(R.string.as_channel), "@aemulatorofficial") { open(Links.CHANNEL) }
                    Row_(Icons.Rounded.Person, stringResource(R.string.as_author), "uxazu") { open(Links.AUTHOR) }
                    Row_(Icons.Rounded.History, stringResource(R.string.as_orig), "t.me/istratii_tech") { open(Links.ORIGINAL) }
                    Row_(Icons.Rounded.Code, stringResource(R.string.as_github), "github.com/uxazu/aemulator") { open(Links.GITHUB) }
                }
            }

            item {
                Section(stringResource(R.string.as_support)) {
                    Row_(Icons.Rounded.Favorite, stringResource(R.string.as_donate), "dalink.to/uxazu") { open(Links.DONATE) }
                    Row_(Icons.Rounded.ContentCopy, stringResource(R.string.as_usdt), Links.USDT, mono = true) { copy(Links.USDT) }
                    Row_(Icons.Rounded.ContentCopy, stringResource(R.string.as_ton), Links.TON, mono = true) { copy(Links.TON) }
                }
            }
        }
    }

    if (langDialog) {
        AlertDialog(
            onDismissRequest = { langDialog = false },
            icon = { Icon(Icons.Rounded.Language, null) },
            title = { Text(stringResource(R.string.as_language)) },
            text = {
                LazyColumn {
                    items(AppPrefs.LANGUAGES) { (tag, name) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                langDialog = false
                                if (tag != langTag) { AppPrefs.setLanguage(ctx, tag); onRestyle() }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tag == langTag, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                            Text(name.ifEmpty { stringResource(R.string.as_lang_system) }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { langDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AboutCard() {
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("AEmulator", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.as_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.as_about), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = if (subtitle == null) 8.dp else 0.dp))
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        }
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(vertical = 8.dp)) { content() }
        }
    }
}

@Composable
private fun Row_(icon: ImageVector, title: String, sub: String, mono: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub, fontFamily = if (mono) FontFamily.Monospace else null, maxLines = 2) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
