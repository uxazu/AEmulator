package app.aemu.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.aemu.core.GuestImage
import app.aemu.core.ImageStore
import app.aemu.core.VmSettings
import app.aemu.importer.Importer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportState(
    val active: Boolean = false,
    val file: String = "",
    val step: String = "",
    val progress: Float = -1f,
    val log: List<String> = emptyList(),
    val error: String? = null,
    val done: GuestImage? = null,
)

class LibraryModel(app: Application) : AndroidViewModel(app) {
    private val _images = MutableStateFlow<List<GuestImage>>(emptyList())
    val images: StateFlow<List<GuestImage>> = _images
    private val _import = MutableStateFlow(ImportState())
    val import: StateFlow<ImportState> = _import
    private var importer: Importer? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _images.value = withContext(Dispatchers.IO) { ImageStore.list(getApplication()) }
        }
    }

    fun import(uri: Uri) {
        if (_import.value.active) return
        val ctx = getApplication<Application>()
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "firmware"
        _import.value = ImportState(active = true, file = name, step = "Начинаю")
        viewModelScope.launch(Dispatchers.IO) {
            val log = ArrayList<String>()
            val imp = Importer(ctx,
                onProgress = { step, p -> _import.value = _import.value.copy(step = step, progress = if (p >= 0) p else _import.value.progress.takeIf { it >= 0 } ?: -1f) },
                log = { s -> log.add(s); _import.value = _import.value.copy(log = log.takeLast(50)) })
            importer = imp
            try {
                // умолчания из настроек приложения поверх того, что подобрал анализатор (экран, частоты)
                val raw = imp.import(uri, name)
                val d = app.aemu.AppPrefs.defaults(ctx)
                val img = raw.copy(settings = raw.settings.copy(gpu = d.gpu, jit = d.jit, netProxy = d.netProxy,
                    showNavBar = d.showNavBar, keepScreenOn = d.keepScreenOn))
                if (img != raw) ImageStore.save(ctx, img)
                _import.value = _import.value.copy(active = false, done = img, step = "Готово", progress = 1f)
            } catch (t: Throwable) {
                _import.value = _import.value.copy(active = false, error = t.message ?: t.toString())
            } finally {
                importer = null
                refresh()
            }
        }
    }

    fun cancelImport() { importer?.cancelled = true }
    fun dismissImport() { _import.value = ImportState() }

    fun delete(img: GuestImage) {
        viewModelScope.launch(Dispatchers.IO) {
            ImageStore.delete(getApplication(), img.id)
            refresh()
        }
    }

    fun rename(img: GuestImage, name: String) = save(img.copy(name = name))

    fun updateSettings(img: GuestImage, s: VmSettings) = save(img.copy(settings = s))

    private fun save(img: GuestImage) {
        viewModelScope.launch(Dispatchers.IO) {
            ImageStore.save(getApplication(), img)
            refresh()
        }
    }
}
