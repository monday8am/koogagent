package com.monday8am.edgelab.presentation.authormanager

import com.monday8am.edgelab.data.auth.AuthorInfo
import com.monday8am.edgelab.data.auth.AuthorRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthorUiState(
    val authors: ImmutableList<AuthorInfo> = persistentListOf(),
    val isLoading: Boolean = false,
    val addFieldText: String = "",
    val addError: String? = null,
    val isAdding: Boolean = false,
)

sealed class AuthorUiAction {
    data class UpdateAddField(val text: String) : AuthorUiAction()

    data object SubmitAdd : AuthorUiAction()

    data class RemoveAuthor(val name: String) : AuthorUiAction()
}

interface AuthorManagerViewModel {
    val uiState: StateFlow<AuthorUiState>

    fun onUiAction(action: AuthorUiAction)

    fun dispose()
}

class AuthorManagerViewModelImpl(private val authorRepository: AuthorRepository) :
    AuthorManagerViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AuthorUiState())
    override val uiState: StateFlow<AuthorUiState> = _uiState

    init {
        authorRepository.authors
            .onEach { authors -> _uiState.update { it.copy(authors = authors.toImmutableList()) } }
            .launchIn(scope)
    }

    override fun onUiAction(action: AuthorUiAction) {
        when (action) {
            is AuthorUiAction.UpdateAddField -> {
                _uiState.update { it.copy(addFieldText = action.text, addError = null) }
            }

            is AuthorUiAction.SubmitAdd -> {
                val name = _uiState.value.addFieldText.trim()
                if (name.isBlank()) return

                _uiState.update { it.copy(isAdding = true, addError = null) }
                scope.launch {
                    authorRepository
                        .addAuthor(name)
                        .onSuccess {
                            _uiState.update { it.copy(addFieldText = "", isAdding = false) }
                        }
                        .onFailure {
                            _uiState.update {
                                it.copy(addError = "Author not found", isAdding = false)
                            }
                        }
                }
            }

            is AuthorUiAction.RemoveAuthor -> {
                scope.launch { authorRepository.removeAuthor(action.name) }
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }
}
