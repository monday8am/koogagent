package com.monday8am.presentation.notifications

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.DeviceContext
import com.monday8am.koogagent.data.Location
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.NotificationResult
import com.monday8am.koogagent.data.WeatherCondition
import com.monday8am.koogagent.data.WeatherProvider
import com.monday8am.presentation.modelselector.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal class FakeLocalInferenceEngine : LocalInferenceEngine {
    var initializeCalled = false

    override suspend fun initialize(modelConfig: ModelConfiguration, modelPath: String): Result<Unit> {
        initializeCalled = true
        return Result.success(Unit)
    }

    override fun initializeAsFlow(modelConfig: ModelConfiguration, modelPath: String): Flow<LocalInferenceEngine> {
        initializeCalled = true
        return flowOf(this)
    }

    override fun resetConversation(): Result<Unit> = Result.success(Unit)

    override suspend fun prompt(prompt: String): Result<String> = Result.success("Test response")

    override fun promptStreaming(prompt: String) = flowOf("Hi!")

    override fun closeSession(): Result<Unit> = Result.success(Unit)
}

internal class FakeNotificationEngine : NotificationEngine {
    var showNotificationCalled = false
    var lastNotification: NotificationResult? = null

    override fun showNotification(result: NotificationResult) {
        showNotificationCalled = true
        lastNotification = result
    }
}

internal class FakeWeatherProvider : WeatherProvider {
    override suspend fun getCurrentWeather(latitude: Double, longitude: Double) = WeatherCondition.SUNNY
}

internal class FakeLocationProvider : LocationProvider {
    override suspend fun getLocation() = Location(latitude = 40.4168, longitude = -3.7038)
}

internal class FakeDeviceContextProvider : DeviceContextProvider {
    override fun getDeviceContext() = DeviceContext(language = "en-US", country = "US")
}

internal class FakeModelDownloadManager(
    private val modelExists: Boolean = true,
    private val progressSteps: List<Float> = emptyList(),
    private val shouldFail: Boolean = false,
) : ModelDownloadManager {
    override fun downloadModel(
        modelId: String,
        downloadUrl: String,
        bundleFilename: String,
    ): Flow<ModelDownloadManager.Status> = flow {
        if (shouldFail) {
            throw Exception("Download failed") // Throw inside flow builder to be caught by .catch { }
        }

        progressSteps.forEach { progress ->
            emit(ModelDownloadManager.Status.InProgress(progress))
        }
        emit(ModelDownloadManager.Status.Completed(File("/fake/path/$bundleFilename")))
    }

    override fun cancelDownload() {
    }

    override suspend fun modelExists(bundleFilename: String): Boolean = modelExists

    override fun getModelPath(bundleFilename: String): String = "/fake/path/$bundleFilename"

    override suspend fun deleteModel(bundleFilename: String): Boolean = true
}
