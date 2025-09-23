package com.monday8am.koogagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.monday8am.agent.NotificationGenerator
import com.monday8am.agent.OllamaAgent
import com.monday8am.koogagent.ui.NotificationUtils
import com.monday8am.koogagent.ui.NotificationViewModel
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.agent.MealType
import com.monday8am.agent.MotivationLevel
import com.monday8am.agent.NotificationContext
import com.monday8am.agent.WeatherCondition
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationUtils.createChannel(this)
        NotificationUtils.requestNotificationPermission(this)

        setContent {
            val viewModel: NotificationViewModel by viewModels()

            KoogAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        onClickButton = { viewModel.processAndShowNotification() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onClickButton: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    )  {
        Button(onClick = {
            scope.launch {
                val message = NotificationGenerator(
                    agent = OllamaAgent()
                ).generate(
                    NotificationContext(
                        mealType = MealType.WATER,
                        motivationLevel = MotivationLevel.HIGH,
                        weather = WeatherCondition.SUNNY,
                        alreadyLogged = true,
                        userLocale = "en-US",
                        country = "ES",
                    )
                )
                println(message)
            }
        }) {
            Text("Generate Meal Message")
        }

        Button(
            onClick = onClickButton,
        ) {
            Text(
                text = "Trigger Notification",
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KoogAgentTheme {
        MainScreen(
            onClickButton = { }
        )
    }
}