package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    internal data object Initialize : UiAction()
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
    private val mutex = Mutex()

    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var activeDownloadJob: Job? = null

    init {
        scope.launch {
            handleInitialize()
        }
    }

    override fun onUiAction(action: UiAction) {
        scope.launch {
            when (action) {
                is UiAction.Initialize -> handleInitialize()
                is UiAction.SelectModel -> handleSelectModel(action.modelId)
                is UiAction.DownloadModel -> handleDownloadModel(action.modelId)
                is UiAction.CancelCurrentDownload -> handleCancelDownload()
                is UiAction.DeleteModel -> handleDeleteModel(action.modelId)
            }
        }
    }

    override fun dispose() {
        activeDownloadJob?.cancel()
        modelDownloadManager.cancelDownload()
        scope.cancel()
    }

    private suspend fun handleInitialize() {
        if (modelRepository.getAllModels().isNotEmpty() || _uiState.value.catalogError != null) {
            return
        }

        updateState { it.copy(isLoadingCatalog = true, statusMessage = "Loading models from Hugging Face...") }

        val result = modelCatalogProvider.fetchModels()
        if (result.isSuccess) {
            val models = result.getOrThrow()
            modelRepository.setModels(models)
            handleCatalogLoaded(models)
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            updateState {
                it.copy(
                    isLoadingCatalog = false,
                    catalogError = error,
                    statusMessage = "Failed to load catalog: $error"
                )
            }
        }
    }

    private suspend fun handleCatalogLoaded(models: List<ModelConfiguration>) {
        val activeDownloads = modelDownloadManager.activeDownloads.take(1).let { flow ->
            var result: Map<String, ModelDownloadManager.Status> = emptyMap()
            flow.collect { result = it }
            result
        }

        val modelsWithStatus = models.map { config ->
            val isDownloaded = modelDownloadManager.modelExists(config.bundleFilename)
            val activeStatus = activeDownloads[config.modelId]

            // If there's an active background download, re-attach to it
            if (activeStatus != null && !isDownloaded) {
                reattachToDownload(config)
            }

            ModelInfo(
                config = config,
                isDownloaded = isDownloaded,
                downloadStatus = when {
                    isDownloaded -> DownloadStatus.Completed
                    activeStatus is ModelDownloadManager.Status.InProgress ->
                        DownloadStatus.Downloading(activeStatus.progress ?: 0f)

                    activeStatus is ModelDownloadManager.Status.Pending ->
                        DownloadStatus.Queued

                    else -> DownloadStatus.NotStarted
                },
                isGated = config.isGated,
            )
        }

        updateState {
            it.copy(
                models = modelsWithStatus,
                isLoadingCatalog = false,
                catalogError = null,
                statusMessage = "Found ${modelsWithStatus.count { m -> m.isDownloaded }} of ${modelsWithStatus.size} models downloaded"
            )
        }
    }

    private fun reattachToDownload(config: ModelConfiguration) {
        scope.launch {
            modelDownloadManager.downloadModel(config.modelId, config.downloadUrl, config.bundleFilename)
                .collect { status ->
                    handleDownloadStatus(config.modelId, status)
                }
        }
    }

    private suspend fun handleSelectModel(modelId: String) {
        val modelName = _uiState.value.models.find { it.config.modelId == modelId }?.config?.displayName
        updateState {
            it.copy(
                selectedModelId = modelId,
                statusMessage = "Selected $modelName"
            )
        }
    }

    private suspend fun handleDownloadModel(modelId: String) {
        mutex.withLock {
            val state = _uiState.value
            if (state.currentDownload != null) {
                // Already downloading something, queue this one
                _uiState.update {
                    it.copy(
                        queuedDownloads = it.queuedDownloads + modelId,
                        models = it.updateModelStatus(modelId, DownloadStatus.Queued),
                        statusMessage = "Download queued"
                    )
                }
            } else {
                // Start downloading immediately
                startDownload(modelId)
            }
        }
    }

    private fun startDownload(modelId: String) {
        val model = modelRepository.findById(modelId)
            ?: _uiState.value.models.first { it.config.modelId == modelId }.config

        _uiState.update {
            it.copy(
                selectedModelId = modelId,
                currentDownload = DownloadInfo(modelId = modelId, progress = 0f),
                models = it.updateModelStatus(modelId, DownloadStatus.Downloading(0f)),
                queuedDownloads = it.queuedDownloads.filter { id -> id != modelId },
                statusMessage = "Starting download..."
            )
        }

        activeDownloadJob = scope.launch(ioDispatcher) {
            modelDownloadManager.downloadModel(model.modelId, model.downloadUrl, model.bundleFilename)
                .collect { status ->
                    handleDownloadStatus(modelId, status)
                }
        }
    }

    private suspend fun handleDownloadStatus(modelId: String, status: ModelDownloadManager.Status) {
        when (status) {
            is ModelDownloadManager.Status.Pending -> {
                // Initial state, do nothing
            }

            is ModelDownloadManager.Status.InProgress -> {
                val progress = status.progress ?: 0f
                updateState {
                    it.copy(
                        currentDownload = DownloadInfo(modelId, progress),
                        models = it.updateModelStatus(modelId, DownloadStatus.Downloading(progress)),
                        statusMessage = "Downloading: ${modelId.take(20)}..."
                    )
                }
            }

            is ModelDownloadManager.Status.Completed -> {
                updateState {
                    it.copy(
                        models = it.models.map { m ->
                            if (m.config.modelId == modelId) {
                                m.copy(isDownloaded = true, downloadStatus = DownloadStatus.Completed)
                            } else {
                                m
                            }
                        },
                        currentDownload = null,
                        statusMessage = "Download complete!"
                    )
                }
                processNextInQueue()
            }

            is ModelDownloadManager.Status.Failed -> {
                updateState {
                    it.copy(
                        models = it.updateModelStatus(modelId, DownloadStatus.Failed(status.message)),
                        currentDownload = null,
                        statusMessage = "Download failed: ${status.message}"
                    )
                }
                processNextInQueue()
            }

            is ModelDownloadManager.Status.Cancelled -> {
                updateState {
                    it.copy(
                        models = it.models.map { m ->
                            if (m.downloadStatus is DownloadStatus.Downloading || m.downloadStatus == DownloadStatus.Queued) {
                                m.copy(downloadStatus = DownloadStatus.NotStarted)
                            } else {
                                m
                            }
                        },
                        currentDownload = null,
                        queuedDownloads = emptyList(),
                        statusMessage = "Download cancelled"
                    )
                }
            }
        }
    }

    private suspend fun processNextInQueue() {
        mutex.withLock {
            val next = _uiState.value.queuedDownloads.firstOrNull()
            if (next != null) {
                startDownload(next)
            }
        }
    }

    private suspend fun handleCancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        modelDownloadManager.cancelDownload()

        updateState {
            it.copy(
                models = it.models.map { m ->
                    if (m.downloadStatus is DownloadStatus.Downloading || m.downloadStatus == DownloadStatus.Queued) {
                        m.copy(downloadStatus = DownloadStatus.NotStarted)
                    } else {
                        m
                    }
                },
                currentDownload = null,
                queuedDownloads = emptyList(),
                statusMessage = "Download cancelled"
            )
        }
    }

    private suspend fun handleDeleteModel(modelId: String) {
        updateState { it.copy(statusMessage = "Deleting model...") }

        val model = modelRepository.findById(modelId)
            ?: _uiState.value.models.first { it.config.modelId == modelId }.config

        val success = modelDownloadManager.deleteModel(model.bundleFilename)

        if (success) {
            updateState {
                val newSelectedId = if (it.selectedModelId == modelId) null else it.selectedModelId
                it.copy(
                    models = it.models.map { m ->
                        if (m.config.modelId == modelId) {
                            m.copy(isDownloaded = false, downloadStatus = DownloadStatus.NotStarted)
                        } else {
                            m
                        }
                    },
                    selectedModelId = newSelectedId,
                    statusMessage = "Model deleted"
                )
            }
        } else {
            updateState { it.copy(statusMessage = "Failed to delete model") }
        }
    }

    private suspend fun updateState(block: (UiState) -> UiState) {
        mutex.withLock {
            _uiState.update(block)
        }
    }

    private fun UiState.updateModelStatus(modelId: String, status: DownloadStatus): List<ModelInfo> =
        this.models.map { m ->
            if (m.config.modelId == modelId) m.copy(downloadStatus = status) else m
        }
}
