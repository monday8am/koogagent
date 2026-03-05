package com.monday8am.edgelab.presentation.ridesetup

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RideSetupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RideSetupViewModelImpl =
        RideSetupViewModelImpl(ioDispatcher = testDispatcher)

    // region Initialization Tests
    // stateIn initial value is set synchronously — no advancement needed.

    @Test
    fun `Initialize should default to simulation mode`() = runTest {
        val viewModel = createViewModel()

        assertEquals(GpsMode.SIMULATION, viewModel.uiState.value.gpsMode)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should load route catalog`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.routes.isNotEmpty())
        assertEquals("strade-bianche", state.routes.first().id)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should have no route selected`() = runTest {
        val viewModel = createViewModel()

        assertNull(viewModel.uiState.value.selectedRouteId)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should have start disabled`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isStartEnabled)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should default to normal playback speed`() = runTest {
        val viewModel = createViewModel()

        assertEquals(PlaybackSpeed.NORMAL, viewModel.uiState.value.playbackSpeed)

        viewModel.dispose()
    }

    // endregion

    // region GPS Mode Tests

    @Test
    fun `SelectGpsMode should update gps mode`() = runTest {
        val viewModel = createViewModel()

        viewModel.onUiAction(UiAction.SelectGpsMode(GpsMode.DEVICE_GPS))
        runCurrent()

        assertEquals(GpsMode.DEVICE_GPS, viewModel.uiState.value.gpsMode)

        viewModel.dispose()
    }

    @Test
    fun `SelectGpsMode to DEVICE_GPS should enable start without route`() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.isStartEnabled)

        viewModel.onUiAction(UiAction.SelectGpsMode(GpsMode.DEVICE_GPS))
        runCurrent()

        assertTrue(viewModel.uiState.value.isStartEnabled)

        viewModel.dispose()
    }

    // endregion

    // region Route Selection Tests

    @Test
    fun `SelectRoute should update selected route id`() = runTest {
        val viewModel = createViewModel()

        viewModel.onUiAction(UiAction.SelectRoute("strade-bianche"))
        runCurrent()

        assertEquals("strade-bianche", viewModel.uiState.value.selectedRouteId)

        viewModel.dispose()
    }

    @Test
    fun `SelectRoute should enable start button`() = runTest {
        val viewModel = createViewModel()

        viewModel.onUiAction(UiAction.SelectRoute("strade-bianche"))
        runCurrent()

        assertTrue(viewModel.uiState.value.isStartEnabled)

        viewModel.dispose()
    }

    // endregion

    // region Playback Speed Tests

    @Test
    fun `SetPlaybackSpeed should update playback speed`() = runTest {
        val viewModel = createViewModel()

        viewModel.onUiAction(UiAction.SetPlaybackSpeed(PlaybackSpeed.FAST))
        runCurrent()

        assertEquals(PlaybackSpeed.FAST, viewModel.uiState.value.playbackSpeed)
        assertEquals(2f, viewModel.uiState.value.playbackSpeed.multiplier)

        viewModel.dispose()
    }

    // endregion

    // region Advanced Settings Tests

    @Test
    fun `ToggleAdvancedExpanded should flip expanded state`() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.isAdvancedExpanded)

        viewModel.onUiAction(UiAction.ToggleAdvancedExpanded)
        runCurrent()
        assertTrue(viewModel.uiState.value.isAdvancedExpanded)

        viewModel.onUiAction(UiAction.ToggleAdvancedExpanded)
        runCurrent()
        assertFalse(viewModel.uiState.value.isAdvancedExpanded)

        viewModel.dispose()
    }

    @Test
    fun `UpdateAdvancedSettings should update settings`() = runTest {
        val viewModel = createViewModel()

        val updated =
            AdvancedSettings(useRemoteLLM = false, showDeveloperHUD = true, enableAutoVoice = false)
        viewModel.onUiAction(UiAction.UpdateAdvancedSettings(updated))
        runCurrent()

        val settings = viewModel.uiState.value.advancedSettings
        assertFalse(settings.useRemoteLLM)
        assertTrue(settings.showDeveloperHUD)
        assertFalse(settings.enableAutoVoice)

        viewModel.dispose()
    }

    // endregion

    // region StartRide Tests

    @Test
    fun `StartRide action should not throw and not change state`() = runTest {
        val viewModel = createViewModel()

        viewModel.onUiAction(UiAction.SelectRoute("strade-bianche"))
        runCurrent()
        val stateBefore = viewModel.uiState.value

        viewModel.onUiAction(UiAction.StartRide)
        runCurrent()

        assertEquals(stateBefore, viewModel.uiState.value)

        viewModel.dispose()
    }

    // endregion
}
