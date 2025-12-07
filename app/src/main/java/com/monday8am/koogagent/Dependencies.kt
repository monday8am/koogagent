package com.monday8am.koogagent

import android.content.Context
import com.google.ai.edge.localagents.core.proto.Tool
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.WeatherProvider
import com.monday8am.koogagent.data.WeatherProviderImpl
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.inference.litertlm.LiteRTLmTools
import com.monday8am.koogagent.inference.litertlm.NativeLocationTools
import com.monday8am.koogagent.inference.mediapipe.MediaPipeTools
import com.monday8am.koogagent.ui.DeviceContextProviderImpl
import com.monday8am.koogagent.ui.NotificationEngineImpl
import com.monday8am.presentation.notifications.DeviceContextProvider
import com.monday8am.presentation.notifications.NotificationEngine
import com.monday8am.presentation.modelselector.ModelDownloadManager

/**
 * Simple service locator for app dependencies.
 * Centralizes dependency creation and avoids factory boilerplate.
 */
object Dependencies {
    lateinit var appContext: Context

    val notificationEngine: NotificationEngine by lazy {
        NotificationEngineImpl(appContext)
    }

    val weatherProvider: WeatherProvider by lazy {
        WeatherProviderImpl()
    }

    val locationProvider: LocationProvider by lazy {
        MockLocationProvider()
    }

    val deviceContextProvider: DeviceContextProvider by lazy {
        DeviceContextProviderImpl(appContext)
    }

    val modelDownloadManager: ModelDownloadManager by lazy {
        ModelDownloadManagerImpl(appContext)
    }

    val nativeTools: List<Any> by lazy {
        listOf(
            NativeLocationTools(),
            LiteRTLmTools(),
        )
    }

    val mediaPipeTools: List<Tool> by lazy {
        listOf(MediaPipeTools.createAllTools())
    }
}
