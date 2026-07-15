package dev.thor.rombutler.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.thor.rombutler.ui.log.LogScreen
import dev.thor.rombutler.ui.doctor.LibraryDoctorScreen
import dev.thor.rombutler.ui.review.ReviewScreen
import dev.thor.rombutler.ui.scan.ScanScreen
import dev.thor.rombutler.ui.settings.SettingsScreen
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
        // Springy slide+fade: screens glide in with a gentle settle
        // instead of a linear tween — subtle, but it makes navigation
        // feel physical rather than mechanical.
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = navSpring(),
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
                animationSpec = navSpring(),
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
                onOpenSetup = { navController.navigate(Routes.SETTINGS) },
                onOpenReview = { navController.navigate(Routes.REVIEW) },
                onOpenLog = { navController.navigate(Routes.LOG) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenReview = { navController.navigate(Routes.REVIEW) },
                onOpenDoctor = { navController.navigate(Routes.DOCTOR) },
            )
        }
        composable(Routes.DOCTOR) {
            LibraryDoctorScreen(
                onBack = { navController.popBackStack() },
                onOpenReview = { navController.navigate(Routes.REVIEW) },
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

private fun navSpring() = spring(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
