package com.mejdi14.kmpstartupmvp.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mejdi14.kmpstartupmvp.KmpApplication
import com.mejdi14.kmpstartupmvp.analytics.ScreenNames
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mejdi14.kmpstartupmvp.PaymentsViewModel
import com.mejdi14.kmpstartupmvp.payments.PaymentsScreen
import com.mejdi14.kmpstartupmvp.AuthViewModel
import com.mejdi14.kmpstartupmvp.auth.AuthGate
import com.mejdi14.kmpstartupmvp.MetadataViewModel
import com.mejdi14.kmpstartupmvp.home.HomeScreen
import com.mejdi14.kmpstartupmvp.account.AccountScreen
import com.mejdi14.kmpstartupmvp.auth.AuthScreen
import com.mejdi14.kmpstartupmvp.auth.ResetPasswordDialog
import com.mejdi14.kmpstartupmvp.ui.components.LoadingIndicator

/**
 * Nav graph + D-002 auth gate. The graph is built once; navigation happens only
 * when [AuthViewModel.authGate] changes value. Token refreshes update the session
 * but never change the gate, so nothing here reacts to them (§1.6 bug not ported).
 */
@Composable
fun AppNavHost(
    authViewModel: AuthViewModel,
    metadataViewModel: MetadataViewModel,
    paymentsViewModel: PaymentsViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val analytics = (LocalContext.current.applicationContext as KmpApplication).analytics

    // Screen views, like Flutter's PosthogObserver: one per destination change (dialogs included, as /reset was a page there).
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            ScreenNames.forRoute(entry.destination.route)?.let(analytics::screen)
        }
    }
    val gate by authViewModel.authGate.collectAsState()
    val pendingReset by authViewModel.pendingPasswordReset.collectAsState()

    // Fires on gate transitions only (and once on first composition, a no-op
    // when the gate already matches the start destination, e.g. after rotation).
    LaunchedEffect(gate) {
        val target = Routes.forGate(gate)
        val current = navController.currentDestination?.route
        // A recovery link signs the user in; keep the reset dialog above Home rather than popping it.
        if (current != target && !(current == Routes.RESET && gate == AuthGate.LoggedIn)) {
            navController.navigate(target) {
                // Clear everything: login/home must not be reachable by Back across the gate.
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Password-reset deep link (task 9): once signed in by the recovery code, show the dialog over Home.
    LaunchedEffect(gate, pendingReset) {
        if (gate == AuthGate.LoggedIn && pendingReset && navController.currentDestination?.route != Routes.RESET) {
            if (navController.currentDestination?.route != Routes.HOME) {
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            navController.navigate(Routes.RESET) { launchSingleTop = true }
        }
    }

    // Payments deep link (`…/payments?price=`): held until signed in, like Flutter's surviving initUrl.
    val paymentRequest by paymentsViewModel.pendingRequest.collectAsState()
    LaunchedEffect(gate, paymentRequest) {
        val request = paymentRequest
        if (gate == AuthGate.LoggedIn && request != null) {
            paymentsViewModel.consumeRequest()
            navController.navigate(Routes.payments(request.price)) { launchSingleTop = true }
        }
    }

    NavHost(navController = navController, startDestination = Routes.forGate(gate), modifier = modifier) {
        composable(Routes.LOADING) { LoadingIndicator() }
        composable(Routes.LOGIN) { AuthScreen(authViewModel) }
        composable(Routes.HOME) {
            HomeScreen(
                authViewModel = authViewModel,
                metadataViewModel = metadataViewModel,
                onOpenPayments = { navController.navigate(Routes.payments(null)) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
            )
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(
                authViewModel = authViewModel,
                metadataViewModel = metadataViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        // Dialog destination: Flutter's /reset was a DialogPage over the current screen.
        dialog(Routes.RESET) {
            ResetPasswordDialog(
                onSubmit = authViewModel::updatePassword,
                onDismiss = {
                    authViewModel.dismissPasswordReset()
                    navController.popBackStack()
                },
            )
        }
        composable(
            Routes.PAYMENTS_PATTERN,
            arguments = listOf(navArgument(Routes.PRICE_ARG) { type = NavType.StringType; nullable = true }),
        ) { entry ->
            PaymentsScreen(
                price = entry.arguments?.getString(Routes.PRICE_ARG),
                viewModel = paymentsViewModel,
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
