package com.monday8am.koogagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherProvider
import com.monday8am.presentation.notifications.DeviceContextProvider
import com.monday8am.presentation.notifications.ModelDownloadManager
import com.monday8am.presentation.notifications.NotificationEngine
import com.monday8am.presentation.notifications.NotificationViewModel
import com.monday8am.presentation.notifications.NotificationViewModelImpl
import com.monday8am.presentation.notifications.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidNotificationViewModel(
    private val impl: NotificationViewModel,
) : ViewModel(),
    NotificationViewModel by impl {
    override val uiState: StateFlow<UiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState(),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}

class NotificationViewModelFactory(
    private val inferenceEngine: LocalInferenceEngine,
    private val notificationEngine: NotificationEngine,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val deviceContextProvider: DeviceContextProvider,
    private val modelManager: ModelDownloadManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroidNotificationViewModel::class.java)) {
            val impl =
                NotificationViewModelImpl(
                    inferenceEngine = inferenceEngine,
                    notificationEngine = notificationEngine,
                    weatherProvider = weatherProvider,
                    locationProvider = locationProvider,
                    deviceContextProvider = deviceContextProvider,
                    modelManager = modelManager,
                )
            return AndroidNotificationViewModel(impl) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
