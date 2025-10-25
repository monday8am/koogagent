package com.monday8am.presentation.notifications

import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for NotificationViewModelImpl, focusing on the reduce function.
 * These tests verify that state transitions are correct and LogMessage types are properly set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelReduceTest {

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

    private val testContext = NotificationContext(
        mealType = MealType.BREAKFAST,
        motivationLevel = MotivationLevel.HIGH,
        alreadyLogged = false,
        userLocale = "en-US",
        country = "US",
    )

    private val testNotification = NotificationResult(
        title = "Test Title",
        body = "Test Body",
        language = "en",
        confidence = 0.95,
        isFallback = false,
    )

    // Helper to create a ViewModel instance for testing
    private fun createViewModel(
        inferenceEngine: FakeLocalInferenceEngine = FakeLocalInferenceEngine(),
        notificationEngine: FakeNotificationEngine = FakeNotificationEngine(),
        weatherProvider: FakeWeatherProvider = FakeWeatherProvider(),
        locationProvider: FakeLocationProvider = FakeLocationProvider(),
        deviceContextProvider: FakeDeviceContextProvider = FakeDeviceContextProvider(),
        modelManager: FakeModelDownloadManager = FakeModelDownloadManager(),
    ): NotificationViewModelImpl {
        return NotificationViewModelImpl(
            inferenceEngine = inferenceEngine,
            notificationEngine = notificationEngine,
            weatherProvider = weatherProvider,
            locationProvider = locationProvider,
            deviceContextProvider = deviceContextProvider,
            modelManager = modelManager,
        )
    }

    @Test
    fun `reduce with DownloadModel Loading should update download status`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel,
            actionState = ActionState.Loading
        )

        assertEquals(ModelDownloadManager.Status.InProgress(0f), newState.downloadStatus)
    }

    @Test
    fun `reduce with ShowNotification Loading should set InitializingModel message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.ShowNotification,
            actionState = ActionState.Loading
        )

        assertEquals(LogMessage.InitializingModel, newState.statusMessage)
    }

    @Test
    fun `reduce with DownloadModel InProgress should set Downloading message with progress`() {
        val viewModel = createViewModel()
        val progress = 0.45f

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel,
            actionState = ActionState.Success(ModelDownloadManager.Status.InProgress(progress))
        )

        assertTrue(newState.statusMessage is LogMessage.Downloading)
        assertEquals(progress, newState.statusMessage.progress)
        assertFalse(newState.isModelReady)
    }

    @Test
    fun `reduce with DownloadModel Completed should set DownloadComplete message and mark model ready`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel,
            actionState = ActionState.Success(ModelDownloadManager.Status.Completed(java.io.File("/fake/model.bin")))
        )

        assertEquals(LogMessage.DownloadComplete, newState.statusMessage)
        assertTrue(newState.isModelReady)
        assertTrue(newState.downloadStatus is ModelDownloadManager.Status.Completed)
    }

    @Test
    fun `reduce with DownloadModel other status should set DownloadFinished message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel,
            actionState = ActionState.Success(ModelDownloadManager.Status.Pending)
        )

        assertEquals(LogMessage.DownloadFinished, newState.statusMessage)
        assertFalse(newState.isModelReady)
    }

    @Test
    fun `reduce with ShowNotification Success should set PromptingWithContext message`() {
        val viewModel = createViewModel()
        val stateWithContext = initialState.copy(context = testContext)

        val newState = viewModel.reduce(
            state = stateWithContext,
            action = UiAction.ShowNotification,
            actionState = ActionState.Success(Unit)
        )

        assertTrue(newState.statusMessage is LogMessage.PromptingWithContext)
        assertTrue(newState.statusMessage.contextFormatted.contains("BREAKFAST"))
    }

    @Test
    fun `reduce with NotificationReady should set NotificationGenerated message and update notification`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.NotificationReady(testNotification),
            actionState = ActionState.Success(Unit)
        )

        assertTrue(newState.statusMessage is LogMessage.NotificationGenerated)
        assertEquals(testNotification, newState.notification)
        assertTrue(newState.statusMessage.notificationFormatted.contains("Test Title"))
    }

    // === Tests for UpdateContext Success ===

    @Test
    fun `reduce with UpdateContext should update context without changing other state`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.UpdateContext(testContext),
            actionState = ActionState.Success(testContext)
        )

        assertEquals(testContext, newState.context)
        assertEquals(initialState.statusMessage, newState.statusMessage) // Unchanged
        assertEquals(initialState.isModelReady, newState.isModelReady) // Unchanged
    }

    // === Tests for Initialize Success ===

    @Test
    fun `reduce with Initialize Success when model ready should set WelcomeModelReady message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.Initialize,
            actionState = ActionState.Success(true)
        )

        assertTrue(newState.statusMessage is LogMessage.WelcomeModelReady)
        assertTrue(newState.isModelReady)
        assertTrue(newState.statusMessage.modelName.contains("gemma"))
    }

    @Test
    fun `reduce with Initialize Success when model not ready should set WelcomeDownloadRequired message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.Initialize,
            actionState = ActionState.Success(false)
        )

        assertEquals(LogMessage.WelcomeDownloadRequired, newState.statusMessage)
        assertFalse(newState.isModelReady)
    }

    // === Tests for Error state ===

    @Test
    fun `reduce with Error should set Error message`() {
        val viewModel = createViewModel()
        val errorMessage = "Network error occurred"

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.DownloadModel,
            actionState = ActionState.Error(Exception(errorMessage))
        )

        assertTrue(newState.statusMessage is LogMessage.Error)
        assertEquals(errorMessage, newState.statusMessage.message)
    }

    @Test
    fun `reduce with Error without message should use default error message`() {
        val viewModel = createViewModel()

        val newState = viewModel.reduce(
            state = initialState,
            action = UiAction.ShowNotification,
            actionState = ActionState.Error(Exception())
        )

        assertTrue(newState.statusMessage is LogMessage.Error)
        assertEquals("Unknown error", newState.statusMessage.message)
    }
}
