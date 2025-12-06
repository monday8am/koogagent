package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.presentation.notifications.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
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
)

/**
 * Per-model information including download state
 */
data class ModelInfo(
    val config: ModelConfiguration,
    val isDownloaded: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
)

/**
 * Information about the currently downloading model
 */
data class DownloadInfo(
    val modelId: String,
    val progress: Float, // 0-100
)

/**
 * Download status for each model
 */
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

/**
 * User actions and internal events
 */
sealed class UiAction {
    data class SelectModel(
        val modelId: String,
    ) : UiAction()

    data class DownloadModel(
        val modelId: String,
    ) : UiAction()

    data object CancelCurrentDownload : UiAction()

    // Internal actions
    internal data object Initialize : UiAction()

    internal data class DownloadProgress(
        val modelId: String,
        val status: ModelDownloadManager.Status,
    ) : UiAction()

    internal data class ProcessNextDownload(
        val modelId: String,
    ) : UiAction()
}

/**
 * Action processing results
 */
internal sealed interface ActionState {
    data object Loading : ActionState

    data class Success(
        val result: Any,
    ) : ActionState

    data class Error(
        val throwable: Throwable,
    ) : ActionState
}

/**
 * Internal sealed class for queue actions
 */
private sealed interface QueueAction {
    data class Added(
        val modelId: String,
    ) : QueueAction
}

/**
 * Internal sealed class for download start
 */
private data class StartDownload(
    val modelId: String,
)

/**
 * Platform-agnostic ViewModel interface
 */
interface ModelSelectorViewModel {
    val uiState: Flow<UiState>

    fun onUiAction(uiAction: UiAction)

    fun dispose()
}

