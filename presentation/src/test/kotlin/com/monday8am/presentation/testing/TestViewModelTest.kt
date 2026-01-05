package com.monday8am.presentation.testing

import com.monday8am.koogagent.data.HardwareBackend
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.presentation.notifications.FakeLocalInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var inferenceEngine: FakeLocalInferenceEngine
    private lateinit var viewModel: TestViewModelImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        inferenceEngine = FakeLocalInferenceEngine()
        val config = ModelCatalog.DEFAULT.copy(hardwareAcceleration = HardwareBackend.CPU_ONLY)
        viewModel = TestViewModelImpl(
            initialModel = config,
            modelPath = "/fake/path",
            inferenceEngine = inferenceEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        val state = viewModel.uiState.value
        assertEquals(
            "Initial backend should be CPU",
            HardwareBackend.CPU_ONLY,
            state.selectedModel.hardwareAcceleration
        )
        assertFalse(state.isRunning)
        assertFalse(state.isInitializing)
    }

    @Test
    fun `RunTests triggers initialization and updates state`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        // Run tests (uses current CPU backend)
        viewModel.onUiAction(TestUiAction.RunTests(useGpu = false))

        assertTrue(inferenceEngine.initializeCalled)
    }

    @Test
    fun `RunTests with useGpu updates model`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        // Run tests with GPU toggle
        viewModel.onUiAction(TestUiAction.RunTests(useGpu = true))

        assertTrue("Engine should have closeSession called", inferenceEngine.closeSessionCalled)
        assertTrue("Engine should have initialize called", inferenceEngine.initializeCalled)

        val state = viewModel.uiState.value
        assertEquals(
            "Backend should switch to GPU",
            HardwareBackend.GPU_SUPPORTED,
            state.selectedModel.hardwareAcceleration
        )
    }

    @Test
    fun `CancelTests transitions state to Idle`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        // Start tests
        viewModel.onUiAction(TestUiAction.RunTests(useGpu = false))

        // Cancel tests
        viewModel.onUiAction(TestUiAction.CancelTests)

        val state = viewModel.uiState.value
        assertFalse("State should not be running after cancellation", state.isRunning)
    }
}
