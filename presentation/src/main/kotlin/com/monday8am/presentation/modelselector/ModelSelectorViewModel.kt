package com.monday8am.presentation.modelselector

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for model selector screen
 */
data class UiState(
    val models: List<ModelInfo> = emptyList(),
    val selectedModelId: String? = null,
    val currentDownload: DownloadInfo? = null,
    val queuedDownloads: List<String> = emptyList(), // modelIds waiting to download
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
    val progress: Float, // 0-100
)

sealed interface DownloadStatus {
    data object NotStarted : DownloadStatus

    data object Queued : DownloadStatus

    data class Downloading(
        val progress: Float,
    ) : DownloadStatus // 0-100

    data object Completed : DownloadStatus

    data class Failed(
        val error: String,
    ) : DownloadStatus
}

sealed class UiAction {
    data class SelectModel(
        val modelId: String,
    ) : UiAction()

    data class DownloadModel(
        val modelId: String,
    ) : UiAction()

    data object CancelCurrentDownload : UiAction()

    data class DeleteModel(
        val modelId: String,
    ) : UiAction()

    // Internal actions
    internal data object Initialize : UiAction()

    internal data class DownloadProgress(
        val modelId: String,
        val status: ModelDownloadManager.Status,
    ) : UiAction()

    internal data class ProcessNextDownload(
        val modelId: String,
    ) : UiAction()

    internal data class CatalogLoaded(
        val models: List<ModelConfiguration>,
    ) : UiAction()

    internal data class CatalogLoadFailed(
        val error: String,
    ) : UiAction()
}

internal sealed interface ActionState {
    data object Loading : ActionState

    data class Success(
        val result: Any,
    ) : ActionState

    data class Error(
        val throwable: Throwable,
    ) : ActionState
}

interface ModelSelectorViewModel {
    val uiState: StateFlow<UiState>

    fun onUiAction(uiAction: UiAction)

    fun dispose()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelSelectorViewModelImpl(
    private val modelCatalogProvider: ModelCatalogProvider,
    private val modelDownloadManager: ModelDownloadManager,
) : ModelSelectorViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Loaded models - populated after catalog is fetched
    private var loadedModels: List<ModelConfiguration> = emptyList()