/**
 * Implementation of ModelSelectorViewModel using MVI pattern
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelSelectorViewModelImpl(
    private val availableModels: List<ModelConfiguration>,
    private val modelDownloadManager: ModelDownloadManager,
) : ModelSelectorViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal val userActions = MutableSharedFlow<UiAction>(replay = 0)

    override val uiState: Flow<UiState> =
        userActions
            .onStart { emit(UiAction.Initialize) }
            .flatMapConcat { action -> processAction(action) }
            .flowOn(Dispatchers.IO)
            .scan(UiState()) { state, (action, actionState) ->
                reduce(state, action, actionState)
            }.distinctUntilChanged()

    private fun processAction(action: UiAction): Flow<Pair<UiAction, ActionState>> {
        val actionFlow: Flow<Any> =
            when (action) {
                is UiAction.Initialize -> {
                    flow {
                        val modelsWithStatus =
                            availableModels.map { config ->
                                val isDownloaded = modelDownloadManager.modelExists(config.bundleFilename)
                                ModelInfo(
                                    config = config,
                                    isDownloaded = isDownloaded,
                                    downloadStatus = if (isDownloaded) DownloadStatus.Completed else DownloadStatus.NotStarted,
                                )
                            }
                        emit(modelsWithStatus)
                    }
                }

                is UiAction.DownloadModel -> {
                    flowOf(action.modelId)
                }

                is UiAction.SelectModel -> {
                    flowOf(action.modelId)
                }

                is UiAction.ProcessNextDownload -> {
                    val model = availableModels.first { it.modelId == action.modelId }
                    modelDownloadManager
                        .downloadModel(model.modelId, model.downloadUrl, model.bundleFilename)
                        .map { status -> UiAction.DownloadProgress(action.modelId, status) }
                }

                is UiAction.CancelCurrentDownload -> {
                    flow { emit(modelDownloadManager.cancelDownload()) }
                }

                is UiAction.DownloadProgress -> {
                    flowOf(action)
                }
            }

        return actionFlow
            .map<Any, ActionState> { result -> ActionState.Success(result) }
            .onStart {
                if (action is UiAction.DownloadModel || action is UiAction.ProcessNextDownload ||
                    action is UiAction.CancelCurrentDownload
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

    internal fun reduce(
        state: UiState,
        action: UiAction,
        actionState: ActionState,
    ): UiState =
        when (actionState) {
            is ActionState.Loading -> reduceLoading(state, action)
            is ActionState.Success -> reduceSuccess(state, action, actionState)
            is ActionState.Error -> state.copy(statusMessage = "Error: ${actionState.throwable.message ?: "Unknown error"}")
        }

    private fun reduceLoading(
        state: UiState,
        action: UiAction,
    ): UiState =
        when (action) {
            is UiAction.ProcessNextDownload -> state.copy(statusMessage = "Starting download...")
            is UiAction.CancelCurrentDownload -> state.copy(statusMessage = "Cancelling downloads...")
            else -> state
        }

    private fun reduceSuccess(
        state: UiState,
        action: UiAction,
        actionState: ActionState.Success,
    ): UiState =
        when (action) {
            is UiAction.Initialize -> {
                @Suppress("UNCHECKED_CAST")
                val models = actionState.result as List<ModelInfo>
                state.copy(
                    models = models,
                    statusMessage = "Found ${models.count { it.isDownloaded }} of ${models.size} models downloaded",
                )
            }

            is UiAction.DownloadModel -> {
                val modelId = actionState.result as String
                if (state.currentDownload != null) {
                    // Queue download
                    state.copy(
                        queuedDownloads = state.queuedDownloads + modelId,
                        models = state.updateModelStatus(modelId, DownloadStatus.Queued),
                        statusMessage = "Download queued",
                    )
                } else {
                    // Start download immediately
                    onUiAction(UiAction.ProcessNextDownload(modelId))
                    state.copy(
                        currentDownload = DownloadInfo(modelId = modelId, progress = 0f),
                        statusMessage = "Starting download...",
                    )
                }
            }

            is UiAction.DownloadProgress -> {
                reduceDownloadProgress(state, action)
            }

            is UiAction.SelectModel -> {
                val modelId = actionState.result as String
                val selectedModelName = availableModels.find { it.modelId == modelId }?.displayName
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

            else -> {
                state
            }
        }

    private fun reduceDownloadProgress(
        state: UiState,
        action: UiAction.DownloadProgress,
    ): UiState =
        when (val status = action.status) {
            is ModelDownloadManager.Status.InProgress -> {
                val progress = status.progress ?: 0f
                state.copy(
                    currentDownload = DownloadInfo(action.modelId, progress),
                    models = state.updateModelStatus(action.modelId, DownloadStatus.Downloading(progress)),
                    statusMessage = "Downloading ${progress.toInt()}%",
                )
            }

            is ModelDownloadManager.Status.Completed -> {
                val updatedModels =
                    state.models.map {
                        if (it.config.modelId == action.modelId) {
                            it.copy(isDownloaded = true, downloadStatus = DownloadStatus.Completed)
                        } else {
                            it
                        }
                    }
                processNextInQueue(state.copy(models = updatedModels), "Download complete")
            }

            is ModelDownloadManager.Status.Failed -> {
                val updatedModels = state.updateModelStatus(action.modelId, DownloadStatus.Failed(status.message))
                processNextInQueue(state.copy(models = updatedModels), "Download failed: ${status.message}")
            }

            is ModelDownloadManager.Status.Cancelled -> {
                state.copy(
                    models = state.updateModelStatus(action.modelId, DownloadStatus.NotStarted),
                    currentDownload = null,
                    queuedDownloads = emptyList(), // Clear queue on cancel
                    statusMessage = "Download cancelled",
                )
            }

            else -> {
                state
            }
        }

    private fun processNextInQueue(
        state: UiState,
        baseStatusMessage: String,
    ): UiState {
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

    private fun UiState.updateModelStatus(
        modelId: String,
        downloadStatus: DownloadStatus,
    ): List<ModelInfo> =
        this.models.map { modelInfo ->
            if (modelInfo.config.modelId == modelId) {
                modelInfo.copy(downloadStatus = downloadStatus)
            } else {
                modelInfo
            }
        }
}
