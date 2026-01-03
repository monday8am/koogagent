package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.AuthRepository
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.ModelRepository
import com.monday8am.koogagent.data.RepositoryState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    val isLoggedIn: Boolean = false,
)

data class ModelInfo(
    val config: ModelConfiguration,
    val isDownloaded: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
    val isGated: Boolean = false,
)

data class DownloadInfo(val modelId: String, val progress: Float)

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
    data class SubmitToken(val token: String) : UiAction()
    data object Logout : UiAction()
    internal data object Initialize : UiAction()
}

interface ModelSelectorViewModel {
    val uiState: StateFlow<UiState>
    fun onUiAction(action: UiAction)
    fun dispose()
}

class ModelSelectorViewModelImpl(
    private val modelDownloadManager: ModelDownloadManager,
    private val modelRepository: ModelRepository,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelSelectorViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val uiState: StateFlow<UiState> = combine(
        modelRepository.loadingState,
        modelDownloadManager.modelsStatus,
        authRepository.authToken,
    ) { loadingState: RepositoryState, modelsStatus: Map<String, ModelDownloadManager.Status>, authToken: String? ->
        deriveUiState(loadingState, modelsStatus, authToken != null)
    }
        .flowOn(ioDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, UiState())

    init {
        loadCatalog()
    }

    override fun onUiAction(action: UiAction) {
        when (action) {
            is UiAction.Initialize -> loadCatalog()
            is UiAction.DownloadModel -> startDownload(action.modelId)
            is UiAction.CancelCurrentDownload -> cancelDownload()
            is UiAction.DeleteModel -> deleteModel(action.modelId)
            is UiAction.SubmitToken -> submitToken(action.token)
            is UiAction.Logout -> logout()
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun loadCatalog() {
        scope.launch {
            modelRepository.refreshModels()
        }
    }

    private fun startDownload(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        scope.launch(ioDispatcher) {
            modelDownloadManager.downloadModel(
                model.modelId,
                model.downloadUrl,
                model.bundleFilename
            )
        }
    }

    private fun cancelDownload() {
        modelDownloadManager.cancelDownload()
    }

    private fun deleteModel(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        scope.launch(ioDispatcher) {
            modelDownloadManager.deleteModel(model.bundleFilename)
        }
    }

    private fun submitToken(token: String) {
        scope.launch {
            authRepository.saveToken(token)
            modelRepository.refreshModels()
        }
    }

    private fun logout() {
        scope.launch {
            authRepository.clearToken()
            modelRepository.refreshModels()
        }
    }

    private fun deriveUiState(
        loadingState: RepositoryState,
        modelsStatus: Map<String, ModelDownloadManager.Status>,
        isLoggedIn: Boolean,
    ): UiState = when (loadingState) {
        is RepositoryState.Idle,
        is RepositoryState.Loading -> UiState(
            isLoadingCatalog = true,
            statusMessage = "Loading models...",
            isLoggedIn = isLoggedIn,
        )

        is RepositoryState.Error -> UiState(
            isLoadingCatalog = false,
            catalogError = loadingState.message,
            statusMessage = "Error: ${loadingState.message}",
            isLoggedIn = isLoggedIn,
        )

        is RepositoryState.Success -> {
            var currentDownload: DownloadInfo? = null
            val queuedIds = mutableListOf<String>()

            val modelsInfo = loadingState.models.map { config ->
                val status = modelsStatus[config.bundleFilename] ?: ModelDownloadManager.Status.NotStarted

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
                    ModelDownloadManager.Status.NotStarted -> DownloadStatus.NotStarted
                }

                ModelInfo(
                    config = config,
                    isDownloaded = status is ModelDownloadManager.Status.Completed,
                    downloadStatus = downloadStatus,
                    isGated = config.isGated
                )
            }.sortedBy {
                if (it.downloadStatus is DownloadStatus.Downloading ||
                    it.downloadStatus is DownloadStatus.Queued ||
                    it.downloadStatus is DownloadStatus.Completed
                ) {
                    0
                } else {
                    1
                }
            }

            UiState(
                models = modelsInfo,
                currentDownload = currentDownload,
                queuedDownloads = queuedIds,
                isLoadingCatalog = false,
                catalogError = null,
                isLoggedIn = isLoggedIn,
                statusMessage = currentDownload?.let { "Downloading: ${it.modelId.take(20)}..." }
                    ?: "Select a model",
            )
        }
    }
}
