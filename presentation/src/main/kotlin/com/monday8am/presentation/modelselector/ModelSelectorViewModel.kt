package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.ModelRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State for model selector screen
 */
data class UiState(
    val models: List<ModelInfo> = emptyList(),
    val currentDownload: DownloadInfo? = null,
    val queuedDownloads: List<String> = emptyList(),
    val statusMessage: String = "Select a model to get started",
    val isLoadingCatalog: Boolean = true,
    val catalogError: String? = null,
)

data class ModelInfo(
    val config: ModelConfiguration,
    val isDownloaded: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
    val isGated: Boolean = false,
)

data class DownloadInfo(
    val modelId: String,
    val progress: Float,
)

sealed interface DownloadStatus {
    data object NotStarted : DownloadStatus
    data object Queued : DownloadStatus
    data class Downloading(val progress: Float) : DownloadStatus
    data object Completed : DownloadStatus
    data class Failed(val error: String) : DownloadStatus
}

sealed class UiAction {
    data class DownloadModel(val modelId: String) : UiAction()
    data object CancelCurrentDownload : UiAction()
    data class DeleteModel(val modelId: String) : UiAction()
    internal data object Initialize : UiAction()
}

/**
 * Internal state for catalog loading
 */
private sealed interface CatalogState {
    data object Loading : CatalogState
    data class Success(val models: List<ModelConfiguration>, val version: Long = 0) : CatalogState
    data class Error(val message: String) : CatalogState
}

interface ModelSelectorViewModel {
    val uiState: StateFlow<UiState>
    fun onUiAction(action: UiAction)
    fun dispose()
}

class ModelSelectorViewModelImpl(
    private val modelCatalogProvider: ModelCatalogProvider,
    private val modelDownloadManager: ModelDownloadManager,
    private val modelRepository: ModelRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelSelectorViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)

    override val uiState: StateFlow<UiState> = combine(
        catalogState,
        modelDownloadManager.activeDownloads,
    ) { catalog, activeDownloads ->
        deriveUiState(catalog, activeDownloads)
    }.stateIn(scope, SharingStarted.Eagerly, UiState())

    init {
        loadCatalog()
    }

    override fun onUiAction(action: UiAction) {
        when (action) {
            is UiAction.Initialize -> loadCatalog()
            is UiAction.DownloadModel -> startDownload(action.modelId)
            is UiAction.CancelCurrentDownload -> cancelDownload()
            is UiAction.DeleteModel -> deleteModel(action.modelId)
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun loadCatalog() {
        catalogState.value = CatalogState.Loading
        scope.launch(ioDispatcher) {
            val result = modelCatalogProvider.fetchModels()
            if (result.isSuccess) {
                val models = result.getOrThrow()
                modelRepository.setModels(models)
                catalogState.value = CatalogState.Success(models)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                catalogState.value = CatalogState.Error(error)
            }
        }
    }

    private fun startDownload(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        scope.launch(ioDispatcher) {
            modelDownloadManager.downloadModel(
                model.modelId,
                model.downloadUrl,
                model.bundleFilename
            ).collect {}
        }
    }

    private fun cancelDownload() {
        modelDownloadManager.cancelDownload()
    }

    private fun deleteModel(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        scope.launch(ioDispatcher) {
            if (modelDownloadManager.deleteModel(model.bundleFilename)) {
                // Increment version to trigger UI refresh
                val current = catalogState.value
                if (current is CatalogState.Success) {
                    catalogState.value = current.copy(version = current.version + 1)
                }
            }
        }
    }

    private suspend fun deriveUiState(
        catalog: CatalogState,
        activeDownloads: Map<String, ModelDownloadManager.Status>,
    ): UiState = when (catalog) {
        is CatalogState.Loading -> UiState(
            isLoadingCatalog = true,
            statusMessage = "Loading models...",
        )

        is CatalogState.Error -> UiState(
            isLoadingCatalog = false,
            catalogError = catalog.message,
            statusMessage = "Error: ${catalog.message}",
        )

        is CatalogState.Success -> {
            var currentDownload: DownloadInfo? = null
            val queuedIds = mutableListOf<String>()

            val modelsInfo = catalog.models.map { config ->
                val status = activeDownloads[config.modelId]
                val isDownloaded = modelDownloadManager.modelExists(config.bundleFilename)

                val downloadStatus = when (status) {
                    is ModelDownloadManager.Status.InProgress -> {
                        val progress = status.progress ?: 0f
                        if (currentDownload == null) {
                            currentDownload = DownloadInfo(config.modelId, progress)
                        }
                        DownloadStatus.Downloading(progress)
                    }

                    is ModelDownloadManager.Status.Pending -> {
                        queuedIds.add(config.modelId)
                        DownloadStatus.Queued
                    }

                    is ModelDownloadManager.Status.Completed -> DownloadStatus.Completed
                    is ModelDownloadManager.Status.Failed -> DownloadStatus.Failed(status.message)
                    is ModelDownloadManager.Status.Cancelled -> DownloadStatus.NotStarted
                    null -> if (isDownloaded) DownloadStatus.Completed else DownloadStatus.NotStarted
                }

                ModelInfo(
                    config = config,
                    isDownloaded = isDownloaded || status is ModelDownloadManager.Status.Completed,
                    downloadStatus = downloadStatus,
                    isGated = config.isGated
                )
            }

            UiState(
                models = modelsInfo,
                currentDownload = currentDownload,
                queuedDownloads = queuedIds,
                isLoadingCatalog = false,
                catalogError = null,
                statusMessage = currentDownload?.let { "Downloading: ${it.modelId.take(20)}..." }
                    ?: "Select a model",
            )
        }
    }
}
