package com.kesham.pocketbrain

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Per-row state in the Model Manager. */
data class ModelRowState(
    val entry: CatalogEntry,
    val isDownloaded: Boolean,
    val isPushed: Boolean,
    val progress: Float? = null,
    val isVerifying: Boolean = false,
    val error: String? = null,
) {
    val isBusy: Boolean get() = progress != null || isVerifying
}

class ModelManagerViewModel(private val appContext: Context) : ViewModel() {

    private val _rows = MutableStateFlow(emptyList<ModelRowState>())
    val rows: StateFlow<List<ModelRowState>> = _rows.asStateFlow()

    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _freeBytes = MutableStateFlow(0L)
    val freeBytes: StateFlow<Long> = _freeBytes.asStateFlow()

    private val jobs = mutableMapOf<Model, Job>()

    init {
        refresh()
    }

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
    }

    private fun refresh() {
        _freeBytes.value = ModelStorage.freeBytes(appContext)
        _rows.value = ModelCatalog.entries.map { entry ->
            val existing = _rows.value.firstOrNull { it.entry.model == entry.model }
            ModelRowState(
                entry = entry,
                isDownloaded = ModelStorage.downloadedFile(appContext, entry.model).exists(),
                isPushed = ModelStorage.isPushed(appContext, entry.model),
                progress = existing?.progress,
                isVerifying = existing?.isVerifying ?: false,
                error = existing?.error,
            )
        }
    }

    private fun update(model: Model, transform: (ModelRowState) -> ModelRowState) {
        _rows.value = _rows.value.map { if (it.entry.model == model) transform(it) else it }
    }

    fun download(entry: CatalogEntry) {
        if (jobs[entry.model]?.isActive == true) return

        if (!ModelDownloader.hasNetwork(appContext)) {
            update(entry.model) { it.copy(error = "No network connection") }
            return
        }
        if (_wifiOnly.value && !ModelDownloader.isUnmetered(appContext)) {
            update(entry.model) {
                it.copy(error = "Wi-Fi only is on and you are on a metered network")
            }
            return
        }
        val shortfall = ModelDownloader.shortfallBytes(appContext, entry)
        if (shortfall > 0) {
            update(entry.model) {
                it.copy(error = "Needs ${formatBytes(shortfall)} more free storage")
            }
            return
        }

        update(entry.model) { it.copy(error = null, progress = 0f) }
        jobs[entry.model] = viewModelScope.launch {
            val result = ModelDownloader.download(appContext, entry) { stage ->
                when (stage) {
                    is DownloadStage.Downloading -> update(entry.model) {
                        it.copy(
                            progress = stage.bytesDone.toFloat() / stage.totalBytes.toFloat(),
                            isVerifying = false,
                        )
                    }

                    DownloadStage.Verifying -> update(entry.model) {
                        it.copy(progress = 1f, isVerifying = true)
                    }
                }
            }
            update(entry.model) {
                it.copy(
                    progress = null,
                    isVerifying = false,
                    error = (result as? DownloadResult.Failed)?.message,
                )
            }
            refresh()
        }
    }

    fun cancel(model: Model) {
        jobs[model]?.cancel()
        jobs.remove(model)
        update(model) { it.copy(progress = null, isVerifying = false) }
        refresh()
    }

    fun delete(model: Model) {
        ModelStorage.deleteDownload(appContext, model)
        refresh()
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ModelManagerViewModel(context) as T
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1e6)
    else -> "%.0f KB".format(bytes / 1e3)
}
