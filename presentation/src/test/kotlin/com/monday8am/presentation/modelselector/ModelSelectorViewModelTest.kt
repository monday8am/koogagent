package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ModelSelectorViewModelImpl, focusing on the reduce function.
 * These tests verify that state transitions are correct for all actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelSelectorViewModelTest {
    private val testModels = listOf(ModelCatalog.QWEN3_0_6B, ModelCatalog.GEMMA3_1B)
    private val initialState = UiState()
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Helper to create a ViewModel instance for testing
    private fun createViewModel(
        modelDownloadManager: ModelDownloadManager = FakeModelDownloadManager(),
    ): ModelSelectorViewModelImpl =
        ModelSelectorViewModelImpl(
            availableModels = testModels,
            modelDownloadManager = modelDownloadManager,
        )

    // === Tests for Initialize Success ===

    @Test
    fun `reduce with Initialize Success should populate models list`() {
        val viewModel = createViewModel()
        val modelInfoList = testModels.map { config ->
            ModelInfo(
                config = config,
                isDownloaded = false,
                downloadStatus = DownloadStatus.NotStarted,
            )
        }

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.Initialize,
            actionState = ActionState.Success(modelInfoList),
        )

        assertEquals(2, newState.models.size)
        assertEquals(testModels[0].modelId, newState.models[0].config.modelId)
        assertEquals(testModels[1].modelId, newState.models[1].config.modelId)
        assertTrue(newState.statusMessage.contains("0 of 2"))
    }

    @Test
    fun `reduce with Initialize Success should show downloaded count`() {
        val viewModel = createViewModel()
        val modelInfoList = listOf(
            ModelInfo(config = testModels[0], isDownloaded = true, downloadStatus = DownloadStatus.Completed),
            ModelInfo(config = testModels[1], isDownloaded = false, downloadStatus = DownloadStatus.NotStarted),
        )

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.Initialize,
            actionState = ActionState.Success(modelInfoList),
        )

        assertTrue(newState.statusMessage.contains("1 of 2"))
    }

    // === Tests for SelectModel Success ===

    @Test
    fun `reduce with SelectModel Success should update selectedModelId`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.SelectModel(modelId),
            actionState = ActionState.Success(modelId),
        )

        assertEquals(modelId, newState.selectedModelId)
        assertTrue(newState.statusMessage.contains("Selected"))
        assertTrue(newState.statusMessage.contains(testModels[0].displayName))
    }

    // === Tests for DownloadModel Success ===

    @Test
    fun `reduce with DownloadModel Success when no current download should start download`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel(modelId),
            actionState = ActionState.Success(modelId),
        )

        assertEquals(modelId, newState.currentDownload?.modelId)
        assertEquals(0f, newState.currentDownload?.progress)
        assertTrue(newState.statusMessage.contains("Starting download"))
    }

    @Test
    fun `reduce with DownloadModel Success when download in progress should queue download`() {
        val viewModel = createViewModel()
        val currentModelId = testModels[0].modelId
        val queuedModelId = testModels[1].modelId
        val stateWithDownload = initialState.copy(
            currentDownload = DownloadInfo(modelId = currentModelId, progress = 50f),
            models = testModels.map { ModelInfo(config = it) },
        )

        val newState = viewModel.reduce(
            state = stateWithDownload,
            action = UiAction.DownloadModel(queuedModelId),
            actionState = ActionState.Success(queuedModelId),
        )

        assertTrue(newState.queuedDownloads.contains(queuedModelId))
        assertEquals(currentModelId, newState.currentDownload?.modelId) // Current download unchanged
        assertEquals(DownloadStatus.Queued, newState.models.find { it.config.modelId == queuedModelId }?.downloadStatus)
        assertTrue(newState.statusMessage.contains("queued"))
    }

    // === Tests for CancelCurrentDownload ===

    @Test
    fun `reduce with CancelCurrentDownload Loading should show cancelling message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.CancelCurrentDownload,
            actionState = ActionState.Loading,
        )

        assertTrue(newState.statusMessage.contains("Cancelling"))
    }

    @Test
    fun `reduce with CancelCurrentDownload Success should clear downloads and queue`() {
        val viewModel = createViewModel()
        val stateWithDownloads = initialState.copy(
            currentDownload = DownloadInfo(modelId = testModels[0].modelId, progress = 50f),
            queuedDownloads = listOf(testModels[1].modelId),
        )

        val newState = viewModel.reduce(
            state = stateWithDownloads,
            action = UiAction.CancelCurrentDownload,
            actionState = ActionState.Success(Unit),
        )

        assertNull(newState.currentDownload)
        assertTrue(newState.queuedDownloads.isEmpty())
        assertTrue(newState.statusMessage.contains("cancelled"))
    }

    // === Tests for DeleteModel ===

    @Test
    fun `reduce with DeleteModel Loading should show deleting message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DeleteModel(testModels[0].modelId),
            actionState = ActionState.Loading,
        )

        assertTrue(newState.statusMessage.contains("Deleting"))
    }

    @Test
    fun `reduce with DeleteModel Success should reset model status`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId
        val stateWithDownloadedModel = initialState.copy(
            models = listOf(
                ModelInfo(config = testModels[0], isDownloaded = true, downloadStatus = DownloadStatus.Completed),
                ModelInfo(config = testModels[1], isDownloaded = false, downloadStatus = DownloadStatus.NotStarted),
            ),
        )

        val newState = viewModel.reduce(
            state = stateWithDownloadedModel,
            action = UiAction.DeleteModel(modelId),
            actionState = ActionState.Success(true to modelId),
        )

        val deletedModel = newState.models.find { it.config.modelId == modelId }
        assertFalse(deletedModel!!.isDownloaded)
        assertEquals(DownloadStatus.NotStarted, deletedModel.downloadStatus)
        assertTrue(newState.statusMessage.contains("deleted"))
    }

    @Test
    fun `reduce with DeleteModel Success should clear selection if deleted model was selected`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId
        val stateWithSelection = initialState.copy(
            selectedModelId = modelId,
            models = listOf(
                ModelInfo(config = testModels[0], isDownloaded = true, downloadStatus = DownloadStatus.Completed),
                ModelInfo(config = testModels[1], isDownloaded = false, downloadStatus = DownloadStatus.NotStarted),
            ),
        )

        val newState = viewModel.reduce(
            state = stateWithSelection,
            action = UiAction.DeleteModel(modelId),
            actionState = ActionState.Success(true to modelId),
        )

        assertNull(newState.selectedModelId)
    }

    @Test
    fun `reduce with DeleteModel Success should preserve selection if different model deleted`() {
        val viewModel = createViewModel()
        val selectedModelId = testModels[0].modelId
        val deletedModelId = testModels[1].modelId
        val stateWithSelection = initialState.copy(
            selectedModelId = selectedModelId,
            models = listOf(
                ModelInfo(config = testModels[0], isDownloaded = true, downloadStatus = DownloadStatus.Completed),
                ModelInfo(config = testModels[1], isDownloaded = true, downloadStatus = DownloadStatus.Completed),
            ),
        )

        val newState = viewModel.reduce(
            state = stateWithSelection,
            action = UiAction.DeleteModel(deletedModelId),
            actionState = ActionState.Success(true to deletedModelId),
        )

        assertEquals(selectedModelId, newState.selectedModelId)
    }

    @Test
    fun `reduce with DeleteModel failure should show error message`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DeleteModel(modelId),
            actionState = ActionState.Success(false to modelId),
        )

        assertTrue(newState.statusMessage.contains("Failed"))
    }

    // === Tests for ProcessNextDownload (download progress states) ===

    @Test
    fun `reduce with ProcessNextDownload Loading should show starting message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.ProcessNextDownload(testModels[0].modelId),
            actionState = ActionState.Loading,
        )

        assertTrue(newState.statusMessage.contains("Starting download"))
    }

    @Test
    fun `reduce with ProcessNextDownload InProgress should update download progress`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId
        val progress = 45f
        val stateWithModels = initialState.copy(
            models = testModels.map { ModelInfo(config = it) },
        )

        val newState = viewModel.reduce(
            state = stateWithModels,
            action = UiAction.ProcessNextDownload(modelId),
            actionState = ActionState.Success(ModelDownloadManager.Status.InProgress(progress)),
        )

        assertEquals(modelId, newState.currentDownload?.modelId)
        assertEquals(progress, newState.currentDownload?.progress)
        val model = newState.models.find { it.config.modelId == modelId }
        assertTrue(model?.downloadStatus is DownloadStatus.Downloading)
        assertEquals(progress, (model?.downloadStatus as DownloadStatus.Downloading).progress)
    }

    @Test
    fun `reduce with ProcessNextDownload Completed should mark model as downloaded`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId
        val stateWithModels = initialState.copy(
            models = testModels.map { ModelInfo(config = it) },
            currentDownload = DownloadInfo(modelId = modelId, progress = 100f),
        )

        val newState = viewModel.reduce(
            state = stateWithModels,
            action = UiAction.ProcessNextDownload(modelId),
            actionState = ActionState.Success(ModelDownloadManager.Status.Completed(File("/fake/path"))),
        )

        val model = newState.models.find { it.config.modelId == modelId }
        assertTrue(model!!.isDownloaded)
        assertEquals(DownloadStatus.Completed, model.downloadStatus)
        assertNull(newState.currentDownload) // Download finished
        assertTrue(newState.statusMessage.contains("complete"))
    }

    @Test
    fun `reduce with ProcessNextDownload Failed should set failed status`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId
        val errorMessage = "Network error"
        val stateWithModels = initialState.copy(
            models = testModels.map { ModelInfo(config = it) },
            currentDownload = DownloadInfo(modelId = modelId, progress = 50f),
        )

        val newState = viewModel.reduce(
            state = stateWithModels,
            action = UiAction.ProcessNextDownload(modelId),
            actionState = ActionState.Success(ModelDownloadManager.Status.Failed(errorMessage)),
        )

        val model = newState.models.find { it.config.modelId == modelId }
        assertTrue(model?.downloadStatus is DownloadStatus.Failed)
        assertEquals(errorMessage, (model?.downloadStatus as DownloadStatus.Failed).error)
        assertTrue(newState.statusMessage.contains("failed"))
    }

    @Test
    fun `reduce with ProcessNextDownload Cancelled should reset model status`() {
        val viewModel = createViewModel()
        val modelId = testModels[0].modelId
        val stateWithModels = initialState.copy(
            models = testModels.map { ModelInfo(config = it, downloadStatus = DownloadStatus.Downloading(50f)) },
            currentDownload = DownloadInfo(modelId = modelId, progress = 50f),
            queuedDownloads = listOf(testModels[1].modelId),
        )

        val newState = viewModel.reduce(
            state = stateWithModels,
            action = UiAction.ProcessNextDownload(modelId),
            actionState = ActionState.Success(ModelDownloadManager.Status.Cancelled),
        )

        val model = newState.models.find { it.config.modelId == modelId }
        assertEquals(DownloadStatus.NotStarted, model?.downloadStatus)
        assertNull(newState.currentDownload)
        assertTrue(newState.queuedDownloads.isEmpty()) // Queue cleared on cancel
        assertTrue(newState.statusMessage.contains("cancelled"))
    }

    @Test
    fun `reduce with ProcessNextDownload Completed should process next in queue`() {
        val viewModel = createViewModel()
        val currentModelId = testModels[0].modelId
        val queuedModelId = testModels[1].modelId
        val stateWithQueue = initialState.copy(
            models = testModels.map { ModelInfo(config = it) },
            currentDownload = DownloadInfo(modelId = currentModelId, progress = 100f),
            queuedDownloads = listOf(queuedModelId),
        )

        val newState = viewModel.reduce(
            state = stateWithQueue,
            action = UiAction.ProcessNextDownload(currentModelId),
            actionState = ActionState.Success(ModelDownloadManager.Status.Completed(File("/fake/path"))),
        )

        // Next download should be set up
        assertEquals(queuedModelId, newState.currentDownload?.modelId)
        assertEquals(0f, newState.currentDownload?.progress)
        assertTrue(newState.queuedDownloads.isEmpty())
        assertTrue(newState.statusMessage.contains("Starting next"))
    }

    // === Tests for Error state ===

    @Test
    fun `reduce with Error should set error message`() {
        val viewModel = createViewModel()
        val errorMessage = "Something went wrong"

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel(testModels[0].modelId),
            actionState = ActionState.Error(Exception(errorMessage)),
        )

        assertTrue(newState.statusMessage.contains("Error"))
        assertTrue(newState.statusMessage.contains(errorMessage))
    }

    @Test
    fun `reduce with Error without message should use default error message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel(testModels[0].modelId),
            actionState = ActionState.Error(Exception()),
        )

        assertTrue(newState.statusMessage.contains("Error"))
        assertTrue(newState.statusMessage.contains("Unknown error"))
    }
}

/**
 * Fake ModelDownloadManager for testing.
 */
internal class FakeModelDownloadManager(
    private val modelExistsMap: Map<String, Boolean> = emptyMap(),
    private val defaultModelExists: Boolean = false,
    private val deleteSuccess: Boolean = true,
) : ModelDownloadManager {
    var downloadModelCalled = false
    var cancelDownloadCalled = false
    var deleteModelCalled = false
    var lastDeletedFilename: String? = null

    override fun downloadModel(
        modelId: String,
        downloadUrl: String,
        bundleFilename: String,
    ): Flow<ModelDownloadManager.Status> = flow {
        downloadModelCalled = true
        emit(ModelDownloadManager.Status.Completed(File("/fake/path/$bundleFilename")))
    }

    override fun cancelDownload() {
        cancelDownloadCalled = true
    }

    override suspend fun modelExists(bundleFilename: String): Boolean =
        modelExistsMap[bundleFilename] ?: defaultModelExists

    override fun getModelPath(bundleFilename: String): String = "/fake/path/$bundleFilename"

    override suspend fun deleteModel(bundleFilename: String): Boolean {
        deleteModelCalled = true
        lastDeletedFilename = bundleFilename
        return deleteSuccess
    }
}
