package com.monday8am.koogagent.copilot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.monday8am.koogagent.copilot.ui.navigation.AppNavigation
import com.monday8am.koogagent.copilot.ui.theme.CyclingCopilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize service locator with application context
        Dependencies.appContext = applicationContext

        // Handle OAuth redirect intent on fresh start
        if (savedInstanceState == null) {
            Dependencies.oAuthManager.onHandleIntent(intent)
        }

        setContent {
            CyclingCopilotTheme {
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
}
