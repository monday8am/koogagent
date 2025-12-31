package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for model selector screen
 */
data class UiState(
    val models: List<ModelInfo> = emptyList(),
    val selectedModelId: String? = null,
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
    data class SelectModel(val modelId: String) : UiAction()
    data class DownloadModel(val modelId: String) : UiAction()
    data object CancelCurrentDownload : UiAction()
    data class DeleteModel(val modelId: String) : UiAction()
}

interface ModelSelectorViewModel {
    val uiState: StateFlow<UiState>
    fun onUiAction(action: UiAction)
    fun dispose()
}

class ModelSelectorViewModelImpl(
    private val modelCatalogProvider: ModelCatalogProvider,
    private val modelDownloadManager: ModelDownloadManager,
    private val modelRepository: com.monday8am.koogagent.data.ModelRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : ModelSelectorViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalogResult = MutableStateFlow<Result<List<ModelConfiguration>>?>(null)

    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        scope.launch(ioDispatcher) {
            val models = modelCatalogProvider.fetchModels()
            if (models.isSuccess) {
                modelRepository.setModels(models.getOrNull()!!)
            }
            catalogResult.value = models
        }
        // Observe combined state
        observeCombinedState()
    }

    private fun observeCombinedState() {
        scope.launch {
            combine(
                flow = catalogResult.filterNotNull(),
                flow2 = modelDownloadManager.activeDownloads
            ) { catalogResult, activeDownloads ->
                if (catalogResult.isSuccess) {
                    val models = catalogResult.getOrThrow()
                    var currentDownload: DownloadInfo? = null
                    val queuedIds = mutableListOf<String>()

                    val modelsInfo = models.map { config ->
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
                            downloadStatus = downloadStatus
                        )
                    }

                    UiState(
                        models = modelsInfo,
                        selectedModelId = _uiState.value.selectedModelId,
                        currentDownload = currentDownload,
                        queuedDownloads = queuedIds,
                        isLoadingCatalog = false,
                        catalogError = null,
                        statusMessage = currentDownload?.let { "Downloading: ${it.modelId.take(20)}..." }
                            ?: "Select a model"
                    )
                } else {
                    val error = catalogResult.exceptionOrNull()?.message ?: "Unknown error"
                    UiState(
                        isLoadingCatalog = false,
                        catalogError = error,
                        statusMessage = "Error: $error"
                    )
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    override fun onUiAction(action: UiAction) {
        scope.launch {
            when (action) {
                is UiAction.SelectModel -> handleSelectModel(action.modelId)
                is UiAction.DownloadModel -> handleDownloadModel(action.modelId)
                is UiAction.CancelCurrentDownload -> handleCancelDownload()
                is UiAction.DeleteModel -> handleDeleteModel(action.modelId)
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun handleSelectModel(modelId: String) {
        val modelName = _uiState.value.models.find { it.config.modelId == modelId }?.config?.displayName
        _uiState.update { it.copy(selectedModelId = modelId, statusMessage = "Selected $modelName") }
    }

    private fun handleDownloadModel(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        // Just tell the manager to start the download. The combined observer will update UI.
        scope.launch(ioDispatcher) {
            modelDownloadManager.downloadModel(model.modelId, model.downloadUrl, model.bundleFilename).collect {}
        }
    }

    private fun handleCancelDownload() {
        modelDownloadManager.cancelDownload()
    }

    private suspend fun handleDeleteModel(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        if (modelDownloadManager.deleteModel(model.bundleFilename)) {
            _uiState.update {
                it.copy(
                    selectedModelId = if (it.selectedModelId == modelId) null else it.selectedModelId,
                    statusMessage = "Model deleted"
                )
            }
        }
    }
}
