package com.monday8am.koogagent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.monday8am.koogagent.ui.NotificationEngineImpl
import com.monday8am.koogagent.ui.navigation.AppNavigation
import com.monday8am.koogagent.ui.theme.KoogAgentTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize service locator with application context
        Dependencies.appContext = applicationContext

        (Dependencies.notificationEngine as NotificationEngineImpl).apply {
            createChannel()
            requestNotificationPermission(this@MainActivity)
        }

        Dependencies.oAuthManager.onHandleIntent(intent)

        setContent {
            KoogAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Dependencies.oAuthManager.onHandleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            Dependencies.dispose()
        }
    }
}
