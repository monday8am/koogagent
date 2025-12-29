package com.monday8am.presentation.notifications

import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for NotificationViewModelImpl, focusing on the reduce function.
 * These tests verify that state transitions are correct and LogMessage types are properly set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    private val testModel = ModelCatalog.DEFAULT
    private val testModelPath = "/fake/path/model.bin"
    private val initialState = UiState(selectedModel = testModel)
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val testContext =
        NotificationContext(
            mealType = MealType.BREAKFAST,
            motivationLevel = MotivationLevel.HIGH,
            alreadyLogged = false,
            userLocale = "en-US",
            country = "US",
        )

    private val testNotification =
        NotificationResult(
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
    ): NotificationViewModelImpl =
        NotificationViewModelImpl(
            selectedModel = testModel,
            modelPath = testModelPath,
            inferenceEngine = inferenceEngine,
            notificationEngine = notificationEngine,
            weatherProvider = weatherProvider,
            locationProvider = locationProvider,
            deviceContextProvider = deviceContextProvider,
        )

    @Test
    fun `reduce with ShowNotification Loading should set InitializingModel message`() {
        val viewModel = createViewModel()

        val newState =
            viewModel.reduce(
                state = initialState,
                action = UiAction.ShowNotification,
                actionState = ActionState.Loading,
            )

        assertEquals(LogMessage.InitializingModel, newState.statusMessage)
    }

    @Test
    fun `reduce with ShowNotification Success should set PromptingWithContext message`() {
        val viewModel = createViewModel()
        val stateWithContext = initialState.copy(context = testContext)

        val newState =
            viewModel.reduce(
                state = stateWithContext,
                action = UiAction.ShowNotification,
                actionState = ActionState.Success(Unit),
            )

        assertTrue(newState.statusMessage is LogMessage.PromptingWithContext)
        assertTrue(newState.statusMessage.contextFormatted.contains("BREAKFAST"))
    }

    @Test
    fun `reduce with NotificationReady should set NotificationGenerated message and update notification`() {
        val viewModel = createViewModel()

        val newState =
            viewModel.reduce(
                state = initialState,
                action = UiAction.NotificationReady(testNotification),
                actionState = ActionState.Success(Unit),
            )

        assertTrue(newState.statusMessage is LogMessage.NotificationGenerated)
        assertEquals(testNotification, newState.notification)
        assertTrue(newState.statusMessage.notificationFormatted.contains("Test Title"))
    }

    // === Tests for UpdateContext Success ===

    @Test
    fun `reduce with UpdateContext should update context without changing other state`() {
        val viewModel = createViewModel()

        val newState =
            viewModel.reduce(
                state = initialState,
                action = UiAction.UpdateContext(testContext),
                actionState = ActionState.Success(testContext),
            )

        assertEquals(testContext, newState.context)
        assertEquals(initialState.statusMessage, newState.statusMessage) // Unchanged
    }

    // === Tests for Initialize Success ===

    @Test
    fun `reduce with Initialize Success should set WelcomeModelReady message`() {
        val viewModel = createViewModel()

        val newState =
            viewModel.reduce(
                state = initialState,
                action = UiAction.Initialize,
                actionState = ActionState.Success(Unit),
            )

        assertTrue(newState.statusMessage is LogMessage.WelcomeModelReady)
        assertTrue(newState.statusMessage.modelName.isNotEmpty())
    }

    // === Tests for Error state ===

    @Test
    fun `reduce with Error should set Error message`() {
        val viewModel = createViewModel()
        val errorMessage = "Network error occurred"

        val newState =
            viewModel.reduce(
                state = initialState,
                action = UiAction.ShowNotification,
                actionState = ActionState.Error(Exception(errorMessage)),
            )

        assertTrue(newState.statusMessage is LogMessage.Error)
        assertEquals(errorMessage, newState.statusMessage.message)
    }

    @Test
    fun `reduce with Error without message should use default error message`() {
        val viewModel = createViewModel()

        val newState =
            viewModel.reduce(
                state = initialState,
                action = UiAction.ShowNotification,
                actionState = ActionState.Error(Exception()),
            )

        assertTrue(newState.statusMessage is LogMessage.Error)
        assertEquals("Unknown error", newState.statusMessage.message)
    }
}
