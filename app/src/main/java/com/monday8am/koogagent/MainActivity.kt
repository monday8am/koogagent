package com.monday8am.koogagent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.monday8am.koogagent.oauth.OAuthRedirectActivity
import com.monday8am.koogagent.ui.navigation.AppNavigation
import com.monday8am.koogagent.ui.theme.KoogAgentTheme

class MainActivity : ComponentActivity() {

    // State for OAuth result - observed by composables
    private val oAuthResultState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize service locator with application context
        Dependencies.appContext = applicationContext

        (Dependencies.notificationEngine as com.monday8am.koogagent.ui.NotificationEngineImpl).apply {
            createChannel()
            requestNotificationPermission(this@MainActivity)
        }

        // Check if launched from OAuth redirect
        handleOAuthIntent(intent)

        setContent {
            KoogAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding),
                        oAuthResultIntent = oAuthResultState.value,
                        onOAuthResultConsumed = { oAuthResultState.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent) {
        if (intent.getBooleanExtra(OAuthRedirectActivity.EXTRA_OAUTH_REDIRECT, false)) {
            oAuthResultState.value = intent
        }
    }
}
