package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.ModelRepository
import com.monday8am.presentation.notifications.FakeModelDownloadManager
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Fake implementation of ModelCatalogProvider for testing.
 */
internal class FakeModelCatalogProvider(
    private val models: List<ModelConfiguration> = emptyList(),
    private val shouldFail: Boolean = false,
    private val failureMessage: String = "Test failure",
) : ModelCatalogProvider {
    override suspend fun fetchModels(): Result<List<ModelConfiguration>> {
        return if (shouldFail) {
            Result.failure(Exception(failureMessage))
        } else {
            Result.success(models)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelSelectorViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val model1 = ModelCatalog.QWEN3_0_6B
    private val model2 = ModelCatalog.GEMMA3_1B
    private val testModels = listOf(model1, model2)

    private val initialState = UiState()
    private lateinit var viewModel: ModelSelectorViewModelImpl
    private lateinit var fakeRepository: ModelRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = ModelRepository()
        // Create a fresh ViewModel for each test
        viewModel =
            ModelSelectorViewModelImpl(
                modelCatalogProvider = FakeModelCatalogProvider(models = testModels),
                modelDownloadManager = FakeModelDownloadManager(),
                modelRepository = fakeRepository,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region Initialization Tests

    @Test
    fun `Initialize should show loading state`() {
        val newState = reduce(action = UiAction.Initialize, result = Unit)

        assertTrue(newState.isLoadingCatalog)
        assertStatusMessageContains(newState, "Loading models")
    }

    @Test
    fun `CatalogLoaded should populate models and show correct count`() {
        val modelInfoList = testModels.map { ModelInfo(config = it, isDownloaded = false) }

        val newState = reduce(action = UiAction.CatalogLoaded(testModels), result = modelInfoList)

        assertEquals(2, newState.models.size)
        assertEquals(model1.modelId, newState.models[0].config.modelId)
        assertFalse(newState.isLoadingCatalog)
        assertNull(newState.catalogError)
        assertStatusMessageContains(newState, "0 of 2")
    }

    @Test
    fun `CatalogLoaded should correctly count downloaded models`() {
        val modelInfoList =
            listOf(
                ModelInfo(config = model1, isDownloaded = true),
                ModelInfo(config = model2, isDownloaded = false),
            )

        val newState = reduce(action = UiAction.CatalogLoaded(testModels), result = modelInfoList)

        assertFalse(newState.isLoadingCatalog)
        assertStatusMessageContains(newState, "1 of 2")
    }

    @Test
    fun `CatalogLoadFailed should set error and stop loading`() {
        val errorMessage = "Network error"

        val newState = reduce(action = UiAction.CatalogLoadFailed(errorMessage), result = errorMessage)

        assertFalse(newState.isLoadingCatalog)
        assertEquals(errorMessage, newState.catalogError)
        assertStatusMessageContains(newState, "Failed to load catalog")
    }

    @Test
    fun `CatalogLoaded should populate repository with models`() {
        val modelInfoList = testModels.map { ModelInfo(config = it, isDownloaded = false) }

        val newState = reduce(action = UiAction.CatalogLoaded(testModels), result = modelInfoList)

        // Verify repository was populated
        assertEquals(2, fakeRepository.getAllModels().size)
        assertNotNull(fakeRepository.findById(model1.modelId))
        assertNotNull(fakeRepository.findById(model2.modelId))
    }

    // endregion

    // region Model Selection and Deletion Tests

    @Test
    fun `SelectModel should update selectedModelId and status message`() {
        val stateWithModels = givenState(models = testModels.map { ModelInfo(config = it) })

        val newState = reduce(
            state = stateWithModels,
            action = UiAction.SelectModel(model1.modelId),
            result = model1.modelId,
        )

        assertEquals(model1.modelId, newState.selectedModelId)
        assertStatusMessageContains(newState, "Selected", model1.displayName)
    }

    @Test
    fun `DeleteModel should reset model status and show deleted message`() {
        val stateWithDownloadedModel =
            givenState(
                models =
                listOf(
                    ModelInfo(config = model1, isDownloaded = true, downloadStatus = DownloadStatus.Completed),
                    ModelInfo(config = model2, isDownloaded = false),
                ),
            )

        val newState =
            reduce(
                state = stateWithDownloadedModel,
                action = UiAction.DeleteModel(model1.modelId),
                result = true to model1.modelId,
            )

        val deletedModel = newState.models.find { it.config.modelId == model1.modelId }
        assertFalse(deletedModel!!.isDownloaded)
        assertEquals(DownloadStatus.NotStarted, deletedModel.downloadStatus)
        assertStatusMessageContains(newState, "deleted")
    }

    @Test
    fun `DeleteModel should clear selection if the deleted model was selected`() {
        val stateWithSelection =
            givenState(
                selectedModelId = model1.modelId,
                models = listOf(ModelInfo(config = model1, isDownloaded = true)),
            )

        val newState =
            reduce(
                state = stateWithSelection,
                action = UiAction.DeleteModel(model1.modelId),
                result = true to model1.modelId,
            )

        assertNull(newState.selectedModelId)
    }

    // endregion

    // region Download Flow Tests

    @Test
    fun `DownloadModel should start a new download and select model if none is active`() {
        val newState = reduce(action = UiAction.DownloadModel(model1.modelId), result = model1.modelId)

        assertEquals(model1.modelId, newState.currentDownload?.modelId)
        assertEquals(0f, newState.currentDownload?.progress)
        assertEquals(model1.modelId, newState.selectedModelId)
        assertStatusMessageContains(newState, "Starting download")
    }

    @Test
    fun `DownloadModel should queue a download if another is already in progress`() {
        val stateWithDownload =
            givenState(
                currentDownload = DownloadInfo(modelId = model1.modelId, progress = 50f),
                models = testModels.map { ModelInfo(config = it) },
            )

        val newState =
            reduce(
                state = stateWithDownload,
                action = UiAction.DownloadModel(model2.modelId),
                result = model2.modelId,
            )

        assertTrue(newState.queuedDownloads.contains(model2.modelId))
        assertEquals(model1.modelId, newState.currentDownload?.modelId) // Unchanged
        val queuedModel = newState.models.find { it.config.modelId == model2.modelId }
        assertEquals(DownloadStatus.Queued, queuedModel?.downloadStatus)
        assertStatusMessageContains(newState, "queued")
    }

    @Test
    fun `DownloadProgress InProgress should update download progress`() {
        val stateWithModels = givenState(models = testModels.map { ModelInfo(config = it) })
        val progress = 45f

        val newState =
            reduce(
                state = stateWithModels,
                action = UiAction.DownloadProgress(model1.modelId, ModelDownloadManager.Status.InProgress(progress)),
                result = UiAction.DownloadProgress(model1.modelId, ModelDownloadManager.Status.InProgress(progress)),
            )

        assertEquals(model1.modelId, newState.currentDownload?.modelId)
        assertEquals(progress, newState.currentDownload?.progress)
        val model = newState.models.find { it.config.modelId == model1.modelId }
        val downloadStatus = assertIs<DownloadStatus.Downloading>(model?.downloadStatus)
        assertEquals(progress, downloadStatus.progress)
    }

    @Test
    fun `DownloadProgress Completed should mark model as downloaded`() {
        val stateWithModels = givenState(models = testModels.map { ModelInfo(config = it) })

        val newState =
            reduce(
                state = stateWithModels,
                action = UiAction.DownloadProgress(
                    model1.modelId,
                    ModelDownloadManager.Status.Completed(File("dummy"))
                ),
                result = UiAction.DownloadProgress(
                    model1.modelId,
                    ModelDownloadManager.Status.Completed(File("dummy"))
                ),
            )

        assertNull(newState.currentDownload)
        val model = newState.models.find { it.config.modelId == model1.modelId }
        assertTrue(model!!.isDownloaded)
        assertEquals(DownloadStatus.Completed, model.downloadStatus)
        assertStatusMessageContains(newState, "Download complete!")
    }

    @Test
    fun `CancelCurrentDownload should clear currentDownload and queued list`() {
        val stateWithDownloads =
            givenState(
                currentDownload = DownloadInfo(modelId = model1.modelId, progress = 50f),
                queuedDownloads = listOf(model2.modelId),
            )

        val newState =
            reduce(
                state = stateWithDownloads,
                action = UiAction.CancelCurrentDownload,
                result = Unit,
            )

        assertNull(newState.currentDownload)
        assertTrue(newState.queuedDownloads.isEmpty())
        assertStatusMessageContains(newState, "cancelled")
    }

    @Test
    fun `DownloadProgress Cancelled should reset all downloading and queued models to NotStarted`() {
        val stateWithDownloads =
            givenState(
                currentDownload = DownloadInfo(modelId = model1.modelId, progress = 50f),
                queuedDownloads = listOf(model2.modelId),
                models =
                listOf(
                    ModelInfo(config = model1, downloadStatus = DownloadStatus.Downloading(50f)),
                    ModelInfo(config = model2, downloadStatus = DownloadStatus.Queued),
                ),
            )

        val newState =
            reduce(
                state = stateWithDownloads,
                action = UiAction.DownloadProgress(model1.modelId, ModelDownloadManager.Status.Cancelled),
                result = UiAction.DownloadProgress(model1.modelId, ModelDownloadManager.Status.Cancelled),
            )

        // All downloading/queued models should be reset to NotStarted
        val downloadingModel = newState.models.find { it.config.modelId == model1.modelId }
        val queuedModel = newState.models.find { it.config.modelId == model2.modelId }
        assertEquals(DownloadStatus.NotStarted, downloadingModel?.downloadStatus)
        assertEquals(DownloadStatus.NotStarted, queuedModel?.downloadStatus)

        // Download state should be cleared
        assertNull(newState.currentDownload)
        assertTrue(newState.queuedDownloads.isEmpty())
        assertStatusMessageContains(newState, "cancelled")
    }

    @Test
    fun `DownloadProgress Cancelled should not affect already completed models`() {
        val stateWithMixedModels =
            givenState(
                currentDownload = DownloadInfo(modelId = model1.modelId, progress = 50f),
                models =
                listOf(
                    ModelInfo(config = model1, downloadStatus = DownloadStatus.Downloading(50f)),
                    ModelInfo(config = model2, isDownloaded = true, downloadStatus = DownloadStatus.Completed),
                ),
            )

        val newState =
            reduce(
                state = stateWithMixedModels,
                action = UiAction.DownloadProgress(model1.modelId, ModelDownloadManager.Status.Cancelled),
                result = UiAction.DownloadProgress(model1.modelId, ModelDownloadManager.Status.Cancelled),
            )

        // Downloading model should be reset
        val downloadingModel = newState.models.find { it.config.modelId == model1.modelId }
        assertEquals(DownloadStatus.NotStarted, downloadingModel?.downloadStatus)

        // Completed model should remain unchanged
        val completedModel = newState.models.find { it.config.modelId == model2.modelId }
        assertEquals(DownloadStatus.Completed, completedModel?.downloadStatus)
        assertEquals(completedModel?.isDownloaded, true)
    }

    // endregion

    // --- Helper Functions ---

    /**
     * A helper to create a specific UiState for the "arrange" part of a test.
     */
    private fun givenState(
        models: List<ModelInfo> = emptyList(),
        selectedModelId: String? = null,
        currentDownload: DownloadInfo? = null,
        queuedDownloads: List<String> = emptyList(),
    ): UiState = initialState.copy(
        models = models,
        selectedModelId = selectedModelId,
        currentDownload = currentDownload,
        queuedDownloads = queuedDownloads,
    )

    /**
     * A helper to execute the reduce function with a simplified signature.
     * It defaults to using the initial state and a Success ActionState.
     */
    private fun <T : Any> reduce(state: UiState = this.initialState, action: UiAction, result: T): UiState =
        viewModel.reduce(state, action, ActionState.Success(result))

    /**
     * A custom assertion to check if the status message contains all given substrings.
     */
    private fun assertStatusMessageContains(state: UiState, vararg expected: String) {
        assertTrue(
            expected.all { state.statusMessage.contains(it, ignoreCase = true) },
            "Expected status message '${state.statusMessage}' to contain all of: ${expected.joinToString()}",
        )
    }
}
