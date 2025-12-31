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
    private lateinit var modelsStatusFlow: MutableStateFlow<Map<String, ModelDownloadManager.Status>>

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = ModelRepository()
        modelsStatusFlow = MutableStateFlow(emptyMap())
        fakeDownloadManager = FakeModelDownloadManager(
            modelsStatusFlow = modelsStatusFlow
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
        assertTrue(state.statusMessage.contains(errorMessage))

        viewModel.dispose()
    }

    @Test
    fun `Initialize should allow retry after failure`() = runTest {
        var shouldFail = true
        val catalogProvider = object : ModelCatalogProvider {
            override suspend fun fetchModels(): Result<List<ModelConfiguration>> {
                return if (shouldFail) {
                    Result.failure(Exception("Network error"))
                } else {
                    Result.success(testModels)
                }
            }
        }

        val viewModel = createViewModel(catalogProvider = catalogProvider)
        advanceUntilIdle()

        // Verify failure
        assertNotNull(viewModel.uiState.value.catalogError)

        // Retry
        shouldFail = false
        viewModel.onUiAction(UiAction.Initialize)
        advanceUntilIdle()

        // Verify success
        assertNull(viewModel.uiState.value.catalogError)
        assertEquals(2, viewModel.uiState.value.models.size)

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

    @Test
    fun `Initialize should re-attach to active download`() = runTest {
        // Setup manager with an active download for model1
        modelsStatusFlow.value = mapOf(
            model1.bundleFilename to ModelDownloadManager.Status.InProgress(progress = 42f)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(42f, state.currentDownload?.progress)
        assertEquals(model1.modelId, state.currentDownload?.modelId)

        val model1Info = state.models.find { it.config.modelId == model1.modelId }
        assertNotNull(model1Info)
        assertTrue(model1Info.downloadStatus is DownloadStatus.Downloading)
        assertEquals(42f, model1Info.downloadStatus.progress)

        viewModel.dispose()
    }

    // endregion

    // region Download Flow Tests

    @Test
    fun `DownloadModel should start download when no active download`() = runTest {
        // VM needs models in repository to start download
        fakeRepository.setModels(testModels)

        val downloadManager = FakeModelDownloadManager(
            progressSteps = listOf(25f, 50f, 75f),
            modelsStatusFlow = modelsStatusFlow
        )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))

        // Wait for progress updates and completion
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Download should complete because fake manager sends Completed at the end
        assertNull(state.currentDownload)
        val model = state.models.find { it.config.modelId == model1.modelId }
        assertNotNull(model)
        assertTrue(model.isDownloaded)
        assertEquals(DownloadStatus.Completed, model.downloadStatus)

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel should queue when another download is active`() = runTest {
        fakeRepository.setModels(testModels)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // In the new reactive architecture, queue is derived from modelsStatus.
        // Set up the flow to show model1 in progress and model2 pending.
        modelsStatusFlow.value = mapOf(
            model1.bundleFilename to ModelDownloadManager.Status.InProgress(progress = 10f),
            model2.bundleFilename to ModelDownloadManager.Status.Pending
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.queuedDownloads.size)
        assertEquals(model2.modelId, state.queuedDownloads.first())
        assertEquals(model1.modelId, state.currentDownload?.modelId)

        viewModel.dispose()
    }

    @Test
    fun `CancelDownload should call manager`() = runTest {
        fakeRepository.setModels(testModels)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))
        // Trigger cancel
        viewModel.onUiAction(UiAction.CancelCurrentDownload)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.currentDownload)
        assertTrue(state.queuedDownloads.isEmpty())

        viewModel.dispose()
    }

    // endregion

    // region Delete Model Tests

    @Test
    fun `DeleteModel should reset model status`() = runTest {
        fakeRepository.setModels(testModels)

        // Create a manager that tracks deletion
        var deleted = false
        val downloadManager = object : ModelDownloadManager by fakeDownloadManager {
            override suspend fun modelExists(bundleFilename: String): Boolean = !deleted
            override suspend fun deleteModel(bundleFilename: String): Boolean {
                deleted = true
                return true
            }
        }

        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        // Delete model
        viewModel.onUiAction(UiAction.DeleteModel(model1.modelId))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val deletedModel = state.models.find { it.config.modelId == model1.modelId }
        assertNotNull(deletedModel)
        // After deletion and re-evaluation, model should be NotStarted
        assertEquals(DownloadStatus.NotStarted, deletedModel.downloadStatus)
        assertFalse(deletedModel.isDownloaded)

        viewModel.dispose()
    }

    @Test
    fun `isReady should update reactively when downloadedFilenames changes`() = runTest {
        fakeRepository.setModels(testModels)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Initially not downloaded
        val initialModel = viewModel.uiState.value.models.find { it.config.modelId == model1.modelId }
        assertNotNull(initialModel)
        assertFalse(updatedModelIsReady(viewModel, model1.modelId))

        // Simulate file appearing
        fakeDownloadManager.setDownloadedFilenames(setOf(model1.bundleFilename))
        advanceUntilIdle()

        assertTrue(updatedModelIsReady(viewModel, model1.modelId))

        viewModel.dispose()
    }

    private fun updatedModelIsReady(viewModel: ModelSelectorViewModelImpl, modelId: String): Boolean {
        return viewModel.uiState.value.models.find { it.config.modelId == modelId }?.isDownloaded ?: false
    }

    // endregion
}