    internal val userActions = MutableSharedFlow<UiAction>(replay = 0)
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            userActions
                .onStart { emit(UiAction.Initialize) }
                .flatMapConcat { action -> processAction(action) }
                .onEach { pair: Pair<UiAction, ActionState> ->
                    if (pair.first !is UiAction.ProcessNextDownload) Logger.d { "Action: $pair" }
                }
                .flowOn(Dispatchers.IO)
                .collect { result: Pair<UiAction, ActionState> ->
                    val (action, actionState) = result
                    _uiState.update { state: UiState ->
                        reduce(state, action, actionState)
                    }
                }
        }
    }

    private fun processAction(action: UiAction): Flow<Pair<UiAction, ActionState>> {
        val actionFlow: Flow<Any> =
            when (action) {
                is UiAction.Initialize -> {
                    if (loadedModels.isEmpty() && _uiState.value.catalogError == null) {
                        // Launch catalog fetch in background and return immediately
                        scope.launch {
                            val result = modelCatalogProvider.fetchModels()
                            if (result.isSuccess) {
                                val models = result.getOrThrow()
                                loadedModels = models
                                userActions.emit(UiAction.CatalogLoaded(models))
                            } else {
                                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                userActions.emit(UiAction.CatalogLoadFailed(error))
                            }
                        }
                    }
                    flowOf(Unit) // Return immediately
                }

                is UiAction.CatalogLoaded -> {
                    flow {
                        val modelsWithStatus =
                            action.models.map { config ->
                                val isDownloaded = modelDownloadManager.modelExists(config.bundleFilename)
                                ModelInfo(
                                    config = config,
                                    isDownloaded = isDownloaded,
                                    downloadStatus = if (isDownloaded) DownloadStatus.Completed else DownloadStatus.NotStarted,
                                    isGated = config.isGated,
                                )
                            }
                        emit(modelsWithStatus)
                    }
                }

                is UiAction.CatalogLoadFailed -> {
                    flowOf(action.error)
                }

                is UiAction.DownloadModel -> {
                    flowOf(action.modelId)
                }

                is UiAction.SelectModel -> {
                    flowOf(action.modelId)
                }

                is UiAction.ProcessNextDownload -> {
                    // Launch download in separate coroutine to avoid blocking action flow.
                    // The coroutine will terminate naturally when the download completes or is cancelled.
                    scope.launch {
                        val model = loadedModels.first { it.modelId == action.modelId }
                        modelDownloadManager
                            .downloadModel(model.modelId, model.downloadUrl, model.bundleFilename)
                            .collect { status ->
                                userActions.emit(UiAction.DownloadProgress(action.modelId, status))
                            }
                    }
                    flowOf(Unit) // Return immediately
                }

                is UiAction.CancelCurrentDownload -> {
                    // cancelDownload() triggers WorkManager cancellation, which causes
                    // the downloadModel() flow to emit Status.Cancelled and complete naturally
                    flow { emit(modelDownloadManager.cancelDownload()) }
                }

                is UiAction.DownloadProgress -> {
                    flowOf(action)
                }

                is UiAction.DeleteModel -> {
                    flow {
                        val model = loadedModels.first { it.modelId == action.modelId }
                        val success = modelDownloadManager.deleteModel(model.bundleFilename)
                        emit(success to action.modelId)
                    }
                }
            }

        return actionFlow
            .map<Any, ActionState> { result -> ActionState.Success(result) }
            .onStart {
                if (action is UiAction.DownloadModel || action is UiAction.ProcessNextDownload ||
                    action is UiAction.CancelCurrentDownload || action is UiAction.DeleteModel
                ) {
                    emit(ActionState.Loading)
                }
            }.catch { throwable -> emit(ActionState.Error(throwable)) }
            .map { actionState -> action to actionState }
    }

    override fun onUiAction(uiAction: UiAction) {
        scope.launch {
            userActions.emit(uiAction)
        }
    }

    override fun dispose() {
        modelDownloadManager.cancelDownload()
        scope.cancel()
    }

    internal fun reduce(state: UiState, action: UiAction, actionState: ActionState): UiState = when (actionState) {
        is ActionState.Loading -> reduceLoading(state, action)
        is ActionState.Success -> reduceSuccess(state, action, actionState)
        is ActionState.Error -> state.copy(
            statusMessage = "Error: ${actionState.throwable.message ?: "Unknown error"}"
        )
    }

    private fun reduceLoading(state: UiState, action: UiAction): UiState = when (action) {
        is UiAction.ProcessNextDownload -> state.copy(statusMessage = "Starting download...")
        is UiAction.CancelCurrentDownload -> state.copy(statusMessage = "Cancelling downloads...")
        is UiAction.DeleteModel -> state.copy(statusMessage = "Deleting model...")
        else -> state
    }

    private fun reduceSuccess(state: UiState, action: UiAction, actionState: ActionState.Success): UiState =
        when (action) {
            is UiAction.Initialize -> {
                // Catalog fetch launched in background, nothing to reduce here
                state.copy(
                    isLoadingCatalog = true,
                    statusMessage = "Loading models from Hugging Face...",
                )
            }

            is UiAction.CatalogLoaded -> {
                @Suppress("UNCHECKED_CAST")
                val models = actionState.result as List<ModelInfo>
                state.copy(
                    models = models,
                    isLoadingCatalog = false,
                    catalogError = null,
                    statusMessage = "Found ${models.count { it.isDownloaded }} of ${models.size} models downloaded",
                )
            }

            is UiAction.CatalogLoadFailed -> {
                val error = actionState.result as String
                state.copy(
                    isLoadingCatalog = false,
                    catalogError = error,
                    statusMessage = "Failed to load catalog: $error",
                )
            }

            is UiAction.DownloadModel -> {
                val modelId = actionState.result as String
                if (state.currentDownload != null) {
                    state.copy(
                        queuedDownloads = state.queuedDownloads + modelId,
                        models = state.updateModelStatus(modelId, DownloadStatus.Queued),
                        statusMessage = "Download queued",
                    )
                } else {
                    onUiAction(UiAction.ProcessNextDownload(modelId))
                    state.copy(
                        selectedModelId = modelId,
                        currentDownload = DownloadInfo(modelId = modelId, progress = 0f),
                        statusMessage = "Starting download...",
                    )
                }
            }

            is UiAction.ProcessNextDownload -> {
                // Download launched in background, nothing to reduce here
                state
            }

            is UiAction.DownloadProgress -> {
                reduceDownloadProgress(state = state, modelId = action.modelId, status = action.status)
            }

            is UiAction.SelectModel -> {
                val modelId = actionState.result as String
                val selectedModelName = state.models.find { it.config.modelId == modelId }?.config?.displayName
                state.copy(
                    selectedModelId = modelId,
                    statusMessage = "Selected $selectedModelName",
                )
            }

            is UiAction.CancelCurrentDownload -> {
                state.copy(
                    currentDownload = null,
                    queuedDownloads = emptyList(),
                    statusMessage = "Downloads cancelled",
                )
            }

            is UiAction.DeleteModel -> {
                @Suppress("UNCHECKED_CAST")
                val result = actionState.result as Pair<Boolean, String>
                val (success, modelId) = result
                if (success) {
                    val updatedModels =
                        state.models.map {
                            if (it.config.modelId == modelId) {
                                it.copy(isDownloaded = false, downloadStatus = DownloadStatus.NotStarted)
                            } else {
                                it
                            }
                        }
                    val newSelectedId = if (state.selectedModelId == modelId) null else state.selectedModelId
                    state.copy(
                        models = updatedModels,
                        selectedModelId = newSelectedId,
                        statusMessage = "Model deleted",
                    )
                } else {
                    state.copy(statusMessage = "Failed to delete model")
                }
            }
        }

    @Suppress("DefaultLocale")
    private fun reduceDownloadProgress(state: UiState, modelId: String, status: ModelDownloadManager.Status): UiState =
        when (status) {
            is ModelDownloadManager.Status.InProgress -> {
                val progress = status.progress ?: 0f
                state.copy(
                    currentDownload = DownloadInfo(modelId, progress),
                    models = state.updateModelStatus(modelId, DownloadStatus.Downloading(progress)),
                    statusMessage = "Downloading: $modelId",
                )
            }

            is ModelDownloadManager.Status.Completed -> {
                val updatedModels =
                    state.models.map {
                        if (it.config.modelId == modelId) {
                            it.copy(isDownloaded = true, downloadStatus = DownloadStatus.Completed)
                        } else {
                            it
                        }
                    }
                processNextInQueue(state.copy(models = updatedModels), "Download complete")
            }

            is ModelDownloadManager.Status.Failed -> {
                val updatedModels = state.updateModelStatus(modelId, DownloadStatus.Failed(status.message))
                processNextInQueue(state.copy(models = updatedModels), "Download failed: ${status.message}")
            }

            is ModelDownloadManager.Status.Cancelled -> {
                state.copy(
                    models =
                    state.models.map {
                        if (it.downloadStatus is DownloadStatus.Downloading || it.downloadStatus == DownloadStatus.Queued) {
                            it.copy(downloadStatus = DownloadStatus.NotStarted)
                        } else {
                            it
                        }
                    },
                    currentDownload = null,
                    queuedDownloads = emptyList(), // Clear queue on cancel
                    statusMessage = "Download cancelled",
                )
            }

            else -> {
                state
            }
        }

    private fun processNextInQueue(state: UiState, baseStatusMessage: String): UiState {
        val nextInQueue = state.queuedDownloads.firstOrNull()
        if (nextInQueue != null) {
            onUiAction(UiAction.ProcessNextDownload(nextInQueue))
        }
        return state.copy(
            currentDownload = nextInQueue?.let { DownloadInfo(it, 0f) },
            queuedDownloads = state.queuedDownloads.drop(1),
            statusMessage = if (nextInQueue != null) "$baseStatusMessage! Starting next..." else "$baseStatusMessage!",
        )
    }

    private fun UiState.updateModelStatus(modelId: String, downloadStatus: DownloadStatus): List<ModelInfo> =
        this.models.map { modelInfo ->
            if (modelInfo.config.modelId == modelId) {
                modelInfo.copy(downloadStatus = downloadStatus)
            } else {
                modelInfo
            }
        }
}
