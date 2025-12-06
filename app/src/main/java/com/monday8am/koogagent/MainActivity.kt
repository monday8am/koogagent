package com.monday8am.koogagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.WeatherProviderImpl
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.inference.litertlm.LiteRTLmTools
import com.monday8am.koogagent.inference.litertlm.NativeLocationTools
import com.monday8am.koogagent.inference.mediapipe.MediaPipeTools
import com.monday8am.koogagent.ui.DeviceContextProviderImpl
import com.monday8am.koogagent.ui.NotificationEngineImpl
import com.monday8am.koogagent.ui.NotificationViewModelFactory
import com.monday8am.koogagent.ui.navigation.AppNavigation
import com.monday8am.koogagent.ui.screens.modelselector.ModelSelectorViewModelFactory
import com.monday8am.koogagent.ui.theme.KoogAgentTheme

class MainActivity : ComponentActivity() {
    private val notificationEngine: NotificationEngineImpl by lazy {
        NotificationEngineImpl(this.applicationContext)
    }

    private val viewModelFactory: NotificationViewModelFactory by lazy {
        val applicationContext = this.applicationContext

        // Native LiteRT-LM tools with @Tool annotations
        // These are passed to ConversationConfig for native tool calling
        val nativeTools =
            listOf(
                NativeLocationTools(),
                LiteRTLmTools(),
            )

        // MediaPipe tools using AI Edge On-Device APIs
        // These use FunctionDeclaration format for MediaPipe inference
        val mediaPipeTools = listOf(MediaPipeTools.createAllTools())

        // Select model at startup (could be configurable in future)
        val selectedModel = ModelCatalog.DEFAULT

        val notificationEngine = notificationEngine
        val weatherProvider = WeatherProviderImpl()
        val locationProvider = MockLocationProvider() // <-- using Mock for now.
        val deviceContextProvider = DeviceContextProviderImpl(applicationContext)

        NotificationViewModelFactory(
            context = applicationContext,
            selectedModel = selectedModel,
            notificationEngine = notificationEngine,
            weatherProvider = weatherProvider,
            locationProvider = locationProvider,
            deviceContextProvider = deviceContextProvider,
            liteRtTools = nativeTools,
            mediaPipeTools = mediaPipeTools,
        )
    }

    private val modelSelectorFactory: ModelSelectorViewModelFactory by lazy {
        val modelDownloadManager = ModelDownloadManagerImpl(this.applicationContext)
        ModelSelectorViewModelFactory(
            availableModels = listOf(ModelCatalog.DEFAULT),
            modelDownloadManager = modelDownloadManager,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        notificationEngine.createChannel()
        notificationEngine.requestNotificationPermission(this)

        setContent {
            KoogAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        modelSelectorFactory = modelSelectorFactory,
                        notificationFactory = viewModelFactory,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
