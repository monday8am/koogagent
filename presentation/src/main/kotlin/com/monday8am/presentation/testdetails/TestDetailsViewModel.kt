package com.monday8am.presentation.testdetails

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.testing.AssetsTestRepository
import com.monday8am.koogagent.data.testing.TestCaseDefinition
import com.monday8am.koogagent.data.testing.TestDomain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TestDetailsUiState(
    val tests: ImmutableList<TestCaseDefinition> = persistentListOf(),
    val availableDomains: ImmutableList<TestDomain> = persistentListOf(),
    val filterDomain: TestDomain? = null,
    val isLoading: Boolean = true,
)

sealed class TestDetailsUiAction {
    data class SetDomainFilter(val domain: TestDomain?) : TestDetailsUiAction()
}

interface TestDetailsViewModel {
    val uiState: Flow<TestDetailsUiState>

    fun onUiAction(uiAction: TestDetailsUiAction)

    fun dispose()
}

class TestDetailsViewModelImpl(testRepository: AssetsTestRepository = AssetsTestRepository()) :
    TestDetailsViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val filterDomainFlow = MutableStateFlow<TestDomain?>(null)

    private val loadedTests: StateFlow<List<TestCaseDefinition>> =
        testRepository
            .getTestsAsFlow()
            .catch { e ->
                Logger.e("Failed to load tests", e)
                emit(emptyList())
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val uiState: StateFlow<TestDetailsUiState> =
        combine(loadedTests, filterDomainFlow) { tests, filterDomain ->
                val filteredTests =
                    if (filterDomain == null) tests else tests.filter { it.domain == filterDomain }

                val availableDomains = tests.map { it.domain }.distinct()

                TestDetailsUiState(
                    tests = filteredTests.toImmutableList(),
                    availableDomains = availableDomains.toImmutableList(),
                    filterDomain = filterDomain,
                    isLoading = false,
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue =
                    TestDetailsUiState(
                        tests = persistentListOf(),
                        availableDomains = persistentListOf(),
                        filterDomain = null,
                        isLoading = true,
                    ),
            )

    override fun onUiAction(uiAction: TestDetailsUiAction) {
        when (uiAction) {
            is TestDetailsUiAction.SetDomainFilter -> {
                filterDomainFlow.value = uiAction.domain
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }
}
