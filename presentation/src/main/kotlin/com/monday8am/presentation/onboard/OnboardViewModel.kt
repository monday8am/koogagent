package com.monday8am.presentation.onboard

import com.monday8am.koogagent.data.auth.AuthRepository
import com.monday8am.koogagent.data.model.ModelConfiguration
import com.monday8am.presentation.modelselector.ModelDownloadManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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

/** UI State for onboard screen with simplified two-model download */
data class UiState(
    val models: ImmutableList<ModelInfo> = persistentListOf(),
    val isLoadingCatalog: Boolean = false,
    val isLoggedIn: Boolean = false,
    val statusMessage: String = "Sign in to download models",
)

data class ModelInfo(
    val config: ModelConfiguration,
    val isDownloaded: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
)

sealed interface DownloadStatus {
    data object NotStarted : DownloadStatus

    data class Downloading(val progress: Float) : DownloadStatus

    data object Completed : DownloadStatus

    data class Failed(val error: String) : DownloadStatus
}

sealed class UiAction {
    data class DownloadModel(val modelId: String) : UiAction()

    data object CancelCurrentDownload : UiAction()

    data class SubmitToken(val token: String) : UiAction()

    data object StartOAuth : UiAction()
}

interface OnboardViewModel {
    val uiState: StateFlow<UiState>

    fun onUiAction(action: UiAction)

    fun dispose()
}

class OnboardViewModelImpl(
    private val modelDownloadManager: ModelDownloadManager,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OnboardViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        // TODO: Replace with actual URLs once provided by user
        private val COPILOT_MODELS =
            listOf(
                ModelConfiguration(
                    displayName = "User HF Model",
                    modelFamily = "gemma",
                    parameterCount = 1.0f,
                    quantization = "int8",
                    contextLength = 4096,
                    downloadUrl =
                        "https://huggingface.co/USER/MODEL/resolve/main/model.bin", // Placeholder
                    bundleFilename = "user-hf-model.bin",
                    fileSizeBytes = 1_000_000_000L, // 1GB placeholder
                    isGated = true,
                ),
                ModelConfiguration(
                    displayName = "Gemma 3 1B",
                    modelFamily = "gemma",
                    parameterCount = 1.0f,
                    quantization = "int8",
                    contextLength = 4096,
                    downloadUrl =
                        "https://huggingface.co/google/gemma-3-1b/resolve/main/model.bin", // Placeholder
                    bundleFilename = "gemma3-1b.bin",
                    fileSizeBytes = 1_000_000_000L, // 1GB placeholder
                    isGated = true,
                ),
            )
    }

    override val uiState: StateFlow<UiState> =
        combine(modelDownloadManager.modelsStatus, authRepository.authToken) {
                modelsStatus,
                authToken ->
                deriveUiState(modelsStatus = modelsStatus, isLoggedIn = authToken != null)
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, UiState())

    override fun onUiAction(action: UiAction) {
        when (action) {
            is UiAction.DownloadModel -> startDownload(action.modelId)
            is UiAction.CancelCurrentDownload -> cancelDownload()
            is UiAction.SubmitToken -> submitToken(action.token)
            is UiAction.StartOAuth -> {
                /* OAuth flow handled by UI layer */
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun startDownload(modelId: String) {
        val model = COPILOT_MODELS.find { it.modelId == modelId } ?: return
        scope.launch(ioDispatcher) {
            modelDownloadManager.downloadModel(
                model.modelId,
                model.downloadUrl,
                model.bundleFilename,
            )
        }
    }

    private fun cancelDownload() {
        modelDownloadManager.cancelDownload()
    }

    private fun submitToken(token: String) {
        scope.launch { authRepository.saveToken(token) }
    }

    private fun deriveUiState(
        modelsStatus: Map<String, ModelDownloadManager.Status>,
        isLoggedIn: Boolean,
    ): UiState {
        val modelsInfo =
            COPILOT_MODELS.map { config ->
                    val status =
                        modelsStatus[config.bundleFilename]
                            ?: ModelDownloadManager.Status.NotStarted
                    val (downloadStatus, isDownloaded) = mapDownloadStatus(status)

                    ModelInfo(
                        config = config,
                        isDownloaded = isDownloaded,
                        downloadStatus = downloadStatus,
                    )
                }
                .toImmutableList()

        val anyDownloading = modelsInfo.any { it.downloadStatus is DownloadStatus.Downloading }
        val allDownloaded = modelsInfo.all { it.isDownloaded }

        val statusMessage =
            when {
                !isLoggedIn -> "Sign in with HuggingFace to download models"
                anyDownloading -> "Downloading models..."
                allDownloaded -> "All models ready"
                else -> "Ready to download"
            }

        return UiState(
            models = modelsInfo,
            isLoadingCatalog = false,
            isLoggedIn = isLoggedIn,
            statusMessage = statusMessage,
        )
    }

    private fun mapDownloadStatus(
        status: ModelDownloadManager.Status
    ): Pair<DownloadStatus, Boolean> {
        val downloadStatus =
            when (status) {
                is ModelDownloadManager.Status.InProgress -> {
                    val progress = status.progress ?: 0f
                    DownloadStatus.Downloading(progress)
                }
                is ModelDownloadManager.Status.Completed -> DownloadStatus.Completed
                is ModelDownloadManager.Status.Failed -> DownloadStatus.Failed(status.message)
                is ModelDownloadManager.Status.Cancelled,
                ModelDownloadManager.Status.NotStarted,
                is ModelDownloadManager.Status.Pending -> DownloadStatus.NotStarted
            }
        val isDownloaded = status is ModelDownloadManager.Status.Completed
        return Pair(downloadStatus, isDownloaded)
    }
}
