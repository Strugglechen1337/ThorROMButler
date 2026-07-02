package dev.thor.rombutler.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.thor.rombutler.ui.log.LogScreen
import dev.thor.rombutler.ui.review.ReviewScreen
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
        // Fluid slide+fade transitions between the flow's screens
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIM_MILLIS),
            ) + fadeIn(tween(NAV_ANIM_MILLIS))
        },
        exitTransition = {
            fadeOut(tween(NAV_ANIM_MILLIS))
        },
        popEnterTransition = {
            fadeIn(tween(NAV_ANIM_MILLIS))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIM_MILLIS),
            ) + fadeOut(tween(NAV_ANIM_MILLIS))
        },
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
                onOpenReview = { navController.navigate(Routes.REVIEW) },
                onOpenLog = { navController.navigate(Routes.LOG) },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                onBack = { navController.popBackStack() },
                onMoved = {
                    // Back from the log lands on a fresh scan, not the
                    // stale review list.
                    navController.navigate(Routes.LOG) {
                        popUpTo(Routes.SCAN)
                    }
                },
            )
        }
        composable(Routes.LOG) {
            LogScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private const val NAV_ANIM_MILLIS = 320
