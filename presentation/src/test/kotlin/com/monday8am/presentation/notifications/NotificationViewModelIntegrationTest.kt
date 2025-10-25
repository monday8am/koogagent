package com.monday8am.presentation.notifications

import app.cash.turbine.test
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
import com.monday8am.koogagent.data.WeatherProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Integration tests for NotificationViewModelImpl.
 * These tests verify the end-to-end Flow pipeline and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should emit Initializing message when model not exists`() = runTest {
        val modelManager = FakeModelDownloadManager(modelExists = false)
        val viewModel = createViewModel(modelManager = modelManager)

        viewModel.uiState.test {
            skipInitialState()
            val state = awaitItem()
            assertEquals(LogMessage.WelcomeDownloadRequired, state.statusMessage)
            assertFalse(state.isModelReady)
        }

        viewModel.dispose()
    }

    @Test
    fun `initial state should emit WelcomeModelReady when model exists`() = runTest {
        val modelManager = FakeModelDownloadManager(modelExists = true)
        val viewModel = createViewModel(modelManager = modelManager)

        viewModel.uiState.test {
            skipInitialState() // Skip the default UiState()

            val state = awaitItem() // Get the initialized state
            assertTrue(state.statusMessage is LogMessage.WelcomeModelReady)
            assertTrue(state.isModelReady)
        }

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel action should emit download progress states`() = runTest {
        val modelManager = FakeModelDownloadManager(
            modelExists = false,
            progressSteps = listOf(0.25f, 0.5f, 0.75f, 1.0f)
        )
        val viewModel = createViewModel(modelManager = modelManager)

        viewModel.uiState.test {
            skipInitialState() // Skip initial emission
            awaitItem() // Skip download model ready item

            viewModel.onUiAction(UiAction.DownloadModel)

            val welcomeDownload = awaitItem()
            assertTrue(welcomeDownload.statusMessage is LogMessage.WelcomeDownloadRequired)

            val progressState1 = awaitItem()
            assertTrue(progressState1.statusMessage is LogMessage.Downloading)
            assertEquals(0.25f, progressState1.statusMessage.progress)

            val progressState2 = awaitItem()
            assertTrue(progressState2.statusMessage is LogMessage.Downloading)
            assertEquals(0.5f, progressState2.statusMessage.progress)

            val progressState3 = awaitItem()
            assertTrue(progressState3.statusMessage is LogMessage.Downloading)
            assertEquals(0.75f, progressState3.statusMessage.progress)

            val progressState4 = awaitItem()
            assertTrue(progressState4.statusMessage is LogMessage.Downloading)
            assertEquals(1.0f, progressState4.statusMessage.progress)

            val completedState = awaitItem()
            assertEquals(LogMessage.DownloadComplete, completedState.statusMessage)
            assertTrue(completedState.isModelReady)
        }

        viewModel.dispose()
    }

    @Test
    fun `UpdateContext action should update context in state`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipInitialState() // Skip initial emission
            awaitItem() // Skip download model ready item

            // Update context
            val newContext = NotificationContext(
                mealType = MealType.LUNCH,
                motivationLevel = MotivationLevel.MEDIUM,
                alreadyLogged = true,
                userLocale = "es-ES",
                country = "ES",
            )
            viewModel.onUiAction(UiAction.UpdateContext(newContext))

            val updatedState = awaitItem()
            assertEquals(newContext, updatedState.context)
            assertEquals(MealType.LUNCH, updatedState.context.mealType)
            assertEquals(MotivationLevel.MEDIUM, updatedState.context.motivationLevel)
            assertTrue(updatedState.context.alreadyLogged)
        }

        viewModel.dispose()
    }

    @Test
    fun `ShowNotification action should initialize engine and set PromptingWithContext message`() = runTest {
        val inferenceEngine = FakeLocalInferenceEngine()
        val viewModel = createViewModel(inferenceEngine = inferenceEngine)

        viewModel.uiState.test {
            skipInitialState()
            awaitItem() // Skip download model ready item

            // Trigger notification
            viewModel.onUiAction(UiAction.ShowNotification)

            // Should first emit InitializingModel during loading
            val loadingState = awaitItem()
            assertEquals(LogMessage.InitializingModel, loadingState.statusMessage)

            // Then should emit PromptingWithContext after initialization
            val promptingState = awaitItem()
            assertTrue(promptingState.statusMessage is LogMessage.PromptingWithContext)

            // Verify engine was initialized
            assertTrue(inferenceEngine.initializeCalled)
        }

        viewModel.dispose()
    }

    @Test
    fun `Error during download should emit Error message`() = runTest {
        val modelManager = FakeModelDownloadManager(shouldFail = true)
        val viewModel = createViewModel(modelManager = modelManager)

        viewModel.uiState.test {
            skipInitialState() // Skip initial emission
            awaitItem() // Skip download model ready item

            // Trigger download
            viewModel.onUiAction(UiAction.DownloadModel)
            awaitItem() // Skip download model notification

            // Should emit error state with LogMessage.Error
            val errorState = awaitItem()
            assertTrue(errorState.statusMessage is LogMessage.Error)
            assertEquals("Download failed", errorState.statusMessage.message)
        }

        viewModel.dispose()
    }

    @Test
    fun `NotificationReady action should update notification in state and trigger side effect`() = runTest {
        val notificationEngine = FakeNotificationEngine()
        val viewModel = createViewModel(notificationEngine = notificationEngine)

        val testNotification = NotificationResult(
            title = "Healthy Breakfast",
            body = "Time for oats!",
            language = "en",
            confidence = 0.9,
            isFallback = false,
        )

        viewModel.uiState.test {
            skipInitialState() // Skip initial emission
            awaitItem() // Skip download model ready item

            // Trigger notification ready
            viewModel.onUiAction(UiAction.NotificationReady(testNotification))

            // Should emit state with notification
            val notificationState = awaitItem()
            assertTrue(notificationState.statusMessage is LogMessage.NotificationGenerated)
            assertNotNull(notificationState.notification)
            assertEquals(testNotification, notificationState.notification)

            // Give time for side effect to execute
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify notification engine was called
            assertTrue(notificationEngine.showNotificationCalled)
            assertEquals(testNotification, notificationEngine.lastNotification)
        }

        viewModel.dispose()
    }

    private fun createViewModel(
        inferenceEngine: LocalInferenceEngine = FakeLocalInferenceEngine(),
        notificationEngine: NotificationEngine = FakeNotificationEngine(),
        weatherProvider: WeatherProvider = FakeWeatherProvider(),
        locationProvider: LocationProvider = FakeLocationProvider(),
        deviceContextProvider: DeviceContextProvider = FakeDeviceContextProvider(),
        modelManager: ModelDownloadManager = FakeModelDownloadManager(),
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
}
