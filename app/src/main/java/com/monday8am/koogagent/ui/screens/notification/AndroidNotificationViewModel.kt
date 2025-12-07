package com.monday8am.koogagent.ui.screens.notification

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.localagents.core.proto.Tool
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.WeatherProvider
import com.monday8am.koogagent.inference.InferenceEngineFactory
import com.monday8am.presentation.notifications.DeviceContextProvider
import com.monday8am.presentation.notifications.NotificationEngine
import com.monday8am.presentation.notifications.NotificationViewModel
import com.monday8am.presentation.notifications.NotificationViewModelImpl
import com.monday8am.presentation.notifications.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidNotificationViewModel(
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

class NotificationViewModelFactory(
    private val context: Context,
    private val selectedModel: ModelConfiguration,
    private val notificationEngine: NotificationEngine,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val deviceContextProvider: DeviceContextProvider,
    private val liteRtTools: List<Any>,
    private val mediaPipeTools: List<Tool>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroidNotificationViewModel::class.java)) {
            // Create inference engine once for the selected model
            val inferenceEngine =
                InferenceEngineFactory.create(
                    context = context,
                    inferenceLibrary = selectedModel.inferenceLibrary,
                    liteRtTools = liteRtTools,
                    mediaPipeTools = mediaPipeTools,
                )

            // Construct model path using same logic as ModelDownloadManagerImpl
            val modelDestinationPath = "${context.applicationContext.filesDir}/data/local/tmp/slm/"
            val modelPath = "$modelDestinationPath${selectedModel.bundleFilename}"

            val impl =
                NotificationViewModelImpl(
                    selectedModel = selectedModel,
                    modelPath = modelPath,
                    inferenceEngine = inferenceEngine,
                    notificationEngine = notificationEngine,
                    weatherProvider = weatherProvider,
                    locationProvider = locationProvider,
                    deviceContextProvider = deviceContextProvider,
                )
            return AndroidNotificationViewModel(impl, selectedModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
