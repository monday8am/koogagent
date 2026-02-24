package com.monday8am.edgelab.copilot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.monday8am.edgelab.copilot.ui.screens.onboard.OnboardScreen

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Onboard,
        modifier = modifier,
    ) {
        composable<Route.Onboard> {
            OnboardScreen(onNavigateToSetup = { navController.navigate(Route.RideSetup) })
        }

        composable<Route.RideSetup> {
            // TODO: Implement RideSetupScreen
        }

        composable<Route.LiveRide> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.LiveRide>()
            // TODO: Implement LiveRideScreen
        }
    }
}
