package com.monday8am.koogagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.monday8am.koogagent.ui.screens.modelselector.ModelSelectorScreen
import com.monday8am.koogagent.ui.screens.notification.NotificationScreen
import com.monday8am.koogagent.ui.screens.testing.TestScreen

@Composable
fun AppNavigation(
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
                onNavigateToNotification = { modelId ->
                    navController.navigate(Route.Notification(modelId))
                },
                onNavigateToTesting = { modelId -> navController.navigate(Route.Testing(modelId)) },
            )
        }
        composable<Route.Notification> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.Notification>()
            NotificationScreen(modelId = args.modelId)
        }
        composable<Route.Testing> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.Testing>()
            TestScreen(modelId = args.modelId)
        }
    }
}
