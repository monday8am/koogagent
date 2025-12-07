package com.monday8am.koogagent.ui.screens.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.presentation.notifications.NotificationViewModel
import com.monday8am.presentation.notifications.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

internal class AndroidNotificationViewModel(
    private val impl: NotificationViewModel,
    private val selectedModel: ModelConfiguration,
) : ViewModel(),
    NotificationViewModel by impl {
    override val uiState: StateFlow<UiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState(selectedModel = selectedModel),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
