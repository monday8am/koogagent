package com.monday8am.presentation.notifications

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.agent.core.LocalLLModel
import com.monday8am.koogagent.data.DeviceContext
import com.monday8am.koogagent.data.Location
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.NotificationResult
import com.monday8am.koogagent.data.WeatherCondition
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

internal class FakeLocalInferenceEngine : LocalInferenceEngine {
    var initializeCalled = false

    override suspend fun initialize(model: LocalLLModel): Result<Unit> {
        initializeCalled = true
        return Result.success(Unit)
    }

    override fun initializeAsFlow(model: LocalLLModel): Flow<LocalInferenceEngine> {
        initializeCalled = true
        return flowOf(this)
    }

    override suspend fun prompt(prompt: String): Result<String> = Result.success("Test response")

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
    override suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
    ) = WeatherCondition.SUNNY
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
        url: String,
        modelName: String,
    ): Flow<ModelDownloadManager.Status> =
        flow {
            if (shouldFail) {
                throw Exception("Download failed") // Throw inside flow builder to be caught by .catch { }
            }

            progressSteps.forEach { progress ->
                emit(ModelDownloadManager.Status.InProgress(progress))
            }
            emit(ModelDownloadManager.Status.Completed(File("/fake/path/$modelName")))
        }

    override fun cancelDownload() {
    }

    override suspend fun modelExists(modelName: String): Boolean = modelExists

    override fun getModelPath(modelName: String): String = "/fake/path/$modelName"
}
