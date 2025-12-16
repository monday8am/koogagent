package com.monday8am.presentation.notifications

import app.cash.turbine.test
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for NotificationViewModelImpl.
 * These tests verify the end-to-end Flow pipeline and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelIntegrationTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testModelPath = "/fake/path/model.bin"

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should emit WelcomeModelReady message`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                skipInitialState() // Skip the default UiState()

                val state = awaitItem() // Get the initialized state
                assertTrue(state.statusMessage is LogMessage.WelcomeModelReady)
            }

            viewModel.dispose()
        }

    @Test
    fun `UpdateContext action should update context in state`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                skipInitialState() // Skip initial emission
                awaitItem() // Skip WelcomeModelReady item

                // Update context
                val newContext =
                    NotificationContext(
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

    @Ignore("Outdated!")
    fun `ShowNotification action should initialize engine and set PromptingWithContext message`() =
        runTest {
            val inferenceEngine = FakeLocalInferenceEngine()
            val viewModel = createViewModel(inferenceEngine = inferenceEngine)

            viewModel.uiState.test {
                skipInitialState()
                awaitItem() // Skip WelcomeModelReady item

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
    fun `NotificationReady action should update notification in state and trigger side effect`() =
        runTest {
            val notificationEngine = FakeNotificationEngine()
            val viewModel = createViewModel(notificationEngine = notificationEngine)

            val testNotification =
                NotificationResult(
                    title = "Healthy Breakfast",
                    body = "Time for oats!",
                    language = "en",
                    confidence = 0.9,
                    isFallback = false,
                )

            viewModel.uiState.test {
                skipInitialState() // Skip initial emission
                awaitItem() // Skip WelcomeModelReady item

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

    @Test
    fun `Error during initialization should emit Error message`() =
        runTest {
            val failingEngine =
                object : LocalInferenceEngine by FakeLocalInferenceEngine() {
                    override fun initializeAsFlow(
                        modelConfig: com.monday8am.koogagent.data.ModelConfiguration,
                        modelPath: String,
                    ) = kotlinx.coroutines.flow.flow<LocalInferenceEngine> {
                        throw Exception("Initialization failed")
                    }
                }
            val viewModel = createViewModel(inferenceEngine = failingEngine)

            viewModel.uiState.test {
                skipInitialState()
                awaitItem() // Skip WelcomeModelReady item

                // Trigger notification which will fail during engine initialization
                viewModel.onUiAction(UiAction.ShowNotification)

                // Should first emit loading
                val loadingState = awaitItem()
                assertEquals(LogMessage.InitializingModel, loadingState.statusMessage)

                // Then should emit error
                val errorState = awaitItem()
                assertTrue(errorState.statusMessage is LogMessage.Error)
                assertEquals("Initialization failed", (errorState.statusMessage as LogMessage.Error).message)
            }

            viewModel.dispose()
        }

    private fun createViewModel(
        inferenceEngine: LocalInferenceEngine = FakeLocalInferenceEngine(),
        notificationEngine: NotificationEngine = FakeNotificationEngine(),
        weatherProvider: WeatherProvider = FakeWeatherProvider(),
        locationProvider: LocationProvider = FakeLocationProvider(),
        deviceContextProvider: DeviceContextProvider = FakeDeviceContextProvider(),
    ): NotificationViewModelImpl =
        NotificationViewModelImpl(
            selectedModel = ModelCatalog.DEFAULT,
            modelPath = testModelPath,
            inferenceEngine = inferenceEngine,
            notificationEngine = notificationEngine,
            weatherProvider = weatherProvider,
            locationProvider = locationProvider,
            deviceContextProvider = deviceContextProvider,
        )
}
