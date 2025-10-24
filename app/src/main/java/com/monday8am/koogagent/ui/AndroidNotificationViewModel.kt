package com.monday8am.koogagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherProvider
import com.monday8am.koogagent.mediapipe.LocalInferenceEngine
import com.monday8am.koogagent.mediapipe.download.ModelDownloadManager

// This class remains the same - a simple wrapper.
class AndroidNotificationViewModel(
    private val impl: NotificationViewModel
) : ViewModel(), NotificationViewModel by impl

// The factory is now a separate, top-level class, which is cleaner.
class NotificationViewModelFactory(
    // It now declares the dependencies it needs, rather than creating them.
    private val inferenceEngine: LocalInferenceEngine,
    private val notificationEngine: NotificationEngine,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val deviceContextProvider: DeviceContextProvider,
    private val modelManager: ModelDownloadManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroidNotificationViewModel::class.java)) {
            // 1. Create the pure Kotlin implementation with the provided dependencies
            val impl = NotificationViewModelImpl(
                inferenceEngine = inferenceEngine,
                notificationEngine = notificationEngine,
                weatherProvider = weatherProvider,
                locationProvider = locationProvider,
                deviceContextProvider = deviceContextProvider,
                modelManager = modelManager
            )
            // 2. Wrap it in the Android-specific ViewModel
            return AndroidNotificationViewModel(impl) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}