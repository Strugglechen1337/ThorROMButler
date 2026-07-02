package dev.thor.rombutler.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.thor.rombutler.ui.scan.ScanScreen
import dev.thor.rombutler.ui.setup.SetupScreen

/**
 * Central navigation graph.
 *
 * @param startDestination either [Routes.SETUP] (first launch / incomplete
 *   setup) or [Routes.SCAN] (setup already done).
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Routes.SCAN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                onOpenSetup = { navController.navigate(Routes.SETUP) },
            )
        }
        // REVIEW and LOG follow in M5/M6.
    }
}
