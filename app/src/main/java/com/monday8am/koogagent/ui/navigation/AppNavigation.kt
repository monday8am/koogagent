package com.monday8am.koogagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.monday8am.koogagent.ui.NotificationViewModelFactory
import com.monday8am.koogagent.ui.screens.modelselector.ModelSelectorScreen
import com.monday8am.koogagent.ui.screens.modelselector.ModelSelectorViewModelFactory
import com.monday8am.koogagent.ui.screens.notification.NotificationScreen

@Composable
fun AppNavigation(
    modelSelectorFactory: ModelSelectorViewModelFactory,
    notificationFactory: NotificationViewModelFactory,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.ModelSelector,
        modifier = modifier,
    ) {
        composable<Route.ModelSelector> {
            ModelSelectorScreen(
                onNavigateToNotification = {
                    navController.navigate(Route.Notification)
                },
                viewModelFactory = modelSelectorFactory,
            )
        }

        composable<Route.Notification> {
            NotificationScreen(
                viewModelFactory = notificationFactory,
            )
        }
    }
}
