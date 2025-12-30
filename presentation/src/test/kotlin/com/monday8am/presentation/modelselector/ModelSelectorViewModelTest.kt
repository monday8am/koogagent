package com.monday8am.presentation.modelselector

import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.ModelRepository
import com.monday8am.presentation.notifications.FakeModelDownloadManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

    private lateinit var fakeRepository: ModelRepository
    private lateinit var fakeDownloadManager: FakeModelDownloadManager
    private lateinit var activeDownloadFlow: MutableStateFlow<Map<String, ModelDownloadManager.Status>>

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = ModelRepository()
        activeDownloadFlow = MutableStateFlow(emptyMap())
        fakeDownloadManager = FakeModelDownloadManager(
            modelExists = false,
            activeDownloadFlow = activeDownloadFlow
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        catalogProvider: ModelCatalogProvider = FakeModelCatalogProvider(models = testModels),
        downloadManager: ModelDownloadManager = fakeDownloadManager,
    ): ModelSelectorViewModelImpl {
        return ModelSelectorViewModelImpl(
            modelCatalogProvider = catalogProvider,
            modelDownloadManager = downloadManager,
            modelRepository = fakeRepository,
            ioDispatcher = testDispatcher,
        )
    }

    // region Initialization Tests

    @Test
    fun `Initialize should load catalog and show models`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.models.size)
        assertFalse(state.isLoadingCatalog)
        assertNull(state.catalogError)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should handle catalog load failure`() = runTest {
        val errorMessage = "Network error"
        val viewModel = createViewModel(
            catalogProvider = FakeModelCatalogProvider(shouldFail = true, failureMessage = errorMessage)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingCatalog)
        assertEquals(errorMessage, state.catalogError)
        assertTrue(state.statusMessage.contains("Failed"))

        viewModel.dispose()
    }

    @Test
    fun `Initialize should populate repository with models`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, fakeRepository.getAllModels().size)
        assertNotNull(fakeRepository.findById(model1.modelId))
        assertNotNull(fakeRepository.findById(model2.modelId))

        viewModel.dispose()
    }

    // endregion

    // region Model Selection Tests

    @Test
    fun `SelectModel should update selectedModelId`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.SelectModel(model1.modelId))
        advanceUntilIdle()

        assertEquals(model1.modelId, viewModel.uiState.value.selectedModelId)
        assertTrue(viewModel.uiState.value.statusMessage.contains("Selected"))

        viewModel.dispose()
    }

    // endregion

    // region Download Flow Tests

    @Test
    fun `DownloadModel should start download when no active download`() = runTest {
        val downloadManager = FakeModelDownloadManager(
            modelExists = false,
            progressSteps = listOf(25f, 50f, 75f, 100f),
            activeDownloadFlow = activeDownloadFlow
        )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Download should complete
        assertNull(state.currentDownload) // Download finished
        val model = state.models.find { it.config.modelId == model1.modelId }
        assertTrue(model!!.isDownloaded)
        assertEquals(DownloadStatus.Completed, model.downloadStatus)

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel should queue when another download is active`() = runTest {
        val downloadManager = FakeModelDownloadManager(
            modelExists = false,
            progressSteps = listOf(25f, 50f, 75f, 100f),
            activeDownloadFlow = activeDownloadFlow
        )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        // Start first download
        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))
        // Don't advance yet - immediately queue another
        viewModel.onUiAction(UiAction.DownloadModel(model2.modelId))
        advanceUntilIdle()

        // Both should eventually complete (queue processing)
        val state = viewModel.uiState.value
        val model1Info = state.models.find { it.config.modelId == model1.modelId }
        val model2Info = state.models.find { it.config.modelId == model2.modelId }

        // model1 was downloaded first
        assertTrue(model1Info!!.isDownloaded)
        // model2 might still be in queue or completed depending on timing
        // Just verify the queue is empty at the end
        assertTrue(state.queuedDownloads.isEmpty())

        viewModel.dispose()
    }

    @Test
    fun `CancelDownload should clear current download and queue`() = runTest {
        val downloadManager = FakeModelDownloadManager(
            modelExists = false,
            progressSteps = listOf(25f, 50f, 75f, 100f),
            activeDownloadFlow = activeDownloadFlow
        )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        // Start a download and queue another
        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))
        viewModel.onUiAction(UiAction.DownloadModel(model2.modelId))

        // Cancel immediately
        viewModel.onUiAction(UiAction.CancelCurrentDownload)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.currentDownload)
        assertTrue(state.queuedDownloads.isEmpty())
        assertTrue(state.statusMessage.contains("cancelled"))

        viewModel.dispose()
    }

    // endregion

    // region Delete Model Tests

    @Test
    fun `DeleteModel should reset model status`() = runTest {
        // Setup with a downloaded model
        val downloadManager = FakeModelDownloadManager(
            modelExists = true, // Model is already downloaded
            activeDownloadFlow = activeDownloadFlow
        )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DeleteModel(model1.modelId))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val deletedModel = state.models.find { it.config.modelId == model1.modelId }
        assertFalse(deletedModel!!.isDownloaded)
        assertEquals(DownloadStatus.NotStarted, deletedModel.downloadStatus)
        assertTrue(state.statusMessage.contains("deleted"))

        viewModel.dispose()
    }

    @Test
    fun `DeleteModel should clear selection if deleted model was selected`() = runTest {
        val downloadManager = FakeModelDownloadManager(
            modelExists = true,
            activeDownloadFlow = activeDownloadFlow
        )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        // Select then delete
        viewModel.onUiAction(UiAction.SelectModel(model1.modelId))
        advanceUntilIdle()
        viewModel.onUiAction(UiAction.DeleteModel(model1.modelId))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedModelId)

        viewModel.dispose()
    }

    // endregion
}
